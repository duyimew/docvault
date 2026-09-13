# DocVault Web Security Evidence

Scope: Web/runtime improvements for DocVault only. This evidence intentionally excludes CI/CD, deployment pipeline, cluster policy, image registry, and other infrastructure pipeline topics.

## Security Rules Implemented

### Compliance Officer

- Can query audit events through `GET /audit/query`.
- Can verify the audit hash chain through `GET /audit/verify-chain`.
- Can export document-scoped compliance evidence packets through `GET /metadata/documents/:docId/evidence-packet`.
- Can inspect metadata only when document policy allows it.
- Cannot preview, stream, presign, or download file content.

Enforced in:

- `apps/web/src/lib/auth/permissions.ts`
- `services/gateway/src/proxy/metadata.proxy.controller.ts`
- `services/gateway/src/proxy/documents.proxy.controller.ts`
- `services/metadata-service/src/policy/policy.service.ts`
- `services/document-service/src/documents/documents.controller.ts`

### Metadata Access Boundary

Document detail, workflow history, comments, and ACL list now share `PolicyService.assertCanReadMetadata(...)` before returning document-related data.

ACL matching supports `USER`, `ROLE`, `GROUP`, and `ALL` subjects. Group ACLs use normalized Keycloak group names, so a token claim such as `/finance-team` is matched as `finance-team`; matching `DENY` entries override baseline role/classification visibility and explicit `ALLOW` entries.

Covered routes:

- `GET /metadata/documents/:docId`
- `GET /metadata/documents/:docId/workflow-history`
- `GET /metadata/documents/:docId/comments`
- `GET /metadata/documents/:docId/acl`
- `PUT /metadata/documents/:docId/comments/:commentId`
- `DELETE /metadata/documents/:docId/comments/:commentId`

Denied metadata reads emit `DOCUMENT_METADATA_READ_DENIED`.

Comment update/delete also validates the route `docId` and `commentId` as a pair before mutation. A comment author cannot update or delete their previous comment after document visibility is revoked, and a guessed comment id under the wrong document id returns not found before mutation.

### Frontend Permission and Contract Alignment

- Web action visibility is centralized in `apps/web/src/lib/auth/permissions.ts`.
- Permission decisions can return a short denial reason through `getDocumentAccessDecision(...)`, so UI paths can explain policy outcomes without duplicating rules.
- Document list/detail surfaces denied Download actions as disabled controls with policy reasons when the current UI context has enough policy evidence. List rows intentionally suppress ACL-dependent reasons when ACL context is absent; detail views and backend authorization remain authoritative. The preview dialog keeps its fallback download button disabled when download policy denies file content access.
- Download UI now mirrors backend policy more closely: compliance officers are blocked, status must be `PUBLISHED`, a version must exist, matching `DOWNLOAD DENY` ACL wins when ACL data is available, and classification rules require ownership or explicit ACL allow for `CONFIDENTIAL` / `SECRET`.
- Preview UI now blocks compliance officers, deleted/no-version documents, and matching `READ DENY` ACL. Approver preview keeps the backend's classification bypass after ACL deny checks.
- Metadata-detail helper models the backend read boundary for owner, explicit `READ ALLOW`, admin, compliance, approver, status, and classification cases.
- Production frontend code now consumes canonical `classification` and `currentVersion` fields instead of the legacy `classificationLevel` / `currentVersionNumber` aliases.
- Backend policy remains authoritative. Frontend helpers are UI hints; list rows may not carry full ACL context, so protected operations still rely on metadata/document service authorization.
- Regression coverage lives in `apps/web/src/lib/auth/permissions.spec.ts` and runs with Vitest.

### Shared Auth and Contract Cleanup

