# Hướng dẫn sử dụng hệ thống web DocVault

Tài liệu này hướng dẫn người dùng cuối thao tác trên giao diện web DocVault: đăng nhập, điều hướng menu, và sử dụng từng tính năng theo vai trò. Nếu bạn cần hướng dẫn cài đặt và khởi động hệ thống, xem `docs/guides/HUONG_DAN_SU_DUNG.md`.

DocVault là hệ thống quản lý tài liệu doanh nghiệp, hỗ trợ vòng đời đầy đủ: tạo → tải lên → duyệt → xuất bản → lưu trữ, kèm phân quyền theo vai trò (RBAC) và nhật ký kiểm toán chống giả mạo.

---

## 1. Truy cập và đăng nhập

1. Mở trình duyệt, truy cập **http://localhost:3010**.
2. Trang **Login** hiện ra, nhấn **"Sign in with SSO"**.

![Trang đăng nhập DocVault](images/web/login.png)

3. Trình duyệt chuyển sang trang đăng nhập Keycloak. Nhập tài khoản và mật khẩu.
4. Sau khi đăng nhập thành công, hệ thống tự chuyển về **Dashboard**.

Trang Login có hiển thị sẵn danh sách tài khoản demo để bạn tiện thử nghiệm.

### Tài khoản demo

Mật khẩu chung cho tất cả: **`Passw0rd!`**

| Tài khoản | Vai trò | Mô tả nhanh |
|---|---|---|
| `viewer1` | Viewer | Xem danh sách, xem trước, tải tài liệu đã xuất bản |
| `editor1` | Editor | Tạo tài liệu, tải file lên, gửi duyệt, lưu trữ |
| `approver1` | Approver | Phê duyệt hoặc từ chối tài liệu |
| `co1` | Compliance Officer | Xem audit log, kiểm tra tuân thủ (không tải được file) |
| `admin1` | Admin | Toàn quyền |

### Đăng xuất

Nhấn vào khu vực tài khoản (góc trên bên phải hoặc cuối thanh bên), chọn **Logout** để kết thúc phiên.

---

## 2. Bố cục giao diện

Sau khi đăng nhập, giao diện gồm ba khu vực chính:

- **Thanh bên trái (Sidebar)**: menu điều hướng. Các mục hiển thị tùy theo vai trò của bạn.
- **Thanh trên cùng (Topbar)**: thanh tìm kiếm / lệnh nhanh, chuông thông báo, và thông tin tài khoản.
- **Vùng nội dung chính**: hiển thị trang đang mở.

Trên màn hình nhỏ (mobile), nhấn nút menu ở góc trái trên để mở Sidebar.

### Command Palette (tìm nhanh)

Nhấn tổ hợp phím tắt (biểu tượng tìm kiếm trên Topbar) để mở **Command Palette** — cho phép nhảy nhanh đến tài liệu hoặc trang chức năng mà không cần đi qua menu.

---

## 3. Menu điều hướng theo vai trò

Mỗi vai trò chỉ thấy các mục phù hợp. Bảng dưới đây liệt kê toàn bộ mục menu và vai trò được thấy:

| Mục menu | Chức năng | Vai trò thấy được |
|---|---|---|
| **Dashboard** | Tổng quan, thống kê, hành động nhanh | Tất cả |
| **Documents** | Danh sách toàn bộ tài liệu được phép xem | Tất cả |
| **My Documents** | Tài liệu do bạn sở hữu | Editor, Admin |
| **New Document** | Tạo tài liệu mới | Editor, Admin |
| **Approvals** | Hàng đợi tài liệu chờ duyệt (kèm badge số lượng) | Approver, Admin |
| **Notifications** | Thông báo công việc cần xử lý | Tất cả |
| **Evidence** | Trung tâm bằng chứng tuân thủ | Compliance Officer, Admin |
| **Security** | Tình trạng an ninh: policy deny, malware, DLP | Compliance Officer, Admin |
| **Access Review** | Tái chứng nhận quyền truy cập tài liệu nhạy cảm | Compliance Officer, Admin |
| **Audit** | Nhật ký kiểm toán chống giả mạo | Compliance Officer, Admin |
| **Retention** | Vòng đời lưu trữ và tự động archive | Compliance Officer, Admin |
| **Trash** | Tài liệu đã xóa, có thể khôi phục | Editor, Admin |
| **Members** | Quản lý thành viên tổ chức | Admin |


