# Tổng hợp Web Runtime đã triển khai và hướng dẫn kiểm thử

Ngày lập: 2026-05-31

Phạm vi: tài liệu này chỉ tổng hợp các cải tiến Web App / runtime security của DocVault. Không bao gồm DevSecOps pipeline, GitOps, registry, Kubernetes policy, Jenkins, ArgoCD hay các hạng mục hạ tầng pipeline.

Nguồn đối chiếu:

- `docs/architecture/web-security-evidence.md`
- `docs/runbooks/web-key-rotation-and-mfa-runbook.md`

## 1. Tổng quan kết quả

DocVault đã được nâng cấp từ một web app quản lý tài liệu có upload/download thành một hệ thống quản lý tài liệu bảo mật có các lớp kiểm soát sau:

- Kiểm soát truy cập theo RBAC + ACL + trạng thái tài liệu + classification.
- Compliance Officer có thể kiểm toán metadata/audit nhưng không được xem nội dung file.
- Download/preview nhạy cảm đi qua grant token ngắn hạn, có hỗ trợ key rotation bằng `kid`.
- Audit event có trust boundary service-to-service và hash-chain để phát hiện sửa log.
- Upload file có malware scan và DLP scan trước/sau khi lưu metadata.
- Tài liệu nhạy cảm bị hạn chế presigned URL trực tiếp, ưu tiên stream qua service để watermark.
- Retention/records management có trường dữ liệu, endpoint, audit và workflow history.
- Security dashboard tổng hợp deny, malware, DLP, audit-chain, risk scoring, behavior anomaly và recommendation.
- Evidence Center gom audit-chain, recommendation packet, document packet và retention evidence thành workspace compliance riêng.
- Demo Kit gom screenshot targets, presenter flow, scope note và markdown export để trình bày bằng chứng Web runtime.
- Notification Center có work queue đầy đủ cho approvals, retention, security và document events, kèm read/unread filter và target links.
- Documents/My Documents có smart workbench views, search/filter thương mại hơn: saved views, quick views, owner, tag, status, classification, sort, reset, active chips và URL query state.
- Document detail/preview có metadata summary, evidence links và trạng thái preview rõ ràng theo policy/format.
- Approvals có SLA summary, assignment lane, due status, filter/sort và drawer hiển thị SLA context.
- Approval UX có readiness checklist, attention reasons và reject reason presets cho approver.
- AI-ready guardrails xác định rõ metadata-safe và content-denied operations trước khi tích hợp LLM thật.
- Access impact preview giúp thay đổi classification có cảnh báo trước khi submit.
- One-click compliance evidence packet để xuất gói bằng chứng cho từng document.

Ước lượng theo kế hoạch Web App: khoảng 96-98% hoàn thành. Các hạng mục bắt buộc cho demo bảo mật web app đã có code và test; phần còn lại chủ yếu là chụp evidence và các nâng cấp AI/enterprise thật sự.

## 2. Bảng đối chiếu hạng mục đã làm

| Mã mục | Nội dung | Trạng thái | Bằng chứng chính |
|---|---|---|---|
| W-P0.1 | Secret/key lifecycle, MFA, grant token rotation | Gần xong | `docs/runbooks/web-key-rotation-and-mfa-runbook.md`, grant token env `GRANT_TOKEN_CURRENT_KID` / `GRANT_TOKEN_PREVIOUS_KID` |
| W-P0.2 | RBAC + ACL + status + classification policy | Gần xong | `services/metadata-service/src/policy/policy.service.ts`, `apps/web/src/lib/auth/permissions.ts` |
| W-P0.3 | Audit ingestion trust boundary | Xong | `services/audit-service/src/auth/service-token.guard.ts` |
| W-P0.4 | Audit hash-chain tamper evidence | Gần xong | `GET /audit/verify-chain`, audit tamper demo script |
| W-P0.5 | E2E security evidence | Gần xong | `scripts/e2e-check.mjs`, `pnpm test:e2e` |
| W-P1.1 | Malware scan upload | Gần xong | `services/document-service/src/security/malware-scanner.service.ts` |
| W-P1.2 | DLP và classification policy | Gần xong | DLP state, downgrade guard, admin override audit |
| W-P1.3 | Encryption at rest và presigned URL posture | Đủ cho MVP | MinIO SSE, sensitive stream-only download |
| W-P1.4 | Security dashboard | Xong | `apps/web/src/app/(app)/security/page.tsx` |
| W-P1.5 | Evidence Center | Xong | `apps/web/src/app/(app)/evidence/page.tsx` |
| W-P1.6 | Notification Center work queue | Xong | `apps/web/src/app/(app)/notifications/page.tsx`, `apps/web/src/features/notifications/notifications-center.ts` |
| W-P1.7 | Commercial document search/filter UX | Xong | `apps/web/src/features/documents/document-filter-model.ts`, `apps/web/src/components/documents/document-filters.tsx` |
| W-P1.8 | Retention / records management | Gần xong | `/metadata/retention/documents`, `/metadata/retention/run` |
| W-P1.9 | Document detail/preview UX polish | Xong | `apps/web/src/features/documents/document-detail-presentation.ts`, `apps/web/src/components/documents/document-versions-card.tsx` |
| W-P1.10 | Approval readiness UX | Xong | `apps/web/src/features/documents/document-approval-readiness.ts`, `apps/web/src/components/documents/document-approval-readiness-card.tsx` |
| W-P1.11 | Web Runtime Evidence Demo Kit | Xong | `apps/web/src/features/demo/demo-evidence-kit.ts`, `apps/web/src/app/(app)/demo-kit/page.tsx` |
| W-P1.12 | Approval SLA + Assignment runtime triage | Xong | `apps/web/src/features/approvals/approval-sla.ts`, `apps/web/src/components/common/approvals/approvals-table.tsx` |
| W-P1.13 | Document Saved Views workbench | Xong | `apps/web/src/features/documents/document-saved-views.ts`, `apps/web/src/components/documents/document-filters.tsx` |
| W-P2.1 | Shared auth/contracts | Một phần lớn | `@docvault/auth/rbac`, OpenAPI gateway contract |
| W-P3 | AI-ready / future security intelligence | AI-ready đã mạnh, chưa có LLM thật | AI guardrails, access impact, risk scoring, anomaly, recommendation |

## 3. Các tính năng mới và ý nghĩa

### 3.1. Secret, grant token và key rotation

**Đã làm**

- `DOWNLOAD_GRANT_SECRET` và `PREVIEW_GRANT_SECRET` không còn fallback hard-code.
- Metadata-service ký grant token cho download/preview.
- Document-service verify grant token trước khi stream/download.
- Có cơ chế zero-downtime rotation bằng:
  - `GRANT_TOKEN_CURRENT_KID`
  - `GRANT_TOKEN_PREVIOUS_KID`
  - `DOWNLOAD_GRANT_SECRET_<kid>`
  - `PREVIEW_GRANT_SECRET_<kid>`
- Có runbook MFA và key rotation tại `docs/runbooks/web-key-rotation-and-mfa-runbook.md`.

**Ý nghĩa**

- Nếu secret bị lộ, có thể rotate key mà không làm logout/deny tất cả grant đang còn TTL.
- Grant token chỉ sống ngắn hạn, giảm rủi ro nếu token bị copy.
- Tách download grant và preview grant giúp hạn chế ảnh hưởng khi một loại secret gặp sự cố.

**Cách sử dụng / kiểm tra**

1. Đảm bảo `services/metadata-service/.env` và `services/document-service/.env` có cùng bộ secret.
2. Legacy dev mode:

