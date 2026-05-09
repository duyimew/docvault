# Kế hoạch đồ án DocVault cập nhật theo tiến độ hiện tại

## 1. Thông tin đề tài

**Tên đề tài tiếng Việt:** Thiết kế và triển khai hệ thống quản lý tài liệu bảo mật DocVault theo kiến trúc Microservices và quy trình DevSecOps tích hợp GitOps.

**Tên đề tài tiếng Anh:** Design and Implementation of Secure Document Management System (DocVault) using Microservices Architecture and DevSecOps Pipeline integrated with GitOps.

**Nhóm thực hiện:**

- Huỳnh Lê Đại Thắng - 23521422
- Nguyễn Trường Duy - 23520380

**Mục tiêu tổng quát:**

Xây dựng hệ thống quản lý và phê duyệt tài liệu DocVault theo kiến trúc microservices, triển khai trên Kubernetes, tích hợp pipeline DevSecOps có các bước kiểm thử, quét bảo mật, build service, push image, cập nhật manifest GitOps và triển khai tự động bằng ArgoCD.

---

## 2. Phạm vi hệ thống DocVault

### 2.1. Chức năng chính

Hệ thống DocVault tập trung vào các chức năng chính sau:

- Đăng nhập và phân quyền người dùng theo vai trò.
- Quản lý tài liệu: tạo metadata, upload, download, versioning.
- Quy trình phê duyệt tài liệu: Draft -> Pending -> Approved/Published.
- Kiểm soát truy cập RBAC.
- Audit trail: ghi nhận các hành động quan trọng như upload, download, submit, approve, reject, allow/deny.
- Notification: thông báo khi có sự kiện workflow hoặc sự kiện bảo mật.

### 2.2. Các service chính

| Thành phần | Vai trò |
|---|---|
| API Gateway | Điểm vào hệ thống, routing request, xác thực JWT, kiểm soát truy cập cơ bản |
| IAM / Keycloak | Quản lý user, role, token OIDC/OAuth2 |
| Metadata Service | Quản lý metadata, trạng thái tài liệu, ACL/policy |
| Document Service | Xử lý upload/download file, lưu trữ qua MinIO |
| Workflow Service | Điều phối trạng thái phê duyệt tài liệu |
| Audit Service | Ghi và truy vấn audit log |
| Notification Service | Gửi thông báo workflow/security event |
| Web Frontend | Giao diện người dùng |

---

## 3. Công nghệ sử dụng

| Nhóm công nghệ | Công cụ / nền tảng | Mục đích |
|---|---|---|
| Frontend | ReactJS / Next.js | Giao diện người dùng |
| Backend | Node.js / NestJS | Xây dựng microservices |
| IAM | Keycloak | Đăng nhập, cấp token, quản lý role |
| Database | PostgreSQL / MongoDB | Lưu metadata và audit log |
| Object Storage | MinIO | Lưu trữ file tài liệu |
| Containerization | Docker | Đóng gói ứng dụng |
| Orchestration | Kubernetes | Triển khai và vận hành service |
| CI | Jenkins | Điều phối pipeline CI/CD |
| GitOps CD | ArgoCD | Đồng bộ manifest từ Git xuống Kubernetes |
| IaC | Terraform | Mô tả và kiểm tra hạ tầng |
| SAST | SonarQube | Quét chất lượng mã nguồn và lỗi bảo mật |
| SCA | OWASP Dependency Check | Quét lỗ hổng thư viện phụ thuộc |
| IaC Scan | Checkov | Quét lỗi cấu hình hạ tầng |
| Container / FS Scan | Trivy | Quét filesystem/image |
| DAST | OWASP ZAP | Kiểm thử bảo mật động sau deploy |
| Observability | Prometheus, Grafana, Loki | Thu thập metrics, dashboard và log |

---

## 4. Kế hoạch triển khai tổng thể

### Giai đoạn 1: Phân tích và thiết kế hệ thống

**Mục tiêu:**

- Xác định kiến trúc tổng thể của DocVault.
- Thiết kế ranh giới trách nhiệm giữa các microservices.
- Xác định luồng nghiệp vụ chính.
- Xây dựng API contract hoặc OpenAPI cho các service quan trọng.

**Kết quả cần có:**

