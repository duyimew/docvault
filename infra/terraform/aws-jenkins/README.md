# DocVault Jenkins Controller and Agent on AWS

This stack creates the quick AWS equivalent of the previous VMware setup:

- one Jenkins controller EC2 instance;
- one separate SSH-based Jenkins build agent EC2 instance;
- a security group path for the controller to SSH into the agent;
- the Jenkins agent user, Docker, Git, Java 21, kubectl and AWS CLI on the agent;
- an SSH key pair for the Jenkins controller to use when launching the agent.

The repo pipeline currently requires the agent label:

```text
docker-agent-alpine-ubuntu-vm
```

## Quick Start

```bash
cd infra/terraform/aws-jenkins
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` before apply:

- if your public IP is stable, set `admin_cidr_blocks` to your public IP CIDR, for example `["203.0.113.10/32"]`;
- if your public IP changes often, keep `jenkins_ui_cidr_blocks = []` and `admin_ssh_cidr_blocks = []`, then use SSM port forwarding;
- optionally set `ssh_key_name` to an existing EC2 key pair for admin SSH. If you omit it, Terraform generates one;
- keep `agent_instance_type = "t3.large"` for DocVault Docker image builds unless cost is more important than speed.

Apply:

```bash
terraform init
terraform fmt -recursive
terraform validate
terraform plan -out tfplan
terraform apply tfplan
```

Open Jenkins:

```bash
terraform output -raw controller_url
```

If `jenkins_ui_cidr_blocks = []`, the public URL will not open directly. Use SSM
port forwarding instead, then browse `http://127.0.0.1:8080`:

```bash
terraform output -raw controller_ssm_port_forward_command
```

If you did not set `ssh_key_name`, save the generated admin SSH key:

```bash
terraform output -raw admin_ssh_private_key_pem > docvault-jenkins-admin.pem
chmod 600 docvault-jenkins-admin.pem
```

Get the Jenkins initial admin password when SSH is allowed:

```bash
terraform output -raw controller_initial_password_command
```

Run the printed SSH command to get the initial admin password.

If `admin_ssh_cidr_blocks = []`, use an SSM shell instead:

```bash
terraform output -raw controller_ssm_session_command
```

Run the printed command, then inside the controller session:

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

If your EKS API endpoint or NodePort security groups are CIDR-restricted, add
the Jenkins agent CIDR to the allowlist because the pipeline runs from the
agent:

```bash
terraform output -raw agent_public_cidr
```

## Optional Cloudflare Tunnel

If your public IP changes often and you still want a public Jenkins URL, use a
Cloudflare Tunnel with Cloudflare Access in front of it.

Recommended security group settings:

```hcl
jenkins_ui_cidr_blocks      = []
admin_ssh_cidr_blocks       = []
create_ssm_instance_profile = true
```

Cloudflare setup:

1. In Cloudflare Zero Trust, create a tunnel.
2. Add a public hostname such as `jenkins.example.com`.
3. Route the hostname to:

```text
http://localhost:8080
```

4. Copy the connector token and pass it to Terraform without committing it:

```bash
export TF_VAR_cloudflared_tunnel_token='<cloudflare-connector-token>'
terraform apply tfplan
```

The controller installs `cloudflared` and registers the connector during
bootstrap. Keep Cloudflare Access enabled for this hostname; publishing Jenkins
without Access is not recommended.

## Configure the Jenkins Agent

Get the private key Terraform generated for the Jenkins agent credential:

```bash
terraform output -raw jenkins_agent_ssh_private_key_pem > jenkins-agent-key.pem
chmod 600 jenkins-agent-key.pem
```

In Jenkins:

1. Go to `Manage Jenkins -> Credentials -> System -> Global credentials`.
2. Add `SSH username with private key`.
3. Username: `jenkins`.
4. Private key: paste `jenkins-agent-key.pem`.
5. ID: `jenkins-agent-ssh`.

Then create the node:

```text
Manage Jenkins -> Nodes -> New Node
Node name: docvault-aws-agent
Type: Permanent Agent
Remote root directory: /home/jenkins/agent
Labels: docker-agent-alpine-ubuntu-vm
Usage: Only build jobs with label expressions matching this node
Launch method: Launch agents via SSH
Host: <terraform output agent_private_ip>
Credentials: jenkins-agent-ssh
Host Key Verification Strategy: Manually trusted key, or Non verifying for a quick lab
```

Set controller executors to `0`:

```text
Manage Jenkins -> Nodes -> Built-In Node -> Configure -> Number of executors: 0
```

## Configure the DocVault Pipeline

Follow the existing repo docs after the controller is reachable:

- `docs/TEAM_SETUP_DEPLOYMENT_GUIDE.md`
- `docs/DEVSECOPS_PIPELINE_SETUP_GUIDE.md`
- `docs/setup_jenkins_k8s_account.md`

Minimum Jenkins pieces for this repo:

- plugins: Pipeline, Git, GitHub Branch Source, Credentials Binding, SSH Build Agents, Docker Pipeline, Workspace Cleanup, SonarQube Scanner, Timestamper;
- credentials: `dockerhub-credentials`, `github-credentials`, and optionally `jenkins-argocd-kubeconfig`;
- shared library: name `docvault`, default version `devsecops-pipeline`;
- pipeline SCM branch: `*/devsecops-pipeline`;
- script path: `Jenkinsfile`.

The agent already has Docker, Git and kubectl. The pipeline's first environment check runs:

```bash
docker --version
```

## EKS and Argo CD Health Check

For `RUN_ARGO_HEALTH_CHECK=true`, keep using the safer read-only kubeconfig flow:

```text
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
```

That credential is documented in `docs/setup_jenkins_k8s_account.md`. Do not use an admin kubeconfig for Jenkins.

## Notes

- Generated private keys are stored in Terraform state. For stronger security, use an existing EC2 key pair via `ssh_key_name`, and generate the Jenkins agent key outside Terraform by passing `jenkins_agent_public_key`.
- `cloudflared_tunnel_token` is sensitive, but Terraform still stores values needed to render EC2 user data in state. Treat Terraform state as secret material.
- `admin_cidr_blocks = ["0.0.0.0/0"]` is convenient but not safe. Use your own IP CIDR for SSH and Jenkins UI.
- The controller URL is HTTP on port `8080` for speed. Put it behind TLS or a VPN before treating it as anything beyond a lab/demo environment.
