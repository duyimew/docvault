# DocVault — Gateway Endpoint Table & Response DTOs

> Base URL: `http://localhost:3000/api`
> All requests must include header: `Authorization: Bearer <keycloak_jwt>`
> Detail/history/comments/ACL endpoints are role-gated and then policy-filtered by document status, classification, ownership, and ACL.
> ACL `DENY` overrides `ALLOW`. `GROUP` ACL `subjectId` values match normalized Keycloak group names, for example `/finance-team` is evaluated as `finance-team`.

**Compliance Officer rule:** `compliance_officer` can inspect allowed metadata and audit evidence, including hash-chain verification, but cannot preview, stream, presign, or download file content.

---

## 1. Documents — Metadata

### GET `/metadata/documents` — Document List
**Roles:** viewer, editor, approver, compliance_officer, admin

**Query params:** _(none currently, sorted by createdAt desc)_

**Response `200`:**
```jsonc
[
  {
    "id": "uuid",
    "title": "string",
    "description": "string | null",
    "ownerId": "string",
    "classification": "PUBLIC | INTERNAL | CONFIDENTIAL | SECRET",
    "dlpStatus": "NOT_SCANNED | CLEAR | DETECTED",
    "dlpFindings": "array | null",
    "dlpDetectedAt": "ISO8601 | null",
    "retentionClass": "PUBLIC_730D | INTERNAL_365D | CONFIDENTIAL_180D | SECRET_90D | null",
    "retentionUntil": "ISO8601 | null",
    "retentionReason": "string | null",
    "tags": ["string"],
    "status": "DRAFT | PENDING | PUBLISHED | ARCHIVED",
    "currentVersion": 0,
    "publishedAt": "ISO8601 | null",
    "archivedAt": "ISO8601 | null",
    "createdAt": "ISO8601",
    "updatedAt": "ISO8601"
  }
]
```

---

### GET `/metadata/documents/:docId` — Document Detail
**Roles:** viewer, editor, approver, compliance_officer, admin _(policy-filtered)_

**Response `200`:**
```jsonc
{
  "id": "uuid",
  "title": "string",
  "description": "string | null",
  "ownerId": "string",
  "classification": "PUBLIC | INTERNAL | CONFIDENTIAL | SECRET",
  "dlpStatus": "NOT_SCANNED | CLEAR | DETECTED",
  "dlpFindings": "array | null",
  "dlpDetectedAt": "ISO8601 | null",
  "retentionClass": "PUBLIC_730D | INTERNAL_365D | CONFIDENTIAL_180D | SECRET_90D | null",
  "retentionUntil": "ISO8601 | null",
  "retentionReason": "string | null",
  "tags": ["string"],
  "status": "DRAFT | PENDING | PUBLISHED | ARCHIVED",
  "currentVersion": 1,
  "publishedAt": "ISO8601 | null",
  "archivedAt": "ISO8601 | null",
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601",
  "versions": [
    {
      "id": "uuid",
      "docId": "uuid",
      "version": 1,
      "objectKey": "doc/{docId}/v1/filename.pdf",
      "checksum": "string",
      "size": 102400,
      "filename": "filename.pdf",
      "contentType": "application/pdf | null",
      "dlpStatus": "NOT_SCANNED | CLEAR | DETECTED",
      "dlpFindings": "array | null",
      "createdAt": "ISO8601",
      "createdBy": "string"
    }
  ],
  "aclEntries": [
    {
      "id": "uuid",
      "docId": "uuid",
      "subjectType": "USER | ROLE | GROUP | ALL",
      "subjectId": "string | null", // userId, roleName, normalized groupName, or null for ALL
      "permission": "READ | DOWNLOAD | WRITE | APPROVE",
      "effect": "ALLOW | DENY",
      "createdAt": "ISO8601"
    }
  ]
}
```

**Response `403`:** Authenticated user is not allowed to read this document's metadata.

---

### POST `/metadata/documents` — Create New Document
**Roles:** editor, admin

**Request body:**
```jsonc
{
  "title": "string",          // required
  "description": "string",     // optional
  "classification": "PUBLIC | INTERNAL | CONFIDENTIAL | SECRET", // optional, default INTERNAL
  "tags": ["string"]          // optional, max 50 tags
}
```

