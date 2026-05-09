param(
  [string]$Namespace = "argocd",
  [string]$ServiceAccount = "jenkins-argocd-reader",
  [string]$TokenSecret = "jenkins-argocd-reader-token",
  [string]$RbacManifest = "infra/k8s/ci/jenkins-argocd-reader.yaml",
  [string]$OutputPath = "jenkins-argocd-reader.kubeconfig"
)

$ErrorActionPreference = "Stop"

Write-Host "Applying RBAC manifest: $RbacManifest"
kubectl apply -f $RbacManifest

Write-Host "Reading current Kubernetes cluster connection from active kubectl context..."
$clusterName = kubectl config view --raw --minify -o jsonpath='{.clusters[0].name}'
$server = kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.server}'
$certificateAuthorityData = kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}'

if (-not $clusterName -or -not $server -or -not $certificateAuthorityData) {
  throw "Could not read cluster name, server or certificate-authority-data from the active kubectl context."
}

Write-Host "Waiting for ServiceAccount token secret $Namespace/$TokenSecret..."
$tokenData = ""
for ($i = 0; $i -lt 30; $i++) {
  $tokenData = kubectl -n $Namespace get secret $TokenSecret -o jsonpath='{.data.token}' 2>$null
  if ($tokenData) {
    break
  }
  Start-Sleep -Seconds 2
}

if (-not $tokenData) {
  throw "Token secret $Namespace/$TokenSecret was not populated."
}

$token = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($tokenData))

$contextName = "$ServiceAccount@$clusterName"
$userName = "$ServiceAccount-token"

$kubeconfig = @"
apiVersion: v1
kind: Config
clusters:
  - name: $clusterName
    cluster:
      server: $server
      certificate-authority-data: $certificateAuthorityData
users:
  - name: $userName
    user:
      token: $token
contexts:
  - name: $contextName
    context:
      cluster: $clusterName
      namespace: $Namespace
      user: $userName
current-context: $contextName
"@

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
Set-Content -LiteralPath $outputFullPath -Value $kubeconfig -NoNewline

Write-Host "Generated kubeconfig: $outputFullPath"
Write-Host "Testing read-only Argo CD Application access..."
kubectl --kubeconfig $outputFullPath get applications -n $Namespace

Write-Host ""
Write-Host "Next step: copy this kubeconfig into Jenkins as a secret file credential or mount it as KUBECONFIG."