- Non-gateway services now keep their local `roles.decorator.ts` / `roles.guard.ts` compatibility wrappers, but re-export `Roles`, `ROLES_KEY`, and `RolesGuard` from `@docvault/auth/rbac`.
- JWT strategies remain service-local for now because runtime behavior still differs: gateway supports the `dv_access_token` cookie path, while downstream services use service-specific JWT validation tolerance.
- `libs/contracts/openapi/gateway.yaml` is aligned with runtime routes for document comments, draft workflow delete, users batch lookup, classification enum refs, security summary risk scoring, behavior anomaly signals, AI guardrails, and access impact preview.
- OpenAPI YAML parsing is covered with an offline `js-yaml` parse check.

### Grant Token Secrets

- Download grants require `DOWNLOAD_GRANT_SECRET`.
- Preview grants require `PREVIEW_GRANT_SECRET`.
- There is no hard-coded fallback secret.
- Grant TTL is 300 seconds.
- Zero-downtime rotation is supported with `GRANT_TOKEN_CURRENT_KID`, `GRANT_TOKEN_PREVIOUS_KID`, and kid-specific secrets such as `DOWNLOAD_GRANT_SECRET_2026_05` / `PREVIEW_GRANT_SECRET_2026_05`.
- Metadata-service signs new grants with the current `kid`; document-service accepts only current or previous `kid` values.

Legacy single-secret dev mode is still supported when `GRANT_TOKEN_CURRENT_KID` is not set.

Rotation runbook: `docs/runbooks/web-key-rotation-and-mfa-runbook.md`.

### MFA Demo Posture

- The Keycloak realm enables the `CONFIGURE_TOTP` required action.
- `co-mfa-demo` and `admin-mfa-demo` are dedicated MFA demo users and require OTP enrollment on first interactive login.
- `co1` and `admin1` intentionally remain non-MFA automation users so password-grant smoke tests and E2E evidence can run without manual OTP enrollment.
- Production posture should require MFA for all human admin and compliance officer accounts while keeping non-human automation on separate service accounts.

Runbook: `docs/runbooks/web-key-rotation-and-mfa-runbook.md`.

### Download Posture and At-Rest Evidence

- MinIO bucket initialization enables SSE-S3 for the local bucket in `infra/minio/init.sh`.
- Published `PUBLIC`/`INTERNAL` documents can receive a short-lived presigned URL after metadata-service authorizes download.
- `CONFIDENTIAL` and `SECRET` downloads are marked `watermarkRequired`.
- When `watermarkRequired=true`, document-service returns `url: null` and a `streamingEndpoint` instead of a direct presigned URL.
- The stream path re-authorizes or verifies the grant token, reads from MinIO, and applies watermarking before returning content.
- Compliance officer remains blocked from preview, stream, presign, and download regardless of metadata visibility.

### Upload Malware and DLP Controls

- Document-service scans uploads before MinIO writes.
- EICAR test payloads are rejected with `MALWARE_UPLOAD_BLOCKED`.
- Malware scanning supports `local-eicar` deterministic demo mode and an optional `clamav` daemon-backed mode via `MALWARE_SCANNER_MODE=clamav`.
- ClamAV mode defaults to fail-closed when the daemon is unavailable; local development can explicitly set `MALWARE_SCANNER_FAILURE_POLICY=fail-open`.
- Blocked malware uploads do not create a MinIO object and do not register a metadata version.
- DLP scan detects email, phone/national-id-like values, and sensitive keywords such as `secret`, `confidential`, and `internal only`.
- DLP detections emit `DLP_PATTERN_DETECTED`, persist DLP state on metadata/version records, and escalate `PUBLIC`/`INTERNAL` documents to `CONFIDENTIAL`.
- DLP-detected documents cannot be downgraded below `CONFIDENTIAL` by non-admin users; denied downgrade emits `DLP_CLASSIFICATION_DOWNGRADE_DENIED`.
- Admin downgrade override to `PUBLIC` / `INTERNAL` requires `classificationOverrideReason`; missing reasons are denied and approved overrides emit `DLP_CLASSIFICATION_OVERRIDE_APPROVED` with the audited reason.
- Web document detail shows DLP status, suggested classification, scan source, detection time, and finding category/count/severity without exposing raw matched sensitive values.