---

## 4. Dashboard — Tổng quan

![Dashboard tổng quan](images/web/dashboard.png)

Trang đầu tiên sau khi đăng nhập, gồm:

- **Thẻ thống kê**: tổng số tài liệu và số lượng theo trạng thái DRAFT, PENDING, PUBLISHED.
- **Tài liệu gần đây**: các tài liệu cập nhật gần nhất, nhấn để mở chi tiết.
- **Quick Actions**: nút hành động nhanh tùy vai trò:
  - Editor / Admin: **Create Document**
  - Approver / Admin: **Review Approvals** (kèm badge số lượng chờ duyệt)
  - Compliance Officer: **Audit Logs**

---

## 5. Làm việc với tài liệu (Editor / Admin)

### 5.1. Tạo tài liệu mới

![Trang tạo tài liệu mới](images/web/new-document.png)

1. Vào **New Document** trên menu, hoặc nhấn **Create Document** ở Dashboard.
2. Điền thông tin:
   - **Title** (bắt buộc): tên tài liệu.
   - **Description** (tùy chọn): mô tả ngắn.
   - **Classification**: mức phân loại bảo mật — `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `SECRET`.
   - **Tags** (tùy chọn): nhãn phân loại.
3. Nhấn **Create** → tài liệu được tạo ở trạng thái **DRAFT** và mở trang chi tiết.

### 5.2. Tải file lên

1. Trên trang chi tiết tài liệu, tìm phần **Versions**.
2. Nhấn **Upload** và chọn file từ máy.
3. File được lưu vào kho lưu trữ MinIO, hệ thống tự tính checksum SHA-256 và tạo một bản ghi version.

> File tải lên được quét malware trước khi lưu. Nếu phát hiện nội dung nhạy cảm, hệ thống có thể tự nâng mức phân loại lên `CONFIDENTIAL` (DLP).

### 5.3. Gửi duyệt

1. Trên trang chi tiết, ở panel hành động bên phải nhấn **Submit**.
2. Xác nhận → trạng thái chuyển **DRAFT → PENDING**.
3. Approver sẽ nhận được thông báo trong hàng đợi Approvals.

### 5.4. Lưu trữ (Archive)

- Khi tài liệu đã **PUBLISHED**, Editor (chủ sở hữu) hoặc Admin có thể nhấn **Archive** để chuyển sang **ARCHIVED**.
- Tài liệu ARCHIVED chỉ cho xem trước, không cho phép tải xuống.

### 5.5. Chỉnh sửa tài liệu

- Mở chi tiết tài liệu rồi vào **Edit** để cập nhật Title, Description, Classification, Tags (áp dụng khi tài liệu còn ở trạng thái cho phép chỉnh sửa).

### 5.6. My Documents

Mục **My Documents** liệt kê riêng các tài liệu bạn sở hữu, giúp theo dõi nhanh tiến độ và trạng thái của chúng.

### 5.7. Trash — Khôi phục tài liệu đã xóa

- Tài liệu bị xóa được chuyển vào **Trash** và có thể khôi phục trong khoảng thời gian cho phép.
- Mở **Trash**, chọn tài liệu và nhấn khôi phục trước khi hết hạn recovery window.


---

## 6. Phê duyệt tài liệu (Approver / Admin)

### 6.1. Hàng đợi Approvals

![Hàng đợi phê duyệt](images/web/approvals.png)

1. Vào mục **Approvals** trên menu. Badge cạnh menu hiển thị số tài liệu đang chờ.
2. Danh sách liệt kê các tài liệu ở trạng thái **PENDING**.
3. Nhấn vào một tài liệu để xem chi tiết và nội dung trước khi quyết định.

### 6.2. Phê duyệt hoặc từ chối

- **Approve**: trạng thái chuyển **PENDING → PUBLISHED**, tài liệu sẵn sàng cho người xem tải về.
- **Reject**: trạng thái chuyển **PENDING → DRAFT** để Editor chỉnh sửa lại.

> Nếu phê duyệt một tài liệu đã được xử lý, hệ thống trả về xung đột (409) để tránh thao tác trùng.

Approver có quyền xem trước (preview) tài liệu ở mọi mức phân loại để phục vụ thẩm định.

---

## 7. Xem và tải tài liệu (Viewer và các vai trò khác)

### 7.1. Danh sách tài liệu

![Danh sách tài liệu](images/web/documents.png)

- Vào **Documents** để xem danh sách tài liệu bạn được phép thấy.
- Danh sách hiển thị tùy theo vai trò và mức phân loại (xem mục 11).
- Bạn luôn thấy tài liệu mình **sở hữu** hoặc có **ACL** cấp quyền, bất kể mức phân loại.

### 7.2. Xem trước (Preview)

- Mở chi tiết tài liệu rồi nhấn **Preview** để xem nội dung ngay trong trình duyệt.
- Quyền preview phụ thuộc vai trò và mức phân loại tài liệu.

### 7.3. Tải xuống (Download)

- Với tài liệu **PUBLISHED**, nhấn **Download** trên trang chi tiết.
- Hệ thống cấp link tải an toàn (presigned). Tài liệu nhạy cảm có thể chỉ cho phép tải dạng luồng (stream-only), không lộ URL trực tiếp.
- **Compliance Officer không tải được file** trong mọi trường hợp — quy tắc này được enforce ở tầng metadata-service.

### 7.4. Bình luận (Comments)

1. Mở chi tiết tài liệu, tìm phần **Comments** ở cột phải.
2. Nhập nội dung và gửi.
3. Mọi vai trò có quyền xem tài liệu đều có thể bình luận.

### 7.5. Thao tác hàng loạt (Bulk actions)

1. Trong danh sách tài liệu, tick chọn nhiều tài liệu bằng checkbox.
2. Thanh bulk action hiện ra với các nút khả dụng theo vai trò:
   - **Bulk Submit**: gửi duyệt nhiều DRAFT cùng lúc.
   - **Bulk Approve**: duyệt nhiều PENDING (Approver / Admin).
   - **Bulk Archive**: lưu trữ nhiều PUBLISHED.
3. Kết quả hiển thị qua thông báo toast, ví dụ: "Bulk Submit: 3 thành công, 1 thất bại".

### 7.6. Quản lý ACL (Quyền truy cập chi tiết)

Chỉ **Editor (chủ sở hữu)** và **Admin** quản lý được ACL.

1. Mở chi tiết tài liệu, vào phần **Access Control**.
2. Thêm rule mới:
   - **Subject**: USER / ROLE / GROUP / ALL. Với GROUP, nhập tên group Keycloak đã chuẩn hóa (ví dụ `/finance-team` trong token dùng `finance-team` trong ACL).
   - **Permission**: READ / DOWNLOAD / WRITE / APPROVE.
   - **Effect**: ALLOW hoặc DENY.

> Khi một tài liệu có cả ALLOW và DENY, **DENY luôn ưu tiên**.


---

## 8. Tính năng tuân thủ và an ninh (Compliance Officer / Admin)

### 8.1. Audit — Nhật ký kiểm toán

![Nhật ký kiểm toán](images/web/audit.png)

- Vào **Audit** để xem toàn bộ sự kiện hệ thống (tạo, upload, submit, approve, download...).
- Nhật ký dùng cơ chế hash-chain chống giả mạo; có thể kiểm tra tính toàn vẹn của chuỗi (verify-chain).
- Viewer / Editor / Approver **không** xem được Audit (trả về 403).

### 8.2. Security — Tình trạng an ninh

![Tình trạng an ninh](images/web/security.png)

- Vào **Security** để xem posture vận hành: số lần bị chặn theo policy (policy deny), file bị chặn do malware, sự kiện DLP, và bằng chứng audit-chain.

### 8.3. Evidence — Trung tâm bằng chứng

![Trung tâm bằng chứng](images/web/evidence.png)

- Vào **Evidence** để xem workspace bằng chứng tuân thủ: chuỗi audit, khuyến nghị, gói tài liệu (document packets), và bản ghi retention.

### 8.4. Access Review — Tái chứng nhận quyền

- Vào **Access Review** để rà soát và tái chứng nhận quyền truy cập với các tài liệu nhạy cảm và các ACL cấp rộng.

### 8.5. Retention — Vòng đời lưu trữ

![Vòng đời lưu trữ](images/web/retention.png)

- Vào **Retention** để xem trạng thái vòng đời và tự động archive của tài liệu đã xuất bản.

### 8.6. Demo Kit nội bộ

- Demo Kit không còn là mục sidebar của sản phẩm. Route `/demo-kit` chỉ giữ cho presenter/nội bộ khi cần checklist bằng chứng runtime và chụp ảnh báo cáo.

> Compliance Officer thấy metadata của mọi tài liệu PUBLISHED và ARCHIVED để phục vụ kiểm toán, nhưng chỉ preview được tài liệu PUBLIC và không bao giờ tải được file.

---

## 9. Quản trị tổ chức (Admin)

### Members

- Vào **Members** để xem danh sách thành viên trong tổ chức và vai trò của họ.
- Tài khoản và vai trò được quản lý qua Keycloak; trang này phản ánh thông tin đó.

---

## 10. Thông báo, hồ sơ và cài đặt

![Trung tâm thông báo](images/web/notifications.png)

- **Notifications**: hàng đợi công việc cần xử lý (duyệt, retention, security, sự kiện tài liệu). Chuông trên Topbar hiển thị số chưa đọc; có thể lọc theo nhóm và trạng thái đã/chưa đọc.
- **Profile**: thông tin tài khoản cá nhân (do Keycloak quản lý).
- **Settings**: thông tin phiên đăng nhập và cấu hình môi trường; cũng là nơi chuyển đổi giao diện sáng/tối (Dark Mode).

---

## 11. Phân quyền theo phân loại tài liệu

### Khả năng thấy trong danh sách

| Phân loại | viewer | editor | approver | CO | admin |
|---|:--:|:--:|:--:|:--:|:--:|
| `PUBLIC` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `INTERNAL` | ❌ | ✅ | ✅ | ✅ | ✅ |
| `CONFIDENTIAL` | ❌ | ❌ | ✅ | ✅ | ✅ |
| `SECRET` | ❌ | ❌ | ✅ | ✅ | ✅ |

### Khả năng xem trước (Preview)

| Phân loại | viewer | editor | approver | CO | admin |
|---|:--:|:--:|:--:|:--:|:--:|
| `PUBLIC` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `INTERNAL` | ✅ | ✅ | ✅ | ❌ | ✅ |
| `CONFIDENTIAL` | ❌ | ✅¹ | ✅ | ❌ | ✅ |
| `SECRET` | ❌ | ❌ | ✅ | ❌ | ✅ |

> ¹ Cần có ACL rõ ràng hoặc là chủ sở hữu.
> Ngoài bảng trên, bạn luôn thấy/preview được tài liệu mình sở hữu hoặc có ACL cấp quyền.

---

## 12. Vòng đời tài liệu

```
📝 DRAFT ──(Editor gửi duyệt)──► ⏳ PENDING ──(Approver duyệt)──► ✅ PUBLISHED ──(Editor lưu trữ)──► 📁 ARCHIVED
                                      │
                                      └──(Approver từ chối)──► 📝 DRAFT (sửa lại)
