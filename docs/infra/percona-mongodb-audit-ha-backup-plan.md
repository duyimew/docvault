# Percona MongoDB Audit HA + Backup Plan

Status: draft for review  
Created: 2026-06-23  
Scope: EKS testing environment, namespace `docvault`, GitOps branch `gitops-testing`  
Target service: `audit-service`

## Outcome

Move audit persistence from the current singleton `mongo` StatefulSet to a Percona-managed MongoDB deployment with S3/KMS backups, while preserving rollback to the existing MongoDB PVC until the new cluster and restore path are proven.

Target end state:

- Percona Server for MongoDB Operator is installed through GitOps.
- A resource-capped MongoDB replica set serves `audit-service`.
- MongoDB backup writes to a dedicated S3 bucket using IRSA and SSE-KMS.
- `audit-service` uses the new MongoDB connection string explicitly, not implicitly through the shared `docvault-app-secrets`.
- Old `StatefulSet/mongo` is retired only after migration verification; old PVC/PV/EBS is retained until explicit destructive cleanup approval.

Non-goal for this first rollout: full AZ-loss tolerance. The current cluster has only two AZs: one node in `ap-southeast-1a` and two nodes in `ap-southeast-1b`. A 3-member replica set can tolerate a pod/node failure, but not every possible AZ-loss scenario.

## Current Evidence

### Current MongoDB and audit-service

- Current MongoDB is a singleton `StatefulSet/mongo` with `replicas: 1` in `infra/k8s/infra-deps/base/mongodb.yaml:21-33`.
- Current MongoDB image is `mongo:7` in `infra/k8s/infra-deps/base/mongodb.yaml:51-54`.
- Current MongoDB resources are `requests: 100m/256Mi`, `limits: 500m/512Mi` in `infra/k8s/infra-deps/base/mongodb.yaml:81-87`.
- Current MongoDB PVC is `10Gi` on `docvault-gp3` in `infra/k8s/infra-deps/base/mongodb.yaml:91-100`.
- Current MongoDB credentials come from AWS Secrets Manager path `/docvault/__SECRETS_ENVIRONMENT__/mongodb` via `ExternalSecret/mongodb-secret` in `infra/k8s/infra-deps/base/mongodb.yaml:1-19`.
- `audit-service` reads MongoDB from `MONGODB_URI` in `services/audit-service/src/app.module.ts:21-22`.
- `audit-service` currently gets `MONGODB_URI` through `envFrom: docvault-app-secrets` in `infra/k8s/values/audit-service.yaml:22-24`.
- The live `docvault-app-secrets` contains `MONGODB_URI` pointing to `mongo:27017/docvault_audit?authSource=admin`.
- The Argo Application for `docvault-audit-service` currently ignores `/spec/template/spec/containers/0/env` in `infra/argocd-apps/docvault-apps.yaml:89-93`; this can suppress an explicit `MONGODB_URI` override during cutover, same class of issue already seen during metadata PostgreSQL cutover.

### Current cluster capacity

Measured scheduler capacity on 2026-06-23:

| Node | Zone | Allocatable | Current requests | Approx request headroom | Pods |
| --- | --- | --- | --- | --- | --- |
| `ip-10-20-1-223` | `ap-southeast-1a` | `1930m`, `7248308Ki` | `780m`, `1128Mi` | `~1150m`, `~6Gi` | `18/35` |
| `ip-10-20-2-124` | `ap-southeast-1b` | `1930m`, `7248300Ki` | `780m`, `1584Mi` | `~1150m`, `~5.5Gi` | `17/35` |
| `ip-10-20-2-48` | `ap-southeast-1b` | `1930m`, `7248308Ki` | `1000m`, `1538Mi` | `~930m`, `~5.6Gi` | `28/35` |

No pods are currently Pending.

Important gap: `kubectl top nodes` returns `Metrics API not available`, so actual working-set CPU/RAM must be checked through Prometheus/Grafana or metrics-server should be fixed before treating this as production-grade capacity evidence.

## Architecture Decision

### Recommended rollout shape

Use a phased Percona deployment:

1. Install Percona Operator and CRDs.
2. Create a new Percona MongoDB cluster for audit data.
3. Configure backup to S3/KMS and prove a backup completes.
4. Freeze audit writes, migrate data from the old `mongo`, verify counts/indexes/hash chain.
5. Cut `audit-service` over by explicit `MONGODB_URI` override.
6. Retire old `mongo` workload, preserving the old PVC/PV/EBS for rollback.

