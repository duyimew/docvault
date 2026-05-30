# Harbor DNS + Trusted TLS With Cloudflare

Updated: 2026-05-25

This document originally explained the DNS/load balancer method. The current DocVault plan has changed:

```text
Primary app/admin UI exposure: Cloudflare Tunnel published application routes
Harbor registry push/pull: keep direct DNS/LB or NGINX Ingress as fallback if tunnel push/pull is unstable
```

Use this newer guide first:

```text
docs/cloudflare_tunnel_published_app_routes.md
```

The older DNS/TLS method below is still useful if Docker registry push/pull through Tunnel is unreliable.

This document explains the DNS/TLS method:

```text
Deploy Harbor on EKS
Expose Harbor with an AWS Load Balancer
Use Cloudflare for DNS
Use Cloudflare DNS-01 to issue a trusted Let's Encrypt certificate
Point harbor.<your-domain> to the AWS Load Balancer
Jenkins and EKS pull/push images through HTTPS
```

## 1. The Idea in Plain Language

Harbor needs a public HTTPS name because Docker clients and Kubernetes nodes must trust the registry certificate.

For example:

```text
harbor.yourdomain.com
```

Cloudflare can help in two different ways:

1. **DNS hosting**: Cloudflare controls the DNS record for `harbor.yourdomain.com`.
2. **DNS challenge for TLS**: cert-manager can use a Cloudflare API token to prove domain ownership to Let's Encrypt and generate a public trusted certificate.

For a container registry, the safest MVP path is:

```text
Cloudflare DNS-only record -> AWS Load Balancer -> Harbor TLS inside Kubernetes
```

This means Docker connects directly to the AWS Load Balancer, sees a normal public certificate, and can push/pull images without special node trust config.

## 2. DNS-only vs Proxied

Cloudflare has two modes for many DNS records:

| Mode | Icon | What happens | Recommendation for Harbor |
|---|---|---|---|
| DNS-only | Gray cloud | DNS returns the AWS Load Balancer hostname/IP path. Traffic does not pass through Cloudflare proxy. | Recommended for Harbor registry push/pull. |
| Proxied | Orange cloud | Traffic goes through Cloudflare first, then to the origin. | Useful for websites, but be careful for registry blobs and large uploads. |

Why avoid proxied mode for the registry first?

- Docker image layers can be large.
- Cloudflare proxied requests have upload/request limits by plan.
- Proxy timeouts or body-size limits can turn image pushes into confusing `413`, `524`, or retry errors.
- If the record is DNS-only, Docker talks directly to the AWS Load Balancer and only needs a normal trusted public certificate.

You can later test proxied mode for the Harbor UI, but do the first working registry path with DNS-only.

## 3. Recommended Architecture for DocVault

```text
Jenkins
  |
  | docker login / push
  v
harbor.docvault-demo.example.com
  |
  | Cloudflare DNS-only CNAME
  v
AWS Load Balancer created by EKS Service
  |
  | HTTPS
  v
Harbor pods in namespace harbor
  |
  v
EBS-backed PVCs using docvault-gp3
```

For DocVault, this matches:

- Terraform EKS in `infra/terraform/aws-eks`.
- Harbor values in `infra/k8s/harbor/values-eks.yaml`.
- Jenkins Harbor parameters in `Jenkinsfile`.
- GitOps image values in `infra/k8s/values/*.yaml`.

## 4. Prerequisites

You need:

- A domain you control, for example `example.com`.
- The domain added to Cloudflare.
- Cloudflare nameservers configured at your domain registrar.
- AWS account ready for EKS.
- `kubectl`, `helm`, and `terraform` installed on your machine/VM.
- A real email for Let's Encrypt account registration.

Example names used below:

```text
Domain: example.com
Harbor hostname: harbor.example.com
AWS region: ap-southeast-1
EKS cluster: docvault-eks
Harbor namespace: harbor
TLS secret: harbor-tls
```

Replace those with your real values.

## 4.1. If You Do Not Have a Domain Yet

Stop before the Cloudflare DNS-01 step. Cloudflare cannot issue or validate TLS for a random hostname; it must manage a real DNS zone that you own.

You have three realistic choices:

| Choice | Good for | Recommendation |
|---|---|---|
| Buy a cheap domain and add it to Cloudflare | Real Harbor + trusted TLS + clean demo | Best choice. |
| Use Google Cloud DNS instead of Cloudflare | Good if you want a `.vn` domain and your team already knows GCP DNS | Good choice; use cert-manager CloudDNS solver. |
| Use AWS Route 53 instead of Cloudflare | AWS-only setup | Also good, but the runbook needs Route 53 DNS-01 instead of Cloudflare DNS-01. |
| Use self-signed Harbor TLS | Temporary lab only | Avoid for EKS unless you are ready to configure Docker/Jenkins/EKS node trust manually. |

For this project, the cleanest path is:

```text
Buy domain -> add domain to Cloudflare -> use harbor.<domain> -> continue this guide
```

Cheap domain examples:

```text
docvault-demo.com
docvault-demo.net
docvault-lab.com
```

The exact name does not matter. What matters is that Cloudflare shows it as an active zone in the Cloudflare dashboard. If Cloudflare lists:

```text
example.com
```

then:

```yaml
dnsZones:
  - example.com
```

and Harbor can use:

```text
harbor.example.com
```

Do not use `harbor.example.com` as `dnsZones` unless it is separately delegated as its own Cloudflare zone.

## 4.2. If You Want a `.vn` Domain With Google Cloud DNS

This is likely what your teammates did.

There are three separate concepts:

| Concept | Meaning | Example |
|---|---|---|
| Registrar | Where you buy/manage ownership of the domain | Vietnamese `.vn` registrar |
| DNS provider | Where DNS records are hosted | Google Cloud DNS |
| TLS issuer | Who issues the HTTPS certificate | Let's Encrypt through cert-manager |

You can buy a domain from a `.vn` registrar, then delegate DNS to Google Cloud DNS.

Flow:

```text
Buy example.vn from a .vn registrar
Create public zone example.vn in Google Cloud DNS
Copy Google Cloud DNS nameservers
Paste those nameservers into the .vn registrar
cert-manager uses Google CloudDNS DNS-01
Harbor gets trusted TLS for harbor.example.vn
```

This works even though EKS is on AWS. DNS does not need to live in the same cloud as the Kubernetes cluster.

### 4.2.1. Create Google Cloud DNS Public Zone

In Google Cloud:

```text
Cloud DNS -> Create zone
Zone type: Public
DNS name: example.vn.
Zone name: docvault-vn
```

Or with `gcloud`:

```powershell
gcloud dns managed-zones create docvault-vn `
  --description="DocVault public DNS zone" `
  --dns-name="example.vn." `
  --visibility="public"
```

Then list the assigned Google nameservers:

```powershell
gcloud dns managed-zones describe docvault-vn
```

You will see nameservers like:

```text
ns-cloud-a1.googledomains.com.
ns-cloud-a2.googledomains.com.
ns-cloud-a3.googledomains.com.
ns-cloud-a4.googledomains.com.
```

At the `.vn` registrar, replace the domain nameservers with those Google nameservers.

### 4.2.2. Create a GCP Service Account for cert-manager

cert-manager needs permission to create temporary TXT records for DNS-01 validation.

In GCP, create a service account like:

```text
cert-manager-dns01
```

Grant it:

```text
DNS Administrator
```

For tighter permissions later, restrict it to the DNS project/zone. For MVP, project-level DNS admin is simpler.

Create and download a JSON key. Do not commit it.

Create the Kubernetes secret in EKS:

```powershell
kubectl create secret generic clouddns-dns01-solver-svc-acct `
  -n cert-manager `
  --from-file=key.json="C:\path\to\gcp-service-account-key.json"
```

### 4.2.3. ClusterIssuer for Google Cloud DNS

Use this instead of the Cloudflare `ClusterIssuer`.

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-google-dns
spec:
  acme:
    email: your-email@example.vn
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-google-dns-account-key
    solvers:
      - dns01:
          cloudDNS:
            project: your-gcp-project-id
            serviceAccountSecretRef:
              name: clouddns-dns01-solver-svc-acct
              key: key.json
        selector:
          dnsZones:
            - example.vn
```

Then the Harbor certificate becomes:

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: harbor-tls
  namespace: harbor
