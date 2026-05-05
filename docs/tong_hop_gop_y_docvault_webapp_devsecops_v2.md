# Tổng hợp góp ý của thầy cho DocVault: Web App bảo mật và Pipeline DevSecOps

> Tài liệu này tổng hợp lại các ghi chú trao đổi với giảng viên hướng dẫn, đồng thời viết lại theo bố cục rõ ràng hơn gồm **hai phần chính**:
>
> 1. **Phần Web App DocVault**: tập trung vào nghiệp vụ quản lý tài liệu bảo mật, RBAC, audit, hash, secret key, key rotation và các chức năng nên học hỏi từ các hệ thống quản lý tài liệu doanh nghiệp phổ biến.
> 2. **Phần Pipeline DevSecOps**: tập trung vào CI/CD, GitOps, Policy as Code, artifact registry, RBAC cho pipeline/Kubernetes, quản lý môi trường dev/production và bằng chứng demo.

---

## 0. Cách hiểu tổng quát từ góp ý của thầy

Theo góp ý của thầy, DocVault không nên được trình bày đơn thuần là một web app upload/download tài liệu. Hướng đúng nên là:

> **DocVault là một hệ thống quản lý tài liệu bảo mật cấp doanh nghiệp, triển khai theo kiến trúc microservices trên Kubernetes, có DevSecOps/GitOps, kiểm soát truy cập nhiều lớp, quản lý secret/key đúng chuẩn, Policy as Code và khả năng mở rộng sang AI/AIOps.**

Có hai lớp cần tách rõ:

| Lớp | Trọng tâm | Câu hỏi cần trả lời |
|---|---|---|
| Web App | Tài liệu được lưu, phân quyền, phê duyệt, kiểm toán, mã hóa và bảo vệ như thế nào? | DocVault có giống một hệ thống quản lý tài liệu doanh nghiệp thật không? |
| Pipeline DevSecOps | Code, image, manifest, secret, artifact và deployment được kiểm soát như thế nào? | Hệ thống có được build/deploy an toàn và có thể chứng minh bằng evidence không? |

Điểm quan trọng: **Policy as Code và RBAC không chỉ nằm trong web app**. Chúng cần xuất hiện ở cả ứng dụng, pipeline, Kubernetes, ArgoCD và registry. Riêng phần **key rotation** trong góp ý của thầy nên được đặt chủ yếu ở **web app / runtime application security**, không cần đẩy thành yêu cầu bắt buộc ở pipeline.

---

# PHẦN I. WEB APP DOCVAULT: HỆ THỐNG QUẢN LÝ TÀI LIỆU BẢO MẬT

## 1.1. Định vị lại DocVault so với web app thông thường

DocVault hiện tại đã có định hướng khá tốt: quản lý tài liệu, workflow phê duyệt, RBAC, audit trail, MinIO, Keycloak, PostgreSQL/MongoDB và triển khai microservices. Tuy nhiên, để giống một công cụ quản lý tài liệu bảo mật doanh nghiệp hơn, DocVault cần được trình bày theo các năng lực sau:

1. **Secure Document Storage**: lưu file có versioning, checksum/hash, encryption at rest.
2. **Document Workflow**: Draft -> Pending -> Approved/Published, có approve/reject rõ ràng.
3. **Fine-grained Access Control**: RBAC/ACL theo user, role, document status, classification.
4. **Compliance Audit Trail**: ghi lại ai làm gì, lúc nào, kết quả gì, từ IP nào.
5. **Tamper-evident Logging**: log có thể phát hiện bị sửa bằng hash-chain.
6. **Secret/Key Management**: không hard-code secret key; có cơ chế key rotation ở web app/runtime.
7. **Content Governance**: classification, retention, DLP/watermarking nếu có thời gian.
8. **Threat Protection**: malware scanning, phát hiện hành vi bất thường, ransomware-oriented monitoring nếu mở rộng.
9. **AI-ready**: metadata/classification/audit được thiết kế để về sau tích hợp AI tìm kiếm, tóm tắt, gợi ý phân loại hoặc hỏi đáp tài liệu theo quyền.

Cách nói gọn trong báo cáo:

> Điểm mạnh của DocVault không nằm ở chức năng upload/download tài liệu cơ bản, mà nằm ở việc kết hợp quản lý tài liệu bảo mật với RBAC, workflow, audit trail, hash integrity, secret/key lifecycle, Kubernetes-native deployment và DevSecOps/GitOps.

---

## 1.2. Đối chiếu với các công cụ quản lý tài liệu bảo mật phổ biến

Dưới đây là các hệ thống/công cụ phổ biến nên tham khảo để định vị DocVault.

### 1.2.1. Box / Box Shield

Box Shield tập trung vào bảo vệ nội dung bằng classification, access policy, malware/threat detection và ransomware detection. Box cũng nhấn mạnh AI-powered classification và threat analysis.

**Điểm DocVault nên học:**

| Năng lực từ Box Shield | DocVault nên bổ sung |
|---|---|
| Content classification | Thêm `classification`: public/internal/confidential/restricted |
| Access policy theo classification | Tài liệu confidential chỉ role/group nhất định được download |
| Threat/malware detection | Tích hợp ClamAV hoặc Trivy filesystem scan cho file upload |
| Ransomware-oriented signal | Ghi nhận nhiều lần download/delete/version change bất thường để phục vụ AIOps sau này |
| Admin alert | Notification khi deny nhiều lần, upload file độc hại, download bất thường |

### 1.2.2. Microsoft SharePoint/OneDrive + Microsoft Purview

Microsoft Purview dùng sensitivity labels và DLP policy để bảo vệ dữ liệu trên nhiều vị trí như Exchange, SharePoint, OneDrive, thiết bị và các workload Microsoft 365 khác.

**Điểm DocVault nên học:**

| Năng lực từ Microsoft Purview | DocVault nên bổ sung |
|---|---|
| Sensitivity label | Classification label trên metadata tài liệu |
| DLP rule | Rule phát hiện nội dung nhạy cảm như email, số điện thoại, mã định danh |
| Policy condition | Nếu tài liệu confidential thì chặn public sharing/download |
| Audit/compliance | Audit query theo user/time/action/document classification |
| Admin workflow | Cần cơ chế approve khi chia sẻ tài liệu nhạy cảm |

### 1.2.3. M-Files

M-Files nổi bật ở hướng **metadata-driven document management**: tài liệu không chỉ nằm trong folder, mà được quản trị bằng metadata, business context, relationship, workflow và governance.

**Điểm DocVault nên học:**

| Năng lực từ M-Files | DocVault nên bổ sung |
|---|---|
| Metadata-driven architecture | Metadata Service là source-of-truth, không để Document Service ôm ACL/query |
| Context-based search | Search theo title, tag, owner, classification, status |
| Workflow automation | State machine rõ ràng: Draft -> Pending -> Published/Rejected |
| Relationship | Về sau có thể liên kết tài liệu với dự án/phòng ban/khách hàng |
| AI-ready metadata | Metadata tốt giúp AI tóm tắt, hỏi đáp, gợi ý phân loại tốt hơn |

### 1.2.4. OpenText Extended ECM

OpenText Extended ECM định vị là enterprise content management, kết nối nội dung với các quy trình doanh nghiệp như SAP, Salesforce, Microsoft và hỗ trợ workflow/compliance.

**Điểm DocVault nên học:**