### Recommended MongoDB sizing

Initial Percona cluster should be intentionally small:

```yaml
replicaSet members: 3
per-member requests:
  cpu: 100m-200m
  memory: 512Mi
per-member limits:
  cpu: 500m
  memory: 1Gi
storage:
  size: 10Gi
  storageClass: docvault-gp3
```

Expected additional scheduler requests during migration:

- Percona operator: roughly `50m-100m CPU`, `128Mi-256Mi memory`.
- Three MongoDB members: roughly `300m-600m CPU`, `1536Mi memory`.
- Backup trigger/agent overhead: roughly `50m-200m CPU`, `128Mi-256Mi memory`.
- Old MongoDB remains during migration: `100m CPU`, `256Mi memory`.

This is schedulable based on current request headroom, but it should be guarded by explicit requests/limits and topology rules.

### Placement

Required placement policy:

- Hard or preferred anti-affinity by hostname so MongoDB members do not land on the same node.
- Topology spread by `topology.kubernetes.io/zone` where supported.
- Avoid scheduling all new MongoDB pods to the already dense node `ip-10-20-2-48`, which currently has `28/35` pods.

Reality check:

- With three nodes across two AZs, at least two MongoDB members will land in `ap-southeast-1b`.
- This improves node-level HA but does not guarantee quorum if the wrong AZ is lost.
- For real AZ-level HA, add a third node group/subnet/AZ before claiming 3-AZ durability.

## Implementation Plan

### Phase 0 — Preflight and baseline

Actions:

1. Record current audit MongoDB state:
   - database name;
   - collection counts;
   - index definitions;
   - latest audit event timestamp;
   - latest chain epoch/incident state;
   - audit hash-chain verification result.
2. Confirm current `audit-service` runtime `MONGODB_URI` host/database without printing password.
3. Take a logical backup from old MongoDB:
   - `mongodump --archive --gzip`;
   - store outside the MongoDB PVC.
4. Record old MongoDB PVC/PV/EBS IDs:
   - `pvc/mongo-data-mongo-0`;
   - bound PV;
   - underlying EBS volume.
5. Confirm no Pending pods and no node pressure conditions before adding Percona.

Acceptance criteria:

- Baseline counts/indexes are recorded.
- A readable dump exists outside the source PVC.
- Rollback source PVC and EBS volume ID are known.
- `audit-service` remains healthy before any migration.

### Phase 1 — Add backup infrastructure for MongoDB

Preferred resources:

- Dedicated S3 bucket: `docvault-mongodb-backups-testing-913355241407`.
- Dedicated KMS key or a separate alias under the existing backup key pattern.
- Dedicated IRSA role scoped to the Percona/PBM backup service account.

Do not reuse the document-service bucket. Keep document blobs and database backups isolated for IAM, lifecycle, retention, and incident response.

Terraform files:

- Add or extend a module under `infra/terraform/modules/`.
- Instantiate it from `infra/terraform/aws-eks/main.tf`.
- Expose outputs in `infra/terraform/aws-eks/outputs.tf`.

Security requirements:

- S3 public access block enabled.
- Versioning enabled.
- SSE-KMS enforced.
- TLS-only bucket policy.
- IRSA trust policy restricted to exact service account subject.
- Object access restricted to MongoDB backup prefix.
- Bucket-level inspection permissions allowed where the backup tool requires `HeadBucket` / `GetBucketLocation`.

Verification:

- `terraform fmt -recursive`.
- `terraform validate`.
- `terraform plan` shows only intended S3/KMS/IAM resources.
- AWS CLI verifies bucket encryption/versioning/public access block.
- IRSA role cannot access document bucket or CNPG backup bucket.

### Phase 2 — Install Percona Operator through GitOps

Recommended GitOps shape:

- Prefer a dedicated Argo Application for Percona Operator/CRDs if the operator install is Helm-based or contains cluster-scoped CRDs.
- Otherwise place manifests under a clearly separated infra component:
  - `infra/k8s/infra-deps/overlays/testing-s3/percona-mongodb-operator/`

Why separate operator from DB cluster:

- CRDs and controller upgrades have a different lifecycle from audit data.
- A failed MongoDB cluster rollout should not require reinstalling CRDs.
- Future operator upgrades can be reviewed independently.

