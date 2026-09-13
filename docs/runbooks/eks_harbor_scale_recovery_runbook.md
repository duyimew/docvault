# EKS Harbor Scale Recovery Runbook

Updated: 2026-06-20

Scope: Fix loi sau khi scale node group len lai ma Harbor bi loi, cac app
DocVault bi `ImagePullBackOff`, hoac scheduler bao `Too many pods`.

---

## 1. Dau hieu thuong gap

Sau khi resume EKS tu node group = 0 hoac scale cluster len lai:

```powershell
kubectl get pod -n harbor
kubectl get pod -n docvault
```

Co the thay:

- `harbor-core` bi `CrashLoopBackOff`.
- `harbor-redis-0` hoac `harbor-jobservice` bi `Pending`.
- Cac pod `docvault-*` bi `ImagePullBackOff` hoac `ErrImagePull`.
- Event pull image bao `503 Service Temporarily Unavailable` tu
  `https://harbor.docvault.id.vn/v2/...`.
- Scheduler event bao `Too many pods` hoac `PersistentVolume's node affinity`.

Vi du:

```text
0/2 nodes are available: 1 Too many pods, 1 node(s) didn't match PersistentVolume's node affinity
failed to resolve reference "...harbor.docvault.id.vn/...": 503 Service Temporarily Unavailable
```

Neu PowerShell hien `ParserError`, thuong la do copy ca prompt `PS ...>` hoac
dong tiep dien `>>`. Chi copy phan lenh, khong copy `PS ...>` va `>>`.

---

## 2. Nguyen nhan goc

Chuoi loi thuong la:

1. Node group sau khi resume chi co it node, vi du 2 node `t3.large`.
2. Moi node co gioi han pod rieng. Trong su co da gap, node zone
   `ap-southeast-1b` cham `35/35` pod.
3. Mot so PVC/EBS cua Harbor nhu `harbor-redis-0` va `harbor-jobservice`
   nam co dinh theo AZ. Pod dung PVC do phai schedule dung zone cua volume.
4. Node dung zone do het slot pod, nen Redis/Jobservice khong schedule duoc.
5. Harbor core khong ket noi duoc Redis, nen crash.
6. Harbor registry endpoint tra `503`, nen cac app DocVault khong pull duoc
   image va roi vao `ImagePullBackOff`.

Day khong phai loi image tag dau tien. Neu Harbor chua healthy thi DocVault
pull image that bai la he qua.

---

## 3. Kiem tra nhanh

Set bien moi truong:

```powershell
$env:AWS_REGION = "ap-southeast-1"
$env:CLUSTER_NAME = "docvault-eks"
$env:HARBOR_NS = "harbor"
$env:DOCVAULT_NS = "docvault"
```

Kiem tra AWS identity va cluster context:

```powershell
aws sts get-caller-identity --profile default
kubectl config current-context
kubectl get nodes -o wide
```

Lay node group name:

```powershell
$env:NODEGROUP_NAME = aws eks list-nodegroups `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --query "nodegroups[?starts_with(@, 'docvault-ng')]|[0]" `
  --output text

Write-Host "NODEGROUP_NAME=$env:NODEGROUP_NAME"
```

Kiem tra size hien tai:

```powershell
aws eks describe-nodegroup `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --nodegroup-name $env:NODEGROUP_NAME `
  --query "nodegroup.{status:status,scaling:scalingConfig,instanceTypes:instanceTypes}"
```

Kiem tra pod loi:

```powershell
kubectl get pod -n harbor -o wide
kubectl get pod -n docvault -o wide
kubectl get events -A --sort-by=.lastTimestamp
```

Kiem tra event chi tiet cua Redis, Jobservice, Core va mot app DocVault:

```powershell
kubectl describe pod -n harbor harbor-redis-0
kubectl describe pod -n harbor -l component=jobservice
kubectl describe pod -n harbor -l component=core
kubectl describe pod -n docvault -l app=docvault-gateway
```