- Sơ đồ kiến trúc hệ thống.
- Danh sách service và trách nhiệm từng service.
- Luồng nghiệp vụ upload -> submit -> approve -> publish -> download.
- Mô hình RBAC.
- Thiết kế audit trail.

**Trạng thái hiện tại:** Đã thực hiện ở mức nền tảng để phục vụ xây dựng service và pipeline.

---

### Giai đoạn 2: Xây dựng ứng dụng DocVault MVP

**Mục tiêu:**

Xây dựng các service cốt lõi để hệ thống có thể chạy được luồng nghiệp vụ end-to-end.

**Nội dung thực hiện:**

- Xây dựng Gateway.
- Tích hợp IAM / Keycloak.
- Xây dựng Metadata Service.
- Xây dựng Document Service.
- Xây dựng Workflow Service.
- Xây dựng Audit Service.
- Xây dựng Notification Service.
- Xây dựng Web Frontend.

**Luồng nghiệp vụ demo cần đạt:**

1. Editor đăng nhập.
2. Editor tạo document metadata.
3. Editor upload tài liệu.
4. Editor submit tài liệu sang trạng thái Pending.
5. Approver đăng nhập.
6. Approver approve tài liệu.
7. Viewer đăng nhập.
8. Viewer download tài liệu đã Published.
9. Compliance Officer đăng nhập.
10. Compliance Officer xem audit log thành công.
11. Compliance Officer thử download tài liệu và bị deny.

**Trạng thái hiện tại:** Các service chính đã được đưa vào pipeline build/scan, gồm document-service, gateway, metadata-service, audit-service, notification-service, workflow-service và web frontend. Web app đã chạy được trên EKS qua NodePort, đăng nhập Keycloak, upload, preview và download đã được test thủ công thành công. Việc còn lại là chuẩn hóa bằng chứng demo và hoàn tất các phần sau PR.

---

### Giai đoạn 3: Xây dựng DevSecOps Pipeline

**Mục tiêu:**

Thiết lập pipeline Jenkins tự động từ commit code đến build, scan, push và cập nhật GitOps manifest.

**Các stage chính trong pipeline:**

1. Checkout & Initialize Config
2. Prevent Loop
3. System Check
4. Install
5. Pre-build Security
6. Unit Tests
7. Build & Scan Services
8. Push Image / Push & GitOps
9. DAST - OWASP ZAP
10. End

**Các bước security đã tích hợp:**

- IaC - Checkov Scan
- IaC - Terraform Validate
- SAST - SonarQube
- SCA - OWASP Dependency Check
- Trivy FS Scan
- Unit Tests

**Các service đã được build/scan trong pipeline:**

- document-service
- gateway
- metadata-service
- audit-service
- notification-service
- workflow-service
- web

**Trạng thái hiện tại:** Đã hoàn thành phần lớn pipeline DevSecOps lõi. Pipeline đã chạy qua các bước pre-build security, unit test, build/scan service và push/GitOps. Đây là phần trọng tâm của đồ án hiện tại.

---

### Giai đoạn 4: GitOps với ArgoCD

**Mục tiêu:**

Sử dụng ArgoCD để đồng bộ trạng thái ứng dụng từ Git xuống Kubernetes theo mô hình GitOps.

**Nội dung cần đạt:**

- Pipeline cập nhật manifest hoặc image tag sau khi build thành công.
- Manifest được push vào Git repository.
- ArgoCD phát hiện thay đổi và sync xuống Kubernetes.
- Ứng dụng trên cluster đạt trạng thái Synced/Healthy.
- Có thể rollback bằng cách quay lại phiên bản manifest trước đó.

**Trạng thái hiện tại:** ArgoCD đã hoạt động tốt. Phần GitOps/CD đã đạt yêu cầu cơ bản. Cần lưu lại bằng chứng demo gồm ảnh ArgoCD Application ở trạng thái Synced/Healthy và log pipeline cập nhật manifest.

---

### Giai đoạn 5: DAST bằng OWASP ZAP

**Mục tiêu:**

Bổ sung kiểm thử bảo mật động sau khi ứng dụng được triển khai lên môi trường test/staging.

**Nội dung cần làm:**

- Cấu hình OWASP ZAP baseline scan hoặc API scan.
- Chỉ định URL môi trường test/staging sau khi deploy.
- Nếu có OpenAPI spec, cấu hình ZAP scan theo OpenAPI.
- Xuất report HTML/JSON.
- Lưu report vào Jenkins Artifacts.
- Đặt rule pass/fail phù hợp.

