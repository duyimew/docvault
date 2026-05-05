# DocVault - Ke hoach trien khai EKS bang Terraform va Argo CD

Updated: 2026-05-05  
Target branch GitOps: `gitops-testing`  
Current pipeline status: Jenkins da build/push image va update `infra/k8s/values/*.yaml` thanh cong.

---

## 0. Muc tieu cua tai lieu

Tai lieu nay huong dan ban trien khai phan **CD/GitOps tren AWS EKS** theo huong chuan DevSecOps/IaC:

```text
Terraform -> AWS VPC/EKS/Node Group/IAM
kubectl/Helm -> cai Argo CD vao EKS
Argo CD -> sync DocVault tu branch gitops-testing
Jenkins -> build, scan, push Docker image, update Helm values
```

Muc tieu cuoi cung:

1. EKS cluster duoc tao bang Terraform.
2. Terraform code co the scan bang Checkov.
3. Argo CD chay tren EKS.
4. Argo CD doc repo `docvault`, branch `gitops-testing`.
5. DocVault duoc sync xuong namespace `docvault`.
6. Gateway/Web co the health check duoc.
7. Sau khi co target that, moi bat lai DAST OWASP ZAP.

---

## 1. Tai sao dung Terraform thay vi eksctl?

Dung `eksctl` nhanh va de hoc, nhung voi do an DevSecOps cua DocVault, Terraform phu hop hon vi:

- Ha tang duoc quan ly bang code, dung tinh than Infrastructure as Code.
- Co the dua vao pipeline voi cac stage `terraform fmt`, `terraform validate`, `terraform plan`, `checkov`.
- Co bang chung ro rang khi demo: ha tang duoc scan truoc khi tao/sua.
- De review thay doi ha tang bang Pull Request.
- Gan chat voi phan nghien cuu sau nay ve IaC auto-remediation.

Nguyen tac tach trach nhiem:

| Lop | Cong cu | Vai tro |
|---|---|---|
| AWS infrastructure | Terraform | Tao VPC, subnet, EKS, managed node group, IAM/OIDC |
| Kubernetes GitOps controller | Argo CD | Keo desired state tu Git xuong cluster |
| Application deployment | Argo CD + Helm/K8s manifests | Deploy DocVault |
| CI/security gates | Jenkins | Test, scan, build, push image, update GitOps branch |
| Runtime scan | ZAP | Chi bat sau khi gateway da reachable |

---

## 2. Kien truc muc tieu

```text
Developer push code
        |
        v
Jenkins Pipeline
  - install/test/build
  - SonarQube/SCA/Trivy/Checkov
  - build Docker images
  - push Docker Hub tag vXX
  - update infra/k8s/values/*.yaml on gitops-testing
        |
        v
GitHub branch: gitops-testing
        |
        v
Argo CD on EKS
  - watches repo/path/branch
  - detects OutOfSync
  - syncs Kubernetes resources
        |
        v
AWS EKS namespace: docvault
  - gateway
  - web
  - metadata-service
  - document-service
  - workflow-service
  - audit-service
  - notification-service
  - infra deps: postgres/minio/mongo/keycloak, neu chart cua ban co quan ly
```

---

## 3. Dieu kien dau vao

Truoc khi lam theo tai lieu nay, ban can co:

- Jenkins pipeline da `SUCCESS` toi stage `Push & GitOps`.
- Docker Hub da co image tag moi, vi du `v23`.
- Branch `gitops-testing` da co commit update image tag/digest.
- AWS account co quyen tao VPC, EKS, EC2, IAM, Security Group.
- May local/VM co the chay AWS CLI, Terraform, kubectl.

Kiem tra nhanh:

```bash
aws --version
terraform version
kubectl version --client
git --version
```

---

## 4. Chuan bi AWS CLI va bien moi truong

### 4.1. Login AWS CLI

```bash
aws configure
```

Nhap:

```text
AWS Access Key ID
AWS Secret Access Key
Default region name: ap-southeast-1
Default output format: json
```

Kiem tra identity:

```bash
aws sts get-caller-identity
```

Ket qua dung phai tra ve `Account`, `UserId`, `Arn`.

### 4.2. Bien moi truong khuyen nghi

