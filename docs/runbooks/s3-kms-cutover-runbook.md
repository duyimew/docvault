# DocVault S3 + KMS Local Cutover Runbook

Updated: 2026-06-16

Runbook nay huong dan chuyen `document-service` tu MinIO trong Kubernetes sang Amazon S3 + AWS KMS tren EKS khi operator tu thao tac tren may ca nhan. Khong dung Jenkins pipeline trong quy trinh nay.

Muc tieu cuoi cung:

- `document-service` ghi/doc file vao S3 bucket Terraform quan ly.
- Object moi duoc ma hoa bang SSE-KMS va KMS customer managed key co automatic rotation.
- `document-service` dung IRSA, khong dung `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- MinIO khong bi xoa ngay. MinIO duoc giu lai trong rollback window, sau do moi cleanup.

## 1. Ket Luan Ngan Gon

Voi cach local/manual, ban se lam 4 nhom viec:

1. Tu may ca nhan chay Terraform tao S3/KMS/IAM role.
2. Neu can giu file cu, mirror object tu MinIO sang S3.
3. Cap nhat `infra/k8s/values/document-service.yaml` bang Terraform outputs va push vao branch GitOps `gitops-testing`.
4. De Argo CD sync `docvault-document-service`, sau do verify upload/download va SSE-KMS.

MinIO dang chay trong namespace `docvault` se khong tu mat di. No van ton tai cho den khi ban cleanup GitOps infra overlay va Kubernetes resource rieng.

Khong can chay lai quy trinh nay chi de rotate KMS key. KMS automatic rotation do AWS quan ly.

## 2. Hien Trang Cluster Hien Tai

Theo output ban cung cap:

```text
namespace docvault: Active
service minio: ClusterIP, ports 9000/9001, age 15d
pod minio-0: Running
pod docvault-document-service-...: Running
```

Trong repo hien tai, `infra/k8s/values/document-service.yaml` van dang dung MinIO:

```yaml
env:
  S3_ENDPOINT: "http://minio:9000"
  S3_BUCKET: "docvault"
  S3_REGION: "us-east-1"
  S3_FORCE_PATH_STYLE: "true"

envValueFrom:
  - name: S3_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: minio-secret
        key: MINIO_ROOT_USER
  - name: S3_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: minio-secret
        key: MINIO_ROOT_PASSWORD
