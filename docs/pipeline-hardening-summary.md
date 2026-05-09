# Tóm Tắt Cải Tiến Pipeline DocVault

Cập nhật: 2026-05-10

Tài liệu này tóm tắt các cải tiến mới đã thực hiện cho Jenkins pipeline, GitOps deployment, DAST và observability sau khi MVP DocVault đã chạy ổn định trên EKS.

## Trạng Thái Xác Minh Mới Nhất

Pipeline Jenkins đã chạy xanh toàn bộ ở build `#61` trên branch `devsecops-pipeline`.

Các stage đã pass:

- `Pre-build Security`: Checkov, Terraform validate, SonarQube, Dependency Check, Trivy FS, Unit Tests.
- `Build & Scan Services`: build/scan các service chính theo batch.
- `Push & GitOps`: push image và cập nhật branch `gitops-testing`.
- `Argo CD Health Check`: pass với kubeconfig read-only của ServiceAccount `jenkins-argocd-reader`.
- `Post-deploy Smoke Test`: pass với `GET /` và `GET /api/health`.
- `DAST - OWASP ZAP`: pass và archive report.

Jenkins container hiện dùng image `myjenkins-blueocean:lts-jdk21`, đã có:

```text
kubectl v1.35.0
docker buildx v0.33.0
```

## 1. Tối Ưu Pipeline Chạy Song Song

Pipeline Jenkins đã được tối ưu để các bước độc lập có thể chạy song song, giúp giảm thời gian build nhưng vẫn giữ đầy đủ bằng chứng security scan.

Các phần đang chạy song song:

- `Pre-build Security` chạy nhiều bước song song:
  - IaC Checkov scan.
  - Terraform validate.
  - SAST SonarQube.
  - SCA OWASP Dependency Check.
  - Trivy filesystem scan.
  - Unit tests.
- Build service image và scan image được chia batch trong `buildAndScan`.
- Push Docker image được chia batch trong `pushAndGitOps`.

Implementation:

```text
Jenkinsfile
vars/buildAndScan.groovy
vars/pushAndGitOps.groovy
```

Cấu hình quan trọng:

```text
buildParallelism: 3
pushParallelism: 3
```

Hai giá trị này được khai báo trong:

```text
vars/docvaultConfig.groovy
```

## 2. Cấu Hình OWASP ZAP DAST

OWASP ZAP được cấu hình là stage DAST tùy chọn sau deploy.

Jenkins parameters khuyến nghị:

```text
RUN_ZAP=true
ZAP_TARGET=http://<node-external-ip>:30006
```

Nếu `ZAP_TARGET` để trống, pipeline sẽ dùng fallback:

```text
ZAP_TARGET = DEPLOY_TARGET_URL
```

Hành vi hiện tại:

- Kiểm tra target reachable trước khi chạy ZAP.
- Chạy image `ghcr.io/zaproxy/zaproxy:stable`.
- Sinh report HTML và JSON.
- Archive report làm Jenkins artifacts.
- Dùng `-I` để các warning không làm fail MVP demo.
- Sau khi có report, pipeline đọc `zap_report.json`; nếu có High/Critical risk code thì fail pipeline.

Artifacts được sinh:

```text
zap-report/zap_report.html
zap-report/zap_report.json
```

Implementation:

```text
vars/dastZap.groovy
vars/postCleanup.groovy
```

Quy tắc chọn target:

```text
Đúng:       http://<node-external-ip>:30006
Không dùng: http://<node-external-ip>:30006/api
```

Lý do: `/api` root có thể trả `404`, không phù hợp làm URL khởi đầu cho ZAP baseline crawl. Web app vẫn gọi API qua `/api/...` bình thường.

## 3. Cấu Hình Observability

Prometheus, Grafana, Loki và promtail được cấu hình thông qua Argo CD Applications.

Argo CD app manifests:

```text
infra/argocd-apps/monitoring.yaml
infra/argocd-apps/loki.yaml
```

Các thành phần:

- `monitoring-stack`: kube-prometheus-stack, gồm Prometheus, Grafana và Alertmanager.
- `loki-stack`: Loki và promtail để thu thập log Kubernetes tập trung.

