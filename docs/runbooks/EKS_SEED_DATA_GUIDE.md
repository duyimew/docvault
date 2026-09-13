# Hướng Dẫn Seed Data Trên EKS

Tài liệu này hướng dẫn cách seed data cho DocVault sau khi triển khai lên EKS
bằng Kubernetes và Argo CD. Mục tiêu là có đủ dữ liệu để demo, nhưng vẫn tránh
chạy nhầm các lệnh wipe/reset trên môi trường không mong muốn.

## 1. Nguyên Tắc Seed Trên EKS

DocVault nên seed theo 2 lớp:

1. **Baseline metadata seed**: tạo organization, membership, document mẫu, ACL
   và workflow history cơ bản. Lớp này chạy bằng Kubernetes Job trong namespace
   `docvault`, dùng image `metadata-service`.
2. **Demo business-flow seed**: tạo document qua Gateway API, upload file thật,
   submit/approve, tạo DLP/audit evidence. Lớp này chỉ chạy sau khi tất cả
   service đã healthy.

Không nên seed upload/version/audit trực tiếp bằng SQL hoặc Prisma vì sẽ bỏ qua
side effect quan trọng như MinIO object, checksum, workflow, audit hash-chain,
DLP và policy checks.

## 2. Điều Kiện Trước Khi Seed

Đảm bảo các thành phần sau đã được deploy và healthy:

- Namespace `docvault` đã tồn tại.
- Postgres đã chạy.
- MongoDB đã chạy.
- MinIO đã chạy và `minio-init-job` đã tạo bucket.
- Keycloak đã chạy và realm `docvault` đã import user/role demo.
- Argo CD app `docvault-metadata` đã sync xong migration job.
- Secret `docvault-app-secrets` đã tồn tại trong namespace `docvault`.

Kiểm tra nhanh:

```powershell
kubectl get pods -n docvault
kubectl get jobs -n docvault
kubectl get secret docvault-app-secrets -n docvault
```

Nếu dùng Argo CD:

```powershell
kubectl get applications -n argocd
```

## 3. Thứ Tự Đúng

Thứ tự khuyến nghị:

```text
1. Sync infra deps: Postgres, MongoDB, MinIO, Keycloak
2. Sync app services bằng Argo CD
3. Để metadata-service migration job chạy xong
4. Chạy baseline metadata seed bằng Kubernetes Job
5. Đảm bảo backend services healthy
6. Chạy demo business-flow seed nếu cần dữ liệu demo đầy đủ
7. Chạy smoke/e2e/manual test
```

## 4. Seed Baseline Metadata

Baseline metadata seed dùng script:

```bash
pnpm --filter metadata-service db:seed
```

Trên EKS, không chạy lệnh này từ laptop vào database trực tiếp. Hãy chạy trong
cluster bằng Kubernetes Job để nó dùng đúng network, secret và image runtime.

### 4.1. Tạo File Job

Tạo file tạm trên máy local, ví dụ `metadata-seed-job.yaml`:

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: metadata-seed
  namespace: docvault
spec:
  backoffLimit: 1
  template:
    spec:
      restartPolicy: Never
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        runAsGroup: 1001
        fsGroup: 1001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: seed
          image: daithang59/metadata-service:v33
          imagePullPolicy: Always
          workingDir: /app/services/metadata-service
          command:
            - sh
            - -c
            - pnpm db:seed
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
          volumeMounts:
            - name: tmp
              mountPath: /tmp
          env:
            - name: NODE_ENV
              value: production
            - name: KEYCLOAK_BASE_URL
              value: http://keycloak:8080
            - name: KEYCLOAK_REALM
              value: docvault
            - name: KEYCLOAK_CLIENT_ID
              value: docvault-gateway
            - name: KEYCLOAK_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: docvault-app-secrets
                  key: KEYCLOAK_CLIENT_SECRET
                  optional: true
          envFrom:
            - secretRef:
                name: docvault-app-secrets
      volumes:
        - name: tmp
          emptyDir: {}