```env
DOWNLOAD_GRANT_SECRET=replace-with-strong-download-grant-secret
PREVIEW_GRANT_SECRET=replace-with-strong-preview-grant-secret
```

3. Rotation mode:

```env
GRANT_TOKEN_CURRENT_KID=2026_05
GRANT_TOKEN_PREVIOUS_KID=2026_04
DOWNLOAD_GRANT_SECRET_2026_05=replace-with-current-download-grant-secret
DOWNLOAD_GRANT_SECRET_2026_04=replace-with-previous-download-grant-secret
PREVIEW_GRANT_SECRET_2026_05=replace-with-current-preview-grant-secret
PREVIEW_GRANT_SECRET_2026_04=replace-with-previous-preview-grant-secret
```

4. Chạy unit test liên quan:

```bash
pnpm --filter metadata-service test -- policy.service.spec.ts
pnpm --filter document-service test -- download-grant.util.spec.ts preview-grant.util.spec.ts
```

### 3.2. MFA demo posture

**Đã làm**

- Keycloak realm có required action `CONFIGURE_TOTP`.
- Có tài khoản demo MFA riêng cho admin/compliance:
  - `co-mfa-demo`
  - `admin-mfa-demo`
- Các tài khoản automation như `co1`, `admin1` vẫn giữ không bật OTP để E2E/password-grant chạy được.

**Ý nghĩa**

- Báo cáo có thể trả lời góp ý về MFA/2FA mà không làm hỏng automation test.
- Tách human admin/compliance và automation service account là cách trình bày đúng hơn về production posture.

**Cách kiểm tra thủ công**

1. Mở Keycloak local: `http://localhost:8080`.
2. Đăng nhập realm `docvault`.
3. Kiểm tra user `co-mfa-demo` hoặc `admin-mfa-demo`.
4. Xác nhận user có required action `CONFIGURE_TOTP`.
5. Đăng nhập interactive để thấy yêu cầu cấu hình OTP.

### 3.3. Authorization policy thống nhất

**Đã làm**

- Metadata detail, workflow history, comments và ACL list cùng đi qua policy read metadata.
- Download/preview phân biệt rõ:
  - metadata read
  - preview file content
  - download/presign/stream file content
- ACL hỗ trợ `USER`, `ROLE`, `GROUP`, `ALL`.
- GROUP ACL normalize group Keycloak, ví dụ `/finance-team` thành `finance-team`.
- DENY ACL ưu tiên cao hơn baseline allow.
- Compliance Officer bị chặn preview, stream, presign và download file content.
- Frontend action visibility dùng helper `getDocumentAccessDecision(...)` để hiện lý do bị chặn.

**Ý nghĩa**

- Hệ thống không còn chỉ check role đơn giản.
- Người dùng không thể đoán docId để xem detail/history/comment/ACL khi không có quyền.
- Compliance Officer đúng vai trò kiểm toán: xem audit/metadata theo policy, không xem nội dung file.

**Cách kiểm tra thủ công**

1. Đăng nhập `editor1`, tạo document và upload file.
2. Submit document sang Pending.
3. Đăng nhập `approver1`, approve document thành Published.
4. Đăng nhập `viewer1`, thử xem/download document Published nếu ACL cho phép.
5. Đăng nhập `co1`, vào detail/audit để kiểm tra metadata/audit, sau đó thử preview/download:
   - Kết quả đúng: preview/download bị deny.
6. Tạo document `CONFIDENTIAL` hoặc `SECRET`, thử download bằng viewer không có ACL:
   - Kết quả đúng: deny hoặc không có direct presigned URL.

**Lệnh test liên quan**

```bash
pnpm --filter metadata-service test
pnpm --filter web test -- permissions.spec.ts document-preview-dialog.spec.ts
pnpm test:e2e
```

### 3.4. Audit ingestion boundary

**Đã làm**

- `POST /audit/events` là endpoint internal.
- Muốn ghi audit event phải có header `x-docvault-service-token`.
- Token này được validate bằng `AUDIT_INGEST_TOKEN`.
- Gateway và audit-service cùng chặn user JWT thường append audit giả.

**Ý nghĩa**

- Audit trail đáng tin cậy hơn vì user thường không thể tự tạo event "giả".
- Hash-chain audit chỉ có ý nghĩa khi nguồn ghi event bị kiểm soát.

**Cách kiểm tra**

1. Đảm bảo `AUDIT_INGEST_TOKEN` giống nhau trong:
   - `services/gateway/.env`
   - `services/metadata-service/.env`
   - `services/document-service/.env`
   - `services/audit-service/.env`
2. Chạy:

```bash
pnpm --filter audit-service test -- service-token.guard.spec.ts
pnpm --filter gateway test
pnpm test:e2e
```

3. Trong E2E, viewer thường gọi audit ingest phải bị deny.

### 3.5. Audit hash-chain và tamper evidence

**Đã làm**

- Mỗi audit event có `prevHash` và `hash`.
- Có endpoint verify-chain:
  - Backend: `GET /audit/verify-chain`
  - Gateway/API: `/api/audit/verify-chain`
- Web Audit page có action verify chain.
- Có script demo sửa event cũ trong MongoDB để chain invalid.
- Evidence packet ghi lại audit-chain status tại thời điểm export.

**Ý nghĩa**

- Audit log là tamper-evident: nếu ai đó sửa event cũ trong storage, verify-chain sẽ báo invalid.
- Đây không phải blockchain và không làm log bất biến tuyệt đối; nó dùng để phát hiện log bị sửa.

**Cách kiểm tra**

```bash
pnpm --filter audit-service test -- audit-hash.spec.ts
pnpm --filter audit-service audit:tamper-demo:test
```

Demo local an toàn:

```bash
pnpm --filter audit-service audit:tamper-demo -- --dry-run
```

Chỉ khi muốn cố tình sửa dữ liệu demo local mới chạy:

```powershell
$env:DOCVAULT_ALLOW_AUDIT_TAMPER_DEMO='true'
pnpm --filter audit-service audit:tamper-demo -- --apply
```

Sau đó mở trang Audit và bấm Verify Chain. Kết quả kỳ vọng: `valid=false`.

### 3.6. Malware scan khi upload

**Đã làm**

- Document-service scan file upload trước khi ghi MinIO.
- Chế độ demo mặc định `local-eicar` chặn EICAR payload.
- Có chế độ optional `clamav`.
- Nếu malware bị phát hiện:
  - không ghi object vào MinIO
  - không tạo metadata version chính thức
  - emit audit `MALWARE_UPLOAD_BLOCKED`

**Ý nghĩa**

- DocVault có lớp threat protection như các DMS enterprise.
- Demo bằng EICAR giúp chứng minh flow chặn malware mà không cần file độc hại thật.

**Cách kiểm tra**

1. Đặt mode demo:

```env
MALWARE_SCANNER_MODE=local-eicar
```

2. Tạo file EICAR test và upload qua UI hoặc E2E.
3. Kết quả đúng:
   - upload bị deny
   - không có version mới
   - audit có `MALWARE_UPLOAD_BLOCKED`

Unit test:

```bash
pnpm --filter document-service test -- malware-scanner.service.spec.ts documents.service.spec.ts
```

### 3.7. DLP và classification guard

**Đã làm**

- DLP scan phát hiện:
  - email
  - phone/national-id-like values
  - keyword `secret`, `confidential`, `internal only`
