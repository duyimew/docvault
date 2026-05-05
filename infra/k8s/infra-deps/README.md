# DocVault Infra Dependencies

These manifests are for the first EKS/GitOps demo only. They deploy Postgres, MongoDB, MinIO, Keycloak, and application secrets inside the `docvault` namespace so the app can boot quickly.

Production hardening work should replace:

- Plain Kubernetes `Secret` manifests with SealedSecrets, SOPS, External Secrets, or AWS Secrets Manager integration.
- `emptyDir` database/object-store volumes with PVCs backed by EBS/EFS or managed AWS services.
- In-cluster demo dependencies with managed services where appropriate.

Apply through Argo CD with:

```bash
kubectl apply -f infra/argocd-apps/docvault-infra.yaml
```