### Audit Ingestion Boundary

- User JWT identifies the actor for normal API requests.
- Audit event ingestion requires `x-docvault-service-token`.
- `AUDIT_INGEST_TOKEN` must match in the emitting service, gateway proxy, and audit-service.
- A normal viewer/editor/approver/compliance JWT cannot append fake audit events.

### Audit Hash-Chain Evidence

- Audit-service exposes `GET /audit/verify-chain`.
- Audit-service exposes `GET /audit/security-summary`.
- Gateway proxies it to `GET /api/audit/verify-chain`.
- Gateway proxies security summary to `GET /api/audit/security-summary`.
- Web Audit page has a Verify Chain action, valid/invalid status, and security cards for deny, malware, DLP, and download-denied counters.
- Web Security page at `/security` gives compliance/admin a dashboard for audit-chain posture, deny/download-deny/malware/DLP counters, high-volume download grants, sensitive preview/download grants, alert cards, repeated deny actors, and recent DENY/DLP events.
- Security dashboard quick investigations deep-link into Audit filters for `DENY`, `ERROR`, `DOCUMENT_DOWNLOAD_DENIED`, `DLP_PATTERN_DETECTED`, `DOCUMENT_DOWNLOAD_AUTHORIZED`, and `DOCUMENT_PREVIEW_AUTHORIZED`.
- Security dashboard now includes deterministic document risk scoring from audit metadata only. It scores recent preview/download authorization events by classification, access frequency, distinct actors, and download grants, then deep-links each risky document to Audit with `documentId` filtering.
- Security dashboard now includes deterministic behavior anomaly signals from audit metadata only. It flags ransomware-oriented patterns such as mass content access, denied-access bursts, and destructive document activity, then deep-links each actor signal to Audit with `actorId` filtering.
- Security dashboard now includes a deterministic Security Recommendation Engine. It converts audit-chain failures, DLP detections, malware blocks, risky document activity, and behavior anomaly signals into prioritized recommended actions with evidence and Audit deep-links; it uses audit metadata only and does not expose file content, object keys, presigned URLs, preview grants, or download grants.
- Viewing security recommendations emits `SECURITY_RECOMMENDATIONS_VIEWED` with recommendation ids/counts/types and audit filters only, so recommendation access is auditable without storing file content or grant data.
- The hash chain is tamper-evident, not immutable storage. If an old audit event is edited directly in storage, verification should return `valid: false`.
- Local/dev tamper demo script: `pnpm --filter audit-service audit:tamper-demo -- --dry-run` previews the target event, and `pnpm --filter audit-service audit:tamper-demo -- --apply` mutates one non-head event after `DOCVAULT_ALLOW_AUDIT_TAMPER_DEMO=true` is set.
- The tamper demo refuses to run against non-local MongoDB hosts unless `DOCVAULT_ALLOW_NONLOCAL_AUDIT_TAMPER_DEMO=true` is also set.
- Audit query supports `documentId` filtering for direct `DOCUMENT` events and related events carrying `metadata.docId`, such as comment audit events.

### Retention / Records Management Evidence

- Metadata-service stores `retentionClass`, `retentionUntil`, and `retentionReason` on published records.
- Approval stamps retention evidence from classification policy:
  - `PUBLIC_730D`
  - `INTERNAL_365D`
  - `CONFIDENTIAL_180D`
  - `SECRET_90D`
- Compliance/admin can inspect retention evidence through `GET /metadata/retention/documents`.
- Admin can trigger the local demo retention run through `POST /metadata/retention/run`.
- Due records are auto-archived as `ARCHIVED`.
- Retention archive writes workflow history with `action=RETENTION` and `actorId=system:retention`.
- Retention archive emits audit event `DOCUMENT_AUTO_ARCHIVED`.
- Web Retention page shows tracked, due soon, overdue, and archived record counts.