| Năng lực từ OpenText ECM | DocVault nên bổ sung |
|---|---|
| Enterprise workflow | Workflow approval đủ rõ, có reject reason, event notification |
| Compliance lifecycle | Retention policy mô phỏng 90 ngày hoặc theo classification |
| Integration readiness | API Gateway + OpenAPI để dễ tích hợp hệ thống khác |
| Records/document governance | Audit, versioning, metadata, classification cần đồng bộ |

### 1.2.5. Alfresco Governance Services

Alfresco Governance Services kết hợp records management, security controls, classification và retention lifecycle; tài liệu Alfresco cũng nhấn mạnh retention schedule và vòng đời record.

**Điểm DocVault nên học:**

| Năng lực từ Alfresco Governance | DocVault nên bổ sung |
|---|---|
| Records management | Phân biệt document thường và record đã Published/Approved |
| Retention schedule | Thêm trường `retentionClass`, `retentionUntil` |
| Classification/security controls | Policy download theo classification/status/role |
| Audit-proof mindset | Hash-chain audit log để phát hiện chỉnh sửa |

### 1.2.6. Nextcloud

Nextcloud có hướng self-hosted file collaboration và hỗ trợ end-to-end encryption khi được bật phía server; tài liệu được mã hóa trên thiết bị và server không thấy nội dung plaintext.

**Điểm DocVault nên học:**

| Năng lực từ Nextcloud | DocVault nên bổ sung |
|---|---|
| Self-hosted/on-premise | Định vị DocVault là hệ thống nội bộ doanh nghiệp tự quản lý |
| Encryption | MinIO SSE cho MVP; E2EE hoặc client-side encryption để future work |
| Privacy model | Admin hạ tầng không nhất thiết được xem nội dung file |
| Access control | Không lộ presigned URL nếu không đủ quyền |

### 1.2.7. Egnyte

Egnyte nhấn mạnh ransomware detection, granular least-privilege permissions, 2FA, data classification, audit trail và recovery.

**Điểm DocVault nên học:**

| Năng lực từ Egnyte | DocVault nên bổ sung |
|---|---|
| Least privilege | RBAC + ACL + deny Compliance Officer download |
| Ransomware detection | Future work: phát hiện nhiều version thay đổi bất thường trong thời gian ngắn |
| Data classification | Classification + policy theo classification |
| Audit trail | Audit đủ event: upload/download/deny/approve/reject/login |
| 2FA | Bật OTP/MFA trong Keycloak nếu demo được |

---

## 1.3. Kết luận đối chiếu: DocVault cần làm gì để giống DMS/ECM doanh nghiệp hơn?

### 1.3.1. Những phần DocVault đã có hoặc đang đúng hướng

| Nhóm năng lực | Hiện trạng DocVault |
|---|---|
| Microservices | Gateway, IAM, Document, Metadata, Workflow, Audit, Notification |
| IAM/SSO | Keycloak OIDC/OAuth2 |
| RBAC | Viewer, Editor, Approver, Compliance Officer |
| Workflow | Draft -> Pending -> Published/Rejected |
| Object storage | MinIO S3 protocol |
| Metadata | PostgreSQL lưu metadata, version pointer, ACL |
| Audit | Audit service append-only, query theo user/time/action |
| Checksum | SHA-256 cho file/version |
| DevSecOps/GitOps | Jenkins, ArgoCD, SonarQube, Trivy, Checkov, ZAP |

### 1.3.2. Những phần nên bổ sung để DocVault “giống enterprise DMS” hơn

| Ưu tiên | Chức năng bổ sung | Lý do |
|---|---|---|
| Cao | Classification label: public/internal/confidential/restricted | Nền tảng cho access policy, DLP, audit, AI classification |
| Cao | Hash-chain audit log | Tăng tính kiểm toán, chứng minh log không bị sửa |
| Cao | File malware scan khi upload | Hệ thống quản lý tài liệu doanh nghiệp cần chặn file độc hại |
| Cao | Key/secret rotation cho web app/runtime | Đúng keyword thầy nhắc: secret key tự thay đổi sau thời gian |
| Cao | MFA/OTP trên Keycloak | Tăng bảo mật đăng nhập |
| Trung bình | Retention policy | Gần với records management/compliance |
| Trung bình | DLP pattern detection | Phát hiện email, số điện thoại, mã định danh trong tài liệu |
| Trung bình | Watermark khi preview/download | Hữu ích cho tài liệu confidential |
| Trung bình | Admin security dashboard | Thấy deny event, download nhiều, upload độc hại |
| Thấp/Future | E2EE/client-side encryption | Hay nhưng khó, nên để future work |
| Thấp/Future | Ransomware behavior detection | Gắn với AIOps/ML sau này |

### 1.3.3. DocVault có thể “hay hơn” công cụ phổ biến ở điểm nào?

DocVault khó cạnh tranh về độ đầy đủ chức năng với Box, SharePoint, M-Files hay OpenText. Nhưng trong phạm vi đồ án/bài báo, DocVault có thể có điểm hay riêng:

1. **DevSecOps-first DMS**: nhiều DMS tập trung vào nghiệp vụ tài liệu, còn DocVault nhấn mạnh toàn bộ chuỗi supply chain từ code đến runtime.
2. **GitOps-native**: trạng thái hệ thống được quản lý bằng Git và ArgoCD, có rollback/dev-prod rõ.
3. **Policy as Code xuyên suốt**: cùng tư duy policy áp dụng ở web app, manifest, Terraform, admission và runtime action.
4. **Audit trail dùng được cho AIOps**: audit không chỉ phục vụ compliance mà còn làm dữ liệu phân tích vận hành/anomaly.
5. **AI-ready architecture**: metadata/classification/audit được thiết kế ngay từ đầu để về sau tích hợp LLM hoặc AIOps.
6. **Tự quản lý nội bộ**: phù hợp bối cảnh doanh nghiệp muốn on-premise/private cloud thay vì đưa tài liệu lên SaaS bên ngoài.

Cách viết vào báo cáo:

> So với các nền tảng DMS/ECM phổ biến, DocVault chưa hướng đến độ đầy đủ thương mại như Box, SharePoint/Microsoft Purview, M-Files hay OpenText. Tuy nhiên, điểm khác biệt của DocVault nằm ở cách tiếp cận DevSecOps-first và GitOps-native: hệ thống không chỉ bảo vệ tài liệu ở tầng ứng dụng mà còn kiểm soát toàn bộ vòng đời phát triển, triển khai và vận hành bằng security gates, Policy as Code, private registry, audit trail và observability. Đây là nền tảng để mở rộng sang AI-driven classification, LLM remediation và AIOps self-healing.

---

## 1.4. Kiến trúc web app DocVault nên chốt

### 1.4.1. Service boundaries

| Service | Trách nhiệm chính |
|---|---|
| API Gateway | Routing, JWT verification, rate limit, centralized audit wrapper |
| IAM/Keycloak | Login, OIDC/OAuth2, user, role, group, token, MFA |
| Document Service | Upload/download file, versioning blob, checksum, MinIO interaction |
| Metadata Service | Source-of-truth cho metadata, ACL, classification, status, version pointer |
| Workflow Service | State machine, submit/approve/reject, update status |
| Audit Service | Append-only audit event, query theo time/user/action/result, hash-chain |
| Notification Service | Email/Slack/log notification cho submitted/approved/rejected/security-deny |

Nguyên tắc quan trọng:

> **Document Service không tự quyết quyền download. Metadata/Gateway mới là nơi policy check.**

