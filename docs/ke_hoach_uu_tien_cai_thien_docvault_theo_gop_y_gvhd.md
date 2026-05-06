# Kế hoạch ưu tiên cải thiện DocVault theo góp ý GVHD

Ngày rà soát: 2026-05-06

Nguồn đối chiếu chính: `docs/tong_hop_gop_y_docvault_webapp_devsecops_v2.md`

Phạm vi rà soát: `apps/web`, `services/*`, `libs/*`, `infra/*`, `Jenkinsfile`, `vars/*.groovy`, OpenAPI contract và các tài liệu hiện có.

Mục tiêu của file này là tách rõ những việc nên làm tiếp thành 2 nhóm:

1. **Web App DocVault**: tập trung vào nghiệp vụ quản lý tài liệu bảo mật, RBAC/ACL, workflow, audit, hash, secret/key lifecycle, malware/DLP và evidence demo ở tầng ứng dụng.
2. **Pipeline DevSecOps/GitOps**: tập trung vào CI/CD, registry, Policy as Code, dev/prod, Kubernetes storage, RBAC hạ tầng, security gates và evidence demo pipeline.

## Tổng quan hiện trạng

DocVault hiện đã có nền khá tốt của một hệ thống quản lý tài liệu bảo mật cloud-native:

- Frontend Next.js trong `apps/web`.
- Backend microservices NestJS gồm gateway, metadata, document, workflow, audit và notification.
- Keycloak RBAC với các role viewer/editor/approver/compliance/admin.
- Workflow tài liệu Draft -> Pending -> Published/Rejected/Archived/Deleted.
- Classification `PUBLIC/INTERNAL/CONFIDENTIAL/SECRET`.
- Checksum SHA-256 cho file version.
- Watermark cho tài liệu CONFIDENTIAL/SECRET khi download stream.
- Retention job auto-archive theo classification.
- Audit hash-chain với `prevHash`, `hash` và endpoint verify-chain.
- Jenkins pipeline có Unit Test, SonarQube, Dependency Check, Trivy, Checkov, Terraform validate, image scan, GitOps update và ZAP tùy chọn.
- ArgoCD manifests và Helm chart generic cho services.

Các khoảng trống quan trọng còn lại:

- Secret/key lifecycle, MFA và key rotation chưa rõ.
- Một số policy đọc metadata/detail/history/comment/ACL chưa thống nhất với policy download.
- `GROUP` ACL có schema/UI nhưng policy chưa evaluate đầy đủ.
- Chính sách preview nội dung cho `compliance_officer` cần chốt lại để không lệch với rule "được kiểm toán nhưng không xem nội dung file".
- Audit event ingestion chưa có service-to-service trust boundary chặt.
- Chưa có malware scan và DLP runtime.
- Retention đã có cron auto-archive, nhưng chưa có fields/runbook/evidence theo hướng records management như `retentionClass` hoặc `retentionUntil`.
- Pipeline còn dùng Docker Hub, chưa có Harbor/Nexus chính.
- Chưa có OPA/Kyverno/Conftest policy gate riêng.
- ArgoCD hiện chủ yếu deploy một namespace `docvault`, chưa tách dev/prod.
- Stateful components trên K8s demo còn dùng `emptyDir`, chưa có PVC.
- Evidence fail/pass pipeline, ArgoCD rollback, Harbor scan, tamper audit chưa được đóng gói thành bộ demo rõ ràng.

---

# Phần I. Web App DocVault

Phần này ưu tiên những việc làm cho DocVault giống một hệ thống quản lý tài liệu bảo mật doanh nghiệp hơn, đúng với góp ý về RBAC, ACL, workflow, audit, hash, secret key, key rotation, malware/DLP và governance.

## Ưu tiên Web App

| Ưu tiên | Hạng mục | Trạng thái hiện tại | Mục tiêu |
|---|---|---|---|
| W-P0.1 | Secret/key lifecycle, MFA, key rotation | Có secret demo và fallback secret | Không hard-code/fallback secret, có MFA và rotation plan/demo |
| W-P0.2 | Authorization policy thống nhất | Download policy khá tốt, metadata/detail chưa chặt đều | RBAC + ACL + status + classification nhất quán |
| W-P0.3 | Audit trust boundary | Có hash-chain, nhưng endpoint ghi audit cần khóa chặt | Chỉ service hợp lệ được ghi audit |
| W-P0.4 | Audit hash-chain evidence | Có code verify-chain | Có demo tamper detection rõ ràng |
| W-P0.5 | E2E security evidence | Có E2E MVP | Mở rộng theo role/status/classification/ACL |
| W-P1.1 | Malware scan upload | Chưa có runtime scan | Chặn file độc hại trước khi lưu chính thức |
| W-P1.2 | DLP và classification policy | Có classification thủ công | Có regex/Tika tối thiểu để phát hiện dữ liệu nhạy cảm |
| W-P1.3 | Encryption at rest và presigned URL posture | Có MinIO, checksum, watermark | Nêu rõ/làm rõ mã hóa và luồng tải file nhạy cảm |
| W-P1.4 | Security dashboard ứng dụng | Có audit page cơ bản | Có view deny/malware/DLP/audit-chain/security events |
| W-P1.5 | Retention và records management | Có cron auto-archive theo classification | Có retention fields/runbook/evidence rõ |
| W-P2.1 | Shared auth/contracts | `libs` còn mỏng, auth còn lặp | Giảm lệch contract và duplicate guard/strategy |
| W-P3 | AI-ready/future | Metadata/audit đã có nền | AI classification, summarization, ransomware/AIOps future |

