# Cloudflare Tunnel Published Application Routes for DocVault

Updated: 2026-05-25

This is the new exposure plan for DocVault demo/admin web applications:

```text
User browser
  -> https://docvault.<cloudflare-domain>
  -> Cloudflare Zero Trust / Access
  -> Cloudflare Tunnel
  -> cloudflared pods in EKS
  -> Kubernetes ClusterIP service
```

This avoids a public AWS Load Balancer for web/admin UIs. `cloudflared` creates outbound connections from EKS to Cloudflare, and Cloudflare publishes selected hostnames.

Use this plan for:

- DocVault web UI.
- Argo CD UI.
- Grafana UI.
- Harbor UI.

Do not use this as the primary path for:

- Jenkins `docker push`.
- Kubernetes image pulls from Harbor.
- Large Harbor registry blobs.

Cloudflare published application routes are browser/application-friendly. Harbor registry traffic is a Docker registry data path and still deserves separate push/pull testing before trusting it.

## 1. What Cloudflare Means by Published Application Route

Cloudflare's route docs say a route maps an IP or hostname to a Cloudflare One connector installed in your private network. A published application route exposes an application to the Internet using a domain connected to Cloudflare, so users can access it without VPN or special client software.

For this project, the connector is:

```text
cloudflared running in EKS
```

The published routes are hostnames like:

```text
docvault.example.com
argocd.example.com
grafana.example.com
harbor.example.com
```

Each route points to an internal Kubernetes service URL.

## 2. Prerequisites

You still need a domain added to Cloudflare.

Cloudflare's published application route flow requires:

- A Cloudflare Tunnel created with `cloudflared`.
- A website/domain added to Cloudflare.

Without a domain in Cloudflare, you can test with temporary tunnel URLs, but you cannot create your own stable hostname like `docvault.example.com`.

If you do not have a domain yet, get one first or use another DNS provider plan. For published application routes, Cloudflare must be able to offer the domain from the dashboard drop-down.

## 3. Target Routes

Recommended initial route set:

| Hostname | Cloudflare route service URL | Purpose |
|---|---|---|
| `docvault.example.com` | `http://docvault-web.docvault.svc.cluster.local:3006` | Main DocVault app. |
| `argocd.example.com` | `http://argocd-server.argocd.svc.cluster.local:80` | Argo CD UI. |
| `grafana.example.com` | `http://monitoring-stack-grafana.monitoring.svc.cluster.local:80` | Grafana UI. |
| `harbor.example.com` | Harbor internal service URL after Harbor install | Harbor UI only at first. |

For Harbor, do not use the published route as the Jenkins registry endpoint until you prove real Docker push/pull works with non-trivial images.

## 4. Create the Tunnel in Cloudflare Zero Trust

In Cloudflare:

```text
Zero Trust -> Networks -> Connectors -> Cloudflare Tunnels -> Create a tunnel
```

Choose:

```text
Connector type: cloudflared
Environment: Docker
```

Cloudflare will show a command containing a token:

```text
cloudflared tunnel run --token <very-long-token>
```

Copy only the token value.

## 5. Deploy cloudflared on EKS

Create the token secret:

```powershell
kubectl create namespace cloudflare-tunnel --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic cloudflared-token `
  -n cloudflare-tunnel `
  --from-literal=token="<cloudflare-tunnel-token>"
```

Apply the manifest:

```powershell
kubectl apply -f infra/k8s/cloudflare-tunnel/cloudflared.yaml
kubectl get pods -n cloudflare-tunnel
kubectl logs -n cloudflare-tunnel deploy/cloudflared --tail=100
```

Expected:

```text
Registered tunnel connection
```

The manifest runs two replicas for availability and pins `cloudflare/cloudflared:2026.5.0`.

## 6. Add a Published Application Route

In Cloudflare dashboard:

```text
Zero Trust -> Networks -> Connectors -> Cloudflare Tunnels
```

Then:

1. Select your tunnel.
2. Select `Edit`.
3. Open `Published application routes`.
4. Select `Add a published application route`.
5. Enter a subdomain and choose your Cloudflare domain.
6. Choose a service type, usually `HTTP`.
7. Enter the internal Kubernetes service URL.
8. Save.

Example for DocVault web:

```text
Subdomain: docvault
Domain: example.com
Path: empty
Service type: HTTP
Service URL: http://docvault-web.docvault.svc.cluster.local:3006
```

Result:

```text
https://docvault.example.com
```

Note: Cloudflare says multi-level subdomains, such as `app.dev.example.com`, may require an Advanced Certificate. Keep first demo hostnames one level deep:

```text
docvault.example.com
argocd.example.com
grafana.example.com
harbor.example.com
```

## 7. Add Cloudflare Access Policy

After publishing the route, protect it:

```text
Zero Trust -> Access -> Applications -> Add an application -> Self-hosted
```

Example:

```text
Application name: DocVault
Application domain: docvault.example.com
Session duration: 8 hours
Policy: Allow
Include: Emails -> your-email@example.com
```

For admin UIs, require Access:

```text
argocd.example.com
grafana.example.com
harbor.example.com
```

For DocVault app, decide whether you want double login:

```text
Cloudflare Access login -> Keycloak login
```

This is acceptable for a private demo, but if OAuth callbacks get confusing, leave Cloudflare Access on admin UIs only and let DocVault use Keycloak normally.

## 8. Service URLs for This Repo

After Argo CD deploys the stack, confirm services:

```powershell
kubectl get svc -A
```

Likely service targets:

```text
DocVault web:
http://docvault-web.docvault.svc.cluster.local:3006

Gateway:
http://docvault-gateway.docvault.svc.cluster.local:3000

Keycloak:
http://keycloak.docvault.svc.cluster.local:8080

Argo CD:
http://argocd-server.argocd.svc.cluster.local:80

Grafana:
http://monitoring-stack-grafana.monitoring.svc.cluster.local:80
```

For Harbor, inspect service names after install:

```powershell
kubectl get svc -n harbor
```

Then route the UI to the Harbor portal/core endpoint recommended by the Harbor chart service layout.

## 9. DocVault/Keycloak Callback Notes

If exposing DocVault through:

```text
https://docvault.example.com
```

then Keycloak browser URLs and redirect URIs must match the public hostname.

At minimum, expect to update:

- Keycloak valid redirect URIs.
- Keycloak web origins.
- `FRONTEND_URL`.
- `KEYCLOAK_BROWSER_BASE_URL`.
- `ALLOWED_ORIGINS`.

For a quick first tunnel test, route only the web app and use demo login if available. Then fix Keycloak public URL behavior once the tunnel is stable.

## 10. Verification

Check tunnel pods:

```powershell
kubectl get pods -n cloudflare-tunnel
kubectl logs -n cloudflare-tunnel deploy/cloudflared --tail=100
```

Check application route:

```powershell
curl -I https://docvault.example.com
```

Check in browser:

```text
https://docvault.example.com
```

Check Cloudflare:

```text
Zero Trust -> Networks -> Connectors -> Cloudflare Tunnels -> your tunnel -> Healthy
Zero Trust -> Access -> Logs
```

## 10.1. Troubleshooting a Route That Does Not Open

Debug in this order.

### A. Domain is active in Cloudflare

Cloudflare Dashboard:

```text
Websites -> docvault.id.vn -> Active
```

If the site is still pending, fix nameservers at the domain registrar first.

### B. Hostname resolves to Cloudflare

```powershell
nslookup harbor.docvault.id.vn
```

Expected: Cloudflare returns an address or CNAME path. If it says nonexistent domain, the published application route/DNS record is missing or the zone is not active.

### C. Tunnel connector is healthy

```powershell
kubectl get pods -n cloudflare-tunnel
kubectl logs -n cloudflare-tunnel -l app=cloudflared --tail=100
```

Expected log:

```text
Registered tunnel connection
```

Also check Cloudflare:

```text
Zero Trust -> Networks -> Connectors -> Cloudflare Tunnels -> Healthy
```

### D. Published application route points to the right internal service

For Harbor in this repo:

```text
Service type: HTTP
Service URL: http://harbor.harbor.svc.cluster.local:80
```

Do not use `https://` inside the cluster for this Harbor tunnel values file.

### E. Harbor service works inside the cluster

```powershell
kubectl run curl-test -n cloudflare-tunnel --rm -it --image=curlimages/curl --restart=Never -- `
  curl -I http://harbor.harbor.svc.cluster.local:80
```

Expected: HTTP response from Harbor/nginx, often `200`, `302`, or similar.

### F. Harbor pods are ready

```powershell
kubectl get pods -n harbor
kubectl logs -n harbor deploy/harbor-core --tail=100
kubectl logs -n harbor deploy/harbor-jobservice --tail=100
```

If Harbor is still starting or failing, fix pods before debugging Cloudflare.

## 11. Harbor Position in the New Plan

New recommendation:

```text
Harbor UI: OK through Cloudflare published application route.
Harbor registry push/pull: test carefully; keep direct DNS/LB as fallback.
```

If you want to try Harbor registry through the published route anyway, prove it with:

```powershell
docker login harbor.example.com
docker pull alpine:3.20
docker tag alpine:3.20 harbor.example.com/docvault-dev/alpine-test:tunnel
docker push harbor.example.com/docvault-dev/alpine-test:tunnel
docker pull harbor.example.com/docvault-dev/alpine-test:tunnel
```

Then test a real DocVault image. If push fails with upload/body/timeout errors, do not burn time debugging Harbor. Use NGINX Ingress or a direct LoadBalancer for registry traffic.

## 11.1. Deploy Harbor for Cloudflare Tunnel Testing

The repo includes a Harbor values file for this path:

```text
infra/k8s/harbor/values-eks-cloudflare-tunnel.yaml
```

It uses:

```yaml
expose:
  type: clusterIP
  tls:
    enabled: false
