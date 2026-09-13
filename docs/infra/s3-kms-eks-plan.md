# Ke Hoach Chuyen DocVault tu MinIO sang Amazon S3 + AWS KMS tren EKS

Updated: 2026-06-16

Tai lieu nay la phuong an khuyen nghi cho moi truong DocVault chay tren AWS:

```text
document-service tren EKS
  -> AWS SDK for JavaScript S3Client
  -> Amazon S3 bucket
  -> Server-side encryption SSE-KMS
  -> AWS KMS customer managed key co automatic rotation
```

MinIO nen duoc giu cho local/dev hoac moi truong lab. Production AWS nen dung Amazon S3 native thay vi tu van hanh MinIO + KES, tru khi co yeu cau bat buoc ve self-hosted object storage, on-prem, hoac multi-cloud portability.

## 1. Quyet Dinh Kien Truc

### Chon S3 + SSE-KMS cho production AWS

Phuong an nay thay the MinIO StatefulSet/KES bang dich vu managed cua AWS:

- Amazon S3 luu file/blob tai lieu.
- AWS KMS customer managed symmetric key ma hoa object qua SSE-KMS.
- KMS automatic key rotation duoc bat tren key.
- S3 Bucket Keys duoc bat de giam so request den KMS va giam chi phi.
- `document-service` dung IRSA de lay temporary AWS credentials, khong dung `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- Metadata hien tai van chi luu `objectKey`, nen neu copy object sang S3 voi cung key path thi khong can migrate DB.

### MinIO chi giu cho local/dev

Local Docker va demo offline co the tiep tuc dung:

```text
S3_ENDPOINT=http://localhost:9000
S3_FORCE_PATH_STYLE=true
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=...
S3_BUCKET=docvault
```

Production AWS nen unset `S3_ENDPOINT` va khong inject static S3 credentials.

## 2. Trang Thai Hien Tai Trong Repo

Nhung diem can biet truoc khi migration:

- `services/document-service/src/storage/storage.service.ts` da dung `@aws-sdk/client-s3`, nen khong can viet lai toan bo storage layer.
- `infra/k8s/values/document-service.yaml` dang tro den MinIO noi bo:

```yaml
S3_ENDPOINT: "http://minio:9000"
S3_BUCKET: "docvault"
S3_REGION: "us-east-1"
S3_FORCE_PATH_STYLE: "true"
```

- Cung file values dang inject MinIO root credentials vao `S3_ACCESS_KEY` va `S3_SECRET_KEY`.
- Helm chart `infra/k8s/charts/docvault-service` hien chua co cau hinh ServiceAccount/IRSA rieng cho tung service.
- `Deployment` template hien dat `automountServiceAccountToken: false`. Khi dung IRSA can cap nhat chart de `document-service` co service account rieng va co projected token cho AWS STS.
- Prisma model `DocumentVersion` chi luu `objectKey`, khong luu bucket name. Day la loi the khi chuyen backend storage.

## 3. Tai Nguyen AWS Can Tao

Dung Terraform trong `infra/terraform/aws-eks` de quan ly nhat quan voi EKS hien tai.

### 3.1 S3 bucket

Ten de xuat:

```text
docvault-documents-<environment>-<account-id>
```

Vi du:

```text
docvault-documents-testing-111122223333
```

Thiet lap bat buoc:

- Block Public Access: bat tat ca.
- Versioning: bat. Huu ich cho phuc hoi va audit.
- Default encryption: SSE-KMS bang customer managed key.
- S3 Bucket Key: bat.
- Lifecycle policy: tuy retention yeu cau cua demo/production.
- Object Lock: chi bat neu can WORM/compliance retention that. Luu y Object Lock phai quyet dinh luc tao bucket.

### 3.2 KMS key

Tao customer managed symmetric KMS key:

```text
alias/docvault-s3-documents-<environment>
```

Cau hinh:

- `enable_key_rotation = true`.
- Mac dinh AWS KMS rotation la 365 ngay neu khong dat custom rotation period.
- Dung key cung region voi S3 bucket.
- Key policy chi cho phep `document-service` role dung cryptographic actions can thiet.

### 3.3 IAM role cho document-service bang IRSA

Tao IAM role rieng:

```text
docvault-document-service-s3-<environment>
```

Trust policy bind chat voi service account:

```text
system:serviceaccount:docvault:docvault-document-service
```

Khong dung node instance role de cap quyen S3/KMS cho app.

## 4. Terraform Mau

Tao file moi, vi du:

```text
infra/terraform/aws-eks/documents-s3-kms.tf
```

Snippet duoi day la khung tham khao. Khi implement that, can format va chay `terraform fmt`.

```hcl
data "aws_caller_identity" "current" {}

