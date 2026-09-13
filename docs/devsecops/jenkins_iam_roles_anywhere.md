# Jenkins IAM Roles Anywhere Temporary Credentials

Updated: 2026-06-04

This document explains how DocVault Jenkins reads AWS Secrets Manager without long-lived AWS access keys.

The final goal is:

```text
Local Jenkins controller VM
  -> authenticates to AWS with an X.509 certificate
  -> receives temporary AWS STS credentials through IAM Roles Anywhere
  -> reads Harbor robot token from AWS Secrets Manager
  -> exposes that token as a Jenkins credential

Jenkins agent VM
  -> receives the token during pipeline execution
  -> logs in to Harbor and pushes images
```

The working verification commands were:

```bash
aws sts get-caller-identity

aws secretsmanager get-secret-value \
  --region ap-southeast-1 \
  --secret-id harbor-docvault-dev-robot-token
```

After Jenkins was restarted with AWS SDK config loading enabled, the credential appeared in Jenkins as:

```text
harbor-docvault-dev-robot-token
```

## Why IAM Roles Anywhere

Normally, an AWS workload should avoid static access keys.

For workloads inside AWS, this is easy:

```text
EC2 workload -> EC2 instance profile role
EKS workload -> IRSA / Pod Identity
Lambda workload -> Lambda execution role
```

The DocVault Jenkins controller is different because it runs on a local VM, outside AWS. AWS cannot automatically attach an EC2 instance profile or EKS service account role to it.

IAM Roles Anywhere solves this by letting an external workload authenticate with an X.509 certificate. AWS validates that certificate against a trusted certificate authority, then returns temporary AWS credentials for an IAM role.

In this project, IAM Roles Anywhere is used so Jenkins can read AWS Secrets Manager without storing a normal IAM user access key.

Official references:

- IAM Roles Anywhere overview: <https://docs.aws.amazon.com/rolesanywhere/latest/userguide/introduction.html>
- IAM Roles Anywhere trust model: <https://docs.aws.amazon.com/rolesanywhere/latest/userguide/trust-model.html>
- IAM Roles Anywhere credential helper: <https://docs.aws.amazon.com/rolesanywhere/latest/userguide/credential-helper.html>

## The Core Idea

There are two identities involved:

```text
Certificate authority identity:
  docvault-ca.pem
  docvault-ca.key

Jenkins workload identity:
  jenkins-controller.pem
  jenkins-controller.key
```

The certificate authority signs certificates.

Jenkins owns a client certificate signed by that certificate authority.

AWS trusts the certificate authority through an IAM Roles Anywhere trust anchor.

When Jenkins asks AWS for credentials, the request proves:

```text
1. Jenkins has the private key for jenkins-controller.pem.
2. jenkins-controller.pem was signed by the trusted DocVault CA.
3. The certificate subject matches the IAM role trust policy.
```

If all checks pass, AWS returns temporary STS credentials.

## File Purposes

### `docvault-ca.pem`

This is the public CA certificate.

AWS stores this certificate in the IAM Roles Anywhere trust anchor. It is safe to distribute because it does not contain the private key.

AWS uses it to verify that the Jenkins certificate was issued by the trusted DocVault CA.

### `docvault-ca.key`

This is the CA private key.

It is sensitive because it can sign new certificates. Anyone who steals it can create another certificate that AWS may trust.

This key should not be committed, uploaded, or left casually on the Jenkins VM. Keep it offline or in a secure admin location after issuing the Jenkins certificate.

### `jenkins-controller.pem`

This is the public client certificate for the Jenkins controller.

The credential helper sends this certificate to IAM Roles Anywhere during authentication.

### `jenkins-controller.key`

This is the Jenkins controller private key.

The credential helper uses this key to sign the IAM Roles Anywhere request. AWS verifies the signature using `jenkins-controller.pem`.

This file must be readable by the Jenkins OS user, but should not be world-readable.

Recommended permissions:

```bash
sudo chown root:jenkins /etc/jenkins/aws-rolesanywhere/jenkins-controller.key
sudo chmod 640 /etc/jenkins/aws-rolesanywhere/jenkins-controller.key
```

## Why The CA Certificate Needs CA Constraints

The CA certificate must say:

```text
X509v3 Basic Constraints: critical
    CA:TRUE

X509v3 Key Usage: critical
    Certificate Sign, CRL Sign
```

This matters because AWS must know that `docvault-ca.pem` is allowed to act as a certificate authority.

`CA:TRUE` means:

```text
This certificate is allowed to issue/sign other certificates.
```

`Certificate Sign` means:

```text
The public key in this certificate can verify certificates signed by this CA.
```