**Trạng thái hiện tại:** Đã có implementation trong Jenkins. `Jenkinsfile` có parameter `RUN_ZAP` và `ZAP_TARGET`; `vars/dastZap.groovy` kiểm tra target reachable, chạy OWASP ZAP baseline scan, xuất `zap_report.html`, `zap_report.json` và dùng policy `UNSTABLE` để không chặn demo khi ZAP trả warning/finding; `vars/postCleanup.groovy` đã archive `zap-report/*.html` và `zap-report/*.json`. Việc còn lại là chạy pipeline thật với target EKS `http://<node-ip>:30006/api` và lưu artifact làm bằng chứng.

**Mức ưu tiên:** Cao. Đây là phần còn thiếu rõ ràng trong pipeline DevSecOps.

---

### Giai đoạn 6: Observability

**Mục tiêu:**

Triển khai hệ thống quan sát để theo dõi trạng thái vận hành của DocVault trên Kubernetes.

**Nội dung cần làm:**

- Cài Prometheus để thu thập metrics.
- Cài Grafana để hiển thị dashboard.
- Cài Loki/promtail để thu thập log tập trung.
- Cấu hình dashboard tối thiểu gồm:
  - CPU/RAM theo pod hoặc service.
  - Request rate.
  - Error rate.
  - Latency nếu có metrics.
  - Trạng thái các service.
- Cấu hình log truy vết theo service.

**Trạng thái hiện tại:** Đã có Argo CD Application tại `infra/argocd-apps/monitoring.yaml` cho `kube-prometheus-stack`, đã bật auto-sync/self-heal và đã apply lên EKS. App `monitoring-stack` đã đạt `Synced/Healthy`, các pod Grafana/Prometheus/Alertmanager đã Running. Việc còn lại là mở Grafana bằng port-forward và chụp dashboard pod health, CPU/RAM, workload status. Loki/promtail để làm sau nếu còn thời gian.

**Mức ưu tiên:** Cao. Đây là tiêu chí quan trọng trong phần hoàn thiện đồ án và là nền tảng cho hướng mở rộng AIOps sau này.

---

### Giai đoạn 7: Xử lý cảnh báo SCA

**Mục tiêu:**

Làm sạch hoặc kiểm soát các cảnh báo từ OWASP Dependency Check trước ngày demo/báo cáo.

**Nội dung cần làm:**

- Mở Dependency Check report.
- Phân loại cảnh báo theo Critical, High, Medium, Low.
- Xác định cảnh báo thật và false positive.
- Nâng cấp dependency nếu có CVE nghiêm trọng.
- Nếu chưa thể xử lý ngay, tạo exception record có lý do rõ ràng.
- Chốt policy: không cho qua CVE Critical/High ở dependency trực tiếp, trừ khi có ngoại lệ được ghi nhận.

**Trạng thái hiện tại:** Đã bắt đầu xử lý. Các dependency trực tiếp rủi ro cao ở web đã được nâng cấp (`mammoth`, `dompurify`) và một số dependency transitive đã được pin bằng `pnpm.overrides`. Các advisory còn lại chủ yếu thuộc framework/tooling hoặc cần major upgrade, được ghi nhận trong `docs/security-sca-triage.md` để đối chiếu với artifact Dependency Check của Jenkins.

**Mức ưu tiên:** Trung bình-cao. Có thể xử lý sau DAST và Observability, nhưng cần hoàn tất trước demo chính thức.

---

## 5. Đánh giá tiến độ hiện tại

### 5.1. Kết luận giai đoạn

Hiện tại nhóm đang ở giai đoạn cuối của phần CI/CD DevSecOps và GitOps deployment, tương ứng khoảng tuần 11-12 hoặc đầu tuần 13 so với kế hoạch ban đầu.

Nhóm đã hoàn thành phần trọng tâm của pipeline, bao gồm:

- Checkout source code.
- Khởi tạo cấu hình pipeline.
- Kiểm tra tránh vòng lặp commit.
- Kiểm tra hệ thống.
- Cài đặt dependencies.
- Chạy các bước pre-build security.
- Chạy unit test.
- Build/scan nhiều microservices.
- Push và cập nhật GitOps.
- ArgoCD hoạt động tốt.