## W-P0.1 - Siết secret/key lifecycle, MFA và key rotation

Lý do ưu tiên: thầy nhắc trực tiếp về secret key và key rotation. Hiện `infra/k8s/infra-deps/app-secrets.yaml` chứa demo plaintext secret, Keycloak realm có client secret/user password demo, và `DOWNLOAD_GRANT_SECRET`/`PREVIEW_GRANT_SECRET` còn có fallback mặc định trong code.

Việc nên làm:

1. Bỏ fallback production cho `DOWNLOAD_GRANT_SECRET` và `PREVIEW_GRANT_SECRET`; nếu thiếu secret thì service fail fast.
2. Thiết kế grant token có `kid` để hỗ trợ rotation:
   - current key dùng để ký token mới;
   - previous key còn được verify trong thời gian ngắn;
   - hết TTL thì bỏ previous key.
3. Tách secret runtime theo môi trường:
   - dev dùng secret dev;
   - prod dùng secret prod;
   - không dùng chung DB password, MinIO key, grant secret, Keycloak client secret.
4. Bật MFA/TOTP trong Keycloak ít nhất cho `admin` và `compliance_officer` để demo.
5. Viết runbook Keycloak signing key rotation:
   - active key;
   - passive key;
   - token TTL;
   - thời điểm remove old key;
   - cách rollback.
6. Rà lại log để không in secret/token nhạy cảm.

Tiêu chí hoàn thành:

- `document-service` và `metadata-service` không chạy nếu thiếu grant secret bắt buộc.
- Có demo MFA/TOTP cho admin hoặc compliance officer.
- Có tài liệu key rotation cho Keycloak và grant token.
- Có bằng chứng dev/prod không dùng chung secret.

## W-P0.2 - Thống nhất authorization policy: RBAC + ACL + status + classification

Lý do ưu tiên: góp ý của thầy nhấn mạnh DocVault không nên chỉ check role. Quyền truy cập cần kết hợp role, ACL, trạng thái tài liệu và classification.

Hiện trạng cần chú ý:

- Download authorization đã có rule chặn compliance officer và check classification.
- `GET /metadata/documents` đã có filtering.
- `GET /metadata/documents/:docId` vẫn cần policy đọc metadata chặt hơn để tránh đoán ID.
- Workflow history, comments và ACL list nên dùng chung policy đọc metadata.
- `GROUP` ACL có schema/UI nhưng `matchesAcl()` và `matchesPreviewAcl()` chưa evaluate group.

Việc nên làm:

1. Tạo policy tập trung, ví dụ `DocumentAccessPolicy`.
2. Tách rõ các permission:
   - `READ_METADATA`;
   - `PREVIEW`;
   - `DOWNLOAD`;
   - `WRITE`;
   - `APPROVE`;
   - `MANAGE_ACL`.
3. Dùng policy này cho:
   - list document;
   - document detail;
   - workflow history;
   - comments;
   - ACL list;
   - preview;
   - download.
4. Hoàn thiện `GROUP` ACL bằng group claim từ Keycloak hoặc tạm ẩn/remove `GROUP` khỏi UI/DTO/contract nếu chưa kịp.
5. Viết ma trận quyền rõ:
   - Draft/Pending/Published/Archived;
   - Public/Internal/Confidential/Secret;
   - Viewer/Editor/Approver/Compliance/Admin;
   - ACL allow/deny.
6. Chốt lại policy preview cho `compliance_officer`:
   - nếu preview được xem là truy cập nội dung file, compliance officer phải bị chặn giống download;
   - nếu chỉ cho preview tài liệu `PUBLIC`, cần ghi rõ trong backend, frontend và tài liệu;
   - tránh tình trạng backend cho preview nhưng báo cáo nói compliance không được xem nội dung.
