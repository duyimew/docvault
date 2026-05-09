# Thiết Lập Kubernetes Account Riêng Cho Jenkins

Mục tiêu: Jenkins có thể chạy stage `Argo CD Health Check` mà không cần dùng kubeconfig admin.

Account này chỉ có quyền đọc Argo CD `Application` trong namespace `argocd`.

Trạng thái hiện tại: đã apply ServiceAccount/RBAC vào cluster `docvault-eks`, đã tạo kubeconfig `jenkins-argocd-reader.kubeconfig`, đã upload vào Jenkins dưới credential ID `jenkins-argocd-kubeconfig`, và pipeline build `#61` đã pass `Argo CD Health Check`.

## 1. Thành Phần Đã Thêm

Manifest RBAC:

```text
infra/k8s/ci/jenkins-argocd-reader.yaml
```

Nội dung quyền:

```text
namespace: argocd
serviceAccount: jenkins-argocd-reader
resources: argoproj.io/applications
verbs: get, list, watch
tokenSecret: jenkins-argocd-reader-token
```

Script tạo kubeconfig:

```text
scripts/create-jenkins-argocd-kubeconfig.ps1
```

Jenkins image:

```text
Dockerfile.jenkins
```

Đã bổ sung `kubectl` và `docker buildx` vào image Jenkins.

## 2. Tạo Kubeconfig Cho Jenkins

Chạy từ máy đang có quyền admin vào EKS:

```powershell
.\scripts\create-jenkins-argocd-kubeconfig.ps1
```

Script sẽ:

- Apply RBAC manifest.
- Tạo ServiceAccount token Secret `jenkins-argocd-reader-token`.
- Sinh file kubeconfig:

```text
jenkins-argocd-reader.kubeconfig
```

File này đã được ignore bởi `.gitignore`. Không commit file kubeconfig.

## 3. Đưa Kubeconfig Vào Jenkins

Trước hết rebuild Jenkins image để image có `kubectl` và `docker buildx`:

```powershell
docker build -t myjenkins-blueocean:lts-jdk21 -f Dockerfile.jenkins .
```

Sau đó restart Jenkins container bằng image mới, giữ nguyên volume Jenkins:

```powershell
docker stop jenkins-blueocean
docker rm jenkins-blueocean
docker run --name jenkins-blueocean `
  --restart=unless-stopped `
  --detach `
  --network jenkins `
  --env DOCKER_HOST=tcp://docker:2376 `
  --env DOCKER_CERT_PATH=/certs/client `
  --env DOCKER_TLS_VERIFY=1 `
  --publish 8080:8080 `
  --publish 50000:50000 `
  --volume jenkins-data:/var/jenkins_home `
  --volume jenkins-docker-certs:/certs/client:ro `
  myjenkins-blueocean:lts-jdk21
```

Kiểm tra:

```powershell
docker exec jenkins-blueocean sh -lc "kubectl version --client=true && docker buildx version"
```

Cách khuyến nghị: Jenkins credential dạng Secret file.

1. Vào Jenkins UI.
2. Manage Jenkins.
3. Credentials.
4. Add Credentials.
5. Kind: `Secret file`.
6. Upload file:

```text
jenkins-argocd-reader.kubeconfig
```

7. Đặt credential ID, ví dụ:

```text
jenkins-argocd-kubeconfig
```

Sau đó pipeline có thể bind file này vào biến `KUBECONFIG`.

Pipeline đã có parameter:

```text
KUBECONFIG_CREDENTIAL_ID
```

Điền giá trị này bằng credential ID vừa tạo, ví dụ:

```text
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
```

## 4. Cách Test Thủ Công

Trên máy local:

```powershell
kubectl --kubeconfig .\jenkins-argocd-reader.kubeconfig get applications -n argocd
```

Kết quả kỳ vọng:

```text
NAME                         SYNC STATUS   HEALTH STATUS
docvault-gateway             Synced        Healthy
docvault-web                 Synced        Healthy
...
```

Account này không nên có quyền ghi, xóa hoặc sync Argo CD app.

Lưu ý: health gate mặc định chỉ check các app service chính. `docvault-infra-deps` có thể `OutOfSync` do hook/secret/runtime drift nên không nên để nó chặn pipeline app nếu chưa xử lý riêng.

## 5. Bật Lại Stage Trong Jenkins

Sau khi Jenkins image có `kubectl` và Jenkins đã có kubeconfig credential, chạy pipeline với:

```text
RUN_ARGO_HEALTH_CHECK=true
ARGOCD_NAMESPACE=argocd
KUBECONFIG_CREDENTIAL_ID=jenkins-argocd-kubeconfig
```

Nếu Jenkins chưa được mount/bind kubeconfig thì giữ:

```text
RUN_ARGO_HEALTH_CHECK=false
```