Lý do: Document Service chỉ nên xử lý blob/file. Metadata Service giữ ACL, classification, status và policy attributes. Điều này làm kiến trúc rõ hơn và giống hệ thống doanh nghiệp hơn.

---

## 1.5. RBAC và access control trong web app

### 1.5.1. Role tối thiểu

| Role | Quyền chính |
|---|---|
| Viewer | Xem metadata, download tài liệu Published nếu ACL cho phép |
| Editor | Tạo document, upload version, sửa metadata, submit workflow |
| Approver | Approve/reject tài liệu Pending |
| Compliance Officer | Xem audit log, xem metadata tùy chính sách, **không được download nội dung file** |

### 1.5.2. Rule đặc biệt cần demo

Rule quan trọng nhất:

```text
Compliance Officer có quyền kiểm toán nhưng không có quyền xem nội dung tài liệu.
```

Demo cần có:

```text
co1 -> GET /audit/query -> 200 OK
co1 -> POST /documents/:docId/download-authorize -> 403 Forbidden
co1 -> gọi direct download/presigned URL không hợp lệ -> 403 Forbidden
```

### 1.5.3. RBAC + ACL + status + classification

Không nên chỉ kiểm tra role. Nên kết hợp:

```text
allow_download(user, document) khi:
  document.status == PUBLISHED
  AND user.role IN {viewer, editor, approver}
  AND ACL cho phép user/role/group download
  AND classification policy không chặn
  AND user.role != compliance_officer
```

Ví dụ rule:

| Document | Viewer | Editor | Approver | Compliance Officer |
|---|---|---|---|---|
| Draft | Không download | Owner có thể xem/upload | Không download | Không download |
| Pending | Không download | Owner có thể xem | Approver xem metadata | Không download |
| Published + public | Download nếu ACL allow | Download nếu ACL allow | Download nếu ACL allow | Không download |
| Published + confidential | Chỉ nhóm được chỉ định | Chỉ owner/nhóm | Chỉ nếu policy allow | Không download |

---

## 1.6. Hash trong web app: file integrity và audit integrity

Thầy có nhắc keyword “hash”. Với DocVault, nên triển khai hai loại hash.

### 1.6.1. SHA-256 checksum cho file/version

Mỗi lần upload file:

```text
Client upload file
-> Document Service nhận multipart file
-> Tính SHA-256(file_content)
-> Lưu file vào MinIO: doc/{docId}/v{version}/{filename}
-> Lưu checksum, size, objectKey vào Metadata Service
-> Ghi audit event DOCUMENT_UPLOADED
```

Metadata version nên có:

```json
{
  "docId": "...",
  "version": 1,
  "objectKey": "doc/abc/v1/contract.pdf",
  "checksum": "sha256:...",
  "size": 123456,
  "createdBy": "editor1",
  "createdAt": "..."
}
```

Mục đích:

| Mục đích | Ý nghĩa |
|---|---|
| Integrity | Biết file có bị thay đổi không |
| Versioning | Mỗi version có hash riêng |
| Audit evidence | Chứng minh file được approve/download là version nào |
| Demo | Download file rồi tính lại SHA-256 để chứng minh khớp |

### 1.6.2. Hash-chain cho audit log

Audit log không chỉ ghi “ai làm gì”, mà nên có cơ chế phát hiện bị sửa.

Mỗi audit event:

```json
{
  "eventId": "evt-001",
  "timestamp": "2026-...",
  "actorId": "editor1",
  "action": "DOCUMENT_SUBMITTED",
  "resourceId": "doc-001",
  "result": "SUCCESS",
  "prevHash": "hash-cua-event-truoc",
  "hash": "sha256(canonical_event_json + prevHash)"
}
```

Nếu một event cũ bị sửa, hash của event đó và các event sau sẽ sai. Đây là cơ chế **tamper-evident audit log**.

MVP đủ dùng:

```text
- MongoDB/PostgreSQL lưu audit_events
- Mỗi event có prevHash + hash
- Có endpoint internal verify audit chain
- Demo: sửa thử một event trong DB -> verify báo chain invalid
```

---

## 1.7. Secret key và key rotation trong web app

Phần này đặt ở **web app/runtime**, không đặt thành yêu cầu chính trong pipeline.

### 1.7.1. Các loại secret/key trong web app

| Secret/key | Dùng cho |
|---|---|
| JWT signing key của Keycloak | Ký access token/ID token |
| DB password | Metadata/Audit service kết nối DB |
| MinIO access key/secret key | Document Service truy cập object storage |
| Encryption key/KMS key | Mã hóa file hoặc metadata nhạy cảm |
| API token Slack/SMTP | Notification Service gửi cảnh báo |
| Internal service token | Service-to-service nếu cần |

Không được lưu các giá trị này trong:

```text
source code
Dockerfile
image layer
plain Helm values
ConfigMap
Git repo
log pipeline
frontend code
```

### 1.7.2. Công cụ đề xuất cho secret/key management web app

| Mức | Công cụ | Vai trò |
|---|---|---|
| MVP | Kubernetes Secret | Lưu secret khi chạy trên K8s |
| GitOps-safe | Sealed Secrets | Mã hóa secret để có thể commit lên Git |
| Tốt hơn | External Secrets Operator | Đồng bộ secret từ Vault/AWS Secrets Manager/Azure Key Vault/GCP Secret Manager vào K8s |
| Enterprise-like | HashiCorp Vault | Dynamic secrets, database credentials có TTL, secret rotation |
| IAM | Keycloak | Quản lý signing keys/JWKS, token/session, MFA |

### 1.7.3. Key rotation nên hiểu như thế nào?

“Secret key tự động thay đổi sau một khoảng thời gian” có thể triển khai ở ba chỗ chính:

#### A. JWT signing key rotation trong Keycloak

Luồng:

```text
Keycloak có active signing key
Gateway verify JWT qua JWKS endpoint
Định kỳ rotate key
Key cũ chuyển sang passive để token cũ vẫn verify được tới khi hết hạn
Sau thời gian an toàn, remove key cũ
```

Khuyến nghị cho DocVault:

```text
Access token TTL: 5-15 phút
Refresh token TTL: dài hơn nhưng có rotation nếu cấu hình được
Gateway verify: issuer, audience, exp, nbf, signature qua JWKS
MFA/OTP: bật cho user quan trọng như approver/compliance officer
```

#### B. Database credential rotation bằng Vault

MVP có thể dùng DB password cố định trong Kubernetes Secret. Bản nâng cao dùng Vault:

```text
Service cần DB credential
-> Vault cấp username/password ngắn hạn có TTL
-> Hết TTL thì credential bị revoke/rotate
-> Service lấy credential mới
```

Lợi ích:

| Lợi ích | Giải thích |
|---|---|
| Giảm rủi ro lộ password | Credential hết hạn sau thời gian ngắn |
| Audit tốt hơn | Biết service nào lấy credential lúc nào |
| Least privilege | Mỗi service có role DB riêng |

#### C. Encryption key rotation cho file/metadata

MVP:

```text
MinIO SSE-S3 hoặc SSE với key nội bộ
```

Advanced:

```text
Dùng SSE-KMS/Vault Transit/KMS
Mỗi object/version lưu keyVersion
Khi rotate key, object mới dùng key mới
Object cũ vẫn decrypt bằng keyVersion cũ
```

Không nên cố re-encrypt toàn bộ dữ liệu nếu không đủ thời gian. Chỉ cần thiết kế metadata có `encryptionKeyVersion` để chứng minh hướng mở rộng.