spec:
  secretName: harbor-tls
  issuerRef:
    name: letsencrypt-google-dns
    kind: ClusterIssuer
  dnsNames:
    - harbor.example.vn
```

And Harbor values should use:

```yaml
externalURL: https://harbor.example.vn
```

For NGINX Ingress, change the annotation from:

```yaml
cert-manager.io/cluster-issuer: letsencrypt-cloudflare
```

to:

```yaml
cert-manager.io/cluster-issuer: letsencrypt-google-dns
```

### 4.2.4. Create the Harbor DNS Record in Google Cloud DNS

After either Harbor direct LoadBalancer or ingress-nginx creates an AWS Load Balancer, create:

```text
Name: harbor.example.vn.
Type: CNAME
Target: <aws-load-balancer-hostname>
TTL: 300
```

With `gcloud`:

```powershell
gcloud dns record-sets create harbor.example.vn. `
  --zone=docvault-vn `
  --type=CNAME `
  --ttl=300 `
  --rrdatas="<aws-load-balancer-hostname>."
```

Then test:

```powershell
nslookup harbor.example.vn
curl -I https://harbor.example.vn
docker login harbor.example.vn
```

If Docker login works without `x509` errors, the `.vn` + Google Cloud DNS path is working.

## 5. Step 1 - Deploy EKS

From this repo:

```powershell
cd infra/terraform/aws-eks
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out tfplan
terraform apply tfplan
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes
kubectl get sc
```

Confirm the storage class exists:

```powershell
kubectl get storageclass docvault-gp3
```

Current successful DocVault EKS apply output captured on 2026-05-25:

```text
cluster_name = "docvault-eks"
region = "ap-southeast-1"
vpc_id = "vpc-0ebf350bac281ea64"
cluster_security_group_id = "sg-098520114b88c2461"
node_group_name = "docvault-ng-20260525133007975800000013"
public_subnet_ids = [
  "subnet-02b5b923a134e7258",
  "subnet-047fbf5fa4dec4caa",
]
private_subnet_ids = [
  "subnet-047ca1d8087916641",
  "subnet-091ac684811996ec2",
]
configure_kubectl = "aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks"
```

After apply, configure local `kubectl`:

```powershell
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl config current-context
kubectl get nodes -o wide
kubectl get pods -A
```

## 6. Step 2 - Install cert-manager

cert-manager will request and renew the certificate for Harbor.

```powershell
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager `
  --namespace cert-manager `
  --create-namespace `
  --set crds.enabled=true
```

Check:

```powershell
kubectl get pods -n cert-manager
```

## 7. Step 3 - Create a Cloudflare API Token

In Cloudflare:

```text
My Profile -> API Tokens -> Create Token
```

Use permissions:

```text
Zone - DNS - Edit
Zone - Zone - Read
```

Restrict the token to your zone if possible, for example:

```text
Include -> Specific zone -> example.com
```

Do not commit this token.

Create the Kubernetes secret:

```powershell
kubectl create secret generic cloudflare-api-token-secret `
  -n cert-manager `
  --from-literal=api-token="<cloudflare-api-token>"
```

## 8. Step 4 - Create a Let's Encrypt ClusterIssuer

Create `infra/k8s/harbor/clusterissuer-letsencrypt-cloudflare.yaml` locally or apply this directly after replacing the email and zone:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-cloudflare
spec:
  acme:
    email: your-email@example.com
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-cloudflare-account-key
    solvers:
      - dns01:
          cloudflare:
            apiTokenSecretRef:
              name: cloudflare-api-token-secret
              key: api-token
        selector:
          dnsZones:
            - example.com
```

Apply it:

```powershell
kubectl apply -f infra/k8s/harbor/clusterissuer-letsencrypt-cloudflare.yaml
kubectl get clusterissuer
```

Expected:

```text
letsencrypt-cloudflare   True
```

## 9. Step 5 - Request the Harbor Certificate

Create `infra/k8s/harbor/certificate-harbor.yaml` locally or apply this after replacing the hostname:

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: harbor-tls
  namespace: harbor
spec:
  secretName: harbor-tls
  issuerRef:
    name: letsencrypt-cloudflare
    kind: ClusterIssuer
  dnsNames:
    - harbor.example.com
```

Create namespace and request the cert:

```powershell
kubectl create namespace harbor
kubectl apply -f infra/k8s/harbor/certificate-harbor.yaml
kubectl describe certificate harbor-tls -n harbor
kubectl get secret harbor-tls -n harbor
```

cert-manager will create a temporary TXT record in Cloudflare for DNS-01 validation, then store the issued certificate in the `harbor-tls` secret.

## 10. Step 6 - Configure Harbor Values

Edit:

```text
infra/k8s/harbor/values-eks.yaml
```

Set:

```yaml
externalURL: https://harbor.example.com

expose:
  type: loadBalancer
  tls:
    enabled: true
    certSource: secret
    secret:
      secretName: harbor-tls
```

Keep persistence on `docvault-gp3`:

```yaml
persistence:
  enabled: true
  persistentVolumeClaim:
    registry:
      storageClass: "docvault-gp3"
```

Create Harbor bootstrap secret:

```powershell
kubectl create secret generic harbor-bootstrap-secrets `
  -n harbor `
  --from-literal=HARBOR_ADMIN_PASSWORD="<strong-admin-password>" `
  --from-literal=secretKey="<16-char-random-key>"
```

## 11. Step 7 - Install Harbor

```powershell
helm repo add harbor https://helm.goharbor.io
helm repo update
helm upgrade --install harbor harbor/harbor `
  -n harbor `
  -f infra/k8s/harbor/values-eks.yaml
```

Wait:

```powershell
kubectl get pods -n harbor
kubectl get pvc -n harbor
kubectl get svc -n harbor
```

Find the Harbor load balancer:

```powershell
kubectl get svc -n harbor
```

You should see an external hostname like:

```text
xxxx.elb.ap-southeast-1.amazonaws.com
```

## 12. Step 8 - Create Cloudflare DNS Record

In Cloudflare DNS:

```text
Type: CNAME
Name: harbor
Target: xxxx.elb.ap-southeast-1.amazonaws.com
Proxy status: DNS only
TTL: Auto
```

Important:

```text
Use gray cloud / DNS only for the first working Harbor registry setup.
```

Wait for DNS:

```powershell
nslookup harbor.example.com
curl -I https://harbor.example.com
```

Then test Docker:

```powershell
docker login harbor.example.com
```

If `docker login` succeeds without `x509` errors, your DNS + trusted TLS path works.

## 13. Step 9 - Create Harbor Projects and Robot Accounts

In Harbor UI:

```text
https://harbor.example.com
```

Create projects:

```text
docvault-dev
docvault-prod
```

Create robot accounts:

| Project | Robot account | Purpose |
|---|---|---|
| `docvault-dev` | `jenkins-push` | Jenkins push/pull for dev images. |
| `docvault-prod` | `prod-promoter` | Controlled promotion to prod. |

Enable:

- Trivy vulnerability scanning.
- Scan on push, if available.
- Retention rules for dev images.
- Tag immutability for prod/release tags.

Store the dev robot account in Jenkins:

```text
Credential ID: harbor-docvault-dev-robot
Type: Username with password
Username: robot$docvault-dev+jenkins-push
Password: <robot-secret-from-harbor>
```

Use the exact robot username Harbor shows.

## 14. Step 10 - Run Jenkins With Harbor

Use these Jenkins parameters:

```text
REGISTRY_HOST=harbor.example.com
REGISTRY_NAMESPACE=docvault-dev
REGISTRY_CREDENTIAL_ID=harbor-docvault-dev-robot
PUSH_LATEST=false
FORCE_BUILD_ALL=true
GITOPS_BRANCH=gitops-testing
RUN_ARGO_HEALTH_CHECK=false
RUN_ZAP=false
```

Expected image references after Jenkins updates GitOps:

```yaml
image:
  repository: "harbor.example.com/docvault-dev/gateway"
  tag: "v<build-number>"
  digest: "sha256:..."
```

## 15. Step 11 - Private Harbor Pull Secret for DocVault

If Harbor projects are private, create a pull secret in `docvault`:

```powershell
kubectl create namespace docvault --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret docker-registry harbor-docvault-dev-pull `
  -n docvault `
  --docker-server=harbor.example.com `
  --docker-username="robot$docvault-dev+jenkins-push" `
  --docker-password="<robot-secret-from-harbor>"