```

| Trạng thái | Ý nghĩa | Ai thao tác tiếp |
|---|---|---|
| **DRAFT** | Mới tạo hoặc bị từ chối | Editor: sửa, upload, gửi duyệt |
| **PENDING** | Đang chờ duyệt | Approver: duyệt hoặc từ chối |
| **PUBLISHED** | Đã duyệt, cho tải | Viewer/Editor: xem, tải; Editor: lưu trữ |
| **ARCHIVED** | Đã lưu trữ | Chỉ xem trước, không tải |

---

## 13. Xử lý sự cố thường gặp

- **Không đăng nhập được**: kiểm tra Keycloak đang chạy tại `http://localhost:8080`; mật khẩu demo là `Passw0rd!` (P hoa, số 0, kết thúc dấu !).
- **Trang báo lỗi khi tải dữ liệu**: kiểm tra Gateway tại `http://localhost:3000/health`; đảm bảo `apps/web/.env.local` có `NEXT_PUBLIC_API_BASE_URL=http://localhost:3000/api`; frontend chạy ở port 3010.
- **Không thấy mục menu mong đợi**: menu hiển thị theo vai trò; đăng nhập bằng tài khoản có vai trò phù hợp (xem mục 3).
- **Không tải được file dù đã PUBLISHED**: nếu bạn là Compliance Officer, đây là hành vi đúng — CO không bao giờ tải được file.

Chi tiết cách cài đặt, khởi động và kiểm thử hệ thống xem `docs/guides/HUONG_DAN_SU_DUNG.md`.
