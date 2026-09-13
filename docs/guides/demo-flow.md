# DocVault Demo Flow - EKS

This is the primary demo script for the deployed MVP on AWS EKS. Use the local setup only as a fallback.

## Prerequisites

1. EKS node group is running and kubeconfig points to the DocVault cluster.
2. Argo CD has synced the application from `gitops-testing`.
3. Refresh NodePort URLs and Keycloak redirect settings:

```powershell
.\scripts\setup-eks-access.ps1
```

4. Open the web app:

```text
http://<node-external-ip>:30006
```

Useful URLs:

```text
http://<node-external-ip>:30006/login
http://<node-external-ip>:30006/api
```

For the official demo, use Keycloak login rather than local demo-login mode.

---

## Evidence to Capture

- Jenkins build with security stages.
- Argo CD application `Synced/Healthy`.
- Web app login page on EKS.
- Successful Keycloak login.
- Demo Kit page showing web runtime scope, screenshot targets, presenter flow, and markdown export.
- Document smart workbench saved views, quick views, active chips, counts, and URL query state.
- Document detail metadata summary, evidence links, and version preview posture.
- Approval SLA summary, assignment lane, due status, and sort/filter controls.
- Approval readiness checklist in document detail and Approvals review drawer.
- Reject dialog reason presets for approver review.
- Upload, preview and download success.
- Compliance Officer preview/download denial reason on document detail.
- Notification Center showing approval/security/retention/document work queues.
- ZAP report artifact: `zap_report.html` and `zap_report.json`.
- Grafana dashboard showing pod/workload health and CPU/RAM.
- Dependency Check report plus SCA triage record.

---

## Step 0 - Presenter Opens Demo Kit

1. Login through Keycloak as a Compliance Officer or Admin test user.
2. Open **Demo Kit**.
3. Expected result: the page shows Web/runtime scope, screenshot targets, presenter flow, out-of-scope notes, and copy/download markdown actions.
4. Use this page as the checklist while capturing the rest of the demo evidence.

---

## Step 1 - Editor Login

1. Open `http://<node-external-ip>:30006/login`.
2. Choose Keycloak login.
3. Login as the Editor test user.
4. Expected result: dashboard opens and the sidebar shows document workflows available to Editor.

---

## Step 2 - Editor Creates and Uploads a Document

1. Go to **Documents** and click **New Document**.
2. Fill in:
   - **Title**: `Q1 2026 Financial Report`
   - **Description**: `Quarterly financial summary`
   - **Classification**: `Confidential`
   - **Tags**: `finance`, `quarterly`
3. Attach a PDF or DOCX file.
4. Click **Save Draft**.
5. Expected result: document detail page opens with status **Draft**.
6. Check the metadata summary and Version History preview posture.
7. Open preview.
8. Expected result: the uploaded file preview renders through the EKS gateway route, or the version row explains why the format is unsupported.
9. Return to **Documents**.
10. Apply saved views such as **Pending review**, **Sensitive attention**, or save the current filter as a custom view.
11. Switch quick views such as **Needs action**, **Pending review**, **Published**, or **Sensitive**.
12. Search for `finance`, filter by classification/status/tag/owner, then clear active chips.
13. Expected result: saved-view counts, quick-view counts, result count, URL query state, active chips and table rows update together.

---

## Step 3 - Editor Submits for Approval

1. On the document detail page, click **Submit for Review**.
2. Confirm the action.
3. Expected result: status changes to **Pending Review** and a success toast appears.

---

## Step 4 - Approver Reviews and Approves

1. Sign out.
2. Login through Keycloak as the Approver test user.
3. Open **Approvals**.
4. Select the pending document.
5. Check SLA summary cards, assignment lane, due status, and SLA sort/filter controls.
6. Check the approval readiness checklist in the review drawer.
7. Open **Reject** once to show the preset reason buttons, then cancel.
8. Click **Approve** and confirm.
9. Expected result: document is approved/published and leaves the pending approval queue.
10. Open the bell menu or **Notifications**.
11. Expected result: approval notifications can be filtered by unread/read state and link back to the target workflow.

---

## Step 5 - Viewer Downloads the Published Document

1. Sign out.
2. Login through Keycloak as the Viewer test user.
3. Open **Documents** and select the published document.
4. Check the metadata summary and preview posture in Version History.
5. Click **Download**.
6. Expected result: browser downloads the original file through the gateway.

---

## Step 6 - Compliance Officer Checks Audit and Deny Behavior