Kiem tra log Harbor core:

```powershell
kubectl logs -n harbor -l component=core --tail=120
kubectl logs -n harbor -l component=core --previous --tail=120
```

Neu log co Redis timeout nhu sau, tap trung fix Harbor Redis truoc:

```text
failed to ping redis://harbor-redis:6379
failed to initialize cache
```

Kiem tra PVC/PV zone:

```powershell
kubectl get pvc -n harbor -o wide

kubectl get pv -o custom-columns=NAME:.metadata.name,STATUS:.status.phase,CLAIM:.spec.claimRef.namespace/.spec.claimRef.name,SC:.spec.storageClassName,NODE_AFFINITY:.spec.nodeAffinity.required.nodeSelectorTerms[*].matchExpressions[*].values
```

Dem pod tren tung node:

```powershell
kubectl get pods -A -o json |
  ConvertFrom-Json |
  Select-Object -ExpandProperty items |
  Group-Object { $_.spec.nodeName } |
  Sort-Object Count -Descending |
  ForEach-Object { [PSCustomObject]@{ Node = $_.Name; PodCount = $_.Count } } |
  Format-Table -AutoSize
```

Kiem tra gioi han pod cua node:

```powershell
kubectl get nodes -L topology.kubernetes.io/zone `
  -o custom-columns=NAME:.metadata.name,ZONE:.metadata.labels.topology\.kubernetes\.io/zone,PODS:.status.allocatable.pods,CPU:.status.allocatable.cpu,MEM:.status.allocatable.memory
```

---

## 4. Fix nhanh khi Harbor Redis/Jobservice Pending

Muc tieu: giai phong slot pod de Harbor Redis/Jobservice len truoc. Khi Harbor
healthy, cac app DocVault moi pull image duoc.

### 4.1. Scale tam cac app DocVault ve 0

Lenh nay khong xoa PVC, secret, ingress hay image. No chi giam replica cua cac
deployment app de giai phong slot pod.

```powershell
kubectl scale -n docvault `
  deploy/docvault-audit-service `
  deploy/docvault-document-service `
  deploy/docvault-gateway `
  deploy/docvault-metadata `
  deploy/docvault-notification-service `
  deploy/docvault-web `
  deploy/docvault-workflow-service `
  --replicas=0
```

Luu y: Neu Argo CD automated self-heal dang bat, Argo CD co the tao lai pod.
Neu van khong du slot, tam thoi tang node group len truoc theo muc 5.1.

### 4.2. Doi Harbor len Ready

```powershell
kubectl get pod -n harbor -o wide
kubectl wait -n harbor --for=condition=Ready pod/harbor-redis-0 --timeout=180s
kubectl wait -n harbor --for=condition=Ready pod -l component=jobservice --timeout=180s
kubectl wait -n harbor --for=condition=Ready pod -l component=core --timeout=180s
```

Neu core van crash sau khi Redis da Ready:

```powershell
kubectl rollout restart -n harbor deploy/harbor-core
kubectl rollout status -n harbor deploy/harbor-core --timeout=180s
```

Kiem tra Harbor:

```powershell
kubectl get pod -n harbor
kubectl get svc,endpoints,endpointslice -n harbor -o wide
kubectl logs -n ingress-nginx deploy/ingress-nginx-controller --tail=120
```

Trong ingress log, registry pull thanh cong se co cac request `200` toi
`/v2/docvault-dev/...`.

### 4.3. Scale app DocVault len lai

```powershell
kubectl scale -n docvault `
  deploy/docvault-audit-service `
  deploy/docvault-document-service `
  deploy/docvault-gateway `
  deploy/docvault-metadata `
  deploy/docvault-notification-service `
  deploy/docvault-web `
  deploy/docvault-workflow-service `
  --replicas=1