```

`infra/k8s/infra-deps/overlays/testing/kustomization.yaml` include `../../base`, va base dang include:

```text
minio.yaml
minio-init-job.yaml
```

Vi vay sau khi switch app sang S3, MinIO van tiep tuc chay neu GitOps infra overlay chua duoc doi.

## 3. Dieu Kien Truoc Khi Cutover

Tren may ca nhan can co cac tool:

- `aws`
- `terraform`
- `kubectl`
- `helm`
- `yq`
- `git`
- `mc` neu can migrate object tu MinIO sang S3
- `argocd` optional, co the sync bang Argo CD UI neu khong cai CLI

Kiem tra nhanh:

```powershell
aws --version
terraform version
kubectl version --client
helm version
yq --version
git --version
```

Neu can cai `yq` tren Windows:

```powershell
winget install mikefarah.yq
```

Neu can cai MinIO client `mc`:

```powershell
winget install MinIO.Client
```

Repo/code can co:

- Terraform `infra/terraform/aws-eks` expose outputs:
  - `documents_bucket_name`
  - `documents_kms_key_arn`
  - `document_service_role_arn`
- `document-service` code da support:
  - unset `S3_ENDPOINT` cho native AWS S3
  - `S3_USE_STATIC_CREDENTIALS=false`
  - `S3_SERVER_SIDE_ENCRYPTION=aws:kms`
  - `S3_KMS_KEY_ID`
  - `S3_BUCKET_KEY_ENABLED=true`
- Helm chart da support `serviceAccount` va `automountServiceAccountToken`.
- Terraform remote backend da san sang neu environment nay co the duoc thao tac tu nhieu may. Khong nen dung local state cho environment dung chung.

## 4. Chuan Bi Local Session

Mo PowerShell tai thu muc goc repo:

```powershell
cd C:\Users\THANG\docvault-devsecops
```

Set region:

```powershell
$env:AWS_REGION = "ap-southeast-1"
$env:AWS_DEFAULT_REGION = "ap-southeast-1"
```

Neu dung AWS profile rieng:

```powershell
$env:AWS_PROFILE = "docvault"
```

Kiem tra AWS identity:

```powershell
aws sts get-caller-identity
```

Kiem tra kubectl context:

```powershell
kubectl config current-context
kubectl get ns
kubectl -n docvault get svc minio
kubectl -n docvault get pods
```

Neu can refresh kubeconfig tu Terraform output:

```powershell
terraform -chdir=infra\terraform\aws-eks output -raw configure_kubectl
```

Lenh output co dang:

```text
aws eks update-kubeconfig --region ap-southeast-1 --name <cluster-name>
```

Chay lenh do tren may ca nhan neu kubectl chua tro dung cluster.

## 5. Chay Terraform Plan Tren May Ca Nhan

Chay init/fmt/validate/plan:

```powershell
terraform -chdir=infra\terraform\aws-eks init -input=false
terraform -chdir=infra\terraform\aws-eks fmt -check -recursive
terraform -chdir=infra\terraform\aws-eks validate
terraform -chdir=infra\terraform\aws-eks plan -input=false -out=tfplan
```

Doc plan truoc khi apply. Ky vong thay doi chinh nam o:

- S3 document bucket
- S3 access logs bucket
- KMS key/alias
- IAM role/policy cho `document-service`
- S3 bucket policy/lifecycle/logging/notification

Neu plan co thay doi ngoai pham vi storage ma ban khong mong muon, dung lai va review Terraform state/config truoc khi apply.

## 6. Apply Terraform Tren May Ca Nhan

Chi apply sau khi da review plan:

```powershell
terraform -chdir=infra\terraform\aws-eks apply -input=false tfplan
```

Lay outputs:

```powershell
$bucket = terraform -chdir=infra\terraform\aws-eks output -raw documents_bucket_name
$kmsArn = terraform -chdir=infra\terraform\aws-eks output -raw documents_kms_key_arn
$roleArn = terraform -chdir=infra\terraform\aws-eks output -raw document_service_role_arn

Write-Host "bucket=$bucket"
Write-Host "kmsArn=$kmsArn"
Write-Host "roleArn=$roleArn"
```

Kiem tra AWS resource co ton tai:

```powershell
aws s3api head-bucket --bucket $bucket
aws kms describe-key --key-id $kmsArn
aws iam get-role --role-name ($roleArn -replace '^.*/', '')
```

## 7. Neu Khong Can Giu File Cu Trong MinIO

Day la luong nhanh cho lab/demo neu object cu trong MinIO khong quan trong.

Sau khi Terraform apply xong:

1. Cap nhat `document-service.yaml` bang Terraform outputs.
2. Push thay doi vao branch GitOps `gitops-testing`.
3. De Argo CD sync `docvault-document-service`.
4. Smoke test upload/download file moi.
5. Giu MinIO trong rollback window, khong xoa ngay.

Di tiep den muc 9 de cap nhat values.

## 8. Neu Can Giu File Cu Trong MinIO

Neu MinIO dang co file can giu, mirror object sang S3 truoc khi push/sync values S3 cho `document-service`.

### 8.1 Maintenance Window

Trong maintenance window:

- Tam dung upload moi.
- Thong bao team khong tao document moi trong luc migrate.
- Neu can chat che hon, tam thoi scale `document-service` hoac gateway ve 0, hoac chan route upload o ingress/gateway.

Vi Argo CD app `docvault-document-service` hien co automated sync, khong push/merge thay doi S3 vao `gitops-testing` truoc khi mirror xong. Neu da lo push, tam thoi tat automated sync:

```powershell
kubectl -n argocd patch application docvault-document-service --type merge -p '{"spec":{"syncPolicy":{"syncOptions":["CreateNamespace=true","RespectIgnoreDifferences=true"]}}}'
```

Kiem tra:

```powershell
kubectl -n argocd get application docvault-document-service -o yaml | Select-String -Pattern "automated|syncOptions" -Context 1,3
```

### 8.2 Port-forward MinIO

Mo terminal rieng:

```powershell
kubectl -n docvault port-forward svc/minio 19000:9000
```

Lay MinIO credentials tu Kubernetes secret:

```powershell
$minioUserB64 = kubectl -n docvault get secret minio-secret -o jsonpath='{.data.MINIO_ROOT_USER}'
$minioPassB64 = kubectl -n docvault get secret minio-secret -o jsonpath='{.data.MINIO_ROOT_PASSWORD}'
$minioBucketB64 = kubectl -n docvault get secret minio-secret -o jsonpath='{.data.MINIO_BUCKET}'

