# Phân công công việc DocVault cho Thắng và Duy

Ngày tạo: 2026-05-06

Nguồn kế hoạch: `docs/ke_hoach_uu_tien_cai_thien_docvault_theo_gop_y_gvhd.md`

Định hướng thực hiện của nhóm: **ưu tiên Pipeline DevSecOps/GitOps trước**, sau đó chuyển sang **Web App security hardening**. Cách này hợp lý vì pipeline là phần thầy góp ý nhiều về evidence, Policy as Code, registry, dev/prod, RBAC hạ tầng và Kubernetes storage. Tuy nhiên, nhóm vẫn nên chốt sớm vài quyết định Web App P0 để tránh làm pipeline xong nhưng demo bảo mật ứng dụng bị lệch.

## Nguyên tắc phân công

- Mỗi hạng mục có một người **owner chính** chịu trách nhiệm hoàn thành, người còn lại review/test.
- Ưu tiên làm ra evidence: ảnh chụp, log, report, lệnh kiểm chứng, output pipeline.
- Không làm đồng thời trên cùng một file nếu không cần thiết.
- Pipeline làm trước theo thứ tự: dev/prod -> PVC -> Harbor -> Policy as Code -> CI gates -> RBAC/evidence.
- Web App làm sau theo thứ tự: policy access -> audit trust boundary -> secret/key rotation -> E2E -> malware/DLP/retention.

---

# Giai đoạn 1. Pipeline DevSecOps/GitOps

## P1.1 - Tách GitOps dev/prod

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Tạo namespace `docvault-dev` và `docvault-prod`.
2. Tách values theo môi trường:
   - `infra/k8s/values-dev/*.yaml`
   - `infra/k8s/values-prod/*.yaml`
3. Tạo ArgoCD Applications riêng cho dev/prod.
4. Dev auto-sync, prod manual sync hoặc dùng release branch/tag.
5. Tách image tag, resource, env, secret reference giữa dev/prod.

Kết quả cần có:

- ArgoCD app dev/prod riêng.
- Dev/prod deploy vào namespace khác nhau.
- Ảnh chụp hoặc log `kubectl get ns`, `kubectl get applications -n argocd`.
- Tài liệu ngắn giải thích khác biệt dev/prod.

## P1.2 - Persistent storage cho Kubernetes

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Thay `emptyDir` bằng PVC cho:
   - PostgreSQL;
   - MongoDB audit;
   - MinIO.
2. Chọn StorageClass demo:
   - local-path nếu lab một node;
   - Longhorn nếu muốn demo distributed storage;
   - EBS/EFS nếu chạy EKS.
3. Test restart Pod và kiểm tra dữ liệu còn.

Kết quả cần có:

- Manifest PVC hoặc Helm values có PVC.
- Output `kubectl get pvc -n docvault-dev`.
- Evidence restart Pod không mất metadata/file/audit.
- Đoạn giải thích "volume nằm ở đâu" để đưa vào báo cáo.

## P1.3 - Harbor private registry và image promotion

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Dựng Harbor làm private registry chính.
2. Tạo project:
   - `docvault-dev`
   - `docvault-prod`
3. Tạo robot account cho Jenkins.
4. Bật vulnerability scanning, retention policy, immutable tag nếu có thể.
5. Cập nhật Jenkins push image vào Harbor thay vì Docker Hub.
6. Cập nhật Helm values `image.repository` sang Harbor.
7. Ghi image tag/digest vào values.
8. Thiết kế promotion dev -> prod.

Kết quả cần có:

- Jenkins push image vào Harbor.
- ArgoCD deploy image từ Harbor.
- Harbor scan report.
- Image prod được promote rõ từ image dev.
- Không còn phụ thuộc Docker Hub làm registry chính.

## P1.4 - Policy as Code gate

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Tạo thư mục `policies/`.
2. Chọn OPA Conftest hoặc Kyverno CLI cho CI.
3. Viết policy tối thiểu:
   - cấm privileged container;
   - cấm run as root;
   - bắt buộc resource requests/limits;
   - cấm image tag `latest`;
   - chỉ cho phép image từ Harbor/Nexus;
   - cấm plaintext Kubernetes Secret trong GitOps path;
   - không mở SSH/RDP/admin port `0.0.0.0/0` trong Terraform;
   - Dockerfile final image phải non-root và không copy `.env`.