```bash
export AWS_REGION=ap-southeast-1
export CLUSTER_NAME=docvault-eks
export DOCVAULT_NS=docvault
```

Neu dung PowerShell:

```powershell
$env:AWS_REGION="ap-southeast-1"
$env:CLUSTER_NAME="docvault-eks"
$env:DOCVAULT_NS="docvault"
```

---

## 5. Luu y chi phi AWS

EKS co chi phi rieng cho control plane va EC2 node. De demo do an:

- Lan dau nen dung 2 node `t3.large` de giam loi thieu RAM khi chay Argo CD, Keycloak, Postgres, MongoDB, MinIO, web, gateway va cac backend service.
- Sau khi on dinh co the giam ve `t3.medium` de tiet kiem chi phi.
- Chua dung NAT Gateway neu khong can, vi NAT Gateway co the ton chi phi.
- Chua bat ALB/Ingress public ngay tu dau.
- Test xong thi `terraform destroy` neu khong dung tiep.

Co 2 profile:

| Profile | Node subnet | NAT Gateway | Bao mat | Chi phi | Khuyen nghi |
|---|---|---:|---|---|---|
| MVP cost-saving | public subnets | khong | vua du demo | thap hon | nen dung luc moi bat dau |
| Better practice | private subnets | co | tot hon | cao hon | dung sau khi MVP on |

Tai lieu nay uu tien **MVP cost-saving** de ban nhanh co Argo CD/EKS chay that. Khi bao cao, ghi ro day la moi truong test/demo, chua phai production.

---

## 6. Cau truc thu muc Terraform de xuat

Tao thu muc:

```text
infra/terraform/aws-eks/
  .terraform.lock.hcl
  versions.tf
  providers.tf
  variables.tf
  main.tf
  outputs.tf
  terraform.tfvars.example
  README.md
```

Lenh tao nhanh:

```bash
mkdir -p infra/terraform/aws-eks
cd infra/terraform/aws-eks
```

Cay thu muc `infra` sau khi bo sung Terraform/Argo CD nen co dang:

```text
infra/
  README.md
  .env.example
  docker-compose.dev.yml
  argocd-apps/
    docvault-infra.yaml
    docvault-apps.yaml
    monitoring.yaml
  db/
    README.md
    init-postgres.sql
  keycloak/
    README.md
    realm-docvault.json
    seed-roles.sh
  minio/
    README.md
    init.sh
  k8s/
    charts/
      docvault-service/
        Chart.yaml
        values.yaml
        templates/
          deployment.yaml
          networkpolicy.yaml
          service.yaml
    infra-deps/
      README.md
      app-secrets.yaml
      keycloak.yaml
      keycloak-cm.yaml
      minio.yaml
      mongodb.yaml
      monitoring-ns.yaml
      postgres.yaml
    values/
      audit-service.yaml
      document-service.yaml
      gateway.yaml
      metadata-service.yaml
      notification-service.yaml
      web.yaml
      workflow-service.yaml
  terraform/
    aws-eks/
      .terraform.lock.hcl
      versions.tf
      providers.tf
      variables.tf
      main.tf
      outputs.tf
      terraform.tfvars.example
      README.md
```

Ghi chu:

- `argocd-apps/` chua Argo CD `Application` manifests, apply sau khi Argo CD da duoc cai trong cluster.
- `k8s/charts/docvault-service/` la Helm chart chung cho cac service DocVault.
- `k8s/values/*.yaml` la desired state Jenkins cap nhat tren branch `gitops-testing`.
- `k8s/infra-deps/` la dependency demo cho lan sync dau, gom Postgres, MongoDB, MinIO, Keycloak va secrets.
- `terraform/aws-eks/` la Terraform source tao VPC/EKS/node group/IAM lien quan.
- `.terraform/`, `terraform.tfvars`, `tfstate`, va `tfplan` la local generated files, khong commit.

---

## 7. Terraform files mau

Repo hien tai da co san cac file Terraform trong `infra/terraform/aws-eks`. Uu tien dung file trong repo lam source of truth. Cac file da duoc bo sung them mot so hardening cho demo EKS: EKS control plane logs, node metadata IMDSv2, encrypted node root volume, va bien `cluster_endpoint_public_access_cidrs` de co the gioi han CIDR truy cap API server.