1. Sign out.
2. Login through Keycloak as the Compliance Officer test user.
3. Open **Audit**.
4. Expected result: audit table shows events from create, upload, submit, approve and download.
5. Open the published document detail page.
6. Expected result: the Compliance Officer cannot preview/download file content; Version History shows the policy denial reason.
7. Open evidence links from document detail to Audit, Evidence Center, Retention or Security when available.
8. Expected result: links stay metadata-only and do not reveal file content.

---

## DevSecOps Demo Add-On

Run the Jenkins pipeline after the app is reachable on EKS:

```text
RUN_ZAP=true
DEPLOY_TARGET_URL=http://<node-external-ip>:30006
ZAP_TARGET=http://<node-external-ip>:30006
RUN_ARGO_HEALTH_CHECK=true
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
GITOPS_BRANCH=gitops-testing
```

Expected result:

- `Argo CD Health Check` passes for the service applications.
- `Post-deploy Smoke Test` passes for `GET /` and `GET /api/health`.
- `DAST - OWASP ZAP` stage runs.
- Jenkins archives `zap-report/zap_report.html`.
- Jenkins archives `zap-report/zap_report.json`.
- Low/medium warnings are recorded for the demo; High/Critical findings require a written action item.

---

## Observability Demo Add-On

Apply the monitoring and logging Argo CD apps:

```powershell
kubectl apply -f infra/argocd-apps/monitoring.yaml
kubectl apply -f infra/argocd-apps/loki.yaml
kubectl get app monitoring-stack loki-stack -n argocd
kubectl get pods -n monitoring
```

Open Grafana and Loki:

```powershell
kubectl port-forward svc/monitoring-stack-grafana -n monitoring 3000:80
kubectl port-forward svc/loki-stack -n monitoring 3100:3100
```

Navigate to `http://localhost:3000` and capture:

- Grafana dashboards for pod health, CPU/RAM and workload status.
- Grafana Explore logs with datasource **Loki** and query `{namespace="docvault"}`.

Optional API check:

```powershell
$query = [uri]::EscapeDataString('{namespace="docvault"}')
Invoke-RestMethod -Uri "http://127.0.0.1:3100/loki/api/v1/query_range?query=$query&limit=5"
```

---

## Local Fallback

Use local mode only if EKS is unavailable:

```bash
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env.example up -d
pnpm --filter metadata-service start:dev
pnpm --filter audit-service start:dev
pnpm --filter document-service start:dev
pnpm --filter notification-service start:dev
pnpm --filter workflow-service start:dev
pnpm --filter gateway start:dev
pnpm --filter web dev -- --port 3100
```

Navigate to: **http://localhost:3100**

---

## Step 0 — Presenter: Demo Kit

1. Demo Login as `co1` or `admin1`
2. Open **Demo Kit** in sidebar
3. ✅ Should see Web runtime evidence scope, screenshot targets, presenter flow, and markdown export actions
4. ✅ Out-of-scope notes should make clear this page does not claim DevSecOps pipeline evidence

---

## Step 1 — Editor: Login

1. Click **Demo Login** tab (or use JWT Token tab with real token)
2. Username: `editor1`, select role: **Editor**
3. Click **Enter as Editor**
4. ✅ Should redirect to Dashboard, showing sidebar with Documents and Dashboard links

---

## Step 2 — Editor: Create Document

1. Click **Documents** in sidebar → Click **New Document** button
2. Fill in:
   - **Title**: `Q1 2026 Financial Report`
   - **Description**: `Quarterly financial summary`
   - **Classification**: `Confidential`
   - **Tags**: `finance`, `quarterly`
3. Drag & drop or click to attach a PDF file
4. Click **Save Draft**
5. ✅ Should redirect to document detail page, status = **Draft**
6. Check **Metadata summary** and **Version History** preview posture
7. ✅ Summary should show owner/status/classification/retention/current version/checksum/content type without object keys
8. Return to **Documents**
9. Apply saved views such as **Pending review**, **Sensitive attention**, or save the current filter as a custom view
10. Switch quick views such as **Needs action**, **Pending review**, **Published**, or **Sensitive**
11. Search `finance`, set status/classification/tag/owner filters, and change sort
12. ✅ Saved-view counts, quick-view counts, active chips and URL query params should reflect the selected workbench state

---

## Step 3 — Editor: Submit for Approval

1. On the document detail page, look for **Action Panel** on the right
2. Click **Submit for Review**
3. Confirm in the dialog
4. ✅ Toast: "Document submitted for approval"
5. ✅ Status changes to **Pending Review**

---

## Step 4 — Approver: Login

1. Click user avatar in top-right → **Sign out**
2. Demo Login as `approver1`, role: **Approver**
3. Click **Approvals** in sidebar
4. ✅ Should see the submitted document in the pending list
5. ✅ Should see SLA summary cards, assignment lane, due status, and sort/filter controls
6. ✅ Opening the row should show assignment/SLA and the approval readiness checklist in the drawer

