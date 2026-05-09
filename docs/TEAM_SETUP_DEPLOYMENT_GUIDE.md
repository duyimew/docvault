# DocVault Team Setup & Deployment Guide

Mục tiêu của tài liệu này là làm entry point cho thành viên mới clone repo về,
chạy dự án, dùng Jenkins/SonarQube trên Docker, dùng Terraform để tạo AWS EKS,
deploy bằng Argo CD/GitOps và truy cập web app trên EKS qua NodePort.

Tài liệu này gom các bước quan trọng nhất để triển khai trên EKS. Khi cần chi
tiết hơn, đọc thêm:

- `docs/DEVSECOPS_PIPELINE_SETUP_GUIDE.md`: cấu hình Jenkins pipeline.
- `docs/jenkins_docker.md`: chạy Jenkins bằng Docker.
- `docs/sonarqube-docker-jenkins-setup.md`: chạy SonarQube và nối với Jenkins.
- `docs/docvault_terraform_eks_argocd_plan.md`: tạo EKS và bootstrap Argo CD.
- `docs/docvault_eks_pause_resume_runbook.md`: tắt/mở node EKS để tiết kiệm chi phí.
- `docs/PORT_FORWARD_TESTING.md`: truy cập EKS bằng NodePort sau khi node IP thay đổi.
- `docs/demo-flow.md`: kịch bản demo EKS gồm app, Jenkins/ZAP, Grafana và Loki.
- `docs/security-sca-triage.md`: bản ghi xử lý SCA, package đã fix và exception còn lại.

## 1. Repository và Branch Model

- Source repo: `https://github.com/daithang59/docvault.git`
- Branch phát triển hiện tại: `devsecops-pipeline`
- Branch đích PR: `main`
- Branch GitOps: `gitops-testing`

Luồng hiện tại:

1. Developer push code lên branch source.
2. Jenkins build/test/scan, build Docker image và push image.
3. Jenkins cập nhật Helm values trên branch `gitops-testing`.
4. Argo CD trên EKS watch branch `gitops-testing` và sync vào cluster.

Không commit secrets, `.env`, `terraform.tfvars`, Terraform state hoặc plan file.

## 2. Prerequisites

Máy dùng để thao tác triển khai cần có:

- Git
- Docker Desktop hoặc Docker Engine + Docker Compose
- Node.js 20+
- pnpm 9.15.0
- AWS CLI
- Terraform 1.6+
- kubectl
- Helm, Argo CD CLI và Checkov nếu cần thao tác/scan thủ công
- Docker Hub account và GitHub token cho Jenkins credentials
- AWS IAM permission đủ để tạo VPC, EKS, IAM role, Security Group và EBS add-on

Khuyến nghị kích hoạt đúng pnpm version của repo:

```powershell
corepack enable
corepack prepare pnpm@9.15.0 --activate
```

## 3. Clone Repository

```powershell
git clone https://github.com/daithang59/docvault.git
cd docvault
git checkout devsecops-pipeline
```

Nếu đang clone sau khi PR đã merge, checkout `main` thay cho
`devsecops-pipeline`.

## 4. Jenkins và SonarQube Trên Docker

### 4.1. Tạo Docker network

```powershell
docker network create jenkins
```

Nếu network đã tồn tại, Docker sẽ báo lỗi trùng tên; có thể bỏ qua.

### 4.2. Build Jenkins image có Docker CLI, Buildx và kubectl

Repo có `Dockerfile.jenkins` dựa trên `jenkins/jenkins:lts-jdk21` và cài Docker
CLI, Docker Buildx và `kubectl` để Jenkins có thể build image bằng BuildKit
và tự kiểm tra Argo CD health.

```powershell
docker build -t docvault-jenkins:lts-jdk21 -f Dockerfile.jenkins .
```

Nếu dùng container local hiện tại của nhóm, image tag đang dùng là:

```powershell
docker build -t myjenkins-blueocean:lts-jdk21 -f Dockerfile.jenkins .
```

### 4.3. Start Docker-in-Docker daemon

```powershell
docker run --name jenkins-docker --rm --detach `
  --privileged --network jenkins --network-alias docker `
  --env DOCKER_TLS_CERTDIR=/certs `
  --volume jenkins-docker-certs:/certs/client `
  --volume jenkins-data:/var/jenkins_home `
  --publish 2376:2376 `
  docker:dind
```

### 4.4. Start Jenkins controller

```powershell
docker run --name jenkins-blueocean --restart=on-failure --detach `
  --network jenkins `
  --env DOCKER_HOST=tcp://docker:2376 `
  --env DOCKER_CERT_PATH=/certs/client `
  --env DOCKER_TLS_VERIFY=1 `
  --publish 8080:8080 --publish 50000:50000 `
  --volume jenkins-data:/var/jenkins_home `
  --volume jenkins-docker-certs:/certs/client:ro `
  docvault-jenkins:lts-jdk21
```

Lấy initial admin password:

```powershell
docker exec jenkins-blueocean cat /var/jenkins_home/secrets/initialAdminPassword
```

Mở Jenkins: `http://localhost:8080`

### 4.5. Start SonarQube

```powershell
docker volume create sonarqube_data
docker volume create sonarqube_extensions
docker volume create sonarqube_logs

docker run -d --name sonarqube --network jenkins `
  -p 9000:9000 `
  -v sonarqube_data:/opt/sonarqube/data `
  -v sonarqube_extensions:/opt/sonarqube/extensions `
  -v sonarqube_logs:/opt/sonarqube/logs `
  sonarqube:lts-community
```

Mở SonarQube: `http://localhost:9000`

Lần đầu đăng nhập `admin` / `admin`, đổi password, tạo token cho Jenkins.

### 4.6. Jenkins configuration cần có

Credentials:

- `dockerhub-credentials`: Docker Hub username/password hoặc token.
- `github-credentials`: GitHub PAT có quyền push branch GitOps.
- `sonar-token`: token tạo trong SonarQube.

SonarQube server trong Jenkins:

- Name: `sqdocvault`
- URL nếu Jenkins và SonarQube cùng Docker network: `http://sonarqube:9000`

Global Shared Library:

- Name: `docvault`
- Default version: `devsecops-pipeline`
- Repo URL: `https://github.com/daithang59/docvault.git`

Pipeline job:

- Branch: `*/devsecops-pipeline`
- Script path: `Jenkinsfile`
- Agent label trong Jenkinsfile: `docker-agent-alpine-ubuntu-vm`

Nếu Jenkins agent label khác, cập nhật node label hoặc Jenkinsfile cho khớp.

Pipeline parameters quan trọng:

- `FORCE_BUILD_ALL=true`: rebuild toàn bộ image, phù hợp lần chạy đầu.
- `GITOPS_BRANCH=gitops-testing`: branch Argo CD đang watch.
- `DEPLOY_TARGET_URL=http://<node-external-ip>:30006`: web base URL dùng cho smoke test sau deploy. Jenkins sẽ kiểm tra `GET /` và `GET /api/health`.
- `RUN_ARGO_HEALTH_CHECK=true`: bật khi Jenkins agent đã có `kubectl` và kubeconfig truy cập được namespace `argocd`.
- `ARGOCD_NAMESPACE=argocd`: namespace chứa Argo CD Application.
- `ARGOCD_APPS=docvault-gateway docvault-metadata docvault-document-service docvault-workflow-service docvault-audit-service docvault-notification-service docvault-web`: danh sách app service chính cần đợi `Synced/Healthy`.
- `ARGOCD_TIMEOUT_SECONDS=300`: thời gian tối đa chờ Argo CD sync/health.
- `KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig`: Jenkins Secret file credential chứa kubeconfig read-only cho ServiceAccount `jenkins-argocd-reader`.
- `RUN_ZAP=false`: để false khi app chưa deploy hoặc NodePort chưa reachable.
- `ZAP_TARGET=http://<node-external-ip>:30006`: web base URL dùng cho OWASP ZAP baseline scan khi bật ZAP. Nếu để trống, pipeline dùng `DEPLOY_TARGET_URL`. Không dùng `/api` làm target baseline vì `/api` root có thể trả 404; web app vẫn gọi API qua `/api/...` như bình thường.

