# Backup-First Databases and MinIO Retirement Plan

Status: ready for execution handoff  
Created: 2026-06-22  
Mode: direct planning  
Scope: EKS testing environment (`docvault`, `gitops-testing`)

## Outcome

Deliver a resource-bounded, cloud-native persistence posture without paying the steady-state cost of six database replicas:

- Run metadata PostgreSQL as a two-instance CloudNativePG cluster, pinned to PostgreSQL 16 for migration compatibility and spread across the two available AZs.
- Archive PostgreSQL WAL continuously and create scheduled base backups in a dedicated, KMS-encrypted S3 backup bucket.
- Keep audit MongoDB as one data-bearing member, convert it to a one-member replica set, and use Percona Backup for MongoDB (PBM) for S3 backup/PITR without deploying the full Percona Operator replica set.
- Retire MinIO from the EKS testing runtime after proving document-service uses AWS S3/KMS exclusively.
- Preserve rollback copies of the old PostgreSQL and MinIO EBS volumes until explicit cleanup gates pass.
- Keep local Docker Compose MinIO support out of scope; this plan removes MinIO from EKS testing, not from local development.

Stop condition: the application runs against the new PostgreSQL endpoint, audit writes and hash-chain verification pass on the replica-set MongoDB, both databases have independently restored from S3 in an isolated environment, MinIO workloads are absent from EKS, and resource/rollback evidence is recorded.

## Requirements Summary

1. Resource efficiency
   - PostgreSQL steady state is limited to two database pods: one primary and one streaming replica on different nodes/AZs.
   - MongoDB steady state remains one database pod plus one resource-capped PBM agent.
   - Backup jobs are bounded and terminate after completion.
   - No immediate three-replica rollout.

2. Recoverability
   - PostgreSQL supports point-in-time recovery through base backup plus WAL archive.
   - MongoDB supports consistent replica-set backup and PITR where the selected PBM version supports it.
   - EBS snapshots are a secondary fast-recovery layer, not the only backup.
   - Restore drills prove that backups are usable.

3. Security
   - Backup workloads use IRSA; no static AWS credentials are stored in Git or Kubernetes values.
   - Backup buckets are private, versioned, SSE-KMS encrypted, and protected with a bounded Object Lock governance retention period.
   - Backup and document content use separate buckets and IAM roles.

4. Safe migration
   - PostgreSQL source and target coexist until validation completes.
   - MongoDB replica-set conversion is performed under an audit-write maintenance window.
   - PVC/EBS deletion and AWS secret deletion are separate, delayed destructive steps.

5. GitOps integrity
   - All desired-state changes reach `gitops-testing`, the branch watched by Argo CD (`infra/argocd-apps/README.md:7-9`).
   - Cutover is split into reversible commits; infrastructure creation, data migration, endpoint switch, and old-resource retirement are not bundled.

## Current-State Evidence