Các phần còn lại tập trung vào hoàn thiện security validation sau deploy, observability và xử lý cảnh báo bảo mật phụ thuộc.

### 5.2. Bảng trạng thái công việc

| Hạng mục | Trạng thái | Ghi chú |
|---|---|---|
| Phân tích đề tài | Đã làm | Đã có đề cương và phạm vi rõ ràng |
| Thiết kế kiến trúc microservices | Đã làm nền tảng | Cần bổ sung sơ đồ/ảnh minh họa vào báo cáo |
| Xây dựng các service chính | Đang hoàn thiện | Các service đã xuất hiện trong pipeline build/scan |
| Jenkins pipeline | Đã làm phần lõi | Pipeline chạy được qua nhiều stage chính |
| Checkov Scan | Đã tích hợp | Đã nằm trong Pre-build Security |
| Terraform Validate | Đã tích hợp | Đã nằm trong Pre-build Security |
| SonarQube SAST | Đã tích hợp | Đã nằm trong Pre-build Security |
| Dependency Check SCA | Đã tích hợp, còn cảnh báo | Sẽ xử lý sau khi pipeline hoàn thiện |
| Trivy FS Scan | Đã tích hợp | Đã nằm trong Pre-build Security |
| Unit Tests | Đã tích hợp | Cần lưu bằng chứng coverage nếu có |
| Build services | Đã làm | Build document, gateway, metadata, audit, notification, workflow, web |
| Push & GitOps | Đã làm | Pipeline có bước cập nhật GitOps |
| ArgoCD | Đã hoạt động tốt | Cần lưu ảnh Synced/Healthy |
| DAST OWASP ZAP | Đã có code và policy, cần chạy thật | Jenkins/ZAP kiểm tra reachability, sinh/archive report và chuyển UNSTABLE thay vì fail demo khi có warning |
| Observability | Đã triển khai Prometheus/Grafana | `monitoring-stack` auto-sync/self-heal, Synced/Healthy, cần chụp dashboard |
| Demo E2E nghiệp vụ | Đã test thủ công trên EKS | Login Keycloak, upload, preview, download đã pass; cần lưu bằng chứng |
| Báo cáo và slide | Chưa hoàn thiện | Làm sau khi có thêm bằng chứng pipeline, ArgoCD, observability |

---

## 6. Những phần đã hoàn thành

### 6.1. DevSecOps Pipeline lõi

Pipeline Jenkins đã được xây dựng với các stage chính từ checkout, kiểm tra môi trường, cài đặt dependencies, chạy security scan, unit test, build service và push/GitOps.

### 6.2. Security scanning trong CI

Pipeline đã tích hợp nhiều công cụ bảo mật quan trọng:

- Checkov cho Infrastructure-as-Code.
- Terraform Validate để kiểm tra cấu hình Terraform.
- SonarQube cho SAST.
- OWASP Dependency Check cho SCA.
- Trivy FS Scan.
- Unit Tests.

### 6.3. Build nhiều service trong hệ thống

Pipeline hiện xử lý được các thành phần chính:

- document-service
- gateway
- metadata-service
- audit-service
- notification-service
- workflow-service
- web

### 6.4. GitOps với ArgoCD

ArgoCD đã hoạt động tốt, chứng minh mô hình GitOps/CD đã được triển khai thành công ở mức cơ bản. Đây là một trong các yêu cầu quan trọng của đồ án.

---

## 7. Những phần chưa hoàn thành

### 7.1. DAST OWASP ZAP

Hiện tại chưa có bằng chứng chạy OWASP ZAP thực tế trên target EKS. Code scan, target URL parameter, target reachability check, report output, UNSTABLE policy và artifact archive đã có trong pipeline. Cần chạy Jenkins với `RUN_ZAP=true` và `ZAP_TARGET=http://<node-ip>:30006/api`.

### 7.2. SCA còn cảnh báo

Dependency Check đã chạy nhưng có cảnh báo. Nhóm đã tạo bản ghi triage tại `docs/security-sca-triage.md`, nâng cấp một số dependency trực tiếp và pin override cho các dependency transitive có bản vá phù hợp. Trước demo cần tải artifact Jenkins mới và đối chiếu lại với bản ghi triage này.

### 7.3. Observability chưa triển khai

