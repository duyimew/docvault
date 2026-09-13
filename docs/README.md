# DocVault Documentation Index

Tài liệu dự án DocVault DevSecOps được tổ chức thành các thư mục chuyên biệt dưới đây:

---

## 1. Mục Lục Theo Thư Mục

### 📂 [`architecture/`](./architecture/) — Kiến Trúc & Đặc Tả Hệ Thống
- [`API_CONTRACT.md`](./architecture/API_CONTRACT.md): OpenAPI contract qua API Gateway.
- [`ERD.md`](./architecture/ERD.md): Sơ đồ quan hệ thực thể theo từng bounded context.
- [`PROJECT_STATUS.md`](./architecture/PROJECT_STATUS.md): Báo cáo hiện trạng kỹ thuật và runtime của repo.
- [`web-security-evidence.md`](./architecture/web-security-evidence.md): Bằng chứng thiết kế và thực thi an toàn thông tin (JWT, MFA, Key Rotation, Hash Chain).

### 📂 [`runbooks/`](./runbooks/) — Cẩm Nang Triển Khai & Vận Hành
- [`DEPLOYMENT_RUNBOOK.md`](./runbooks/DEPLOYMENT_RUNBOOK.md): Hướng dẫn triển khai hạ tầng Docker Compose dev và khởi động service tuần tự.
- [`RUN_PROJECT.md`](./runbooks/RUN_PROJECT.md): Hướng dẫn chi tiết chạy toàn bộ dự án trên máy local.
- [`TEAM_SETUP_DEPLOYMENT_GUIDE.md`](./runbooks/TEAM_SETUP_DEPLOYMENT_GUIDE.md): Tài liệu onboarding team về AWS EKS, Jenkins, Argo CD và GitOps.
- [`restart_devsecops_local.md`](./runbooks/restart_devsecops_local.md): Hướng dẫn restart các container bảo mật local.
- [`docvault_eks_pause_resume_runbook.md`](./runbooks/docvault_eks_pause_resume_runbook.md): Quy trình tắt/mở node EKS để tối ưu chi phí AWS.
- [`eks_harbor_scale_recovery_runbook.md`](./runbooks/eks_harbor_scale_recovery_runbook.md): Khôi phục Harbor registry trên EKS khi scaling sự cố.
- [`harbor_eks_deployment_runbook.md`](./runbooks/harbor_eks_deployment_runbook.md): Triển khai Harbor private registry trên EKS.
- [`s3-kms-cutover-runbook.md`](./runbooks/s3-kms-cutover-runbook.md): Chuyển đổi lưu trữ tài liệu sang AWS S3 SSE-KMS.
- [`audit-log-ingest-token-runbook.md`](./runbooks/audit-log-ingest-token-runbook.md): Quản lý token ghi nhận audit log an toàn.
- [`web-key-rotation-and-mfa-runbook.md`](./runbooks/web-key-rotation-and-mfa-runbook.md): Quy trình xoay vòng signing key (`kid`) và cấu hình MFA TOTP.
- [`SECURITY_ROTATION.md`](./runbooks/SECURITY_ROTATION.md): Lịch trình và hướng dẫn xoay vòng secret/credential định kỳ.
- [`EKS_SEED_DATA_GUIDE.md`](./runbooks/EKS_SEED_DATA_GUIDE.md): Nạp dữ liệu mẫu ban đầu vào cơ sở dữ liệu trên EKS.

### 📂 [`devsecops/`](./devsecops/) — Pipeline CI/CD, Quét Bảo Mật & Hardening
- [`DEVSECOPS_PIPELINE_SETUP_GUIDE.md`](./devsecops/DEVSECOPS_PIPELINE_SETUP_GUIDE.md): Thiết lập Jenkins Declarative Pipeline đa chặng.
- [`pipeline-hardening-summary.md`](./devsecops/pipeline-hardening-summary.md): Báo cáo hardening pipeline CI/CD (OWASP DC, Trivy, SonarQube, ZAP, Cosign).
- [`security-sca-triage.md`](./devsecops/security-sca-triage.md): Bản ghi xử lý phân loại lỗ hổng phụ thuộc SCA và suppression rules.
- [`jenkins_docker.md`](./devsecops/jenkins_docker.md): Thiết lập Jenkins master/agent trên Docker.
- [`jenkins_iam_roles_anywhere.md`](./devsecops/jenkins_iam_roles_anywhere.md): Cấu hình xác thực IAM Roles Anywhere cho Jenkins ngoài AWS.
- [`setup_jenkins_auto_trigger.md`](./devsecops/setup_jenkins_auto_trigger.md): Thiết lập webhook kích hoạt build tự động từ GitHub.
- [`setup_jenkins_k8s_account.md`](./devsecops/setup_jenkins_k8s_account.md): Cấu hình Kubernetes ServiceAccount cho Jenkins kiểm tra Argo CD.
- [`sonarqube-docker-jenkins-setup.md`](./devsecops/sonarqube-docker-jenkins-setup.md): Kết nối SonarQube với Jenkins pipeline.

