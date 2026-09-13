# Danh sách tính năng web DocVault

Cập nhật: 2026-06-11

Tài liệu này tổng hợp các tính năng đang được triển khai ở frontend `apps/web` của DocVault. Nội dung được đối chiếu từ routes, navigation, component UI và API client hiện có. Tài liệu này mô tả năng lực của web app theo mã nguồn hiện tại, không thay thế cho tài liệu hướng dẫn thao tác chi tiết trong `docs/guides/HUONG_DAN_SU_DUNG_WEB.md`.

## Phạm vi đối chiếu

- Frontend chính: `apps/web/src/app`, `apps/web/src/components`, `apps/web/src/features`, `apps/web/src/lib`.
- Navigation và route: `apps/web/src/lib/constants/nav.ts`, `apps/web/src/lib/constants/routes.ts`.
- API client: `apps/web/src/lib/api/endpoints.ts` và các file `*.api.ts` trong `apps/web/src/features`.
- Tài liệu hướng dẫn hiện có: `docs/guides/HUONG_DAN_SU_DUNG_WEB.md`.

## Tóm tắt nhanh

DocVault web hiện là một ứng dụng quản lý tài liệu bảo mật với các nhóm tính năng chính:

- Đăng nhập SSO qua Keycloak, quản lý phiên và phân quyền theo vai trò.
- Dashboard tổng quan tài liệu, công việc vận hành, độ sẵn sàng demo và hành động nhanh.
- Quản lý vòng đời tài liệu: tạo, sửa metadata, upload file, submit, approve, reject, archive, delete, restore.
- Danh sách tài liệu có lọc nâng cao, quick views, saved views, folder tree, phân trang và thao tác hàng loạt.
- Chi tiết tài liệu có preview/download, version history, version diff, restore version, workflow timeline, ACL, comments, activity feed, share links, legal hold, approval chain, DLP findings, AI guardrails và evidence links.
- Hàng đợi phê duyệt có SLA, lọc/sắp xếp ưu tiên và review drawer.
- Trung tâm thông báo cho workflow, retention, security và document events.
- Audit log chống giả mạo với verify-chain, bộ lọc điều tra và security summary.
- Security posture: policy deny, malware, DLP, recommendations, workflow xử lý khuyến nghị, risk scoring và behavior anomalies.
- Evidence Center: tạo manifest, bundle, report, export recommendation/document evidence packets với step-up proof.
- Retention: bằng chứng vòng đời lưu trữ và chạy retention có xác nhận step-up cho admin.
- Access Review: rà soát quyền truy cập tài liệu nhạy cảm và broad ACL grants.
- Trash: khôi phục tài liệu đã xóa trong recovery window.
- Members: admin quản lý thành viên tổ chức, đổi role hoặc xóa thành viên.
- Profile, Settings, theme, command palette và demo kit.

## Bảng tính năng theo màn hình