locals {
  documents_bucket_name = "docvault-documents-${var.environment}-${data.aws_caller_identity.current.account_id}"
  document_service_sa   = "docvault-document-service"
}

resource "aws_kms_key" "documents_s3" {
  description             = "DocVault document object encryption key"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = merge(local.tags, {
    Name = "docvault-documents-s3-${var.environment}"
  })
}

resource "aws_kms_alias" "documents_s3" {
  name          = "alias/docvault-s3-documents-${var.environment}"
  target_key_id = aws_kms_key.documents_s3.key_id
}

resource "aws_s3_bucket" "documents" {
  bucket = local.documents_bucket_name

  tags = merge(local.tags, {
    Name = local.documents_bucket_name
  })
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket = aws_s3_bucket.documents.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.documents_s3.arn
    }

    bucket_key_enabled = true
  }
}
```

### 4.1 IAM policy cho document-service

Quyen toi thieu cho app hien tai:

```hcl
data "aws_iam_policy_document" "document_service_s3" {
  statement {
    sid = "ListDocumentBucket"

    actions = [
      "s3:ListBucket"
    ]

    resources = [
      aws_s3_bucket.documents.arn
    ]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["doc/*"]
    }
  }

  statement {
    sid = "ReadWriteDocumentObjects"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:AbortMultipartUpload",
      "s3:ListMultipartUploadParts"
    ]

    resources = [
      "${aws_s3_bucket.documents.arn}/doc/*"
    ]
  }

  statement {
    sid = "UseDocumentKmsKeyViaS3"

    actions = [
      "kms:Decrypt",
      "kms:Encrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey"
    ]

    resources = [
      aws_kms_key.documents_s3.arn
    ]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["s3.${var.aws_region}.amazonaws.com"]
    }

    # S3 Bucket Keys can use bucket ARN as encryption context.
    condition {
      test     = "StringLike"
      variable = "kms:EncryptionContext:aws:s3:arn"
      values = [
        aws_s3_bucket.documents.arn,
        "${aws_s3_bucket.documents.arn}/*"
      ]
    }
  }
}

resource "aws_iam_policy" "document_service_s3" {
  name   = "docvault-document-service-s3-${var.environment}"
  policy = data.aws_iam_policy_document.document_service_s3.json
}
```

### 4.2 IRSA trust policy

Neu EKS module hien tai expose `module.eks.oidc_provider_arn` va `module.eks.oidc_provider`, co the dung:

```hcl
data "aws_iam_policy_document" "document_service_irsa_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:sub"
      values   = ["system:serviceaccount:docvault:${local.document_service_sa}"]
    }
  }
}

resource "aws_iam_role" "document_service" {
  name               = "docvault-document-service-s3-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.document_service_irsa_trust.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "document_service_s3" {
  role       = aws_iam_role.document_service.name
  policy_arn = aws_iam_policy.document_service_s3.arn
}
```

Them outputs:

```hcl
output "documents_bucket_name" {
  value = aws_s3_bucket.documents.bucket
}

output "documents_kms_key_arn" {
  value = aws_kms_key.documents_s3.arn
}