Terraform cung cai `aws-ebs-csi-driver` EKS add-on va gan `AmazonEBSCSIDriverPolicy` vao node role cho MVP. Infra deps hien tai van dung `emptyDir`, nhung neu sau nay doi Postgres/MongoDB/MinIO sang PVC thi cluster da co storage driver can thiet.

### 7.1. `versions.tf`

> Ghi chu: Template nay pin module EKS `20.32.0` vi day la version on dinh, syntax pho bien va phu hop cho MVP. Sau nay co the nang len module moi hon sau khi doc upgrade guide.

```hcl
terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # MVP: local state. Khi lam nghiem tuc hon, chuyen sang S3 backend.
  # backend "s3" {
  #   bucket         = "docvault-terraform-state"
  #   key            = "eks/terraform.tfstate"
  #   region         = "ap-southeast-1"
  #   dynamodb_table = "docvault-terraform-locks"
  #   encrypt        = true
  # }
}
```

### 7.2. `providers.tf`

```hcl
provider "aws" {
  region = var.aws_region
}
```

### 7.3. `variables.tf`

```hcl
variable "aws_region" {
  description = "AWS region for EKS"
  type        = string
  default     = "ap-southeast-1"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "docvault-eks"
}

variable "cluster_version" {
  description = "Kubernetes version for EKS"
  type        = string
  default     = "1.35"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "testing"
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDR blocks allowed to access the public EKS API endpoint. Replace 0.0.0.0/0 with your workstation public IP CIDR when possible."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "EC2 instance types for EKS managed node group"
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_desired_size" {
  description = "Desired node count"
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum node count"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Maximum node count"
  type        = number
  default     = 3
}

variable "node_disk_size" {
  description = "Encrypted root volume size for each node in GiB"
  type        = number
  default     = 30
}

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway. False is cheaper for MVP when nodes are in public subnets."
  type        = bool
  default     = false
}
```

### 7.4. `main.tf`

```hcl
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = var.cluster_name

  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  tags = {
    Project     = "DocVault"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${local.name}-vpc"
  cidr = "10.20.0.0/16"

  azs             = local.azs
  public_subnets  = ["10.20.1.0/24", "10.20.2.0/24"]
  private_subnets = ["10.20.11.0/24", "10.20.12.0/24"]

  enable_nat_gateway      = var.enable_nat_gateway
  single_nat_gateway      = true
  map_public_ip_on_launch = true

  enable_dns_hostnames = true
  enable_dns_support   = true

  manage_default_security_group  = true
  default_security_group_ingress = []
  default_security_group_egress  = []

  public_subnet_tags = {
    "kubernetes.io/role/elb" = 1
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = 1
  }

  tags = local.tags
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "20.32.0"

  cluster_name    = local.name
  cluster_version = var.cluster_version

  # Cho phep kubectl/Argo CD truy cap API server tu Internet trong moi truong demo.
  # Truoc khi apply, nen doi CIDR thanh public IP cua may thao tac, vi du x.x.x.x/32.
  cluster_endpoint_public_access       = true
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs

  # De IAM principal tao cluster co quyen admin tren cluster ngay tu dau.
  enable_cluster_creator_admin_permissions = true

  cluster_enabled_log_types = [
    "api",
    "audit",
    "authenticator",
    "controllerManager",
    "scheduler",
  ]

  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
    aws-ebs-csi-driver = {
      most_recent = true
    }
  }

  vpc_id     = module.vpc.vpc_id
  subnet_ids = concat(module.vpc.public_subnets, module.vpc.private_subnets)

  # MVP cost-saving: node group dat tren public subnets de khong can NAT Gateway.
  # Neu chuyen sang private node, doi thanh module.vpc.private_subnets va bat enable_nat_gateway=true.
  eks_managed_node_groups = {
    docvault = {
      name = "docvault-ng"

      subnet_ids     = var.enable_nat_gateway ? module.vpc.private_subnets : module.vpc.public_subnets
      instance_types = var.node_instance_types
      capacity_type  = "ON_DEMAND"
      ami_type       = "AL2023_x86_64_STANDARD"

      min_size     = var.node_min_size
      max_size     = var.node_max_size
      desired_size = var.node_desired_size

      use_custom_launch_template = true

      iam_role_additional_policies = {
        AmazonEBSCSIDriverPolicy = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
      }

      metadata_options = {
        http_endpoint               = "enabled"
        http_tokens                 = "required"
        http_put_response_hop_limit = 2
      }

      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = var.node_disk_size
            volume_type           = "gp3"
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = {
        workload = "docvault"
      }
    }
  }

  tags = local.tags
}
```