**Response `201`:** (Document object — same as list item, tags sanitized)

---

### PATCH `/metadata/documents/:docId` — Update Metadata
**Roles:** editor (must be owner), admin

**Request body:** _(all fields optional)_
```jsonc
{
  "title": "string",
  "description": "string",
  "classification": "PUBLIC | INTERNAL | CONFIDENTIAL | SECRET",
  "tags": ["string"]
}
```

**Response `200`:** Updated Document object

**Response `403`:** DLP-detected documents cannot be downgraded below `CONFIDENTIAL`.

---

### GET `/metadata/documents/:docId/workflow-history` — Workflow History
**Roles:** viewer, editor, approver, compliance_officer, admin _(policy-filtered)_

**Response `200`:**
```jsonc
[
  {
    "id": "uuid",
    "docId": "uuid",
    "fromStatus": "DRAFT | PENDING | PUBLISHED",
    "toStatus": "PENDING | PUBLISHED | ARCHIVED | DRAFT",
    "action": "SUBMIT | APPROVE | REJECT | ARCHIVE | RETENTION",
    "actorId": "string",
    "reason": "string | null",
    "createdAt": "ISO8601"
  }
]
```

**Response `403`:** Authenticated user is not allowed to read this document's metadata.

---

### POST `/metadata/documents/:docId/acl` — Upsert ACL Rule
**Roles:** editor, admin

**Request body:**
```jsonc
{
  "subjectType": "USER | ROLE | GROUP | ALL",
  "subjectId": "string",   // userId / roleName / normalized Keycloak groupName (null if ALL)
  "permission": "READ | DOWNLOAD | WRITE | APPROVE",
  "effect": "ALLOW | DENY"
}
```

**Response `200 | 201`:** ACL Entry object

---

### GET `/metadata/documents/:docId/acl` — View ACL List
**Roles:** editor, approver, compliance_officer, admin _(policy-filtered)_

**Response `200`:** Array of ACL Entry objects

**Response `403`:** Authenticated user is not allowed to read this document's metadata, or does not have an ACL-readable role.

---

### POST `/metadata/documents/:docId/download-authorize` — Request Download Permission
**Roles:** viewer, editor, approver, admin _(compliance_officer denied)_

**Request body:** _(optional)_
```jsonc
{
  "version": 1   // optional, defaults to currentVersion
}
```

**Response `200`:**
```jsonc
{
  "docId": "uuid",
  "version": 1,
  "objectKey": "doc/{docId}/v1/filename.pdf",
  "filename": "filename.pdf",
  "contentType": "application/pdf | null",
  "expiresInSeconds": 300,
  "expiresAt": "ISO8601",
  "grantToken": "base64url.signature"
}
```
> **FE uses this `grantToken` to call `/documents/:docId/presign-download` or `/stream`.**

**Response `403`:** When compliance_officer calls, or document is not yet PUBLISHED:
```jsonc
{ "statusCode": 403, "message": "Only published documents can be downloaded" }
```

---

### POST `/metadata/documents/:docId/preview-authorize` — Request Preview Permission
**Roles:** viewer, editor, approver, admin _(compliance_officer denied)_

**Request body:** _(optional)_
```jsonc
{
  "version": 1   // optional, defaults to currentVersion
}
```

**Response `200`:**
```jsonc
{
  "docId": "uuid",
  "version": 1,
  "objectKey": "doc/{docId}/v1/filename.pdf",
  "filename": "filename.pdf",
  "contentType": "application/pdf | null",
  "expiresInSeconds": 300,
  "expiresAt": "ISO8601",
  "classification": "PUBLIC | INTERNAL | CONFIDENTIAL | SECRET",
  "grantToken": "base64url.signature"
}
```

**Response `403`:** Compliance officers are denied because preview is file content access, or ACL/classification policy denies READ.

---

## 2. Retention / Records Management

### GET `/metadata/retention/documents` — Retention Evidence
**Roles:** compliance_officer, admin

**Query params:**
| Param | Type | Description |
|---|---|---|
| `asOf` | ISO8601 | Optional demo clock for deterministic status calculation |