```

Neu co job migrate bi fail vi pull image luc Harbor chua san sang, xoa job/pod
theo cach cua chart/Argo CD roi de Argo sync tao lai. Truoc khi xoa, xem ten:

```powershell
kubectl get job,pod -n docvault | Select-String -Pattern "migrate|metadata"
```

Chi xoa job migrate khi chac no la job retry duoc va khong dang chay thanh cong.

---

## 5. Fix capacity de tranh lap lai

Fix nhanh o muc 4 chi giai quyet lan hien tai. Neu node group van qua nho, loi
co the lap lai sau lan pause/resume tiep theo.

### 5.1. Tang node group bang AWS CLI

Tang tam len 3 node:

```powershell
aws eks update-nodegroup-config `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --nodegroup-name $env:NODEGROUP_NAME `
  --scaling-config minSize=1,maxSize=4,desiredSize=3
```

Theo doi:

```powershell
aws eks describe-nodegroup `
  --region $env:AWS_REGION `
  --cluster-name $env:CLUSTER_NAME `
  --nodegroup-name $env:NODEGROUP_NAME `
  --query "nodegroup.{status:status,scaling:scalingConfig}"

kubectl get nodes -w
```

### 5.2. Ghi lai vao Terraform de khong bi drift

Neu chi update bang AWS CLI, lan `terraform apply` sau co the dua size ve gia
tri trong Terraform. Can cap nhat file tfvars dang dung theo moi truong. File
mau hien co:

```text
infra/terraform/aws-eks/terraform.tfvars.example
```

Gia tri nen can nhac:

```hcl
node_desired_size = 3
node_min_size     = 1
node_max_size     = 4
```

Sau do chay Terraform theo workflow hien co cua repo:

```powershell
terraform -chdir=infra\terraform\aws-eks plan
terraform -chdir=infra\terraform\aws-eks apply
```

Neu muon tiet kiem chi phi, van co the pause ve 0 khi nghi, nhung khi resume
nen resume len 3 node thay vi 2 node neu Harbor + monitoring + Argo CD + app
cung chay tren mot cluster.

---

## 6. Verify sau khi fix

Tat ca lenh duoi day nen pass truoc khi xem la xong:

```powershell
kubectl get pod -n harbor
kubectl get pod -n docvault
kubectl get pods -A --field-selector=status.phase=Pending
kubectl get applications -n argocd -o custom-columns=NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status
```

Kiem tra rollout:

```powershell
kubectl rollout status -n docvault deploy/docvault-audit-service --timeout=300s
kubectl rollout status -n docvault deploy/docvault-document-service --timeout=300s
kubectl rollout status -n docvault deploy/docvault-gateway --timeout=300s
kubectl rollout status -n docvault deploy/docvault-metadata --timeout=300s
kubectl rollout status -n docvault deploy/docvault-notification-service --timeout=300s
kubectl rollout status -n docvault deploy/docvault-web --timeout=300s
kubectl rollout status -n docvault deploy/docvault-workflow-service --timeout=300s
```

Expected:

- Harbor pods: all `Running`, core `1/1`, Redis `1/1`, jobservice `1/1`.
- DocVault pods: all app deployments `1/1 Running`.
- Pending pods: `No resources found`.
- Argo CD apps: `Synced` and `Healthy`.

---

## 7. Checklist root cause

Neu loi lap lai, xac nhan cac diem sau truoc khi sua tiep:

- [ ] `harbor-redis-0` co Pending khong?
- [ ] `harbor-core` log co `failed to initialize cache` khong?
- [ ] Event co `Too many pods` khong?
- [ ] Event co `PersistentVolume's node affinity` khong?
- [ ] Node dung zone cua PVC Redis co cham gioi han pod khong?
- [ ] Harbor ingress log da tra `200` cho `/v2/docvault-dev/...` chua?
- [ ] Cac pod DocVault con loi pull image sau khi Harbor da Ready khong?

Neu Harbor da Ready ma van `ImagePullBackOff`, luc do moi chuyen sang kiem tra
image tag/digest, `imagePullSecrets`, robot account Harbor, TLS/DNS, va quyen
project `docvault-dev`.
