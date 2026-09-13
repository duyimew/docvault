# Hướng dẫn chạy và sử dụng hệ thống DocVault

---

## Phần 1 — Chạy hệ thống

### 1.1. Yêu cầu trước khi chạy

Cài sẵn trên máy:

- **Node.js** phiên bản 18 trở lên
- **pnpm** phiên bản 9 trở lên
- **Docker Desktop** phiên bản 24 trở lên

Đảm bảo các port sau chưa bị chiếm: `3000`, `3001`, `3002`, `3003`, `3004`, `3005`, `3010`, `5432`, `8080`, `9000`, `9001`.

### 1.2. Cài đặt dependencies

Mở terminal tại thư mục gốc của dự án, chạy:

```bash
pnpm install
```

### 1.3. Khởi động hạ tầng Docker

Lệnh này khởi động PostgreSQL, MinIO (lưu trữ file) và Keycloak (quản lý đăng nhập):

```bash
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.example up -d
```

Đợi khoảng 30–60 giây cho tất cả container lên xong. Kiểm tra bằng:

```bash
docker compose -f infra/docker-compose.dev.yml ps
```

Tất cả container phải ở trạng thái **healthy** hoặc **running**.

### 1.4. Chạy database migration (chỉ lần đầu)

```bash
pnpm --filter metadata-service prisma:deploy
pnpm --filter audit-service prisma:deploy
```

### 1.4b. Tạo dữ liệu mẫu (lần đầu)

Bước này tạo sẵn 4 tài liệu mẫu để có thể dùng ngay mà không cần tạo thủ công:

```bash
pnpm --filter metadata-service db:seed
```

Các tài liệu được tạo:

| Tài liệu | Phân loại | Trạng thái |
|---|---|---|
| Q1 Financial Report 2026 | CONFIDENTIAL | PUBLISHED |
| Employee Handbook v3 | INTERNAL | PUBLISHED |
| Product Roadmap 2026 | CONFIDENTIAL | DRAFT |
| Meeting Notes — All Hands Feb | PUBLIC | PUBLISHED |

### 1.5. Khởi động backend

**Cách nhanh** — một lệnh duy nhất (chạy tất cả service theo đúng thứ tự):

```bash
pnpm start:sequential
```

Lần đầu chạy nên kèm migration:

```bash
RUN_PRISMA_DEPLOY=1 pnpm start:sequential
```

**Cách thủ công** — mỗi service mở trong một terminal riêng:

```bash
pnpm --filter metadata-service start:dev       # Terminal 1 — port 3001
pnpm --filter document-service start:dev       # Terminal 2 — port 3002
pnpm --filter workflow-service start:dev       # Terminal 3 — port 3003
pnpm --filter audit-service start:dev          # Terminal 4 — port 3004
pnpm --filter notification-service start:dev   # Terminal 5 — port 3005
pnpm --filter gateway start:dev                # Terminal 6 — port 3000 (chạy SAU CÙNG)
```

> ⚠️ **Gateway phải chạy sau cùng**, sau khi tất cả service khác đã sẵn sàng.

**Trên Windows**, có thể dùng PowerShell script thay thế:

```powershell
.\start-all.ps1          # Khởi động tất cả
.\start-all.ps1 -StopAll # Dừng tất cả
```

### 1.6. Khởi động frontend

```bash
pnpm --filter web dev -- --port 3010
```

### 1.7. Mở ứng dụng

Mở trình duyệt, truy cập: **http://localhost:3010**

---

## Phần 2 — Tài khoản demo

Hệ thống đã có sẵn 5 tài khoản mẫu với các vai trò khác nhau. Mật khẩu chung cho tất cả: **`Passw0rd!`**

| Tài khoản | Vai trò | Họ làm được gì |
|---|---|---|
| `viewer1` | Viewer | Xem danh sách/xem trước tài liệu đã xuất bản hoặc lưu trữ nếu policy cho phép; tải xuống tài liệu đã xuất bản |
| `editor1` | Editor | Tạo tài liệu, tải file lên, gửi duyệt, lưu trữ |
| `approver1` | Approver | Phê duyệt hoặc từ chối tài liệu |
| `co1` | Compliance Officer | Xem audit log, kiểm tra tuân thủ (không được tải file) |
| `admin1` | Admin | Toàn quyền |

---

## Phần 3 — Đăng nhập