### One-Click Compliance Evidence Packet

- Compliance/admin can export a document-scoped JSON evidence packet from the document detail action panel.
- Gateway endpoint: `GET /metadata/documents/:docId/evidence-packet`.
- The packet includes document metadata, version checksums, ACL entries, workflow history, retention evidence, audit hash-chain verification status at generation time, and related audit events.
- Related audit events are fetched with `documentId=:docId`, so both direct document audit events and comment events with `metadata.docId` are included.
- The packet intentionally excludes file content, presigned URLs, preview grants, and download grant tokens. Compliance officers remain blocked from file content access.
- Viewer/editor/approver roles cannot call the packet endpoint unless they also have compliance/admin role.

### AI-Ready Guardrails

- Metadata-service exposes `GET /metadata/documents/:docId/ai-guardrails`.
- Gateway proxies it at the same path.
- The endpoint first applies `PolicyService.assertCanReadMetadata(...)`; users who cannot read metadata do not receive AI context decisions.
- The response separates metadata-safe operations (`METADATA_CLASSIFICATION`, `METADATA_TAGGING`) from content operations (`CONTENT_SUMMARIZATION`, `CONTENT_QA`).
- Compliance officers remain metadata-only for AI; content summarization and Q&A are denied because they would require file content access.
- The response intentionally excludes file content, object keys, presigned URLs, preview grants, and download grants.
- A successful guardrail evaluation emits `AI_GUARDRAILS_EVALUATED`.
- Web document detail shows an AI guardrails card beside the DLP evidence so future AI features have visible RBAC/ACL/content boundaries before any LLM integration.

### Policy Simulation and Access Impact

- Metadata-service exposes `POST /documents/:docId/access-impact`.
- Gateway proxies it at `POST /metadata/documents/:docId/access-impact` for owner editors and admins.
- The endpoint simulates baseline role impact for a proposed classification change before metadata is mutated.
- The response shows current/proposed classification, watermark posture, access expansion/reduction warnings, DLP override requirements, and role-level metadata/download deltas.
- The response intentionally excludes real user enumeration, file content, object keys, presigned URLs, preview grants, and download grants.
- A successful simulation emits `DOCUMENT_ACCESS_IMPACT_SIMULATED`.
- Web document edit shows an access impact preview when the selected classification differs from the current classification, so risky downgrades are visible before submit. Backend authorization and DLP override enforcement remain authoritative.

## Demo Evidence Matrix

The root E2E script covers:

- No token rejected.
- Expired token rejected.
- Viewer create denied.
- Editor create/upload/submit allowed.
- Approver approve allowed.
- Viewer download published document allowed.
- Viewer guessed confidential detail/history/comments/ACL denied.
- Confidential upload stored, approved, and protected from direct presigned URL exposure.
- Viewer confidential presign denied.
- Editor confidential presign returns `url: null`, `watermarkRequired: true`, and a stream endpoint.
- Editor confidential stream download allowed through the controlled path.
- GROUP ACL read behavior is covered by metadata unit tests and live E2E evidence with `finance-team`.
- EICAR upload blocked and no object/version created.
- Sensitive text upload creates DLP evidence and escalates classification.
- DLP-detected document downgrade to `PUBLIC` denied.
- Compliance metadata allowed where policy permits.
- Compliance preview denied.
- Compliance direct preview denied.
- Compliance download/presign/stream denied.
- Compliance audit query allowed.
- Compliance audit verify-chain allowed.
- Compliance evidence packet allowed and includes metadata/version/workflow/retention/audit evidence.
- Compliance security summary allowed and includes malware/DLP/deny evidence.
- Compliance retention evidence allowed.
- Admin retention run archives due published records.
- Retention workflow history records `system:retention`.
- Retention audit query includes `DOCUMENT_AUTO_ARCHIVED`.
- Viewer evidence packet denied.
- Viewer audit query denied.
- Viewer audit ingest denied.