Cấu hình hiện tại:

- Argo CD automated sync đã bật.
- Self-heal đã bật.
- Grafana có datasource cho Prometheus, Alertmanager và Loki.
- Loki nhận log từ namespace `docvault` thông qua promtail.

Lệnh apply:

```powershell
kubectl apply -f infra\argocd-apps\monitoring.yaml
kubectl apply -f infra\argocd-apps\loki.yaml
```

Lệnh kiểm tra:

```powershell
kubectl get app monitoring-stack loki-stack -n argocd
kubectl get pods -n monitoring
```

Mở Grafana và Loki ở local:

```powershell
kubectl port-forward svc/monitoring-stack-grafana -n monitoring 3000:80
kubectl port-forward svc/loki-stack -n monitoring 3100:3100
```

Bằng chứng Grafana/Loki nên chụp:

- Dashboard pod health.
- Dashboard CPU/RAM.
- Dashboard workload status.
- Grafana Explore với datasource Loki và query:

```text
{namespace="docvault"}
```

## 4. Post-deploy Smoke Test

Pipeline đã có smoke test tự động để kiểm tra web app thật sau khi deploy lên EKS.

Jenkins parameter mới:

```text
DEPLOY_TARGET_URL=http://<node-external-ip>:30006
```

Khi `DEPLOY_TARGET_URL` được set, Jenkins sẽ chạy stage `Post-deploy Smoke Test` sau `Push & GitOps`.

Stage này kiểm tra:

```text
GET <DEPLOY_TARGET_URL>
GET <DEPLOY_TARGET_URL>/api/health
```

Kết quả kỳ vọng:

- Web root trả HTTP `2xx` hoặc `3xx`.
- Health endpoint trả HTTP `2xx`.
- Stage retry khoảng 5 phút trước khi fail.

Implementation:

```text
vars/postDeploySmokeTest.groovy
```

## 5. Argo CD Health Check

Pipeline đã có health gate tùy chọn cho Argo CD.

Jenkins parameters mới:

```text
RUN_ARGO_HEALTH_CHECK=true
ARGOCD_NAMESPACE=argocd
ARGOCD_APPS=docvault-gateway docvault-metadata docvault-document-service docvault-workflow-service docvault-audit-service docvault-notification-service docvault-web
ARGOCD_TIMEOUT_SECONDS=300
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
```

Khi bật, Jenkins sẽ đợi tất cả Argo CD Application được cấu hình đạt:

```text
sync=Synced
health=Healthy
```

Health gate mặc định chỉ kiểm tra các app service chính. `docvault-infra-deps` không nằm trong default vì app này có thể `OutOfSync` do hook/secret/runtime drift và nên được xử lý riêng, không chặn pipeline ứng dụng.

Implementation:

```text
vars/argocdHealthCheck.groovy
```

Điều kiện cần:

- Jenkins agent có `kubectl`.
- Jenkins agent có kubeconfig/RBAC cho phép đọc Argo CD `Application` trong namespace `argocd`.
- Jenkins credential dạng Secret file có ID `jenkins-argocd-kubeconfig`.

Nếu Jenkins chưa có quyền truy cập cluster, giữ:

```text
RUN_ARGO_HEALTH_CHECK=false
```

và chụp bằng chứng Argo CD `Synced/Healthy` thủ công.

## 6. Policy Security Gate

Pipeline đã chốt policy security gate rõ hơn:

- Critical/High findings: fail pipeline hoặc phải có exception record.
- Medium/Low findings: có thể chấp nhận cho MVP demo nếu được ghi nhận trong tài liệu triage.
- ZAP warnings: archive report và review, không chặn MVP demo.
- ZAP High/Critical: fail pipeline.

## 7. SCA Gate Policy

OWASP Dependency Check đã có rule chặn rõ hơn.

Policy hiện tại:

- Critical/High hoặc CVSS `>= 7`: fail pipeline.
- Nếu muốn chấp nhận rủi ro, phải có exception record.
- Medium/Low có thể chấp nhận cho MVP demo nếu đã ghi trong SCA triage.