output "document_service_role_arn" {
  value = aws_iam_role.document_service.arn
}
```

## 5. Bucket Policy De Tang Bao Ve

Ap dung sau khi app da gui SSE-KMS headers trong `PutObjectCommand`.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyInsecureTransport",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": ["arn:aws:s3:::<BUCKET>", "arn:aws:s3:::<BUCKET>/*"],
      "Condition": {
        "Bool": {
          "aws:SecureTransport": "false"
        }
      }
    },
    {
      "Sid": "DenyUploadsWithoutSseKms",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<BUCKET>/*",
      "Condition": {
        "StringNotEquals": {
          "s3:x-amz-server-side-encryption": "aws:kms"
        }
      }
    },
    {
      "Sid": "DenyUploadsWithWrongKmsKey",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<BUCKET>/*",
      "Condition": {
        "StringNotEquals": {
          "s3:x-amz-server-side-encryption-aws-kms-key-id": "<KMS_KEY_ARN>"
        }
      }
    }
  ]
}
```

Ghi chu: neu chua sua app de gui SSE-KMS headers, khong ap dung 2 deny rule cuoi ngay lap tuc. Default bucket encryption van ma hoa object, nhung bucket policy dua tren request headers co the reject upload khong co header.

## 6. Thay Doi Code document-service

Cap nhat `services/document-service/src/storage/storage.service.ts` de support ca MinIO local va S3 production.

Muc tieu:

- Chi set `endpoint` khi `S3_ENDPOINT` co gia tri.
- Chi set static credentials khi ca `S3_ACCESS_KEY` va `S3_SECRET_KEY` co gia tri.
- Cho phep tat static credentials bang `S3_USE_STATIC_CREDENTIALS=false` de IRSA thang ngay ca khi secret cu van con inject `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- Production AWS de AWS SDK dung default credential provider chain, tu do nhan IRSA credentials.
- `forcePathStyle` mac dinh false khi dung S3.
- Upload object gui SSE-KMS headers khi production config co `S3_KMS_KEY_ID`.

Huong sua logic:

```ts
const endpoint = process.env.S3_ENDPOINT || undefined;
const accessKeyId = process.env.S3_ACCESS_KEY;
const secretAccessKey = process.env.S3_SECRET_KEY;
const staticCredentialsEnabled =
  process.env.S3_USE_STATIC_CREDENTIALS !== "false";
const credentials =
  staticCredentialsEnabled && accessKeyId && secretAccessKey
    ? { accessKeyId, secretAccessKey }
    : undefined;

const client = new S3Client({
  region: process.env.S3_REGION ?? "ap-southeast-1",
  ...(endpoint ? { endpoint } : {}),
  forcePathStyle: process.env.S3_FORCE_PATH_STYLE === "true",
  ...(credentials ? { credentials } : {}),
});
```

Trong `PutObjectCommand`, them optional encryption fields:

```ts
new PutObjectCommand({
  Bucket: this.bucket,
  Key: params.objectKey,
  Body: params.body,
  ContentType: params.contentType,
  Metadata: params.metadata,
  ServerSideEncryption: process.env.S3_SERVER_SIDE_ENCRYPTION as
    | "aws:kms"
    | undefined,
  SSEKMSKeyId: process.env.S3_KMS_KEY_ID,
  BucketKeyEnabled:
    process.env.S3_BUCKET_KEY_ENABLED === "true" ? true : undefined,
});
```

Can refactor de tranh cast tuy tien neu muon type-safe hon:

```ts
const serverSideEncryption =
  process.env.S3_SERVER_SIDE_ENCRYPTION === "aws:kms" ? "aws:kms" : undefined;
```

Presigned URL:

- Voi MinIO local, co the giu logic `S3_PUBLIC_URL`.
- Voi S3 production, khong can replace endpoint. SDK se tao URL den S3 host dung.
- Nen sua `publicUrl` chi apply khi `S3_ENDPOINT` duoc set.

## 7. Thay Doi Helm Chart

Them ServiceAccount support vao chart `infra/k8s/charts/docvault-service`.

### 7.1 Values mac dinh

Trong `infra/k8s/charts/docvault-service/values.yaml`:

```yaml
serviceAccount:
  create: false
  name: ""
  annotations: {}
  automountToken: false