## Verification Commands

```powershell
pnpm --filter metadata-service test
pnpm --filter document-service test
pnpm --filter audit-service test
pnpm --filter gateway test
pnpm --filter web test
pnpm --filter web exec tsc --noEmit
pnpm --filter web lint
pnpm --filter web build
node --check scripts\e2e-check.mjs
pnpm test:e2e
git diff --check
```

`pnpm test:e2e` requires the local DocVault stack to be running.

Latest baseline before Web gap-closure implementation on 2026-05-30:

- `pnpm --filter metadata-service test`: 7 suites passed, 33 tests passed.
- `pnpm --filter document-service test`: 6 suites passed, 25 tests passed.
- `pnpm --filter audit-service test`: 3 suites passed, 11 tests passed.

Gap-closure targeted evidence on 2026-05-30:

- Comment mutation policy: `pnpm --filter metadata-service test -- documents.controller.spec.ts comments.service.spec.ts` passed with 2 suites and 8 tests.
- Malware scanner adapter: `pnpm --filter document-service test -- malware-scanner.service.spec.ts` passed with 1 suite and 4 tests.
- Audit tamper demo: `pnpm --filter audit-service audit:tamper-demo:test` passed with 4 script-level tests, and guard checks refused missing demo flag / non-local MongoDB URL before connection.
- Web DLP findings UI: `pnpm --filter web exec tsc --noEmit`, `pnpm --filter web lint`, and `pnpm --filter web build` passed. Lint retained 7 existing warnings reported by the tool.
- Evidence packet: `pnpm --filter audit-service test -- audit-query.spec.ts`, `pnpm --filter gateway test -- metadata.proxy.controller.spec.ts`, and `pnpm --filter web exec tsc --noEmit` passed. The gateway packet test verifies no grant token is included.
- Frontend permission/contract alignment: `pnpm --filter web exec tsc --noEmit`, `pnpm --filter web lint`, `pnpm --filter web build`, `node --check scripts\e2e-check.mjs`, `pnpm test:e2e`, and `git diff --check` passed. Lint retained the existing 7 warnings.
- Web permission regression tests: `pnpm --filter web test -- permissions.spec.ts` passed with 1 file and 8 tests after first failing on the missing `getDocumentAccessDecision` helper.
- Preview dialog download-denial regression: `pnpm --filter web test -- document-preview-dialog.spec.ts` passed with 1 file and 1 test after first failing because the denied fallback still rendered as a normal download action.
- Security dashboard model regression: `pnpm --filter web test -- security-dashboard.spec.ts` passed with 1 file and 6 tests after first failing on the missing dashboard model/deep-link helpers and then on stale quick-filter expectations.
- Audit pagination regression: `pnpm --filter web test -- audit.api.spec.ts` passed with 1 file and 2 tests after first failing because `queryAuditLogWindow` did not exist.
- Shared auth/contracts cleanup: `pnpm --filter @docvault/auth build`, affected service builds for document/metadata/audit/workflow/notification, `pnpm --filter document-service test -- shared-rbac.spec.ts`, `pnpm --filter audit-service test`, `pnpm --filter metadata-service test -- app.controller.spec.ts`, gateway build/test, and OpenAPI `js-yaml` parse check passed.
- W-P3 document risk scoring: `pnpm --filter audit-service test -- security-summary.spec.ts` first failed on missing `riskyDocuments`, then passed with 2 tests; `pnpm --filter web test -- security-dashboard.spec.ts` first failed on missing `riskScoring`, then passed with 7 tests. `pnpm --filter audit-service test`, `pnpm --filter audit-service build`, `pnpm --filter web test`, `pnpm --filter web exec tsc --noEmit`, and `pnpm --filter web build` passed.
- W-P3 behavior anomaly detection: `pnpm --filter audit-service test -- security-summary.spec.ts` first failed on missing `behaviorSignals`, then passed with 3 tests; `pnpm --filter web test -- security-dashboard.spec.ts` first failed on missing `behaviorAnomalies`, then passed with 9 tests.
- W-P3 AI-ready guardrails: `pnpm --filter metadata-service test -- policy.service.spec.ts` first failed on missing `getAiGuardrails`, `pnpm --filter gateway test -- metadata.proxy.controller.spec.ts` first failed on missing proxy method, and `pnpm --filter web test -- document-ai-guardrails-card.spec.ts` first failed on the missing component. After implementation all three targeted commands passed.