Implementation:

```text
vars/dependencyCheck.groovy
docs/security-sca-triage.md
```

Dependency Check dùng:

```text
--failOnCVSS 7
```

Artifacts được archive:

```text
dependency-check-report/*.html
dependency-check-report/*.json
```

## 8. Trivy Filesystem Gate

Trivy filesystem scan hiện chặn High và Critical findings.

Policy hiện tại:

```text
--severity HIGH,CRITICAL --exit-code 1
```

Trivy FS chạy trên một bản copy tạm của workspace, trong đó các thư mục generated/cache được loại bỏ trước khi scan:

```text
.git
node_modules
.pnpm-store
.turbo
.next
dist
coverage
dependency-check-report
checkov-report
zap-report
*/.terraform
*/.terraform/*
```

Trivy FS cũng giới hạn misconfiguration scanner vào Dockerfile, Kubernetes và Helm:

```text
--misconfig-scanners dockerfile,kubernetes,helm
```

Lý do: Terraform đã có stage riêng là `Terraform Validate` và `IaC - Checkov Scan`. Không để Trivy FS quét thư mục `.terraform/modules` vì đây là downloaded module/vendor output sau `terraform init`, dễ tạo finding trùng hoặc không thuộc code nguồn trực tiếp của nhóm.

Implementation:

```text
vars/trivyFsScan.groovy
```

## 9. Thứ Tự Stage Jenkins Hiện Tại

Thứ tự pipeline chính:

```text
Checkout & Initialize Config
Prevent Loop
System Check
Install
Pre-build Security
Build & Scan Services
Push & GitOps
Argo CD Health Check
Post-deploy Smoke Test
DAST - OWASP ZAP
End
```

Chi tiết các phần chính:

```text
Pre-build Security
  - IaC - Checkov Scan
  - IaC - Terraform Validate
  - SAST - SonarQube
  - SCA - Dependency Check
  - Trivy FS Scan
  - Unit Tests

Build & Scan Services
  - Build service images theo batch
  - Scan images bằng Trivy

Push & GitOps
  - Push images theo batch
  - Cập nhật GitOps branch
```

`Argo CD Health Check` chỉ chạy khi:

```text
RUN_ARGO_HEALTH_CHECK=true
```

`Post-deploy Smoke Test` chỉ chạy khi:

```text
DEPLOY_TARGET_URL được set
```

`DAST - OWASP ZAP` chỉ chạy khi:

```text
RUN_ZAP=true
```

## 10. Parameters Khuyến Nghị Khi Demo

Với EKS NodePort hiện tại:

```text
FORCE_BUILD_ALL=false
GITOPS_BRANCH=gitops-testing
DEPLOY_TARGET_URL=http://52.221.234.153:30006
RUN_ARGO_HEALTH_CHECK=true
ARGOCD_NAMESPACE=argocd
ARGOCD_TIMEOUT_SECONDS=300
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
RUN_ZAP=true
ZAP_TARGET=http://52.221.234.153:30006
```

Nếu Jenkins chưa truy cập được Kubernetes:

```text
RUN_ARGO_HEALTH_CHECK=false
```

Sau đó kiểm tra thủ công:

```powershell
kubectl get applications -n argocd
kubectl get pods -n docvault
```

## 11. Bằng Chứng Cần Chụp

Nên chụp các bằng chứng sau cho báo cáo/demo:

- Jenkins build pipeline xanh.
- Các stage parallel trong `Pre-build Security`.
- Log build/scan service theo batch.
- Log push image theo batch.
- Stage `Argo CD Health Check` pass, hoặc ảnh Argo CD UI hiển thị `Synced/Healthy`.
- Stage `Post-deploy Smoke Test` pass.
- Stage `DAST - OWASP ZAP` pass.
- ZAP HTML/JSON artifacts đã archive.
- Dependency Check HTML/JSON artifacts đã archive.
- SCA triage record đã cập nhật.
- Web app trên EKS mở thành công.
- Login, upload, preview và download đã test thủ công.
- Screenshot Grafana dashboard.
- Screenshot Loki logs với query:

```text
{namespace="docvault"}
```
