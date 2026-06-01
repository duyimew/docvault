# DocVault AWS EKS Terraform

This directory creates the MVP AWS foundation for the DocVault GitOps demo:

- VPC with public and private subnets across two AZs.
- EKS cluster with managed node group.
- EKS core add-ons: CoreDNS, kube-proxy, VPC CNI, and AWS EBS CSI driver.
- Cluster creator admin access for first-time bootstrap.
- EKS control plane logs, IMDSv2 on nodes, and encrypted node root volumes.

## Usage

```bash
cd infra/terraform/aws-eks
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -recursive
terraform validate
terraform plan -out tfplan
terraform apply tfplan
```

Optional local scan:

```bash
checkov -d infra/terraform/aws-eks
```

Configure kubectl after apply:

```bash
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes
```

The default profile keeps nodes in public subnets to avoid NAT Gateway cost during the MVP demo. Set `enable_nat_gateway = true` to place nodes in private subnets.

Before apply, check the EKS versions available in your region and narrow `cluster_endpoint_public_access_cidrs` in `terraform.tfvars` when possible:

```bash
aws eks describe-cluster-versions --region ap-southeast-1
```

Do not commit `terraform.tfvars`, local state, or plan files.

## AWS credentials on Windows

If `terraform plan` fails with:

```text
Error: No valid credential sources found
failed to refresh cached credentials, no EC2 IMDS role found
```

Terraform cannot find AWS credentials in your local shell. The EC2 metadata error is only the provider's final fallback; it does not mean you need EC2.

Use one of these local authentication methods.

### Option A: IAM access key profile

```powershell
aws configure --profile docvault
$env:AWS_PROFILE = "docvault"
$env:AWS_REGION = "ap-southeast-1"
aws sts get-caller-identity
terraform plan -out tfplan
```

### Option B: AWS IAM Identity Center / SSO profile

```powershell
aws configure sso --profile docvault-sso
aws sso login --profile docvault-sso
$env:AWS_PROFILE = "docvault-sso"
$env:AWS_REGION = "ap-southeast-1"
aws sts get-caller-identity
terraform plan -out tfplan
```

### Option C: temporary session credentials

```powershell
$env:AWS_ACCESS_KEY_ID = "<access-key-id>"
$env:AWS_SECRET_ACCESS_KEY = "<secret-access-key>"
$env:AWS_SESSION_TOKEN = "<session-token-if-any>"
$env:AWS_REGION = "ap-southeast-1"
aws sts get-caller-identity
terraform plan -out tfplan
```

Never commit credentials or write them into Terraform files. Prefer `AWS_PROFILE` for repeatable local work.
