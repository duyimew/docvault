# DocVault Port Forward Testing

Tai lieu nay dung de test DocVault tren EKS/Argo CD qua `kubectl port-forward`.

## 1. Chuan bi hosts cho Keycloak

Mo PowerShell bang quyen Administrator, them hostname `keycloak` tro ve local:

```powershell
Add-Content -Path C:\Windows\System32\drivers\etc\hosts -Value "`n127.0.0.1 keycloak"
```

Kiem tra:

```powershell
ping keycloak
```

## 2. Chay port-forward

Mo 3 PowerShell terminal rieng, moi terminal chay 1 lenh va de nguyen khong tat.

### Web app

```powershell
kubectl port-forward -n docvault svc/docvault-web 3006:3006
```

Truy cap:

```text
http://localhost:3006
```

### Gateway API

Image frontend hien tai goi API tai `http://localhost:3000/api`, nen forward gateway vao port `3000`:

```powershell
kubectl port-forward -n docvault svc/docvault-gateway 3000:3000
```

Kiem tra:

```powershell
curl.exe http://localhost:3000/api/health
```

Ket qua mong doi:

```json
{"status":"ok","service":"gateway"}
```

### Keycloak

Khong dung local port `8080` neu may dang chay Jenkins. Dung port `18080`:

```powershell
kubectl port-forward -n docvault svc/keycloak 18080:8080
```

Truy cap Keycloak:

```text
http://keycloak:18080
```

## 3. Dang nhap demo

Mo:

```text
http://localhost:3006/login
```

Khi bam SSO, browser phai redirect sang:

```text
http://keycloak:18080/realms/docvault/...
```

Tai khoan demo:

| Username | Password | Role |
| --- | --- | --- |
| `viewer1` | `Passw0rd!` | viewer |
| `editor1` | `Passw0rd!` | editor |
| `approver1` | `Passw0rd!` | approver |
| `co1` | `Passw0rd!` | compliance_officer |
| `admin1` | `Passw0rd!` | admin |

## 4. Test API bang token

Lay token tu Keycloak:

```powershell
$token = (Invoke-RestMethod `
  -Method Post `
  -Uri "http://keycloak:18080/realms/docvault/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    client_id="docvault-gateway"
    client_secret="dev-gateway-secret"
    grant_type="password"
    username="editor1"
    password="Passw0rd!"
  }).access_token
```

Goi API gateway:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:3000/api/me" `
  -Headers @{ Authorization = "Bearer $token" }
```

List documents:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:3000/api/metadata/documents" `
  -Headers @{ Authorization = "Bearer $token" }
```

Ket qua ban dau co the la mang rong:

```json
[]
```

## 5. Troubleshooting nhanh

Kiem tra service:

```powershell
kubectl get svc -n docvault
```

Kiem tra pod:

```powershell
kubectl get pods -n docvault
```

Kiem tra Argo CD health:

```powershell
kubectl get applications -n argocd
```

Neu browser bao `ERR_CONNECTION_REFUSED` toi `localhost:3000`, gateway port-forward chua chay hoac bi tat:

```powershell
kubectl port-forward -n docvault svc/docvault-gateway 3000:3000
```

Neu SSO redirect sang Jenkins o `localhost:8080`, Keycloak URL/hosts chua dung. Can dam bao:

```text
127.0.0.1 keycloak
```

va dang chay:

```powershell
kubectl port-forward -n docvault svc/keycloak 18080:8080
```

Neu `/api/metadata/documents` tra `500`, kiem tra metadata-service va database schema:

```powershell
kubectl logs -n docvault deploy/docvault-metadata --tail=100
kubectl exec -n docvault deploy/db -- psql -U docvault -d docvault_metadata -c "\dt"
```

Luu y: Postgres hien dang dung `emptyDir`, neu pod `db` bi recreate thi schema/data co the mat va can apply lai migration.