Đã triển khai kube-prometheus-stack qua Argo CD và app `monitoring-stack` đã `Synced/Healthy`. Chưa hoàn thiện bằng chứng dashboard/log; cần mở Grafana và chụp dashboard vận hành.

### 7.4. Bằng chứng demo cần chuẩn hóa

Cần thu thập và lưu lại các bằng chứng:

- Ảnh pipeline Jenkins chạy thành công.
- Ảnh ArgoCD Synced/Healthy.
- Report SonarQube.
- Report Checkov.
- Report Trivy.
- Report Dependency Check.
- Report OWASP ZAP sau khi chạy Jenkins với `RUN_ZAP=true`.
- Ảnh Grafana dashboard sau khi triển khai observability.
- Ảnh web app EKS, Keycloak login, upload, preview và download thành công.
- Video hoặc script demo nghiệp vụ DocVault.

---

## 8. Kế hoạch làm tiếp

### Ưu tiên 0: Theo dõi PR vào main

**Mục tiêu:** Đưa các fix NodePort/Keycloak redirect, preview/download qua gateway và tài liệu setup/deployment vào `main`.

**Việc cần làm:**

- Theo dõi review của PR đã mở.
- Đảm bảo PR ghi rõ test trên EKS: login, upload, preview, download.
- Đính kèm hoặc lưu riêng các bằng chứng Jenkins, Argo CD, web app và luồng nghiệp vụ.

**Output cần có:**

- Link PR.
- Checklist verification trong PR.
- Screenshot hoặc ghi chú test EKS.

---

### Ưu tiên 1: Hoàn thiện DAST OWASP ZAP

**Mục tiêu:** Stage DAST chạy thật, có report và có thể lưu vào Jenkins Artifacts.

**Việc cần làm:**

- Chạy Jenkins với `RUN_ZAP=true`.
- Đặt `ZAP_TARGET=http://<node-ip>:30006/api`.
- Pipeline tự kiểm tra Jenkins/ZAP context truy cập được target trước khi scan.
- Kiểm tra artifact `zap_report.html` và `zap_report.json`.
- Policy demo đã được implement: stage chuyển `UNSTABLE` thay vì fail toàn build khi ZAP trả warning/finding, nhưng vẫn fail nếu không sinh được report.

**Output cần có:**

- Screenshot stage DAST chạy thành công.
- File ZAP report.
- Mô tả policy pass/fail.

---

### Ưu tiên 2: Triển khai Observability

**Mục tiêu:** Có dashboard và log tập trung để chứng minh khả năng vận hành.

**Việc cần làm:**

- `infra/argocd-apps/monitoring.yaml` đã được apply và bật auto-sync/self-heal.
- Argo CD app `monitoring-stack` đã ở trạng thái Synced/Healthy.
- Mở Grafana bằng port-forward.
- Chụp dashboard pod health, CPU/RAM và workload status.
- Nếu còn thời gian mới bổ sung Loki/promtail để có log tập trung.

**Output cần có:**

- Screenshot Grafana dashboard.
- Screenshot log trong Loki.
- Mô tả các chỉ số giám sát chính.

---

### Ưu tiên 3: Xử lý cảnh báo SCA

**Mục tiêu:** Không còn cảnh báo nghiêm trọng chưa xử lý trước demo.

**Việc cần làm:**

- Mở Dependency Check report từ Jenkins artifact.
- Đối chiếu với `docs/security-sca-triage.md`.
- Phân loại cảnh báo theo Critical/High/Medium/Low.
- Fix dependency trực tiếp có CVE nghiêm trọng nếu có thể.
- Với false positive hoặc dependency chưa thể fix, cập nhật exception record có lý do.

**Output cần có:**

- Dependency Check report.
- Danh sách dependency đã fix hoặc exception.
- Mô tả policy xử lý CVE.

---

### Ưu tiên 4: Chuẩn hóa demo end-to-end

**Mục tiêu:** Chứng minh hệ thống DocVault hoạt động thật, không chỉ có pipeline.

**Luồng demo đề xuất:**

1. Editor upload tài liệu.
2. Editor submit tài liệu sang Pending.
3. Approver approve tài liệu.
4. Viewer download tài liệu Published.
5. Compliance Officer xem audit log.
6. Compliance Officer thử download và bị deny.
7. Jenkins pipeline chạy qua các security gates.
8. ArgoCD sync ứng dụng thành công.
9. Grafana hiển thị metrics/logs.