4. Thêm stage `Policy as Code` vào Jenkins.
5. Archive policy report.

Kết quả cần có:

- Pipeline fail khi manifest thiếu resources.
- Pipeline fail khi dùng image `latest`.
- Pipeline fail khi image ngoài Harbor/Nexus.
- Pipeline fail khi commit plaintext Secret.
- Có report policy violation.

## P1.5 - Siết CI security gates

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Thêm `--exit-code 1` cho Trivy FS theo ngưỡng đã chọn.
2. Chuyển Dependency Check từ `UNSTABLE` sang fail thật nếu CVSS >= 7, hoặc ghi rõ exception.
3. Bật SonarQube Quality Gate.
4. Thêm Gitleaks hoặc TruffleHog secret scan.
5. Giữ Checkov fail pipeline.
6. ZAP chỉ bật khi gateway target reachable, giữ `RUN_ZAP=false` khi chưa có target.
7. Archive đầy đủ report.

Kết quả cần có:

- Commit lỗi security làm pipeline fail.
- Commit fix làm pipeline pass.
- Có report Sonar, Dependency Check, Trivy, Checkov, Policy as Code, ZAP nếu bật.

## P1.6 - RBAC hạ tầng

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Jenkins không dùng cluster-admin.
2. ArgoCD dev chỉ sync namespace dev.
3. ArgoCD prod chỉ sync namespace prod.
4. Service runtime không tự động mount service account token nếu không cần.
5. Harbor robot account tách quyền dev/prod.
6. Production deploy cần approval hoặc release branch/tag.

Kết quả cần có:

- Evidence Jenkins không có cluster-admin.
- Evidence ArgoCD dev không sync được prod.
- Evidence ServiceAccount không đọc được secret ngoài quyền.
- Harbor RBAC theo project.

## P1.7 - Evidence pack pipeline

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Tạo checklist evidence trong `docs/`.
2. Chuẩn bị commit fail:
   - missing resources;
   - image `latest`;
   - plaintext secret;
   - Terraform mở port nguy hiểm.
3. Chuẩn bị commit fix.
4. Lưu evidence:
   - Jenkins fail/pass;
   - Harbor scan;
   - ArgoCD sync;
   - ArgoCD rollback;
   - PVC tồn tại sau restart;
   - RBAC deny.

Kết quả cần có:

- Bộ evidence dùng được cho báo cáo/slide.
- Demo có câu chuyện: fail -> fix -> pass -> deploy -> rollback.

---

# Giai đoạn 2. Web App security hardening

## W2.1 - Chốt và sửa authorization policy

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Tạo policy tập trung cho:
   - `READ_METADATA`;
   - `PREVIEW`;
   - `DOWNLOAD`;
   - `WRITE`;
   - `APPROVE`;
   - `MANAGE_ACL`.
2. Áp policy cho:
   - document detail;
   - workflow history;
   - comments;
   - ACL list;
   - preview;
   - download.
3. Chốt policy preview cho `compliance_officer`.
4. Hoàn thiện `GROUP` ACL hoặc ẩn/remove nếu chưa dùng thật.

Kết quả cần có:

- Không thể đoán ID để đọc metadata trái quyền.
- Compliance officer không xem/download nội dung nếu rule đã chốt là cấm.
- E2E cover role/status/classification/ACL.

## W2.2 - Audit trust boundary và audit hash-chain demo

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Khóa `POST /audit/events` bằng service-to-service auth.
2. Tách user-facing route khỏi internal audit route nếu cần.
3. Retention/system job có credential hợp lệ để emit audit.
4. Thêm demo verify-chain:
   - chain valid;
   - sửa DB;
   - chain invalid.
5. Thêm UI hoặc script gọi `/audit/verify-chain`.

Kết quả cần có:

- User thường không ghi audit event giả được.
- Có demo tamper-evident audit.
- Có output/script dùng được trong báo cáo.

