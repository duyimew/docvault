# DocVault DevSecOps Pipeline Setup Guide

Tài liệu này hướng dẫn cấu hình môi trường để chạy pipeline DevSecOps có sẵn trong repo DocVault. Điểm xuất phát giả định: bạn đã clone repo, mở bằng VS Code, chạy được local infra bằng Docker, nhưng chưa cấu hình Jenkins/SonarQube/GitOps.

## 1. Kiểm Tra Git Remote Trước Khi Push

Kiểm tra trạng thái hiện tại:

```bash
git status --short --branch
git remote -v
git branch -vv
```

Trạng thái hiện tại của repo này nên là:

- `origin`: `https://github.com/daithang59/docvault.git` - repo bạn push code lên.
- `upstream`: `https://github.com/duyimew/docvault.git` - repo fork dùng để tham khảo/fetch thay đổi nếu cần.
- Branch làm pipeline: `devsecops-pipeline`.

Nếu remote chưa đúng, cấu hình lại:

```bash
git remote set-url origin https://github.com/daithang59/docvault.git
git remote add upstream https://github.com/duyimew/docvault.git
```

Nếu `upstream` đã tồn tại, dùng:

```bash
git remote set-url upstream https://github.com/duyimew/docvault.git
```

Push branch pipeline lên repo của bạn:

```bash
git push -u origin HEAD:devsecops-pipeline
```

Không push trực tiếp vào `upstream`; giữ remote đó để fetch/cherry-pick thay đổi từ repo của `duyimew` khi cần.

## 2. Pipeline Hiện Có Trong Repo

Pipeline chính nằm ở:

- `Jenkinsfile`: điều phối các stage.
- `vars/*.groovy`: shared-library steps Jenkins đang gọi.
- `Dockerfile.backend`: build image cho các NestJS services.
- `apps/web/Dockerfile`: build image frontend.
- `infra/k8s/charts/docvault-service`: Helm chart generic.
- `infra/k8s/values/*.yaml`: values theo service.
- `infra/argocd-apps/*.yaml`: ArgoCD Application manifests.

Luồng mục tiêu:

```text
Git push -> Jenkins -> install/test/scan -> Docker build -> Trivy image scan
-> Docker Hub push -> update Helm values on GitOps branch -> ArgoCD sync
```

## 3. Chuẩn Bị GitHub

Bạn cần:

1. Một repo GitHub bạn có quyền push.
2. Branch code hiện tại: `devsecops-pipeline`.
3. Branch GitOps: `gitops-testing`.
4. Personal Access Token có quyền push repo.

Tạo branch GitOps từ branch hiện tại:

```bash
git checkout devsecops-pipeline
git checkout -b gitops-testing
git push -u origin gitops-testing
git checkout devsecops-pipeline
```

Pipeline sẽ update các file `infra/k8s/values/*.yaml` trên branch `gitops-testing` bằng commit có `[skip ci]`.

## 4. Chạy Jenkins Bằng Docker

Khuyến nghị tạo Jenkins container có Docker CLI và quyền dùng Docker socket.

Tạo thư mục local:

```bash
mkdir -p .jenkins
```

Chạy Jenkins:

```bash
docker run -d \
  --name docvault-jenkins \
  -p 8088:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/.jenkins/dependency-check-data:/home/abby/jenkins_workspace/dependency-check-data" \
  jenkins/jenkins:lts
```

Nếu Jenkins container không có Docker CLI, tạo image riêng:

```Dockerfile
FROM jenkins/jenkins:lts
USER root
RUN apt-get update && apt-get install -y docker.io git curl && rm -rf /var/lib/apt/lists/*
USER jenkins
```

Build và chạy:

```bash
docker build -t docvault-jenkins:lts .
docker run -d \
  --name docvault-jenkins \
  -p 8088:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/.jenkins/dependency-check-data:/home/abby/jenkins_workspace/dependency-check-data" \
  docvault-jenkins:lts
```

Lấy initial password:

```bash
docker exec docvault-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Mở Jenkins: `http://localhost:8088`.

## 5. Cài Jenkins Plugins

Cài tối thiểu:

- Pipeline
- Git
- GitHub Branch Source
- Credentials Binding
- Docker Pipeline
- Workspace Cleanup
- SonarQube Scanner for Jenkins
- Timestamper, nếu muốn log dễ đọc hơn