- Nếu tài liệu `PUBLIC` / `INTERNAL` có DLP hit, hệ thống escalate lên `CONFIDENTIAL`.
- Non-admin không được downgrade tài liệu có DLP hit xuống `PUBLIC`.
- Admin downgrade override phải nhập `classificationOverrideReason`.
- Audit event liên quan:
  - `DLP_PATTERN_DETECTED`
  - `DLP_CLASSIFICATION_DOWNGRADE_DENIED`
  - `DLP_CLASSIFICATION_OVERRIDE_APPROVED`
- Web document detail hiện DLP evidence nhưng không hiện raw sensitive value.

**Ý nghĩa**

- Tránh public hóa tài liệu có dữ liệu nhạy cảm.
- Admin vẫn có đường override có lý do và có audit, phù hợp với compliance workflow.
- UI chỉ hiện count/category/severity, không rò rỉ nội dung sensitive.

**Cách kiểm tra thủ công**

1. Đăng nhập `editor1`.
2. Tạo/upload file có nội dung ví dụ:

```text
confidential internal only contact test@example.com
```

3. Kiểm tra document detail:
   - classification bị gợi ý/escalate lên `CONFIDENTIAL`
   - DLP evidence hiện category/count/severity
4. Thử edit classification xuống `PUBLIC` bằng editor:
   - Kết quả đúng: bị deny.
5. Đăng nhập admin, thử downgrade và nhập override reason:
   - Kết quả đúng: được chấp nhận và có audit override.

Test:

```bash
pnpm --filter metadata-service test -- documents.service.spec.ts
pnpm --filter document-service test -- documents.service.spec.ts
pnpm --filter web test
```

### 3.8. Encryption at rest và download posture

**Đã làm**

- MinIO local bucket init có SSE-S3.
- `PUBLIC` / `INTERNAL` Published có thể nhận presigned URL ngắn hạn nếu policy cho phép.
- `CONFIDENTIAL` / `SECRET` đánh dấu `watermarkRequired=true`.
- Khi `watermarkRequired=true`, response không trả direct `url`, chỉ trả `streamingEndpoint`.
- Stream path re-authorize/verify grant token và watermark trước khi trả file.
- Compliance Officer luôn bị chặn preview/stream/presign/download.

**Ý nghĩa**

- File nhạy cảm không lộ direct presigned URL để tải thẳng từ object storage.
- Luồng stream qua service cho phép enforce watermark và audit.
- Có câu trả lời rõ cho câu hỏi "file được mã hóa và tải về an toàn như thế nào".

**Cách kiểm tra**

1. Tạo tài liệu `CONFIDENTIAL`, approve thành `PUBLISHED`.
2. Gọi download authorize bằng editor/owner.
3. Kết quả đúng:
   - `url: null`
   - `watermarkRequired: true`
   - có `streamingEndpoint`
4. Mở streaming endpoint để tải file qua controlled path.
5. Thử cùng thao tác bằng `co1`:
   - Kết quả đúng: deny.

E2E đã cover:

```bash
pnpm test:e2e
```

### 3.9. Security dashboard và recommendation engine

**Đã làm**

- Web page: `/security`.
- Chỉ Compliance Officer/Admin được xem.
- Dashboard hiện:
  - audit-chain posture
  - deny counters
  - malware blocked
  - DLP detections
  - download denied
  - high-volume content access
  - sensitive preview/download grants
  - recent DENY/DLP events
  - risky documents
  - behavior anomaly signals
  - prioritized security recommendations
- Recommendation engine tạo action deterministic từ audit metadata, không dùng file content.
- Khi xem recommendation, audit event `SECURITY_RECOMMENDATIONS_VIEWED` được ghi với ids/counts/types/filter, không ghi token hay nội dung file.
- Recommendation có workflow trạng thái qua `PATCH /audit/security-recommendations/:id/workflow`.
- Enum workflow: `OPEN`, `INVESTIGATING`, `REVIEWED`, `RESOLVED`; security summary overlay `recommendation.workflow`, mặc định `OPEN` nếu chưa có event workflow.
- Khi cập nhật workflow, audit event `SECURITY_RECOMMENDATION_STATUS_UPDATED` được ghi với `resourceType=SECURITY_RECOMMENDATION`, `resourceId` là recommendation id.
- Recommendation card có History timeline để xem lịch sử workflow metadata-only.
- History endpoint:
  - `GET /audit/security-recommendations/:id/workflow-history`
- Workflow history chỉ trả metadata entries:
  - `eventId`
  - `status`
  - `note?`
  - `updatedAt`
  - `updatedBy`
- Recommendation card có action download evidence packet JSON.
- Recommendation evidence packet được tạo client-side từ recommendation, audit-chain status và workflow history.
- Packet là metadata-only và có `excludedSensitiveFields` để ghi rõ các trường nhạy cảm bị loại trừ.
- Recommendation card có Playbook deterministic:
  - owner gợi ý theo loại recommendation
  - SLA target theo severity
  - due date tính từ lần workflow update gần nhất
  - checklist triage/investigate/review/resolve tự cập nhật theo workflow status

**Ý nghĩa**

- Đây là điểm vượt lên web quản lý tài liệu thông thường: compliance/admin có màn hình an ninh tổng hợp và có hành động gợi ý.
- Recommendation deterministic giúp demo ổn định, không phụ thuộc LLM.
- Không đưa file content/object key/presigned URL/grant token vào recommendation metadata.
- Workflow recommendation giúp compliance/admin ghi nhận điều tra/review/resolve mà vẫn giữ audit trail metadata-safe.
- History timeline giúp chứng minh tiến trình xử lý recommendation mà không mở rộng quyền xem nội dung file.
- Evidence packet JSON giúp xuất bằng chứng kiểm toán riêng cho recommendation, vẫn loại trừ file content, object key, presigned URL và grant token.
- Playbook biến recommendation từ cảnh báo tĩnh thành quy trình xử lý có owner, SLA và checklist, giúp demo rõ hơn phần vận hành sau phát hiện rủi ro.

**Cách sử dụng**

1. Chạy web:

```bash
pnpm --filter web dev
```

2. Mở:

```text
http://localhost:3006/security
```

3. Đăng nhập bằng `co1` hoặc `admin1`.
4. Bấm các link Open audit trong quick filters, risky documents, behavior anomalies hoặc recommendations.
5. Kiểm tra trang Audit được mở với filter tương ứng.
6. Cập nhật workflow cho một recommendation qua UI hoặc API:

```json
{
  "status": "INVESTIGATING",
  "note": "Đang kiểm tra chuỗi deny liên quan."
}
```

7. Gọi lại `GET /audit/security-summary` và kiểm tra recommendation tương ứng có `workflow.status`.
8. Mở Audit và lọc `action=SECURITY_RECOMMENDATION_STATUS_UPDATED`, `resourceType=SECURITY_RECOMMENDATION`, `resourceId=<recommendation-id>`.
9. Kiểm tra metadata audit chỉ có dữ liệu như `recommendationId`, `status`, `note`; không có file content, object key, presigned URL hoặc grant token.
10. Mở History trên recommendation card và kiểm tra timeline có entry metadata-only tương ứng với status vừa cập nhật.
11. Download evidence packet JSON từ recommendation card.
12. Kiểm tra packet có recommendation, audit-chain status, playbook, workflow history và `excludedSensitiveFields`; không có file content, object key, presigned URL hoặc grant token.
13. Kiểm tra Playbook hiển thị owner gợi ý, SLA badge, due date và checklist đổi trạng thái theo workflow.

**Cách test**

