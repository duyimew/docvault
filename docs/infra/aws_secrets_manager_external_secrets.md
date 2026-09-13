# AWS Secrets Manager + External Secrets Operator

Updated: 2026-05-30

This replaces plaintext Kubernetes `Secret` manifests in `infra/k8s/infra-deps` with AWS Secrets Manager as the source of truth.

The Kubernetes workloads keep using the same secret names:

```text
docvault-app-secrets
postgres-secret
mongodb-secret
minio-secret
keycloak-secret
```

External Secrets Operator creates those Kubernetes Secrets from AWS Secrets Manager.

## Architecture

```text
AWS Secrets Manager
  -> External Secrets Operator on EKS
  -> Kubernetes Secret in namespace docvault
  -> DocVault pods read envFrom / secretKeyRef as before
```

The repo uses:

```text
infra/k8s/infra-deps/base/secretstore.yaml
infra/k8s/infra-deps/base/app-secrets.yaml
infra/k8s/infra-deps/base/postgres.yaml
infra/k8s/infra-deps/base/mongodb.yaml
infra/k8s/infra-deps/base/minio.yaml
infra/k8s/infra-deps/base/keycloak.yaml
infra/k8s/infra-deps/overlays/testing/kustomization.yaml
infra/terraform/aws-eks/external-secrets-irsa.tf
```

## 1. Apply Terraform IAM Role for ESO

From:

```powershell
cd infra/terraform/aws-eks
terraform plan -out tfplan
terraform apply tfplan
terraform output external_secrets_role_arn
```

Save the output ARN. It should look like:

```text
arn:aws:iam::<account-id>:role/docvault-eks-external-secrets
```

The IAM policy only allows reading secrets under:

```text
/docvault/testing/*
```

## 2. Create Secrets in AWS Secrets Manager

Create JSON secrets with these exact names.

### `/docvault/testing/postgres`

```json
{
  "POSTGRES_USER": "docvault",
  "POSTGRES_PASSWORD": "replace-me",
  "POSTGRES_DB": "docvault_metadata"
}
```

### `/docvault/testing/mongodb`

```json
{
  "MONGO_INITDB_ROOT_USERNAME": "root",
  "MONGO_INITDB_ROOT_PASSWORD": "replace-me"
}
```

### `/docvault/testing/app`

Use real connection strings when creating the AWS secret. The placeholders below intentionally avoid example passwords so repository secret scans do not treat the documentation as leaked credentials.

```json
{
  "DATABASE_URL": "<postgres-connection-url>",
  "MONGODB_URI": "<mongodb-connection-url>",
  "S3_ACCESS_KEY": "minioadmin",
  "S3_SECRET_KEY": "replace-me",
  "KEYCLOAK_ADMIN": "admin",
  "KEYCLOAK_ADMIN_PASSWORD": "replace-me",
  "KEYCLOAK_CLIENT_SECRET": "replace-me",
  "DOWNLOAD_GRANT_SECRET": "replace-me-with-long-random-value",
  "PREVIEW_GRANT_SECRET": "replace-me-with-long-random-value"
}
```

### `/docvault/testing/minio`

```json
{
  "MINIO_ROOT_USER": "minioadmin",
  "MINIO_ROOT_PASSWORD": "replace-me",
  "MINIO_BUCKET": "docvault"
}
```

### `/docvault/testing/keycloak`

```json
{
  "KEYCLOAK_ADMIN": "admin",
  "KEYCLOAK_ADMIN_PASSWORD": "replace-me"
}
```

PowerShell example:

```powershell
aws secretsmanager create-secret `
  --region ap-southeast-1 `
  --name /docvault/testing/postgres `
  --secret-string '{"POSTGRES_USER":"docvault","POSTGRES_PASSWORD":"replace-me","POSTGRES_DB":"docvault_metadata"}'
```

For existing secrets, use:

```powershell
aws secretsmanager put-secret-value `
  --region ap-southeast-1 `
  --secret-id /docvault/testing/postgres `
  --secret-string '{...}'
```

## 3. Install External Secrets Operator

Use Helm:

```powershell
helm repo add external-secrets https://charts.external-secrets.io
helm repo update

$esoRoleArn = terraform -chdir=infra/terraform/aws-eks output -raw external_secrets_role_arn

helm upgrade --install external-secrets external-secrets/external-secrets `
  -n external-secrets `
  --create-namespace `
  --set installCRDs=true `
  --set serviceAccount.name=external-secrets `
  --set serviceAccount.annotations."eks\.amazonaws\.com/role-arn"="$esoRoleArn"
```

Check:

```powershell
kubectl get pods -n external-secrets
kubectl get crd | findstr external-secrets
```

## 4. Apply Infra Dependencies

If old plaintext Kubernetes secrets already exist and were not created by ESO, delete them before applying:

```powershell
kubectl delete secret docvault-app-secrets postgres-secret mongodb-secret minio-secret keycloak-secret -n docvault --ignore-not-found
```

If you previously applied the files before creating the `docvault` namespace, clean up accidental default-namespace resources:

```powershell
kubectl delete statefulset db mongo minio -n default --ignore-not-found
kubectl delete service db mongo minio -n default --ignore-not-found
kubectl delete configmap postgres-init-sql keycloak-realm-config -n default --ignore-not-found
kubectl delete deployment keycloak -n default --ignore-not-found
kubectl delete job minio-init -n default --ignore-not-found
```

Apply infra deps through the testing Kustomize overlay:

```powershell
kubectl apply -k infra/k8s/infra-deps/overlays/testing
```

Or sync through Argo CD after External Secrets Operator is installed.

## 5. Verify

```powershell
kubectl get secretstore -n docvault
kubectl get externalsecret -n docvault
kubectl describe externalsecret docvault-app-secrets -n docvault
kubectl describe externalsecret minio-secret -n docvault
kubectl describe externalsecret keycloak-secret -n docvault
kubectl get secret docvault-app-secrets postgres-secret mongodb-secret minio-secret keycloak-secret -n docvault
```

Expected:

```text
SecretStore aws-secrets-manager Ready=True
ExternalSecret Ready=True
Kubernetes Secret objects exist
```

Do not print the actual secret values during screenshots or evidence collection.

## 6. Notes

- This does not change application code.
- This does not change existing `envFrom.secretRef` or `secretKeyRef` names.
- The old plaintext values are removed from Git for `docvault-app-secrets`, `postgres-secret`, `mongodb-secret`, `minio-secret`, and `keycloak-secret`.
- Keycloak `start-dev --import-realm` performs a Quarkus build/update step at startup, so the demo Keycloak container cannot use `readOnlyRootFilesystem: true`. Other hardening settings still apply.
- Harbor bootstrap secrets and Cloudflare tunnel token are still operational secrets created manually for now. They can be moved to AWS Secrets Manager in a second pass.