### 1.7.4. Cách demo key rotation ở mức đồ án

MVP demo khả thi:

```text
1. Cấu hình Keycloak access token TTL ngắn.
2. Gateway verify JWT qua JWKS.
3. Token hết hạn -> API trả 401.
4. Role không đủ quyền -> API trả 403.
5. Thử rotate realm key trong Keycloak hoặc mô tả bằng sequence + ảnh cấu hình.
6. Secret DB/MinIO nằm trong Kubernetes Secret/ExternalSecret, không nằm trong code.
```

Nếu có thời gian:

```text
- Dùng External Secrets Operator + Vault dev mode.
- Demo thay đổi secret trong Vault -> ESO sync về Kubernetes Secret.
- Rollout restart service để nhận secret mới.
```

---

## 1.8. Encryption, presigned URL và download security

### 1.8.1. Encryption

| Layer | Đề xuất MVP | Advanced |
|---|---|---|
| In transit | TLS ở ingress | Istio mTLS service-to-service |
| At rest file | MinIO SSE AES-256 | SSE-KMS/Vault/KMS |
| Metadata DB | PVC/storage encryption mô phỏng | Cloud-managed encryption/KMS |
| Client-side | Không bắt buộc | E2EE future work |

### 1.8.2. Download grant token

Không nên để user gọi MinIO trực tiếp nếu chưa qua policy.

Luồng đúng:

```text
Client -> Metadata Service: xin download authorization
Metadata Service -> check role + ACL + status + classification
Nếu deny -> 403 + audit DOWNLOAD_DENIED
Nếu allow -> cấp grantToken ngắn hạn
Client/Gateway -> Document Service: tạo presigned URL hoặc stream
Document Service -> verify grantToken
Document Service -> trả file/presigned URL ngắn hạn
```

Grant token nên có:

```json
{
  "docId": "...",
  "version": 1,
  "objectKey": "...",
  "actorId": "viewer1",
  "expiresAt": "...",
  "nonce": "..."
}
```

Yêu cầu:

```text
- Grant token TTL ngắn: 1-5 phút
- Presigned URL TTL ngắn: 1-5 phút
- Không cache presigned URL ở frontend lâu
- Không log presigned URL đầy đủ
- Direct download nếu không qua grantToken phải fail
```

---

## 1.9. Content classification, DLP và malware scanning

### 1.9.1. Classification metadata

Thêm field:

```text
classification: public | internal | confidential | restricted
```

Rule gợi ý:

| Classification | Rule |
|---|---|
| public | Viewer download nếu Published và ACL allow |
| internal | Chỉ user nội bộ có role phù hợp |
| confidential | Cần ACL explicit hoặc group cụ thể |
| restricted | Chỉ owner/approver đặc biệt; download cần audit high severity |

### 1.9.2. DLP pattern detection

MVP đơn giản:

```text
- Extract text từ PDF/DOCX nếu có thể
- Regex tìm email, số điện thoại, mã định danh demo
- Nếu match -> gợi ý classification = confidential
- Ghi audit DLP_CLASSIFICATION_SUGGESTED
```

Công cụ có thể tham khảo:

| Công cụ | Vai trò |
|---|---|
| Apache Tika | Extract text/metadata từ PDF, DOCX, XLSX |
| Presidio | Detect PII patterns |
| OpenDLP | Tham khảo DLP open-source |
| Regex/Zod custom | MVP nhanh cho demo |

### 1.9.3. Malware scanning khi upload

MVP nên có:

```text
Client upload file
-> Document Service lưu tạm
-> ClamAV scan
-> Nếu clean: lưu MinIO + tạo version
-> Nếu infected: reject + audit UPLOAD_BLOCKED_MALWARE
```

Công cụ:

| Công cụ | Vai trò |
|---|---|
| ClamAV | Malware scan open-source cho file upload |
| ICAP server | Mô hình enterprise scan gateway nếu nâng cao |
| YARA rules | Detect pattern nâng cao, optional |

---

## 1.10. AI tích hợp vào web app: nên để mức nào?

AI trong web app nên chia làm hai mức.

### 1.10.1. MVP/Good: AI hỗ trợ metadata và tìm kiếm

| Use case | Mô tả |
|---|---|
| Auto-tagging | Gợi ý tag từ title/content |
| Classification suggestion | Gợi ý public/internal/confidential |
| Document summary | Tóm tắt nội dung tài liệu mà user có quyền xem |
| Semantic search | Tìm tài liệu theo ngữ nghĩa |
| Audit summary | Compliance Officer hỏi “tuần này ai download tài liệu confidential?” |

Điều kiện bắt buộc:

```text
AI chỉ được đọc tài liệu mà user hiện tại có quyền đọc.
Nếu Compliance Officer không được download/xem nội dung, AI cũng không được dùng nội dung đó để trả lời.
```

### 1.10.2. Future work: AI security assistant

| Use case | Mô tả |
|---|---|
| Risk scoring | Chấm điểm rủi ro tài liệu theo classification + download frequency |
| Insider threat hint | Cảnh báo hành vi bất thường |
| Policy explanation | Giải thích vì sao user bị deny download |
| Workflow recommendation | Gợi ý approver phù hợp |

---

## 1.11. Checklist Web App nên làm sau góp ý của thầy

### MVP bắt buộc

```text
[ ] Keycloak OIDC login hoạt động
[ ] Gateway verify JWT issuer/audience/exp/signature
[ ] RBAC: viewer/editor/approver/compliance_officer
[ ] Compliance Officer xem audit được nhưng download bị deny
[ ] Metadata Service là source-of-truth cho ACL/status/classification/version
[ ] Document Service upload/download qua MinIO
[ ] Mỗi file version có SHA-256 checksum
[ ] Workflow Draft -> Pending -> Published/Rejected
[ ] Audit log đủ event: login/upload/download/deny/submit/approve/reject
[ ] Không lộ presigned URL nếu chưa policy allow
[ ] Secret không hard-code trong code/Dockerfile/frontend
[ ] Kubernetes Secret hoặc ExternalSecret cho DB/MinIO/JWT-related config
```

### Nên bổ sung để giống enterprise DMS hơn

```text
[ ] Classification label: public/internal/confidential/restricted
[ ] Policy download theo classification
[ ] Hash-chain audit log
[ ] Malware scanning bằng ClamAV khi upload
[ ] Key rotation mô phỏng: JWT signing key hoặc DB/MinIO secret update
[ ] MFA/OTP trong Keycloak cho approver/compliance officer
[ ] Retention policy mô phỏng 90 ngày hoặc theo classification
[ ] Admin/Security dashboard cho deny events và suspicious downloads
```

### Optional/future work

```text
[ ] DLP pattern detection bằng Apache Tika + Presidio/regex
[ ] Watermark khi preview/download tài liệu confidential
[ ] Client-side encryption/E2EE
[ ] AI auto-tagging/classification/summarization theo RBAC
[ ] Ransomware-like behavior detection từ audit/version events
```

---

# PHẦN II. PIPELINE DEVSECOPS: BUILD, SCAN, POLICY, GITOPS VÀ TRIỂN KHAI AN TOÀN

## 2.1. Cách hiểu đúng về pipeline theo góp ý của thầy

Pipeline không chỉ là “build image rồi deploy”. Pipeline phải chứng minh được:

1. Code được test.
2. Code được scan bảo mật.
3. Dependency được scan CVE.
4. Container image được scan trước khi deploy.
5. Terraform/Kubernetes manifest được scan.
6. Policy as Code kiểm tra cấu hình trước khi deploy.
7. Artifact/image được lưu ở registry nội bộ.
8. ArgoCD triển khai dev/production từ Git.
9. Mỗi môi trường có namespace, values, secret và policy riêng.
10. RBAC áp dụng cho Jenkins, ArgoCD, Kubernetes service account và registry.

Cách nói ngắn:

> Pipeline DevSecOps là nơi áp dụng Policy as Code, quality gates và least privilege để ngăn lỗi bảo mật trước khi ứng dụng đi vào Kubernetes.

---

## 2.2. Pipeline đề xuất end-to-end

```text
Developer push code
-> Jenkins/GitLab CI trigger
-> Unit Test + Coverage
-> SAST
-> SCA
-> Build Docker image
-> Container image scan
-> IaC scan
-> Policy as Code gate
-> Generate SBOM/sign image (optional)
-> Push image to Harbor/Nexus
-> Update Helm/Kustomize manifest in GitOps repo
-> ArgoCD sync dev namespace
-> Smoke test dev
-> Manual approval / protected branch
-> ArgoCD sync production namespace
-> Monitoring + logging + audit evidence
```

---

## 2.3. Policy as Code trong pipeline

Đây là phần thầy nhấn mạnh. Policy as Code không chỉ là web app authorization. Trong pipeline, Policy as Code dùng để kiểm tra file cấu hình trước deploy.

### 2.3.1. Những file nên kiểm tra

```text
Dockerfile
Kubernetes YAML
Helm chart
values-dev.yaml
values-prod.yaml
Kustomize overlays
Terraform files
ArgoCD Application manifest
Jenkinsfile/GitLab CI config nếu có thể
```

### 2.3.2. Rule nên viết

| Nhóm | Policy rule |
|---|---|
| Container | Không chạy root, không privileged, không dùng latest tag |
| Kubernetes | Bắt buộc runAsNonRoot, resource requests/limits, readOnlyRootFilesystem nếu có thể |
| Secret | Không hard-code password/token trong YAML/values |
| Network | Không expose service không cần thiết; production ingress dùng TLS |
| Terraform | Không mở SSH/RDP 0.0.0.0/0; bắt buộc encryption/logging |
| ArgoCD | Production chỉ sync từ branch/tag được phép |
| Image source | Chỉ cho phép image từ registry nội bộ Harbor/Nexus |

### 2.3.3. Công cụ đề xuất

| Công cụ | Dùng ở đâu | Vai trò |
|---|---|---|
| OPA + Conftest | CI pipeline | Test Dockerfile/K8s/Terraform/YAML bằng Rego policy |
| Checkov | CI pipeline | Scan Terraform, Kubernetes, Helm, Dockerfile, CI config |
| Terrascan | CI pipeline | Scan IaC thay thế/bổ sung Checkov |
| Kyverno CLI | CI pipeline | Test Kubernetes policy trước khi apply |
| Gatekeeper | Kubernetes admission | Chặn manifest vi phạm policy khi vào cluster |
| Kyverno | Kubernetes admission | Policy engine Kubernetes-native, viết rule bằng YAML |

### 2.3.4. Hai lớp chặn policy

```text
Lớp 1: CI Policy Gate
- Fail sớm khi pull request/commit vi phạm policy.

Lớp 2: Kubernetes Admission Policy
- Nếu có manifest vi phạm lọt qua pipeline, Gatekeeper/Kyverno chặn khi apply vào cluster.
```

Cách viết vào báo cáo:

> Policy as Code được áp dụng trong pipeline như một quality gate bắt buộc. Các rule bảo mật được viết thành mã để tự động kiểm tra Dockerfile, Kubernetes manifest, Helm values, Terraform và ArgoCD Application. Nếu phát hiện cấu hình chạy privileged, thiếu resource limit, hard-code secret, mở port nguy hiểm hoặc dùng image tag latest, pipeline sẽ fail và không cho triển khai.

---

## 2.4. RBAC trong pipeline, Kubernetes và registry

Bạn nghe đúng: RBAC không chỉ ở web app. Pipeline cũng cần RBAC.

### 2.4.1. RBAC trong pipeline

| Actor | Quyền nên có | Không nên có |
|---|---|---|
| Developer | Push code, mở merge request | Không deploy thẳng production |
| Jenkins build job | Pull code, test, build image | Không có cluster-admin |
| Jenkins scan job | Đọc source/image để scan | Không cần deploy |
| Jenkins image publish job | Push image vào đúng project registry | Không push tùy tiện vào repo khác |
| Jenkins manifest update job | Update GitOps repo dev | Không tự ý update prod nếu chưa approval |
| Release manager | Approve production promotion | Không cần đọc runtime secrets |

### 2.4.2. RBAC trong Kubernetes

Mỗi service/controller nên có ServiceAccount riêng:

```text
docvault-gateway-sa
docvault-metadata-sa
docvault-document-sa
docvault-workflow-sa
docvault-audit-sa
docvault-notification-sa
jenkins-deployer-sa
argocd-dev-sa
argocd-prod-sa
external-secrets-sa
```

Nguyên tắc:

```text
- Không dùng cluster-admin cho tất cả.
- ArgoCD dev chỉ sync namespace dev.
- ArgoCD prod chỉ sync namespace prod.
- Service này không được đọc Secret của service khác.
- Document Service chỉ đọc MinIO secret, không đọc DB secret của service khác.
- Metadata Service chỉ đọc DB secret của nó.
```

### 2.4.3. RBAC trong Harbor/Nexus

| Role | Quyền |
|---|---|
| Developer | Pull image dev, không push production |
| CI bot | Push image vào project docvault-dev hoặc docvault-build |
| Release bot | Promote/copy image sang production project |
| ArgoCD | Pull image từ registry |
| Security/admin | Xem scan report, quản lý policy |

Điểm demo:

```text
- Jenkins push image bằng robot account.
- ArgoCD pull image bằng imagePullSecret riêng.
- Không dùng Docker Hub public làm nơi chính cho image production.
```

---

## 2.5. Artifact registry: Harbor, Nexus, Artifactory

Thầy có nhắc Docker Hub, Nexus, Harbor và artifactory. Ý chính là không nên phụ thuộc vào Docker Hub như nơi duy nhất để lưu image/artifact.

### 2.5.1. Vì sao cần artifact repository nội bộ?

| Vấn đề nếu chỉ dùng Docker Hub/Internet | Lợi ích khi có registry/artifact repo nội bộ |
|---|---|
| Phụ thuộc Internet | Build/deploy ổn định hơn |
| Pull dependency/image lặp lại mất thời gian | Cache artifact, tăng tốc pipeline |
| Khó kiểm soát image được phép chạy | Enforce chỉ deploy image từ registry nội bộ |
| Khó quản lý vulnerability/signing/promotion | Scan, sign, promote dev -> prod rõ ràng |
| Không phù hợp hệ thống nội bộ doanh nghiệp | Doanh nghiệp tự quản lý artifact |

### 2.5.2. Harbor

Phù hợp nhất nếu trọng tâm là container/Kubernetes.

Nên dùng Harbor cho:

```text
- Private container image registry
- Image vulnerability scanning
- Project-level RBAC
- Image retention policy
- Image promotion dev -> production
- Optional: image signing, replication
```

Luồng:

```text
Jenkins build image
-> Trivy scan
-> Push image to Harbor project docvault-dev
-> ArgoCD dev deploy image từ Harbor
-> Sau approval, promote image sang docvault-prod
-> ArgoCD prod deploy image đã promote
```

### 2.5.3. Nexus Repository

Phù hợp nếu cần repository tổng quát cho nhiều loại artifact.

Nên dùng Nexus cho:

```text
- npm proxy/cache cho frontend
- Maven/Gradle proxy nếu có Java
- Docker registry/proxy
- Raw artifact storage
- Dependency cache để build nhanh hơn
```

### 2.5.4. Artifactory

JFrog Artifactory phù hợp enterprise nhưng có thể nặng cho đồ án.

Dùng khi muốn trình bày hướng production:

```text
- Universal artifact repository
- Docker/npm/Maven/PyPI/Helm artifacts
- Build info, promotion, retention, Xray scan nếu có license
```

### 2.5.5. Đề xuất chọn cho DocVault

| Mục tiêu | Công cụ nên chọn |
|---|---|
| Làm nhanh, đúng Kubernetes | Harbor |
| Cần cache npm/Maven/Docker | Nexus |
| Enterprise reference | Artifactory |
| MVP thực tế | Harbor cho image + npm cache tùy chọn bằng Nexus nếu kịp |

Khuyến nghị cho đồ án:

> Dùng **Harbor** làm private container registry chính. Nếu muốn trả lời tốt câu hỏi “artifact/dependency cache nằm ở đâu”, trình bày thêm **Nexus** như repository manager cho npm/Maven/Docker proxy.

---

## 2.6. ArgoCD multi-environment: dev và production

Thầy yêu cầu ít nhất hai môi trường: dev và production.

### 2.6.1. Cấu trúc Helm gợi ý

```text
deploy/
  helm/docvault/
    Chart.yaml
    values.yaml
    values-dev.yaml
    values-prod.yaml
    templates/
      gateway.yaml
      metadata.yaml
      document.yaml
      workflow.yaml
      audit.yaml
      notification.yaml
      configmap.yaml
      secret-ref.yaml
      ingress.yaml
  argocd/
    app-dev.yaml
    app-prod.yaml
```

### 2.6.2. Cấu trúc Kustomize gợi ý

```text
k8s/
  base/
    deployment.yaml
    service.yaml
    ingress.yaml
  overlays/
    dev/
      namespace.yaml
      kustomization.yaml
      patch-values.yaml
    prod/
      namespace.yaml
      kustomization.yaml
      patch-values.yaml
```

### 2.6.3. Khác biệt dev/prod

| Thành phần | Dev | Production |
|---|---|---|
| Namespace | docvault-dev | docvault-prod |
| Values file | values-dev.yaml | values-prod.yaml |
| Replica | 1 | 2-3 hoặc theo HPA |
| Resource limits | thấp | rõ ràng và cao hơn |
| Ingress | dev-docvault.local | docvault.company.local |
| Secret | secret dev | secret prod riêng |
| DB/MinIO bucket | dev riêng | prod riêng |
| Policy | có thể warning một số rule | enforce nghiêm |
| Deploy | auto sync | manual approval hoặc protected branch |

### 2.6.4. Cách ArgoCD hiểu môi trường

Mỗi môi trường là một ArgoCD Application riêng:

```yaml
# app-dev.yaml
source:
  path: deploy/helm/docvault
  helm:
    valueFiles:
      - values-dev.yaml
destination:
  namespace: docvault-dev
```

```yaml
# app-prod.yaml
source:
  path: deploy/helm/docvault
  helm:
    valueFiles:
      - values-prod.yaml
destination:
  namespace: docvault-prod
```

Ý chính:

> Cùng một Helm chart/Kustomize base, nhưng mỗi môi trường có file values/overlay riêng. ArgoCD đọc cấu hình tương ứng và sync vào namespace tương ứng.

---

## 2.7. ConfigMap, Secret và quản lý credential trên Kubernetes

### 2.7.1. Phân biệt ConfigMap và Secret

| Loại | Dùng cho | Ví dụ |
|---|---|---|
| ConfigMap | Cấu hình không nhạy cảm | APP_ENV, LOG_LEVEL, SERVICE_URL |
| Secret | Dữ liệu nhạy cảm | DB_PASSWORD, MINIO_SECRET_KEY, SMTP_TOKEN |

Không lưu secret trong ConfigMap.

### 2.7.2. GitOps-safe secret

| Mức | Công cụ | Cách dùng |
|---|---|---|
| MVP | Kubernetes Secret tạo thủ công | Không commit secret thật |
| Tốt hơn | Sealed Secrets | Commit secret đã mã hóa vào Git |
| Enterprise-like | External Secrets Operator | Secret thật nằm ở Vault/AWS/Azure/GCP, K8s chỉ sync |
| Advanced | HashiCorp Vault | Dynamic secret, lease, TTL, audit |

### 2.7.3. Lưu ý về key rotation

Ở tài liệu này, key rotation được đặt ở phần **web app/runtime**. Trong pipeline chỉ cần đảm bảo:

```text
- Không hard-code secret.
- Không in secret ra log.
- Secret dev/prod tách riêng.
- Pipeline bot chỉ truy cập secret cần thiết.
- Production secret không cho developer thường đọc.
```

---

## 2.8. Storage trên Kubernetes: dữ liệu nằm ở đâu?

Thầy hỏi “volume nằm ở đâu” là câu rất quan trọng. Trong Kubernetes, Pod có thể bị reschedule sang node khác. Nếu dữ liệu chỉ nằm trong container filesystem thì sẽ mất. Vì vậy cần dùng PV/PVC/StorageClass.

### 2.8.1. Thành phần cần persistent storage

| Thành phần | Cần PVC không? | Lý do |
|---|---|---|
| PostgreSQL | Có | Metadata, ACL, status, version pointer |
| MongoDB/Elasticsearch audit | Có | Audit log |
| MinIO | Có | File/blob tài liệu |
| Harbor/Nexus | Có | Image/artifact repository |
| Prometheus | Có nếu muốn giữ metric | Observability |
| Loki | Có nếu muốn giữ log | Log truy vết |

### 2.8.2. Công nghệ storage đề xuất

| Mức | Công nghệ | Khi nào dùng |
|---|---|---|
| Local demo | local-path provisioner | Một node, dễ chạy |
| Lab nhiều node | Longhorn | Dễ demo distributed block storage |
| Production-like | Rook-Ceph | Mạnh nhưng phức tạp |
| Cloud | AWS EBS/EFS | Nếu dùng EKS |
| Đơn giản nhiều node | NFS | Dễ nhưng không nên xem là production-grade cho mọi workload |

### 2.8.3. Cách trình bày trong báo cáo

> Các thành phần có trạng thái như PostgreSQL, MinIO, Audit DB và Harbor/Nexus không lưu dữ liệu trong container filesystem mà sử dụng PersistentVolumeClaim. Ở môi trường demo có thể dùng local-path hoặc Longhorn; ở hướng production-like có thể dùng Rook-Ceph hoặc cloud-managed storage. Điều này bảo đảm dữ liệu vẫn tồn tại khi Pod bị restart hoặc reschedule sang node khác.

---

## 2.9. Security gates trong pipeline