```bash
pnpm --filter audit-service test -- security-summary.spec.ts
pnpm --filter web test -- security-dashboard.spec.ts
pnpm --filter audit-service build
pnpm --filter web exec tsc --noEmit
pnpm --filter web build
```

### 3.10. Evidence Center

**Đã triển khai**

- Web page: `/evidence`.
- Role: compliance/admin.
- Source cards cho:
  - Audit Chain
  - Recommendation Packets
  - Retention Evidence
  - Document Packets
- Export `docvault-evidence-center-manifest.json`.
- Export từng recommendation evidence packet trực tiếp từ Evidence Center.
- Export từng document evidence packet từ retention evidence records.
- Manifest và document packet export là metadata-only và ghi `excludedSensitiveFields`.
- Document packet export loại bỏ cả alias nhạy cảm như `storagePath` và `downloadToken`, ngoài `objectKey`, `presignedUrl`, `grantToken`, `fileContent`.
- Evidence Bundle Builder cho phép chọn nhiều recommendation/document packet và export một case bundle manifest metadata-only.
- Evidence Case Presentation hiển thị case id, readiness status, audit-chain posture, retention posture, checklist, recommendation timeline và document packet list từ bundle đang chọn.
- Export Evidence Report HTML standalone, có thể mở/print trong browser và vẫn metadata-only.

**Ý nghĩa**

- Gom bằng chứng compliance vào một workspace riêng thay vì bắt người demo nhảy qua nhiều trang.
- Tạo “demo bundle manifest” để trình bày báo cáo: audit-chain, recommendation ids, document packet ids, retention summary.
- Tạo case bundle manifest có counts, checklist, audit-chain status, retention summary và danh sách packet đã chọn để trình bày một hồ sơ kiểm toán có cấu trúc.
- Biến bundle JSON thành màn hình presentation/report dễ hiểu hơn, gần với sản phẩm compliance thương mại.
- Không phải DevSecOps; đây là tính năng web/runtime compliance evidence.

**Cách sử dụng**

1. Đăng nhập `co1` hoặc admin.
2. Mở:

```text
http://localhost:3006/evidence
```

3. Kiểm tra 4 source cards.
4. Nhấn `Export manifest`.
5. Kiểm tra manifest có `metadataOnly`, `excludedSensitiveFields`, audit-chain status, recommendation packet ids và document packet ids.
6. Export một recommendation packet.
7. Export một document evidence packet.
8. Tick các packet cần gom vào bundle, hoặc dùng `Select recommendations` / `Select documents`.
9. Nhấn `Export bundle`.
10. Kiểm tra bundle manifest có `metadataOnly`, `excludedSensitiveFields`, counts, checklist, audit-chain status, retention summary và packet filenames.
11. Nhấn `Report` để tải Evidence Report HTML.
12. Chuyển sang tab `Presentation`.
13. Chỉ ra case readiness, audit-chain, retention posture, checklist, recommendation timeline và document packet list.
14. Dùng deep link sang Audit/Security/Retention/Document detail để chỉ ra evidence chain.

**Cách test**

```bash
pnpm --filter web test -- evidence-center.spec.ts evidence-report.spec.ts
pnpm --filter web exec tsc --noEmit
```

### 3.11. Notification Center work queue

**Đã triển khai**

- Web page: `/notifications`.
- Sidebar có mục Notifications cho viewer/editor/approver/compliance/admin.
- Bell topbar có link mở full Notification Center.
- Notification model gom work queue theo:
  - Approvals
  - Retention
  - Security
  - Documents
- Có summary cards cho tổng số và unread count từng nhóm.
- Có filter `All / Unread / Read`.
- Có action `Mark read` từng dòng và `Mark all read` toàn bộ queue.
- Notification rows hiển thị type, severity, group, read state, timestamp, target link và reason/description.
- Target links điều hướng về `/approvals`, `/retention`, `/security`, `/audit` hoặc document detail.
- Mapping đã chuẩn bị cho event tương lai như `RETENTION_OVERDUE`, `DLP_DETECTED`, `MALWARE_BLOCKED`, `AUDIT_CHAIN_INVALID`.

**Ý nghĩa**

- Biến notification từ dropdown nhỏ thành work queue giống sản phẩm thương mại.
- Approver có thể mở nhanh pending submissions; compliance/admin có thể nhìn retention/security/audit items theo mức độ nghiêm trọng.
- Read/unread filter giúp trình bày trạng thái xử lý công việc mà không cần reload page.

**Cách sử dụng**

1. Đăng nhập bằng bất kỳ role hợp lệ.
2. Mở bell topbar và chọn `Open notification center`, hoặc mở trực tiếp:

```text
http://localhost:3006/notifications
```

3. Chọn summary card `Approvals`, `Retention`, `Security` hoặc `Documents`.
4. Chuyển filter `Unread` để chỉ xem việc chưa xử lý.
5. Bấm `Mark read` trên một dòng hoặc `Mark all read`.
6. Bấm target action để điều hướng tới trang xử lý tương ứng.

**Cách test**

```bash
pnpm --filter web test -- notifications-center.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

### 3.12. Commercial document search/filter UX

**Đã triển khai**

- Web pages: `/documents` và `/my-documents`.
- Smart workbench quick views có count:
  - All
  - Needs action
  - Drafts
  - Pending review
  - Published
  - Sensitive
- Search match trên title, description, filename, tags, owner id và owner display.
- Filter theo:
  - Status
  - Classification
  - Owner
  - Tag
- Sort theo recently updated, created date, title, status, classification và owner.
- Active filter chips có thể xóa từng filter.
- Reset toàn bộ filter/sort về mặc định.
- Empty state giải thích filter nào đang làm danh sách rỗng.
- `/documents` đồng bộ query string, ví dụ `?view=sensitive&q=finance&status=PUBLISHED&tag=board&sort=title&dir=asc`.
- Filter model được tách thành helper testable để không nhồi logic vào page component.

**Ý nghĩa**

- Trải nghiệm Documents gần hơn với app quản lý tài liệu thương mại: người dùng có thể chuyển nhanh giữa workbench views, scan count theo nhóm việc, combine filter, bỏ filter nhanh và gửi link trạng thái filter.
- Không đổi backend contract trong giai đoạn này. Filter hiện vẫn chạy trên dữ liệu document list đã tải về từ frontend; backend search/filter sâu hơn có thể làm ở phase enterprise sau.

**Cách sử dụng**

1. Đăng nhập viewer/editor/admin.
2. Mở:

```text
http://localhost:3006/documents
```

3. Search theo title/tag/owner/filename.
4. Chọn quick view `Needs action`, `Pending review`, `Published` hoặc `Sensitive`.
5. Chọn status, classification, owner hoặc tag.
6. Đổi sort và kiểm tra thứ tự bảng.
7. Xóa từng active chip hoặc bấm `Reset`.
8. Copy URL có query string và reload để kiểm tra filter state được khôi phục.

**Cách test**

```bash
pnpm --filter web test -- document-filter-model.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

### 3.13. Document detail/preview UX polish

**Đã triển khai**

- Web page: `/documents/:id`.
- Metadata summary card hiển thị nhanh owner, status, classification, retention, current version, checksum rút gọn, content type và published/updated date.
- Version History hiển thị trạng thái preview theo từng version:
  - `Preview supported`
  - `Preview blocked by policy`
  - `Preview unsupported`