7. Đảm bảo rule quan trọng luôn đúng:
   - compliance officer có thể xem audit/metadata theo chính sách;
   - compliance officer không được download nội dung file;
   - direct stream/presigned path cũng bị chặn.

Tiêu chí hoàn thành:

- User không có quyền không thể lấy document detail bằng cách đoán ID.
- Workflow history/comment/ACL không bypass policy.
- `GROUP` ACL hoạt động thật hoặc không còn xuất hiện trong UI/contract.
- E2E test cover role + status + classification + ACL allow/deny.

## W-P0.3 - Khóa audit event ingestion bằng service-to-service trust boundary

Lý do ưu tiên: audit hash-chain chỉ đáng tin khi việc ghi audit event được kiểm soát. Nếu user thường có thể gọi endpoint ghi audit bằng JWT của họ, audit trail dễ bị event giả.

Việc nên làm:

1. Tách endpoint internal audit khỏi gateway user-facing path.
2. `POST /audit/events` chỉ cho:
   - service account token;
   - internal shared token đã rotate;
   - hoặc mTLS/network internal nếu môi trường hỗ trợ.
3. Các service gọi audit bằng credential service-to-service riêng, không chỉ forward JWT người dùng.
4. Retention job/system job cần credential hợp lệ để emit audit event.
5. Ghi audit cho deny path quan trọng:
   - download denied;
   - metadata denied;
   - ACL denied;
   - malware denied;
   - DLP blocked/detected;
   - policy denied.

Tiêu chí hoàn thành:

- Viewer/editor thường không gọi được `POST /audit/events`.
- Gateway/public API không expose endpoint ghi audit cho user thường.
- Retention job ghi được `DOCUMENT_AUTO_ARCHIVED`.
- Audit log phân biệt được actor người dùng và actor service/system.

## W-P0.4 - Biến audit hash-chain thành demo tamper-evident rõ ràng

Lý do ưu tiên: code đã có `prevHash`, `hash` và `verify-chain`, nhưng cần có bằng chứng dễ trình bày.

Việc nên làm:

1. Thêm UI hoặc nút trong trang audit để gọi `/audit/verify-chain`.
2. Thêm script demo:
   - tạo vài event;
   - verify chain valid;
   - sửa một event trong MongoDB;
   - verify chain invalid.
3. Mở rộng audit filter theo:
   - action;
   - result;
   - actor;
   - resource;
   - time;
   - classification nếu metadata có trong event.
4. Ghi rõ trong báo cáo: hash-chain là tamper-evident, không phải blockchain và không làm log bất biến tuyệt đối.

Tiêu chí hoàn thành:

- Compliance/admin xem được audit chain valid/invalid.
- Có ảnh chụp hoặc log demo sửa DB làm chain invalid.
- E2E hoặc script kiểm tra `/audit/verify-chain`.

## W-P0.5 - Mở rộng E2E security evidence cho web app

Lý do ưu tiên: hệ thống đã có `scripts/e2e-check.mjs`, nên nên biến nó thành bằng chứng bảo mật chính thay vì chỉ smoke test MVP.

Việc nên làm:

1. Mở rộng E2E cho ma trận:
   - role;
   - status;
   - classification;
   - ACL allow/deny;
   - owner vs non-owner.
2. Thêm test cho:
   - compliance query audit OK;
   - compliance download denied;
   - direct stream denied;
   - confidential/secret download đi qua stream watermark;
   - audit verify-chain;
   - metadata detail denied nếu không đủ quyền.
3. Tạo dữ liệu demo ổn định:
   - public published;
   - internal published;
   - confidential published;
   - secret published;
   - draft của owner;
   - pending chờ approver.
4. Lưu output E2E làm evidence.

Tiêu chí hoàn thành:

- Một lệnh có thể chứng minh các rule bảo mật chính của web app.
- Output E2E có thể đưa vào báo cáo/slide.

## W-P1.1 - Thêm malware scanning khi upload file

Lý do ưu tiên: đây là năng lực enterprise DMS được tài liệu góp ý xếp cao, học từ Box Shield/Egnyte.

Việc nên làm:

1. Dựng ClamAV trong local compose và Kubernetes.
2. Thay đổi upload flow:
   - nhận file;
   - tính checksum;
   - scan malware;
   - clean thì lưu MinIO và tạo version;
   - infected thì chặn và không tạo version.
3. Dùng EICAR test file để demo.
4. Ghi audit:
   - `DOCUMENT_UPLOAD_SCANNED`;
   - `DOCUMENT_UPLOAD_BLOCKED_MALWARE`.
5. Gửi notification cho admin/compliance khi phát hiện malware.

Tiêu chí hoàn thành:

- Upload EICAR bị chặn.
- File độc hại không nằm trong version chính thức.
- Audit ghi actor, filename, checksum, result `DENY`.

