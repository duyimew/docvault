# DocVault Team Setup & Deployment Guide

Mục tiêu của tài liệu này là làm entry point cho thành viên mới clone repo về,
chạy dự án, dùng Jenkins/SonarQube trên Docker, dùng Terraform để tạo AWS EKS,
deploy bằng Argo CD/GitOps và truy cập web app trên EKS qua NodePort.

Tài liệu này gom các bước quan trọng nhất. Khi cần chi tiết hơn, đọc thêm:

- `docs/RUN_PROJECT.md`: chạy local backend/frontend.
- `docs/DEVSECOPS_PIPELINE_SETUP_GUIDE.md`: cấu hình Jenkins pipeline.
- `docs/jenkins_docker.md`: chạy Jenkins bằng Docker.
- `docs/sonarqube-docker-jenkins-setup.md`: chạy SonarQube và nối với Jenkins.
- `docs/docvault_terraform_eks_argocd_plan.md`: tạo EKS và bootstrap Argo CD.
- `docs/docvault_eks_pause_resume_runbook.md`: tắt/mở node EKS để tiết kiệm chi phí.
- `docs/PORT_FORWARD_TESTING.md`: truy cập EKS bằng NodePort sau khi node IP thay đổi.

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

Máy local/dev machine cần có:

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

## 3. Clone và Cài Đặt Dependency

```powershell
git clone https://github.com/daithang59/docvault.git
cd docvault
git checkout devsecops-pipeline
pnpm install
```

Chạy quick verification:

```powershell
pnpm lint
pnpm test
pnpm build
```

Nếu đang clone sau khi PR đã merge, checkout `main` thay cho
`devsecops-pipeline`.

## 4. Chạy Local App

### 4.1. Tạo env file

Từ repo root:

```powershell
Copy-Item .env.example .env
```

Nếu cần chạy service từ thư mục riêng, copy thêm file `.env.example` của service
tương ứng:

```powershell
Copy-Item services\gateway\.env.example services\gateway\.env
Copy-Item services\metadata-service\.env.example services\metadata-service\.env
Copy-Item services\document-service\.env.example services\document-service\.env
Copy-Item services\workflow-service\.env.example services\workflow-service\.env
Copy-Item services\audit-service\.env.example services\audit-service\.env
Copy-Item services\notification-service\.env.example services\notification-service\.env
```

### 4.2. Start local infrastructure

Dùng file example trực tiếp:

```powershell
docker compose -f infra\docker-compose.dev.yml --env-file infra\.env.example up -d
```

Hoặc tạo file env riêng:

```powershell
Copy-Item infra\.env.example infra\.env
docker compose -f infra\docker-compose.dev.yml --env-file infra\.env up -d
```

Stack local gồm Postgres, MongoDB, Mongo Express, MinIO, MinIO init job và
Keycloak.

### 4.3. Database migration và seed

```powershell
pnpm --filter metadata-service prisma:deploy
pnpm --filter metadata-service db:seed
```

Audit service hiện dùng MongoDB cho audit logs. Nếu cần migrate dữ liệu audit cũ:

```powershell
$env:RUN_AUDIT_MIGRATION="true"
```

### 4.4. Start backend và frontend

Backend one-command mode:

```powershell
pnpm start:sequential
```

Frontend:

```powershell
pnpm --filter web dev
```

URL mặc định:

- Web: `http://localhost:3006`
- Gateway Swagger: `http://localhost:3000/docs`
- Keycloak: `http://localhost:8080`
- MinIO Console: `http://localhost:9001`
- Mongo Express: `http://localhost:8081`

Seed users:

- `viewer1`
- `editor1`
- `approver1`
- `co1`
- `admin1`

Mật khẩu mặc định: `Passw0rd!`

Smoke test:

```powershell
pnpm test:e2e
```

## 5. Jenkins và SonarQube Trên Docker

### 5.1. Tạo Docker network

```powershell
docker network create jenkins
```

Nếu network đã tồn tại, Docker sẽ báo lỗi trùng tên; có thể bỏ qua.

### 5.2. Build Jenkins image có Docker CLI

Repo có `Dockerfile.jenkins` dựa trên `jenkins/jenkins:lts-jdk21` và cài Docker
CLI.

```powershell
docker build -t docvault-jenkins:lts-jdk21 -f Dockerfile.jenkins .
```

### 5.3. Start Docker-in-Docker daemon

```powershell
docker run --name jenkins-docker --rm --detach `
  --privileged --network jenkins --network-alias docker `
  --env DOCKER_TLS_CERTDIR=/certs `
  --volume jenkins-docker-certs:/certs/client `
  --volume jenkins-data:/var/jenkins_home `
  --publish 2376:2376 `
  docker:dind
```

### 5.4. Start Jenkins controller

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

### 5.5. Start SonarQube

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

### 5.6. Jenkins configuration cần có

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
- `RUN_ZAP=false`: nên để false cho đến khi gateway target trên EKS reachable.
- `ZAP_TARGET=http://<gateway-url>/api`: chỉ cần khi bật ZAP.

## 6. Terraform AWS EKS

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

## 7. Bootstrap Argo CD và GitOps Apps

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
```

Kiểm tra:

```powershell
kubectl get applications -n argocd
kubectl get pods -n docvault
kubectl get svc -n docvault
```

Argo CD sẽ sync từ branch `gitops-testing`. Jenkins pipeline là nơi cập nhật
image tag/digest trên branch này sau khi build thành công.

## 8. Truy Cập EKS Bằng NodePort

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
```

Nếu cluster có nhiều node, script in ra `All Web URLs`. Dùng bất kỳ URL nào
trong danh sách đó.

Ghi chú: NodePort URL phụ thuộc external IP của node. Vì vậy sau mỗi lần resume
cluster, nên chạy lại `setup-eks-access.ps1` trước khi test login/upload/preview.

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

Chạy local verification:

```powershell
pnpm lint
pnpm test
pnpm build
```

Kiểm tra pipeline:

- Jenkins build xanh.
- SonarQube scan chạy thành công hoặc quality gate được xử lý theo rule của team.
- Docker image đã push lên registry.
- Branch `gitops-testing` đã được Jenkins cập nhật image tag/digest.
- Argo CD applications `Synced` và `Healthy`.
- Smoke test EKS đã pass: login Keycloak, upload file, preview file, download file.

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
