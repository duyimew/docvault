<#
.SYNOPSIS
  Detect node external IP and patch K8s deployments + Keycloak with the correct URLs.

.DESCRIPTION
  Run this script after scaling EKS nodes back up. It:
    1. Detects available node external IPs
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

# ── Step 1: Detect node external IPs ────────────────────────────
Write-Host "`n[1/3] Detecting node external IPs..." -ForegroundColor Yellow

$nodeIps = @()
$maxAttempts = 12
for ($i = 1; $i -le $maxAttempts; $i++) {
    # Parse 'kubectl get nodes -o wide' — EXTERNAL-IP is column 7
    $lines = kubectl get nodes -o wide 2>$null | Select-Object -Skip 1
    $foundIps = @()
    foreach ($line in $lines) {
        $cols = $line -split '\s+'
        if ($cols.Length -ge 7 -and $cols[6] -ne '<none>') {
            $foundIps += $cols[6]
        }
    }
    $nodeIps = @($foundIps | Select-Object -Unique)
    if ($nodeIps.Count -gt 0) { break }
    Write-Host "  Attempt $i/$maxAttempts - no nodes with ExternalIP yet, waiting 15s..."
    Start-Sleep -Seconds 15
}

if ($nodeIps.Count -eq 0) {
    Write-Error "No node external IP found. Are nodes running? Check: kubectl get nodes -o wide"
    exit 1
}

$nodeIp = $nodeIps[0]
$webUrls = @($nodeIps | ForEach-Object { "http://${_}:30006" })
$kcUrls = @($nodeIps | ForEach-Object { "http://${_}:30080" })
$webUrl = "http://${nodeIp}:30006"
$kcUrl  = "http://${nodeIp}:30080"
$kcIssuers = @($kcUrls | ForEach-Object { "${_}/realms/docvault" })
$kcIssuer = $kcIssuers[0]
$allowedOrigins = @($webUrls + @("http://localhost:3006")) -join ","
$acceptedIssuers = @($kcIssuers | Select-Object -Unique) -join ","

Write-Host "  Node IPs:     $($nodeIps -join ', ')" -ForegroundColor Green
Write-Host "  Web URLs:     $($webUrls -join ', ')" -ForegroundColor Green
Write-Host "  KC URLs:      $($kcUrls -join ', ')" -ForegroundColor Green
Write-Host "  Canonical Web: $webUrl" -ForegroundColor Green
Write-Host "  KC Issuers:   $acceptedIssuers" -ForegroundColor Green

# ── Step 2: Patch deployments ───────────────────────────────────
Write-Host "`n[2/3] Patching deployments..." -ForegroundColor Yellow

$runtimePatchedApps = @(
    "docvault-web",
    "docvault-gateway",
    "docvault-metadata",
    "docvault-document-service",
    "docvault-workflow-service",
    "docvault-audit-service",
    "docvault-notification-service"
)

foreach ($app in $runtimePatchedApps) {
    $argoPatch = @{
        spec = @{
            ignoreDifferences = @(
                @{
                    group = "apps"
                    kind = "Deployment"
                    jsonPointers = @("/spec/template/spec/containers/0/env")
                }
            )
            syncPolicy = @{
                syncOptions = @("CreateNamespace=true", "RespectIgnoreDifferences=true")
            }
        }
    } | ConvertTo-Json -Depth 10

    kubectl patch application $app -n argocd --type merge -p $argoPatch 2>$null | Out-Null
}

# Patch web app
Write-Host "  Patching docvault-web..."
kubectl set env deployment/docvault-web -n $Namespace `
    FRONTEND_URL="$webUrl" `
    KEYCLOAK_BROWSER_BASE_URL="$kcUrl"

# Patch gateway (CORS / callback URLs)
Write-Host "  Patching docvault-gateway..."
kubectl set env deployment/docvault-gateway -n $Namespace `
    FRONTEND_URL="$webUrl" `
    ALLOWED_ORIGINS="$allowedOrigins" `
    KEYCLOAK_BASE_URL="http://keycloak:8080" `
    KEYCLOAK_ISSUER="$acceptedIssuers"

# Patch backend services to accept public Keycloak issuers while keeping
# KEYCLOAK_BASE_URL on the in-cluster service for JWKS/token calls.
$authDeployments = @(
    "docvault-metadata",
    "docvault-document-service",
    "docvault-workflow-service",
    "docvault-audit-service",
    "docvault-notification-service"
)

foreach ($deployment in $authDeployments) {
    Write-Host "  Patching $deployment auth issuer..."
    kubectl set env deployment/$deployment -n $Namespace `
        KEYCLOAK_BASE_URL="http://keycloak:8080" `
        KEYCLOAK_ISSUER="$acceptedIssuers"
}

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

        # Update redirect URIs, post-logout redirects, and web origins.
        # Keep the existing client object so Keycloak does not drop unrelated
        # client settings during PUT.
        $client = $clients[0]
        $redirectUris = @(
            "http://localhost:3000/*",
            "http://localhost:3006/*",
            "http://localhost:3006/api/auth/callback"
        )
        foreach ($url in $webUrls) {
            $redirectUris += "$url/*"
            $redirectUris += "$url/api/auth/callback"
        }
        $client.redirectUris = @($redirectUris | Select-Object -Unique)

        $webOrigins = @(
            "http://localhost:3000",
            "http://localhost:3006"
        )
        $webOrigins += $webUrls
        $client.webOrigins = @($webOrigins | Select-Object -Unique)

        if (-not $client.attributes) {
            $client | Add-Member -MemberType NoteProperty -Name attributes -Value ([pscustomobject]@{})
        }
        $client.attributes | Add-Member `
            -MemberType NoteProperty `
            -Name "post.logout.redirect.uris" `
            -Value "+" `
            -Force

        $updateBody = $client | ConvertTo-Json -Depth 20

        Invoke-RestMethod -Method Put `
            -Uri "http://localhost:18090/admin/realms/docvault/clients/$clientUuid" `
            -Headers @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" } `
            -Body $updateBody

        Write-Host "  Keycloak redirect and logout URIs updated." -ForegroundColor Green
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
Write-Host "  All Web URLs: $($webUrls -join ', ')" -ForegroundColor Green
Write-Host "  Keycloak:     $kcUrl" -ForegroundColor Green
Write-Host "  Gateway API:  $webUrl/api  (proxied via Next.js)" -ForegroundColor Green
Write-Host ""
Write-Host "  ArgoCD:  kubectl port-forward -n argocd svc/argocd-server 18081:443" -ForegroundColor DarkGray
Write-Host ""