## W-P1.2 - Bổ sung DLP tối thiểu và classification policy

Lý do ưu tiên: classification đã có, nhưng chưa có DLP. DLP giúp chứng minh DocVault học từ Microsoft Purview/Box Shield.

Việc nên làm:

1. MVP dùng regex để phát hiện:
   - email;
   - số điện thoại;
   - mã định danh giả lập;
   - từ khóa `secret`, `confidential`, `internal only`.
2. Khi upload hoặc update metadata:
   - nếu match pattern thì gợi ý classification cao hơn;
   - hoặc chặn downgrade xuống `PUBLIC`;
   - admin override phải có audit reason.
3. Nếu kịp, dùng Apache Tika để extract text từ PDF/DOCX trước khi scan.
4. Ghi audit `DLP_PATTERN_DETECTED`.

Tiêu chí hoàn thành:

- File chứa dữ liệu nhạy cảm được gợi ý hoặc ép classification `CONFIDENTIAL`.
- Không downgrade tài liệu có DLP hit xuống `PUBLIC` nếu không có admin override.
- Audit ghi được DLP decision.

## W-P1.3 - Làm rõ encryption at rest và presigned URL posture

Lý do ưu tiên: góp ý có secure storage và encryption. Hiện có checksum/watermark nhưng cần làm rõ mã hóa và luồng download an toàn.

Việc nên làm:

1. Bật hoặc tài liệu hóa MinIO server-side encryption cho MVP.
2. Nêu rõ key model:
   - MVP: MinIO SSE;
   - nâng cao: Vault/KMS;
   - future: client-side encryption/E2EE.
3. Rà lại presigned URL:
   - TTL ngắn;
   - chỉ non-sensitive mới dùng presigned URL trực tiếp;
   - CONFIDENTIAL/SECRET buộc stream qua service để watermark;
   - bucket không public.
4. Audit đầy đủ:
   - object key;
   - checksum;
   - version;
   - classification;
   - actor;
   - result.

Tiêu chí hoàn thành:

- Báo cáo trả lời được file được mã hóa ở tầng nào.
- CONFIDENTIAL/SECRET không nhận presigned URL trực tiếp.
- Có demo checksum sau download khớp metadata.

## W-P1.4 - Security dashboard ứng dụng

Lý do ưu tiên: giúp demo dễ hiểu hơn và chuẩn bị nền cho AIOps sau này.

Việc nên làm:

1. Mở rộng audit page hoặc thêm admin/security dashboard hiển thị:
   - deny events;
   - download nhiều;
   - malware blocked;
   - DLP hits;
   - audit chain status;
   - sensitive document access.
2. Thêm filter nhanh theo `DENY`, `ERROR`, `DOCUMENT_DOWNLOAD_DENIED`, `DLP_PATTERN_DETECTED`.
3. Thêm alert/notification tối thiểu:
   - nhiều deny liên tiếp;
   - malware detected;
   - audit chain invalid.

Tiêu chí hoàn thành:

- Admin/compliance có màn hình nhìn thấy tình hình bảo mật.
- Có dữ liệu demo deny/malware/DLP/audit-chain.

## W-P1.5 - Củng cố retention và records management

Lý do ưu tiên: tài liệu góp ý có nhắc Alfresco/OpenText và hướng records management. Repo hiện đã có retention cron theo classification, nhưng còn thiếu mô hình dữ liệu/evidence rõ để trình bày như một năng lực compliance.

Việc nên làm:

1. Chốt retention model:
   - MVP hiện tại: retention tính từ `publishedAt` theo classification;
   - nếu cần rõ hơn, bổ sung `retentionClass` và `retentionUntil`;
   - phân biệt document thường và record đã `PUBLISHED`.
2. Thêm endpoint hoặc admin view để xem retention status:
   - còn bao nhiêu ngày;
   - ngày sẽ auto-archive;
   - lý do retention.
3. Retention job phải ghi workflow history và audit event đầy đủ.
4. Thêm demo:
   - tạo document đã published quá hạn;
   - chạy retention job;
   - document chuyển `ARCHIVED`;
   - audit có `DOCUMENT_AUTO_ARCHIVED`.

Tiêu chí hoàn thành:

- Báo cáo giải thích được retention lifecycle.
- Có bằng chứng auto-archive theo classification.
- Audit/workflow history ghi rõ actor `system:retention`.

## W-P2.1 - Dọn shared auth/contracts

Lý do ưu tiên: không phải blocker demo, nhưng repo hiện còn lặp guard/strategy/filter ở nhiều service.

Việc nên làm:

1. Di chuyển phần chung vào `libs/auth` nếu phù hợp:
   - JWT strategy;
   - RolesGuard;
   - Roles decorator;
   - request context;
   - exception filter nếu dùng chung.
2. Cập nhật `libs/contracts/openapi/gateway.yaml` khớp runtime.
3. Tạo event contract cho:
   - audit event;
   - notification event;
   - workflow event.
4. Dọn frontend field compatibility cũ nếu không còn cần, ví dụ `classificationLevel`.

Tiêu chí hoàn thành:

- Ít duplicate auth code giữa services.
- Contract mô tả đúng endpoint runtime.
- Frontend/backend ít mapping lệch.

## W-P3 - Future work cho Web App

Chỉ nên làm sau khi W-P0 và W-P1 ổn:

1. AI auto-classification, auto-tagging, summarization theo RBAC.
2. AI search/QA phải tuân thủ policy hiện tại:
   - AI chỉ được đọc tài liệu user có quyền đọc;
   - nếu compliance officer không được xem/download nội dung, AI cũng không được dùng nội dung đó để trả lời;
   - mọi AI answer cần gắn audit event.
3. Ransomware behavior detection từ audit events.
4. Client-side encryption hoặc E2EE.
5. Risk scoring theo classification + download frequency.
6. Full records management nâng cao.

---

# Phần II. Pipeline DevSecOps và GitOps

Phần này ưu tiên những việc chứng minh DocVault không chỉ bảo mật ở tầng ứng dụng, mà còn kiểm soát toàn bộ supply chain từ code, dependency, image, manifest, secret, registry đến deployment trên Kubernetes.

## Ưu tiên Pipeline DevSecOps

| Ưu tiên | Hạng mục | Trạng thái hiện tại | Mục tiêu |
|---|---|---|---|
| D-P0.1 | Dev/prod GitOps | Một namespace `docvault` là chính | Tách `docvault-dev` và `docvault-prod` |
| D-P0.2 | Persistent storage K8s | Infra deps dùng `emptyDir` | PVC/StorageClass cho Postgres, Mongo, MinIO |
| D-P0.3 | Private registry Harbor/Nexus | Jenkins/values dùng Docker Hub | Harbor chính, image promotion rõ |
| D-P0.4 | Policy as Code gate | Có Checkov, chưa có OPA/Kyverno riêng | Gate manifest/Helm/Terraform bằng policy |
| D-P0.5 | Siết CI security gates | Một số stage chưa fail cứng | Fail thật với SAST/SCA/secret/IaC/container |
| D-P0.6 | GitOps/RBAC hạ tầng | ArgoCD app có, RBAC chưa rõ | Least privilege Jenkins/ArgoCD/service runtime |
| D-P1.1 | Evidence fail/pass pipeline | Có docs rời rạc | Bộ evidence chuẩn cho hội đồng |
| D-P1.2 | Secret management GitOps-safe | Secret demo plaintext | SealedSecrets/SOPS/ESO/Vault |
| D-P1.3 | Observability pipeline/runtime | Có monitoring app nền | Prometheus/Grafana/Loki/dashboard tối thiểu |
| D-P2.1 | SBOM/signing | Chưa có | Syft/Trivy SBOM, Cosign image signing |
| D-P3 | AIOps/LLM remediation/Artifactory | Future | Paper/future work hoặc enterprise reference |

## D-P0.1 - Tách GitOps dev/prod rõ ràng

Lý do ưu tiên: thầy yêu cầu ít nhất hai môi trường dev và production. Hiện ArgoCD app trong `infra/argocd-apps` deploy vào namespace `docvault`.

Việc nên làm:

1. Tạo namespace:
   - `docvault-dev`;
   - `docvault-prod`.
2. Tách values theo môi trường:
   - `infra/k8s/values-dev/*.yaml`;
   - `infra/k8s/values-prod/*.yaml`;
   - hoặc `values.yaml`, `values-dev.yaml`, `values-prod.yaml` nếu refactor chart.
3. Tạo ArgoCD Applications riêng:
   - `docvault-dev-*`;
   - `docvault-prod-*`.
4. Quy định sync:
   - dev auto-sync;
   - prod manual sync hoặc chỉ sync từ release branch/tag.
5. Prod dùng replica/resource/secret/domain khác dev.
6. Pipeline chỉ update dev tự động; promotion sang prod cần approval hoặc merge/tag release.

Tiêu chí hoàn thành:

- ArgoCD có app dev/prod riêng.
- `kubectl get ns` thấy `docvault-dev` và `docvault-prod`.
- Dev/prod có values/secret/image tag riêng.
- Báo cáo giải thích được khác biệt dev/prod.

## D-P0.2 - Thay `emptyDir` bằng PVC cho stateful components