---

## Step 5 — Approver: Approve Document

1. Click on the pending document row → Review drawer opens on right
2. Review the document details, assignment/SLA, workflow history, and readiness checklist
3. Click **Approve**
4. Confirm in dialog
5. ✅ Toast: "Document approved and published"
6. ✅ Document disappears from approvals queue

*Alternative — Reject:*
- Click **Reject**, choose a preset reason or enter a custom reason, confirm
- Document returns to Draft status

---

## Step 6 — Viewer: Login and Download

1. Sign out → Demo Login as `viewer1`, role: **Viewer**
2. Go to **Documents** → Find the published document
3. Click on it → Navigate to detail page
4. Status should show **Published**
5. Check metadata summary and Version History preview posture
6. Click **Download** in the action panel
7. ✅ Browser should start downloading the file

---

## Step 7 — Compliance Officer: Audit

1. Sign out → Demo Login as `co1`, role: **Compliance Officer**
2. Go to **Audit** in sidebar
3. ✅ Should see security cards for denied events, malware blocked, DLP hits, and download denied
4. Click **Verify Chain**
5. ✅ Should see audit chain status and checked event count
6. Try filtering by action type or date
7. Navigate to a document detail page
8. ✅ Version History shows preview/download blocked by policy (compliance_officer can audit metadata/events, not file content)
9. ✅ Evidence links can open Audit/Evidence/Retention/Security workspaces without exposing file content

---

## Step 8 — Compliance/Admin: Retention Evidence

1. While signed in as `co1`, go to **Retention** in sidebar
2. ✅ Should see tracked records, due soon, overdue, and archived counters
3. Open a record and verify the document detail shows retention class and retention deadline
4. Sign out → sign in as `admin1`
5. Go to **Retention** and click **Run Retention**
6. ✅ Due records move to **Archived**
7. Open the archived document workflow timeline
8. ✅ Should show `Retention archived` by `system:retention`
9. Go to **Audit** and filter action `DOCUMENT_AUTO_ARCHIVED`
10. ✅ Should see the retention audit event

---

## Step 9 — Notification Center Work Queue

1. Sign in as `approver1`, `co1`, or `admin1`
2. Open the bell menu and click **Open notification center**
3. Filter by **Approvals**, **Retention**, **Security**, or **Documents**
4. Switch between **All**, **Unread**, and **Read**
5. Click **Mark read** on one item, then click **Mark all read**
6. ✅ Should update the queue without a full page reload
7. Click a target action
8. ✅ Should navigate to the matching page: Approvals, Retention, Security, Audit, or Document detail

---

## Step 10 — Compliance Officer: Evidence Case Presentation

1. Sign in as `co1` or stay in the compliance session
2. Go to **Evidence Center** in sidebar
3. ✅ Should see source cards for Audit Chain, Recommendation Packets, Retention Evidence, and Document Packets
4. Select recommendation packets and document packets for the audit case
5. Click **Bundle** to export the metadata-only bundle manifest
6. Click **Report** to export the printable Evidence Report HTML
7. Open **Presentation**
8. ✅ Should see case readiness, audit-chain posture, retention posture, checklist, recommendation timeline, and document packet list
9. Confirm the bundle/report show `metadataOnly` and `excludedSensitiveFields`

---

## Security Evidence Probes

Use the backend E2E command below to demonstrate the automated controls:

- `GROUP` ACL uses normalized Keycloak group names such as `finance-team`; if the local token contains that claim, E2E proves group metadata access.
- `CONFIDENTIAL` download posture withholds direct presigned URL and returns a stream-only/watermark path.
- EICAR upload is blocked before MinIO storage.
- Sensitive text upload records DLP evidence and escalates classification to `CONFIDENTIAL`.
- DLP-detected document downgrade to `PUBLIC` is denied.
- Compliance officer can view `/audit/security-summary`.
- Compliance officer can view `/metadata/retention/documents`.
- Admin can run `/metadata/retention/run` and produce `DOCUMENT_AUTO_ARCHIVED`.

---

## Quick Backend E2E (no browser needed)

```bash
pnpm test:e2e
```

Covers all flows automatically. See [README.md](../README.md#e2e-checks-covered) for what is tested.

---

## Notes for Presenter

- Demo Login mode can be used to inspect role-based UI quickly
- For a real end-to-end demo, keep all backend services running and prefer JWT Login with Keycloak tokens
- Frontend should run on port `3100` to avoid conflicts with gateway `3000` and metadata-service `3001`