| Màn hình | Route | Vai trò chính | Mô tả |
| --- | --- | --- | --- |
| Login | `/login` | Chưa đăng nhập | Đăng nhập SSO qua Keycloak, xử lý callback/logout, hiển thị tài khoản demo. |
| Dashboard | `/dashboard` | Tất cả vai trò đã đăng nhập | Thống kê tài liệu, demo readiness, operational widgets, work queue, recent documents và quick actions theo vai trò. |
| Documents | `/documents` | Tất cả vai trò đã đăng nhập | Danh sách tài liệu được phép xem, lọc/tìm kiếm/saved views/folder tree, phân trang, download và workflow actions. |
| My Documents | `/my-documents` | Editor, Admin | Danh sách tài liệu do người dùng hiện tại sở hữu, dùng lại bộ lọc và bảng thao tác như Documents. |
| New Document | `/documents/new` | Editor, Admin | Tạo draft mới với title, description, classification, tags và upload file ban đầu tùy chọn. |
| Document Detail | `/documents/[id]` | Người có quyền metadata | Trang trung tâm cho metadata, version, preview/download, workflow, ACL, comments, activity, share link, legal hold, approval chain và evidence. |
| Edit Document | `/documents/[id]/edit` | Admin hoặc Editor sở hữu tài liệu | Cập nhật metadata tài liệu khi có quyền chỉnh sửa. |
| Approvals | `/approvals` | Approver, Admin | Hàng đợi tài liệu `PENDING`, SLA summary, lọc SLA, sort, review drawer, approve/reject. |
| Notifications | `/notifications` | Tất cả vai trò đã đăng nhập | Work queue thông báo, lọc theo nhóm và trạng thái đọc, mark read/mark all read. |
| Audit | `/audit` | Compliance Officer, Admin | Truy vấn audit log, bộ lọc điều tra, summary security, verify audit chain, phân trang log. |
| Security | `/security` | Compliance Officer, Admin | Security posture, quick filters, alerts, recent security events, recommendations, workflow history, evidence packet, risk scoring và behavior anomalies. |
| Evidence Center | `/evidence` | Compliance Officer, Admin | Builder/presentation cho evidence bundle, export manifest/bundle/report và packets. |
| Access Review | `/access-review` | Compliance Officer, Admin | Rà soát quyền truy cập tài liệu nhạy cảm, broad/stale permissions và link sang audit evidence. |
| Retention | `/retention` | Compliance Officer, Admin | Bằng chứng retention, trạng thái due soon/overdue/archived, admin có thể chạy retention bằng step-up. |
| Trash | `/trash` | Editor, Admin | Danh sách tài liệu đã xóa mềm và restore trước khi hết recovery window. |
| Shared | `/shared?token=...` | Người có share token hợp lệ | Redeem share link token, ghi nhớ token trong session và chuyển đến chi tiết tài liệu. |
| Members | `/org/members` | Admin | Xem tổ chức, danh sách thành viên, đổi Member/Admin, xóa thành viên, tránh self-action. |
| Profile | `/profile` | Tất cả vai trò đã đăng nhập | Hồ sơ cá nhân từ Keycloak/session: tên, username, email, user id, roles. |
| Settings | `/settings` | Tất cả vai trò đã đăng nhập | Thông tin phiên, role, app config, API gateway URL, readiness score và capability list. |
| Demo Kit | `/demo-kit` | Nội bộ/presenter | Checklist bằng chứng runtime phục vụ demo/báo cáo; không còn hiển thị trong sidebar sản phẩm. |

## Chi tiết nhóm tính năng

### 1. Xác thực, phiên đăng nhập và phân quyền

- Web đăng nhập bằng Keycloak SSO thông qua `/api/auth/login`, xử lý callback bằng `/api/auth/me`, lưu session vào auth provider và chuyển về Dashboard.
- Có luồng logout, xóa cookie bootstrap `dv_user` và `kc_state`.
- Menu sidebar và command palette được lọc theo role của người dùng.
- Các vai trò chính trong UI: `viewer`, `editor`, `approver`, `compliance_officer`, `admin`.
- Quyền xem metadata, preview, download, submit, approve, reject, archive, delete, ACL, audit và evidence được gom ở helper `permissions.ts`.

### 2. Điều hướng, layout và tiện ích chung

- Sidebar hiển thị menu theo role, gồm Dashboard, Documents, My Documents, New Document, Approvals, Notifications, Evidence, Security, Access Review, Audit, Retention, Trash và Members.
- Topbar có thông tin tổ chức/người dùng, thông báo và menu tài khoản.
- Command Palette mở bằng `Ctrl+K` hoặc `Cmd+K`, tìm nhanh trang và action theo role.
- Có dark/light theme qua theme provider và `ThemeToggle`.
- Các màn hình có loading/error/empty state thống nhất.

### 3. Dashboard

- Hiển thị tổng số tài liệu và số theo trạng thái `DRAFT`, `PENDING`, `PUBLISHED`.
- Hiển thị command center vận hành: lifecycle pipeline, attention priority, work queue và metric theo quyền.
- Hiển thị operational widgets như pending approvals, DLP detected, retention due soon, unread notifications.
- Hiển thị work queue và recent documents.
- Quick actions thay đổi theo role: browse documents, create document, review approvals, audit logs.

### 4. Quản lý danh sách tài liệu