- Preview button bị disable khi policy chặn hoặc format không preview trực tiếp được.
- Lý do preview/download bị chặn được đặt gần Version History để Compliance Officer thấy rõ vì sao không được xem file content.
- Evidence links card cho compliance/admin dẫn tới:
  - Audit events đã filter theo document id.
  - Evidence Center.
  - Retention records nếu document có retention metadata.
  - Security posture nếu document có DLP detection.
- Preview error handling không log response body ra console.

**Ý nghĩa**

- Document detail giống app thương mại hơn vì người dùng đọc được trạng thái vận hành của tài liệu ngay trên một màn hình.
- Compliance Officer có đường đi từ metadata sang audit/evidence/retention/security mà vẫn không mở quyền xem file content.
- Người demo giải thích được ba case khác nhau: preview được, bị chặn bởi policy, hoặc không hỗ trợ format.
- Không đổi backend contract trong giai đoạn này; đây là lớp presentation/UX trên policy và metadata hiện có.

**Cách sử dụng**

1. Đăng nhập `editor1`, `viewer1`, `co1` hoặc `admin1`.
2. Mở một document detail:

```text
http://localhost:3006/documents/<document-id>
```

3. Kiểm tra metadata summary ngay dưới header.
4. Trong Version History, kiểm tra preview posture của từng version.
5. Đăng nhập `co1` và mở cùng document:
   - Kết quả đúng: preview/download bị chặn và reason được hiển thị gần Version History.
   - Evidence links dẫn sang Audit/Evidence/Retention/Security nếu role và document metadata phù hợp.
6. Với file không hỗ trợ preview trực tiếp, ví dụ `.zip`, preview button bị disable với lý do unsupported format.

**Cách test**

```bash
pnpm --filter web test -- document-detail-presentation.spec.ts document-detail-polish-cards.spec.ts document-preview-dialog.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

### 3.14. Approval readiness UX

**Đã triển khai**

- Document detail có `Approval readiness` card.
- Approvals review drawer có checklist readiness ngay trong panel review.
- Checklist kiểm tra:
  - file/current version
  - metadata title/description
  - classification
  - tags
  - DLP status
  - retention evidence
  - workflow/submission state
- Trạng thái tổng hợp:
  - `Ready`
  - `Needs attention`
  - `Blocked`
- Reject dialog có reason presets để approver chọn nhanh:
  - missing metadata
  - classification needs review
  - DLP remediation
  - retention evidence incomplete

**Ý nghĩa**

- Approval flow không còn chỉ là nút approve/reject; approver có checklist vận hành để xem tài liệu có đủ điều kiện review chưa.
- Editor/compliance có thể giải thích rõ vì sao tài liệu cần bổ sung metadata/tag/retention/DLP trước khi approve.
- Đây là lớp FE/runtime UX, không thay đổi backend workflow state machine và không claim approval lock production.

**Cách sử dụng**

1. Đăng nhập `editor1`, tạo/upload document và submit.
2. Mở document detail để xem `Approval readiness`.
3. Đăng nhập `approver1`.
4. Mở `/approvals` và chọn một document pending.
5. Kiểm tra drawer có readiness checklist.
6. Bấm reject và chọn một preset reason nếu muốn trả document về draft.

**Cách test**

```bash
pnpm --filter web test -- document-approval-readiness.spec.ts document-approval-readiness-card.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

### 3.15. Retention và records management

**Đã làm**

- Metadata-service lưu:
  - `retentionClass`
  - `retentionUntil`
  - `retentionReason`
- Khi document được approve/publish, retention được tính theo classification:
  - `PUBLIC_730D`
  - `INTERNAL_365D`
  - `CONFIDENTIAL_180D`
  - `SECRET_90D`
- Endpoint:
  - `GET /metadata/retention/documents`
  - `POST /metadata/retention/run`
- Due records được auto-archive thành `ARCHIVED`.
- Workflow history ghi `action=RETENTION`, `actorId=system:retention`.
- Audit ghi `DOCUMENT_AUTO_ARCHIVED`.
- Web page: `/retention`.

**Ý nghĩa**

- DocVault có records management/compliance lifecycle, không chỉ là CRUD document.
- Có bằng chứng document được lưu giữ theo classification và auto-archive khi quá hạn.

**Cách sử dụng**

1. Đăng nhập `co1` hoặc `admin1`.
2. Mở:

```text
http://localhost:3006/retention
```

3. Xem các cột retention class, deadline, status, days remaining.
4. Admin có thể chạy retention demo endpoint nếu UI/action có hỗ trợ trong flow local.

**Cách test**

```bash
pnpm --filter metadata-service test
pnpm test:e2e
```

### 3.16. One-click compliance evidence packet

**Đã làm**

- Compliance/admin có thể export evidence packet theo document:
  - `GET /metadata/documents/:docId/evidence-packet`
- Packet gồm:
  - metadata
  - version checksum
  - ACL
  - workflow history
  - retention evidence
  - audit hash-chain status
  - related audit events
- Packet không gồm:
  - file content
  - object key
  - storage path alias
  - presigned URL
  - preview grant
  - download grant token / download token alias
- Security dashboard cũng có recommendation evidence packet JSON metadata-only, tạo client-side từ recommendation + audit-chain status + playbook + workflow history và ghi `excludedSensitiveFields`.
- Viewer/editor/approver không có role compliance/admin sẽ bị deny.

**Ý nghĩa**

- Rất hữu ích khi báo cáo/demo: một endpoint gom đủ bằng chứng compliance cho tài liệu.
- Bảo đảm compliance officer xem đủ evidence nhưng vẫn không xem nội dung file.

**Cách kiểm tra**

1. Đăng nhập `co1`.
2. Mở document detail có quyền metadata.
3. Chọn action export evidence packet nếu UI hiện.
4. Kiểm tra JSON không có grant token/presigned URL/file content.
5. Đăng nhập `viewer1` và gọi cùng endpoint:
   - Kết quả đúng: 403.

Test:

```bash
pnpm --filter gateway test -- metadata.proxy.controller.spec.ts
pnpm test:e2e
```

### 3.17. AI-ready guardrails

**Đã làm**

- Endpoint:
  - `GET /metadata/documents/:docId/ai-guardrails`
- Trước khi trả AI context decision, endpoint check `assertCanReadMetadata(...)`.
- Response tách rõ:
  - metadata-safe operations: classification/tagging
  - content operations: summarization/Q&A
- Compliance Officer chỉ được metadata-only, bị deny content operations.
- Response không trả file content, object key, presigned URL hay grant token.
- Audit event: `AI_GUARDRAILS_EVALUATED`.
- Web document detail có card AI guardrails.

**Ý nghĩa**

- Đây là nền tảng đúng để tích hợp LLM sau này mà không phá policy hiện tại.
- AI không được đọc nội dung mà user không có quyền đọc.
- Compliance Officer không được dùng AI để "đi vòng" vào nội dung file.

**Cách kiểm tra**

```bash
pnpm --filter metadata-service test -- policy.service.spec.ts
pnpm --filter gateway test -- metadata.proxy.controller.spec.ts
pnpm --filter web test -- document-ai-guardrails-card.spec.ts
```

Kiểm tra thủ công:

1. Mở document detail.
2. Xem AI guardrails card.
3. Đăng nhập `co1` và xác nhận content summarization/Q&A bị deny.

### 3.18. Access impact preview

**Đã làm**

- Endpoint:
  - `POST /metadata/documents/:docId/access-impact`
- Gateway proxy:
  - `POST /metadata/documents/:docId/access-impact`
- Web edit document hiện access impact card khi classification mới khác classification hiện tại.
- Response cho biết:
  - current/proposed classification
  - watermark posture
  - access expansion/reduction warning
  - DLP override requirement
  - role-level metadata/download delta