$minioUser = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($minioUserB64))
$minioPass = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($minioPassB64))

if ($minioBucketB64) {
  $minioBucket = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($minioBucketB64))
} else {
  $minioBucket = "docvault"
}

Write-Host "minioBucket=$minioBucket"
```

### 8.3 Mirror MinIO -> S3 Bang mc

May chay lenh can co `mc` va AWS credentials ghi duoc vao S3 bucket.

```powershell
mc alias set minio-local http://127.0.0.1:19000 $minioUser $minioPass
mc alias set aws-s3 https://s3.ap-southeast-1.amazonaws.com $env:AWS_ACCESS_KEY_ID $env:AWS_SECRET_ACCESS_KEY --api S3v4

mc ls minio-local/$minioBucket
mc mirror --overwrite minio-local/$minioBucket aws-s3/$bucket
```

Neu ban dung AWS temporary credentials co session token, dam bao `mc` nhan duoc session token theo cach cau hinh local cua ban. Neu `mc mirror` khong gui duoc request headers SSE-KMS ma bucket policy reject upload, dung AWS CLI/SDK migration script rieng voi cac field:

```text
ServerSideEncryption=aws:kms
SSEKMSKeyId=<documents_kms_key_arn>
BucketKeyEnabled=true
```

Sau khi mirror, kiem tra nhanh:

```powershell
mc ls --recursive minio-local/$minioBucket | Measure-Object
aws s3 ls s3://$bucket --recursive | Measure-Object
```

Verify sample object quan trong:

```powershell
aws s3api head-object `
  --bucket $bucket `
  --key "doc/<document-id>/v<version>/<filename>" `
  --query "{SSE:ServerSideEncryption,KMS:SSEKMSKeyId,BucketKey:BucketKeyEnabled}"
```

## 9. Cap Nhat GitOps Values Tren May Ca Nhan

Argo CD dang theo doi branch `gitops-testing`. Sau khi Terraform apply xong va migrate xong neu can, cap nhat `infra/k8s/values/document-service.yaml` bang Terraform outputs.

### 9.1 Cach Khuyen Nghi: Dung script local

Script local doc Terraform outputs, cap nhat YAML bang `yq`, roi chay `helm lint` va `helm template`.

Neu ban da apply Terraform o muc 6, chay:

```powershell
.\scripts\update-values.ps1 -SkipTerraform
```

Neu muon script tu plan/apply luon tu dau:

```powershell
.\scripts\update-values.ps1 -Apply
```

Sau khi script chay xong, review diff:

```powershell
git diff -- infra\k8s\values\document-service.yaml
```

### 9.2 Cach Manual Bang yq

Neu khong dung script, chay cac lenh sau:

```powershell
$valuesPath = "infra\k8s\values\document-service.yaml"

$env:DOCVAULT_S3_BUCKET = $bucket
$env:DOCVAULT_S3_REGION = terraform -chdir=infra\terraform\aws-eks output -raw region
$env:DOCVAULT_S3_KMS_KEY_ARN = $kmsArn
$env:DOCVAULT_DOCUMENT_SERVICE_ROLE_ARN = $roleArn

yq e '.env.S3_BUCKET = strenv(DOCVAULT_S3_BUCKET)' -i $valuesPath
yq e '.env.S3_REGION = strenv(DOCVAULT_S3_REGION)' -i $valuesPath
yq e '.env.S3_KMS_KEY_ID = strenv(DOCVAULT_S3_KMS_KEY_ARN)' -i $valuesPath
yq e '.env.S3_SERVER_SIDE_ENCRYPTION = "aws:kms"' -i $valuesPath
yq e '.env.S3_BUCKET_KEY_ENABLED = "true"' -i $valuesPath
yq e '.env.S3_USE_STATIC_CREDENTIALS = "false"' -i $valuesPath
yq e '.env.S3_ENDPOINT = ""' -i $valuesPath
yq e '.env.S3_FORCE_PATH_STYLE = "false"' -i $valuesPath
yq e '.serviceAccount.create = true' -i $valuesPath
yq e '.serviceAccount.name = "docvault-document-service"' -i $valuesPath
yq e '.serviceAccount.automountToken = true' -i $valuesPath
yq e '.serviceAccount.annotations."eks.amazonaws.com/role-arn" = strenv(DOCVAULT_DOCUMENT_SERVICE_ROLE_ARN)' -i $valuesPath
yq e 'del(.envValueFrom[]? | select(.name == "S3_ACCESS_KEY" or .name == "S3_SECRET_KEY"))' -i $valuesPath
yq e 'if (.envValueFrom == []) then del(.envValueFrom) else . end' -i $valuesPath

helm lint infra\k8s\charts\docvault-service `
  -f infra\k8s\values\common-harbor.yaml `
  -f $valuesPath

helm template docvault-document-service infra\k8s\charts\docvault-service `
  -f infra\k8s\values\common-harbor.yaml `
  -f $valuesPath | Out-Null
```