Restart Jenkins sau khi cài plugin.

## 6. Cấu Hình Jenkins Agent Label

`Jenkinsfile` hiện dùng:

```groovy
agent { label 'docker-agent-alpine-ubuntu-vm' }
```

Bạn có 2 lựa chọn:

1. Gắn label `docker-agent-alpine-ubuntu-vm` cho built-in node trong Jenkins.
2. Hoặc sửa `Jenkinsfile` sang label bạn đang dùng, ví dụ `agent any`.

Với setup cá nhân, cách nhanh nhất là vào:

```text
Manage Jenkins -> Nodes -> Built-In Node -> Configure -> Labels
```

Thêm:

```text
docker-agent-alpine-ubuntu-vm
```

Node này phải chạy được:

```bash
docker --version
git --version
```

## 7. Cấu Hình Jenkins Credentials

Pipeline yêu cầu đúng credential IDs sau:

| ID | Loại | Dùng cho |
|---|---|---|
| `dockerhub-credentials` | Username with password | `docker login`, push images |
| `github-credentials` | Username with password/token | push GitOps commit |

Tạo trong:

```text
Manage Jenkins -> Credentials -> System -> Global credentials
```

Với GitHub, password nên là Personal Access Token, không dùng mật khẩu tài khoản.

## 8. Cấu Hình SonarQube

Pipeline dùng `withSonarQubeEnv(cfg.sonarQubeInstallation)` và config hiện đặt tên installation là `sqdocvault`.

Chạy SonarQube local:

```bash
docker run -d \
  --name docvault-sonarqube \
  -p 9000:9000 \
  sonarqube:community
```

Mở `http://localhost:9000`, tạo token, rồi cấu hình trong Jenkins:

```text
Manage Jenkins -> System -> SonarQube servers
Name: sqdocvault
Server URL: http://host.docker.internal:9000
Token: <sonar-token>
```

Nếu Jenkins chạy trên Linux VM, thay `host.docker.internal` bằng IP thật của máy chạy SonarQube.

## 9. Cập Nhật Pipeline Config

File chính:

```text
vars/docvaultConfig.groovy
```

Kiểm tra và sửa các giá trị sau cho đúng môi trường của bạn:

```groovy
dockerOrg: '<dockerhub-user>'
gitOpsRepoUrl: 'https://github.com/daithang59/docvault.git'
gitOpsBranch: 'gitops-testing'
sonarHostUrl: 'http://host.docker.internal:9000'
zapTarget: ''
```

Trong repo hiện tại `dockerOrg` đã đặt là `daithang59`. Nếu Docker Hub username của bạn khác GitHub username, đổi `dockerOrg` trong `vars/docvaultConfig.groovy` và `repository` trong `infra/k8s/values/*.yaml` sang namespace Docker Hub thật.

Khi chua deploy len Kubernetes, giu `RUN_ZAP=false`. Sau khi Gateway co URL that va Jenkins/ZAP container curl duoc URL do, dien pipeline parameter `ZAP_TARGET=http://<gateway-url>/api` va bat `RUN_ZAP=true`.

## 10. Tạo Jenkins Shared Library

`Jenkinsfile` có dòng:

```groovy
@Library('docvault@devsecops-pipeline') _
```

Vì vậy Jenkins phải biết shared library tên `docvault`.

Sau khi PR đã merge vào `testing`, có thể đổi lại `@Library('docvault@testing') _` và cấu hình Jenkins chạy branch `testing`.

Vào:

```text
Manage Jenkins -> System -> Global Trusted Pipeline Libraries
```

Cấu hình:

```text
Name: docvault
Default version: devsecops-pipeline
Retrieval method: Modern SCM
SCM: Git
Project Repository: https://github.com/daithang59/docvault.git
Credentials: github-credentials
```

Jenkins sẽ đọc các step trong thư mục `vars/`.

## 11. Tạo Pipeline Job

Tạo job:

```text
New Item -> Pipeline -> docvault-devsecops
```

Chọn:

```text
Pipeline script from SCM
SCM: Git
Repository URL: https://github.com/daithang59/docvault.git
Credentials: github-credentials
Branch Specifier: */devsecops-pipeline
Script Path: Jenkinsfile
```

