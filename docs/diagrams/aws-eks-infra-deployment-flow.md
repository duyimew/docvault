# Luong trien khai ha tang AWS/EKS bang Terraform cho DocVault

So do nay mo ta quy trinh tao/cap nhat ha tang AWS/EKS bang Terraform. Cach ve theo dang pipeline 6 buoc: thay doi ha tang di qua PR, Jenkins chay validation, Terraform tao AWS foundation, sau do EKS nhan lop platform va namespace ung dung.

## So do Mermaid

```mermaid
flowchart TB
  NoManual["No manual infrastructure changes<br/>Khong sua truc tiep tren AWS Console"]
  GateNote["Terraform plan + Checkov scan<br/>phai pass truoc khi apply"]
  Fail["Fail / Fix required<br/>Block apply"]

  S1["1. Infra Change / PR<br/>Developer sua Terraform/Kubernetes/GitOps<br/>Review infrastructure change<br/>No manual AWS changes"]
  S2["2. Jenkins IaC Workflow<br/>Trigger infra validation<br/>Prepare Terraform environment<br/>Use controlled CI runner"]

  subgraph Gate["3. Terraform + Security Gate"]
    direction TB
    TF["Terraform<br/>fmt<br/>init<br/>validate<br/>plan<br/>Checkov IaC scan<br/>manual approval<br/>apply"]
  end

  S4["4. AWS Foundation<br/>VPC<br/>EKS<br/>IAM / OIDC / IRSA<br/>Secrets Manager<br/>S3 + KMS<br/>EBS CSI"]
  S5["5. Kubernetes Platform Layer<br/>Argo CD<br/>External Secrets Operator<br/>cert-manager<br/>ingress-nginx<br/>Kyverno<br/>Monitoring / Loki"]
  S6["6. DocVault Runtime Namespace<br/>namespace: docvault<br/>web<br/>gateway<br/>backend services<br/>Keycloak<br/>PostgreSQL / MongoDB"]

  NoManual -.-> S1
  S1 --> S2 --> Gate --> S4 --> S5 --> S6
  GateNote -.-> Gate
  Gate -.->|fmt / validate / plan / Checkov failed| Fail
```

## Ket qua sau khi Terraform apply

- VPC va subnet san sang cho EKS va load balancer.
- EKS cluster `docvault-eks` va managed node group `docvault-ng` duoc tao.
- EKS add-ons co san: CoreDNS, kube-proxy, VPC CNI, AWS EBS CSI driver.
- IAM/OIDC/IRSA roles san sang cho External Secrets Operator, document-service va cac backup workloads.
- S3/KMS resources san sang cho document storage va database backup.
- Operator co the chay `aws eks update-kubeconfig` roi bootstrap Argo CD/GitOps.

## Ghi chu theo repo hien tai

- Terraform root stack: `infra/terraform/aws-eks`.
- EKS cluster mac dinh: `docvault-eks`.
- Node group: `docvault-ng`, instance type mac dinh `t3.large`, min/desired/max `1/2/3`.
- VPC CIDR mac dinh: `10.20.0.0/16`.

