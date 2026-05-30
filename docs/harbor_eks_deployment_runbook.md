# Harbor on EKS Deployment Runbook

Updated: 2026-05-25

This runbook fits the current DocVault flow:

```text
Terraform EKS -> Harbor on EKS -> Jenkins pushes to Harbor -> GitOps values use Harbor image refs -> Argo CD deploys DocVault
```

Official context checked while writing this:

- Amazon EKS currently lists Kubernetes `1.35`, `1.34`, and `1.33` in standard support, with `1.35` standard support ending on 2027-03-27: <https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-versions.html>
- Harbor 2.14 is the latest Harbor documentation set. Harbor Helm deployment requires `externalURL`, endpoint exposure, and persistent storage decisions: <https://goharbor.io/docs/2.14.0/install-config/harbor-ha-helm/>
- Harbor project robot accounts are the right credential shape for Jenkins automation: <https://goharbor.io/docs/2.14.0/working-with-projects/project-configuration/create-robot-accounts/>
- Harbor supports Trivy vulnerability scanning and exporting vulnerability data through the API: <https://goharbor.io/docs/2.14.0/administration/vulnerability-scanning/>
- Harbor tag immutability protects tags from overwrite, re-tag, and delete once rules match them: <https://goharbor.io/docs/2.14.0/working-with-projects/working-with-images/create-tag-immutability-rules/>

## 1. Decision Before Apply

Do not deploy Harbor as plain HTTP on EKS if EKS nodes must pull images from it. Docker/Jenkins and EKS node containerd both need a trusted registry endpoint. The clean path is:

```text
harbor.<your-domain> over HTTPS with a trusted certificate
```

For MVP, use one of these:

| Option | When to use | Notes |
|---|---|---|
| Real DNS + trusted TLS | Best EKS demo path | Use `harbor.<domain>` and create `harbor-tls` in namespace `harbor`. |
| Temporary self-signed TLS | Short lab only | Jenkins and every EKS node must trust the CA. More moving parts. |
| HTTP NodePort | Local kind/minikube only | Avoid on EKS unless you intentionally configure insecure registries on all nodes. |

The committed values file is:

```text
infra/k8s/harbor/values-eks.yaml
```

Replace:

```yaml
externalURL: https://harbor.example.com
```

with the real Harbor URL before installing Harbor.

## 2. EKS First

From `infra/terraform/aws-eks`:

```powershell
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out tfplan
terraform apply tfplan
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes
kubectl get sc
```

Expected storage class for this repo:

```text
docvault-gp3
```

## 3. Create Harbor Namespace and Secrets

Create namespace:

```powershell
kubectl create namespace harbor
```

Create bootstrap secret. Do not commit these values:

```powershell
kubectl create secret generic harbor-bootstrap-secrets `
  -n harbor `
  --from-literal=HARBOR_ADMIN_PASSWORD="<strong-admin-password>" `
  --from-literal=secretKey="<16-char-secret-key>"
```

Create TLS secret:

```powershell
kubectl create secret tls harbor-tls `
  -n harbor `
  --cert .\harbor.crt `
  --key .\harbor.key
```

The certificate common name/SAN must match `externalURL`, for example `harbor.example.com`.

## 4. Install Harbor With Helm

```powershell
helm repo add harbor https://helm.goharbor.io
helm repo update
helm upgrade --install harbor harbor/harbor `
  -n harbor `
  -f infra/k8s/harbor/values-eks.yaml
```

Check status:

```powershell
kubectl get pods -n harbor
kubectl get pvc -n harbor
kubectl get svc -n harbor
```

If using `type: loadBalancer`, wait for the external endpoint:

```powershell
kubectl get svc harbor -n harbor -w
```

Point DNS for `harbor.<domain>` to the load balancer endpoint, then verify:

```powershell
curl -I https://harbor.<domain>
docker login harbor.<domain>
```

## 5. Harbor Project Setup

Create two Harbor projects:

```text
docvault-dev
docvault-prod
```

Recommended policies:

| Project | Robot account | Permissions | Tag policy |
|---|---|---|---|
| `docvault-dev` | `jenkins-push` | push/pull repository | Keep build tags immutable if possible; do not depend on `latest`. |
| `docvault-prod` | `prod-promoter` | push/pull repository | Immutable tags required. |

Enable Trivy scanning, scan on push if available, retention rules, and tag immutability for release/prod tags.

Save the `docvault-dev` robot account as a Jenkins username/password credential:

```text
Credential ID: harbor-docvault-dev-robot
Username: robot$docvault-dev+jenkins-push, or the exact username Harbor shows
Password: robot account secret
```

Harbor does not show robot secrets again after creation, so store it immediately in Jenkins.

## 6. Jenkins Parameters for Harbor

Run the DocVault pipeline with:

```text
REGISTRY_HOST=harbor.<domain>
REGISTRY_NAMESPACE=docvault-dev
REGISTRY_CREDENTIAL_ID=harbor-docvault-dev-robot
PUSH_LATEST=false
FORCE_BUILD_ALL=true
GITOPS_BRANCH=gitops-testing
RUN_ARGO_HEALTH_CHECK=false
RUN_ZAP=false
```

After the first successful push, the pipeline updates each values file like:

```yaml
image:
  repository: "harbor.<domain>/docvault-dev/gateway"
  tag: "v<jenkins-build-number>"
  digest: "sha256:..."
```

Then Argo CD can deploy from Harbor.

## 7. Kubernetes Pull Secret

Create an image pull secret in `docvault`:

```powershell
kubectl create namespace docvault --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret docker-registry harbor-docvault-dev-pull `
  -n docvault `
  --docker-server=harbor.<domain> `
  --docker-username="<harbor-robot-username>" `
  --docker-password="<harbor-robot-secret>"
```

Add the pull secret to each service values file when the Harbor projects are private:

```yaml
imagePullSecrets:
  - name: harbor-docvault-dev-pull
```

## 8. Promotion Design

Use digest-based promotion:

1. Jenkins builds and pushes to `docvault-dev/<service>:vN`.
2. Jenkins records the digest in GitOps values.
3. After dev validation, promote the same digest to `docvault-prod/<service>:vN` using Harbor copy/retag or controlled `skopeo copy`.
4. Update prod values to the `docvault-prod` repository with the same digest.
5. Prod Argo CD sync is manual or approval-gated.

Do not rebuild for prod. Promotion means the artifact already scanned and validated in dev is the artifact prod runs.

## 9. Evidence Checklist

- `terraform plan` and `terraform apply` output.
- `kubectl get nodes`, `kubectl get sc`, `kubectl get pvc -n harbor`.
- Harbor UI projects `docvault-dev` and `docvault-prod`.
- Harbor robot account permissions.
- Harbor scan report for one DocVault image.
- Jenkins log showing `docker login harbor.<domain>`, push to `docvault-dev`, and GitOps digest update.
- Argo CD app health after pulling from Harbor.
- A failed attempt to overwrite an immutable prod tag.