- Metadata PostgreSQL is a one-replica StatefulSet with a 10 GiB EBS-backed PVC and resource request `100m/256Mi` (`infra/k8s/infra-deps/base/postgres.yaml:46-58`, `infra/k8s/infra-deps/base/postgres.yaml:118-142`).
- Audit MongoDB is a one-replica StatefulSet with a 10 GiB EBS-backed PVC and resource request `100m/256Mi` (`infra/k8s/infra-deps/base/mongodb.yaml:21-33`, `infra/k8s/infra-deps/base/mongodb.yaml:81-100`).
- The StorageClass uses encrypted gp3 EBS and `Retain` (`infra/k8s/infra-deps/base/storageclass.yaml:7-13`).
- CloudNativePG is already exercised by Keycloak with three instances (`infra/k8s/infra-deps/base/keycloak-postgres.yaml:1-11`); runtime operator version is `1.29.1`.
- Metadata-service accepts explicit environment entries and `envFrom` through the shared chart (`infra/k8s/charts/docvault-service/templates/deployment.yaml:82-95`), and its migration job consumes the same values (`infra/k8s/charts/docvault-service/templates/migration-job.yaml:77-81`).
- Prisma uses `DATABASE_URL_RUNTIME` when present, otherwise `DATABASE_URL` (`services/metadata-service/src/prisma/prisma.service.ts:9-17`).
- The metadata schema stores the S3 object-key mapping and ACL/version state that must remain consistent with document blobs (`services/metadata-service/prisma/schema.prisma:95-156`).
- Audit-service reads `MONGODB_URI` directly (`services/audit-service/src/app.module.ts:21-24`).
- document-service is configured for native AWS S3, SSE-KMS, IRSA, and no static credentials (`infra/k8s/values/document-service.yaml:20-27`, `infra/k8s/values/document-service.yaml:39-43`).
- The repository already contains an EKS overlay that removes MinIO resources but preserves its PVC (`infra/k8s/infra-deps/overlays/testing-s3/kustomization.yaml:4-35`, `infra/k8s/infra-deps/overlays/testing-s3/README.md:3-14`).
- Commit `f114d1b` contains the MinIO retirement overlay and changes local `docvault-infra.yaml` to `testing-s3`, but the live Argo Application still resolves `infra/k8s/infra-deps/overlays/testing`; promotion to `gitops-testing` is therefore required.
- Current measured load is low, but scheduling metadata is incomplete: no pods are Pending, node memory working set is about 18-30%, node CPU usage about 3-6%, while one node uses 29 of 35 pod slots. CloudNativePG PostgreSQL pods currently lack requests/limits, so explicit resource declarations are mandatory for new database workloads.

## Architecture Decision

### PostgreSQL

- Resource: `Cluster/metadata-postgres` in namespace `docvault`.
- Initial `instances: 2`; PostgreSQL major version stays at 16.
- Placement: required hostname anti-affinity plus zone-aware scheduling so the primary and replica do not share a worker node or AZ while both AZs are healthy.
- Storage: `docvault-gp3`, initially 10 GiB, expansion enabled by the existing StorageClass.
- Resource envelope: start at the current request/limit (`100m/256Mi`, `500m/512Mi`) and adjust only from Prometheus evidence.
- Application endpoint: CNPG `metadata-postgres-rw` service.
- Credentials: CNPG-generated application secret; inject its URI into metadata-service as an explicit `DATABASE_URL` value so it overrides the shared `envFrom` value.
- Backup: Barman Cloud integration/plugin compatible with CNPG `1.29.1`, daily base backup, continuous WAL archive, 30-day recovery window.
- Scaling path: change `instances` from 2 to 3 after a third AZ/subnet is available and failover/resource requirements are approved.

### MongoDB

- Preserve the existing `mongo-0` PVC and MongoDB 7 data files.
- Convert the server to replica set `rs0` with one data-bearing member.
- Store the replica-set keyfile in AWS Secrets Manager and surface it through External Secrets; mount it read-only with MongoDB-compatible ownership/mode.
- Update `MONGODB_URI` to include `replicaSet=rs0` after the replica set is healthy.
- Run one PBM agent with strict resources and use a CronJob only to trigger/monitor backups.
- Preferred backup: PBM full backup daily plus PITR/oplog archive to S3, retained 30 days.
- Compatibility gate: validate the pinned PBM version against MongoDB 7 and one-member replica sets before deployment. If that gate fails, use a pinned backup-tool image to run `mongodump --oplog --archive --gzip` every six hours and upload via IRSA; record the resulting six-hour RPO explicitly.
- Scaling path: introduce Percona Operator and scale to three data-bearing members later; it is not required for this plan.

### Backup storage

- Add a dedicated Terraform module, proposed path `infra/terraform/modules/database-backups/`.
- Create two buckets to keep retention and IAM boundaries independent:
  - `docvault-postgres-backups-<environment>-<account-id>`
  - `docvault-mongodb-backups-<environment>-<account-id>`