### 7.5. `outputs.tf`

```hcl
output "cluster_name" {
  value = module.eks.cluster_name
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "cluster_security_group_id" {
  value = module.eks.cluster_security_group_id
}

output "region" {
  value = var.aws_region
}

output "configure_kubectl" {
  value = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}
```

### 7.6. `terraform.tfvars.example`

```hcl
aws_region      = "ap-southeast-1"
cluster_name    = "docvault-eks"
cluster_version = "1.35"
environment     = "testing"

# Nen thay 0.0.0.0/0 bang public IP CIDR cua may thao tac, vi du ["203.0.113.10/32"].
cluster_endpoint_public_access_cidrs = ["0.0.0.0/0"]

node_instance_types = ["t3.large"]
node_desired_size   = 2
node_min_size       = 1
node_max_size       = 3
node_disk_size      = 30

# false = tiet kiem chi phi, node nam public subnet
# true = can NAT Gateway neu node nam private subnet
enable_nat_gateway = false
```

Copy thanh file that:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Khong commit `terraform.tfvars` neu co thong tin nhay cam.

---

## 8. Chay Terraform local lan dau

### 8.1. Pre-flight truoc khi apply

Kiem tra AWS identity:

```bash
aws sts get-caller-identity
```

Kiem tra version EKS kha dung trong region. Tai thoi diem 2026-05-05, EKS standard support gom `1.35`, `1.34`, `1.33`; `1.32`, `1.31`, `1.30` dang o extended support. Van nen kiem tra lai region cua ban truoc khi apply:

```bash
aws eks describe-cluster-versions --region ap-southeast-1
```

Neu `cluster_endpoint_public_access_cidrs` dang la `0.0.0.0/0`, can nhac doi thanh public IP CIDR cua may thao tac truoc khi apply.

Review secrets truoc khi deploy len AWS:

```bash
grep -R "password\|secret\|token\|key" infra/k8s/infra-deps infra/k8s/values
```

Dam bao khong co AWS key, production password, PAT/token that trong repo. Cac secret demo co the chap nhan cho moi truong test rieng tu.

### 8.2. Terraform quality va plan

Tu thu muc `infra/terraform/aws-eks`:

```bash
cd infra/terraform/aws-eks
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out tfplan
checkov -d .
terraform show tfplan
```

Neu plan dung, apply:

```bash
terraform apply tfplan
```

Qua trinh tao EKS co the mat 15-25 phut.

Kiem tra output:

```bash
terraform output
```

Sau khi apply:

```bash
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes -o wide
kubectl get sc
kubectl get pods -A
```

---

## 9. Ket noi kubectl vao EKS

Sau khi Terraform apply xong:

```bash
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl config current-context
kubectl get nodes
```

Ket qua dung:

```text
NAME                                          STATUS   ROLES    AGE   VERSION
ip-10-20-...ap-southeast-1.compute.internal   Ready    <none>   ...   v1.xx.x
```

Neu `kubectl get nodes` bao unauthorized:

```bash
aws sts get-caller-identity
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl config current-context
```

---

## 10. Scan Terraform bang Checkov

Chay local truoc:

```bash
checkov -d infra/terraform/aws-eks
```

Neu chua cai Checkov:

```bash
pip install checkov
```

Muc tieu MVP:

- Checkov chay duoc tren Terraform directory.
- Loi nao thuc su nguy hiem thi sua.
- Loi nao chap nhan cho demo thi ghi ro exception trong bao cao, khong dung `checkov:skip` tuy tien.

Trong Jenkins, stage Checkov nen scan ca:

```text
infra/terraform/aws-eks
infra/k8s
```

---

## 11. Cai Argo CD vao EKS

### 11.1. Tao namespace va install

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Cho pod len:

```bash
kubectl get pods -n argocd -w
```

Kiem tra rollout:

```bash
kubectl rollout status deployment/argocd-server -n argocd
kubectl rollout status deployment/argocd-repo-server -n argocd
kubectl rollout status deployment/argocd-applicationset-controller -n argocd
```

### 11.2. Truy cap UI bang port-forward

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

Mo browser:

```text
https://localhost:8080
```

Lan dau co the bi certificate warning. Chon continue.

### 11.3. Lay mat khau admin ban dau

Neu co Argo CD CLI:

```bash
argocd admin initial-password -n argocd
```

Neu chua co CLI:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

Login:

```text
Username: admin
Password: <initial-password>
```

Doi password sau khi login. Sau khi doi password, co the xoa secret initial:

```bash
kubectl -n argocd delete secret argocd-initial-admin-secret
```

---

## 12. Xac dinh path GitOps cua repo DocVault

Repo hien tai da co san Argo CD Application manifests trong:

```text
infra/argocd-apps/
```

Khong tao them `infra/argocd/docvault-app.yaml` voi `path: infra/k8s`, vi `infra/k8s` dang chua ca chart, values va raw manifests. Neu tro Argo CD truc tiep vao path nay, Argo CD co the render sai cau truc mong muon.

Tu root repo, kiem tra:

```bash
find infra/k8s -name Chart.yaml
find infra/argocd-apps -iname "*.yaml"
find infra/k8s -maxdepth 3 -type f | sort
```

Cau truc dung cua repo nay:

| Thanh phan | Path Argo CD dang dung |
|---|---|
| Infra deps | `infra/k8s/infra-deps` |
| DocVault services | `infra/k8s/charts/docvault-service` + tung `infra/k8s/values/*.yaml` |
| Argo CD Applications | `infra/argocd-apps/*.yaml` |

Kiem tra Helm chart:

```bash
helm template docvault-gateway infra/k8s/charts/docvault-service \
  -n docvault \
  -f infra/k8s/values/gateway.yaml
```

Neu render duoc YAML, Argo CD co the sync chart nay.

---

## 13. Apply Argo CD Applications cho DocVault

Dung cac manifest co san trong `infra/argocd-apps`.

Neu repo GitHub la private, hay lam muc 14 de add repo credential truoc khi apply/sync cac Application. Neu khong, Argo CD co the tao Application nhung khong fetch duoc source.

Apply infra deps truoc:

```bash
kubectl apply -f infra/argocd-apps/docvault-infra.yaml
kubectl get applications -n argocd
```

Sau do apply cac service DocVault:

```bash
kubectl apply -f infra/argocd-apps/docvault-apps.yaml
kubectl get applications -n argocd
kubectl describe application docvault-gateway -n argocd
```

Neu muon monitoring sau khi app on dinh:

```bash
kubectl apply -f infra/argocd-apps/monitoring.yaml
```

Khuyen nghi: lan dau de manual sync. Cac manifest Argo CD hien khong bat `automated`, khong bat `prune`.

---

## 14. Neu repo GitHub private

Neu repo private, Argo CD can credential de clone repo.

### Cach UI

Argo CD UI -> Settings -> Repositories -> Connect Repo

Nhap:

```text
Type: git
Project: default
Repository URL: https://github.com/daithang59/docvault.git
Username: <github-user>
Password: <github-pat>
```

### Cach CLI

```bash
argocd login localhost:8080 --username admin --password '<password>' --insecure
argocd repo add https://github.com/daithang59/docvault.git \
  --username <github-user> \
  --password <github-pat>
```

---

## 15. Sync DocVault lan dau

### 15.1. Pre-flight truoc khi sync

Kiem tra Argo CD:

```bash
kubectl get pods -n argocd
argocd repo list
```

Kiem tra branch GitOps va image refs:

```bash
git checkout gitops-testing
git pull
git log -1
grep -R 'tag: "v' infra/k8s/values
grep -R 'digest: "sha256:' infra/k8s/values || true
```

Kiem tra Helm render:

```bash
helm template docvault-gateway infra/k8s/charts/docvault-service \
  -n docvault \
  -f infra/k8s/values/gateway.yaml
```

### 15.2. Sync

Trong UI:

1. Sync app `docvault-infra-deps` truoc.
2. Sync cac app service: `docvault-gateway`, `docvault-metadata`, `docvault-document-service`, `docvault-workflow-service`, `docvault-audit-service`, `docvault-notification-service`, `docvault-web`.
3. Neu status `OutOfSync`, bam `Sync`.
4. Chon `Synchronize`.
5. Theo doi resource tree.

Bang CLI:

```bash
argocd app sync docvault-infra-deps
argocd app wait docvault-infra-deps --health --timeout 300

for app in \
  docvault-gateway \
  docvault-metadata \
  docvault-document-service \
  docvault-workflow-service \
  docvault-audit-service \
  docvault-notification-service \
  docvault-web
do
  argocd app sync "$app"
  argocd app wait "$app" --health --timeout 300
done
```

Neu sync bang UI thi sync infra deps truoc, roi sync cac service.

Kiem tra Kubernetes:

```bash
kubectl get all -n docvault
kubectl get pods -n docvault
kubectl get svc -n docvault
kubectl get ingress -n docvault
```

---

## 16. Debug loi sync/deploy thuong gap

### 16.1. Application OutOfSync

Binh thuong. Bam Sync hoac:

```bash
argocd app sync <application-name>
```

### 16.2. Application Degraded

Xem resource nao bi do trong UI, sau do:

```bash
kubectl describe <kind> <name> -n docvault
```

### 16.3. Pod Pending

```bash
kubectl describe pod <pod-name> -n docvault
kubectl get nodes -o wide
kubectl describe node <node-name>
```

`kubectl top nodes` chi dung duoc sau khi cluster co metrics-server hoac monitoring tuong duong. Neu can metrics-server cho debug:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

Nguyen nhan hay gap:

- Node qua nho.
- Resource requests qua cao.
- PVC chua bind.

### 16.4. ImagePullBackOff

```bash
kubectl describe pod <pod-name> -n docvault
```

Tim cac loi:

```text
unauthorized
manifest unknown
pull access denied
```

Neu image private, tao Docker Hub pull secret:

```bash
kubectl create secret docker-registry dockerhub-secret \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=<docker-user> \
  --docker-password=<docker-pat> \
  --docker-email=<email> \
  -n docvault
```

Sau do Helm values can co:

```yaml
imagePullSecrets:
  - name: dockerhub-secret
```

### 16.5. CrashLoopBackOff

```bash
kubectl logs <pod-name> -n docvault --tail=100
kubectl logs <pod-name> -n docvault --previous --tail=100
kubectl describe pod <pod-name> -n docvault
```

Nguyen nhan hay gap voi DocVault:

- Thieu `DATABASE_URL`.
- Thieu `MONGO_URI`.
- Sai MinIO endpoint.
- Sai Keycloak issuer/audience.
- Service URL noi bo chua dung.
- Health path trong chart khong khop service.

### 16.6. Service khong truy cap duoc

Kiem tra service:

```bash
kubectl get svc -n docvault
kubectl describe svc <service-name> -n docvault
```

Test bang port-forward truoc:

```bash
kubectl port-forward svc/<gateway-service-name> -n docvault 30000:<service-port>
curl http://localhost:30000/api/health
```

---

## 17. Expose Gateway/Web tren EKS

Lam theo thu tu tu de den kho.

### Phuong an 1: Port-forward de test truoc

```bash
kubectl port-forward svc/<gateway-service-name> -n docvault 30000:<service-port>
curl http://localhost:30000/api/health
```

Day la cach nen dung dau tien.

### Phuong an 2: Service type LoadBalancer

Neu chart ho tro:

```yaml
service:
  type: LoadBalancer
```

Sau sync:

```bash
kubectl get svc -n docvault
```

Cho den khi co `EXTERNAL-IP`.

### Phuong an 3: Ingress + AWS Load Balancer Controller

Chi lam sau khi app da healthy bang port-forward/LoadBalancer.

Can them:

- AWS Load Balancer Controller
- IAM policy/role cho controller
- Ingress annotations
- ACM certificate neu dung HTTPS

Phan nay de sau, khong nen lam ngay lan sync dau.

---

## 18. Bat lai DAST OWASP ZAP sau khi co target that

Chi bat ZAP khi gateway URL reachable tu Jenkins container.

Tren host Jenkins:

```bash
docker exec -it jenkins-blueocean sh
```

Trong container:

```bash
curl -I --max-time 10 http://<gateway-url>/api
curl -I --max-time 10 http://<gateway-url>/api/health
```

Neu curl duoc, moi bat pipeline parameter:

```text
RUN_ZAP=true
ZAP_TARGET=http://<gateway-url>/api
```

Neu curl timeout, dung sua ZAP. Hay sua network/expose target truoc.

---

## 19. Tich hop Terraform vao Jenkins pipeline

### 19.1. MVP an toan

Trong giai doan dau, **khong nen de Jenkins auto apply Terraform**.

Jenkins chi nen chay:

```bash
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
checkov -d infra/terraform/aws-eks
```

Terraform `apply` chay manual tren may local/VM cua ban de tranh Jenkins tao/xoa cloud resource ngoai y muon.

### 19.2. Sau khi on dinh

Co the them stage:

```text
Terraform Plan -> Manual Approval -> Terraform Apply
```

Chi chay tren branch duoc phep, vi du `main` hoac `infra`.

### 19.3. Jenkins stage mau

```groovy
stage('IaC - Terraform Validate') {
  steps {
    sh '''
      set -eu
      cd infra/terraform/aws-eks
      terraform fmt -check -recursive
      terraform init -backend=false
      terraform validate
    '''
  }
}

stage('IaC - Checkov Terraform') {
  steps {
    sh '''
      set -eu
      checkov -d infra/terraform/aws-eks
    '''
  }
}
```

---

## 20. Remote state S3 backend - lam sau MVP

Khi lam nghiem tuc hon, khong nen de local state. Chuyen sang:

- S3 bucket de luu state
- DynamoDB table de lock
- encryption enabled

Vi du backend:

```hcl
backend "s3" {
  bucket         = "docvault-terraform-state-<account-id>"
  key            = "eks/terraform.tfstate"
  region         = "ap-southeast-1"
  dynamodb_table = "docvault-terraform-locks"
  encrypt        = true
}
```

Khuyen nghi lam remote state sau khi ban da apply thanh cong local lan dau.

---

## 21. Checklist thuc hien theo ngay

### Ngay 1: Terraform EKS

- [ ] Cai AWS CLI, Terraform, kubectl.
- [ ] `aws sts get-caller-identity` thanh cong.
- [ ] Tao `infra/terraform/aws-eks`.
- [ ] Them `versions.tf`, `providers.tf`, `variables.tf`, `main.tf`, `outputs.tf`.
- [ ] Kiem tra EKS version bang `aws eks describe-cluster-versions --region ap-southeast-1`.
- [ ] Review `cluster_endpoint_public_access_cidrs` va secrets demo truoc khi apply.
- [ ] Chay `terraform init`.
- [ ] Chay `terraform validate`.
- [ ] Chay `terraform plan`.
- [ ] Chay `checkov -d infra/terraform/aws-eks`.
- [ ] Chay `terraform apply`.
- [ ] `kubectl get nodes` thay node `Ready`.

### Ngay 2: Argo CD

- [ ] Tao namespace `argocd`.
- [ ] Cai Argo CD manifest.
- [ ] Tat ca pod Argo CD `Running`.
- [ ] Port-forward UI.
- [ ] Login admin.
- [ ] Doi password admin.
- [ ] Neu repo private, add repo credential truoc khi apply/sync Application.

### Ngay 3: Argo CD Application

- [ ] Xac dinh chart bang `find infra/k8s -name Chart.yaml`.
- [ ] Xac nhan manifest co san trong `infra/argocd-apps`.
- [ ] Chay Helm render pre-flight cho gateway.
- [ ] Apply `infra/argocd-apps/docvault-infra.yaml`.
- [ ] Apply `infra/argocd-apps/docvault-apps.yaml`.
- [ ] Cac Applications DocVault xuat hien trong Argo CD UI.
- [ ] Sync `docvault-infra-deps` truoc, sau do sync cac service app.
- [ ] Ghi lai loi neu co.