Post-deploy verification hiện tại:

- `vars/argocdHealthCheck.groovy` dùng `kubectl get application` để đợi các Argo CD app đạt `Synced/Healthy`.
- `vars/postDeploySmokeTest.groovy` gọi `GET http://<node-external-ip>:30006` và `GET http://<node-external-ip>:30006/api/health`, retry tối đa khoảng 5 phút.
- Jenkins hiện đã có kubeconfig read-only qua credential `jenkins-argocd-kubeconfig`; nếu credential bị thiếu hoặc Jenkins image chưa có `kubectl`, giữ `RUN_ARGO_HEALTH_CHECK=false` và xác minh Argo CD thủ công.

DAST policy hiện tại:

- `vars/dastZap.groovy` kiểm tra `ZAP_TARGET` reachable trước khi chạy scan.
- ZAP chạy bằng image `ghcr.io/zaproxy/zaproxy:stable`.
- Report được xuất ra:
  - `zap-report/zap_report.html`
  - `zap-report/zap_report.json`
- `vars/postCleanup.groovy` archive các report này làm Jenkins artifacts.
- ZAP baseline chạy với `-I`, nghĩa là warning vẫn được ghi vào report nhưng không làm fail pipeline demo.
- Sau khi report được sinh, pipeline đọc `zap_report.json`; nếu có riskcode High/Critical thì stage fail để buộc fix hoặc tạo exception.
- Security gate policy:
  - Critical/High: fail pipeline hoặc cần exception record rõ ràng.
  - Medium/Low/ZAP warning: archive report, review, không chặn MVP demo.

## 5. Terraform AWS EKS

Đăng nhập AWS và kiểm tra identity:

```powershell
aws configure
aws sts get-caller-identity
```

Tạo EKS:

```powershell
cd infra\terraform\aws-eks
Copy-Item terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -out tfplan
terraform apply tfplan
```

Default hiện tại:

- Region: `ap-southeast-1`
- Cluster name: `docvault-eks`
- Node type: `t3.large`
- Node desired/min/max: `2/1/3`
- NAT Gateway: disabled để giảm chi phí MVP

Trước khi apply, nên sửa `terraform.tfvars` để giới hạn
`cluster_endpoint_public_access_cidrs` về public IP của máy mình, ví dụ
`x.x.x.x/32`.

Checkov scan optional:

```powershell
checkov -d .
```

Sau khi apply:

```powershell
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes -o wide
```

Quay về repo root sau khi xong:

```powershell
cd ..\..\..
```

## 6. Bootstrap Argo CD và GitOps Apps

Cài Argo CD:

```powershell
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Mở Argo CD UI:

```powershell
kubectl port-forward -n argocd svc/argocd-server 18081:443
```

Admin password:

```powershell
kubectl -n argocd get secret argocd-initial-admin-secret `
  -o jsonpath="{.data.password}" | ForEach-Object {
    [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_))
  }
```

Mở: `https://localhost:18081`

Nếu repo private, thêm repository credential vào Argo CD trước khi apply apps.

Apply GitOps applications:

```powershell
kubectl apply -f infra\argocd-apps\docvault-infra.yaml
kubectl apply -f infra\argocd-apps\docvault-apps.yaml
kubectl apply -f infra\argocd-apps\monitoring.yaml
kubectl apply -f infra\argocd-apps\loki.yaml
```