```

Lưu ý:

- Đổi `image: daithang59/metadata-service:v33` thành tag/digest đang deploy nếu
  cluster đang dùng tag khác.
- Job trên **không** bật `DOCVAULT_ALLOW_METADATA_RESEED=true`, nên nó sẽ seed
  dạng repeatable/upsert và không wipe toàn bộ metadata.
- Chỉ bật `DOCVAULT_ALLOW_METADATA_RESEED=true` khi bạn thực sự muốn reset demo
  database và chắc chắn đây không phải production data.

### 4.2. Chạy Job

```powershell
kubectl apply -f metadata-seed-job.yaml
kubectl logs -n docvault job/metadata-seed -f
```

Kỳ vọng log có dạng:

```text
Seeding metadata database...
Reset disabled. Set DOCVAULT_ALLOW_METADATA_RESEED=true for a local full wipe.
Seeded 5 baseline documents.
```

Sau khi thành công:

```powershell
kubectl get job metadata-seed -n docvault
kubectl delete job metadata-seed -n docvault
```

Nếu muốn chạy lại:

```powershell
kubectl delete job metadata-seed -n docvault --ignore-not-found
kubectl apply -f metadata-seed-job.yaml
kubectl logs -n docvault job/metadata-seed -f
```

## 5. Khi Nào Mới Được Reset Metadata

Chỉ reset khi đây là môi trường demo/dev và bạn muốn xóa sạch metadata cũ.

Thêm env này vào Job:

```yaml
- name: DOCVAULT_ALLOW_METADATA_RESEED
  value: "true"