**Response `200`:**
```jsonc
{
  "checkedAt": "ISO8601",
  "summary": {
    "tracked": 4,
    "active": 2,
    "dueSoon": 1,
    "overdue": 0,
    "archived": 1
  },
  "records": [
    {
      "docId": "uuid",
      "title": "string",
      "status": "PUBLISHED | ARCHIVED",
      "classification": "PUBLIC | INTERNAL | CONFIDENTIAL | SECRET",
      "publishedAt": "ISO8601 | null",
      "archivedAt": "ISO8601 | null",
      "retentionClass": "SECRET_90D",
      "retentionUntil": "ISO8601 | null",
      "retentionReason": "SECRET records are retained for 90 days after publication",
      "retentionStatus": "ACTIVE | DUE_SOON | OVERDUE | ARCHIVED | UNSET",
      "daysRemaining": 12
    }
  ]
}
```

### POST `/metadata/retention/run` — Run Retention Job
**Roles:** admin

**Query params:**
| Param | Type | Description |
|---|---|---|
| `asOf` | ISO8601 | Optional demo clock. Records due on or before this time are archived. |

**Response `200`:**
```jsonc
{
  "archived": 1,
  "skipped": 0,
  "checkedAt": "ISO8601"
}
```

Due records transition to `ARCHIVED`, receive workflow history `RETENTION` by `system:retention`, and emit audit event `DOCUMENT_AUTO_ARCHIVED`.

---

## 3. Documents — Blob (MinIO)

### POST `/documents/:docId/upload` — Upload File
**Roles:** editor, admin
**Content-Type:** `multipart/form-data`

**Form field:** `file` — binary file

**Response `201`:**
```jsonc
{
  "docId": "uuid",
  "version": 1,
  "filename": "filename.pdf",
  "size": 102400,
  "checksum": "sha256hex",
  "objectKey": "doc/{docId}/v1/filename.pdf",
  "contentType": "application/pdf",
  "dlpStatus": "CLEAR | DETECTED",
  "dlpFindings": []
}
```

Upload security behavior:

- EICAR malware test payloads return `400` before object storage or metadata version creation.
- DLP findings are persisted on the version and parent document.
- DLP-detected `PUBLIC`/`INTERNAL` documents are escalated to `CONFIDENTIAL`.

---

### POST `/documents/:docId/presign-download` — Get Presigned URL
**Roles:** viewer, editor, approver, admin _(compliance_officer denied)_

**Request body:**
```jsonc
{
  "grantToken": "base64url.signature",   // optional, from download-authorize
  "version": 1                           // optional
}
```
If `grantToken` is omitted, document-service re-authorizes through metadata-service before issuing a presigned URL.

**Response `200` for non-watermarked documents:**
```jsonc
{
  "url": "https://minio.example.com/docvault/doc/{docId}/v1/file.pdf?X-Amz-...",
  "expiresAt": "ISO8601"
}
```

**Response `200` for `CONFIDENTIAL` / `SECRET` documents:**
```jsonc
{
  "url": null,
  "watermarkRequired": true,
  "streamingEndpoint": "/documents/{docId}/versions/{version}/stream",
  "expiresAt": "ISO8601"
}
```

Sensitive documents do not expose a direct presigned URL; the frontend should use the stream endpoint so document-service can apply the controlled download/watermark path.

---

### GET `/documents/:docId/versions/:version/stream` — Stream File Directly
**Roles:** viewer, editor, approver, admin _(compliance_officer denied)_

**Query params:**
| Param | Type | Description |
|---|---|---|
| `token` | string | Optional download grant token. If omitted, document-service re-authorizes through metadata-service before streaming. |

