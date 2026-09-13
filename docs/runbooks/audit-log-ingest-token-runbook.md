# Audit log ingest token runbook

Huong dan nay dung khi trang Audit tren moi truong Kubernetes chi hien cac log
`SECURITY_RECOMMENDATIONS_VIEWED`, trong khi cac thao tac nhu create/upload/
approve/reject document khong xuat hien.

## Trieu chung

- Admin hoac compliance officer vao trang Audit nhung khong thay log cua cac user
  khac trong he thong.
- Bang Audit lap lai action `SECURITY_RECOMMENDATIONS_VIEWED`.
- Moi truong dev local van ghi audit log binh thuong.
- Kubernetes Secret `docvault-app-secrets` khong co key `AUDIT_INGEST_TOKEN`.

## Nguyen nhan

Trong Kubernetes, cac service khong doc file `.env` local. Cac bien runtime duoc
nap tu Kubernetes Secret `docvault-app-secrets`, secret nay lai duoc ExternalSecret
sync tu AWS Secrets Manager path `/docvault/<environment>/app`.

Neu `AUDIT_INGEST_TOKEN` thieu hoac khong khop:

- `metadata-service`, `document-service`, `workflow-service`, `gateway` se drop
  audit event khi emit sang audit-service.
- `audit-service` se tu choi request `POST /audit/events` neu token khong hop le.
- Log `SECURITY_RECOMMENDATIONS_VIEWED` van co the xuat hien vi action nay duoc
  audit-service tu ghi khi mo security summary, khong phu thuoc ingest token tu
  service khac.

Luu y: Argo CD `Synced/Healthy` chi cho biet manifest da sync, khong dam bao AWS
Secrets Manager da co day du key runtime.

## Kiem tra nhanh

Kiem tra Kubernetes Secret co key `AUDIT_INGEST_TOKEN` hay khong:

```powershell
$secret = kubectl -n docvault get secret docvault-app-secrets -o json | ConvertFrom-Json
"AUDIT_INGEST_TOKEN present: $([bool]$secret.data.AUDIT_INGEST_TOKEN)"
```

Kiem tra cac pod co nhan env hay khong:

```powershell
kubectl -n docvault exec deploy/docvault-metadata -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo metadata-token-present || echo metadata-token-missing'
kubectl -n docvault exec deploy/docvault-document-service -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo document-token-present || echo document-token-missing'
kubectl -n docvault exec deploy/docvault-workflow-service -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo workflow-token-present || echo workflow-token-missing'
kubectl -n docvault exec deploy/docvault-gateway -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo gateway-token-present || echo gateway-token-missing'
kubectl -n docvault exec deploy/docvault-audit-service -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo audit-token-present || echo audit-token-missing'
```

Kiem tra warning drop event:

```powershell
kubectl -n docvault logs deploy/docvault-metadata --tail=200 | Select-String "AUDIT_INGEST_TOKEN|Audit emit failed|audit event"
kubectl -n docvault logs deploy/docvault-document-service --tail=200 | Select-String "AUDIT_INGEST_TOKEN|Audit emit failed|audit event"
kubectl -n docvault logs deploy/docvault-workflow-service --tail=200 | Select-String "AUDIT_INGEST_TOKEN|Audit emit failed|audit event"
kubectl -n docvault logs deploy/docvault-gateway --tail=200 | Select-String "AUDIT_INGEST_TOKEN|Audit emit failed|audit event"
```

## Khac phuc

Khong sua truc tiep Kubernetes Secret `docvault-app-secrets`, vi ExternalSecret co
the sync de lai. Hay cap nhat secret nguon tren AWS Secrets Manager.

Vi du voi moi truong `testing`, secret source la `/docvault/testing/app`:

```powershell
# 1. Generate token manh. Khong paste token nay vao chat/log/ticket.
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$auditToken = [Convert]::ToBase64String($bytes)

# 2. Lay app secret hien tai tu AWS Secrets Manager.
$secretId = "/docvault/testing/app"
$current = aws secretsmanager get-secret-value --secret-id $secretId --query SecretString --output text | ConvertFrom-Json

# 3. Merge them AUDIT_INGEST_TOKEN, giu nguyen cac key cu.
$current | Add-Member -NotePropertyName AUDIT_INGEST_TOKEN -NotePropertyValue $auditToken -Force
$newJson = $current | ConvertTo-Json -Compress

# 4. Ghi version moi len Secrets Manager.
aws secretsmanager put-secret-value --secret-id $secretId --secret-string $newJson
```

Force ExternalSecret sync, hoac doi theo `refreshInterval` hien tai:

```powershell
kubectl -n docvault annotate externalsecret docvault-app-secrets force-sync="$(Get-Date -Format o)" --overwrite
kubectl -n docvault get externalsecret docvault-app-secrets
```

Verify Kubernetes Secret da co key:

```powershell
$secret = kubectl -n docvault get secret docvault-app-secrets -o json | ConvertFrom-Json
"AUDIT_INGEST_TOKEN present: $([bool]$secret.data.AUDIT_INGEST_TOKEN)"
```

Restart cac service doc env tu `docvault-app-secrets`:

```powershell
kubectl -n docvault rollout restart deploy/docvault-audit-service deploy/docvault-metadata deploy/docvault-document-service deploy/docvault-workflow-service deploy/docvault-gateway

kubectl -n docvault rollout status deploy/docvault-audit-service
kubectl -n docvault rollout status deploy/docvault-metadata
kubectl -n docvault rollout status deploy/docvault-document-service
kubectl -n docvault rollout status deploy/docvault-workflow-service
kubectl -n docvault rollout status deploy/docvault-gateway
```

Verify pod da nhan env:

```powershell
kubectl -n docvault exec deploy/docvault-audit-service -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo audit-token-present || echo audit-token-missing'
kubectl -n docvault exec deploy/docvault-metadata -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo metadata-token-present || echo metadata-token-missing'
kubectl -n docvault exec deploy/docvault-document-service -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo document-token-present || echo document-token-missing'
kubectl -n docvault exec deploy/docvault-workflow-service -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo workflow-token-present || echo workflow-token-missing'
kubectl -n docvault exec deploy/docvault-gateway -- sh -lc 'test -n "$AUDIT_INGEST_TOKEN" && echo gateway-token-present || echo gateway-token-missing'
```

## Xac thuc ket qua

Sau khi token da co trong tat ca pod, tao hanh dong moi trong app:

- create document
- upload file version
- submit approval
- approve hoac reject
- update metadata hoac ACL

Sau do vao trang Audit va refresh. Cac action moi nhu `DOCUMENT_CREATED`,
`DOCUMENT_UPLOADED`, `SUBMIT`, `APPROVE`, `REJECT`, `DOCUMENT_METADATA_UPDATED`
phai xuat hien.

Co the xem log audit-service neu van loi:

```powershell
kubectl -n docvault logs deploy/docvault-audit-service --tail=200 | Select-String "Invalid audit service token|Audit ingest token|Forbidden|audit"
```

## Luu y quan trong

- Audit event da bi drop truoc khi them `AUDIT_INGEST_TOKEN` se khong tu duoc
  backfill. Chi cac thao tac moi sau khi fix moi duoc ghi.
- Neu pod bao token present nhung audit-service bao `Invalid audit service token`,
  token giua caller va audit-service khong khop, hoac pod chua duoc restart sau
  khi ExternalSecret sync.
- Neu can rotate token khong downtime, set `AUDIT_INGEST_TOKEN_PREVIOUS` trong
  giai doan chuyen doi, sau do xoa khi tat ca service da nhan token moi.
- Neu trang Audit van hien nhieu `SECURITY_RECOMMENDATIONS_VIEWED`, do la log
  khi user mo security summary. Kiem tra query/filter frontend rieng neu muon an
  action nay khoi bang mac dinh.