- Documents và My Documents dùng bảng tài liệu có phân trang, checkbox chọn nhiều dòng và action theo quyền.
- Bộ lọc tài liệu gồm search, status, classification, tags, owner/folder và quick views.
- Saved views có thể lưu filter hiện tại lên backend; nếu backend lỗi thì fallback lưu localStorage.
- Folder tree được dựng từ tags dạng slash path như `finance/q1`.
- Query string được đồng bộ với filter để giữ trạng thái lọc trên URL.
- Hỗ trợ bulk submit, bulk approve, bulk archive và bulk delete. Riêng Documents page có cơ chế delay 5 giây và Undo cho bulk action.

### 5. Tạo và chỉnh sửa tài liệu

- New Document cho Editor/Admin tạo draft với `title`, `description`, `classification`, `tags`.
- Có upload file ban đầu tùy chọn sau khi tạo document record.
- Form hỗ trợ nhập tags, chọn classification và lý do override classification khi cần.
- Edit Document cho người có quyền cập nhật metadata.

### 6. Chi tiết tài liệu

Trang chi tiết tài liệu gom nhiều khối nghiệp vụ:

- Header và metadata summary: trạng thái, classification, owner, tags, version hiện tại.
- Approval readiness: kiểm tra mức sẵn sàng trước khi gửi/duyệt.
- DLP findings: hiển thị kết quả phát hiện dữ liệu nhạy cảm.
- AI guardrails: hiển thị chính sách AI-ready cho classification, tagging, summarization và Q&A.
- Version History: liệt kê version, checksum, size, MIME type, người upload, thời điểm upload.
- Version preview/download: preview hoặc download theo từng version nếu policy cho phép.
- Version diff: chọn hai version để so sánh metadata.
- Restore version: khôi phục version cũ bằng cách tạo current version mới, vẫn giữ lịch sử.
- Workflow timeline: lịch sử submit/approve/reject/archive.
- Action panel: submit, approve, reject, archive, delete, upload file, preview latest version và export evidence packet.
- Approval chain: cấu hình danh sách approver theo document.
- Share links: tạo/revoke link chia sẻ với thời hạn và quyền.
- Legal hold: admin đặt hoặc gỡ legal hold để miễn auto-archive theo retention.
- Evidence links: liên kết nhanh sang audit/evidence liên quan.
- ACL: thêm rule USER/ROLE/GROUP/ALL với permission và ALLOW/DENY.
- Comments: thêm và xem bình luận.
- Activity feed: hợp nhất workflow, comments và audit events nếu có quyền audit.

### 7. Preview và download tài liệu

- Preview hỗ trợ PDF, image, text, Markdown, HTML/text-like formats và DOCX rendering.
- Preview dialog có zoom, reset zoom, close, fallback error state và thông báo định dạng không hỗ trợ preview.
- Download dùng luồng authorize trước, sau đó presign hoặc stream theo kết quả backend.
- Tài liệu `ARCHIVED` vẫn cho xem metadata/preview theo quyền đọc; download vẫn bị giới hạn theo policy download.
- Share token được đưa vào authorize preview/download khi người dùng truy cập qua shared link.
- Compliance Officer bị chặn preview/download file content theo policy frontend.

### 8. Workflow và phê duyệt

- Vòng đời chính: `DRAFT -> PENDING -> PUBLISHED -> ARCHIVED`, reject chuyển `PENDING -> DRAFT`, delete áp dụng cho draft theo quyền.
- Approvals page chỉ cho Approver/Admin.
- Hàng đợi approval lấy các document trạng thái `PENDING`.
- Có SLA summary: overdue, due soon, on time, compliance review.
- Có SLA filter và sort theo priority, due date hoặc queued time.
- Review drawer hiển thị thông tin document, assignment/SLA, workflow history và nút approve/reject kèm confirm.

### 9. Thông báo

- Notifications page lấy danh sách từ `/notify`.
- Nhóm thông báo: approvals, retention, security, documents và all.
- Lọc theo read state: all, unread, read.
- Có unread count, mark single read và mark all read.
- Mỗi notification có target action link để đi đến màn hình liên quan.
- Notification types hiện có gồm submitted, approved, rejected, archived, deleted, retention due/overdue, security recommendation overdue, malware blocked, DLP detected và audit chain invalid.