1. Truy cập **http://localhost:3010** → trang Login hiện ra.
2. Nhấn nút **"Sign in with SSO"** → trình duyệt chuyển sang trang Keycloak.
3. Nhập tài khoản demo (ví dụ: `editor1` / `Passw0rd!`) → đăng nhập.
4. Hệ thống tự chuyển về trang **Dashboard**.

> Trên trang đăng nhập cũng hiển thị sẵn danh sách các tài khoản demo để tiện sử dụng.

---

## Phần 4 — Sử dụng hệ thống (theo vai trò)

### 4.1. Dashboard — Tổng quan

Sau khi đăng nhập, bạn được đưa đến trang **Dashboard** gồm:

- **Thẻ thống kê**: Tổng số tài liệu, số DRAFT, PENDING, PUBLISHED.
- **Tài liệu gần đây**: 5 tài liệu cập nhật gần nhất, nhấn vào để xem chi tiết.
- **Quick Actions**: Các hành động nhanh tùy theo vai trò:
  - Editor/Admin: nút "Create Document"
  - Approver/Admin: nút "Review Approvals" (kèm badge số lượng chờ duyệt)
  - Compliance Officer: nút "Audit Logs"

---

### 4.2. Editor — Tạo và quản lý tài liệu

#### Bước 1: Tạo tài liệu mới

