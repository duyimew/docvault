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
- Upload, preview and download success.
- ZAP report artifact: `zap_report.html` and `zap_report.json`.
- Grafana dashboard showing pod/workload health and CPU/RAM.
- Dependency Check report plus SCA triage record.

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
6. Open preview.
7. Expected result: the uploaded file preview renders through the EKS gateway route.

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
5. Click **Approve** and confirm.
6. Expected result: document is approved/published and leaves the pending approval queue.

---

## Step 5 - Viewer Downloads the Published Document

1. Sign out.
2. Login through Keycloak as the Viewer test user.
3. Open **Documents** and select the published document.
4. Click **Download**.
5. Expected result: browser downloads the original file through the gateway.

---

## Step 6 - Compliance Officer Checks Audit and Deny Behavior

1. Sign out.
2. Login through Keycloak as the Compliance Officer test user.
3. Open **Audit**.
4. Expected result: audit table shows events from create, upload, submit, approve and download.
5. Open the published document detail page.
6. Expected result: the Compliance Officer cannot download the file; the UI hides or blocks the download action.

---

## DevSecOps Demo Add-On

Run the Jenkins pipeline after the app is reachable on EKS:

```text
RUN_ZAP=true
ZAP_TARGET=http://<node-external-ip>:30006/api
GITOPS_BRANCH=gitops-testing
```

Expected result:

- `DAST - OWASP ZAP` stage runs.
- Jenkins archives `zap-report/zap_report.html`.
- Jenkins archives `zap-report/zap_report.json`.
- Low/medium warnings are recorded for the demo; High/Critical findings require a written action item.

---

## Observability Demo Add-On

Apply the monitoring Argo CD app:

```powershell
kubectl apply -f infra/argocd-apps/monitoring.yaml
kubectl get app monitoring-stack -n argocd
kubectl get pods -n monitoring
```

Open Grafana:

```powershell
kubectl port-forward svc/monitoring-stack-grafana -n monitoring 3000:80
```

Navigate to `http://localhost:3000` and capture dashboard evidence for pod health, CPU/RAM and workload status.

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

Then open `http://localhost:3100`.