```

TLS is terminated at Cloudflare. Inside the cluster, `cloudflared` connects to Harbor over HTTP.

Before install, replace:

```yaml
externalURL: https://harbor.example.com
```

with your Cloudflare published route hostname:

```yaml
externalURL: https://harbor.<your-domain>
```

Then deploy:

```powershell
kubectl apply -f infra/k8s/infra-deps/storageclass.yaml
kubectl create namespace harbor --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic harbor-bootstrap-secrets `
  -n harbor `
  --from-literal=HARBOR_ADMIN_PASSWORD="<strong-admin-password>" `
  --from-literal=secretKey="<16-char-random-key>"

helm repo add harbor https://helm.goharbor.io
helm repo update
helm upgrade --install harbor harbor/harbor `
  -n harbor `
  -f infra/k8s/harbor/values-eks-cloudflare-tunnel.yaml
```

Check:

```powershell
kubectl get pods -n harbor
kubectl get pvc -n harbor
kubectl get svc -n harbor
```

The Cloudflare route should point to:

```text
http://harbor.harbor.svc.cluster.local:80
```

Use service type:

```text
HTTP
```

Then open:

```text
https://harbor.<your-domain>
```

Default username:

```text
admin
```

Password is the value you used for `HARBOR_ADMIN_PASSWORD`.

## 11.2. Verified Harbor Tunnel Push/Pull Evidence

Verified on 2026-05-26:

```text
Registry: harbor.docvault.id.vn
Project: docvault-dev
Robot account: robot$docvault-dev+jenkins-push
Test image: harbor.docvault.id.vn/docvault-dev/alpine-test:manual
Result: docker login, push, and pull succeeded through Cloudflare Tunnel.
Digest: sha256:c64c687cbea9300178b30c95835354e34c4e4febc4badfe27102879de0483b5e
```

Commands used:

```powershell
docker login harbor.docvault.id.vn
docker pull alpine:3.20
docker tag alpine:3.20 harbor.docvault.id.vn/docvault-dev/alpine-test:manual
docker push harbor.docvault.id.vn/docvault-dev/alpine-test:manual
docker pull harbor.docvault.id.vn/docvault-dev/alpine-test:manual
```

This proves the tunnel path works for a small registry image. The next validation is pushing one real DocVault service image from Jenkins.

## 11.3. Managing Harbor Robot Credentials

Harbor robot account JSON contains a username and token. Treat it as a password.

Recommended options:

| Option | Where the secret lives | Jenkins sees credential? | Good for |
|---|---|---:|---|
| Jenkins Credentials | Jenkins credential store | Yes | Fastest MVP/demo setup. |
| AWS Secrets Manager Credentials Provider | AWS Secrets Manager | Jenkins references it by ID | Better project story on AWS. |
| HashiCorp Vault / other external vault | Vault | Jenkins fetches at runtime | Strongest general pattern, more setup. |
| Manual env var on Jenkins agent | Shell/session | Yes, fragile | Avoid except quick debugging. |

### MVP path

Use Jenkins Credentials with:

```text
Kind: Username with password
ID: harbor-docvault-dev-robot
Username: robot$docvault-dev+jenkins-push
Password: <robot-token>
```

This is acceptable for a short demo if Jenkins access is restricted and the credential is masked in logs.

### Better AWS path

Store the robot credential in AWS Secrets Manager and expose it to Jenkins through the **AWS Secrets Manager Credentials Provider** plugin.

High-level flow:

```text
Harbor robot JSON
  -> AWS Secrets Manager secret
  -> Jenkins plugin exposes it as a credential ID
  -> Pipeline still uses REGISTRY_CREDENTIAL_ID=harbor-docvault-dev-robot
```

The pipeline does not need to know whether the credential came from Jenkins' local store or AWS Secrets Manager; it only uses the credential ID.

Benefits:

- Secret is encrypted with AWS KMS.
- You can rotate the Harbor robot token without editing the Jenkinsfile.
- Jenkins can be treated as a consumer, not the source of truth.
- Better evidence for security hardening.

Keep these rules:

- Do not commit the robot JSON.
- Delete the local JSON after storing it.
- Use separate robot accounts for dev push and prod promotion.
- Do not grant Jenkins project admin permissions in Harbor.

## 12. Evidence Checklist

- Cloudflare Tunnel shows `Healthy`.
- `kubectl get pods -n cloudflare-tunnel` shows two running cloudflared pods.
- Published application route exists for `docvault.example.com`.
- Cloudflare Access policy protects admin routes.
- Browser opens DocVault over HTTPS without exposing a Kubernetes LoadBalancer.
- Screenshot of Cloudflare Access logs for a successful login.
- `kubectl get svc -A` shows app services remain `ClusterIP`/internal.

## 13. References

- Cloudflare Add routes docs: <https://developers.cloudflare.com/cloudflare-one/networks/routes/add-routes/#add-a-published-application-route>
- Cloudflare Tunnel on Kubernetes: <https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/deployment-guides/kubernetes/>
- Cloudflare Access self-hosted applications: <https://developers.cloudflare.com/cloudflare-one/applications/configure-apps/self-hosted-apps/>
