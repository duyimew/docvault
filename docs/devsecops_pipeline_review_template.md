# DevSecOps Pipeline Review Brief for AI Agent — DocVault

## 1) Mục tiêu tài liệu

Tài liệu này dùng làm **brief chuẩn** cho AI Agent để:

1. Đọc hiểu **kế hoạch triển khai DevSecOps pipeline** cho dự án DocVault.
2. So sánh kế hoạch đó với **codebase hiện tại**.
3. Đưa ra nhận xét có cấu trúc về:
   - những gì đã sẵn sàng,
   - những gì còn thiếu,
   - những gì đang làm sai hướng,
   - mức độ ưu tiên triển khai tiếp theo.

AI Agent **không chỉ liệt kê file**. Nó phải đánh giá repo theo góc nhìn **kiến trúc, CI/CD, security gates, GitOps, observability, deployability và demo readiness**.

---

## 2) Bối cảnh dự án

### 2.1. Tên dự án
**DocVault** — hệ thống quản lý tài liệu bảo mật theo kiến trúc **microservices**, áp dụng **DevSecOps** và **GitOps**, định hướng mở rộng sang **LLM Auto-Remediation for IaC** và **AIOps** ở các giai đoạn sau.

### 2.2. Mục tiêu hiện tại
Phần **MVP web-microservices đã hoàn thiện cơ bản**. Giai đoạn hiện tại tập trung vào:

- containerization,
- hạ tầng triển khai,
- CI security gates,
- GitOps/CD,
- observability,
- chuẩn bị demo evidence.

### 2.3. Phạm vi giai đoạn này
AI Agent chỉ đánh giá theo **scope DevSecOps pipeline cho MVP hiện tại**.

**Không ưu tiên** trong giai đoạn này:
- AIOps,
- LLM remediation,
- service mesh nâng cao,
- canary rollout phức tạp,
- event-driven architecture mở rộng,
- multi-region DR,
- advanced policy engine runtime.

---

## 3) Kiến trúc hệ thống mục tiêu

### 3.1. Các services chính
Dự án được kỳ vọng có ít nhất các thành phần sau:

- **API Gateway**
- **IAM / Keycloak integration**
- **Document Service**
- **Metadata Service**
- **Workflow Service**
- **Audit Service**
- **Notification Service**

### 3.2. Trách nhiệm từng service ở mức kiến trúc

#### API Gateway
- verify JWT,
- route request,
- propagate headers,
- có thể áp dụng centralized RBAC ở mức edge cho một số route nhạy cảm.

#### IAM / Keycloak
- quản lý user / role,
- cấp JWT theo OIDC,
- map roles cho các luồng nghiệp vụ.

#### Metadata Service
- là **source-of-truth** cho:
  - document metadata,
  - ACL / policy attributes,
  - status,
  - version pointer.
- policy check cho quyền đọc / tìm kiếm / download authorization.

#### Document Service
- chỉ xử lý blob/object storage,
- upload/download/presign,
- không ôm logic ACL chính,
- kết nối MinIO hoặc S3-compatible storage.

#### Workflow Service
- điều phối state machine:
  - `DRAFT -> PENDING -> PUBLISHED`
  - có thể có `REJECT -> DRAFT`
- enforce transition đúng role và đúng trạng thái.

#### Audit Service
- append-only audit events,
- query theo actor / action / time range / resource,
- phục vụ demo compliance.

#### Notification Service
- gửi event submitted / approved / rejected,
- MVP có thể chỉ log/console hoặc 1 kênh tối thiểu.

---

## 4) Luồng nghiệp vụ chuẩn phải dùng để đánh giá repo

AI Agent phải kiểm tra codebase có đủ nền để phục vụ **ít nhất 1 luồng E2E chuẩn** sau không:

1. **Editor** tạo document metadata.
2. **Editor** upload file.
3. **Editor** submit workflow.
4. **Approver** approve.
5. Document sang trạng thái **Published**.
6. **Viewer** search/view/download thành công.
7. **Compliance Officer** query audit thành công nhưng **download luôn bị deny**.