- Không enumerate user thật và không trả file content/grant/object key.
- Audit event: `DOCUMENT_ACCESS_IMPACT_SIMULATED`.

**Ý nghĩa**

- Trước khi hạ classification, editor/admin thấy được rủi ro mở rộng truy cập.
- Đây là tính năng enterprise-like vì nó giải thích tác động policy trước khi mutate metadata.

**Cách kiểm tra**

1. Đăng nhập owner editor hoặc admin.
2. Mở document edit.
3. Đổi classification, ví dụ `CONFIDENTIAL -> PUBLIC`.
4. Xem access impact card.
5. Nếu document có DLP hit, kiểm tra UI cảnh báo override requirement.

Test:

```bash
pnpm --filter metadata-service test -- policy.service.spec.ts
pnpm --filter gateway test -- metadata.proxy.controller.spec.ts
pnpm --filter web test -- document-access-impact-card.spec.ts
```

### 3.19. Shared auth/contracts và OpenAPI alignment

**Đã làm**

- Các service downstream re-export `Roles`, `ROLES_KEY`, `RolesGuard` từ `@docvault/auth/rbac`.
- OpenAPI gateway contract được cập nhật cho:
  - comments
  - retention
  - evidence packet
  - audit security summary
  - risk scoring
  - behavior anomaly
  - recommendations
  - AI guardrails
  - access impact
  - malware/DLP upload behavior

**Ý nghĩa**

- Giảm drift giữa frontend/backend/contract.
- Swagger/OpenAPI đúng hơn với runtime, tiện cho báo cáo và demo API.

**Cách kiểm tra**

```bash
pnpm --filter @docvault/auth build
pnpm --filter gateway build
pnpm --filter gateway test
node -e "const fs=require('fs'); const yaml=require('js-yaml'); yaml.load(fs.readFileSync('libs/contracts
```

### 3.20. Web Runtime Evidence Demo Kit

**Đã triển khai**

- Web page: `/demo-kit`.
- Sidebar có mục `Demo Kit` cho `compliance_officer` và `admin`.
- Demo Kit hiển thị:
  - Web/runtime evidence scope.
  - Screenshot targets cho Documents, Document Detail, Approvals, Notifications, Security, Evidence và Retention.
  - Presenter flow từng bước.
  - Out-of-scope notes để tránh claim nhầm DevSecOps pipeline, approval lock production hoặc LLM thật.
  - Copy checklist và Download markdown cho báo cáo.

**Ý nghĩa**

- Người trình bày có một checklist tập trung thay vì phải nhớ từng màn hình cần chụp.
- Dễ chứng minh với giảng viên rằng các cải tiến Web runtime đã có bằng chứng UI cụ thể.
- Đây là presentation/evidence utility, không thay thế Evidence Center và không mở rộng quyền xem dữ liệu nhạy cảm.

**Cách sử dụng**

1. Đăng nhập `co1` hoặc `admin1`.
2. Mở:

```text
http://localhost:3006/demo-kit
```

3. Dùng các route card để mở từng màn cần chụp.
4. Bấm `Copy checklist` hoặc `Download markdown` để lấy checklist đưa vào báo cáo.

**Cách test**

```bash
pnpm --filter web test -- demo-evidence-kit.spec.ts demo-evidence-kit-panel.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

### 3.21. Approval SLA + Assignment runtime triage

**Đã triển khai**

- Approvals page có SLA summary:
  - overdue
  - due soon
  - on time
  - compliance review
- Bảng approvals hiển thị:
  - assignment lane
  - assignment reason
  - SLA state
  - due time
- Có filter theo SLA view:
  - all
  - overdue
  - due soon
  - on time
- Có sort theo:
  - priority
  - due date
  - queued time
- Review drawer hiển thị assignment/SLA ngay trước readiness checklist.
- SLA runtime deterministic:
  - `SECRET`: 8h
  - DLP detected: tối đa 12h
  - `CONFIDENTIAL`: 24h
  - `INTERNAL`: 48h
  - `PUBLIC`: 72h

**Ý nghĩa**

- Approvals không còn là danh sách pending phẳng; approver có work queue để xử lý document theo mức khẩn cấp.
- Assignment lane giúp giải thích vì sao tài liệu đi vào document approver, security approver, compliance review hoặc records reviewer.
- Đây là runtime triage trên frontend từ dữ liệu queue hiện có, chưa claim backend assignment lock hoặc SLA enforcement production.

**Cách sử dụng**

1. Đăng nhập `approver1` hoặc `admin1`.
2. Mở:

```text
http://localhost:3006/approvals
```

3. Xem SLA summary cards.
4. Lọc `Overdue` / `Due soon` / `On time`.
5. Sort theo priority, due date hoặc queued time.
6. Mở một document để xem assignment/SLA trong drawer cùng readiness checklist.

**Cách test**

```bash
pnpm --filter web test -- approval-sla.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

### 3.22. Document Saved Views workbench

**Đã triển khai**

- Documents và My Documents có vùng `Saved views`.
- Built-in saved views:
  - Pending review
  - Sensitive attention
  - Draft handoff
  - Recently published
  - Confidential library
- Người dùng có thể đặt tên và lưu filter hiện tại thành custom saved view.
- Custom saved views được lưu localStorage bằng key `docvault.documents.savedViews`.
- Saved views tái sử dụng filter model hiện có, gồm:
  - quick view
  - search
  - status
  - classification
  - owner
  - tag
  - sort
- Custom saved views không lưu document content, object key, token hoặc dữ liệu file.

**Ý nghĩa**

- Documents không chỉ có filter một lần; người dùng có thể quay lại các work queue quen thuộc như `Sensitive attention` hoặc `Pending review`.
- Đây là nâng cấp UX giống các document/work management tools thương mại: saved work queues, repeatable review views và nhanh hơn khi demo.
- Hiện tại là FE/runtime saved view bằng localStorage; chưa claim backend user preferences hoặc shared team views production.

**Cách sử dụng**

1. Mở:

```text
http://localhost:3006/documents
```

2. Chọn một saved view built-in.
3. Tạo filter riêng, ví dụ search/tag/classification/sort.
4. Nhập tên tại `Name current view...` rồi bấm `Save`.
5. Chọn saved view vừa lưu để áp lại filter.
6. Bấm biểu tượng xóa trên custom saved view nếu muốn xóa.

**Cách test**

```bash
pnpm --filter web test -- document-saved-views.spec.ts document-filters.spec.ts document-filter-model.spec.ts
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

## 4. Hướng dẫn chạy local để demo các tính năng

### 4.1. Chuẩn bị

Yêu cầu:

- Node.js 20+
- pnpm 9+
- Docker Desktop / Docker Engine

Cài dependency:

```bash
pnpm install
```

Start infra local:

```bash
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d
```

Chạy migration metadata:

```bash
pnpm --filter metadata-service prisma:deploy
```

### 4.2. Chạy backend

Cách dễ theo dõi log nhất là mở từng terminal:

```bash
pnpm --filter metadata-service start:dev
pnpm --filter audit-service start:dev
pnpm --filter document-service start:dev
pnpm --filter notification-service start:dev
pnpm --filter workflow-service start:dev
pnpm --filter gateway start:dev
```

Hoặc dùng script root nếu môi trường local đã cấu hình ổn định:

```bash
pnpm start:sequential
```

### 4.3. Chạy frontend

Package web hiện cấu hình mặc định port `3006`:

```bash
pnpm --filter web dev
```

Mở:

```text
http://localhost:3006
```

API base mặc định:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:3000/api
```

