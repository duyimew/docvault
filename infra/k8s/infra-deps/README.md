# DocVault Infra Dependencies

These manifests are for the first EKS/GitOps demo only. They deploy Postgres, MongoDB, MinIO, Keycloak, and application secrets inside the `docvault` namespace so the app can boot quickly.

Stateful demo data is persisted with the `docvault-gp3` StorageClass backed by the AWS EBS CSI driver:

- PostgreSQL: `postgres-data-db-0`, `10Gi`, mounted at `/var/lib/postgresql/data`.
- MongoDB: `mongo-data-mongo-0`, `10Gi`, mounted at `/data/db`.
- MinIO: `minio-data-minio-0`, `20Gi`, mounted at `/data`.

The StorageClass uses encrypted `gp3` EBS volumes, `WaitForFirstConsumer`, and `Retain` reclaim policy so demo data survives Pod restarts and accidental PVC removal requires an explicit PV/EBS cleanup.

If upgrading from the older `emptyDir` manifests, prune or delete the old Deployments after the StatefulSets are healthy:

```bash
kubectl delete deployment -n docvault db mongo minio --ignore-not-found
```

Services select the new StatefulSet pods using the `component` label so traffic does not mix old `emptyDir` pods with PVC-backed pods during migration.

Production hardening work should replace:

- Plain Kubernetes `Secret` manifests with SealedSecrets, SOPS, External Secrets, or AWS Secrets Manager integration.
- In-cluster demo dependencies with managed AWS services where appropriate.

Apply through Argo CD with:

```bash
kubectl apply -f infra/argocd-apps/docvault-infra.yaml
```