Nếu codebase chưa đạt được luồng trên, AI Agent phải xác định rõ:
- thiếu service nào,
- thiếu endpoint nào,
- thiếu integration nào,
- thiếu policy nào,
- thiếu test/demo script nào.

---

## 5) RBAC chuẩn để đối chiếu

AI Agent cần dùng RBAC sau làm chuẩn đánh giá:

### Viewer
- xem metadata,
- download khi document đã Published và policy allow.

### Editor
- tạo document,
- upload/update version,
- submit workflow,
- đọc document của mình.

### Approver
- approve/reject workflow,
- đọc metadata rộng hơn,
- có thể download theo policy.

### Compliance Officer
- xem audit logs,
- có thể xem metadata ở mức phù hợp,
- **không được download file**.

### Điểm bắt buộc
AI Agent phải xác minh xem logic deny download cho Compliance Officer có được enforce ở **backend** hay không. Nếu chỉ chặn ở frontend thì xem là **chưa đạt**.

---

## 6) Security baseline cần có trước khi bước sâu vào pipeline

AI Agent cần đánh giá repo hiện tại đã sẵn sàng cho DevSecOps pipeline hay chưa theo các tiêu chí nền sau:

### 6.1. Service baseline
- mỗi service có cấu trúc rõ ràng,
- chạy local được,
- có healthcheck,
- có cấu hình môi trường,
- có error handling,
- có logging đủ dùng,
- có OpenAPI/Swagger cho endpoint public.

### 6.2. Auth / IAM baseline
- có tích hợp Keycloak hoặc OIDC rõ ràng,
- JWT verify ở gateway hoặc service,
- roles có thể map vào business flow.

### 6.3. Storage baseline
- file/object được lưu vào MinIO hoặc S3-compatible storage,
- metadata tách riêng khỏi blob,
- version pointer được quản lý hợp lý,
- có checksum hoặc ít nhất có định danh version rõ ràng.

### 6.4. Workflow baseline
- có trạng thái tài liệu,
- có logic transition,
- có validate transition sai trạng thái.

### 6.5. Audit baseline
- có ingest audit event,
- có query audit,
- có tracking các hành động quan trọng như:
  - create,
  - upload,
  - submit,
  - approve/reject,
  - download allow/deny,
  - auth event nếu có.

### 6.6. Demo baseline
- có seed data / seed users,
- có demo script / Postman / curl / E2E script,
- có thể tái hiện E2E flow.

Nếu thiếu các yếu tố trên, AI Agent cần kết luận repo **chưa đủ sạch để đẩy mạnh pipeline**, và chỉ ra thứ tự cần sửa.

---

## 7) Kiến trúc DevSecOps pipeline mục tiêu

AI Agent cần dùng pipeline đích sau để so sánh:

### 7.1. Dòng chảy chính
`Developer push code -> Jenkins CI -> test + scans -> build image -> push image -> update manifest/Helm -> ArgoCD sync -> deploy lên Kubernetes -> Prometheus/Grafana/Loki quan sát runtime`

### 7.2. Vai trò từng lớp

#### CI
- build,
- unit test,
- static analysis,
- security scans,
- quality gates,
- build image,
- push image.

#### CD / GitOps
- Jenkins **không deploy trực tiếp bằng kubectl** nếu muốn bám chuẩn GitOps.
- Jenkins nên cập nhật repo manifest / Helm values.
- ArgoCD sync desired state từ Git xuống cluster.

#### Runtime
- metrics qua Prometheus,
- dashboard qua Grafana,
- logs tập trung qua Loki,
- có đủ dữ liệu để demo và vận hành.

---

## 8) Bộ công cụ mục tiêu dùng để đánh giá mức sẵn sàng

AI Agent cần so sánh codebase/repo với bộ công cụ mục tiêu sau:

### Nền tảng
- Docker
- Docker Compose
- Kubernetes
- Terraform

### CI / Security Gates
- Jenkins
- SonarQube
- Trivy
- Checkov
- có thể thêm SCA riêng nếu cần
- OWASP ZAP ở giai đoạn sau

### CD / GitOps
- ArgoCD
- Helm hoặc Kubernetes manifests