```

Không nên commit Job reset vào GitOps branch nếu branch đó sync tự động. Nên
chạy reset Job thủ công, quan sát log, sau đó xóa Job.

## 6. Seed Demo Business Flow

Baseline seed tạo metadata mẫu, nhưng chưa tạo file upload thật trong MinIO.
Nếu muốn demo đầy đủ upload/version/workflow/audit/DLP, chạy demo seed qua
Gateway API.

Script hiện có:

```bash
pnpm run seed:demo
```

Script này nên chạy sau khi các service sau đã healthy:

- `docvault-gateway`
- `docvault-metadata`
- `docvault-document-service`
- `docvault-workflow-service`
- `docvault-audit-service`
- `docvault-notification-service`
- Keycloak
- MinIO

### 6.1. Cách Để Chạy Từ Laptop Bằng Port Forward

Vì gateway trong Kubernetes đang là `ClusterIP`, port-forward gateway:

```powershell
kubectl port-forward -n docvault svc/docvault-gateway 3000:3000
```

Nếu Keycloak NodePort `30080` dùng được từ browser/laptop:

```powershell
$env:GATEWAY_URL="http://localhost:3000"
$env:KEYCLOAK_BASE_URL="http://<NODE_EXTERNAL_IP>:30080"
$env:DOCVAULT_ALLOW_REMOTE_DEMO_SEED="true"
pnpm run seed:demo
```

Nếu muốn port-forward Keycloak thay vì dùng NodePort:

```powershell
kubectl port-forward -n docvault svc/keycloak 8080:8080
```

Rồi chạy:

```powershell
$env:GATEWAY_URL="http://localhost:3000"
$env:KEYCLOAK_BASE_URL="http://localhost:8080"
$env:DOCVAULT_ALLOW_REMOTE_DEMO_SEED="true"
pnpm run seed:demo
```

### 6.2. Tạo Bộ Demo Riêng Cho Mỗi Lần Thuyết Trình

Mặc định script dùng run id `local`. Nếu muốn tạo bộ demo mới:

```powershell
$env:DOCVAULT_DEMO_SEED_RUN_ID="presentation-1"
$env:GATEWAY_URL="http://localhost:3000"
$env:KEYCLOAK_BASE_URL="http://localhost:8080"
$env:DOCVAULT_ALLOW_REMOTE_DEMO_SEED="true"
pnpm run seed:demo
```

Tên document sẽ có dạng:

```text
Seed Demo presentation-1 - Employee Handbook
Seed Demo presentation-1 - Board Packet
Seed Demo presentation-1 - Finance Team Forecast
Seed Demo presentation-1 - DLP Contact Sheet
```

### 6.3. Malware Probe Tùy Chọn

Mặc định demo seed không upload EICAR vì ClamAV trên lần boot đầu có thể mất
thời gian tải database.

Chỉ bật khi ClamAV đã healthy:

```powershell
$env:DOCVAULT_SEED_INCLUDE_MALWARE_PROBE="true"
pnpm run seed:demo
```

## 7. Kiểm Tra Sau Khi Seed

Kiểm tra pod/job:

```powershell
kubectl get pods -n docvault
kubectl get jobs -n docvault
```

Kiểm tra gateway:

```powershell
kubectl port-forward -n docvault svc/docvault-gateway 3000:3000
curl http://localhost:3000/health
```

Đăng nhập bằng các user demo:

| User | Password | Vai trò |
| --- | --- | --- |
| `viewer1` | `Passw0rd!` | viewer |
| `editor1` | `Passw0rd!` | editor |
| `approver1` | `Passw0rd!` | approver |
| `co1` | `Passw0rd!` | compliance officer |
| `admin1` | `Passw0rd!` | admin |

Luôn test tối thiểu các flow:

1. `editor1` thấy document mình tạo/upload.
2. `approver1` approve document pending.
3. `viewer1` bị deny với confidential/internal document không đủ quyền.
4. `co1` xem được audit/security/retention evidence.
5. `admin1` xem được admin/compliance controls.

## 8. Troubleshooting

### Metadata Seed Lỗi Không Resolve Được Keycloak User

Kiểm tra:

```powershell
kubectl logs -n docvault deploy/keycloak
kubectl port-forward -n docvault svc/keycloak 8080:8080
```

Thử endpoint realm:

```powershell
curl http://localhost:8080/realms/docvault/.well-known/openid-configuration
```

Nếu realm chưa có user demo, Keycloak có thể chưa import realm hoặc pod đã dùng
data cũ. Trong setup hiện tại Keycloak dùng `emptyDir`, nên re-create pod thường
sẽ import lại realm.

### Metadata Seed Lỗi Database

Kiểm tra secret:

```powershell
kubectl get secret docvault-app-secrets -n docvault -o yaml
```

Kiểm tra metadata migration job:

```powershell
kubectl get jobs -n docvault | Select-String metadata
kubectl logs -n docvault job/docvault-metadata-migrate
```

Tên job migration có thể khác tùy release name của Argo CD/Helm.

### Demo Seed Lỗi Gateway

Kiểm tra port-forward và health:

```powershell
kubectl port-forward -n docvault svc/docvault-gateway 3000:3000
curl http://localhost:3000/health
```

Kiểm tra service URLs trong gateway values:

```powershell
kubectl describe deploy docvault-gateway -n docvault
```

### Demo Seed Lỗi MinIO

Kiểm tra bucket init job:

```powershell
kubectl get jobs -n docvault | Select-String minio
kubectl logs -n docvault job/minio-init
```

Tên job có thể khác theo manifest thực tế.

## 9. Khuyến Nghị Cho GitOps

Nên để Argo CD tự động chạy:

- infra deps
- application deployments
- metadata migration PreSync job

Không nên để Argo CD tự động chạy demo seed mỗi lần sync, vì seed demo tạo
document/upload/audit side effect và có thể làm bản demo bị nhiều dữ liệu trùng.

Nên chạy thủ công:

- `metadata-seed` Job sau deploy lần đầu hoặc sau khi reset database demo
- `pnpm run seed:demo` sau khi tất cả service healthy và cần tạo evidence mới

## 10. Tóm Tắt Lệnh Nhanh

Baseline metadata:

```powershell
kubectl apply -f metadata-seed-job.yaml
kubectl logs -n docvault job/metadata-seed -f
kubectl delete job metadata-seed -n docvault
```

Demo business flow:

```powershell
kubectl port-forward -n docvault svc/docvault-gateway 3000:3000
kubectl port-forward -n docvault svc/keycloak 8080:8080

$env:GATEWAY_URL="http://localhost:3000"
$env:KEYCLOAK_BASE_URL="http://localhost:8080"
$env:DOCVAULT_ALLOW_REMOTE_DEMO_SEED="true"
pnpm run seed:demo
```