### Ngay 4: Fix runtime

- [ ] `kubectl get all -n docvault`.
- [ ] Sua `ImagePullBackOff` neu co.
- [ ] Sua `CrashLoopBackOff` neu co.
- [ ] Port-forward gateway/web.
- [ ] Health endpoint pass.

### Ngay 5: Evidence va DAST

- [ ] Chup anh Argo CD `Synced/Healthy`.
- [ ] Chup anh Jenkins build `SUCCESS`.
- [ ] Chup commit `gitops-testing`.
- [ ] Test gateway reachable tu Jenkins container.
- [ ] Bat `RUN_ZAP=true` neu gateway reachable.
- [ ] Luu ZAP report.

---

## 22. Checklist thanh cong cho milestone nay

Milestone nay duoc xem la xong khi:

- [ ] Terraform tao duoc EKS cluster.
- [ ] `kubectl get nodes` co node `Ready`.
- [ ] Argo CD pod `Running`.
- [ ] Vao duoc Argo CD UI.
- [ ] Applications `docvault-infra-deps` va cac DocVault services tro dung repo `docvault`, branch `gitops-testing`.
- [ ] Argo CD sync duoc it nhat mot lan cho infra deps va cac service app.
- [ ] Namespace `docvault` co resource duoc tao.
- [ ] Gateway/Web health pass hoac co loi runtime cu the de fix.
- [ ] Co evidence: Terraform output, Argo CD UI, kubectl output, Jenkins GitOps commit.

---

## 23. Lenh huy de tranh ton chi phi

Khi khong dung nua:

```bash
cd infra/terraform/aws-eks
terraform destroy
```

Truoc khi destroy, dam bao khong con resource LoadBalancer bi ket:

```bash
kubectl get svc -A
kubectl get ingress -A
```

Neu co Service type LoadBalancer/Ingress, xoa app truoc:

```bash
kubectl delete application \
  docvault-infra-deps \
  docvault-gateway \
  docvault-metadata \
  docvault-document-service \
  docvault-workflow-service \
  docvault-audit-service \
  docvault-notification-service \
  docvault-web \
  monitoring-stack \
  -n argocd \
  --ignore-not-found
kubectl delete namespace docvault --ignore-not-found
```

Sau do moi `terraform destroy`.

---

## 24. Tai lieu tham khao chinh thuc

- HashiCorp Terraform EKS tutorial: https://developer.hashicorp.com/terraform/tutorials/kubernetes/eks
- Terraform AWS EKS module: https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest
- Amazon EKS Kubernetes version lifecycle: https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-versions.html
- Amazon EKS platform versions: https://docs.aws.amazon.com/eks/latest/userguide/platform-versions.html
- Amazon EBS CSI driver: https://docs.aws.amazon.com/eks/latest/userguide/ebs-csi.html
- AWS CLI `update-kubeconfig`: https://docs.aws.amazon.com/cli/latest/reference/eks/update-kubeconfig.html
- AWS EKS kubeconfig guide: https://docs.aws.amazon.com/eks/latest/userguide/create-kubeconfig.html
- Argo CD Getting Started: https://argo-cd.readthedocs.io/en/stable/getting_started/
- Argo CD project overview: https://argoproj.github.io/cd/

---

## 25. Ghi chu quan trong cho bao cao do an

Khi viet bao cao, nen mo ta ro:

1. Terraform quan ly ha tang AWS EKS.
2. Checkov scan Terraform truoc khi tao/sua ha tang.
3. Jenkins khong deploy truc tiep vao cluster.
4. Jenkins chi update desired state tren branch `gitops-testing`.
5. Argo CD la thanh phan CD/GitOps keo state tu Git xuong EKS.
6. DAST chi chay sau khi moi truong deployed co gateway URL that.
7. Moi action quan trong co evidence: Jenkins log, Git commit, Argo CD sync, kubectl output.

Day la narrative rat dung voi DevSecOps/GitOps va de bao ve truoc hoi dong.