`CRL Sign` means:

```text
This CA can sign certificate revocation lists.
```

CRL stands for Certificate Revocation List. It is a list of certificates that should no longer be trusted before their normal expiry date.

IAM Roles Anywhere rejects trust anchor certificates that do not satisfy CA certificate constraints. That was the cause of this error:

```text
Incorrect basic constraints for CA certificate
```

## Why The Jenkins Certificate Needs Client Constraints

The Jenkins certificate must say:

```text
X509v3 Basic Constraints: critical
    CA:FALSE

X509v3 Key Usage: critical
    Digital Signature

Subject: CN = jenkins-controller
```

`CA:FALSE` means:

```text
This certificate is not allowed to issue other certificates.
```

That is correct because Jenkins is a workload, not a certificate authority.

`Digital Signature` means:

```text
The private key can be used to sign authentication requests.
```

IAM Roles Anywhere needs this because the credential helper signs the request to prove Jenkins owns `jenkins-controller.key`.

`Subject: CN = jenkins-controller` gives the workload a certificate identity.

The IAM role trust policy can restrict access to only certificates with that subject:

```json
{
  "StringEquals": {
    "aws:PrincipalTag/x509Subject/CN": "jenkins-controller"
  }
}
```

Without that condition, any certificate issued by the trusted CA could potentially assume the role.

## OpenSSL Commands Used

### Create The CA Extension File

```bash
cat > ca-ext.cnf <<'EOF'
[ v3_ca ]
basicConstraints = critical, CA:true
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
EOF
```

This creates an OpenSSL config section called `v3_ca`.

It tells OpenSSL to generate a proper CA certificate, not just a generic self-signed certificate.

### Generate The CA Private Key

```bash
openssl genrsa -out docvault-ca.key 4096
```

This creates a 4096-bit RSA private key.

The CA key signs client certificates. Protect this file carefully.

### Create The CA Certificate

```bash
openssl req -x509 -new -nodes \
  -key docvault-ca.key \
  -sha256 \
  -days 3650 \
  -out docvault-ca.pem \
  -subj "/CN=docvault-rolesanywhere-ca" \
  -extensions v3_ca \
  -config ca-ext.cnf
```

Explanation:

```text
req             create a certificate request or self-signed certificate
-x509           output a self-signed certificate instead of a CSR
-new            create a new request/certificate
-nodes          do not encrypt the private key output
-key            use docvault-ca.key
-sha256         sign with SHA256
-days 3650      valid for about 10 years
-out            write certificate to docvault-ca.pem
-subj           set certificate subject
-extensions     use the v3_ca extension block
-config         read extension settings from ca-ext.cnf
```

The CA certificate is self-signed because it is the root of trust for this small private PKI.

### Create The Jenkins Extension File

```bash
cat > jenkins-ext.cnf <<'EOF'
[ v3_client ]
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature
extendedKeyUsage = clientAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
EOF
```

This tells OpenSSL to create a client/workload certificate, not another CA certificate.

### Generate The Jenkins Private Key

```bash
openssl genrsa -out jenkins-controller.key 2048
```

This creates the Jenkins controller private key.

The key is used by `aws_signing_helper` to sign authentication requests.

### Create The Jenkins Certificate Signing Request

```bash
openssl req -new \
  -key jenkins-controller.key \
  -out jenkins-controller.csr \
  -subj "/CN=jenkins-controller"
```

This creates a CSR, or certificate signing request.

The CSR says:

```text
Please issue a certificate for identity CN=jenkins-controller.
```

It does not become trusted until the CA signs it.

### Sign The Jenkins Certificate With The CA

```bash
openssl x509 -req \
  -in jenkins-controller.csr \
  -CA docvault-ca.pem \
  -CAkey docvault-ca.key \
  -CAcreateserial \
  -out jenkins-controller.pem \
  -days 365 \
  -sha256 \
  -extensions v3_client \
  -extfile jenkins-ext.cnf
```

Explanation:

```text
x509             work with X.509 certificates
-req             input is a CSR
-in              CSR to sign
-CA              CA certificate
-CAkey           CA private key used for signing
-CAcreateserial  create a serial number file if needed
-out             output signed certificate
-days 365        valid for 1 year
-sha256          use SHA256 signature
-extensions      use the v3_client extension block
-extfile         read extension settings from jenkins-ext.cnf
```

The result is `jenkins-controller.pem`, a certificate that AWS can validate against the DocVault CA trust anchor.

## Local Certificate Verification

Check the CA certificate:

```bash
openssl x509 -in docvault-ca.pem -text -noout
```

Expected:

```text
X509v3 Basic Constraints: critical
    CA:TRUE
X509v3 Key Usage: critical
    Certificate Sign, CRL Sign
```

Check the Jenkins certificate:

```bash
openssl x509 -in jenkins-controller.pem -text -noout
```

Expected:

```text
X509v3 Basic Constraints: critical
    CA:FALSE
X509v3 Key Usage: critical
    Digital Signature
Subject: CN = jenkins-controller
```

Check that the Jenkins certificate chains to the CA:

```bash
openssl verify \
  -CAfile docvault-ca.pem \
  jenkins-controller.pem
```

Expected:

```text
jenkins-controller.pem: OK
```

## Install Certificates On The Jenkins Controller

The credential helper reads files from:

```text
/etc/jenkins/aws-rolesanywhere/
```

Install the current files:

```bash
sudo mkdir -p /etc/jenkins/aws-rolesanywhere

sudo cp jenkins-controller.pem /etc/jenkins/aws-rolesanywhere/
sudo cp jenkins-controller.key /etc/jenkins/aws-rolesanywhere/
sudo cp docvault-ca.pem /etc/jenkins/aws-rolesanywhere/

sudo chown root:jenkins /etc/jenkins/aws-rolesanywhere/jenkins-controller.key
sudo chmod 640 /etc/jenkins/aws-rolesanywhere/jenkins-controller.key
sudo chmod 644 /etc/jenkins/aws-rolesanywhere/jenkins-controller.pem
sudo chmod 644 /etc/jenkins/aws-rolesanywhere/docvault-ca.pem
```

One real failure during setup was caused by recreating the certificates but forgetting to copy them into this folder. The command still read the old certificate files and AWS rejected them.

## AWS Resources Created

### Trust Anchor

The trust anchor stores the public CA certificate:

```text
docvault-ca.pem
```

This tells IAM Roles Anywhere:

```text
Trust client certificates issued by this CA.
```

### IAM Role

The IAM role is:

```text
docvault-jenkins-secretsmanager-read
```

The trust policy allows IAM Roles Anywhere to assume the role only when the certificate identity matches the Jenkins controller.

Example trust policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "rolesanywhere.amazonaws.com"
      },
      "Action": [
        "sts:AssumeRole",
        "sts:TagSession",
        "sts:SetSourceIdentity"
      ],
      "Condition": {
        "ArnEquals": {
          "aws:SourceArn": "arn:aws:rolesanywhere:ap-southeast-1:<account-id>:trust-anchor/<trust-anchor-id>"
        },
        "StringEquals": {
          "aws:PrincipalTag/x509Subject/CN": "jenkins-controller"
        }
      }
    }
  ]
}
```

The permission policy allows reading only the Jenkins Harbor token:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListSecretsForJenkinsPlugin",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:ListSecrets"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ReadHarborRobotToken",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:DescribeSecret",
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:ap-southeast-1:<account-id>:secret:harbor-docvault-dev-robot-token-*"
    }
  ]
}
```

### Profile

The IAM Roles Anywhere profile connects the external certificate authentication flow to the IAM role.

The profile says:

```text
When a valid certificate authenticates, this profile may vend credentials for this IAM role.
```

## Credential Helper Test

The credential helper command was:

```bash
aws_signing_helper credential-process \
  --region ap-southeast-1 \
  --certificate /etc/jenkins/aws-rolesanywhere/jenkins-controller.pem \
  --private-key /etc/jenkins/aws-rolesanywhere/jenkins-controller.key \
  --trust-anchor-arn arn:aws:rolesanywhere:ap-southeast-1:<account-id>:trust-anchor/<trust-anchor-id> \
  --profile-arn arn:aws:rolesanywhere:ap-southeast-1:<account-id>:profile/<profile-id> \
  --role-arn arn:aws:iam::<account-id>:role/docvault-jenkins-secretsmanager-read
```

Expected output:

```json
{
  "Version": 1,
  "AccessKeyId": "...",
  "SecretAccessKey": "...",
  "SessionToken": "...",
  "Expiration": "..."
}
```

This output is temporary AWS credential JSON. The values expire automatically.

## Configure AWS SDK For Jenkins

For Jenkins to use this automatically, configure the AWS SDK credential process for the `jenkins` OS user.

As the `jenkins` user:

```bash
mkdir -p ~/.aws
nano ~/.aws/config
```

Config:

```ini
[default]
region = ap-southeast-1
credential_process = /usr/local/bin/aws_signing_helper credential-process --region ap-southeast-1 --certificate /etc/jenkins/aws-rolesanywhere/jenkins-controller.pem --private-key /etc/jenkins/aws-rolesanywhere/jenkins-controller.key --trust-anchor-arn arn:aws:rolesanywhere:ap-southeast-1:<account-id>:trust-anchor/<trust-anchor-id> --profile-arn arn:aws:rolesanywhere:ap-southeast-1:<account-id>:profile/<profile-id> --role-arn arn:aws:iam::<account-id>:role/docvault-jenkins-secretsmanager-read
```

Then test:

```bash
aws sts get-caller-identity

aws secretsmanager get-secret-value \
  --region ap-southeast-1 \
  --secret-id harbor-docvault-dev-robot-token
```

`aws sts get-caller-identity` confirms which AWS identity Jenkins is using.

`aws secretsmanager get-secret-value` confirms that Jenkins can read the Harbor robot token secret.

## Configure Jenkins Systemd Environment

Jenkins must load the AWS shared config file so the Java AWS SDK can use `credential_process`.

Edit the Jenkins systemd override:

```bash
sudo systemctl edit jenkins
```

Add:

```ini
[Service]
Environment="AWS_REGION=ap-southeast-1"
Environment="AWS_DEFAULT_REGION=ap-southeast-1"
Environment="AWS_SDK_LOAD_CONFIG=1"
```

Restart:

```bash
sudo systemctl daemon-reload
sudo systemctl restart jenkins
```

After restart, Jenkins discovered the AWS Secrets Manager credential:

```text
harbor-docvault-dev-robot-token
```

## Managing This With Terraform

The AWS-side IAM Roles Anywhere resources can be managed by Terraform:

```text
IAM Roles Anywhere trust anchor
IAM role assumed by Jenkins
Secrets Manager read policy for Jenkins
IAM Roles Anywhere profile
Terraform outputs for aws_signing_helper config
```

The repo contains:

```text
infra/terraform/aws-eks/jenkins-roles-anywhere.tf
```

Terraform should manage the AWS resources, but it should not manage or store `jenkins-controller.key` or `docvault-ca.key`.

Private keys do not belong in Terraform state. Terraform state is sensitive and often copied to remote backends, CI logs, local caches, or other machines. Keep private keys outside Terraform.

Terraform only needs the public CA certificate:

```text
docvault-ca.pem
```

That certificate is public trust material. AWS uses it as the Roles Anywhere trust anchor.

### Enable Terraform Management

In `infra/terraform/aws-eks/terraform.tfvars`, add:

```hcl
enable_jenkins_roles_anywhere = true

jenkins_rolesanywhere_ca_certificate_path = "C:/Users/<user>/rolesanywhere-certs/docvault-ca.pem"
jenkins_rolesanywhere_certificate_common_name = "jenkins-controller"

jenkins_secretsmanager_secret_names = [
  "harbor-docvault-dev-robot-token",
]
```

The path must point to `docvault-ca.pem`, not `docvault-ca.key`.

### If Resources Already Exist In AWS

Terraform does not automatically know about resources created manually in the AWS Console.

For existing resources, choose one:

```text
Option A: import existing resources into Terraform state
Option B: delete the manual resources and let Terraform recreate them
```

Import is safer because your Jenkins setup already works.

Set `enable_jenkins_roles_anywhere = true`, then import the manually-created resources:

```powershell
cd infra/terraform/aws-eks

terraform import 'aws_rolesanywhere_trust_anchor.jenkins[0]' <trust-anchor-id>
terraform import 'aws_rolesanywhere_profile.jenkins[0]' <profile-id>
terraform import 'aws_iam_role.jenkins_rolesanywhere[0]' docvault-jenkins-secretsmanager-read
```

If the inline policy already exists with the same name, import it too:

```powershell
terraform import 'aws_iam_role_policy.jenkins_secretsmanager_read[0]' docvault-jenkins-secretsmanager-read:docvault-jenkins-secretsmanager-read
```

If your manual role has a different name, either rename the Terraform resource value before import or import the existing role and let Terraform show the rename/replacement in `terraform plan`.

After import:

```powershell
terraform plan -out tfplan
```

Review the plan. A good plan should not unexpectedly destroy the working Jenkins role, trust anchor, or profile.

### If Creating From Scratch

If you did not create the resources manually:

```powershell
cd infra/terraform/aws-eks
terraform plan -out tfplan
terraform apply tfplan
```

Then update the Jenkins controller `~/.aws/config` with Terraform outputs:

```powershell
terraform output jenkins_rolesanywhere_trust_anchor_arn
terraform output jenkins_rolesanywhere_profile_arn
terraform output jenkins_rolesanywhere_role_arn
```

Use those values in:

```ini
[default]
region = ap-southeast-1
credential_process = /usr/local/bin/aws_signing_helper credential-process --region ap-southeast-1 --certificate /etc/jenkins/aws-rolesanywhere/jenkins-controller.pem --private-key /etc/jenkins/aws-rolesanywhere/jenkins-controller.key --trust-anchor-arn <trust-anchor-arn> --profile-arn <profile-arn> --role-arn <role-arn>
```

## Jenkins Pipeline Usage

The Harbor robot username contains `$`, so it should not be stored in an AWS tag. AWS tag values do not allow `$`.

The cleaner shape is:

```text
AWS Secrets Manager secret:
  name: harbor-docvault-dev-robot-token
  type: Jenkins string credential
  value: Harbor robot token only

Pipeline:
  fixed username: robot$docvault-dev+jenkins-push
  secret token: from AWS Secrets Manager
```

Example:

```groovy
withCredentials([string(credentialsId: 'harbor-docvault-dev-robot-token', variable: 'HARBOR_TOKEN')]) {
  sh '''
    echo "$HARBOR_TOKEN" | docker login harbor.docvault.id.vn \
      --username 'robot$docvault-dev+jenkins-push' \
      --password-stdin
  '''
}
```

Do not print the token.

## Certificate Rotation

Certificate rotation means replacing old certificates or keys with new ones.

For this setup:

```text
docvault-ca.pem/key:
  long-lived private CA
  used to issue Jenkins certificates

jenkins-controller.pem/key:
  shorter-lived Jenkins workload certificate
  installed on Jenkins controller
```

The Jenkins certificate was created with:

```text
-days 365
```

Before it expires:

```text
1. Use docvault-ca.key to issue a new Jenkins certificate.
2. Copy the new jenkins-controller.pem/key to /etc/jenkins/aws-rolesanywhere/.
3. Keep permissions locked down.
4. Test aws_signing_helper.
5. Restart Jenkins if needed.
```

If `jenkins-controller.key` leaks, rotate the Jenkins certificate immediately.

If `docvault-ca.key` leaks, the trust anchor itself is compromised. Create a new CA, update/recreate the IAM Roles Anywhere trust anchor, issue a new Jenkins cert, and stop trusting the old CA.

## How This Applies To Other Systems

The same pattern applies to any workload outside AWS:

```text
local CI server
on-prem GitLab runner
backup server
deployment machine
monitoring collector
private datacenter service
```

The reusable pattern is:

```text
1. Create or use a private CA.
2. Register the CA public certificate as an IAM Roles Anywhere trust anchor.
3. Issue one client certificate per workload.
4. Restrict IAM role trust policy by certificate attributes.
5. Use aws_signing_helper credential_process.
6. Let AWS SDKs consume temporary credentials automatically.
```

The certificate subject should identify the workload:

```text
CN=jenkins-controller
CN=gitlab-runner-prod
CN=backup-server-01
```

IAM role trust policies should bind the certificate identity to the allowed AWS role.

That is the security boundary: AWS trusts the CA, but IAM controls which certificate identities can assume which roles.

## Troubleshooting

### `Incorrect basic constraints for CA certificate`

The CA certificate is missing `CA:TRUE` or certificate signing key usage.

Recreate the CA certificate with:

```text
basicConstraints = critical, CA:true
keyUsage = critical, keyCertSign, cRLSign
```

### `Untrusted certificate. Insufficient certificate`

Common causes:

```text
The Jenkins cert is missing CA:FALSE.
The Jenkins cert is missing Digital Signature key usage.
The Jenkins cert was signed by a different CA than the AWS trust anchor.
The certs were recreated but not copied to /etc/jenkins/aws-rolesanywhere/.
The trust anchor still contains the old CA certificate.
```

Verify:

```bash
openssl verify \
  -CAfile /etc/jenkins/aws-rolesanywhere/docvault-ca.pem \
  /etc/jenkins/aws-rolesanywhere/jenkins-controller.pem
```

### Jenkins Does Not Show AWS Secrets Manager Credentials

Check:

```text
Jenkins AWS Secrets Manager Credentials Provider plugin is installed.
Jenkins service was restarted.
AWS_SDK_LOAD_CONFIG=1 is set for the Jenkins process.
The jenkins OS user has ~/.aws/config with credential_process.
aws sts get-caller-identity works as the jenkins OS user.
The AWS secret has the correct Jenkins credential tags.
```

### `Permission denied` Reading `jenkins-controller.key`

Fix ownership and mode:

```bash
sudo chown root:jenkins /etc/jenkins/aws-rolesanywhere/jenkins-controller.key
sudo chmod 640 /etc/jenkins/aws-rolesanywhere/jenkins-controller.key
```

Then test again as the `jenkins` user.