Kiểm tra:

```powershell
kubectl get applications -n argocd
kubectl get pods -n docvault
kubectl get svc -n docvault
kubectl get pods -n monitoring
```

Argo CD sẽ sync từ branch `gitops-testing`. Jenkins pipeline là nơi cập nhật
image tag/digest trên branch này sau khi build thành công.

Các app observability hiện tại:

- `monitoring-stack`: cài `kube-prometheus-stack`, gồm Prometheus, Grafana, Alertmanager, kube-state-metrics và node-exporter.
- `loki-stack`: cài Loki và promtail để thu log container từ các namespace, gồm `docvault`.

Cả hai app đều bật Argo CD automated sync:

- `prune: true`
- `selfHeal: true`

Grafana được cấu hình sẵn datasource:

- `Prometheus`: datasource mặc định cho metrics.
- `Alertmanager`: datasource cảnh báo.
- `Loki`: datasource log, trỏ tới `http://loki-stack:3100`.

## 7. Truy Cập EKS Bằng NodePort

Sau khi node được tạo mới, scale về 0 rồi scale lên lại, hoặc node external IP
thay đổi, chạy:

```powershell
.\scripts\setup-eks-access.ps1
```

Script sẽ:

1. Detect node external IP.
2. Patch runtime env cho web/gateway/backend để dùng URL public hiện tại.
3. Cập nhật Keycloak redirect URI và web origins.

URL mặc định trên EKS:

- Web: `http://<node-external-ip>:30006`
- Keycloak: `http://<node-external-ip>:30080`
- Gateway API: `http://<node-external-ip>:30006/api`

Quy ước truy cập:

- Web app và Keycloak login cho người test dùng NodePort.
- Argo CD, Keycloak admin hoặc các service nội bộ khi debug có thể dùng
  `kubectl port-forward`.

Port-forward hay dùng:

```powershell
kubectl port-forward -n argocd svc/argocd-server 18081:443
kubectl port-forward -n docvault svc/keycloak 18090:8080
kubectl port-forward -n docvault svc/minio 19001:9001
kubectl port-forward -n monitoring svc/monitoring-stack-grafana 3000:80
kubectl port-forward -n monitoring svc/loki-stack 3100:3100
```

Nếu cluster có nhiều node, script in ra `All Web URLs`. Dùng bất kỳ URL nào
trong danh sách đó.

Ghi chú: NodePort URL phụ thuộc external IP của node. Vì vậy sau mỗi lần resume
cluster, nên chạy lại `setup-eks-access.ps1` trước khi test login/upload/preview.

## 8. Security Headers, DAST và Observability

### 8.1. Web security headers

Web app cấu hình security headers trong `apps/web/next.config.ts`:

- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy` khóa các browser capabilities không dùng như camera, microphone, geolocation, payment, USB.
- `Cross-Origin-Opener-Policy: same-origin`
- `Cross-Origin-Embedder-Policy: require-corp`
- `Cross-Origin-Resource-Policy: same-origin`
- `Content-Security-Policy` giới hạn resource về cùng origin, chặn object embed và frame ancestor.
- `poweredByHeader: false` để bỏ `X-Powered-By: Next.js`.

Repo cũng có:

- `apps/web/public/robots.txt`
- `apps/web/public/sitemap.xml`

Hai file này tránh việc ZAP lặp lại warning trên `robots.txt` và `sitemap.xml` trả 404.

Sau khi deploy web image mới, kiểm tra header:

```powershell
curl.exe -i http://<node-external-ip>:30006
curl.exe -i http://<node-external-ip>:30006/_next/static/chunks/<chunk>.js
```

### 8.2. OWASP ZAP DAST

Chỉ bật ZAP khi web app đã reachable từ Jenkins/ZAP container.

Jenkins parameters:

```text
DEPLOY_TARGET_URL=http://<node-external-ip>:30006
RUN_ZAP=true
ZAP_TARGET=http://<node-external-ip>:30006
GITOPS_BRANCH=gitops-testing
```

Expected result:

- Stage `DAST - OWASP ZAP` chạy thành công.
- `FAIL-NEW: 0` là điều kiện tốt cho demo.
- Warning còn lại được xem trong report và ghi nhận nếu cần hardening tiếp.
- Jenkins artifacts có `zap_report.html` và `zap_report.json`.

Nếu dùng nhầm `ZAP_TARGET=http://<node-external-ip>:30006/api`, ZAP baseline có thể báo spider error vì `/api` root trả 404. Điều này không có nghĩa API prefix của web sai; đó chỉ là target khởi đầu không phù hợp cho baseline crawl.