Pre-implementation gate:

- Confirm the exact Percona Operator version and CRD fields from official Percona docs for the selected version.
- Confirm support matrix for MongoDB 7.
- Confirm backup configuration format for S3/KMS/PBM.

Resource envelope:

- Operator requests should be explicitly set.
- Initial recommendation:
  - requests: `50m CPU`, `128Mi memory`;
  - limits: `200m CPU`, `256Mi memory`.

Verification:

- Operator deployment ready.
- CRDs established.
- No Pending pods.
- Argo Application Synced/Healthy.

### Phase 3 — Deploy new Percona MongoDB cluster without cutover

Proposed resource name:

- `audit-mongodb`

Proposed namespace:

- `docvault`

Initial topology:

- 3 data-bearing members.
- 10Gi PVC per member on `docvault-gp3`.
- Anti-affinity/topology spread enabled.
- Resources explicitly capped:
  - requests: `100m-200m CPU`, `512Mi memory`;
  - limits: `500m CPU`, `1Gi memory`.

Do not update `audit-service` yet.

Verification:

- Percona cluster reports ready.
- Replica set has one PRIMARY and two SECONDARY members.
- Members are spread across different nodes.
- At least one member lands in `ap-southeast-1a`.
- No Pending pods.
- No node exceeds `32/35` pod slots.
- Backup agent, if installed with the cluster, is running and resource-capped.

### Phase 4 — Configure and prove MongoDB backup

Actions:

1. Configure PBM/S3 backup against the MongoDB backup bucket.
2. Trigger one immediate full backup.
3. Verify backup catalog reports success.
4. Verify objects exist in S3 and use SSE-KMS.
5. Restore the backup into a disposable isolated MongoDB instance or temporary Percona cluster.
6. Validate collection counts, indexes, and audit hash-chain on the restored copy.

Acceptance criteria:

- One full backup completed.
- Restore from backup completed.
- Restored data matches expected baseline.
- Audit hash-chain verification passes on restored data.

Fallback if PBM compatibility is blocked:

- Use `mongodump --oplog --archive --gzip` CronJob with IRSA to S3.
- Declare the actual RPO based on schedule, for example 6 hours.
- Keep PBM as follow-up instead of blocking all migration.

### Phase 5 — Migrate audit data

Actions:

1. Enter audit write freeze:
   - scale `docvault-audit-service` to `0`, or block writes at gateway if that path is safer.
2. Take final old MongoDB dump:
   - `mongodump --archive --gzip`.
3. Restore dump into `audit-mongodb`.
4. Recreate/verify indexes if restore does not preserve them as expected.
5. Run audit verification:
   - collection counts;
   - latest event timestamp;
   - hash-chain verification;
   - endpoint smoke checks after service restart.

Acceptance criteria:

- Old and new collection counts match.
- Indexes match.
- Audit hash-chain verification passes.
- No writes are accepted during the final dump/restore window.

Rollback:

- Keep old `StatefulSet/mongo`, `Service/mongo`, `mongodb-secret`, and PVC intact until after cutover verification.
- If validation fails, restart `audit-service` against old `mongo` by reverting only the app connection override.

### Phase 6 — Cut audit-service over

Required GitOps changes:

- Update `infra/k8s/values/audit-service.yaml` with explicit `envValueFrom` for `MONGODB_URI`, referencing the Percona-generated or ExternalSecret-managed connection secret.
- Remove or narrow the `ignoreDifferences` block for `docvault-audit-service` in `infra/argocd-apps/docvault-apps.yaml:89-93`; otherwise Argo may suppress the env rollout.
- Keep shared `docvault-app-secrets.MONGODB_URI` unchanged during the first cutover window for rollback.

Verification:

- Render Helm chart and confirm `MONGODB_URI` appears in Deployment env.
- Argo `docvault-audit-service` reaches Synced/Healthy.
- Live pod `MONGODB_URI` host points to new Percona MongoDB service, not `mongo`.
- `/health` returns `200`.
- Audit write/read/query smoke tests pass.
- Hash-chain verification passes after at least one new post-cutover audit event.

Rollback:

- Revert the audit-service values commit.
- Sync Argo.
- Confirm pod runtime URI points back to old `mongo`.
- Keep new Percona cluster intact for investigation.

### Phase 7 — Retire old MongoDB workload, retain old storage

