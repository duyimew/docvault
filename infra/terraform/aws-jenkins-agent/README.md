# DocVault Jenkins Agent Only on AWS

This stack creates only an AWS EC2 Jenkins build agent. Use it when your Jenkins
controller stays local, for example in VMware on your laptop, and connects to the
cloud agent over SSH.

Target architecture:

```text
Local VMware Jenkins controller
  -> outbound SSH
AWS EC2 Jenkins agent
```

The agent is prepared for the current DocVault pipeline:

- Java 21
- Docker Engine and Buildx
- Git
- kubectl
- AWS CLI
- `jenkins` Linux user
- remote root directory `/home/jenkins/agent`
- required Jenkins label `docker-agent-alpine-ubuntu-vm`

## Quick Start

```bash
cd infra/terraform/aws-jenkins-agent
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and set `controller_cidr_blocks` to the public IPs or
CIDR ranges used by your local Jenkins controller VM. If your current public IP
is `14.169.77.199`, exact access is:

```hcl
controller_cidr_blocks = ["14.169.77.199/32"]
```

If your ISP rotates inside a `/24`, use:

```hcl
controller_cidr_blocks = ["14.169.77.0/24"]
```

Apply:

```bash
terraform init
terraform fmt -recursive
terraform validate
terraform plan -out tfplan
terraform apply tfplan
```

Save the Jenkins agent SSH private key:

```bash
terraform output -raw jenkins_agent_ssh_private_key_pem > jenkins-agent-key.pem
chmod 600 jenkins-agent-key.pem
```

## Configure the Local Jenkins Controller

In your local Jenkins UI:

1. Go to `Manage Jenkins -> Credentials -> System -> Global credentials`.
2. Add `SSH username with private key`.
3. Username: `jenkins`.
4. Private key: paste `jenkins-agent-key.pem`.
5. ID: `aws-jenkins-agent-ssh`.

Then create the node:

```text
Manage Jenkins -> Nodes -> New Node
Node name: docvault-aws-agent
Type: Permanent Agent
Remote root directory: /home/jenkins/agent
Labels: docker-agent-alpine-ubuntu-vm
Usage: Only build jobs with label expressions matching this node
Launch method: Launch agents via SSH
Host: <terraform output -raw agent_public_ip>
Credentials: aws-jenkins-agent-ssh
Host Key Verification Strategy: Manually trusted key, or Non verifying for a quick lab
```

The repository `Jenkinsfile` already uses:

```groovy
agent { label 'docker-agent-alpine-ubuntu-vm' }
```

## Debug Access

If you need admin SSH and Terraform generated the admin key:

```bash
terraform output -raw admin_ssh_private_key_pem > docvault-jenkins-agent-admin.pem
chmod 600 docvault-jenkins-agent-admin.pem
terraform output -raw admin_ssh_command
```

Run the printed SSH command.

If SSH is blocked or you prefer AWS Systems Manager:

```bash
terraform output -raw agent_ssm_session_command
```

## Security Notes

- Keep `controller_cidr_blocks` as narrow as your changing IP allows.
- `/24` is much better than `0.0.0.0/0`, but less strict than `/32`.
- Generated private keys are stored in Terraform state. Treat the state file as secret material.
- The Jenkins controller connects to the agent. The agent does not need to reach your local Jenkins controller.