### 10. Audit

- Audit page chỉ cho Compliance Officer/Admin.
- Có kiểm tra tính toàn vẹn audit chain bằng `verify-chain`.
- Có summary cards cho denied events, malware blocked, DLP hits và download denied.
- Có quick investigations cho các nhóm sự kiện security quan trọng.
- Bộ lọc audit hỗ trợ result, action, actor, resource type/id, document id và khoảng thời gian.
- Audit table có phân trang và hiển thị actor/resource/action/result/reason.

### 11. Security posture

- Security page tổng hợp audit-chain, policy denies, malware, DLP, download/preview access events.
- Có posture panel, verify audit chain và refresh dữ liệu.
- Quick filters mở thẳng sang Audit với query tương ứng.
- Hiển thị alerts, recent security events, repeated deny actors.
- Security recommendations được sinh deterministic từ audit-chain, DLP, malware, risk scoring và behavior anomalies.
- Recommendation workflow hỗ trợ trạng thái `OPEN`, `INVESTIGATING`, `REVIEWED`, `RESOLVED`, có history và export evidence packet.
- Có document risk scoring dựa trên classification, access volume, actor spread và download grants.
- Có behavior anomalies hướng tới tín hiệu ransomware từ actor activity, denied access, document spread và destructive audit events.

### 12. Evidence Center

- Evidence Center có hai chế độ: Builder và Presentation.
- Source cards tổng hợp trạng thái audit chain, recommendations, document packets và retention records.
- Export manifest dạng JSON.
- Builder cho chọn recommendation packets và document packets để tạo evidence bundle.
- Export bundle JSON và report HTML.
- Export recommendation packet kèm audit chain, workflow history và playbook metadata.
- Export document evidence packet yêu cầu step-up proof bằng challenge phrase.
- Presentation view tạo narrative, section summary, visual timeline và danh sách packets phục vụ demo/báo cáo.

### 13. Retention

- Retention page chỉ cho Compliance Officer/Admin.
- Hiển thị tracked records, due soon, overdue và archived.
- Mỗi record có document, status, classification, retention status, published date, retain until và days remaining.
- Admin có nút Run Retention, yêu cầu step-up confirm trước khi gọi backend.
- Legal hold ở document detail có thể làm tài liệu được miễn auto-archive cho đến khi hold được release.

### 14. Access Review

- Access Review chỉ cho Compliance Officer/Admin.
- Tính posture cho permission recertification trên sensitive documents và broad ACL grants.
- Summary gồm reviewed documents, open reviews, critical reviews, stale permissions.
- Bảng review hiển thị document, severity, subject, permission, evidence và recommended action.
- Có link mở document detail và audit evidence tương ứng.

### 15. Trash và khôi phục

- Trash chỉ cho Editor/Admin.
- Hiển thị document đã xóa, classification, thời điểm xóa, số ngày còn lại trước purge.
- Cho restore nếu còn trong recovery window.

### 16. Share links

- Người có link dạng `/shared?token=...` được redeem token qua backend.
- Web lưu token theo document trong sessionStorage để dùng lại cho preview/download authorization trong phiên đó.
- Nếu token thiếu hoặc không hợp lệ, hiển thị trạng thái share link unavailable.
- Chủ sở hữu/editor hoặc admin có thể quản lý share links trong document detail.

### 17. Quản trị tổ chức

- Members page chỉ cho Admin.
- Hiển thị thông tin organization hiện tại và summary total/admin/member.
- Admin có thể promote/demote Member/Admin và remove member.
- UI chặn đổi role hoặc xóa chính mình.

### 18. Profile và Settings

- Profile hiển thị thông tin tài khoản từ Keycloak/session: display name, username, email, user id, roles và trạng thái active.
- Settings hiển thị session type, roles, app name, API gateway URL, product readiness score và capability list theo role.
- Demo Kit được giữ như route nội bộ/presenter để phục vụ advisor demo và report screenshots, không phải tab điều hướng chính.