Only after Phase 6 has passed.

GitOps retire resources:

- Add delete patches in `infra/k8s/infra-deps/overlays/testing-s3/kustomization.yaml` for:
  - `StatefulSet/mongo`;
  - `Service/mongo`;
  - `ExternalSecret/mongodb-secret`, only if no remaining consumer uses it.

Preserve:

- `PVC/mongo-data-mongo-0`;
- retained PV;
- underlying EBS volume;
- old AWS Secrets Manager entry during rollback window.

Acceptance criteria:

- `StatefulSet/mongo` absent.
- `pod/mongo-0` absent.
- `Service/mongo` absent.
- `audit-service` still uses Percona MongoDB and passes health/smoke tests.
- Old PVC remains `Bound` or retained until explicit destructive cleanup approval.

### Phase 8 — Observability and restore cadence

Add alerts/runbook coverage for:

- Percona Operator not ready.
- MongoDB replica set not healthy.
- Primary unavailable.
- Backup age > 26 hours.
- Backup failure.
- PITR/oplog archive stalled, if PBM PITR is enabled.
- PVC usage high.
- Audit-service connection errors.
- Node pod count above `32/35`.

Restore cadence:

- Monthly restore audit MongoDB backup to isolated environment.
- Verify collection counts, indexes, and hash chain.
- Record restore duration and RPO/RTO evidence.

## Acceptance Criteria

1. Scheduler/resource
   - No Pending pods after operator, cluster, and backup deployment.
   - Every Percona component has explicit requests/limits.
   - No node exceeds `32/35` pods.
   - No node pressure condition is present.

2. MongoDB HA
   - Replica set has one PRIMARY and two SECONDARY members.
   - MongoDB members are on separate nodes.
   - Placement limitation across only two AZs is documented.

3. Backup
   - At least one full backup completed.
   - Backup objects exist in S3 with SSE-KMS.
   - Restore drill passes collection count, index, and hash-chain checks.

4. Cutover
   - `audit-service` live `MONGODB_URI` no longer points to old `mongo`.
   - `/health` returns `200`.
   - Audit write/read/query path passes.
   - Post-cutover audit event participates in a valid hash chain.

5. Rollback safety
   - Old MongoDB PVC/PV/EBS remains available after workload retirement.
   - Shared `docvault-app-secrets.MONGODB_URI` is not overwritten during initial cutover.
   - Destructive storage cleanup is a separate explicit approval.

## Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Metrics API unavailable | Actual CPU/RAM usage is unknown | Use scheduler requests for initial sizing; check Prometheus/Grafana or fix metrics-server before high-confidence production sizing |
| Only two AZs | 3-member MongoDB replica set is not guaranteed AZ-failure tolerant | State this clearly; add third AZ/node group before claiming 3-AZ HA |
| Argo ignores audit Deployment env | `MONGODB_URI` cutover does not apply | Remove/narrow `ignoreDifferences` for `docvault-audit-service` before cutover |
| Operator CRD install mixed with DB cluster rollout | Failed CRD/operator upgrade can block DB rollout | Separate operator lifecycle from DB cluster resources |
| PBM compatibility/version mismatch | Backup/PITR does not work | Validate exact Percona Operator/PBM/MongoDB 7 compatibility before apply; fallback to `mongodump --oplog` |
| Resource defaults too high | Pods Pending or node pressure | Set explicit low requests/limits; deploy operator first, then DB, then backup |
| Migration while audit writes continue | Lost or inconsistent audit data | Scale audit-service to zero during final dump/restore |
| Early deletion of old Mongo PVC | Rollback impossible | Retain old PVC/PV/EBS until explicit cleanup gate |

## Rollout Commit Boundaries

1. Plan/runbook only.
2. Terraform MongoDB backup S3/KMS/IRSA.
3. Percona Operator/CRDs.
4. Percona MongoDB cluster, no cutover.
5. Backup config and restore drill.
6. Migration/cutover audit-service.
7. Retire old Mongo workload, retain PVC.
8. Final cleanup after explicit approval.

## Recommended Next Step

Do not start by cutting over audit-service.

Start with Phase 1 and Phase 2:

1. Add MongoDB backup bucket/KMS/IRSA.
2. Add Percona Operator through GitOps.
3. Verify operator resource footprint.

Only after that should the Percona MongoDB cluster be created.
