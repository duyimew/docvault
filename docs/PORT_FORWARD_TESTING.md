# DocVault EKS Access Guide

Tai lieu nay dung de truy cap DocVault tren EKS.

## Kien truc

```
Browser ─── NodePort:30006 ───► Web App (Next.js)
  │                                 │
  │  /api/* (rewrites)              ▼ (server-side proxy)
  │                            Gateway (ClusterIP:3000)
  │
  └── SSO ── NodePort:30080 ──► Keycloak
```

- **Web App**: NodePort 30006 — truy cap truc tiep
- **Gateway API**: ClusterIP — browser goi `/api/*` qua web app, Next.js proxy server-side
- **Keycloak**: NodePort 30080 — browser redirect cho SSO

## 1. Setup sau moi lan scale node

Sau khi scale node tu 0 len (hoac lan dau deploy), chay:

```powershell
.\scripts\setup-eks-access.ps1
```

Script tu dong:
1. Detect node external IP moi
2. Patch `FRONTEND_URL`, `KEYCLOAK_BROWSER_BASE_URL`, `ALLOWED_ORIGINS`
3. Update Keycloak redirect URIs

**Khong can commit, khong can pipeline, khong rebuild image.**

## 2. Truy cap

Sau khi script chay xong, se in ra URL:

```text
http://<node-ip>:30006          → Web App
http://<node-ip>:30006/api      → Gateway API (proxied)
http://<node-ip>:30080          → Keycloak
```

## 3. Dang nhap demo

Mo: `http://<node-ip>:30006/login`

| Username | Password | Role |
| --- | --- | --- |
| `viewer1` | `Passw0rd!` | viewer |
| `editor1` | `Passw0rd!` | editor |
| `approver1` | `Passw0rd!` | approver |
| `co1` | `Passw0rd!` | compliance_officer |
| `admin1` | `Passw0rd!` | admin |

## 4. Test API

```powershell
$nodeIp = kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type==\"ExternalIP\")].address}'

$token = (Invoke-RestMethod `
  -Method Post `
  -Uri "http://${nodeIp}:30080/realms/docvault/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    client_id="docvault-gateway"
    client_secret="dev-gateway-secret"
    grant_type="password"
    username="editor1"
    password="Passw0rd!"
  }).access_token

# API calls go through web proxy
Invoke-RestMethod -Uri "http://${nodeIp}:30006/api/me" -Headers @{ Authorization = "Bearer $token" }
```

## 5. Scale node workflow

```powershell
# Di ngu - scale ve 0
aws eks update-nodegroup-config --cluster-name docvault-eks --nodegroup-name <ng-name> `
  --scaling-config minSize=0,desiredSize=0,maxSize=3 --region ap-southeast-1

# Sang hom sau - scale lai
aws eks update-nodegroup-config --cluster-name docvault-eks --nodegroup-name <ng-name> `
  --scaling-config minSize=1,desiredSize=2,maxSize=3 --region ap-southeast-1

# Chay setup script (1 lenh duy nhat)
.\scripts\setup-eks-access.ps1
```

## 6. Troubleshooting

```powershell
# Kiem tra services
kubectl get svc -n docvault

# Kiem tra pods
kubectl get pods -n docvault

# Kiem tra node IP
kubectl get nodes -o wide

# Kiem tra env hien tai cua web
kubectl get deployment docvault-web -n docvault -o jsonpath='{.spec.template.spec.containers[0].env}' | ConvertFrom-Json | Format-Table

# ArgoCD (van dung port-forward)
kubectl port-forward -n argocd svc/argocd-server 18081:443
```