### 📂 [`guides/`](./guides/) — Hướng Dẫn Sử Dụng & Kịch Bản Demo
- [`HUONG_DAN_SU_DUNG.md`](./guides/HUONG_DAN_SU_DUNG.md): Hướng dẫn sử dụng tổng quan hệ thống.
- [`HUONG_DAN_SU_DUNG_WEB.md`](./guides/HUONG_DAN_SU_DUNG_WEB.md): Hướng dẫn chi tiết chức năng web app DocVault.
- [`HUONG_DAN_SU_DUNG_WEB.pdf`](./guides/HUONG_DAN_SU_DUNG_WEB.pdf): Bản in PDF của tài liệu hướng dẫn sử dụng web.
- [`DANH_SACH_TINH_NANG_WEB.md`](./guides/DANH_SACH_TINH_NANG_WEB.md): Danh mục toàn bộ tính năng frontend và backend tương ứng.
- [`demo-users.md`](./guides/demo-users.md): Danh sách tài khoản demo, phân quyền RBAC và hướng dẫn đăng nhập.
- [`demo-flow.md`](./guides/demo-flow.md): Kịch bản trình diễn luồng nghiệp vụ E2E từ tải lên đến phê duyệt và audit.
- [`tong_hop_web_runtime_da_trien_khai_va_huong_dan_test.md`](./guides/tong_hop_web_runtime_da_trien_khai_va_huong_dan_test.md): Tổng hợp runtime và hướng dẫn kiểm thử web.

### 📂 [`infra/`](./infra/) — Quy Hoạch Hạ Tầng & Cơ Sở Dữ Liệu
- [`docvault_terraform_eks_argocd_plan.md`](./infra/docvault_terraform_eks_argocd_plan.md): Kế hoạch Terraform tạo VPC, EKS và Argo CD.
- [`aws_secrets_manager_external_secrets.md`](./infra/aws_secrets_manager_external_secrets.md): Đồng bộ bí mật từ AWS Secrets Manager qua External Secrets Operator.
- [`cloudflare_tunnel_published_app_routes.md`](./infra/cloudflare_tunnel_published_app_routes.md): Định tuyến Cloudflare Tunnel cho các dịch vụ public.
- [`harbor_cloudflare_dns_tls_guide.md`](./infra/harbor_cloudflare_dns_tls_guide.md): Cấu hình DNS và TLS cho Harbor qua Cloudflare.
- [`s3-kms-eks-plan.md`](./infra/s3-kms-eks-plan.md): Kế hoạch tích hợp S3 và KMS trên cụm EKS.
- [`backup-first-databases-and-minio-retirement.md`](./infra/backup-first-databases-and-minio-retirement.md): Lộ trình backup cơ sở dữ liệu và chuyển dịch MinIO.
- [`percona-mongodb-audit-ha-backup-plan.md`](./infra/percona-mongodb-audit-ha-backup-plan.md): Kế hoạch thiết lập MongoDB HA với Percona Operator.

### 📂 [`research/`](./research/) — Định Hướng Nghiên Cứu Khoa Học
- [`dinh_huong_nckh_ai_phan_loai_tai_lieu_mat_mlops.md`](./research/dinh_huong_nckh_ai_phan_loai_tai_lieu_mat_mlops.md): Đề xuất nghiên cứu AI tự động phân loại tài liệu mật và tích hợp MLOps pipeline.
- [`dinh_huong_nckh_ai_phan_loai_tai_lieu_mat_mlops.pdf`](./research/dinh_huong_nckh_ai_phan_loai_tai_lieu_mat_mlops.pdf): Bản in PDF của tài liệu nghiên cứu.

### 📂 Các Thư Mục Tài Nguyên Khác
- [`frontend/`](./frontend/): Thiết kế bảng màu và tài liệu Design System giao diện.
- [`diagrams/`](./diagrams/): Mã nguồn sơ đồ Mermaid (`.mmd`) và file Draw.io.
- [`images/`](./images/): Hình ảnh kiến trúc và sơ đồ đã xuất đồ họa.
- [`evidence/`](./evidence/): Ảnh chụp màn hình Playwright phục vụ minh chứng kiểm thử tự động.

---

## 2. Tài Liệu Đọc Nhanh Khuyến Nghị

- **Bắt đầu chạy local**: Đọc [`runbooks/RUN_PROJECT.md`](./runbooks/RUN_PROJECT.md).
- **Vận hành hệ thống & Docker Compose**: Đọc [`runbooks/DEPLOYMENT_RUNBOOK.md`](./runbooks/DEPLOYMENT_RUNBOOK.md).
- **Thành viên mới cần triển khai cụm EKS / Jenkins**: Đọc [`runbooks/TEAM_SETUP_DEPLOYMENT_GUIDE.md`](./runbooks/TEAM_SETUP_DEPLOYMENT_GUIDE.md).
- **Thiết lập Pipeline CI/CD DevSecOps**: Đọc [`devsecops/DEVSECOPS_PIPELINE_SETUP_GUIDE.md`](./devsecops/DEVSECOPS_PIPELINE_SETUP_GUIDE.md).