- Create one dedicated backup KMS key with rotation and a 30-day deletion window, or two keys if compliance isolation is preferred during implementation review.
- Enable bucket versioning, public-access blocking, TLS-only policies, SSE-KMS, and Object Lock governance retention for 30 days.
- Add lifecycle rules only after Object Lock behavior is verified; lifecycle must not promise deletion before retained versions can legally expire.
- Create least-privilege IRSA roles tied to exact service accounts for PostgreSQL backup and MongoDB backup.
- Expose bucket names, KMS ARN, and role ARNs from `infra/terraform/aws-eks/outputs.tf`, following the existing document-storage output pattern (`infra/terraform/aws-eks/outputs.tf:56-68`).
- Instantiate the module beside the existing document-storage module (`infra/terraform/aws-eks/main.tf:49-58`).

## Implementation Steps

### 1. Establish a recovery baseline before changing desired state

Files/runbooks:
- Add `docs/database-backup-and-restore-runbook.md`.
- Reference existing database manifests at `infra/k8s/infra-deps/base/postgres.yaml` and `infra/k8s/infra-deps/base/mongodb.yaml`.

Actions:
- Record current PostgreSQL table/row counts, Prisma migration state, extensions, roles, RLS policies, and database size.
- Record current MongoDB database/collection counts, indexes, latest audit event timestamp, active epoch, and audit hash-chain verification result.
- Create one pre-migration PostgreSQL logical dump and one MongoDB dump outside the database PVCs.
- Record the source EBS volume IDs for PostgreSQL, MongoDB, and MinIO.
- Snapshot source PostgreSQL and MongoDB EBS volumes before migration.
- Define the maintenance window and identify the exact commands for scaling metadata-service/audit-service to zero during final write freeze.

Acceptance:
- Baseline evidence and snapshot IDs are attached to the runbook.
- Both pre-migration dumps can be listed/read from their protected location.
- No migration begins without a known rollback volume and source connection string.

### 2. Provision S3/KMS/IRSA backup infrastructure

Files:
- Add `infra/terraform/modules/database-backups/main.tf`.
- Add `infra/terraform/modules/database-backups/variables.tf`.
- Add `infra/terraform/modules/database-backups/outputs.tf`.
- Update `infra/terraform/aws-eks/main.tf:49-58` with the new module.
- Update `infra/terraform/aws-eks/outputs.tf:56-68` with backup outputs.
- Update the nearest Terraform README/runbook with apply and rollback instructions.

Actions:
- Implement buckets, KMS, bucket policies, Object Lock, lifecycle, and two IRSA roles.
- Scope trust policies to exact namespace/service-account subjects.
- Deny insecure transport and non-KMS uploads.
- Do not grant backup writers broad `s3:DeleteObjectVersion` or KMS administration.

Verification:
- `terraform fmt -check -recursive infra/terraform`.
- `terraform -chdir=infra/terraform/aws-eks init` using the existing state safely.
- `terraform -chdir=infra/terraform/aws-eks validate`.
- Review `terraform plan` for two buckets, KMS/IAM resources, and no replacement of EKS/document-storage resources.
- After apply, query bucket versioning, encryption, Object Lock, lifecycle, and public-access-block status with AWS CLI.
- Assume each IRSA role from its service account and prove it cannot access the document bucket or the other database's backup bucket.

### 3. Deploy CloudNativePG metadata cluster without cutting over the application

Files:
- Add `infra/k8s/infra-deps/overlays/testing-s3/metadata-postgres.yaml`.
- Add the resource to `infra/k8s/infra-deps/overlays/testing-s3/kustomization.yaml`.
- Add backup-store/plugin resources in the same overlay or a focused `database-backups/` component.
- Update `infra/k8s/infra-deps/overlays/testing-s3/README.md`.

Actions:
- Pin a PostgreSQL 16 CNPG image by immutable tag/digest that is compatible with CNPG `1.29.1`.
- Create `metadata-postgres` with `instances: 2`, per-instance resource requests/limits, `docvault-gp3`, explicit database/owner, required hostname anti-affinity, zone-aware placement, and monitoring labels.
- Wire Barman Cloud to the PostgreSQL backup bucket through IRSA.
- Create a `ScheduledBackup` for one daily base backup and enable continuous WAL archive.
- Leave metadata-service pointing at the old `db` service.
- Keep `migration.enabled` unchanged until data restore sequencing is explicit.