### Auth / Security nền
- Keycloak
- JWT / OIDC
- TLS ingress tối thiểu
- MinIO SSE / at-rest encryption mô phỏng nếu có

### Observability
- Prometheus
- Grafana
- Loki

AI Agent không được chỉ nói “chưa thấy tool X”. Nó phải trả lời cụ thể:
- repo đã có file cấu hình nào,
- đã dùng tới mức nào,
- thiếu ở lớp nào,
- thiếu ở mức implementation hay chỉ thiếu wiring/integration.

---

## 9) Thứ tự triển khai chuẩn mà AI Agent phải dùng để nhận xét

Khi so sánh codebase, AI Agent phải đánh giá theo **thứ tự hợp lý**, không đảo ngược:

### Phase 0 — Freeze MVP và chuẩn hóa baseline
- khóa scope,
- không thêm business feature mới,
- dọn blocker,
- chuẩn hóa local run.

### Phase 1 — Containerization và local platform
- Dockerfile cho từng service,
- compose stack,
- env/config,
- healthcheck,
- local E2E.

### Phase 2 — IaC và cluster test
- Terraform dựng môi trường test,
- namespace,
- ingress,
- base K8s deployment.

### Phase 3 — Jenkins CI cơ bản
- checkout,
- install,
- lint,
- test,
- build,
- image build/push.

### Phase 4 — Security quality gates
- SonarQube,
- Trivy,
- Checkov,
- SCA nếu cần,
- ZAP về sau.

### Phase 5 — GitOps/CD
- manifest repo hoặc GitOps directory,
- Helm/manifests,
- ArgoCD Application,
- sync/rollback.

### Phase 6 — Observability và evidence
- Prometheus,
- Grafana,
- Loki,
- dashboard,
- logs,
- demo evidence.

AI Agent phải nhận xét repo đang ở **phase nào thật sự**, không chỉ phase mà nhóm nghĩ mình đang ở.

---

## 10) Definition of Done ở mức pipeline MVP

AI Agent cần dùng bộ tiêu chí sau để kết luận mức hoàn thiện.

### 10.1. DoD cho local baseline
- `docker compose up` chạy được stack tối thiểu,
- đăng nhập/auth hoạt động,
- upload/download/workflow/audit hoạt động ở local,
- E2E flow có thể tái hiện.

### 10.2. DoD cho CI
- có Jenkinsfile hoặc pipeline config rõ ràng,
- commit mới kích hoạt build,
- unit test chạy tự động,
- build image thành công,
- logs pipeline rõ ràng.

### 10.3. DoD cho security gates
- SonarQube analysis chạy được,
- Trivy scan image chạy được,
- Checkov scan IaC chạy được,
- có pass/fail logic rõ ràng,
- ít nhất có 1 case fail gate minh họa được.

### 10.4. DoD cho GitOps/CD
- image tag hoặc values được update trong Git,
- ArgoCD sync xuống cluster,
- rollback bằng Git hoạt động.

### 10.5. DoD cho observability
- có metrics cơ bản: CPU/RAM/request/error/latency nếu phù hợp,
- có dashboard xem được,
- có logs tập trung,
- có dữ liệu để demo sự cố hoặc deny cases.

### 10.6. DoD cho demo
- có 1 E2E business flow,
- có 1 kịch bản gate chặn deploy,
- có 1 kịch bản rollback GitOps,
- có RBAC deny/allow rõ ràng,
- có audit evidence.

---

## 11) Những dấu hiệu “đi sai hướng” mà AI Agent phải phát hiện

AI Agent cần cảnh báo rõ nếu repo/codebase có một hoặc nhiều vấn đề sau:

1. Services chưa ổn định local nhưng đã lao vào Jenkins/ArgoCD.
2. Business flow chưa chạy xong nhưng đã thêm quá nhiều tool scan.
3. Jenkins deploy trực tiếp bằng `kubectl apply` mà không có GitOps repo hoặc desired-state flow rõ ràng.
4. ACL/policy đang nằm ở frontend thay vì backend.
5. Document service đang ôm cả metadata/query/ACL, làm boundary sai.
6. Chưa có E2E demo script nhưng đã cố dựng monitoring/pipeline phức tạp.
7. Thêm RabbitMQ/Kafka/Istio/canary quá sớm khi MVP chưa ổn.
8. Có Dockerfile nhưng không chạy được compose hoặc không healthcheck được.
9. Có K8s manifest nhưng local/dev chưa tái hiện được flow tối thiểu.
10. Có scan tools nhưng không có pass/fail gate rõ ràng.
11. Có dashboards nhưng không có log/metrics từ app thật.
12. Không có seed users/roles dữ liệu demo cho viewer/editor/approver/compliance officer.