1. Vào menu **Documents** ở thanh bên trái, hoặc nhấn **"New Document"** trên Dashboard.
2. Điền thông tin:
   - **Title** (bắt buộc): Tên tài liệu
   - **Description** (tùy chọn): Mô tả ngắn
   - **Classification**: Chọn mức phân loại bảo mật (`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `SECRET`)
   - **Tags** (tùy chọn): Gắn nhãn phân loại
3. Nhấn **"Create"** → tài liệu được tạo ở trạng thái **DRAFT**.

#### Bước 2: Tải file lên

1. Sau khi tạo, hệ thống chuyển đến trang chi tiết tài liệu.
2. Trong phần **Versions**, nhấn nút **Upload** để chọn file từ máy tính.
3. File được lưu vào MinIO, hệ thống tự tính checksum SHA-256 và tạo bản ghi version.

#### Bước 3: Gửi duyệt

1. Trên trang chi tiết tài liệu, ở panel bên phải tìm nút **"Submit"**.
2. Xác nhận → trạng thái chuyển từ **DRAFT → PENDING**.
3. Người Approver sẽ nhận được thông báo.

#### Bước 4: Lưu trữ (Archive)

- Khi tài liệu đã ở trạng thái **PUBLISHED**, Editor (chủ sở hữu) có thể nhấn **"Archive"**.
- Tài liệu chuyển sang **ARCHIVED** → chỉ cho xem trước, không cho tải xuống nữa.

#### Xem tài liệu của tôi

- Vào menu **My Documents** ở thanh bên trái → chỉ hiển thị tài liệu do mình tạo.
- Hỗ trợ bộ lọc, tìm kiếm và thao tác hàng loạt.

---

### 4.3. Approver — Phê duyệt tài liệu

#### Xem danh sách chờ duyệt

1. Vào menu **Approvals** ở thanh bên trái.
2. Trang hiển thị bảng các tài liệu đang ở trạng thái **PENDING**, kèm badge số lượng.

#### Phê duyệt

1. Nhấn **"Review"** ở dòng tài liệu cần duyệt → mở drawer chi tiết.
2. Xem thông tin tài liệu, file đính kèm.
3. Nhấn **"Approve"** → trạng thái chuyển sang **PUBLISHED**, tài liệu sẵn sàng để tải xuống.

#### Từ chối

1. Nhấn **"Reject"** trong drawer.
2. Nhập lý do từ chối (tùy chọn nhưng khuyến nghị có).
3. Xác nhận → trạng thái trở về **DRAFT**, Editor có thể sửa và gửi lại.

---

### 4.4. Viewer — Xem và tải tài liệu

#### Xem danh sách

- Vào menu **Documents** → bảng danh sách tài liệu.
- Sử dụng thanh **tìm kiếm** (search) để tìm theo tên, tag.
- Sử dụng **bộ lọc** (filter) theo trạng thái hoặc mức phân loại.

#### Xem trước tài liệu (Preview)

1. Nhấn vào tên tài liệu để mở trang chi tiết.
2. Trong phần **Versions**, nhấn nút **Preview** ở phiên bản muốn xem.
3. Hệ thống hiển thị PDF trực tiếp trên trang (render bằng pdf.js, không có nút download, không right-click save).

> Preview chỉ khả dụng cho tài liệu ở trạng thái **PUBLISHED** hoặc **ARCHIVED**.

#### Tải xuống file (Download)

1. Trên trang chi tiết, nhấn nút **"Download"** trong panel hành động.
2. Hệ thống kiểm tra quyền, sau đó tải file về máy.

> **Lưu ý**: File PDF với phân loại **CONFIDENTIAL** hoặc **SECRET** sẽ tự động được đóng **watermark** (gồm tên người tải, thời gian, mức phân loại) khi tải xuống.

---

### 4.5. Compliance Officer — Kiểm tra tuân thủ

#### Xem audit log

1. Đăng nhập bằng tài khoản `co1`.
2. Vào menu **Audit** ở thanh bên trái.
3. Trang hiển thị bảng nhật ký kiểm toán ghi lại mọi hành động trong hệ thống.

#### Bộ lọc audit log

Sử dụng các bộ lọc phía trên bảng để thu hẹp kết quả:

| Bộ lọc | Ý nghĩa |
|---|---|
| **actorId** | Lọc theo người thực hiện |
| **action** | Lọc theo hành động (ví dụ: `DOCUMENT_SUBMIT`, `DOCUMENT_APPROVE`) |
| **result** | Lọc theo kết quả: `SUCCESS` hoặc `DENY` |
| **from / to** | Lọc theo khoảng thời gian |

#### Quy tắc đặc biệt cho Compliance Officer

- **Được xem** metadata (chi tiết) của tất cả tài liệu PUBLISHED và ARCHIVED.
- **Không được xem trước** (preview) nội dung file. Compliance Officer chỉ xem metadata/audit/retention evidence.
- **Không được tải xuống** bất kỳ file nào, dù ACL cho phép. Đây là quy tắc cứng trong hệ thống.

---

### 4.6. Admin — Quản trị toàn diện

Admin có **toàn quyền** trên hệ thống — có thể thực hiện tất cả thao tác của các vai trò khác:
- Tạo, sửa, upload, submit, approve, reject, archive tài liệu
- Quản lý ACL (quyền truy cập từng tài liệu)
- Xem audit log
- Tải xuống file

---

## Phần 5 — Các tính năng chung

### 5.1. Tìm kiếm tài liệu

- Nhập từ khóa vào ô **Search** trên trang Documents.
- Hệ thống tìm kiếm theo tiêu đề, mô tả và tags (xử lý phía server, hiệu quả với dữ liệu lớn).

### 5.2. Bộ lọc

Trên trang Documents, sử dụng các dropdown bộ lọc:

| Bộ lọc | Tùy chọn |
|---|---|
| **Status** | DRAFT, PENDING, PUBLISHED, ARCHIVED |
| **Classification** | PUBLIC, INTERNAL, CONFIDENTIAL, SECRET |
| **Sort** | Sắp xếp theo tên, ngày tạo, ngày cập nhật... |

### 5.3. Thao tác hàng loạt (Bulk Actions)

1. Trên trang Documents, tích chọn nhiều tài liệu bằng checkbox.
2. Thanh bulk action hiện ra với các nút:
   - **Bulk Submit**: Gửi duyệt nhiều DRAFT cùng lúc
   - **Bulk Approve**: Phê duyệt nhiều PENDING (chỉ Approver/Admin)
   - **Bulk Archive**: Lưu trữ nhiều PUBLISHED
3. Kết quả hiển thị qua thông báo toast: `"Bulk Submit: 3 thành công, 1 thất bại"`.

### 5.4. Bình luận (Comments)

1. Mở trang chi tiết tài liệu.
2. Ở cột bên phải, phần **Comments** hiển thị các bình luận đã có.
3. Nhập nội dung vào ô text và gửi → bình luận được thêm.
4. Tất cả vai trò có quyền xem tài liệu đều có thể bình luận.

### 5.5. Quản lý ACL (Quyền truy cập)

ACL cho phép kiểm soát quyền truy cập chi tiết trên từng tài liệu. Chỉ **Editor (chủ sở hữu)** và **Admin** mới quản lý được ACL.

1. Mở trang chi tiết tài liệu.
2. Ở cột phải, phần **Access Control** hiển thị các rule hiện tại.
3. Nhấn thêm rule mới:
   - **Subject**: Chọn USER / ROLE / GROUP / ALL
     - Với GROUP, nhập tên group Keycloak đã chuẩn hóa, ví dụ `/finance-team` trong token sẽ dùng `finance-team` trong ACL.
   - **Permission**: READ / DOWNLOAD / WRITE / APPROVE
   - **Effect**: ALLOW hoặc DENY

> **Lưu ý**: Nếu cùng một tài liệu có cả ALLOW và DENY, **DENY luôn ưu tiên hơn**.

### 5.6. Giao diện tối (Dark Mode)

Hệ thống hỗ trợ giao diện tối. Vào **Settings** (menu thanh bên) để chuyển đổi.

---

## Phần 6 — Vòng đời tài liệu tóm tắt

```
📝 DRAFT  ──(Editor gửi duyệt)──►  ⏳ PENDING  ──(Approver phê duyệt)──►  ✅ PUBLISHED  ──(Editor lưu trữ)──►  📁 ARCHIVED
                                         │
                                         └──(Approver từ chối)──► 📝 DRAFT (sửa lại)
```

| Trạng thái | Ý nghĩa | Ai thao tác tiếp |
|---|---|---|
| **DRAFT** | Mới tạo hoặc bị từ chối | Editor: sửa, upload, gửi duyệt |
| **PENDING** | Đang chờ duyệt | Approver: phê duyệt hoặc từ chối |
| **PUBLISHED** | Đã duyệt, cho phép tải | Viewer/Editor: xem, tải; Editor: lưu trữ |
| **ARCHIVED** | Đã lưu trữ | Cho xem metadata/xem trước theo quyền đọc, không tải |

---

## Phần 7 — Xử lý sự cố thường gặp

### Không đăng nhập được

- Đảm bảo Keycloak đang chạy: mở `http://localhost:8080` xem có hiện trang admin không.
- Kiểm tra mật khẩu: tất cả tài khoản demo dùng `Passw0rd!` (chữ P viết hoa, số 0 thay chữ o, kết thúc dấu !).

### Trang web báo lỗi khi tải dữ liệu

- Kiểm tra Gateway đang chạy: mở `http://localhost:3000/health` xem có trả về OK không.
- Kiểm tra file `.env.local` trong `apps/web/` có dòng: `NEXT_PUBLIC_API_BASE_URL=http://localhost:3000/api`
- Đảm bảo frontend chạy trên port **3010**, không trùng với Gateway (3000).

### Service không khởi động được (port bị chiếm)

```bash
# Trên Windows, kiểm tra port đang bị chiếm bởi ai
netstat -ano | findstr :3000
```

Dừng process đang chiếm hoặc dùng lệnh `.\start-all.ps1 -StopAll` để dừng tất cả.

### Lỗi prisma:deploy

- Chắc chắn container PostgreSQL đã healthy.
- Nếu bị lỗi schema cũ, xóa Docker volume rồi tạo lại:

```bash
docker compose -f infra/docker-compose.dev.yml down -v
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.example up -d
# Đợi 30–60s rồi chạy lại migration
pnpm --filter metadata-service prisma:deploy
pnpm --filter audit-service prisma:deploy
```

---

## Phần 8 — Kiểm tra hệ thống (tùy chọn)

### Chạy E2E test tự động

Sau khi tất cả service đang chạy:

```bash
pnpm test:e2e
```

Script tự động kiểm tra các luồng chính: xác thực, tạo tài liệu, upload, submit, approve, download, audit log, GROUP ACL, confidential stream-only posture, malware block, DLP escalation, retention evidence và audit security summary.

### Mở Swagger UI (tài liệu API)

| Service | URL |
|---|---|
| Gateway | http://localhost:3000/docs |
| metadata-service | http://localhost:3001/docs |
| document-service | http://localhost:3002/docs |
| workflow-service | http://localhost:3003/docs |
| audit-service | http://localhost:3004/docs |

### Xem dữ liệu trực tiếp

- **Prisma Studio** (xem database PostgreSQL):
  ```bash
  pnpm --filter metadata-service prisma:studio
  ```
  Mở: http://localhost:5555

- **MinIO Console** (xem file đã upload):
  Mở: http://localhost:9001 — đăng nhập bằng `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` trong `infra/.env`.

- **Keycloak Admin** (quản lý tài khoản):
  Mở: http://localhost:8080 — đăng nhập bằng `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` trong `infra/.env`.
