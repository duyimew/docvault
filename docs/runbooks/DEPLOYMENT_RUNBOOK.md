# DocVault Deployment Runbook

Operational guide for starting, configuring, and verifying DocVault.
Covers the strict startup order, all environment variables (including the
security features added recently), and verification + troubleshooting steps.

> Audience: operators / IT deploying DocVault internally. For architecture see
> `CLAUDE.md`; for RLS specifics see `services/metadata-service/prisma/rls/README.md`.

---

## 1. Topology

```
Client → Gateway (:3000) → Backend services (:3001–:3005)
```

| Service | Port | Database |
|---|---|---|
| gateway | 3000 | — |
| metadata-service | 3001 | PostgreSQL (Prisma) |
| document-service | 3002 | — (MinIO S3) |
| workflow-service | 3003 | — |
| audit-service | 3004 | MongoDB |
| notification-service | 3005 | MongoDB |
| web (Next.js) | 3006 | — |

Infra (Docker): PostgreSQL, MongoDB, Mongo Express, MinIO, Keycloak, ClamAV.

**Startup order is strict: Gateway must start LAST**, after all backend
services are ready (it proxies to them).

---

## 2. First-time setup (after clone)

```bash
# 1. Environment files (not tracked by git)
cp .env.example .env
cp infra/.env.example infra/.env
# then copy/create per-service .env from each services/*/.env.example

# 2. Install dependencies
pnpm install

# 3. Start Docker infra (PostgreSQL, MongoDB, MinIO, Keycloak, ClamAV)
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d

# 4. Wait for infra healthy
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env ps

# 5. Generate Prisma client + apply migrations
pnpm --filter metadata-service prisma:generate
pnpm --filter metadata-service prisma:deploy

# 6. Seed sample data (resolves demo user subs from Keycloak — Keycloak must be up)
pnpm --filter metadata-service db:seed

# 7. Start all services in the correct order
pnpm start:sequential
```

> Keycloak imports its realm (`infra/keycloak/realm-docvault.json`) once at
> container creation. If you change the realm, remove the Keycloak volume and
> recreate the container to re-import (see `infra/keycloak/README.md`).

---

## 3. Routine startup (infra already provisioned)

```bash
# Ensure Docker infra is running
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d

# Recommended: one-command sequential startup (skips migrations)
pnpm start:sequential

# With Prisma migrations applied first:
RUN_PRISMA_DEPLOY=1 pnpm start:sequential
```

Manual order (if not using the script):
1. Docker infra (PostgreSQL, MongoDB, MinIO, Keycloak, ClamAV)
2. `pnpm --filter metadata-service prisma:deploy`
3. Backend: metadata → document → workflow → notification → audit
4. Gateway
5. Web (`pnpm --filter web dev`, binds :3006)

---

## 4. Shutdown

```bash
# Stop app processes (Ctrl+C on the start:sequential process), then infra:
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env down
# Add -v to also remove data volumes (DESTROYS all data — dev only):
# docker compose -f infra/docker-compose.dev.yml --env-file infra/.env down -v
```

---

## 5. Environment variables

### Core (all services)
| Var | Purpose |
|---|---|
| `KEYCLOAK_BASE_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_AUDIENCE` | JWT verification (JWKS, RS256) |
| `DATABASE_URL` | metadata-service: migrations/seed (owner role) |
| `MONGODB_URI` | audit-service & notification-service |
| `*_SERVICE_URL`, `GATEWAY_URL`, `FRONTEND_URL` | inter-service routing |

### Security features (added recently — all optional / fail-safe)
| Var | Service | Effect when set | When unset |
|---|---|---|---|
| `JWT_CLOCK_TOLERANCE_SECONDS` | all | clock-drift tolerance for `exp` (default 300) | uses 300; tokens without `exp` always rejected |
| `AUDIT_INGEST_TOKEN` | audit | required token for event ingestion | ingestion disabled (events dropped) |
| `AUDIT_INGEST_TOKEN_PREVIOUS` | audit | accepted during token rotation | only current token accepted |
| `AUDIT_SIGNING_SECRET` (`_<kid>`) | audit | HMAC-sign each event (tamper-evidence) | events hash-chained but unsigned |
| `AUDIT_SIGNING_KID` | audit | selects signing key for rotation | single-key mode |
| `INTERNAL_CALL_SECRET` | all | secret required to bypass rate-limit on internal calls | no request is exempt (fail-closed) |
| `DATABASE_URL_RUNTIME` | metadata | runtime connects as restricted `docvault_app` role → RLS enforced | falls back to `DATABASE_URL` (RLS bypassed) |
| `EMAIL_API_KEY`, `EMAIL_FROM` | notification | enable cloud email (Resend) | email is a no-op (in-app only) |
| `EMAIL_NOTIFY_TYPES` | notification | comma-separated NotifyTypes that email (default `REJECTED`) | defaults to `REJECTED` |
| `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` | notification | resolve recipient email via Keycloak Admin API | email recipients unresolved → skipped |

> All security features are **fail-safe**: leaving a var unset disables that
> feature without breaking the service. Production hardening = set them all.

---

## 6. Verification

```bash
# Build + unit tests
pnpm build
pnpm test

# End-to-end (requires all services + infra running)
pnpm test:e2e          # expects "All required E2E checks passed."

# Audit hash-chain integrity (compliance/admin token required)
curl -H "Authorization: Bearer <token>" \
  http://localhost:3000/api/audit/verify-chain   # → { valid: true, ... }

# RLS isolation (requires DATABASE_URL_RUNTIME + RLS scripts applied)
pnpm --filter metadata-service exec ts-node \
  -r tsconfig-paths/register --project tsconfig.build.json src/verify-rls.ts
# → {"noContext":N,"correctOrg":N,"wrongOrg":0} + PASS
```

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Gateway 502 / connection refused | started before backends ready | use `pnpm start:sequential`; ensure backends up first |
| `db:seed` fails resolving subs | Keycloak not up / realm not imported | start Keycloak, confirm realm `docvault`, retry |
| User sees no documents / 404 on known doc | user not enrolled in the seed org (lazy-provisioned own org) | re-run `db:seed` (enrolls all demo users); or enroll in `organization_memberships` |
| 403 "Invalid or expired sensitive action proof" | calling run-retention / evidence-packet without step-up proof | `POST /api/metadata/sensitive-actions/proof` first, send `x-docvault-step-up-proof` |
| Audit events dropped | `AUDIT_INGEST_TOKEN` unset | set the token on audit-service and callers |
| Email never sends | `EMAIL_API_KEY`/`EMAIL_FROM` unset, or type not in `EMAIL_NOTIFY_TYPES` | configure Resend keys; add type to `EMAIL_NOTIFY_TYPES` |
| RLS not isolating | app connects as superuser (RLS bypassed) | set `DATABASE_URL_RUNTIME` to `docvault_app`; apply `prisma/rls/*.sql` |
| Realm changes not taking effect | Keycloak imports realm once at creation | remove Keycloak volume, recreate container |