---

## 12) Checklist cực chi tiết để AI Agent đối chiếu với codebase

AI Agent cần duyệt repo theo từng nhóm sau.

### 12.1. Repository structure
Kiểm tra xem repo có:
- cấu trúc monorepo hay multi-repo rõ ràng không,
- thư mục `services/`, `apps/`, `infra/`, `docs/`, `helm/` hoặc tương đương không,
- tách bạch app code, infra code, deployment config hay chưa.

### 12.2. Application readiness
Kiểm tra:
- service entrypoints,
- env files,
- Dockerfile,
- compose files,
- migrations,
- seed scripts,
- test scripts,
- health endpoints,
- swagger/openapi.

### 12.3. Auth & RBAC
Kiểm tra:
- Keycloak integration,
- role constants,
- JWT verification,
- route guards,
- backend authorization,
- special deny cho Compliance Officer.

### 12.4. Storage & document flow
Kiểm tra:
- MinIO/S3 wiring,
- upload endpoint,
- version pointer,
- metadata persistence,
- download authorization,
- presigned URL/stream logic.

### 12.5. Workflow
Kiểm tra:
- status enum,
- state transition,
- approve/reject/submit endpoints,
- ownership/role checks,
- conflict handling.

### 12.6. Audit
Kiểm tra:
- audit event schema,
- ingest mechanism,
- query endpoint,
- filtering theo actor/time/action,
- deny events có được log không.

### 12.7. CI readiness
Kiểm tra:
- Jenkinsfile,
- scripts build/test,
- ability to run in non-interactive CI,
- artifact/image naming,
- secrets/credentials strategy.

### 12.8. Security scans
Kiểm tra:
- SonarQube config,
- Trivy config,
- Checkov config,
- ignore/allowlist policy nếu có,
- gates có thực sự fail pipeline hay mới chỉ report.

### 12.9. GitOps readiness
Kiểm tra:
- K8s manifests/Helm,
- values per env,
- image tag injection,
- ArgoCD application manifest,
- repo separation giữa app và deploy state.

### 12.10. Observability readiness
Kiểm tra:
- metrics endpoint,
- structured logs,
- Prometheus scrape config,
- Grafana dashboard definitions,
- Loki/promtail config,
- traceId/requestId propagation.

### 12.11. Demo readiness
Kiểm tra:
- E2E script,
- Postman collection,
- seed users,
- seed data,
- one-command demo path,
- fail/pass examples.

---

## 13) Cách AI Agent phải phân loại mức độ hoàn thiện

AI Agent phải chia phát hiện thành 4 nhóm rõ ràng:

### A. Đã tốt / sẵn sàng
Những phần đã triển khai đúng hướng, có thể giữ nguyên hoặc chỉ harden nhẹ.

### B. Đã có nền nhưng chưa hoàn thiện
Những phần đã bắt đầu đúng nhưng còn thiếu wiring, config, policies, tests hoặc integration.

### C. Thiếu hoàn toàn
Những phần gần như chưa có, cần đưa vào backlog chính thức.

### D. Sai kiến trúc hoặc sai thứ tự ưu tiên
Những phần đang triển khai nhưng đi lệch so với mục tiêu DevSecOps MVP.

---

## 14) Output format bắt buộc mà AI Agent phải trả về

AI Agent phải trả lời theo đúng cấu trúc sau:

### 14.1. Executive Summary
- Repo hiện tại đang ở phase nào.
- Mức độ sẵn sàng tổng thể: `% gần đúng` hoặc `Low / Medium / High`.
- 3 điểm mạnh lớn nhất.
- 3 blocker lớn nhất.