```

### 7.2 Template serviceaccount

Them file:

```text
infra/k8s/charts/docvault-service/templates/serviceaccount.yaml
```

```yaml
{{- if .Values.serviceAccount.create }}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ .Values.serviceAccount.name | default .Release.Name }}
  namespace: {{ .Values.namespace | quote }}
  labels:
    app: {{ .Release.Name }}
  {{- with .Values.serviceAccount.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
{{- end }}
```

### 7.3 Deployment template

Trong `templates/deployment.yaml`, thay pod spec:

```yaml
serviceAccountName: { { .Values.serviceAccount.name | default .Release.Name } }
automountServiceAccountToken:
  { { .Values.serviceAccount.automountToken | default false } }
```

Voi document-service production AWS, dat `automountToken: true` hoac xac nhan IRSA projected token da duoc inject va `AWS_WEB_IDENTITY_TOKEN_FILE` ton tai trong pod. Voi cac service khac giu false.

## 8. Thay Doi document-service Values Cho AWS

Trong `infra/k8s/values/document-service.yaml`, production AWS nen thanh:

```yaml
env:
  NODE_ENV: production
  PORT: "3002"
  KEYCLOAK_BASE_URL: "http://keycloak:8080"
  KEYCLOAK_ISSUER: "https://auth.docvault.id.vn/realms/docvault"
  KEYCLOAK_REALM: "docvault"
  KEYCLOAK_AUDIENCE: "docvault-gateway"
  METADATA_SERVICE_URL: "http://docvault-metadata:3001"
  AUDIT_SERVICE_URL: "http://docvault-audit-service:3004"
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
    eks.amazonaws.com/role-arn: "arn:aws:iam::<account-id>:role/docvault-document-service-s3-testing"

envValueFrom: []
```

Khong dat trong production:

```yaml
S3_ENDPOINT: "http://minio:9000"
S3_FORCE_PATH_STYLE: "true"
```

Khong inject:

```yaml
S3_ACCESS_KEY
S3_SECRET_KEY
```

Trong AWS Secrets Manager `/docvault/testing/app`, bo `S3_ACCESS_KEY` va `S3_SECRET_KEY` neu chung chi phuc vu MinIO.

## 9. Thay Doi Infra De Khong Deploy MinIO Tren AWS

Neu overlay AWS/testing hien dang include:

```text
infra/k8s/infra-deps/base/minio.yaml
infra/k8s/infra-deps/base/minio-init-job.yaml
```

Thi tao overlay rieng cho AWS production-like, vi du:

```text
infra/k8s/infra-deps/overlays/aws/kustomization.yaml
```

Overlay AWS nen include Postgres/Mongo/Keycloak/SecretStore theo nhu cau, nhung khong include MinIO neu da dung S3.

Khong xoa MinIO PVC ngay sau cutover. Giu lai den khi:

- Da copy du object sang S3.
- Da verify upload/download tren S3.
- Da co backup hoac snapshot can thiet.
- Da qua it nhat mot chu ky demo/test quan trong.

## 10. Ke Hoach Migration Du Lieu

### 10.1 Nguyen tac

- Giu nguyen object key dang co dang `doc/<docId>/v<version>/<filename>`.
- Khong can sua DB neu bucket moi co day du object voi cung key.
- Lam trong maintenance window ngan de tranh upload moi vao MinIO trong luc copy.

### 10.2 Cac buoc

1. Tao S3 bucket + KMS + IAM role + IRSA.
2. Deploy code document-service moi vao staging nhung chua switch traffic neu can.
3. Tam dung upload hoac dat app read-only trong maintenance window.
4. Copy object tu MinIO bucket `docvault` sang S3 bucket moi.
5. Verify sample object count va checksum neu co the.
6. Cap nhat `document-service` values sang S3.
7. Sync Argo CD.
8. Smoke test upload/download/preview.
9. Neu pass, mo lai upload.
10. Giu MinIO readonly de rollback tam thoi.

### 10.3 Cach copy du lieu

Phuong an de kiem soat nhat la dung `mc mirror` vi ca MinIO va S3 deu S3-compatible.

Tu may local co quyen AWS:

```powershell
kubectl -n docvault port-forward svc/minio 9000:9000
```

Trong terminal khac:

```powershell
mc alias set minio http://127.0.0.1:9000 <MINIO_ROOT_USER> <MINIO_ROOT_PASSWORD>
mc alias set aws-s3 https://s3.ap-southeast-1.amazonaws.com <AWS_ACCESS_KEY_ID> <AWS_SECRET_ACCESS_KEY>
mc mirror --overwrite minio/docvault aws-s3/docvault-documents-testing-<account-id>
```

Neu dung temporary session token, can cau hinh `mc` theo profile/session phu hop. Cach production hon la chay mot Kubernetes Job tam thoi co:

- Quyen doc MinIO secret.
- IRSA role co quyen write S3 bucket.
- Container `minio/mc`.
- Xoa Job va role migration sau khi hoan tat.

Default bucket encryption SSE-KMS se ma hoa object duoc copy vao S3. Neu bucket policy yeu cau upload phai co SSE-KMS request headers, dam bao tool copy gui dung headers hoac tam thoi chi bat policy sau khi migration xong.

## 11. Validation

### 11.1 AWS resource validation

```powershell
aws s3api get-bucket-encryption `
  --bucket docvault-documents-testing-<account-id>

aws s3api get-bucket-versioning `
  --bucket docvault-documents-testing-<account-id>

aws s3api get-public-access-block `
  --bucket docvault-documents-testing-<account-id>

aws kms get-key-rotation-status `
  --key-id arn:aws:kms:ap-southeast-1:<account-id>:key/<key-id>
```

### 11.2 Pod identity validation

Sau khi Argo CD sync:

```powershell
kubectl -n docvault get sa docvault-document-service -o yaml
kubectl -n docvault describe pod -l app=docvault-document-service
```

Can thay:

- ServiceAccount co annotation `eks.amazonaws.com/role-arn`.
- Pod dung service account `docvault-document-service`.
- Pod co env/volume lien quan web identity token do EKS inject, neu IRSA hoat dong.

Co the debug tam thoi bang AWS CLI sidecar/job rieng. Khong can cai AWS CLI vao image production cua document-service.

### 11.3 Object encryption validation

Upload mot file test qua app, lay object key tu metadata hoac log, roi chay:

```powershell
aws s3api head-object `
  --bucket docvault-documents-testing-<account-id> `
  --key "doc/<docId>/v<version>/<filename>" `
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

### 11.4 App smoke tests

Chay cac luong sau tren web/gateway:

- Upload file sach: thanh cong, tao document version.
- Upload EICAR: bi chan truoc storage, khong co object S3 moi.
- Download tai lieu PUBLIC/INTERNAL: presigned URL hoat dong.
- Preview tai lieu: range request van hoat dong voi object S3.
- Download/preview CONFIDENTIAL/SECRET: di qua streaming path va watermark.
- Delete/rollback khi metadata create version fail: object S3 orphan duoc delete.

### 11.5 Audit va CloudTrail

Kiem tra:

- CloudTrail co `GenerateDataKey` khi upload SSE-KMS.
- CloudTrail co `Decrypt` khi get object can KMS decrypt.
- Principal la role `docvault-document-service-s3-<environment>`, khong phai node role.

## 12. Rollback

Rollback nhanh chi an toan neu trong maintenance window chua co write moi len S3, hoac da copy nguoc du lieu moi ve MinIO.

### 12.1 Rollback config

Trong `infra/k8s/values/document-service.yaml`, quay lai:

```yaml
S3_ENDPOINT: "http://minio:9000"
S3_BUCKET: "docvault"
S3_REGION: "us-east-1"
S3_FORCE_PATH_STYLE: "true"
```

Va restore `envValueFrom` cho:

```yaml
S3_ACCESS_KEY
S3_SECRET_KEY
```

### 12.2 Rollback du lieu

Neu co object moi duoc upload vao S3 sau cutover, can mirror nguoc ve MinIO truoc khi rollback app:

```powershell
mc mirror --overwrite aws-s3/docvault-documents-testing-<account-id> minio/docvault
```

Neu khong mirror nguoc, DB van tro object key dung nhung MinIO se thieu object moi.

## 13. Cleanup Sau Khi On Dinh

Sau khi da chay on dinh:

- Dung deploy MinIO trong AWS overlay.
- Giu backup/snapshot MinIO trong thoi gian da thoa thuan.
- Xoa MinIO PVC chi sau khi co sign-off.
- Xoa `minio-secret` khoi AWS overlay neu khong con dung.
- Cap nhat docs demo: "MinIO local/dev, S3 + KMS production AWS".
- Cap nhat security evidence: S3 SSE-KMS, KMS rotation, IRSA, CloudTrail.

## 14. Enterprise-like Automation

Repo co 2 cach chay automation va 1 shared-library step:

```text
scripts/update-values.ps1
Jenkinsfile.storage
vars/documentStorageGitOps.groovy
```

`vars/documentStorageGitOps.groovy` la shared-library step dung chung cho Jenkins. No khong tu kich hoat pipeline; entrypoint Jenkins chinh la `Jenkinsfile.storage`.

Runbook thao tac cutover chi tiet: `docs/runbooks/s3-kms-cutover-runbook.md`.

### 14.1 Local/manual script

Mac dinh script chi tao Terraform plan:

```powershell
.\scripts\update-values.ps1
```

Chi khi can apply va cap nhat Helm values moi chay:

```powershell
.\scripts\update-values.ps1 -Apply
```

Neu muon tao commit local tren branch rieng:

```powershell
.\scripts\update-values.ps1 -Apply -Commit
```

Neu muon push branch de mo PR vao branch Argo CD dang theo doi:

```powershell
.\scripts\update-values.ps1 -Apply -Commit -Push
```

Script dung `terraform plan -out tfplan` roi `terraform apply tfplan`, doc outputs, cap nhat `document-service.yaml` bang `yq`, chay `helm lint` va `helm template`, sau do moi commit neu co `-Commit`. Script nay phu hop bootstrap/local manual; Jenkins job rieng la duong chinh cho automation tren CI/CD.

### 14.2 Jenkins job rieng

Tao mot Jenkins Pipeline job rieng tro den `Jenkinsfile.storage`. Job nay khong nam trong CD app binh thuong; no chi duoc chay khi can provision/update S3, KMS, IAM role, hoac can dong bo lai `document-service.yaml` tu Terraform outputs.

Parameters:

```text
APPLY_DOCUMENT_STORAGE_TERRAFORM=false|true
DOCUMENT_STORAGE_REQUIRE_APPROVAL=true
DOCUMENT_STORAGE_VALUES_FILE=infra/k8s/values/document-service.yaml
AWS_REGION=ap-southeast-1
```

Luồng Jenkins:

1. Chay `terraform fmt`, `validate`, `plan`.
2. Neu `APPLY_DOCUMENT_STORAGE_TERRAFORM=true`, Jenkins doi approval bang `input` neu `DOCUMENT_STORAGE_REQUIRE_APPROVAL=true`.
3. Chay `terraform apply tfplan`.
4. Doc Terraform outputs.
5. Clone branch GitOps, mac dinh `gitops-testing`.
6. Cap nhat `document-service.yaml` bang `yq`.
7. Chay `helm lint` va `helm template`.
8. Commit voi `[skip ci]` va push ve branch GitOps.
9. Argo CD sync theo branch GitOps.

Can cau hinh truoc khi bat `APPLY_DOCUMENT_STORAGE_TERRAFORM=true`:

- Terraform S3 remote backend trong `infra/terraform/aws-eks/versions.tf`.
- Remote backend nen co S3 versioning va state locking.
- Jenkins agent co `terraform`, `yq`, `helm`, `git` trong PATH.
- Jenkins agent co AWS credentials tam thoi. Voi setup hien tai, uu tien IAM Roles Anywhere/credential_process da mo ta trong `docs/devsecops/jenkins_iam_roles_anywhere.md`.
- Jenkins credential `github-credentials` co quyen push branch GitOps.

Nen chay theo nhip van hanh sau:

1. Chay job voi `APPLY_DOCUMENT_STORAGE_TERRAFORM=false` de xem plan.
2. Review Terraform plan.
3. Chay lai job voi `APPLY_DOCUMENT_STORAGE_TERRAFORM=true`.
4. Approve Jenkins input neu `DOCUMENT_STORAGE_REQUIRE_APPROVAL=true`.

Neu `APPLY_DOCUMENT_STORAGE_TERRAFORM=false`, job chi tao plan va khong sua GitOps values. Neu `APPLY_DOCUMENT_STORAGE_TERRAFORM=true`, job apply Terraform va push values len branch GitOps de Argo CD sync.

## 15. Checklist Thuc Hien

- [ ] Tao S3 bucket bang Terraform.
- [ ] Tao KMS customer managed key va alias.
- [ ] Bat KMS automatic rotation.
- [ ] Bat S3 default SSE-KMS.
- [ ] Bat S3 Bucket Keys.
- [ ] Bat S3 Block Public Access.
- [ ] Bat S3 Versioning.
- [ ] Tao IAM policy S3/KMS toi thieu cho document-service.
- [ ] Tao IRSA role bind vao `system:serviceaccount:docvault:docvault-document-service`.
- [ ] Them ServiceAccount support vao Helm chart.
- [ ] Sua `storage.service.ts` de support AWS SDK default credential chain.
- [ ] Them optional SSE-KMS headers cho `PutObjectCommand`.
- [ ] Cap nhat `document-service.yaml` cho S3 production.
- [ ] Bo static S3 access key/secret khoi AWS production secret.
- [ ] Copy object MinIO -> S3 voi cung object key.
- [ ] Sync Argo CD.
- [ ] Verify pod dung IRSA role.
- [ ] Verify object moi co `ServerSideEncryption=aws:kms`.
- [ ] Smoke test upload/download/preview/watermark/malware block.
- [ ] Giu MinIO readonly den khi het rollback window.
- [ ] Cau hinh Terraform S3 remote backend truoc khi dung Jenkins `APPLY_DOCUMENT_STORAGE_TERRAFORM=true`.
- [ ] Dam bao Jenkins agent co `terraform`, `yq`, `helm`, `git`.
- [ ] Dam bao Jenkins co AWS temporary credentials va `github-credentials`.
- [ ] Tao Jenkins Pipeline job rieng tro den `Jenkinsfile.storage`.
- [ ] Chay Jenkins storage job voi `APPLY_DOCUMENT_STORAGE_TERRAFORM=false` de xem plan.
- [ ] Chay Jenkins storage job voi `APPLY_DOCUMENT_STORAGE_TERRAFORM=true`, approve input, de push values vao `gitops-testing`.

## 16. Tai Lieu Tham Khao

- Amazon S3 SSE-KMS: https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingKMSEncryption.html
- AWS KMS automatic key rotation: https://docs.aws.amazon.com/kms/latest/developerguide/rotating-keys-enable.html
- Amazon EKS IRSA: https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html
- Assign IAM roles to Kubernetes service accounts: https://docs.aws.amazon.com/eks/latest/userguide/associate-service-account-role.html