Lý do ưu tiên: thầy hỏi "volume nằm ở đâu". Nếu Postgres/Mongo/MinIO dùng `emptyDir`, dữ liệu mất khi Pod restart/reschedule.

Việc nên làm:

1. Thay `emptyDir` bằng PVC cho:
   - PostgreSQL metadata;
   - MongoDB audit;
   - MinIO object storage;
   - Harbor/Nexus nếu triển khai;
   - Prometheus/Loki nếu cần giữ metric/log.
2. Chọn StorageClass cho demo:
   - local-path nếu lab một node;
   - Longhorn nếu muốn distributed storage dễ demo;
   - EBS/EFS nếu chạy EKS.
3. Tài liệu hóa:
   - PV;
   - PVC;
   - StorageClass;
   - dữ liệu tồn tại sau Pod restart.
4. Thử restart Pod và kiểm tra dữ liệu còn.

Tiêu chí hoàn thành:

- `kubectl get pvc -n docvault-dev` có PVC cho DB/MinIO.
- Restart Pod không mất document metadata/file/audit.
- Báo cáo trả lời rõ volume nằm ở đâu.

## D-P0.3 - Chuyển sang private registry Harbor/Nexus

Lý do ưu tiên: tài liệu góp ý đề xuất Harbor/Nexus/Artifactory. Repo hiện dùng Docker Hub `daithang59/*` trong Jenkins và Helm values.

Việc nên làm:

1. Dựng Harbor làm registry container chính.
2. Tạo project:
   - `docvault-dev`;
   - `docvault-prod`.
3. Jenkins dùng robot account Harbor, không dùng tài khoản cá nhân.
4. Bật vulnerability scanning, retention, immutable tag nếu có thể.
5. Tag image bằng git SHA hoặc build number; hạn chế dùng `latest`.
6. Cập nhật Jenkins:
   - push image vào Harbor;
   - lấy digest;
   - ghi tag/digest vào Helm values.
7. Cập nhật Helm values `image.repository` sang Harbor.
8. Thêm policy chỉ cho phép image từ Harbor/Nexus.
9. Nexus có thể bổ sung sau nếu cần npm/Docker dependency cache.

Tiêu chí hoàn thành:

- Pipeline push image vào Harbor.
- ArgoCD deploy image từ Harbor.
- Có Harbor scan report.
- Prod chỉ nhận image đã promote.
- Image ngoài registry nội bộ bị chặn.

## D-P0.4 - Bổ sung Policy as Code gate

Lý do ưu tiên: thầy nhấn mạnh Policy as Code không chỉ nằm trong web app. Pipeline cần kiểm soát Dockerfile, K8s manifest, Helm values, Terraform và ArgoCD Application.

Việc nên làm:

1. Tạo thư mục `policies/` cho OPA Conftest hoặc Kyverno policies.
2. Gate tối thiểu:
   - cấm privileged container;
   - cấm run as root;
   - bắt buộc resources requests/limits;
   - cấm image tag `latest`;
   - chỉ cho phép image từ Harbor/Nexus;
   - cấm plaintext Kubernetes Secret trong GitOps path;
   - bắt buộc readOnlyRootFilesystem nếu phù hợp;
   - cấm ServiceAccount quá rộng.
3. Gate Terraform tối thiểu:
   - không mở SSH/RDP hoặc admin port ra `0.0.0.0/0`;
   - bắt buộc encryption/logging cho resource nhạy cảm nếu rule Checkov hỗ trợ;
   - không dùng `checkov:skip` tùy tiện để né policy.
4. Gate Dockerfile tối thiểu:
   - final image chạy non-root;
   - không copy `.env` vào image;
   - dùng multi-stage build;
   - không lưu secret/token trong image layer.
5. Thêm Jenkins stage `Policy as Code`.
6. Nếu dùng Kyverno, có thể vừa chạy CLI trong CI vừa áp admission policy trong cluster.
7. Lưu report policy violation làm artifact.

Tiêu chí hoàn thành:

- Pipeline fail khi manifest thiếu limits.
- Pipeline fail khi image tag là `latest`.
- Pipeline fail khi image không thuộc Harbor/Nexus.
- Pipeline fail khi commit plaintext Secret vào GitOps path.

## D-P0.5 - Siết CI security gates thành fail thật

Lý do ưu tiên: repo có nhiều stage scan, nhưng một số stage chưa chặn cứng. Muốn thuyết phục về DevSecOps phải có pass/fail rõ.

Hiện trạng cần cải thiện:

- `trivyFsScan.groovy` chưa có `--exit-code 1`.
- `dependencyCheck.groovy` đang `catchError(... stageResult: 'UNSTABLE')`.
- `sonarSast.groovy` có `enforceQualityGate` nhưng config mặc định chưa bật.
- Chưa có Gitleaks/TruffleHog stage riêng.
- Chưa có SBOM/signing.