**Response headers:**
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="file.pdf"`

**Response `200`:** Binary stream (file)

---

### GET `/documents/:docId/preview` — Stream Inline Preview
**Roles:** viewer, editor, approver, admin _(compliance_officer denied)_

**Query params:**
| Param | Type | Description |
|---|---|---|
| `version` | int | Optional specific version, defaults to current version |

**Response `200 | 206`:** Binary stream for inline browser preview.

**Response `403`:** Compliance officers are denied, or metadata preview policy denies READ.

---

## 4. Workflow

### POST `/workflow/:docId/submit` — Submit (DRAFT → PENDING)
**Roles:** editor, admin

**Response `200`:** Document object with `status: "PENDING"`

---

### POST `/workflow/:docId/approve` — Approve (PENDING → PUBLISHED)
**Roles:** approver, admin

**Response `200`:** Document object with `status: "PUBLISHED"`, `publishedAt: "ISO8601"`, `retentionClass`, and `retentionUntil`

---

### POST `/workflow/:docId/reject` — Reject (PENDING → DRAFT)
**Roles:** approver, admin

**Request body:**
```jsonc
{
  "reason": "string"   // optional, reason for rejection
}
```

**Response `200`:** Document object with `status: "DRAFT"`

---

### POST `/workflow/:docId/archive` — Archive (PUBLISHED → ARCHIVED)
**Roles:** editor owner, admin

**Response `200`:** Document object with `status: "ARCHIVED"`, `archivedAt: "ISO8601"`

---

## 5. Audit

### GET `/audit/query` — Query Audit Logs
**Roles:** compliance_officer, admin

**Query params:**
| Param | Type | Description |
|---|---|---|
| `actorId` | string | Filter by performing user |
| `action` | string | Filter by action (e.g. `DOCUMENT_SUBMIT`) |
| `resourceType` | string | Filter by resource type (e.g. `DOCUMENT`) |
| `resourceId` | string | Document UUID |
| `result` | string | `SUCCESS` or `DENY` |
| `from` | ISO8601 | Start time |
| `to` | ISO8601 | End time |
| `limit` | int (1-200) | Number of records, default 100 |

**Response `200`:**
```jsonc
[
  {
    "eventId": "uuid",
    "timestamp": "ISO8601",
    "actorId": "string",
    "actorRoles": ["viewer", "editor"],
    "action": "DOCUMENT_CREATED | DOCUMENT_SUBMIT | DOCUMENT_APPROVE | DOCUMENT_ARCHIVE | DOCUMENT_AUTO_ARCHIVED | DOCUMENT_DOWNLOAD_AUTHORIZED | ...",
    "resourceType": "DOCUMENT",
    "resourceId": "uuid",
    "result": "SUCCESS | DENY",
    "reason": "string | null",
    "ip": "string | null",
    "traceId": "string | null",
    "prevHash": "hex | null",
    "hash": "hex"
  }
]
```

---

### GET `/audit/verify-chain` — Verify Audit Hash Chain
**Roles:** compliance_officer, admin

**Query params:**
| Param | Type | Description |
|---|---|---|
| `limit` | int (1-5000) | Number of recent events to verify, default 1000 |

**Response `200`:**
```jsonc
{
  "valid": true,
  "checked": 125,
  "epochId": "epoch-active",
  "activeEpoch": {
    "epochId": "epoch-active",
    "status": "ACTIVE",
    "valid": true,
    "checked": 125
  },
  "historicalCompromisedCount": 1,
  "compromisedEpochs": [
    {
      "epochId": "epoch-old",
      "status": "COMPROMISED",
      "incidentId": "AUDIT-INC-...",
      "firstBrokenIndex": 3,
      "lastTrustedHash": "hex"
    }
  ]
}
```

Hash-chain verification is tamper-evident: if stored audit events are edited directly, verification returns `valid: false` with broken-link details.

---

### POST `/audit/chain/seal-and-start-epoch` — Seal Compromised Audit Epoch
**Roles:** compliance_officer, admin

Production-safe recovery action for an unrecoverably invalid audit chain. It marks the active epoch as `COMPROMISED`, records an incident, starts a new `ACTIVE` epoch, and appends `AUDIT_CHAIN_EPOCH_STARTED`. It does **not** recompute or rewrite historical hashes.

**Body:**
```jsonc
{
  "reason": "Incident reviewed; trusted restore unavailable."
}
```

**Response `200`:**
```jsonc
{
  "incident": { "incidentId": "AUDIT-INC-..." },
  "previousEpoch": { "epochId": "epoch-old", "status": "COMPROMISED" },
  "newEpoch": { "epochId": "epoch-new", "status": "ACTIVE" },
  "event": { "action": "AUDIT_CHAIN_EPOCH_STARTED" }
}
```

---

### GET `/audit/security-summary` — Security Evidence Summary
**Roles:** compliance_officer, admin

**Response `200`:**
```jsonc
{
  "chain": {
    "valid": true,
    "checked": 125
  },
  "totals": {
    "deniedEvents": 7,
    "malwareBlocked": 1,
    "dlpDetections": 1,
    "downloadDenied": 4
  },
  "repeatedDenyActors": [
    { "actorId": "viewer-1", "denyCount": 5 }
  ]
}
```

---

## Grant Token Runtime

- Download and preview grants use separate HMAC secrets: `DOWNLOAD_GRANT_SECRET` and `PREVIEW_GRANT_SECRET`.
- Both secrets are required in metadata-service and document-service. There is no hard-coded fallback secret.
- Grant TTL is 300 seconds.
- Zero-downtime rotation uses `GRANT_TOKEN_CURRENT_KID`, `GRANT_TOKEN_PREVIOUS_KID`, and kid-specific env vars such as `DOWNLOAD_GRANT_SECRET_2026_05` and `PREVIEW_GRANT_SECRET_2026_05`.
- Metadata-service signs with the current `kid`; document-service accepts current or previous `kid`. If no current `kid` is configured, legacy single-secret dev mode is used.
- Download grants authorize presign/stream download. Preview grants authorize inline preview only.

## Audit Ingestion Boundary

- User JWT identifies the actor for normal API calls.
- Only trusted services may append audit events through `POST /audit/events`, using `x-docvault-service-token` backed by `AUDIT_INGEST_TOKEN`.
- Normal viewer/editor/approver/compliance user JWTs cannot create audit events directly.

---

## Endpoint Summary Table

| Method | Gateway path | Roles | Description |
|---|---|---|---|
| GET | `/metadata/documents` | all | Document list |
| POST | `/metadata/documents` | editor, admin | Create new doc |
| GET | `/metadata/documents/:docId` | policy-filtered all | Doc detail + versions + ACL |
| PATCH | `/metadata/documents/:docId` | editor (owner), admin | Edit metadata |
| GET | `/metadata/documents/:docId/workflow-history` | policy-filtered all | Workflow history |
| POST | `/metadata/documents/:docId/acl` | editor, admin | Upsert ACL rule |
| GET | `/metadata/documents/:docId/acl` | policy-filtered editor, approver, CO, admin | View ACL |
| POST | `/metadata/documents/:docId/download-authorize` | viewer, editor, approver, admin | Request download grant token |
| POST | `/metadata/documents/:docId/preview-authorize` | viewer, editor, approver, admin | Request preview grant token |
| GET | `/metadata/retention/documents` | compliance_officer, admin | Retention evidence |
| POST | `/metadata/retention/run` | admin | Run retention auto-archive |
| POST | `/documents/:docId/upload` | editor, admin | Upload file |
| POST | `/documents/:docId/presign-download` | viewer, editor, approver, admin | Presigned URL |
| GET | `/documents/:docId/versions/:version/stream` | viewer, editor, approver, admin | Stream file |
| GET | `/documents/:docId/preview` | viewer, editor, approver, admin | Inline preview stream |
| POST | `/workflow/:docId/submit` | editor, admin | DRAFT → PENDING |
| POST | `/workflow/:docId/approve` | approver, admin | PENDING → PUBLISHED |
| POST | `/workflow/:docId/reject` | approver, admin | PENDING → DRAFT |
| POST | `/workflow/:docId/archive` | editor owner, admin | PUBLISHED → ARCHIVED |
| GET | `/audit/query` | compliance_officer, admin | Query audit log |
| GET | `/audit/verify-chain` | compliance_officer, admin | Verify audit hash chain |
| GET | `/audit/security-summary` | compliance_officer, admin | Security evidence summary |
| POST | `/audit/events` | internal service token | Append audit event |

---

## Error Response Format

```jsonc
{
  "statusCode": 400 | 401 | 403 | 404 | 409,
  "message": "string or string[]",
  "error": "string"
}
```

| Code | Situation |
|---|---|
| 401 | Missing / invalid / expired JWT |
| 403 | Wrong role or ACL denied |
| 404 | DocId does not exist |
| 409 | Conflict (e.g. approving an already published doc) |
| 400 | Invalid request body |