### 8.3. Post-deploy smoke và Argo CD health gate

Sau stage `Push & GitOps`, pipeline có thể tự xác minh deploy thật thay vì chỉ xem thủ công.

Jenkins parameters khuyến nghị khi app đang chạy trên EKS:

```text
DEPLOY_TARGET_URL=http://<node-external-ip>:30006
RUN_ARGO_HEALTH_CHECK=true
ARGOCD_NAMESPACE=argocd
ARGOCD_TIMEOUT_SECONDS=300
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
```

Smoke test tự động kiểm tra:

```text
GET http://<node-external-ip>:30006
GET http://<node-external-ip>:30006/api/health
```

Argo CD health check tự động đợi các Application trong `ARGOCD_APPS` đạt:

```text
sync=Synced
health=Healthy
```

Trạng thái hiện tại đã có ServiceAccount `jenkins-argocd-reader` và Jenkins credential `jenkins-argocd-kubeconfig`, nên build `#61` đã pass stage này. Nếu credential bị thiếu hoặc cluster access lỗi, tạm giữ `RUN_ARGO_HEALTH_CHECK=false`, chạy pipeline để push GitOps/smoke/ZAP, rồi xác minh Argo CD thủ công bằng:

```powershell
kubectl get applications -n argocd
```

### 8.4. SCA triage

SCA dùng OWASP Dependency Check trong Jenkins và `pnpm audit` có thể dùng để kiểm tra nhanh local.

Các dependency trực tiếp và override bảo mật đã được xử lý trong `package.json`, `apps/web/package.json` và `pnpm-lock.yaml`. Chi tiết xem:

```text
docs/security-sca-triage.md
```

Policy đề xuất:

- Critical/High hoặc CVSS >= 7: pipeline fail bằng `--failOnCVSS 7`, trừ khi team tạo exception record và xử lý lại rule/gate có chủ đích.
- Medium/Low ở tooling/transitive: có thể chấp nhận cho MVP nếu có record trong SCA triage.
- ZAP warning: archive report, review và ghi action item nếu cần, không chặn MVP demo.
- Dependency Check HTML/JSON phải được lưu làm Jenkins artifact.

### 8.5. Prometheus, Grafana, Loki

Apply observability apps:

```powershell
kubectl apply -f infra\argocd-apps\monitoring.yaml
kubectl apply -f infra\argocd-apps\loki.yaml
```

Kiểm tra:

```powershell
kubectl get app monitoring-stack loki-stack -n argocd
kubectl get pods -n monitoring
```

Mở Grafana:

```powershell
kubectl port-forward -n monitoring svc/monitoring-stack-grafana 3000:80
```

URL: `http://127.0.0.1:3000`

Credential mặc định trong manifest demo:

```text
admin / admin
```

Nếu Grafana từ chối password sau khi đã đổi thủ công, reset lại:

```powershell
kubectl exec -n monitoring deploy/monitoring-stack-grafana -c grafana -- `
  grafana-cli admin reset-admin-password admin