Việc nên làm:

1. Trivy FS fail khi có CRITICAL hoặc theo ngưỡng HIGH/CRITICAL đã chọn.
2. Dependency Check fail với CVSS >= 7, hoặc nếu cần demo thì exception phải có lý do rõ.
3. Bật SonarQube Quality Gate fail pipeline.
4. Thêm secret scan bằng Gitleaks hoặc TruffleHog.
5. Giữ Checkov fail pipeline.
6. Container scan Trivy image giữ `--exit-code 1`.
7. DAST ZAP chỉ bật khi gateway/API target thật sự reachable từ Jenkins/ZAP container; nếu chưa deploy được target thì giữ `RUN_ZAP=false` và không xem đó là blocker CI cơ bản.
8. Archive đầy đủ report:
   - Sonar;
   - Dependency Check;
   - Trivy FS/image;
   - Checkov;
   - Policy as Code;
   - ZAP.

Tiêu chí hoàn thành:

- Một commit có lỗi security làm pipeline fail.
- Commit fix làm pipeline pass.
- Report được lưu lại để đưa vào evidence.

## D-P0.6 - RBAC hạ tầng cho Jenkins, ArgoCD và service runtime

Lý do ưu tiên: góp ý nhấn mạnh RBAC không chỉ nằm trong web app mà còn ở pipeline/Kubernetes/ArgoCD/registry.

Việc nên làm:

1. Jenkins:
   - không dùng cluster-admin;
   - chỉ có quyền push registry và update GitOps branch;
   - prod deploy cần approval.
2. ArgoCD:
   - app dev chỉ sync namespace dev;
   - app prod chỉ sync namespace prod;
   - không sync chéo môi trường.
3. Kubernetes runtime:
   - mỗi service có ServiceAccount riêng nếu cần;
   - `automountServiceAccountToken: false` mặc định nếu service không cần Kubernetes API;
   - không cho service đọc secret của service khác.
4. Harbor:
   - robot account theo project;
   - dev/prod project RBAC riêng.

Tiêu chí hoàn thành:

- Jenkins không có cluster-admin.
- ArgoCD dev không sync được prod.
- ServiceAccount metadata không đọc được secret document-service nếu không được cấp.
- Harbor dev/prod có quyền riêng.

## D-P1.1 - Chuẩn hóa evidence demo pipeline

Lý do ưu tiên: thầy/hội đồng cần bằng chứng. Repo có tài liệu rời rạc, cần gom thành một bộ demo.

Việc nên làm:

1. Chuẩn bị commit fail:
   - Dockerfile chạy root;
   - manifest thiếu resources;
   - image tag `latest`;
   - Terraform mở port nguy hiểm;
   - secret giả trong YAML.
2. Chuẩn bị commit fix:
   - non-root;
   - có resources;
   - tag bằng SHA/build number;
   - Terraform secure;
   - secret chuyển sang template/SealedSecret/ESO.
3. Lưu evidence:
   - Jenkins fail/pass;
   - Sonar/Trivy/Checkov/Policy reports;
   - Harbor scan;
   - ArgoCD sync;
   - ArgoCD rollback;
   - Kubernetes RBAC deny;
   - PVC tồn tại sau restart.
4. Tạo file checklist evidence trong `docs/`.

Tiêu chí hoàn thành:

- Có bộ ảnh/log/report dùng được trong báo cáo.
- Demo có câu chuyện fail -> fix -> pass -> deploy -> rollback.

## D-P1.2 - GitOps-safe secret management

Lý do ưu tiên: liên quan trực tiếp tới cả pipeline và web app. Phần Web App dùng secret runtime, còn Pipeline/GitOps phải đảm bảo secret không bị commit plaintext.

Việc nên làm:

1. Thay plaintext `Secret` manifest bằng một hướng:
   - SealedSecrets;
   - SOPS;
   - External Secrets Operator;
   - Vault.
2. Tách secret dev/prod.
3. Pipeline có secret scan.
4. Pipeline không in secret/token ra log.
5. Production secret không cho developer thường đọc trực tiếp.
6. ArgoCD sync secret an toàn.
7. Tài liệu hóa cách rotate secret qua GitOps-safe workflow.

Tiêu chí hoàn thành:

- Git repo không chứa secret thật.
- Secret scan pass.
- Dev/prod secret tách riêng.
- Có runbook rotate secret.

## D-P1.3 - Observability cho runtime và pipeline

Lý do ưu tiên: observability giúp chứng minh vận hành và là nền cho AIOps.

Việc nên làm:

1. Hoàn thiện Prometheus/Grafana deployment.
2. Bổ sung Loki hoặc log aggregation nếu kịp.
3. App metrics tối thiểu:
   - request count;
   - latency;
   - error rate;
   - service health.
4. Dashboard:
   - gateway 5xx;
   - p95 latency;
   - CPU/memory;
   - pod restart;
   - audit/security events nếu tích hợp được.
5. Alert rule tối thiểu:
   - 5xx tăng;
   - pod restart nhiều;
   - audit chain invalid;
   - malware detected.

Tiêu chí hoàn thành:

- Có Grafana dashboard cho service.
- Có alert/demo event tối thiểu.
- Có log/metric liên kết được với traceId hoặc service.

## D-P2.1 - SBOM và image signing

Lý do ưu tiên: đây là phần DevSecOps nâng cao, nên làm sau khi Harbor và policy gates ổn.

Việc nên làm:

1. Tạo SBOM bằng Syft hoặc Trivy.
2. Archive SBOM theo image tag/digest.
3. Ký image bằng Cosign.
4. Verify signature trước deploy nếu kịp.
5. Tài liệu hóa supply chain:
   - source;
   - build;
   - scan;
   - SBOM;
   - sign;
   - push;
   - deploy.

Tiêu chí hoàn thành:

- Mỗi image có SBOM.
- Image có Cosign signature.
- Pipeline hoặc admission policy verify được signature.

## D-P3 - Future work cho Pipeline: LLM remediation, AIOps và Artifactory

Chỉ nên làm sau khi D-P0 và D-P1 ổn:

1. LLM auto-remediation cho IaC:
   - Checkov/Conftest phát hiện lỗi;
   - LLM sinh patch;
   - chạy lại validate/scan;
   - pass thì tạo PR;
   - cấm bypass bằng skip rule.
2. AIOps post-deploy:
   - Prometheus metrics;
   - Loki logs;
   - audit events;
   - anomaly detection;
   - notify/scale/restart/rollback với guardrails.
3. Nexus cache npm/Maven/Docker dependencies nếu cần tối ưu build và trả lời rõ phần artifact/dependency cache.
4. Artifactory có thể đưa vào báo cáo như enterprise reference, không nên ưu tiên triển khai nếu Harbor đã đủ cho MVP.
5. Chaos testing để tạo dữ liệu vận hành.

---

# Thứ tự thực hiện đề xuất

## Sprint 1 - Web App security core

1. W-P0.1: Secret/key lifecycle, bỏ fallback secret, MFA/key rotation runbook.
2. W-P0.2: Policy đọc metadata/detail/history/comment/ACL thống nhất.
3. W-P0.3: Khóa audit ingestion bằng service-to-service auth.
4. W-P0.4: Audit verify-chain demo tamper detection.
5. W-P0.5: E2E security matrix.

## Sprint 2 - Pipeline DevSecOps core

1. D-P0.1: Tách GitOps dev/prod.
2. D-P0.2: PVC cho Postgres/Mongo/MinIO.
3. D-P0.3: Harbor registry và image promotion.
4. D-P0.4: Policy as Code gate.
5. D-P0.5: Siết CI gates fail thật.
6. D-P0.6: RBAC Jenkins/ArgoCD/Kubernetes/Harbor.

## Sprint 3 - Enterprise evidence

1. W-P1.1: Malware scan upload.
2. W-P1.2: DLP tối thiểu.
3. W-P1.3: Encryption at rest và presigned URL posture.
4. W-P1.5: Retention/records management evidence.
5. D-P1.1: Evidence fail/pass pipeline.
6. D-P1.2: GitOps-safe secret management.
7. D-P1.3: Observability dashboard.

## Sprint 4 - Nâng cao nếu còn thời gian

1. D-P2.1: SBOM và Cosign signing.
2. W-P2.1: Shared auth/contracts.
3. W-P1.4: Security dashboard ứng dụng.
4. W-P3/D-P3: AI/AIOps/LLM remediation future work.

---

# Một câu định vị nên dùng trong báo cáo

DocVault hiện đã có nền tảng của một hệ thống quản lý tài liệu bảo mật cloud-native. Phần Web App cần tiếp tục hoàn thiện bảo vệ tài liệu ở runtime thông qua RBAC/ACL/status/classification, audit hash-chain, secret/key rotation, malware/DLP và evidence E2E. Phần Pipeline DevSecOps cần chứng minh supply chain an toàn thông qua private registry, Policy as Code, security gates, GitOps dev/prod, persistent storage, RBAC hạ tầng và bộ evidence fail/pass. Hai phần này kết hợp giúp DocVault khác một web quản lý tài liệu thông thường và tiến gần hơn tới mô hình enterprise DMS DevSecOps-first.