| Stage | Công cụ | Gate gợi ý |
|---|---|---|
| Unit test | Jest/JUnit/Pytest | 0 failed tests, coverage target 70-80% |
| SAST | SonarQube | 0 blocker/critical, Security Rating A nếu cấu hình được |
| SCA | OWASP Dependency Check/Snyk/Trivy fs | 0 direct dependency CVSS >= 7 |
| Secret scan | Gitleaks/TruffleHog | 0 secret leaked |
| Build image | Docker/BuildKit | Multi-stage build, không copy .env |
| Container scan | Trivy/Grype | 0 Critical |
| IaC scan | Checkov/Terrascan | 0 deny violation |
| Policy as Code | OPA Conftest/Kyverno CLI | 0 policy deny |
| DAST | OWASP ZAP | Không có SQLi/XSS critical ở endpoint chính |
| Registry | Harbor/Nexus | Image đã scan, tag immutable nếu có thể |
| GitOps deploy | ArgoCD | Sync đúng namespace, rollback được |

---

## 2.10. Evidence demo cho pipeline

Thầy/hội đồng thường không chỉ nghe mô tả, mà cần bằng chứng.

### 2.10.1. Demo fail/pass pipeline

Chuẩn bị hai commit:

```text
Commit lỗi:
- Dockerfile chạy USER root
- K8s manifest thiếu resource limits
- Terraform mở SSH 0.0.0.0/0
- Dependency có CVE hoặc image có critical CVE

Pipeline phải fail.
```

```text
Commit fix:
- Thêm USER nonroot
- Thêm resources requests/limits
- Sửa security group
- Đổi dependency/image version

Pipeline phải pass.
```

### 2.10.2. Demo ArgoCD dev/prod

```text
- app-dev sync vào namespace docvault-dev bằng values-dev.yaml
- app-prod sync vào namespace docvault-prod bằng values-prod.yaml
- Dev tự động sync
- Production cần approval hoặc merge vào branch/tag release
- Rollback bằng ArgoCD history hoặc revert Git commit
```

### 2.10.3. Demo registry nội bộ

```text
- Jenkins build image docvault/metadata-service:git-sha
- Push vào Harbor/Nexus
- Trivy/Harbor scan report hiển thị kết quả
- ArgoCD deploy image từ registry nội bộ
- Kubernetes policy chỉ cho phép image từ registry nội bộ
```

### 2.10.4. Demo RBAC pipeline/Kubernetes

```text
- Jenkins không có cluster-admin
- ArgoCD dev không sync được namespace prod
- ServiceAccount metadata-service không đọc được secret của document-service
- Production deploy cần approval
```

---

## 2.11. AI/AIOps và hướng bài báo liên quan pipeline

Phần AI nên tách rõ khỏi MVP nếu thời gian ngắn. Có hai hướng:

### 2.11.1. Bài báo: LLM Auto-Remediation cho IaC

Luồng:

```text
Terraform/K8s manifest lỗi
-> Checkov/Conftest phát hiện violation
-> LLM sinh patch
-> Chạy lại terraform validate + checkov/conftest
-> Nếu fail, đưa log lỗi lại cho LLM
-> Lặp tối đa 5 vòng
-> Pass thì tạo PR
```

Policy constraint:

```text
- Cấm checkov:skip
- Cấm disable rule
- Cấm xóa resource chỉ để pass tool
- Không phá chức năng chính
```

Metrics:

```text
Fix success rate
Pass@1/Pass@3/Pass@5
Iteration-to-fix
Time-to-fix
Tool-bypass rate
Regression rate
```

### 2.11.2. Khóa luận: AIOps post-deploy

Input:

```text
Prometheus metrics: RPS, 5xx rate, P95 latency, CPU, memory
Loki logs
Audit events
```

Model:

```text
Isolation Forest hoặc rule-based baseline
```

Action:

```text
notify -> scale up -> restart -> rollback -> human approval
```

Guardrails:

```text
max replicas <= 5
cooldown 5-10 phút
kill switch
human approval cho action rủi ro cao
OPA/Kyverno policy cho runtime action nếu làm nâng cao
```

---

## 2.12. Checklist Pipeline DevSecOps nên làm sau góp ý của thầy

### MVP bắt buộc

```text
[ ] Jenkinsfile/GitLab CI có stage test, build, scan
[ ] SAST bằng SonarQube
[ ] SCA bằng OWASP Dependency Check/Snyk/Trivy fs
[ ] Container scan bằng Trivy
[ ] IaC scan bằng Checkov/Terrascan
[ ] Secret scan bằng Gitleaks hoặc TruffleHog
[ ] Policy as Code gate bằng OPA Conftest hoặc Kyverno CLI
[ ] Docker image push vào Harbor hoặc Nexus thay vì Docker Hub làm registry chính
[ ] ArgoCD deploy bằng Helm/Kustomize
[ ] Có dev namespace và production namespace riêng
[ ] Có values-dev.yaml và values-prod.yaml
[ ] ConfigMap/Secret tách theo môi trường
[ ] ServiceAccount riêng cho Jenkins/ArgoCD/service runtime
[ ] Pipeline có bằng chứng fail/pass
[ ] ArgoCD có bằng chứng sync/rollback
```

### Nên bổ sung để thuyết phục hơn

```text
[ ] Harbor project RBAC + robot account
[ ] Image tag immutable hoặc tag theo git SHA
[ ] Policy chỉ cho phép image từ Harbor/Nexus
[ ] Gatekeeper/Kyverno admission controller trong cluster
[ ] Longhorn/local-path PVC cho PostgreSQL, MinIO, Harbor/Nexus
[ ] Prometheus/Grafana/Loki dashboard tối thiểu
[ ] SBOM bằng Syft hoặc Trivy
[ ] Image signing bằng Cosign nếu kịp
[ ] Manual approval trước deploy production
```

### Optional/future work

```text
[ ] Nexus cache npm/Maven/Docker dependencies
[ ] Artifactory làm enterprise reference
[ ] Vault + External Secrets Operator cho secret runtime nâng cao
[ ] LLM auto-remediation cho IaC làm paper
[ ] AIOps anomaly detection + guardrails làm khóa luận
```

---

# 3. Bảng đề xuất công cụ theo chủ đề thầy nhắc

| Chủ đề | Công cụ thầy có nhắc / nên dùng | Đề xuất cho DocVault |
|---|---|---|
| IAM/SSO | Keycloak | Dùng Keycloak OIDC/OAuth2, role, group, MFA |
| Web app RBAC | Keycloak roles + backend guard | Enforce ở Gateway/Metadata, không chỉ frontend |
| Hash file | SHA-256 | Tính checksum cho từng version file |
| Audit integrity | SHA-256 hash-chain | Append-only audit + prevHash/hash |
| Secret key/key rotation web app | Keycloak keys, Vault, ESO | MVP: Keycloak + K8s Secret; Advanced: Vault/ESO |
| Object storage | MinIO | S3-compatible storage, SSE AES-256 |
| Malware scanning | ClamAV | Scan file upload trước khi lưu chính thức |
| DLP/classification | Apache Tika, Presidio, regex | MVP: classification manual + regex; future: AI/DLP |
| CI | Jenkins | Pipeline as Code |
| SAST | SonarQube | Quality gate code security |
| SCA | OWASP Dependency Check, Snyk, Trivy fs | Scan dependency CVE |
| Container scan | Trivy, Grype | Gate 0 critical CVE |
| IaC scan | Checkov, Terrascan | Scan Terraform/K8s/Helm |
| Policy as Code CI | OPA Conftest, Kyverno CLI | Fail pipeline nếu manifest vi phạm policy |
| K8s admission policy | Gatekeeper, Kyverno | Chặn privileged/root/no limit/latest tag |
| GitOps | ArgoCD | Dev/prod app riêng, Helm/Kustomize values riêng |
| Manifest template | Helm, Kustomize | Chọn một trong hai; Helm dễ values dev/prod |
| Artifact registry | Harbor, Nexus, Artifactory | MVP: Harbor; optional: Nexus cache dependency |
| Persistent storage | PV/PVC/StorageClass | PostgreSQL, MinIO, Audit DB, Harbor/Nexus |
| Storage engine | local-path, Longhorn, Rook-Ceph, EBS/EFS | Demo: local-path/Longhorn; production-like: Rook/EBS |
| Observability | Prometheus, Grafana, Loki | Metrics dashboard + logs |
| DAST | OWASP ZAP | Scan API theo OpenAPI |
| SBOM/signing | Syft, Cosign | Optional nhưng rất tốt |
| AIOps | Prometheus + Isolation Forest + Chaos Mesh | Khóa luận/future work |