Verification:
- `Cluster/metadata-postgres` reports healthy with exactly two ready instances: one primary and one replica on different nodes and AZs.
- `metadata-postgres-rw` resolves and accepts TLS/authenticated connections.
- A forced base backup reaches `Completed` and WAL objects appear in the correct S3 prefix with SSE-KMS.
- Resource requests/limits appear in the pod spec.
- No new pod is Pending and no node exceeds the resource gates in Acceptance Criteria.

### 4. Migrate PostgreSQL data and cut metadata-service over

Files:
- Add a bounded migration Job or runbook-owned manifest under `infra/k8s/jobs/metadata-postgres-migration.yaml`.
- Update `infra/k8s/values/metadata-service.yaml:23-30` to inject the CNPG secret URI explicitly.
- Update the `docvault-metadata` Argo Application in `infra/argocd-apps/docvault-apps.yaml:34-65` so the intentional `env` change is not suppressed by the current blanket ignore rule.

Actions:
- Use at least three GitOps stages:
  1. target cluster and backup resources only;
  2. data copy/validation while the application still uses the source;
  3. endpoint cutover after a short write freeze.
- During the final freeze, scale metadata-service to zero or otherwise reject writes.
- Capture PostgreSQL globals required by the application (`pg_dumpall --globals-only` or explicit role recreation), dump `docvault_metadata` in custom format, restore to the CNPG target, and reapply/verify required extensions and RLS policies.
- Run Prisma `migrate deploy` against the target after restore, not before the source dump is finalized.
- Update metadata-service to use the CNPG `uri` key as explicit `DATABASE_URL`, then restart it.
- Resume writes only after row counts, constraints, indexes, ACL/version mappings, and a create/upload/approve/read smoke flow pass.

Rollback:
- Scale metadata-service down, restore the old `DATABASE_URL`, restart against `db`, and discard target writes made after cutover or replay them through an explicitly reviewed reconciliation step.
- Keep old `StatefulSet/db`, Service, PVC, secret, and source EBS snapshot for at least seven stable days.

Verification:
- Source/target table counts match for every application table.
- `prisma migrate status` reports no pending migration.
- RLS verification passes where enabled.
- Metadata health, document list/detail, ACL checks, workflow history, retention, and S3 object-key lookup pass.
- A post-cutover CNPG backup and PITR restore to a disposable cluster recovers a timestamp after cutover.

### 5. Convert MongoDB to a one-member replica set

Files:
- Update `infra/k8s/infra-deps/base/mongodb.yaml:21-100` or overlay it in `testing-s3` with the replica-set command/keyfile mount.
- Add an ExternalSecret for the replica-set keyfile under the testing overlay.
- Add an idempotent replica-set initialization Job under `infra/k8s/infra-deps/overlays/testing-s3/`.
- Update `docs/infra/aws_secrets_manager_external_secrets.md` with the keyfile secret shape without real secret material.

Actions:
- Create the keyfile in AWS Secrets Manager first.
- Enter an audit-write maintenance window and capture a fresh dump/snapshot.
- Restart the same MongoDB pod/PVC with `--replSet rs0`, authentication, and keyfile.
- Run an idempotent `rs.initiate()` job and wait for the sole member to become PRIMARY.
- Update `/docvault/testing/app` `MONGODB_URI` to include `replicaSet=rs0`, then restart audit-service.
- Verify index definitions and audit hash-chain before reopening writes.

Rollback:
- Stop audit-service, revert the MongoDB command and URI, restart against the unchanged PVC, and verify the pre-change dump/snapshot remains available.

Verification:
- `rs.status()` reports one healthy PRIMARY.
- Audit create/query/verify-chain endpoints pass.
- No duplicate-key or chain-link regression appears during the first post-cutover writes.

### 6. Add MongoDB PBM backup/PITR with a bounded fallback

