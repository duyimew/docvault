# DocVault EKS - Tam dung va mo lai de tiet kiem chi phi

Updated: 2026-05-05  
Scope: Tam dung tai nguyen EKS/DocVault qua dem va mo lai de tiep tuc demo/dev.

---

## 1. Ket luan nhanh

Co 2 cach dung:

| Cach | Tiet kiem | Mat gi | Khi nao dung |
|---|---:|---|---|
| Pause node group ve 0 | Giam tien EC2 node | EKS control plane van tinh phi; pods dung; data `emptyDir` co the mat | Nghi qua dem/ngay hom sau lam tiep |
| `terraform destroy` | Tiet kiem gan nhu toan bo | Xoa cluster, Argo CD, workloads, data demo | Nghi nhieu ngay hoac ket thuc demo |

Khuyen nghi cho giai doan hien tai: **pause node group ve 0** khi nghi qua dem. Neu khong dung nua trong nhieu ngay, chay **terraform destroy**.

Luu y quan trong:

- EKS control plane van tinh phi khi chi scale node group ve 0.
- Neu Postgres/MongoDB/MinIO dang dung `emptyDir`, data demo co the mat khi pod bi terminate/reschedule.
- Argo CD cung chay tren EKS, nen khi node group = 0 thi Argo CD UI se khong truy cap duoc cho den khi mo lai node.
- Repo hien tai dang dung `ClusterIP`, chua tao LoadBalancer/Ingress, nen chua co ALB/NLB public de xoa rieng.

---

## 2. Bien moi truong

PowerShell:

```powershell
$env:AWS_REGION="ap-southeast-1"
$env:CLUSTER_NAME="docvault-eks"
$env:DOCVAULT_NS="docvault"
$env:ARGOCD_NS="argocd"
```

Bash:

```bash
export AWS_REGION=ap-southeast-1
export CLUSTER_NAME=docvault-eks
export DOCVAULT_NS=docvault
export ARGOCD_NS=argocd
```

Kiem tra dang dung dung AWS account:

```bash
aws sts get-caller-identity
```

Lay dung ten managed node group. Terraform module co the tao ten that co suffix, vi du `docvault-ng-20260505092814132600000013`, nen khong nen hardcode `docvault-ng`.

PowerShell:

```powershell
$env:NODEGROUP_NAME = aws eks list-nodegroups `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --query "nodegroups[?starts_with(@, 'docvault-ng')]|[0]" `
  --output text

Write-Host "NODEGROUP_NAME=$env:NODEGROUP_NAME"
```

Bash:

```bash
export NODEGROUP_NAME=$(
  aws eks list-nodegroups \
    --region "$AWS_REGION" \
    --cluster-name "$CLUSTER_NAME" \
    --query "nodegroups[?starts_with(@, 'docvault-ng')]|[0]" \
    --output text
)

echo "NODEGROUP_NAME=$NODEGROUP_NAME"
```

Neu dang o thu muc Terraform va state local con dung, co the lay tu output sau khi da apply/refresh output moi:

```bash
cd infra/terraform/aws-eks
terraform output -raw node_group_name
```

Neu output nay chua co trong state, dung AWS CLI `list-nodegroups` o tren hoac doc truc tiep tu state:

```bash
terraform state show 'module.eks.module.eks_managed_node_group["docvault"].aws_eks_node_group.this[0]'
```

---

## 3. Kiem tra truoc khi tam dung

Kiem tra nodes va pods:

```bash
kubectl get nodes
kubectl get pods -A
kubectl get svc -A
kubectl get ingress -A
```

Dam bao khong co Service type `LoadBalancer` hoac Ingress public neu muon tranh chi phi load balancer:

```bash
kubectl get svc -A
kubectl get ingress -A
```

Kiem tra node group hien tai:

```bash
aws eks describe-nodegroup \
  --region $AWS_REGION \
  --cluster-name $CLUSTER_NAME \
  --nodegroup-name $NODEGROUP_NAME \
  --query "nodegroup.scalingConfig"
```

PowerShell neu bien moi truong khong expand trong Git Bash style:

```powershell
aws eks describe-nodegroup `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --nodegroup-name $env:NODEGROUP_NAME `
  --query "nodegroup.scalingConfig"
```

---

## 4. Cach A - Tam dung qua dem bang cach scale node group ve 0

Day la cach nen dung de nghi qua dem va tiep tuc ngay hom sau.

### 4.1. Ghi lai trang thai hien tai

```bash
kubectl get applications -n argocd || true
kubectl get pods -n docvault -o wide || true
kubectl get pods -n argocd -o wide || true
```

Neu dang port-forward Argo CD/Gateway/Web, dung cac terminal port-forward truoc.

### 4.2. Scale managed node group ve 0

Bash:

```bash
aws eks update-nodegroup-config \
  --region $AWS_REGION \
  --cluster-name $CLUSTER_NAME \
  --nodegroup-name $NODEGROUP_NAME \
  --scaling-config minSize=0,maxSize=3,desiredSize=0
```