### 4.4. Tài khoản demo

Password seed mặc định: `Passw0rd!`

| Username | Role | Mục đích demo |
|---|---|---|
| `viewer1` | `viewer` | Xem/download tài liệu Published nếu policy cho phép |
| `editor1` | `editor` | Tạo document, upload, submit, edit metadata |
| `approver1` | `approver` | Approve/reject Pending document |
| `co1` | `compliance_officer` | Xem audit/security/retention/evidence, không xem file content |
| `admin1` | `admin` | Thao tác admin và override có audit reason |

## 5. Checklist test nhanh theo mục tiêu demo

### 5.1. Test tự động đầy đủ nhất

Sau khi local stack đang chạy:

```bash
pnpm test:e2e
```

Kết quả kỳ vọng:

- Unauthorized/expired token bị reject.
- Viewer không tạo document được.
- Editor tạo/upload/submit được.
- Approver approve được.
- Viewer download Published document khi policy cho phép.
- Confidential/Secret không lộ direct presigned URL.
- Compliance Officer preview/download/stream bị deny.
- Compliance Officer audit query/verify-chain/security summary được allow.
- Malware EICAR upload bị chặn.
- DLP upload escalate classification.
- DLP downgrade bị chặn nếu không có override hợp lệ.
- Evidence packet có metadata/version/workflow/retention/audit evidence.
- Recommendation evidence packet có recommendation metadata, audit-chain status, playbook, workflow history và `excludedSensitiveFields`.
- Viewer bị deny evidence packet/audit ingest.
- Retention auto-archive tạo workflow history và audit.

### 5.2. Test backend theo service

```bash
pnpm --filter metadata-service test
pnpm --filter document-service test
pnpm --filter audit-service test
pnpm --filter gateway test
```

### 5.3. Test frontend

```bash
pnpm --filter web test
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

Lưu ý: lần verify gần nhất có web lint pass với 0 error và còn một số warning sẵn có ở các component/hook cũ.

### 5.4. Test OpenAPI

```bash
node -e "const fs=require('fs'); const yaml=require('js-yaml'); yaml.load(fs.readFileSync('libs/contracts
```

### 5.5. Test tamper audit local

```bash
pnpm --filter audit-service audit:tamper-demo:test
pnpm --filter audit-service audit:tamper-demo -- --dry-run
```

Chỉ sửa dữ liệu local khi cần demo tamper:

```powershell
$env:DOCVAULT_ALLOW_AUDIT_TAMPER_DEMO='true'
pnpm --filter audit-service audit:tamper-demo -- --apply
```

Sau đó mở `/audit` và verify chain.

## 6. Checklist sử dụng trên UI

| Trang | URL local | Role nên dùng | Kiểm tra |
|---|---|---|---|
| Demo Kit | `http://localhost:3006/demo-kit` | compliance/admin | Web runtime scope, screenshot targets, presenter flow, copy/download markdown |
| Documents | `http://localhost:3006/documents` | viewer/editor/admin | Smart workbench saved views, quick views, commercial search/filter, active chips, URL query state, preview/download button policy |
| New Document | `http://localhost:3006/documents/new` | editor/admin | Tạo document và upload file |
| Document Detail | `http://localhost:3006/documents/:id` | editor/approver/co/admin | Metadata summary, approval readiness, version preview posture, policy denial reason, evidence links, DLP evidence, AI guardrails, evidence packet |
| Document Edit | `http://localhost:3006/documents/:id/edit` | owner editor/admin | Access impact preview khi đổi classification |
| Approvals | `http://localhost:3006/approvals` | approver/admin | SLA summary/filter/sort, assignment lane, readiness checklist, approve/reject Pending document, chọn reject reason presets |
| Notifications | `http://localhost:3006/notifications` | mọi role đăng nhập | Work queue, group filters, read/unread, target links |
| Evidence | `http://localhost:3006/evidence` | compliance/admin | Export evidence manifest, recommendation packets, document packets |
| Audit | `http://localhost:3006/audit` | compliance/admin | Query audit, quick filters, verify chain |
| Security | `http://localhost:3006/security` | compliance/admin | Counters, alerts, risk scoring, anomalies, recommendations, playbook, recommendation history/evidence packet |
| Retention | `http://localhost:3006/retention` | compliance/admin | Retention status và records evidence |

## 7. Demo flow để trình bày với giảng viên

1. Đăng nhập `co1` hoặc `admin1`, mở `/demo-kit`, chỉ ra Web runtime scope, screenshot targets, presenter flow và markdown export.
2. Đăng nhập `editor1`.
3. Tạo document mới, upload file bình thường.
4. Vào `/documents`, chọn saved views `Pending review` / `Sensitive attention`, lưu một custom saved view, chuyển quick views `Needs action` / `Pending review` / `Sensitive`, search theo `finance`, lọc status/classification/tag/owner, chỉ ra count, active chips và URL query state.
5. Submit document.
6. Đăng nhập `approver1`, mở `/approvals`, chỉ ra SLA summary/filter/sort, assignment lane, readiness checklist và reject reason presets, sau đó approve document.
7. Mở bell topbar hoặc `/notifications`, lọc `Approvals` / `Unread`, bấm target link và `Mark read`.
8. Đăng nhập `viewer1`, mở document và download nếu policy cho phép.
9. Trên document detail, chỉ ra metadata summary, Version History preview posture và evidence links nếu role có quyền.
10. Tạo/upload tài liệu có nội dung nhạy cảm, ví dụ có email và keyword `confidential`.
11. Chỉ ra DLP evidence và classification escalation.
12. Tạo/upload EICAR test file để chứng minh malware bị chặn.
13. Đăng nhập `co1`.
14. Vào `/notifications`, lọc `Security` hoặc `Retention` nếu có event tương ứng, chỉ ra severity/read state/target link.
15. Vào `/audit`, query event và verify hash-chain.
16. Thử preview/download document bằng `co1` và chỉ ra reason bị deny ngay trong Version History.
17. Bấm evidence links từ document detail sang Audit/Evidence/Retention/Security để chứng minh luồng metadata-only.
18. Vào `/security`, trình bày:
    - deny/malware/DLP counters
    - risky documents
    - behavior anomaly
    - security recommendations
19. Đổi workflow status của một recommendation và chỉ ra Playbook cập nhật checklist/SLA.
20. Mở History timeline và download recommendation evidence packet JSON.
21. Chỉ ra recommendation packet có playbook, `excludedSensitiveFields` và không có file content/object key/presigned URL/grant token.
22. Vào `/evidence`, export manifest, recommendation packet và document evidence packet.
23. Tick nhiều packet, export Evidence Bundle manifest và chỉ ra checklist/counts/packet filenames.
24. Export Evidence Report HTML và mở report để chỉ ra đây là printable metadata-only report.
25. Chuyển sang tab Presentation, chỉ ra case readiness, audit-chain status, retention posture, checklist, recommendation timeline và document packet list.
26. Chỉ ra manifest/report có `metadataOnly`, audit-chain status, recommendation ids, document packet ids và `excludedSensitiveFields`.
27. Vào document detail, export evidence packet và chỉ ra packet không có file content/object key/storage path/token.
28. Vào `/retention`, trình bày retention class/deadline/status.
29. Vào edit document, đổi classification để xem access impact preview.

## 8. Điểm cần nói rõ trong báo cáo