## W2.3 - Secret/key lifecycle, MFA và key rotation

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Bỏ fallback secret mặc định trong runtime service.
2. Grant token hỗ trợ `kid` để rotate key.
3. Demo hoặc tài liệu hóa Keycloak signing key rotation.
4. Bật MFA/TOTP cho admin hoặc compliance officer.
5. Không log secret/token nhạy cảm.

Kết quả cần có:

- Service fail fast nếu thiếu secret bắt buộc.
- Có runbook rotation.
- Có evidence MFA/TOTP.

## W2.4 - Malware scanning khi upload

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Dựng ClamAV local và Kubernetes.
2. Tích hợp scan vào upload flow.
3. Chặn file infected trước khi lưu chính thức.
4. Dùng EICAR để demo.
5. Ghi audit và notification khi malware detected.

Kết quả cần có:

- Upload EICAR bị chặn.
- Không tạo version cho file infected.
- Audit có `DOCUMENT_UPLOAD_BLOCKED_MALWARE`.

## W2.5 - DLP tối thiểu và classification policy

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Regex phát hiện:
   - email;
   - số điện thoại;
   - mã định danh giả lập;
   - từ khóa `secret`, `confidential`, `internal only`.
2. Gợi ý hoặc ép classification cao hơn.
3. Chặn downgrade xuống `PUBLIC` nếu có DLP hit, trừ admin override có reason.
4. Nếu kịp, dùng Apache Tika để extract text PDF/DOCX.
5. Ghi audit `DLP_PATTERN_DETECTED`.

Kết quả cần có:

- File chứa dữ liệu nhạy cảm được gợi ý/ép classification.
- Có audit DLP decision.
- Có demo DLP trong web app hoặc script.

## W2.6 - Retention/records management

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Chốt retention model theo classification hoặc bổ sung `retentionClass`, `retentionUntil`.
2. Thêm view/endpoint retention status nếu kịp.
3. Demo auto-archive document quá hạn.
4. Audit/workflow history ghi actor `system:retention`.

Kết quả cần có:

- Báo cáo giải thích được retention lifecycle.
- Có demo `DOCUMENT_AUTO_ARCHIVED`.

## W2.7 - Web App E2E và security dashboard

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Mở rộng `scripts/e2e-check.mjs` cho:
   - role/status/classification/ACL;
   - compliance denied;
   - metadata denied;
   - audit verify-chain;
   - malware/DLP nếu đã có.
2. Mở rộng audit page hoặc dashboard:
   - deny events;
   - malware blocked;
   - DLP hits;
   - audit chain status.

Kết quả cần có:

- Một lệnh E2E chứng minh security core.
- Dashboard hoặc audit page có dữ liệu bảo mật dễ demo.

---

# Giai đoạn 3. Nâng cao nếu còn thời gian

## N3.1 - SBOM và image signing

Owner chính: **Duy**

Reviewer/hỗ trợ: **Thắng**

Nội dung:

1. Tạo SBOM bằng Syft hoặc Trivy.
2. Archive SBOM theo image tag/digest.
3. Ký image bằng Cosign.
4. Verify signature trước deploy nếu kịp.

## N3.2 - Shared auth/contracts

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Giảm duplicate auth code giữa services.
2. Đồng bộ OpenAPI contract với runtime.
3. Tạo contract cho audit/notification/workflow events.

## N3.3 - AI/AIOps/LLM remediation future work

Owner chính: **Thắng**

Reviewer/hỗ trợ: **Duy**

Nội dung:

1. Ghi hướng AI auto-classification/search/summary theo RBAC.
2. Ghi hướng LLM auto-remediation cho IaC.
3. Ghi hướng AIOps anomaly detection từ Prometheus/Loki/audit.
4. Nhấn mạnh đây là future work, không làm trước P0/P1.

---

# Lịch thực hiện đề xuất

## Tuần 1 - Pipeline nền

- Thắng: D-P0.1 dev/prod GitOps, D-P0.2 PVC.
- Duy: D-P0.3 Harbor, D-P0.4 Policy as Code khung đầu tiên.

## Tuần 2 - Pipeline gates và evidence