## 10. Dua Values Vao Branch GitOps

Sau khi `document-service.yaml` da dung S3/KMS, can dua thay doi vao branch Argo CD dang watch: `gitops-testing`.

### 10.1 PR Flow

Dung khi muon review truoc khi Argo CD sync:

```powershell
git switch -c chore/docvault-s3-kms-values
git add infra\k8s\values\document-service.yaml
git commit -m "Feed document-service from Terraform-owned S3/KMS outputs"
git push -u origin chore/docvault-s3-kms-values
```

Mo PR vao `gitops-testing`, review, merge. Argo CD se doc commit moi tren `gitops-testing`.

### 10.2 Direct GitOps Flow

Dung khi lab/demo va ban chap nhan push truc tiep:

```powershell
git fetch origin gitops-testing
git switch gitops-testing
.\scripts\update-values.ps1 -SkipTerraform
git add infra\k8s\values\document-service.yaml
git commit -m "Feed document-service from Terraform-owned S3/KMS outputs"
git push origin gitops-testing
```

Neu `gitops-testing` chua co `scripts/update-values.ps1`, dung cach manual `yq` o muc 9.2 sau khi switch branch.

## 11. Values Mong Doi Sau Khi Cap Nhat

`infra/k8s/values/document-service.yaml` tren branch `gitops-testing` nen co dang:

```yaml
env:
  S3_BUCKET: "docvault-documents-testing-<account-id>"
  S3_REGION: "ap-southeast-1"
  S3_SERVER_SIDE_ENCRYPTION: "aws:kms"
  S3_KMS_KEY_ID: "arn:aws:kms:ap-southeast-1:<account-id>:key/<key-id>"
  S3_BUCKET_KEY_ENABLED: "true"
  S3_USE_STATIC_CREDENTIALS: "false"
  S3_ENDPOINT: ""
  S3_FORCE_PATH_STYLE: "false"

serviceAccount:
  create: true
  name: docvault-document-service
  automountToken: true
  annotations:
    eks.amazonaws.com/role-arn: "arn:aws:iam::<account-id>:role/<role-name>"
```

`envValueFrom` khong con `S3_ACCESS_KEY` va `S3_SECRET_KEY`.

## 12. Sync Argo CD

Neu Argo CD automated sync dang bat, app se tu sync sau khi branch `gitops-testing` co commit moi.

Kiem tra:

```powershell
kubectl -n argocd get application docvault-document-service
kubectl -n argocd get application docvault-document-service -o jsonpath='{.status.sync.status}{" "}{.status.health.status}{"`n"}'
```

Neu dung Argo CD CLI:

```powershell
argocd app sync docvault-document-service
argocd app wait docvault-document-service --sync --health --timeout 300
```

Neu khong dung CLI, sync bang Argo CD UI.

Neu truoc do da tat automated sync, bat lai sau khi migrate/sync xong:

```powershell
kubectl -n argocd patch application docvault-document-service --type merge -p '{"spec":{"syncPolicy":{"automated":{"prune":false,"selfHeal":true},"syncOptions":["CreateNamespace=true","RespectIgnoreDifferences=true"]}}}'
```

## 13. Xac Minh Kubernetes Va IRSA

Kiem tra rollout:

```powershell
kubectl -n docvault rollout status deploy/docvault-document-service --timeout=300s
kubectl -n docvault get pods -l app=docvault-document-service
```

Kiem tra ServiceAccount:

```powershell
kubectl -n docvault get sa docvault-document-service -o yaml
```

Can thay annotation:

```yaml
eks.amazonaws.com/role-arn: arn:aws:iam::<account-id>:role/<document-service-role>
```

Kiem tra deployment env:

```powershell
kubectl -n docvault get deploy docvault-document-service -o yaml | Select-String -Pattern "serviceAccountName|automountServiceAccountToken|S3_BUCKET|S3_ENDPOINT|S3_USE_STATIC_CREDENTIALS|S3_KMS_KEY_ID" -Context 1,2
```

Neu image co `printenv`, co the kiem tra trong pod:

```powershell
kubectl -n docvault exec deploy/docvault-document-service -- printenv | Select-String -Pattern "^S3_"
```

Ky vong:

```text
S3_ENDPOINT=
S3_FORCE_PATH_STYLE=false
S3_USE_STATIC_CREDENTIALS=false
S3_SERVER_SIDE_ENCRYPTION=aws:kms
S3_BUCKET_KEY_ENABLED=true
```

Khong nen thay:

```text
S3_ACCESS_KEY=...
S3_SECRET_KEY=...
```

## 14. Xac Minh AWS S3/KMS

Kiem tra S3 bucket encryption:

```powershell
aws s3api get-bucket-encryption --bucket $bucket
```

Kiem tra versioning:

```powershell
aws s3api get-bucket-versioning --bucket $bucket
```

Kiem tra public access block:

```powershell
aws s3api get-public-access-block --bucket $bucket
```

Kiem tra KMS rotation:

```powershell
aws kms get-key-rotation-status --key-id $kmsArn
```

Ky vong:

```text
KeyRotationEnabled=true
```

## 15. Smoke Test App

Chay cac luong sau tren web/gateway:

1. Upload mot file moi.
2. Download file vua upload.
3. Preview file neu app co preview.
4. Upload file bi malware/EICAR neu demo flow co support, ky vong bi chan truoc storage.
5. Kiem tra document version duoc tao va object key co dang:

```text
doc/<document-id>/v<version>/<filename>
```

Lay object key tu DB/log/app response, roi kiem tra S3 object:

```powershell
aws s3api head-object `
  --bucket $bucket `
  --key "doc/<document-id>/v<version>/<filename>" `
  --query "{SSE:ServerSideEncryption,KMS:SSEKMSKeyId,BucketKey:BucketKeyEnabled,Metadata:Metadata}"
```

Ky vong:

```json
{
  "SSE": "aws:kms",
  "KMS": "arn:aws:kms:ap-southeast-1:<account-id>:key/<key-id>",
  "BucketKey": true
}
```

Neu upload fail voi loi `AccessDenied` hoac KMS, kiem tra:

- ServiceAccount annotation co dung role ARN khong.
- Pod co dung `serviceAccountName: docvault-document-service` khong.
- IAM trust policy co dung namespace `docvault` va service account `docvault-document-service` khong.
- `S3_KMS_KEY_ID` trong values co dung `documents_kms_key_arn` khong.
- Bucket policy co reject upload neu app khong gui SSE-KMS headers khong.

## 16. MinIO Sau Khi Cutover

Ngay sau khi app da sang S3, MinIO van co the tiep tuc chay:

```powershell
kubectl -n docvault get svc minio
kubectl -n docvault get pod minio-0
kubectl -n docvault get pvc | Select-String -Pattern "minio"
```

Day la binh thuong. Nen giu MinIO trong rollback window, vi:

- File cu co the van can doi chieu.
- Rollback ve MinIO nhanh hon neu S3 cutover co loi.
- PVC MinIO la noi giu data cu, khong nen xoa ngay.

Sau khi S3 smoke test pass, co the chon:

- Giu MinIO chay trong vai ngay de rollback nhanh.
- Scale MinIO ve 0 de giam resource Kubernetes nhung giu PVC.

Scale ve 0:

```powershell
kubectl -n docvault scale statefulset/minio --replicas=0
```

Luu y: neu Argo CD infra app sync lai manifest MinIO, MinIO co the duoc tao/chay lai. Muon cleanup sach can doi GitOps infra overlay truoc.

## 17. Cleanup MinIO Bang GitOps

Chi lam muc nay sau khi:

- App upload/download on dinh tren S3.
- Object cu da migrate neu can.
- Da qua rollback window.
- Da co sign-off xoa MinIO khoi AWS/testing environment.

Khuyen nghi lam bang mot PR rieng:

1. Tao overlay AWS/testing moi khong include MinIO, vi du:

```text
infra/k8s/infra-deps/overlays/testing-s3
```

2. Overlay moi khong nen include:

```text
minio.yaml
minio-init-job.yaml
```

3. Cap nhat `infra/argocd-apps/docvault-infra.yaml`:

```yaml
spec:
  source:
    path: infra/k8s/infra-deps/overlays/testing-s3
```

4. Sync `docvault-infra-deps`.

Vi `docvault-infra-deps` hien khong bat prune tu dong, resource MinIO co the khong bi xoa chi bang viec remove khoi Git. Sau khi da chac chan rollback khong can nua, xoa workload MinIO thu cong:

```powershell
kubectl -n docvault delete job minio-init --ignore-not-found
kubectl -n docvault delete statefulset minio --ignore-not-found
kubectl -n docvault delete svc minio --ignore-not-found
kubectl -n docvault delete externalsecret minio-secret --ignore-not-found
```

Sau khi xoa StatefulSet, PVC thuong van con. Kiem tra:

```powershell
kubectl -n docvault get pvc | Select-String -Pattern "minio"
```

Chi xoa PVC khi da chac chan khong can data MinIO nua:

```powershell
kubectl -n docvault delete pvc minio-data-minio-0
kubectl -n docvault delete secret minio-secret --ignore-not-found
```

Xoa PVC la buoc co kha nang mat data MinIO cu. Khong lam buoc nay neu chua co backup/sign-off.

## 18. Rollback

Rollback nhanh neu MinIO/PVC van con.

### 18.1 Neu chua co upload moi len S3

1. Revert Git commit da doi `document-service.yaml` sang S3, hoac sua lai values:

```yaml
env:
  S3_ENDPOINT: "http://minio:9000"
  S3_BUCKET: "docvault"
  S3_REGION: "us-east-1"
  S3_FORCE_PATH_STYLE: "true"

envValueFrom:
  - name: S3_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: minio-secret
        key: MINIO_ROOT_USER
  - name: S3_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: minio-secret
        key: MINIO_ROOT_PASSWORD
```

2. Sync `docvault-document-service` tren Argo CD.
3. Neu da scale MinIO ve 0, bat lai:

```powershell
kubectl -n docvault scale statefulset/minio --replicas=1
kubectl -n docvault rollout status statefulset/minio --timeout=300s
```

4. Smoke test upload/download tren MinIO.

### 18.2 Neu da co upload moi len S3

Can mirror nguoc S3 -> MinIO truoc khi rollback app, neu khong DB se tro den object key co the chua ton tai trong MinIO.

```powershell
mc mirror --overwrite aws-s3/$bucket minio-local/$minioBucket
```

Sau khi mirror nguoc, rollback values va sync app nhu muc 18.1.

## 19. Tieu Chi Hoan Tat

Co the coi cutover thanh cong khi tat ca dieu kien sau dat:

- Terraform local apply thanh cong.
- Branch `gitops-testing` co commit cap nhat `document-service.yaml`.
- Argo CD app `docvault-document-service` `Synced/Healthy`.
- Deployment `docvault-document-service` rollout thanh cong.
- ServiceAccount `docvault-document-service` co annotation IRSA role.
- Pod khong dung `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- Upload file moi thanh cong.
- `aws s3api head-object` cua file moi tra ve `ServerSideEncryption=aws:kms`.
- `aws kms get-key-rotation-status` tra ve `KeyRotationEnabled=true`.
- MinIO duoc giu lai hoac cleanup theo quy trinh, khong xoa PVC ngoai y muon.

## 20. Khi Nao Chay Lai Quy Trinh Local Nay

Chay lai cac buoc Terraform/update values khi:

- Tao lai EKS/environment.
- Terraform document storage module thay doi.
- IAM role/policy, bucket policy, lifecycle, logging hoac notification thay doi.
- Can recover drift giua Terraform outputs va GitOps values.
- Can tao lai bucket/KMS trong account/region moi.

Khong can chay lai khi:

- KMS automatic rotation den chu ky.
- Deploy image moi cua `document-service`.
- Sync Argo CD thong thuong.
- Restart pod `document-service`.
