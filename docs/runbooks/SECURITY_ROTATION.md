# Security Credential Rotation Checklist

**Why this exists:** development credentials were committed to git history and
must be treated as **compromised**. Untracking files (already done for
`*.tfplan` / `*.kubeconfig`) stops *future* commits but does **not** remove them
from history — anyone with the repo can `git log`/`git show` the old values.

The only reliable remedy is to **rotate the secrets at their source** and
invalidate the old ones. Optionally rewrite history (Section 5) if the repo
will ever be public.

> Do these BEFORE any production / shared deployment. Work top-down; the order
> minimizes lockout risk.

---

## 0. Inventory — credentials known to be in history

| Secret | Where it leaked | Compromised value |
|---|---|---|
| Keycloak client secret | `realm-docvault.json`, `app-secrets.yaml` | `dev-gateway-secret` |
| Demo user passwords | `realm-docvault.json` | `Passw0rd!` |
| Keycloak admin password | `app-secrets.yaml` | `adminpw` |
| PostgreSQL password | `app-secrets.yaml`, `.env` | `docvaultpw` |
| MongoDB root password | `app-secrets.yaml` | `rootpw` |
| MinIO keys | `app-secrets.yaml` | `minioadmin` / `minioadminpw` |
| Grant token secrets | `app-secrets.yaml` | `docvault-download-grant-secret` |
| Jenkins→ArgoCD kubeconfig | `jenkins-argocd-reader.kubeconfig` | (cluster token) |
| App-role DB password | `.env` (added for RLS) | `docvault_app_pw` |

> Anything signed/derived from these (JWTs, grant tokens, audit signatures) is
> only as trustworthy as the secret. Rotating the secret invalidates them.

---

## 1. Keycloak (highest blast radius — do first)

1. **Client secret** (`docvault-gateway`): Keycloak Admin → Clients →
   docvault-gateway → Credentials → **Regenerate Secret**.
   - Update everywhere it's consumed: `KEYCLOAK_CLIENT_SECRET` in gateway,
     notification-service, and any `app-secrets.yaml` / `.env`.
   - Note: `SENSITIVE_ACTION_PROOF_SECRET` falls back to this client secret —
     set an independent `SENSITIVE_ACTION_PROOF_SECRET` instead.
2. **Admin password**: change the Keycloak admin account password; update
   `KEYCLOAK_ADMIN_PASSWORD`.
3. **Demo/real user passwords**: force reset for any real account that ever
   used `Passw0rd!`. For demo accounts, set non-default passwords or disable
   them outside dev.
4. Confirm MFA (TOTP) is enforced for `admin` / `compliance_officer`
   (already configured in the realm).

## 2. Datastores

5. **PostgreSQL**: change passwords for `docvault` (owner) and `docvault_app`
   (RLS runtime role). Update `DATABASE_URL` and `DATABASE_URL_RUNTIME`.
6. **MongoDB**: change the root (or create a least-privilege app user) and
   update `MONGODB_URI` for audit-service and notification-service.
7. **MinIO/S3**: rotate access key + secret (ideally create a scoped key for
   document-service); update `S3_ACCESS_KEY` / `S3_SECRET_KEY`.

## 3. Application secrets

8. **Grant token secrets** (`DOWNLOAD_GRANT_SECRET`, `PREVIEW_GRANT_SECRET`):
   set new strong values. Use the kid-rotation vars
   (`GRANT_TOKEN_CURRENT_KID` + `_<kid>` secrets) for zero-downtime if links
   are outstanding.
9. **`AUDIT_INGEST_TOKEN`**: set a new strong token; use
   `AUDIT_INGEST_TOKEN_PREVIOUS` for a zero-downtime window, then drop it.
10. **`AUDIT_SIGNING_SECRET`**: set for the first time (or rotate via
    `AUDIT_SIGNING_KID` + `AUDIT_SIGNING_SECRET_<kid>`).
11. **`INTERNAL_CALL_SECRET`**: set a strong value (was a guessable flag).
12. **`EMAIL_API_KEY`**: use a real provider key; never commit it.

## 4. Infrastructure / CI

13. **Jenkins→ArgoCD kubeconfig**: revoke the leaked ServiceAccount token in
    the cluster, regenerate the kubeconfig, store it as a CI secret (Jenkins
    credentials / sealed secret) — **never** back in the repo.
14. **`app-secrets.yaml`**: replace plaintext with SealedSecrets / SOPS /
    External Secrets (the file's own comment recommends this).
15. Verify gitleaks CI passes after rotation (no new plaintext secrets).

## 5. (Optional) Purge from git history

Only if the repo will be public or history hygiene is required. **Destructive
— coordinate with everyone who has a clone.**

```bash
# Preferred: git filter-repo (install separately)
git filter-repo --invert-paths \
  --path jenkins-argocd-reader.kubeconfig \
  --path destroy.tfplan \
  --path infra/terraform/aws-devops-ec2/destroy.tfplan \
  --path infra/terraform/aws-eks/destroy.tfplan
# Force-push rewritten history; all collaborators must re-clone.
```

> Rewriting history does NOT un-leak already-cloned secrets — **rotation
> (Sections 1–4) is the real fix.** History purge only reduces future exposure.

---

## 6. Verification after rotation

```bash
pnpm build && pnpm test          # services still build/auth with new secrets
pnpm test:e2e                    # full flow works end-to-end
```

- [ ] Login works with rotated Keycloak client secret + admin password
- [ ] Services connect with new DB / Mongo / MinIO credentials
- [ ] Audit ingestion + chain verify still pass (new ingest/signing secrets)
- [ ] gitleaks CI green
- [ ] Old kubeconfig token rejected by the cluster

---

## Quick priority order

1. Keycloak client secret + admin password (auth backbone)
2. Database / Mongo / MinIO passwords (data access)
3. Grant / audit / internal-call secrets (token integrity)
4. Kubeconfig + CI secret store (deploy access)
5. (Optional) history purge
