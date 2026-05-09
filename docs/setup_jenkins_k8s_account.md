# Thiết Lập Kubernetes Account Riêng Cho Jenkins

Mục tiêu: Jenkins có thể chạy stage `Argo CD Health Check` mà không cần dùng kubeconfig admin.

Account này chỉ có quyền đọc Argo CD `Application` trong namespace `argocd`.

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

Đã bổ sung `kubectl` vào image Jenkins.

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

Trước hết rebuild Jenkins image để image có `kubectl`:

```powershell
docker build -t docvault-jenkins:lts-jdk21 -f Dockerfile.jenkins .
```

Sau đó restart Jenkins container bằng image mới theo cách bạn đang chạy Jenkins.

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
