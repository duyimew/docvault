# Running the Project Locally

Updated: 2026-06-26

This document is a guide for running DocVault on a local machine based on the current code state.

## 1. Requirements

- Node.js 20+
- `pnpm` 9+
- Docker Desktop or Docker Engine + Docker Compose
- Free ports:
  - `3000` gateway
  - `3001` metadata-service
  - `3002` document-service
  - `3003` workflow-service
  - `3004` audit-service
  - `3005` notification-service
  - `3006` frontend web — default from `apps/web/package.json`
  - `5432` Postgres
  - `5555` Prisma Studio (PostgreSQL GUI)
  - `8080` Keycloak
  - `8081` MongoDB Express (MongoDB GUI)
  - `9000` MinIO API
  - `9001` MinIO Console

## 2. Install Dependencies

Run from the repo root:

```bash
pnpm install
```

## 3. Start Infrastructure

The local infra is in `infra/docker-compose.dev.yml`.

Create the infra env file once before starting Compose:

```bash
cp infra/.env.example infra/.env
```

```bash
docker compose -f infra/docker-compose.dev.yml --env-file infra/.env up -d
```

This starts:

- Postgres
- MongoDB
- MongoDB Express (GUI for MongoDB)
- MinIO
- MinIO init job
- Keycloak

Note:

- MongoDB stores audit logs (audit-service uses MongoDB instead of PostgreSQL).
- If you already have an old Postgres volume from the proto-microservices phase, you should delete the old volume or recreate the database before migrating.

## 4. Create Environment Files

Create the following service env files by copying from each nearest `.env.example`:

- `services/gateway/.env`
- `services/metadata-service/.env`
- `services/document-service/.env`
- `services/workflow-service/.env`
- `services/audit-service/.env`
- `services/notification-service/.env`

Create `apps/web/.env.local` manually if you want to override the frontend defaults.

Default values in the repo already match the local stack:

- gateway: `http://localhost:3000`
- metadata-service: `http://localhost:3001`
- document-service: `http://localhost:3002`
- workflow-service: `http://localhost:3003`
- audit-service: `http://localhost:3004`
- notification-service: `http://localhost:3005`
- Keycloak: `http://localhost:8080`
- MinIO: `http://localhost:9000`

Recommended frontend variables:

```env
NEXT_PUBLIC_APP_NAME=DocVault
NEXT_PUBLIC_API_BASE_URL=/api
GATEWAY_URL=http://localhost:3000
FRONTEND_URL=http://localhost:3006
```

## 5. Run Migrations

After Postgres is up:

```bash
pnpm --filter metadata-service prisma:deploy
```

Audit data is stored in MongoDB, so there is no Prisma migration step for
`audit-service`.

## 6. Seed Baseline Metadata

Run the baseline metadata seed after migrations and after Keycloak is healthy.
This creates the demo organization, memberships, baseline documents, ACL
examples, and workflow history.

```bash
pnpm run seed:metadata
```

By default this seed is repeatable and does not wipe all metadata. For a local
full reset, use the explicit guard:

```powershell
$env:DOCVAULT_ALLOW_METADATA_RESEED="true"
pnpm run seed:metadata
```

Do not run demo/local seed commands against production data.

### View Data (GUI)

With Docker infra running, you can open GUI tools to inspect data directly:

**PostgreSQL — Prisma Studio** (metadata-service):

```bash
pnpm --filter metadata-service prisma:studio
```

Open: http://localhost:5555

Core tables to inspect: `organizations`, `organization_memberships`,
`documents`, `document_versions`, `document_acl`, `document_workflow_history`,
`document_comments`, and `document_saved_views`.

**MongoDB — MongoDB Express**:

Open: http://localhost:8081

Login with `MONGO_EXPRESS_USER` / `MONGO_EXPRESS_PASSWORD` from `infra/.env`.

## 7. Start Backend

Recommended: run each service in a separate terminal in this order:

```bash
pnpm --filter metadata-service start:dev
pnpm --filter audit-service start:dev
pnpm --filter document-service start:dev
pnpm --filter notification-service start:dev
pnpm --filter workflow-service start:dev
pnpm --filter gateway start:dev
```

Backend URLs after startup:

- Gateway Swagger: `http://localhost:3000/api/docs`
- Metadata Swagger: `http://localhost:3001/docs`
- Document Swagger: `http://localhost:3002/docs`
- Workflow Swagger: `http://localhost:3003/docs`
- Audit Swagger: `http://localhost:3004/docs`
- Notification Swagger: `http://localhost:3005/docs`

Quick health check:

- `http://localhost:3000/api/health`
- `http://localhost:3001/health`
- `http://localhost:3002/health`
- `http://localhost:3003/health`
- `http://localhost:3004/health`
- `http://localhost:3005/health`

## 8. Start Frontend

The frontend runs on port `3006` by default. This avoids conflicts with the gateway and backend services on ports `3000` to `3005`.

Run:

```bash
pnpm --filter web dev
```

Open:

- `http://localhost:3006`

Login page:

- `http://localhost:3006/login`

If you intentionally want another port, override it explicitly:

```bash
pnpm --filter web dev -- --port 3100
```

## 9. Seed Demo Business Flows

After all backend services and the gateway are running, create realistic demo
data through the Gateway API:

```bash
pnpm run seed:demo
```

This creates uploaded files, document versions, workflow transitions, comments,
DLP evidence, ACL denial examples, and audit evidence through the real service
paths. It uses `DOCVAULT_DEMO_SEED_RUN_ID=local` by default and skips existing
documents for that run id. Set a different run id to create a fresh demo set:

```powershell
$env:DOCVAULT_DEMO_SEED_RUN_ID="presentation-1"
pnpm run seed:demo
```

Optional malware/EICAR evidence is disabled by default because ClamAV first boot
can be slow. Enable it only when ClamAV is healthy:

```powershell
$env:DOCVAULT_SEED_INCLUDE_MALWARE_PROBE="true"
pnpm run seed:demo
```

If the backend is already running and you want the baseline metadata seed plus
API demo seed in one command:

```bash
pnpm run seed:local
```

## 10. Login and Sample Users

Password for all seeded users:

- `Passw0rd!`

Available users:

- `viewer1`
- `editor1`
- `approver1`
- `co1`
- `admin1`

The frontend currently supports 2 login modes:

- Demo Login
  - good for quickly viewing UI/role guards
- JWT Token
  - use real token from Keycloak to go through the full backend

Keycloak local:

- `http://localhost:8080`

## 11. Quick Smoke Test

After all backend is running, you can run the E2E smoke test:

```bash
pnpm test:e2e
```

This script checks the main flows:

- unauthorized requests are blocked
- viewer cannot create
- editor can create/upload/submit
- approver can approve
- viewer can download file after publish
- compliance officer can query audit but cannot download files

## 12. Quick Run Mode and Notes

Root script currently has:

```bash
pnpm dev
```

However, this script runs the entire workspace through Turbo and starts long-running dev tasks together. It is convenient for quick checks, but logs are harder to follow than running backend and frontend separately.

Current recommendation:

- run backend services separately as in step 7, or use `pnpm start:sequential`
- run frontend separately as in step 8 on port `3006`

## 13. Common Errors

### Postgres Migration Error

Check:

- Postgres container is healthy
- database `docvault_metadata` has been initialized
- MongoDB is healthy for `audit-service` and `notification-service`
- old volume is not holding onto old schema

### Frontend API Call Error

Check:

- `apps/web/.env.local` points to `NEXT_PUBLIC_API_BASE_URL=/api`, or to `http://localhost:3000/api` if you intentionally bypass the Next.js proxy
- gateway is running on port `3000`
- frontend is open on `3006`, not `3000` or `3001`

### Cannot Get Keycloak Token

Check:

- Keycloak is up at `http://localhost:8080`
- realm `docvault` has been imported
- client secret in docs and `.env` matches the current seed

### Demo Seed Fails

Check:

- baseline metadata seed has run successfully
- all backend services and the gateway are running
- `GATEWAY_URL` points to `http://localhost:3000` unless intentionally changed
- MinIO is reachable at `http://localhost:9000`
- for non-local demo targets, set `DOCVAULT_ALLOW_REMOTE_DEMO_SEED=true`

## 14. Related Documents

- `README.md`
- `docs/guides/demo-flow.md`
- `docs/guides/demo-users.md`
- `docs/architecture/PROJECT_STATUS.md`
- `infra/README.md`
- `services/README.md`
