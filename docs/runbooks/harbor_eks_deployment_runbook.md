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

Do not deploy Harbor as plain HTTP on EKS. Docker/Jenkins and EKS node containerd both need a trusted registry endpoint.
Also, do not proxy Harbor registry traffic through a Cloudflare Tunnel or Cloudflare proxied (orange cloud) DNS, as this will fail with a `413 Payload Too Large` error for images larger than 100MB.

The recommended production architecture for DocVault is:

```text
harbor.docvault.id.vn
  -> Cloudflare DNS-only (gray cloud)
  -> AWS LoadBalancer (NLB)
  -> ingress-nginx
  -> Harbor Ingress
  -> Harbor services
```

The primary manifest for this is:

```text
infra/k8s/harbor/values-eks-nginx-ingress.yaml
```

The domain is pre-configured to `harbor.docvault.id.vn`.

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

## 3. Install cert-manager and ClusterIssuer

cert-manager requests and auto-renews Let's Encrypt certificates.

```powershell
# Add Helm repo and install cert-manager
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager `
  --namespace cert-manager `
  --create-namespace `
  --set crds.enabled=true

# Create the Cloudflare API token secret (do not commit the token itself)
kubectl create secret generic cloudflare-api-token-secret `
  -n cert-manager `
  --from-literal=api-token="<YOUR_CLOUDFLARE_API_TOKEN>"

# Apply ClusterIssuer
kubectl apply -f infra/k8s/cert-manager/clusterissuer-letsencrypt-cloudflare.yaml
kubectl get clusterissuer
```

## 4. Install NGINX Ingress Controller (ingress-nginx)

The controller handles routing from the external AWS Load Balancer to the Kubernetes Services.

```powershell
# Add Helm repo and install ingress-nginx
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx `
  --namespace ingress-nginx `
  --create-namespace `
  -f infra/k8s/ingress-nginx/values-eks.yaml

# Wait for the external AWS Load Balancer to be provisioned
kubectl get svc ingress-nginx-controller -n ingress-nginx -w
```

Note down the **EXTERNAL-IP** of the load balancer. It will look like:
`xxxxxx.elb.ap-southeast-1.amazonaws.com`

## 5. Configure Cloudflare DNS-only Record

In your Cloudflare dashboard under your active zone (`docvault.id.vn`):

1. Go to **DNS** -> **Records**.
2. Click **Add record**.
3. Set **Type** to `CNAME`.
4. Set **Name** to `harbor`.
5. Set **Target** to the AWS LoadBalancer DNS name retrieved in Step 4.
6. Set **Proxy status** to **DNS only** (gray cloud).
7. Save the record.

> [!IMPORTANT]
> The **DNS-only** (gray cloud) setting is critical. If proxied (orange cloud), the 100MB upload limit applies, causing Docker pushes to fail with `413 Payload Too Large`.

## 6. Create Harbor Namespace and Bootstrap Secrets

Create the namespace and bootstrap credentials before installing Harbor:

```powershell
# Create namespace
kubectl create namespace harbor

# Create bootstrap secret
kubectl create secret generic harbor-bootstrap-secrets `
  -n harbor `
  --from-literal=HARBOR_ADMIN_PASSWORD="<strong-admin-password>" `
  --from-literal=secretKey="<16-char-random-key>"
```

*Note: The `harbor-tls` secret will be created automatically by cert-manager through ingress-shim once the Harbor chart is installed.*

## 7. Install Harbor with Helm and Ingress-NGINX

Deploy Harbor and expose it via NGINX Ingress:

```powershell
helm repo add harbor https://helm.goharbor.io
helm repo update
helm upgrade --install harbor harbor/harbor `
  -n harbor `
  -f infra/k8s/harbor/values-eks-nginx-ingress.yaml
```

Wait for all Harbor pods to be healthy and running:

```powershell
kubectl get pods -n harbor
kubectl get ingress -n harbor
kubectl get certificate -n harbor
```

## 8. Verify TLS and Docker Push

Once deployment succeeds and DNS propagates, test the endpoint:

```powershell
# 1. Verify HTTPS connection
curl -I https://harbor.docvault.id.vn

# 2. Log in using your configured HARBOR_ADMIN_PASSWORD
docker login harbor.docvault.id.vn

# 3. Pull a test image, tag it, and push it to verify upload capacity
docker pull alpine:3.20
docker tag alpine:3.20 harbor.docvault.id.vn/docvault-dev/alpine-test:dns-only
docker push harbor.docvault.id.vn/docvault-dev/alpine-test:dns-only
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
REGISTRY_HOST=harbor.docvault.id.vn
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
  repository: "harbor.docvault.id.vn/docvault-dev/gateway"
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
  --docker-server=harbor.docvault.id.vn `
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
- Jenkins log showing `docker login harbor.docvault.id.vn`, push to `docvault-dev`, and GitOps digest update.
- Argo CD app health after pulling from Harbor.
- A failed attempt to overwrite an immutable prod tag.
