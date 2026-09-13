# DocVault DevSecOps

DocVault là dự án quản lý tài liệu bảo mật được xây dựng theo kiến trúc microservices, đồng thời là một bài toán DevSecOps end-to-end: code được kiểm thử, quét bảo mật, build thành container image, ký/định danh image, cập nhật GitOps và triển khai lên Kubernetes/EKS bằng Argo CD.

Repo này không chỉ chứa application. Phần quan trọng của dự án là quy trình DevSecOps bao quanh application: CI/CD bằng Jenkins, security gates, policy-as-code, private registry Harbor, Terraform EKS, GitOps, post-deploy smoke test, DAST và observability.

## Mục Tiêu Dự Án

- Xây dựng hệ thống quản lý tài liệu có RBAC/ACL, workflow phê duyệt, audit tamper-evident, DLP, malware scan, retention và evidence center.
- Mô hình hóa pipeline DevSecOps có thể chứng minh bằng report/artifact: secret scan, SAST, SCA, IaC scan, filesystem/image scan, policy-as-code và DAST.
- Triển khai theo GitOps: Jenkins chỉ cập nhật image reference/Helm values, Argo CD là bộ điều phối trạng thái chạy trên cluster.
- Tách local development, CI/CD, Kubernetes manifests, Terraform và runtime evidence thành các lớp rõ ràng để dễ demo, kiểm tra và mở rộng.

## Kiến Trúc DocVault

![Kiến trúc application DocVault](report/images/docvault-architecture.drawio.png)

Sơ đồ trên mô tả kiến trúc application DocVault: frontend, gateway, các microservice backend, authentication, metadata database, audit database và object storage.

### Kiến Trúc Triển Khai EKS

![Kiến trúc triển khai DocVault trên EKS](report/images/infra/docvault-eks-architecture.png)

Sơ đồ EKS mô tả lớp triển khai cloud-native: ingress/web, Kubernetes workloads, Harbor registry, GitOps, secrets, storage và observability.

Các vai trò chính trong hệ thống:

| Role | Vai trò |
| --- | --- |
| `viewer` | Xem metadata/tài liệu được phép và tải tài liệu đã publish khi policy cho phép. |
| `editor` | Tạo tài liệu, upload file, submit, archive tài liệu của mình. |
| `approver` | Duyệt hoặc từ chối tài liệu đang chờ phê duyệt. |
| `compliance_officer` | Xem audit/compliance evidence, nhưng không được preview, stream, presign hoặc download nội dung file. |
| `admin` | Quản trị hệ thống, thành viên, retention và các thao tác nhạy cảm. |

Vòng đời tài liệu chính:

![State machine vòng đời tài liệu trong DocVault](report/images/docvault_document_lifecycle_state.drawio.png)

## Luồng DevSecOps

![Luồng CI/CD và GitOps của DocVault](report/images/infra/CICDPipelineFlow.png)

Pipeline chính nằm ở `Jenkinsfile`; các bước tái sử dụng nằm trong `vars/*.groovy`. Cấu hình mặc định hiện hướng tới Harbor registry (`harbor.docvault.id.vn`), GitOps branch cấu hình qua `GITOPS_BRANCH`, và triển khai Kubernetes bằng Helm chart chung trong `infra/k8s/charts/docvault-service`.

### Luồng Triển Khai IaC Lên AWS/EKS

![Luồng triển khai IaC lên AWS EKS](report/images/aws-eks-iac-deployment-flow.drawio.png)

Sơ đồ trên mô tả luồng thay đổi hạ tầng bằng Infrastructure as Code: developer tạo PR, Jenkins chạy Terraform và Checkov, các bước validation/plan phải pass trước khi approval và `terraform apply` tạo AWS foundation, Kubernetes platform layer và runtime DocVault.

## DevSecOps Components

| Thành phần | Vai trò trong dự án |
| --- | --- |
| Jenkins | Điều phối pipeline CI/CD, chạy quality/security gates, build/scan/push image và cập nhật GitOps. |
| `vars/*.groovy` | Jenkins Shared Library cho từng bước: install, test, scan, build, push, GitOps, Argo health, DAST. |
| SonarQube | SAST và Quality Gate. |
| OWASP Dependency-Check | SCA cho dependency, dùng cache NVD để ổn định pipeline. |
| TruffleHog / Gitleaks | Secret scanning, chặn credential bị commit. |
| Trivy | Quét filesystem trước build và quét container image sau build. |
| Checkov | Quét Terraform, Kubernetes và Helm/IaC. |
| Kyverno CLI | Policy-as-code cho manifest Kubernetes đã render. |
| Harbor | Private container registry cho image DocVault, hỗ trợ RBAC/robot account và scan image. |
| Cosign | Ký image digest và attestation SBOM khi bật `SIGN_IMAGES`. |
| Terraform | Tạo AWS VPC, EKS, node group, IRSA, S3/KMS và các IAM boundary. |
| Argo CD | GitOps app-of-apps, sync wave hạ tầng -> application -> ingress/observability. |
| Prometheus, Grafana, Loki | Metrics, dashboard và log tập trung cho môi trường Kubernetes. |
| OWASP ZAP | DAST baseline scan sau khi có endpoint deploy thật. |