PowerShell:

```powershell
aws eks update-nodegroup-config `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --nodegroup-name $env:NODEGROUP_NAME `
  --scaling-config minSize=0,maxSize=3,desiredSize=0
```

Theo doi node group:

```bash
aws eks describe-nodegroup \
  --region $AWS_REGION \
  --cluster-name $CLUSTER_NAME \
  --nodegroup-name $NODEGROUP_NAME \
  --query "nodegroup.{status:status,scaling:scalingConfig}"
```

Theo doi nodes bien mat:

```bash
kubectl get nodes -w
```

Dung khi node group ve `ACTIVE` va `desiredSize` la `0`. `kubectl get nodes` co the tra ve rong.

---

## 5. Mo lai sau khi tam dung node group

### 5.1. Scale node group len lai

Khuyen nghi mo lai voi 2 node `t3.large` theo Terraform default hien tai.

Bash:

```bash
aws eks update-nodegroup-config \
  --region $AWS_REGION \
  --cluster-name $CLUSTER_NAME \
  --nodegroup-name $NODEGROUP_NAME \
  --scaling-config minSize=1,maxSize=3,desiredSize=2
```

PowerShell:

```powershell
aws eks update-nodegroup-config `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --nodegroup-name $env:NODEGROUP_NAME `
  --scaling-config minSize=1,maxSize=3,desiredSize=2
```

Cho nodes len:

```bash
kubectl get nodes -w
```

Kiem tra system pods:

```bash
kubectl get pods -n kube-system
kubectl get pods -n argocd
kubectl get pods -n docvault
```

Neu `kubectl` bi mat context:

```bash
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
```

### 5.2. Kiem tra Argo CD va app

```bash
kubectl get applications -n argocd
kubectl get all -n docvault
```

Neu app bi `OutOfSync` hoac pod chua len:

```bash
argocd app sync docvault-infra-deps

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
done
```

Neu chua dung Argo CD CLI, sync bang UI sau khi port-forward:

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

Mo:

```text
https://localhost:8080
```

### 5.3. Test health bang port-forward

Gateway:

```bash
kubectl port-forward svc/docvault-gateway -n docvault 30000:3000
```

Terminal khac:

```bash
curl -I http://localhost:30000/api/health
```

Web:

```bash
kubectl port-forward svc/docvault-web -n docvault 30006:3006
```

---

## 6. Cach B - Dung han bang terraform destroy

Dung khi khong can cluster trong nhieu ngay.

### 6.1. Xoa Argo CD Applications va workloads truoc

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
kubectl delete namespace monitoring --ignore-not-found
```

Kiem tra LoadBalancer/Ingress:

```bash
kubectl get svc -A
kubectl get ingress -A
```

Neu co Service `LoadBalancer` hoac Ingress, doi AWS xoa LB xong roi destroy.

### 6.2. Destroy Terraform

```bash
cd infra/terraform/aws-eks
terraform plan -destroy -out destroy.tfplan
terraform show destroy.tfplan
terraform apply destroy.tfplan
```

Sau khi destroy:

```bash
aws eks describe-cluster --region ap-southeast-1 --name docvault-eks
```

Lenh tren nen bao cluster khong ton tai.

---

## 7. Mo lai sau khi terraform destroy

```bash
cd infra/terraform/aws-eks
terraform init
terraform validate
terraform plan -out tfplan
terraform apply tfplan
```

Ket noi kubectl:

```bash
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes
```

Cai lai Argo CD:

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl get pods -n argocd -w
```

Neu repo private, add repo credential truoc khi sync app.

Apply Argo CD Applications:

```bash
kubectl apply -f infra/argocd-apps/docvault-infra.yaml
kubectl apply -f infra/argocd-apps/docvault-apps.yaml
```

Sync lai theo thu tu:

```bash
argocd app sync docvault-infra-deps
argocd app sync docvault-gateway
argocd app sync docvault-metadata
argocd app sync docvault-document-service
argocd app sync docvault-workflow-service
argocd app sync docvault-audit-service
argocd app sync docvault-notification-service
argocd app sync docvault-web
```

---

## 8. Checklist truoc khi nghi

- [ ] Khong con terminal `kubectl port-forward` dang chay.
- [ ] `kubectl get svc -A` khong co LoadBalancer khong mong muon.
- [ ] Neu nghi qua dem: node group da ve `desiredSize=0`.
- [ ] Neu nghi nhieu ngay: da `terraform destroy` thanh cong.
- [ ] Da ghi lai output/trang thai can demo tiep.
- [ ] Khong commit `terraform.tfvars`, `tfstate`, `tfplan`.

---

## 9. Checklist khi lam tiep

- [ ] AWS identity dung account.
- [ ] `aws eks update-kubeconfig` thanh cong.
- [ ] Nodes `Ready`.
- [ ] Argo CD pods `Running`.
- [ ] DocVault pods `Running` hoac loi ro rang.
- [ ] Gateway health pass bang port-forward.
