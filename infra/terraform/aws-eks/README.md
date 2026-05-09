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