- Thắng: D-P0.6 RBAC hạ tầng, P1.7 evidence pack.
- Duy: D-P0.5 CI gates, hoàn thiện Policy as Code, Harbor scan.

## Tuần 3 - Web App P0

- Thắng: authorization policy, audit trust boundary, key rotation/MFA.
- Duy: E2E security matrix, hỗ trợ test policy và audit-chain demo.

## Tuần 4 - Web App enterprise features

- Thắng: retention/records management, audit evidence.
- Duy: malware scan, DLP, security dashboard.

## Tuần 5 nếu còn thời gian

- Duy: SBOM/Cosign.
- Thắng: shared contracts/auth, AI/AIOps future-work section cho báo cáo.

---

# Nhận xét về chiến lược ưu tiên

Ưu tiên pipeline trước là hợp lý vì:

- Đây là phần có nhiều khoảng trống lớn so với góp ý của thầy.
- Pipeline dễ tạo evidence rõ: fail/pass, scan report, Harbor, ArgoCD, PVC, RBAC.
- Khi pipeline ổn, việc triển khai và demo Web App security sẽ đáng tin hơn.

Tuy nhiên, nhóm không nên để toàn bộ Web App P0 đến quá muộn. Ít nhất cần chốt sớm policy preview của `compliance_officer`, audit trust boundary và secret/key rotation vì đây là các điểm nếu làm muộn sẽ ảnh hưởng trực tiếp tới demo bảo mật cuối cùng.

---

# Review P1.3-P1.5 ngay 2026-05-25

Trang thai hien tai: P1.3, P1.4, P1.5 da co nen tang tot, nhung can siet them de thanh milestone co the bao ve truoc hoi dong.

## P1.3 - Harbor private registry va promotion

Diem da co:

- Pipeline da co bien registry de doi repository tu Docker Hub sang registry rieng.
- Jenkins ghi image `tag` va `digest` vao Helm values.
- Repo da co file values Harbor cho EKS tai `infra/k8s/harbor/values-eks.yaml`.

Can cai thien:

- Khong dung HTTP Harbor tren EKS neu muon node pull image on dinh. Can chot DNS + HTTPS truoc.
- Harbor project nen tach ro `docvault-dev` va `docvault-prod`.
- Robot account Jenkins chi nen push/pull trong `docvault-dev`; promotion sang prod nen dung credential rieng.
- Khong push `latest` theo mac dinh nua vi no mau thuan voi immutable tag va digest-based deploy.
- Can them buoc promotion dev -> prod bang digest, khong rebuild image cho prod.
- Can them `imagePullSecrets` vao tung values file khi Harbor project private.

## P1.4 - Policy as Code gate

Diem da co:

- Da co thu muc `policies/kyverno`.
- Da co policy cam `latest`, bat resources, va bat non-root.
- Jenkins da co stage `Policy as Code`.

Can cai thien:

- Stage policy phai fail build that, khong chi `UNSTABLE`.
- Can scan ca Helm chart da render, khong chi raw manifest trong `infra/k8s/infra-deps`.
- Can bo sung policy cam privileged container.
- Can bo sung policy chi cho image tu Harbor/Nexus sau khi chot Harbor hostname.
- Can xu ly plaintext Kubernetes Secret trong GitOps path. Hien tai infra demo van co Secret plaintext, nen nen chuyen sang External Secrets/SOPS/SealedSecrets hoac ghi ro exception demo.
- Can archive Kyverno report va rendered manifest lam evidence.

## P1.5 - Siet CI security gates

Diem da co:

- Trivy FS da fail voi HIGH/CRITICAL.
- Dependency Check da dung `--failOnCVSS 7`.
- Checkov dang fail pipeline.
- ZAP dang optional va chi nen bat khi target reachable.

Can cai thien:

- Sonar Quality Gate phai co che do fail pipeline khi da cau hinh webhook/token dung.
- Secret scan phai fail build khi phat hien secret that, khong chi warning.
- Tat ca report can archive: Dependency Check, Trivy, Checkov, Kyverno, TruffleHog, ZAP.
- Can tao evidence fail/pass co chu dich: commit loi `latest`, missing resources, secret plaintext, Terraform open port; sau do commit fix.