Follow-up verification on 2026-05-31:

- Shared RBAC runtime regression: `pnpm --filter document-service test -- shared-rbac.spec.ts` first failed with `Cannot read properties of undefined (reading 'getAllAndOverride')`, proving Nest could not inject `Reflector` into the shared guard. After enabling `emitDecoratorMetadata` in `libs/auth`, `pnpm --filter @docvault/auth build` and `pnpm --filter document-service test -- shared-rbac.spec.ts` passed with 2 tests.
- End-to-end after RBAC DI fix: `pnpm test:e2e` passed all required checks, including editor document creation, confidential protections, DLP/malware guards, retention, evidence packet, audit query, and security summary.
- Current focused verification passed: `pnpm --filter audit-service test -- security-summary.spec.ts`, `pnpm --filter web test -- security-dashboard.spec.ts`, OpenAPI `js-yaml` parse check, `pnpm --filter web exec tsc --noEmit`, affected service builds for metadata/audit/document/gateway/workflow/notification, and `pnpm --filter web build`.
- `pnpm --filter web lint` passed with 0 errors and 5 existing warnings in data table, document form/table, `use-documents.ts`, and `auth-provider.tsx`.
- `git diff --check` passed; Git only reported line-ending conversion warnings.
- Behavior anomaly verification passed: `pnpm --filter audit-service test`, `pnpm --filter audit-service build`, `pnpm --filter web test`, `pnpm --filter web exec tsc --noEmit`, `pnpm --filter web lint`, `pnpm --filter web build`, OpenAPI `js-yaml` parse check, and `pnpm test:e2e`. Web lint retained the same 5 existing warnings.
- DLP admin override verification passed: `pnpm --filter metadata-service test`, `pnpm --filter metadata-service build`, `pnpm --filter web test`, `pnpm --filter web exec tsc --noEmit`, `pnpm --filter web lint`, `pnpm --filter web build`, OpenAPI `js-yaml` parse check, and `git diff --check`. Web lint retained the same 5 existing warnings.
- Access impact preview verification passed: `pnpm --filter metadata-service test -- policy.service.spec.ts`, `pnpm --filter gateway test -- metadata.proxy.controller.spec.ts`, and `pnpm --filter web test -- document-access-impact-card.spec.ts` first failed on missing methods/component, then passed after implementation. Follow-up checks passed: `pnpm --filter metadata-service test`, `pnpm --filter metadata-service build`, `pnpm --filter gateway test`, `pnpm --filter gateway build`, `pnpm --filter web test`, `pnpm --filter web exec tsc --noEmit`, `pnpm --filter web lint`, `pnpm --filter web build`, OpenAPI `js-yaml` parse check, and `git diff --check`. Web lint retained the same 5 existing warnings.

Latest local Sprint 4A E2E evidence on 2026-05-30:

- `PASS editor confidential presign returns stream-only response: 200`
- `PASS confidential presign withheld direct URL`
- `PASS editor confidential stream download: 200`
- `PASS GROUP ACL stored with normalized group name`
- `PASS audit verify-chain valid=false` in the current local data set; E2E asserts the chain status is returned and Evidence Packet captures it at generation time.
- `PASS compliance officer evidence packet: 200`
- `PASS evidence packet includes metadata/version/workflow/retention/audit evidence`
- `PASS viewer evidence packet denied: 403`
- `PASS security summary includes malware/DLP/deny evidence`
- `All required E2E checks passed.`