Files:
- Add PBM agent/config resources under `infra/k8s/infra-deps/overlays/testing-s3/database-backups/`.
- Add a ServiceAccount annotated with the MongoDB backup IRSA role.
- Add a CronJob for backup triggering and retention checks.
- Add restore commands and failure handling to `docs/database-backup-and-restore-runbook.md`.

Actions:
- Pin PBM image/version and prove MongoDB 7 plus one-member replica-set compatibility before apply.
- Configure the MongoDB backup bucket, KMS key, PITR/oplog slicing, and daily full backup.
- Set explicit resources for agent and trigger job; initial cap should not reserve more than `50m/128Mi` for the agent and `200m/256Mi` for the backup job without measured justification.
- If PBM compatibility fails, implement the documented `mongodump --oplog` fallback and declare the resulting RPO from its schedule.

Verification:
- The backup catalog shows a completed backup with no stale lock.
- PITR/oplog objects advance after new audit writes.
- Restore into an isolated MongoDB instance and run collection/index counts plus audit verify-chain.
- Backup failures generate a Prometheus alert and non-zero CronJob status.

### 7. Promote and retire MinIO from EKS

Files/state:
- Existing commit `f114d1b` changes `infra/argocd-apps/docvault-infra.yaml:10-14` to `testing-s3` and adds delete patches in `infra/k8s/infra-deps/overlays/testing-s3/kustomization.yaml:7-35`.
- Update stale EKS-specific descriptions in `infra/argocd-apps/README.md:17-22`, `infra/k8s/README.md`, `infra/k8s/infra-deps/README.md:35-48`, and `infra/k8s/values/README.md:26-30`.
- Preserve Docker Compose MinIO references in `infra/docker-compose.dev.yml:42-74` and local-development documentation.

Actions:
- Promote/cherry-pick the MinIO retirement commit to `gitops-testing`; do not point the live Application at the feature branch.
- Sync the root Application and verify live `docvault-infra-deps.spec.source.path` becomes `infra/k8s/infra-deps/overlays/testing-s3`.
- Before pruning MinIO, prove document-service runtime still has empty `S3_ENDPOINT`, `S3_USE_STATIC_CREDENTIALS=false`, the expected AWS bucket, KMS key, and IRSA service account.
- Run upload/download/preview/stream/malware-block smoke tests against AWS S3.
- Prune/delete only `ExternalSecret/minio-secret`, `StatefulSet/minio`, `Service/minio`, and `Job/minio-init` initially.
- Preserve `PVC/minio-data-minio-0`, its retained PV, EBS volume, and `/docvault/testing/minio` AWS secret for a seven-day rollback window.

Destructive cleanup gate after seven stable days:
- Confirm there are no MinIO endpoints, secret refs, DNS lookups, or failed application requests.
- Inventory any remaining MinIO objects and record the decision to discard or archive them.
- Delete the PVC, then explicitly delete the retained PV/EBS volume only after verifying the resolved volume ID.
- Delete `/docvault/testing/minio` from Secrets Manager only after the ExternalSecret and all consumers are absent.

Verification:
- `kubectl get statefulset,service,job,externalsecret -n docvault` returns no MinIO resources.
- `kubectl get pvc minio-data-minio-0 -n docvault` remains Bound/retained during rollback window, then is absent only after the cleanup gate.
- document-service upload/download paths pass and new objects appear only in AWS S3.
- No Argo resource is OutOfSync solely because of MinIO.

### 8. Retire old metadata PostgreSQL only after restore evidence

Files:
- Add delete patches for the old `StatefulSet/db`, `Service/db`, `ConfigMap/postgres-init-sql`, and `ExternalSecret/postgres-secret` to the testing S3 overlay after the rollback window.
- Update `infra/k8s/infra-deps/README.md:35-48` to describe CNPG metadata storage and backup.

Actions:
- Confirm no pod resolves/connects to `db:5432`.
- Confirm a CNPG S3 restore contains post-cutover data.
- Remove old Kubernetes workload/service resources.
- Preserve the old PVC/PV/EBS for the agreed retention window, then delete through a separately reviewed destructive operation.
- Remove the old `/docvault/testing/postgres` secret only if no remaining consumer uses it.