---

# 4. Đoạn tổng hợp có thể đưa thẳng vào báo cáo

Sau buổi góp ý, nhóm xác định cần chia định hướng hoàn thiện DocVault thành hai lớp chính: lớp ứng dụng quản lý tài liệu bảo mật và lớp pipeline DevSecOps triển khai an toàn.

Ở lớp web app, DocVault không chỉ thực hiện upload/download tài liệu mà cần tiệm cận mô hình hệ thống quản lý tài liệu doanh nghiệp. Hệ thống cần có quản lý metadata, versioning, workflow phê duyệt, RBAC/ACL, classification, checksum SHA-256 cho từng phiên bản tài liệu, audit trail đầy đủ và có thể bổ sung hash-chain để phát hiện chỉnh sửa log. Các secret key như JWT signing key, database password, MinIO credential và encryption key không được hard-code trong mã nguồn hoặc manifest. Ở mức MVP, nhóm sử dụng Kubernetes Secret/Sealed Secrets; ở mức nâng cao có thể dùng External Secrets Operator hoặc HashiCorp Vault để hỗ trợ secret lifecycle và key rotation. Key rotation được xem là một phần của bảo mật runtime/web app, ví dụ rotation JWT signing key trong Keycloak hoặc dynamic database credentials bằng Vault.

Ở lớp pipeline DevSecOps, Policy as Code cần được áp dụng như một security gate bắt buộc, không chỉ là cơ chế phân quyền trong web app. Pipeline cần kiểm tra Dockerfile, Kubernetes manifest, Helm/Kustomize values, Terraform và ArgoCD Application bằng các công cụ như Checkov, Terrascan, OPA Conftest hoặc Kyverno CLI. Các rule cần chặn bao gồm container chạy privileged/root, thiếu resource limits, dùng image tag latest, hard-code secret hoặc mở port nguy hiểm. Sau khi pass các quality gates, image được push vào registry nội bộ như Harbor hoặc Nexus thay vì phụ thuộc trực tiếp vào Docker Hub. ArgoCD triển khai ứng dụng lên ít nhất hai môi trường dev và production, mỗi môi trường có namespace, values file, ConfigMap, Secret và RBAC riêng.

So với các nền tảng quản lý tài liệu phổ biến như Box Shield, Microsoft Purview/SharePoint, M-Files, OpenText, Alfresco, Nextcloud và Egnyte, DocVault nên học hỏi các năng lực như content classification, DLP, audit trail, records retention, malware scanning, least-privilege access, encryption và ransomware-oriented monitoring. Tuy nhiên, điểm khác biệt có thể nhấn mạnh của DocVault là hướng tiếp cận DevSecOps-first và GitOps-native: hệ thống không chỉ bảo vệ tài liệu ở tầng ứng dụng mà còn kiểm soát toàn bộ vòng đời phát triển, đóng gói, triển khai và vận hành bằng security gates, Policy as Code, private registry, audit trail và observability. Đây là nền tảng tốt để mở rộng sang LLM auto-remediation cho IaC và AIOps self-healing trong giai đoạn nghiên cứu tiếp theo.

---

# 5. Kết luận thực thi

Nếu cần làm nhanh nhưng vẫn đúng trọng tâm thầy góp ý, thứ tự ưu tiên nên là:

## Web App

```text
1. Hoàn thiện RBAC + workflow + audit E2E.
2. Thêm classification cho tài liệu.
3. Tính SHA-256 checksum cho file version.
4. Làm audit hash-chain nếu kịp.
5. Đảm bảo secret không hard-code.
6. Mô tả hoặc demo key rotation ở Keycloak/Vault mức phù hợp.
7. Thêm malware scan hoặc ít nhất thiết kế rõ trong báo cáo.
```

## Pipeline DevSecOps

```text
1. Tách dev/prod namespace và values file.
2. Dùng ArgoCD sync theo Helm/Kustomize.
3. Thêm Policy as Code gate trong pipeline.
4. Dùng Harbor làm private registry.
5. Scan SAST/SCA/Container/IaC/Secret.
6. RBAC riêng cho Jenkins/ArgoCD/Kubernetes ServiceAccount.
7. Có demo fail/pass pipeline và rollback GitOps.
```

Một câu chốt để dùng khi trình bày với thầy:

> DocVault được định hướng thành một nền tảng quản lý tài liệu bảo mật nội bộ theo mô hình cloud-native. Phần web app tập trung vào bảo vệ tài liệu, phân quyền, kiểm toán, hash integrity và key lifecycle; phần pipeline DevSecOps tập trung vào security gates, Policy as Code, private artifact registry, GitOps multi-environment và RBAC hạ tầng. Sự kết hợp này giúp DocVault khác với một web quản lý tài liệu thông thường và tạo nền tảng cho bài báo/khóa luận về LLM remediation và AIOps.

---

# 6. Tài liệu/công cụ tham khảo

## Công cụ quản lý tài liệu / ECM / DMS tham khảo

1. Box Shield: https://www.box.com/shield
2. Microsoft Purview sensitivity labels and DLP: https://learn.microsoft.com/en-us/purview/dlp-sensitivity-label-as-condition
3. M-Files Platform: https://www.m-files.com/m-files-platform/
4. OpenText Content Management / Extended ECM: https://www.opentext.com/products/content-management
5. Nextcloud End-to-End Encryption: https://docs.nextcloud.com/server/latest/user_manual/en/files/using_e2ee.html
6. Alfresco Governance Services: https://docs.alfresco.com/governance-services/latest/
7. Egnyte Ransomware Detection and Recovery: https://www.egnyte.com/products/ransomware-detection

## Công cụ DevSecOps/GitOps/Policy tham khảo

1. ArgoCD Helm values: https://argo-cd.readthedocs.io/en/stable/user-guide/helm/
2. OPA Conftest: https://www.conftest.dev/
3. Harbor vulnerability scanning: https://goharbor.io/docs/2.0.0/administration/vulnerability-scanning/
4. Sonatype Nexus Docker registry: https://help.sonatype.com/en/docker-registry.html
5. External Secrets Operator: https://external-secrets.io/latest/
6. Kubernetes Secret: https://kubernetes.io/docs/concepts/configuration/secret/
7. HashiCorp Vault dynamic database credentials: https://developer.hashicorp.com/vault/tutorials/db-credentials/database-secrets
8. Keycloak keys/key rotation reference: https://github.com/keycloak/keycloak/blob/main/docs/documentation/server_admin/topics/realms/keys.adoc