- Hash-chain là tamper-evident, không phải blockchain và không thay thế immutable storage.
- AI hiện tại là AI-ready guardrails / deterministic security intelligence, chưa phải LLM summarization/QA thật.
- Malware scanning local dùng EICAR deterministic mode; ClamAV là optional mode.
- MinIO SSE là encryption-at-rest MVP; hướng nâng cao là Vault/KMS/client-side encryption/E2EE.
- Compliance Officer có thể xem audit/metadata/evidence theo policy, nhưng không được xem file content.
- Security Recommendation Engine chỉ dùng audit metadata, không đưa nội dung file, object key, presigned URL hay grant token vào output/audit.
- Recommendation history và recommendation evidence packet cũng metadata-only; packet ghi `excludedSensitiveFields` để chứng minh các trường nhạy cảm đã bị loại trừ.
- Recommendation Playbook là lớp điều phối deterministic trên metadata/workflow, không phải DevSecOps pipeline và không mở quyền xem file content.
- Evidence Center là workspace runtime để gom/export evidence; Evidence Bundle manifest và Evidence Report HTML chỉ là metadata index/presentation có checklist/counts, document packet export được scrub các content-bearing fields trước khi tải xuống.
- Demo Kit là presentation/checklist utility cho Web runtime evidence; nó không thay thế Evidence Center và không claim DevSecOps pipeline evidence.
- Notification Center là runtime work queue trong web app, không phải alerting/DevSecOps pipeline; nó gom workflow/retention/security/document events thành danh sách hành động có read state và target links.
- Document smart workbench/search/filter hiện là frontend workbench trên document list đã tải; chưa claim là enterprise search engine hay full-text indexing backend.
- Document Saved Views hiện là FE/runtime user preference bằng localStorage; chưa claim backend shared views hoặc team-level saved search production.
- Document detail/preview polish là UX/presentation layer: làm rõ metadata, policy denial và unsupported format; không claim là backend full-text preview engine.
- Approval readiness là FE/runtime review aid cho approver; chưa claim là backend workflow lock hoặc approval state machine production.
- Approval SLA + Assignment là runtime triage trên dữ liệu queue hiện có; chưa claim backend assignment lock, escalation engine hoặc SLA enforcement production.

## 9. Verification đã ghi nhận gần nhất

Theo evidence hiện tại, các lệnh sau đã được chạy và pass trong đợt verify gần nhất:

```bash
pnpm --filter audit-service test
pnpm --filter audit-service build
pnpm --filter gateway test
pnpm --filter gateway build
pnpm --filter web test
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
```

OpenAPI YAML parse check: openapi ok

```bash
git diff --check
pnpm test:e2e
```

Ghi chú:

- Đợt verify web bổ sung cho Approval readiness / smart workbench / detail polish / evidence / notification ngày 2026-06-03 đã chạy:
  - `pnpm --filter web test -- document-approval-readiness.spec.ts document-approval-readiness-card.spec.ts document-filter-model.spec.ts document-detail-presentation.spec.ts document-detail-polish-cards.spec.ts notifications-center.spec.ts evidence-center.spec.ts evidence-report.spec.ts security-dashboard.spec.ts`
  - `pnpm --filter web exec tsc --noEmit`
  - `pnpm --filter web lint`
  - `pnpm --filter web build`
- Đợt verify web bổ sung cho Demo Kit ngày 2026-06-04 đã chạy:
  - `pnpm --filter web test -- demo-evidence-kit.spec.ts demo-evidence-kit-panel.spec.ts document-approval-readiness.spec.ts document-approval-readiness-card.spec.ts document-filter-model.spec.ts evidence-center.spec.ts evidence-report.spec.ts notifications-center.spec.ts security-dashboard.spec.ts`
  - `pnpm --filter web exec tsc --noEmit`
  - `pnpm --filter web lint`
  - `pnpm --filter web build`
  - `Invoke-WebRequest http://localhost:3006/demo-kit` trả `STATUS=200`.
- Đợt verify web bổ sung cho Approval SLA + Assignment ngày 2026-06-04 đã chạy:
  - `pnpm --filter web test -- approval-sla.spec.ts demo-evidence-kit.spec.ts demo-evidence-kit-panel.spec.ts document-approval-readiness.spec.ts document-approval-readiness-card.spec.ts document-filter-model.spec.ts evidence-center.spec.ts evidence-report.spec.ts notifications-center.spec.ts security-dashboard.spec.ts`
  - `pnpm --filter web exec tsc --noEmit`
  - `pnpm --filter web lint`
  - `pnpm --filter web build`
  - `Invoke-WebRequest http://localhost:3006/approvals` trả `STATUS=200`.
- Đợt verify web bổ sung cho Document Saved Views ngày 2026-06-04 đã chạy:
  - `pnpm --filter web test -- document-saved-views.spec.ts document-filters.spec.ts document-filter-model.spec.ts demo-evidence-kit.spec.ts demo-evidence-kit-panel.spec.ts approval-sla.spec.ts document-approval-readiness.spec.ts document-approval-readiness-card.spec.ts evidence-center.spec.ts evidence-report.spec.ts notifications-center.spec.ts security-dashboard.spec.ts`
  - `pnpm --filter web exec tsc --noEmit`
  - `pnpm --filter web lint`
  - `pnpm --filter web build`
  - `Invoke-WebRequest http://localhost:3006/documents` trả `STATUS=200`.
- web lint pass với 0 error và còn 4 warning sẵn có.
- git diff --check / browser smoke cho diff mới nhất nên chạy lại khi sandbox hoặc local runtime cho phép.
- pnpm test:e2e cần local stack đang chạy.

## 10. Việc còn nên làm nếu muốn polish thêm

1. Chụp `/demo-kit`: Web runtime scope, screenshot targets, presenter flow và markdown export actions.
2. Chụp ảnh UI các trang `/security`, `/audit`, `/retention`, document detail metadata summary/version preview posture/evidence links và access impact preview để đưa vào báo cáo.
3. Tạo bảng completion matrix ngắn trong slide: W-P item, status, file evidence, command test.
4. Chụp evidence workflow security recommendation: chuyển `OPEN -> INVESTIGATING -> REVIEWED/RESOLVED`, chỉ ra Playbook owner/SLA/checklist, mở History timeline, tải recommendation evidence packet JSON và audit event tương ứng.
5. Chụp `/evidence`: source cards, export manifest, export recommendation packet, export document packet.
6. Chụp Evidence Bundle Builder: chọn nhiều packet, export bundle manifest và Evidence Report HTML, chỉ ra checklist/counts.
7. Chụp Evidence Case Presentation tab: readiness status, audit-chain posture, retention posture, recommendation timeline và document packet list.
8. Chụp `/notifications`: summary cards, group filter, unread filter, mark-read action và target link sang Approvals/Security/Retention/Audit/Document.
9. Chụp `/documents`: saved views built-in/custom, quick views có count, owner/tag/status/classification filters, active chips, reset action và URL query state.
10. Chụp document detail với tài khoản `co1`: reason preview/download bị chặn và evidence links sang các workspace compliance.
11. Chụp Approval SLA trên `/approvals`: summary cards, filter/sort, assignment lane, due status và drawer SLA context.
12. Chụp Approval readiness trên document detail và `/approvals`: checklist trong drawer, attention reasons và reject reason presets.
13. Nếu muốn claim AI thật, cần bổ sung LLM summarization/QA có enforcement policy; hiện tại nên claim là AI-ready.
14. Nếu muốn production-like encryption, bổ sung Vault/KMS hoặc client-side encryption trong future work.