```

Add to each `infra/k8s/values/*.yaml` file:

```yaml
imagePullSecrets:
  - name: harbor-docvault-dev-pull
```

Then Argo CD can deploy private Harbor images.

## 16. What If You Want Orange Cloud Proxy?

Use it only after DNS-only works.

With orange cloud proxy:

```text
Docker -> Cloudflare edge -> AWS Load Balancer -> Harbor
```

Then set Cloudflare SSL/TLS mode to:

```text
Full (strict)
```

The origin must still present a valid certificate. Cloudflare Full (strict) requires the origin certificate to be unexpired, match the hostname, and be issued by a public CA or Cloudflare Origin CA.

For Harbor registry traffic, be careful:

- Large image pushes may hit Cloudflare upload/request limits.
- Long uploads may hit proxy timeouts.
- Troubleshooting registry push failures becomes harder.

For this project, keep `harbor.example.com` DNS-only until the final demo is stable.

## 17. What If You Use NGINX Ingress?

NGINX Ingress is a good option. The architecture changes from:

```text
Cloudflare DNS -> AWS Load Balancer -> Harbor Service
```

to:

```text
Cloudflare DNS -> AWS Load Balancer -> ingress-nginx controller -> Harbor Ingress -> Harbor services
```

You still need an AWS Load Balancer. The difference is that the Load Balancer belongs to the `ingress-nginx-controller` Service, and Harbor is routed by hostname through Kubernetes Ingress.

Use NGINX Ingress if you want:

- One public entry point for many apps, for example Harbor, Argo CD, Grafana, and later DocVault web.
- Standard Kubernetes `Ingress` resources.
- cert-manager to issue certs through Ingress annotations.
- Easier path-based or host-based routing later.

For a single Harbor-only MVP, direct `LoadBalancer` is simpler. For a more realistic platform story, NGINX Ingress is better.

### 17.1. Install ingress-nginx

```powershell
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx `
  --namespace ingress-nginx `
  --create-namespace `
  --set controller.service.type=LoadBalancer `
  --set controller.service.annotations."service\.beta\.kubernetes\.io/aws-load-balancer-type"="nlb"
```

Check:

```powershell
kubectl get pods -n ingress-nginx
kubectl get svc -n ingress-nginx
kubectl get ingressclass
```

Find the external Load Balancer hostname:

```powershell
kubectl get svc ingress-nginx-controller -n ingress-nginx
```

### 17.2. Cloudflare DNS

Create:

```text
Type: CNAME
Name: harbor
Target: <ingress-nginx-controller-load-balancer-hostname>
Proxy status: DNS only
```

Use DNS-only first for the same reason as before: Docker registry pushes can be large and are easier to troubleshoot without Cloudflare proxying the registry data path.

### 17.3. Harbor Values for NGINX Ingress

Use:

```text
infra/k8s/harbor/values-eks-nginx-ingress.yaml
```

Important fields:

```yaml
expose:
  type: ingress
  tls:
    enabled: true
    certSource: secret
    secret:
      secretName: harbor-tls
  ingress:
    hosts:
      core: harbor.example.com
    className: nginx
    annotations:
      cert-manager.io/cluster-issuer: letsencrypt-cloudflare
      nginx.ingress.kubernetes.io/proxy-body-size: "0"
      nginx.ingress.kubernetes.io/proxy-request-buffering: "off"

externalURL: https://harbor.example.com
```

Why these annotations matter:

- `proxy-body-size: "0"` avoids NGINX rejecting large image layers.
- `proxy-request-buffering: "off"` is friendlier for large Docker push streams.
- The cert-manager annotation lets cert-manager create/manage the TLS secret from the Ingress path if you choose ingress-shim.

### 17.4. Certificate Options With NGINX

You have two clean choices.

Choice A: explicit Certificate resource:

```text
ClusterIssuer -> Certificate -> Secret harbor-tls -> Harbor Ingress
```

This is clearer for learning and matches the earlier steps in this document.

Choice B: cert-manager ingress-shim:

```text
Ingress annotation cert-manager.io/cluster-issuer -> cert-manager creates Certificate automatically
```

This is convenient, but slightly more magical. For your project, use Choice A first.

### 17.5. Install Harbor Through NGINX Ingress

```powershell
helm upgrade --install harbor harbor/harbor `
  -n harbor `
  -f infra/k8s/harbor/values-eks-nginx-ingress.yaml
```

Check:

```powershell
kubectl get ingress -n harbor
kubectl describe ingress -n harbor
kubectl get certificate -n harbor
kubectl get pods -n harbor
curl -I https://harbor.example.com
docker login harbor.example.com
```

If Docker login works, Jenkins can use:

```text
REGISTRY_HOST=harbor.example.com
REGISTRY_NAMESPACE=docvault-dev
REGISTRY_CREDENTIAL_ID=harbor-docvault-dev-robot
PUSH_LATEST=false
```

### 17.6. NGINX Ingress vs Direct LoadBalancer

| Option | Pros | Cons |
|---|---|---|
| Harbor direct LoadBalancer | Fewer components, easier first install. | One LoadBalancer per exposed app unless you add more routing later. |
| NGINX Ingress | One LoadBalancer for many hostnames, nicer platform story, works well with cert-manager. | More moving parts: ingress-nginx, IngressClass, annotations, NGINX limits/timeouts. |

Recommended for DocVault:

```text
If you only need Harbor quickly: direct LoadBalancer.
If you want Harbor + Argo CD + Grafana + app hostnames later: NGINX Ingress.
```

## 18. What If You Use Cloudflare Tunnel?

Cloudflare Tunnel can work well for **web UIs**, but it is not the recommended primary path for a Harbor Docker registry.

It can expose:

```text
https://harbor.example.com -> cloudflared -> Harbor/NGINX inside the cluster
```

But Harbor is not only a normal website. Docker push/pull sends registry API calls plus large image layer uploads/downloads. Those large request bodies and long-lived upload streams are exactly where Cloudflare proxy/tunnel setups can become painful.

Recommended use:

| Use case | Cloudflare Tunnel? | Reason |
|---|---|---|
| Harbor UI demo | Maybe | Opening the UI and browsing projects is normal HTTPS web traffic. |
| Docker `login` smoke test | Maybe | Small HTTP requests may work. |
| Docker `push` / `pull` real images | Avoid for primary path | Image layers can be large and long-lived; proxy limits/timeouts can break pushes. |
| Jenkins production-ish registry path | Avoid | CI should use a boring, direct, reliable registry endpoint. |

For DocVault, prefer:

```text
Cloudflare DNS-only -> AWS Load Balancer / NGINX Ingress -> Harbor
```

Use Tunnel only as a temporary UI access method if you cannot create a public Load Balancer yet.

### 18.1. Cloudflare Zero Trust Published Application

This plan is OK for apps like:

```text
Argo CD UI
Grafana UI
Harbor UI for humans
internal dashboards
```

But be careful with Harbor registry traffic:

```text
docker login
docker push
docker pull
Kubernetes imagePull
Jenkins docker push
```

Those clients do not behave like a browser. If you put a Cloudflare Access login policy in front of Harbor, Docker and Kubernetes clients usually cannot complete the browser-based Zero Trust flow or attach the headers that Cloudflare Access expects.

So this is a good plan:

```text
Cloudflare Tunnel + Zero Trust -> Argo CD UI
Cloudflare Tunnel + Zero Trust -> Grafana UI
Cloudflare Tunnel + Zero Trust -> Harbor UI for manual browsing
```

This is not a good primary CI/CD registry plan:

```text
Jenkins/Kubernetes -> Cloudflare Tunnel + Zero Trust -> Harbor registry
```

For the registry path, prefer:

```text
Jenkins/Kubernetes -> DNS-only hostname -> AWS Load Balancer / NGINX Ingress -> Harbor
```

If you still use a Cloudflare Tunnel hostname for Harbor, do not add a browser Access policy to the Docker registry route. Then test real push/pull with a non-trivial image before trusting it in Jenkins.

### 18.2. Why Tunnel Is Risky for Harbor

Cloudflare Tunnel supports HTTP/HTTPS origins and TCP-style access patterns, but TCP access generally requires client-side Cloudflare tooling such as `cloudflared access tcp`. Docker clients and Kubernetes nodes expect a normal registry endpoint and should not need special client-side tunnel commands.

Also, Docker image pushes can cross common proxy upload-size and timeout limits. A Harbor UI may look fine while real image pushes fail later with errors like:

```text
413 Payload Too Large
524 timeout
unexpected EOF
blob upload invalid
x509 or redirect/token service mismatch
```

This creates a bad demo failure mode because the registry appears reachable but CI cannot push reliably.

### 18.3. If You Still Want to Test Tunnel

Only test it as a temporary experiment:

```text
Tunnel hostname: harbor.example.com
Tunnel service: http://harbor-core-or-nginx-service:80
Harbor externalURL: https://harbor.example.com
```

Then verify all of these, not just the UI:

```powershell
docker login harbor.example.com
docker pull alpine:3.20
docker tag alpine:3.20 harbor.example.com/docvault-dev/alpine-test:tunnel
docker push harbor.example.com/docvault-dev/alpine-test:tunnel
docker pull harbor.example.com/docvault-dev/alpine-test:tunnel
```

If the test image is tiny but DocVault images fail, the tunnel/proxy path is probably the problem. Switch back to DNS-only plus AWS Load Balancer or NGINX Ingress.

## 19. Common Errors

### `x509: certificate signed by unknown authority`

Cause:

- You used a self-signed cert or Cloudflare Origin CA while the DNS record is DNS-only.

Fix:

- Use Let's Encrypt via cert-manager DNS-01, or use orange cloud Full (strict) where Docker sees Cloudflare's edge cert.

### `certificate is valid for X, not harbor.example.com`

Cause:

- Certificate `dnsNames` does not match `externalURL`.

Fix:

- Make `Certificate.spec.dnsNames`, Harbor `externalURL`, and Cloudflare DNS record all use the same hostname.

### Docker push gets `413 Payload Too Large`

Cause:

- Cloudflare proxied upload limit.

Fix:

- Switch Harbor DNS record back to DNS-only.

### Docker push/pull unauthorized

Cause:

- Wrong Harbor project, robot account, or Jenkins credential.

Fix:

- Confirm `REGISTRY_NAMESPACE=docvault-dev`.
- Confirm robot account has push/pull permission.
- Recreate Jenkins credential with the exact robot username and secret.

### Kubernetes `ImagePullBackOff`

Cause:

- Harbor project is private and `imagePullSecrets` is missing or wrong.

Fix:

- Create `harbor-docvault-dev-pull` in the same namespace as DocVault.
- Add `imagePullSecrets` to each service values file.

## 20. Evidence for Report

Capture:

- Cloudflare DNS record for `harbor.example.com` set to DNS-only.
- `kubectl get certificate -n harbor`.
- `kubectl get secret harbor-tls -n harbor`.
- `kubectl get svc -n harbor` showing LoadBalancer.
- Browser screenshot of Harbor UI over HTTPS.
- `docker login harbor.example.com` success.
- Jenkins log showing push to `harbor.example.com/docvault-dev/...`.
- Harbor scan report for one pushed image.
- Argo CD app pulling images from Harbor successfully.

## 21. Source Notes

- Cloudflare DNS docs explain proxied vs DNS-only records and note that proxied requests can hit Cloudflare limits/timeouts.
- Cloudflare Tunnel docs list HTTP/HTTPS and TCP routing options; TCP access is not the same as a normal public Docker registry endpoint because clients may need Cloudflare client-side tooling.
- Cloudflare Full (strict) requires the origin to serve HTTPS with an unexpired certificate matching the hostname and issued by a trusted CA or Cloudflare Origin CA.
- cert-manager recommends Cloudflare API Tokens over global API keys for DNS-01 because tokens can be scoped to DNS edit and zone read permissions.
- Harbor Helm install docs require correct `externalURL`, expose/TLS, and persistence choices.
- Harbor Helm supports `expose.type: ingress`, `expose.ingress.hosts.core`, and `expose.ingress.className`.
- AWS EKS documentation recommends AWS Load Balancer Controller for EKS load balancing, while NGINX Ingress on EKS is also a common pattern where a Service of type `LoadBalancer` fronts the controller.
- cert-manager ingress documentation explains that `cert-manager.io/issuer` or `cert-manager.io/cluster-issuer` annotations can make ingress-shim create `Certificate` resources.