Sau khi merge PR vào branch chính dùng cho CI, đổi `Branch Specifier` sang branch đó, ví dụ `*/testing`.

Pipeline có parameters:

- `FORCE_BUILD_ALL`: bật `true` cho lần chạy đầu tiên.
- `GITOPS_BRANCH`: dùng `gitops-testing`.

## 12. Chạy Pipeline Theo Từng Lớp

Lần đầu chạy:

```text
FORCE_BUILD_ALL=true
GITOPS_BRANCH=gitops-testing
```

Mục tiêu kiểm tra theo thứ tự:

1. `Checkout & Initialize Config`
2. `System Check`
3. `Install`
4. `Unit Tests`
5. `Trivy FS Scan`
6. `SAST - SonarQube`
7. `IaC - Checkov Scan`
8. `Build & Scan Services`
9. `Push & GitOps`

Nếu chưa có cluster/Gateway deployed, ZAP có thể fail. Khi đó xử lý sau, không xem đó là blocker của CI cơ bản.

## 13. Chuẩn Bị Kubernetes Và ArgoCD

Bạn cần một cluster local hoặc VM, ví dụ Docker Desktop Kubernetes, kind, minikube hoặc k3s.

Cài ArgoCD:

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Apply ArgoCD app manifests:

```bash
kubectl apply -f infra/argocd-apps/docvault-infra.yaml
kubectl apply -f infra/argocd-apps/docvault-apps.yaml
```

Nếu muốn monitoring:

```bash
kubectl apply -f infra/argocd-apps/monitoring.yaml
```

Kiểm tra:

```bash
kubectl get applications -n argocd
kubectl get pods
kubectl get svc
```

## 14. Sau Khi Jenkins Update GitOps Branch

Kiểm tra branch `gitops-testing`:

```bash
git fetch origin gitops-testing
git checkout gitops-testing
git log -3 --oneline
git diff HEAD~1 -- infra/k8s/values
```

Bạn nên thấy commit dạng:

```text
chore(gitops): update image refs for ... [skip ci]
```

Và values đổi:

```yaml
image:
  tag: "v<BUILD_NUMBER>"
  digest: "sha256:..."
```

Nếu digest rỗng, chart vẫn deploy bằng tag. Digest thật tốt hơn cho reproducibility.

## 15. Troubleshooting Nhanh

### Push Git lỗi

Kiểm tra remote:

```bash
git remote -v
```

Nếu `origin` không phải repo bạn sở hữu:

```bash
git remote set-url origin https://github.com/daithang59/docvault.git
git push -u origin HEAD:devsecops-pipeline
```

### Jenkins không tìm thấy shared library

Kiểm tra `@Library('docvault@devsecops-pipeline')` khi chạy trước merge, hoặc `@Library('docvault@testing')` sau khi đã merge. Global Trusted Pipeline Library phải có tên `docvault`.

### Jenkins không có Docker

Vào console Jenkins agent chạy:

```bash
docker --version
```

Nếu không có, dùng Jenkins image có Docker CLI và mount `/var/run/docker.sock`.

### SonarQube fail

Kiểm tra installation name phải là:

```text
sqdocvault
```

Hoặc sửa `sonarQubeInstallation` trong `vars/docvaultConfig.groovy`.

### GitOps branch không tồn tại

Tạo branch:

```bash
git checkout -b gitops-testing
git push -u origin gitops-testing
```

### ZAP fail

ZAP can gateway API that su reachable tu Jenkins/ZAP container. Neu chua deploy cluster, giu `RUN_ZAP=false`. Khi da co target, dien `ZAP_TARGET=http://<gateway-url>/api`.

## 16. Thứ Tự Làm Khuyến Nghị

1. Sửa Git remote và push được branch `devsecops-pipeline`.
2. Tạo `gitops-testing`.
3. Deploy Jenkins bằng Docker.
4. Cài plugins và credentials.
5. Cấu hình shared library `docvault`.
6. Tạo pipeline job từ SCM.
7. Chạy `FORCE_BUILD_ALL=true`.
8. Fix lỗi install/test/build/scan nếu có.
9. Kiểm tra Jenkins update được GitOps branch.
10. Sau đó mới cấu hình ArgoCD sync và ZAP/monitoring.