## Phân quyền route/menu hiện tại

| Route/menu | Viewer | Editor | Approver | Compliance Officer | Admin |
| --- | --- | --- | --- | --- | --- |
| Dashboard | Có | Có | Có | Có | Có |
| Documents | Có | Có | Có | Có | Có |
| My Documents | Không | Có | Không | Không | Có |
| New Document | Không | Có | Không | Không | Có |
| Approvals | Không | Không | Có | Không | Có |
| Notifications | Có | Có | Có | Có | Có |
| Evidence | Không | Không | Không | Có | Có |
| Security | Không | Không | Không | Có | Có |
| Access Review | Không | Không | Không | Có | Có |
| Audit | Không | Không | Không | Có | Có |
| Retention | Không | Không | Không | Có | Có |
| Trash | Không | Có | Không | Không | Có |
| Members | Không | Không | Không | Không | Có |
| Profile | Có | Có | Có | Có | Có |
| Settings | Có | Có | Có | Có | Có |

## Ghi chú về trạng thái triển khai

- Danh sách trên phản ánh mã frontend và API client đang có trong repository.
- Một số tính năng phụ thuộc backend/service tương ứng phải chạy đúng, ví dụ audit-service, metadata-service, document-service, notification-service, Keycloak và gateway.
- Các hành động nhạy cảm như export evidence packet và run retention có step-up proof ở frontend; backend vẫn là nơi quyết định cuối cùng.
- File `apps/web/src/config/nav.ts` tồn tại nhưng menu đầy đủ đang nằm ở `apps/web/src/lib/constants/nav.ts`.

## Nguồn mã chính

- `apps/web/src/lib/constants/routes.ts`: danh sách route web.
- `apps/web/src/lib/constants/nav.ts`: menu theo vai trò.
- `apps/web/src/app/(auth)/login/page.tsx`: đăng nhập SSO.
- `apps/web/src/app/(app)/dashboard/page.tsx`: dashboard.
- `apps/web/src/app/(app)/documents/page.tsx`: danh sách tài liệu.
- `apps/web/src/app/(app)/my-documents/page.tsx`: tài liệu của tôi.
- `apps/web/src/app/(app)/documents/new/page.tsx`: tạo tài liệu.
- `apps/web/src/app/(app)/documents/[id]/page.tsx`: chi tiết tài liệu.
- `apps/web/src/app/(app)/documents/[id]/edit/page.tsx`: chỉnh sửa tài liệu.
- `apps/web/src/app/(app)/approvals/page.tsx`: phê duyệt.
- `apps/web/src/app/(app)/notifications/page.tsx`: thông báo.
- `apps/web/src/app/(app)/audit/page.tsx`: audit.
- `apps/web/src/app/(app)/security/page.tsx`: security posture.
- `apps/web/src/app/(app)/evidence/page.tsx`: Evidence Center.
- `apps/web/src/app/(app)/access-review/page.tsx`: Access Review.
- `apps/web/src/app/(app)/retention/page.tsx`: Retention.
- `apps/web/src/app/(app)/trash/page.tsx`: Trash.
- `apps/web/src/app/(app)/shared/page.tsx`: share link redeem.
- `apps/web/src/app/(app)/org/members/page.tsx`: quản lý thành viên.
- `apps/web/src/app/(app)/profile/page.tsx`: hồ sơ cá nhân.
- `apps/web/src/app/(app)/settings/page.tsx`: thông tin hệ thống.
- `apps/web/src/app/(app)/demo-kit/page.tsx`: demo kit.
- `apps/web/src/lib/api/endpoints.ts`: endpoint map.
- `apps/web/src/lib/auth/permissions.ts`: policy UI cho metadata, preview, download và workflow actions.
- `apps/web/src/components/documents/document-preview-dialog.tsx`: preview file.
- `apps/web/src/components/documents/document-versions-card.tsx`: version history/diff/restore.
- `apps/web/src/components/documents/document-action-panel.tsx`: action panel và export evidence packet.
- `apps/web/src/components/command-palette/command-palette.tsx`: command palette.