### 14.2. Architecture Fit Review
Đánh giá từng service/boundary:
- gateway,
- auth,
- metadata,
- document,
- workflow,
- audit,
- notification.

### 14.3. DevSecOps Readiness Review
Đánh giá từng lớp:
- containerization,
- local run,
- IaC,
- CI,
- security scans,
- GitOps,
- observability.

### 14.4. Gap Analysis Table
Một bảng gồm các cột:
- Area
- Expected
- Current state in repo
- Gap
- Severity (High/Medium/Low)
- Recommended action

### 14.5. Top Priorities
Liệt kê **5 việc ưu tiên nhất** cần làm tiếp theo để tiến gần nhất tới pipeline MVP.

### 14.6. Anti-Pattern Warnings
Chỉ rõ các chỗ đang làm sai hướng, nếu có.

### 14.7. Proposed Next Sprint
Đề xuất backlog cho 1 sprint gần nhất, chia theo:
- must-do,
- should-do,
- optional.

---

## 15) Quy tắc đánh giá để tránh nhận xét chung chung

AI Agent phải tuân thủ các nguyên tắc sau khi đọc repo:

1. **Dẫn chứng cụ thể từ codebase**: file, thư mục, config, script, service, manifest.
2. **Không phán đoán mơ hồ** kiểu “có vẻ”, “chắc là”. Nếu chưa chắc thì nói rõ chưa thấy bằng chứng.
3. **Không yêu cầu scope vượt quá giai đoạn hiện tại**.
4. **Không ép thêm tool** nếu chưa có nền để vận hành chúng.
5. **Ưu tiên deployability và demo readiness** hơn sự phức tạp kỹ thuật.
6. **Đánh giá dựa trên khả năng chạy thật**, không chỉ dựa trên việc file có tồn tại.
7. **Luôn kiểm tra sự khớp giữa kiến trúc repo và luồng nghiệp vụ E2E**.

---

## 16) Những câu hỏi chính AI Agent phải tự trả lời khi đọc repo

1. Repo hiện tại đã đủ sạch và ổn để bước vào DevSecOps chưa?
2. MVP business flow có thật sự chạy được end-to-end chưa?
3. Service boundaries có đúng như kiến trúc mục tiêu không?
4. Auth/RBAC đã enforce ở backend chưa?
5. Repo đã sẵn sàng cho containerization và compose chưa?
6. Hạ tầng K8s/Terraform đã có nền chưa?
7. Jenkins CI có thể thêm ngay hay còn blocker từ codebase?
8. Security gates nào nên thêm ngay, gates nào nên để sau?
9. GitOps với ArgoCD có phù hợp với repo hiện tại chưa hay cần tái cấu trúc?
10. Observability đang là thiếu công cụ, thiếu wiring hay thiếu app instrumentation?
11. Nếu bắt đầu triển khai ngay bây giờ, 5 việc đầu tiên phải làm là gì?

---

## 17) Kết luận mong muốn từ AI Agent

Kết quả cuối cùng mà AI Agent cần trả về không phải là “repo có những file gì”, mà là một **bản nhận xét chiến lược nhưng có căn cứ kỹ thuật**, trả lời rõ:

- repo đang ở đâu,
- còn cách pipeline MVP bao xa,
- nên làm gì ngay,
- nên bỏ gì chưa cần làm,
- có gì đang đi sai hướng cần sửa sớm.

---

## 18) Prompt ngắn để dùng kèm tài liệu này cho AI Agent

Bạn có thể đưa kèm prompt sau:

> Hãy đọc tài liệu markdown này trước, sau đó đối chiếu với codebase hiện tại của dự án DocVault. Tôi cần bạn đánh giá mức độ sẵn sàng của repo để triển khai DevSecOps pipeline cho MVP hiện tại. Hãy bám đúng scope trong tài liệu, không mở rộng sang AIOps hay nghiên cứu giai đoạn sau. Tôi muốn một báo cáo có cấu trúc, chỉ rõ điểm mạnh, điểm thiếu, điểm sai hướng, gap analysis, 5 ưu tiên tiếp theo và backlog sprint gần nhất. Mọi nhận xét phải có dẫn chứng cụ thể từ codebase.

