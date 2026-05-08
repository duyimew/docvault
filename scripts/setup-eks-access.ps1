<#
.SYNOPSIS
  Detect node external IP and patch K8s deployments + Keycloak with the correct URLs.

.DESCRIPTION
  Run this script after scaling EKS nodes back up. It:
    1. Detects the first available node external IP
    2. Patches web and gateway deployments with NodePort-based URLs
    3. Updates Keycloak client redirect URIs via the admin API

  No Git commits, no pipeline runs, no image rebuilds needed.

.EXAMPLE
  .\scripts\setup-eks-access.ps1
#>
param(
    [string]$Namespace = "docvault"
)

$ErrorActionPreference = "Stop"

Write-Host "`n=== DocVault EKS Access Setup ===" -ForegroundColor Cyan

# ── Step 1: Detect node external IP ─────────────────────────────
Write-Host "`n[1/3] Detecting node external IP..." -ForegroundColor Yellow

$nodeIp = ""
$maxAttempts = 12
for ($i = 1; $i -le $maxAttempts; $i++) {
    # Parse 'kubectl get nodes -o wide' — EXTERNAL-IP is column 7
    $lines = kubectl get nodes -o wide 2>$null | Select-Object -Skip 1
    foreach ($line in $lines) {
        $cols = $line -split '\s+'
        if ($cols.Length -ge 7 -and $cols[6] -ne '<none>') {
            $nodeIp = $cols[6]
            break
        }
    }
    if ($nodeIp) { break }
    Write-Host "  Attempt $i/$maxAttempts - no nodes with ExternalIP yet, waiting 15s..."
    Start-Sleep -Seconds 15
}

if (-not $nodeIp) {
    Write-Error "No node external IP found. Are nodes running? Check: kubectl get nodes -o wide"
    exit 1
}

$webUrl = "http://${nodeIp}:30006"
$kcUrl  = "http://${nodeIp}:30080"

Write-Host "  Node IP:  $nodeIp" -ForegroundColor Green
Write-Host "  Web URL:  $webUrl" -ForegroundColor Green
Write-Host "  KC URL:   $kcUrl" -ForegroundColor Green

# ── Step 2: Patch deployments ───────────────────────────────────
Write-Host "`n[2/3] Patching deployments..." -ForegroundColor Yellow

# Patch web app
Write-Host "  Patching docvault-web..."
kubectl set env deployment/docvault-web -n $Namespace `
    FRONTEND_URL="$webUrl" `
    KEYCLOAK_BROWSER_BASE_URL="$kcUrl"

# Patch gateway (CORS / callback URLs)
Write-Host "  Patching docvault-gateway..."
kubectl set env deployment/docvault-gateway -n $Namespace `
    FRONTEND_URL="$webUrl" `
    ALLOWED_ORIGINS="$webUrl,http://localhost:3006"

Write-Host "  Deployments patched. Pods will restart automatically." -ForegroundColor Green

# ── Step 3: Update Keycloak redirect URIs ───────────────────────
Write-Host "`n[3/3] Updating Keycloak redirect URIs..." -ForegroundColor Yellow

# Wait for pods to be ready
Write-Host "  Waiting for pods to be ready..."
kubectl rollout status deployment/docvault-web -n $Namespace --timeout=120s 2>$null
kubectl rollout status deployment/docvault-gateway -n $Namespace --timeout=120s 2>$null

# Wait for keycloak
$kcReady = $false
for ($i = 1; $i -le 12; $i++) {
    $phase = kubectl get pod -n $Namespace -l app=keycloak -o jsonpath='{.items[0].status.phase}' 2>$null
    $ready = kubectl get pod -n $Namespace -l app=keycloak -o jsonpath='{.items[0].status.containerStatuses[0].ready}' 2>$null
    if ($phase -eq "Running" -and $ready -eq "true") {
        $kcReady = $true
        break
    }
    Write-Host "  Waiting for Keycloak pod (attempt $i/12)..."
    Start-Sleep -Seconds 10
}

if ($kcReady) {
    # Port-forward keycloak temporarily (internal port, not NodePort)
    $kcJob = Start-Job -ScriptBlock {
        kubectl port-forward -n $using:Namespace svc/keycloak 18090:8080 2>$null
    }
    Start-Sleep -Seconds 4

    try {
        # Get admin token
        $tokenRes = Invoke-RestMethod -Method Post `
            -Uri "http://localhost:18090/realms/master/protocol/openid-connect/token" `
            -ContentType "application/x-www-form-urlencoded" `
            -Body @{
                client_id  = "admin-cli"
                username   = "admin"
                password   = "adminpw"
                grant_type = "password"
            }
        $adminToken = $tokenRes.access_token

        # Get client UUID
        $clients = Invoke-RestMethod -Method Get `
            -Uri "http://localhost:18090/admin/realms/docvault/clients?clientId=docvault-gateway" `
            -Headers @{ Authorization = "Bearer $adminToken" }
        $clientUuid = $clients[0].id

        # Update redirect URIs and web origins
        $updateBody = @{
            redirectUris = @(
                "http://localhost:3000/*",
                "http://localhost:3006/*",
                "http://localhost:3006/api/auth/callback",
                "$webUrl/*",
                "$webUrl/api/auth/callback"
            )
            webOrigins = @(
                "http://localhost:3000",
                "http://localhost:3006",
                $webUrl
            )
        } | ConvertTo-Json

        Invoke-RestMethod -Method Put `
            -Uri "http://localhost:18090/admin/realms/docvault/clients/$clientUuid" `
            -Headers @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" } `
            -Body $updateBody

        Write-Host "  Keycloak redirect URIs updated." -ForegroundColor Green
    }
    catch {
        Write-Warning "Could not auto-update Keycloak: $_"
        Write-Warning "Manually update redirect URIs in Keycloak admin at $kcUrl/admin"
    }
    finally {
        Stop-Job $kcJob -ErrorAction SilentlyContinue
        Remove-Job $kcJob -ErrorAction SilentlyContinue
    }
}
else {
    Write-Warning "Keycloak not ready. Run this script again after Keycloak starts."
}

# ── Summary ─────────────────────────────────────────────────────
Write-Host "`n=== Setup Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Web App:      $webUrl" -ForegroundColor Green
Write-Host "  Keycloak:     $kcUrl" -ForegroundColor Green
Write-Host "  Gateway API:  $webUrl/api  (proxied via Next.js)" -ForegroundColor Green
Write-Host ""
Write-Host "  ArgoCD:  kubectl port-forward -n argocd svc/argocd-server 18081:443" -ForegroundColor DarkGray
Write-Host ""