```

Mở Loki API nếu cần kiểm tra trực tiếp:

```powershell
kubectl port-forward -n monitoring svc/loki-stack 3100:3100
$query = [uri]::EscapeDataString('{namespace="docvault"}')
Invoke-RestMethod -Uri "http://127.0.0.1:3100/loki/api/v1/query_range?query=$query&limit=5"
```

Trong Grafana Explore:

- Datasource: `Loki`
- Query:

```text
{namespace="docvault"}
```

Ảnh bằng chứng nên chụp:

- Grafana dashboard pod/workload health.
- CPU/RAM theo pod hoặc namespace.
- Grafana Explore log từ namespace `docvault`.
- Argo CD app `monitoring-stack` và `loki-stack` ở trạng thái `Synced/Healthy`.

## 9. Pause/Resume EKS Để Tiết Kiệm Chi Phí

Lấy node group name:

```powershell
aws eks list-nodegroups --region ap-southeast-1 --cluster-name docvault-eks
```

Scale về 0:

```powershell
aws eks update-nodegroup-config `
  --region ap-southeast-1 `
  --cluster-name docvault-eks `
  --nodegroup-name <nodegroup-name> `
  --scaling-config minSize=0,maxSize=3,desiredSize=0
```

Resume:

```powershell
aws eks update-nodegroup-config `
  --region ap-southeast-1 `
  --cluster-name docvault-eks `
  --nodegroup-name <nodegroup-name> `
  --scaling-config minSize=1,maxSize=3,desiredSize=2
```

Sau khi nodes Ready:

```powershell
aws eks update-kubeconfig --region ap-southeast-1 --name docvault-eks
kubectl get nodes -o wide
.\scripts\setup-eks-access.ps1
```

## 10. Checklist Trước Khi Mở PR Vào Main

Chạy verification trước khi mở PR:

```powershell
pnpm lint
pnpm test
pnpm build
```

Kiểm tra pipeline:

- Jenkins build xanh. Build `#61` trên branch `devsecops-pipeline` đã pass toàn bộ pipeline với Argo CD health check, smoke test và ZAP.
- SonarQube scan chạy thành công hoặc quality gate được xử lý theo rule của team.
- Docker image đã push lên registry.
- Branch `gitops-testing` đã được Jenkins cập nhật image tag/digest.
- Stage `Argo CD Health Check` pass nếu bật `RUN_ARGO_HEALTH_CHECK=true`; nếu chưa bật thì có screenshot Argo CD applications `Synced` và `Healthy`.
- Stage `Post-deploy Smoke Test` pass với `GET /` và `GET /api/health`.
- Smoke test thủ công EKS đã pass: login Keycloak, upload file, preview file, download file.
- DAST ZAP đã chạy với `ZAP_TARGET=http://<node-external-ip>:30006`, `FAIL-NEW: 0`, report HTML/JSON đã được archive.
- Dependency Check report đã được archive và đối chiếu với `docs/security-sca-triage.md`.
- Observability đã chạy: `monitoring-stack` và `loki-stack` `Synced/Healthy`.
- Grafana metrics và Loki logs đã có screenshot bằng chứng.

Thông tin nên ghi trong PR:

- Summary thay đổi.
- Các command đã verify.
- Link Jenkins build.
- Ảnh hoặc ghi chú test web app trên EKS.
- Nếu có thay đổi env, Terraform, Kubernetes, pipeline hoặc security scan thì ghi rõ.

## 11. Troubleshooting Nhanh

### Keycloak báo `Invalid parameter: redirect_uri`

Thường do NodePort IP thay đổi hoặc Keycloak chưa có redirect URI mới.

```powershell
.\scripts\setup-eks-access.ps1
```

Sau đó clear browser cache/cookies liên quan domain NodePort và login lại.

### Login xong bị quay về `/login`

Kiểm tra web image đang deploy là image mới nhất, vì web phải derive callback
origin từ request host/forwarded host khi chạy sau NodePort. Sau khi deploy lại,
chạy:

```powershell
.\scripts\setup-eks-access.ps1
```

### Preview file lỗi 404 hoặc request thành `/api/api/...`

Thường là stale browser bundle hoặc image cũ. Hard reload browser, verify image
trên Argo CD/Kubernetes, sau đó test lại.

```powershell
kubectl get deploy docvault-web -n docvault -o jsonpath="{.spec.template.spec.containers[0].image}"
```

### Download redirect sang `http://minio:9000/...`

Browser không truy cập được service DNS nội bộ `minio`. Cần đảm bảo luồng
download/preview đi qua gateway/web proxy, không trả MinIO internal URL trực tiếp
cho browser.

### Pod `ImagePullBackOff`

Kiểm tra image tag/digest trên `infra/k8s/values/*.yaml`, Docker Hub image đã
tồn tại, và Argo CD đã sync đúng branch `gitops-testing`.

### Terraform bị unauthorized

Kiểm tra AWS profile, region và IAM permission:

```powershell
aws sts get-caller-identity
aws configure list
```

### Jenkins không build được Docker image

Kiểm tra `jenkins-docker` container đang chạy, Jenkins container có
`DOCKER_HOST=tcp://docker:2376`, và Jenkins agent label khớp với Jenkinsfile.

### ZAP trả `script returned exit code 2`

Với `zap-baseline.py`, exit code `2` thường là có warning mới, không phải fail.
Pipeline hiện đã thêm `-I` để không fail build chỉ vì warning. Nếu vẫn thấy lỗi,
kiểm tra Jenkins đang chạy commit mới nhất của branch shared library.

### ZAP báo spider error trên `/api`

Không dùng `/api` làm `ZAP_TARGET` baseline. Dùng web base URL:

```text
http://<node-external-ip>:30006
```

Web app vẫn dùng `/api/...` để gọi gateway như bình thường.

### Docker build báo thiếu Buildx

Nếu stage `Build & Scan Services` báo:

```text
BuildKit is enabled but the buildx component is missing or broken
```

nghĩa là Jenkins container có Docker CLI nhưng thiếu `docker-buildx-plugin`.
Rebuild Jenkins image từ `Dockerfile.jenkins` mới nhất và recreate container:

```powershell
docker build -t myjenkins-blueocean:lts-jdk21 -f Dockerfile.jenkins .
docker stop jenkins-blueocean
docker rm jenkins-blueocean
```

Sau đó chạy lại container Jenkins với volume `jenkins-data` cũ.

Kiểm tra:

```powershell
docker exec jenkins-blueocean sh -lc "docker buildx version"
```

### Argo CD Health Check báo thiếu `kubectl`

Nếu stage báo:

```text
kubectl is not available on the Jenkins agent
```

rebuild Jenkins image từ `Dockerfile.jenkins` mới nhất và kiểm tra:

```powershell
docker exec jenkins-blueocean sh -lc "kubectl version --client=true"
```

### Argo CD Health Check không tìm thấy credential

Nếu stage báo:

```text
Could not find credentials entry with ID 'jenkins-argocd-kubeconfig'
```

tạo Jenkins credential dạng Secret file, upload `jenkins-argocd-reader.kubeconfig`,
và đặt ID đúng là:

```text
jenkins-argocd-kubeconfig
```

### Grafana chỉ thấy Loki, không thấy Prometheus

Kiểm tra `infra/argocd-apps/loki.yaml` đã tắt Grafana datasource sidecar của
chart Loki:

```yaml
grafana:
  enabled: false
  sidecar:
    datasources:
      enabled: false
```

Sau đó apply lại và restart Grafana:

```powershell
kubectl apply -f infra\argocd-apps\loki.yaml
kubectl apply -f infra\argocd-apps\monitoring.yaml
kubectl rollout restart deployment/monitoring-stack-grafana -n monitoring
kubectl rollout status deployment/monitoring-stack-grafana -n monitoring
```