Verification:
- Metadata-service uses `metadata-postgres-rw`.
- Old `db` Service and StatefulSet are absent.
- CNPG backup freshness and restore alerting remain green after source retirement.

### 9. Add observability and recurring restore drills

Files:
- Add PrometheusRule resources under the existing monitoring/GitOps surface.
- Add Grafana panels or runbook queries for backup age, last success, WAL/oplog archive lag, job failures, PVC use, and database readiness.
- Update `docs/database-backup-and-restore-runbook.md` with monthly drills.

Alerts:
- PostgreSQL base backup older than 26 hours.
- PostgreSQL WAL archive failure/lag beyond the selected RPO.
- MongoDB backup older than 26 hours.
- PBM PITR/oplog archive stalled.
- Backup CronJob failure or active deadline exceeded.
- S3/KMS access denied.
- Database pod not ready.

Restore cadence:
- Monthly restore PostgreSQL into a disposable CNPG cluster and run migrations/count checks.
- Monthly restore MongoDB into an isolated replica set and run audit verify-chain.
- Quarterly simulate loss of the original namespace and recover using only Git, Secrets Manager, S3 backup, and KMS/IAM.

## Acceptance Criteria

1. Resource envelope
   - No Pending pods or node MemoryPressure/DiskPressure after each stage.
   - Five-minute node CPU remains below 70% and memory working set below 70% during normal operation.
   - Backup jobs respect declared requests/limits and terminate within their active deadline.
   - No node exceeds 32 of 35 pod slots after scheduling changes.

2. PostgreSQL
   - Exactly two metadata CNPG instances are ready, with primary and replica on different nodes and AZs.
   - PostgreSQL major version is 16 until a separate upgrade plan is approved.
   - Metadata-service and migration Job use the CNPG endpoint/secret.
   - Source/target row counts, indexes, constraints, extensions, Prisma state, and required RLS policies match.
   - Daily base backup and continuous WAL archive are visible in S3.
   - PITR restore recovers to a chosen post-cutover timestamp.

3. MongoDB
   - `rs0` reports one healthy PRIMARY and audit-service uses a replica-set URI.
   - Daily backup succeeds; PITR/oplog archive advances where PBM compatibility is validated.
   - Isolated restore reproduces collection/index counts and passes audit hash-chain verification.

4. MinIO
   - No MinIO StatefulSet, Service, Job, ExternalSecret, or running pod remains in EKS.
   - document-service continues upload/download/preview/stream behavior through AWS S3/KMS.
   - MinIO PVC/EBS and AWS secret are deleted only after the rollback window and explicit destructive gate.
   - Local Docker Compose remains functional with MinIO.

5. Security
   - No static AWS access keys are introduced.
   - Backup service accounts can access only their backup bucket/prefix and KMS use path.
   - Buckets are private, versioned, Object-Locked, TLS-only, and SSE-KMS encrypted.
   - Restore credentials and database URLs do not appear in logs, Git diffs, or plan output.

6. GitOps
   - All intended changes are present on `gitops-testing`.
   - Argo Applications are Synced/Healthy after each staged commit.
   - No manual runtime patch is left without an equivalent desired-state change or documented one-shot migration exception.

## Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Live Argo Application watches `gitops-testing`, while MinIO retirement currently exists only outside that live desired state | MinIO remains or is recreated | Promote the existing commit to `gitops-testing`, verify the live Application path before prune |
| Blanket Argo `env` ignore suppresses CNPG URL rollout | Metadata continues using old DB | Narrow/remove the ignore rule for metadata and verify rendered/live environment source before cutover |
| PostgreSQL major-version drift | Restore/runtime incompatibility | Pin PostgreSQL 16 for this migration; upgrade separately |
| Prisma migration runs before restore is complete | Schema/data conflict | Stage target creation, restore, migration, and endpoint switch in separate commits/jobs |
| Global roles/RLS omitted from `pg_dump` | Runtime authorization failure | Capture globals explicitly and verify RLS policies/roles before opening writes |
| PostgreSQL has only one replica and MongoDB remains singleton | PostgreSQL tolerates one instance/AZ loss but has less redundancy than a three-instance cluster; MongoDB node/AZ failure still causes downtime | Enforce cross-AZ placement for PostgreSQL, retain S3 recovery paths for both databases, and scale to three data-bearing instances only when capacity permits |
| MongoDB replica-set conversion with auth lacks a valid keyfile | MongoDB fails to start | Create/mount/test keyfile first; retain reversible command/URI and snapshot |
| PBM/MongoDB version incompatibility | No PITR/backup | Pin and test compatibility; use documented `mongodump --oplog` fallback |
| Backup jobs saturate EBS/network | Application latency | Run off-peak, cap resources, alert on duration, measure before increasing frequency |
| Object Lock prevents expected lifecycle deletion | Unexpected storage cost | Use bounded governance retention and test lifecycle behavior before broad retention |
| Deleting PVC with `Retain` leaves EBS cost or deleting the wrong EBS loses rollback | Cost/data loss | Separate PVC, PV, and EBS cleanup; verify exact volume IDs at each gate |
| Current Kubernetes Metrics API is absent | Scheduler/usage decisions use incomplete data | Use existing Prometheus queries for gates and add/restore Metrics API as a follow-up |

## Verification Commands/Surfaces

- Render Kustomize overlays and assert MinIO absence plus CNPG/PBM presence.
- Render metadata Helm chart and inspect `DATABASE_URL`, migration Job, resources, and service account.
- Run Terraform format, validate, plan, and Checkov against the backup module.
- Run targeted metadata-service and audit-service tests, then `pnpm test:e2e` after each database cutover.
- Query AWS S3/KMS/IAM configuration and backup object freshness.
- Query CNPG `Cluster`, `Backup`, and `ScheduledBackup` status.
- Query MongoDB `rs.status()`, PBM status/list, collection/index counts, and audit verify-chain.
- Query Prometheus for node CPU/memory, pod working set, backup duration, and failure alerts.
- Record source/target counts and restore-drill evidence in the runbook.

## Rollout Order and Commit Boundaries

1. Add runbook/baseline only.
2. Add Terraform backup infrastructure; apply and validate.
3. Add CNPG target and PostgreSQL backup; no app cutover.
4. Migrate data and cut metadata-service over.
5. Convert MongoDB to one-member replica set.
6. Add PBM backup/PITR and restore proof.
7. Promote existing testing-S3 overlay and prune MinIO workload resources, preserving PVC/EBS.
8. After seven stable days and restore proof, retire old PostgreSQL and delete retained MinIO/source storage through explicit destructive gates.
9. Finalize alerts, documentation, and recurring restore schedule.

## Boundaries / Non-Goals

- No PostgreSQL or MongoDB three-replica HA rollout in this plan.
- No PostgreSQL major-version upgrade beyond 16.
- No removal of MinIO from local Docker Compose.
- No cross-region replication in the initial testing rollout; design bucket/IAM boundaries so it can be added later.
- No Terraform state-backend migration; the existing local-state risk should be handled separately.
- No automatic deletion of retained PVC/PV/EBS volumes or AWS secrets.

## Execution Handoff

Recommended execution lane: a persistent single-owner workflow because cutovers and restore gates are sequential and stateful. Parallel work is safe only for the independent Terraform backup module, observability/runbook, and pre-cutover manifest preparation; database cutovers must remain sequential.

Suggested role allocation if execution is delegated later:

- `executor` (medium): Terraform backup module and Kubernetes manifests.
- `dependency-expert` (high): pin CNPG Barman/PBM versions and verify compatibility.
- `test-engineer` (medium): migration invariants, restore drills, and E2E evidence.
- `verifier` (high): destructive-gate checks, resource measurements, and final claims.
- `git-master` (high): staged promotion to `gitops-testing` with rollback-safe commit boundaries.

Do not execute PVC/PV/EBS or Secrets Manager deletion in a parallel worker lane.