**Output cần có:**

- Script demo EKS tại `docs/demo-flow.md`.
- Video demo ngắn.
- Screenshot các bước chính.

---

## 9. Checklist trước ngày báo cáo

### Pipeline

- [x] Jenkins pipeline chạy được.
- [x] Có Checkov Scan.
- [x] Có Terraform Validate.
- [x] Có SonarQube SAST.
- [x] Có Dependency Check SCA.
- [x] Có Trivy FS Scan.
- [x] Có Unit Tests.
- [x] Có build nhiều service.
- [x] Có Push & GitOps.
- [x] Có DAST OWASP ZAP code/policy trong Jenkins shared library.
- [ ] Có DAST OWASP ZAP chạy thật với `RUN_ZAP=true`.
- [x] Có cấu hình lưu ZAP report artifact trong Jenkins.
- [ ] Có artifact `zap_report.html` và `zap_report.json` từ lần chạy thật.

### GitOps / Kubernetes

- [x] ArgoCD hoạt động tốt.
- [ ] Có screenshot ArgoCD Synced/Healthy.
- [ ] Có bằng chứng pod/service/ingress chạy ổn định.
- [ ] Có demo rollback hoặc mô tả rollback GitOps.

### Observability

- [x] Có manifest Argo CD cho kube-prometheus-stack với auto-sync/self-heal.
- [x] Có Prometheus chạy trên cluster.
- [x] Có Grafana chạy trên cluster.
- [ ] Có screenshot Grafana dashboard.
- [ ] Có Loki/promtail.
- [ ] Có log theo service.
- [ ] Có screenshot dashboard/log.

### Ứng dụng DocVault

- [x] Có test thủ công login/upload/preview/download trên EKS.
- [ ] Có screenshot Editor upload tài liệu.
- [ ] Có demo Submit -> Pending.
- [ ] Có demo Approver approve.
- [ ] Có demo Viewer download tài liệu Published.
- [ ] Có demo Compliance Officer xem audit.
- [ ] Có demo Compliance Officer download bị deny.

### Báo cáo / minh chứng

- [ ] Cập nhật báo cáo theo pipeline thực tế.
- [ ] Chèn ảnh pipeline Jenkins.
- [ ] Chèn ảnh ArgoCD.
- [ ] Chèn ảnh dashboard Grafana sau khi làm observability.
- [ ] Chèn các report bảo mật.
- [ ] Chuẩn bị slide demo.

---

## 10. Câu mô tả tiến độ có thể đưa vào báo cáo

Hiện tại, nhóm đã hoàn thành phần CI/CD DevSecOps lõi và GitOps deployment với Jenkins và ArgoCD. Pipeline đã tích hợp các bước kiểm tra IaC, SAST, SCA, Trivy, unit test, build nhiều microservices và cập nhật GitOps manifest. Ứng dụng đã chạy được trên EKS qua NodePort và đã test thủ công thành công các luồng Keycloak login, upload, preview và download. DAST OWASP ZAP đã có khung Jenkins sinh artifact, observability đã triển khai kube-prometheus-stack qua Argo CD và SCA đã có bản ghi triage/exception. Các phần còn lại là chạy ZAP thật trên target EKS, chụp Grafana dashboard/log và chuẩn hóa toàn bộ bằng chứng báo cáo.

---

## 11. Kết luận

Tính đến thời điểm hiện tại, đồ án đã đạt được phần quan trọng nhất của hướng DevSecOps: pipeline tự động có nhiều bước kiểm thử và quét bảo mật, build được các service chính và tích hợp GitOps với ArgoCD. Trạng thái này cho thấy nhóm đã đi qua giai đoạn CI/CD lõi và đang bước vào giai đoạn hoàn thiện các tiêu chí đánh giá sau deploy.

Các công việc nên ưu tiên tiếp theo là:

1. Theo dõi và hoàn tất PR vào `main`.
2. Chạy DAST OWASP ZAP thật trên target EKS để có report artifact.
3. Apply monitoring stack và chụp Grafana dashboard.
4. Đối chiếu Dependency Check artifact với `docs/security-sca-triage.md`.
5. Chuẩn hóa demo end-to-end và thu thập bằng chứng cho báo cáo.