## Security Gates Chính

Pipeline được thiết kế để fail sớm khi có lỗi nghiêm trọng:

- Secret scan fail khi phát hiện secret thật trong repository.
- Unit test/lint/build fail khi code không đạt chất lượng tối thiểu.
- Dependency-Check fail với CVSS cao, trừ khi có exception được ghi nhận.
- Trivy filesystem/image scan fail với High/Critical findings theo policy hiện tại.
- SonarQube Quality Gate có thể bắt buộc bằng `ENFORCE_SONAR_QG=true`.
- Checkov validate Terraform/Kubernetes/Helm trước khi deploy.
- Kyverno CLI chặn manifest thiếu resource limits, chạy root/privileged, dùng `latest`, secret literal hoặc image không đến từ Harbor/digest.
- ZAP fail khi có High/Critical finding sau deploy.

Các report thường được archive bởi Jenkins:

```text
secret-scan-report/
dependency-check-report/
trivy-fs-report/
checkov-report/
policy-report/
policy-rendered/
zap-report/
```

## Hạ Tầng Và GitOps

Local development dùng Docker Compose:

- PostgreSQL cho `metadata-service`.
- MongoDB cho `audit-service` và `notification-service`.
- MinIO/S3 cho file tài liệu.
- Keycloak cho identity, role, group.
- ClamAV cho malware scanning.
- Mongo Express và Prisma Studio để xem dữ liệu khi cần.

Môi trường Kubernetes/EKS dùng:

- Terraform stack tại `infra/terraform/aws-eks`.
- Helm chart dùng chung tại `infra/k8s/charts/docvault-service`.
- Helm values từng service tại `infra/k8s/values`.
- Argo CD root app tại `infra/argocd-bootstrap/docvault-root.yaml`.
- Argo CD child apps tại `infra/argocd-apps`.
- External Secrets Operator đọc secret từ AWS Secrets Manager.
- Sync wave:
  - wave 0: infra dependencies.
  - wave 1: DocVault services/web.
  - wave 2: ingress, monitoring, logging.

## Cấu Trúc Repository

```text
docvault-devsecops/
|-- apps/
|   `-- web/                         # Next.js frontend
|-- services/
|   |-- gateway/                     # API gateway, auth, RBAC, proxy
|   |-- metadata-service/            # Metadata, ACL, retention, policy
|   |-- document-service/            # Upload/preview/download, MinIO/S3, DLP/malware
|   |-- workflow-service/            # Document lifecycle workflow
|   |-- audit-service/               # MongoDB audit log, hash chain, security summary
|   `-- notification-service/        # Notification sink/in-app notifications
|-- libs/
|   |-- auth/                        # Shared auth/RBAC helpers
|   |-- contracts/                   # OpenAPI/events contracts
|   `-- throttler/                   # Shared throttling utilities
|-- infra/
|   |-- docker-compose.dev.yml       # Local dependencies
|   |-- terraform/                   # AWS/EKS IaC
|   |-- k8s/                         # Helm, Kustomize, ingress, CI RBAC, Harbor
|   |-- argocd-bootstrap/            # Argo CD app-of-apps root
|   `-- argocd-apps/                 # Argo CD child applications
|-- policies/
|   `-- kyverno/                     # Policy-as-code gates
|-- vars/                            # Jenkins Shared Library steps
|-- docs/                            # Runbooks, evidence, setup guides
|-- Jenkinsfile                      # Main DevSecOps pipeline
|-- Jenkinsfile.storage              # Storage/S3-KMS GitOps pipeline
`-- package.json                     # pnpm/Turbo workspace
```

## Chạy Local Nhanh

Yêu cầu:

- Node.js 20+
- pnpm 9.15.0
- Docker Desktop hoặc Docker Engine + Docker Compose

Cài dependency:

```bash
pnpm install
```

Chuẩn bị env local:

```bash
cp infra/.env.example infra/.env
cp .env.example .env
```

Tạo `.env` cho từng service từ file `.env.example` tương ứng. Với frontend, tạo `apps/web/.env.local` nếu cần override:

```env
NEXT_PUBLIC_APP_NAME=DocVault
NEXT_PUBLIC_API_BASE_URL=/api
GATEWAY_URL=http://localhost:3000
FRONTEND_URL=http://localhost:3006
```

Start local infrastructure:

```bash
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d
```

Migrate và seed metadata:

```bash
pnpm --filter metadata-service prisma:deploy
pnpm run seed:metadata
```

Start backend đúng thứ tự:

```bash
pnpm start:sequential
```

Start frontend:

```bash
pnpm --filter web dev
```

Mở web:

```text
http://localhost:3006
```

Nếu muốn chạy frontend ở port khác:

```bash
pnpm --filter web dev -- --port 3100
```

## Endpoint Quan Trọng

| Thành phần | URL |
| --- | --- |
| Web | `http://localhost:3006` |
| Gateway health | `http://localhost:3000/api/health` |
| Gateway Swagger | `http://localhost:3000/api/docs` |
| metadata-service Swagger | `http://localhost:3001/docs` |
| document-service Swagger | `http://localhost:3002/docs` |
| workflow-service Swagger | `http://localhost:3003/docs` |
| audit-service Swagger | `http://localhost:3004/docs` |
| notification-service Swagger | `http://localhost:3005/docs` |
| Keycloak | `http://localhost:8080` |
| Mongo Express | `http://localhost:8081` |
| MinIO Console | `http://localhost:9001` |

## Tài Khoản Demo

Password mặc định cho các seeded user:

```text
Passw0rd!
```

| Username | Role |
| --- | --- |
| `viewer1` | `viewer` |
| `editor1` | `editor` |
| `approver1` | `approver` |
| `co1` | `compliance_officer` |
| `admin1` | `admin` |

## Verification

Kiểm tra workspace:

```bash
pnpm lint
pnpm test
pnpm build
```

Kiểm tra E2E runtime sau khi backend, frontend và infra đã chạy:

```bash
pnpm test:e2e
```

Các luồng E2E chính gồm:

- Không có token hoặc token hết hạn bị chặn.
- Viewer không thể tạo tài liệu.
- Editor tạo/upload/submit tài liệu.
- Approver approve/reject workflow.
- ACL `GROUP`, DLP, malware block, retention và audit security summary.
- Compliance officer xem audit/evidence nhưng không được tải file.

## Tài Liệu Liên Quan

| Tài liệu | Khi nào đọc |
| --- | --- |
| [docs/runbooks/RUN_PROJECT.md](docs/runbooks/RUN_PROJECT.md) | Chạy local stack từng bước. |
| [docs/runbooks/DEPLOYMENT_RUNBOOK.md](docs/runbooks/DEPLOYMENT_RUNBOOK.md) | Runbook vận hành service, env và troubleshooting. |
| [docs/devsecops/DEVSECOPS_PIPELINE_SETUP_GUIDE.md](docs/devsecops/DEVSECOPS_PIPELINE_SETUP_GUIDE.md) | Cấu hình Jenkins/SonarQube/GitOps pipeline. |
| [docs/runbooks/TEAM_SETUP_DEPLOYMENT_GUIDE.md](docs/runbooks/TEAM_SETUP_DEPLOYMENT_GUIDE.md) | Hướng dẫn EKS, Argo CD, Jenkins, ZAP và observability cho team. |
| [vars/README.md](vars/README.md) | Ý nghĩa từng Jenkins Shared Library step. |
| [infra/README.md](infra/README.md) | Bản đồ hạ tầng, GitOps và Kubernetes. |
| [infra/terraform/aws-eks/README.md](infra/terraform/aws-eks/README.md) | Terraform stack tạo AWS EKS và IAM/IRSA. |
| [infra/argocd-apps/README.md](infra/argocd-apps/README.md) | Argo CD child applications và sync wave. |
| [policies/kyverno/README.md](policies/kyverno/README.md) | Policy-as-code gates cho Kubernetes. |
| [docs/guides/DANH_SACH_TINH_NANG_WEB.md](docs/guides/DANH_SACH_TINH_NANG_WEB.md) | Danh sách tính năng web hiện tại. |
| [docs/architecture/API_CONTRACT.md](docs/architecture/API_CONTRACT.md) | Contract API chính qua Gateway. |
| [docs/architecture/web-security-evidence.md](docs/architecture/web-security-evidence.md) | Evidence runtime security của web/application. |
| [docs/devsecops/security-sca-triage.md](docs/devsecops/security-sca-triage.md) | SCA triage, package đã fix và exception. |
| [docs/devsecops/pipeline-hardening-summary.md](docs/devsecops/pipeline-hardening-summary.md) | Tóm tắt cải tiến pipeline, DAST và observability. |

## Quy Ước Bảo Mật

- Không commit `.env`, secret, kubeconfig thật, `terraform.tfvars`, Terraform state hoặc plan file.
- Secret runtime trên EKS nên đi qua AWS Secrets Manager và External Secrets Operator.
- Không dùng image tag `latest` cho workload DocVault.
- Image deploy nên được pin bằng digest và pull từ Harbor/private registry.
- Compliance officer luôn bị chặn khỏi nội dung file, kể cả khi có metadata visibility.
- Các exception security phải có lý do, mitigation và follow-up rõ ràng.

## Thành Viên

| Họ tên | Vai trò |
| --- | --- |
| Huỳnh Lê Đại Thắng | Author |
| Nguyễn Trường Duy | Coauthor |
