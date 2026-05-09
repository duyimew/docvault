Cách chuẩn là dùng **GitHub Webhook -> Jenkins Pipeline job**.

Với setup hiện tại của bạn, làm như sau:

1. **Jenkins job phải trỏ đúng branch**
   
Trong Jenkins job `docvault`, kiểm tra:

```text
Pipeline script from SCM
Repository URL: https://github.com/daithang59/docvault.git
Branch Specifier: */devsecops-pipeline
Script Path: Jenkinsfile
```

Nếu bạn muốn commit vào `main` cũng tự chạy thì đổi branch specifier thành `*/main`, hoặc dùng Multibranch Pipeline.

2. **Bật trigger trong Jenkins job**

Vào job `docvault` → `Configure` → phần **Build Triggers**:

Tick:

```text
GitHub hook trigger for GITScm polling
```

Lưu lại.

3. **Tạo webhook trong GitHub**

Vào GitHub repo → `Settings` → `Webhooks` → `Add webhook`.

Điền:

```text
Payload URL: http://<jenkins-public-url>/github-webhook/
Content type: application/json
```

Chọn event:

```text
Just the push event
```

Hoặc nếu muốn PR cũng trigger:

```text
Let me select individual events
- Pushes
- Pull requests
```

4. **Jenkins phải public reachable**

GitHub phải gọi được Jenkins. Nếu Jenkins đang chạy local/private như:

```text
http://localhost:8080
http://192.168.x.x:8080
```

thì GitHub webhook sẽ không gọi được.

Bạn cần một trong các cách:

```text
Option A: Deploy Jenkins lên server có public IP/domain
Option B: Dùng ngrok/cloudflared expose Jenkins tạm thời
Option C: Dùng GitHub Actions gọi Jenkins API
```

Ví dụ dùng ngrok:

```powershell
ngrok http 8080
```

Sau đó GitHub webhook dùng URL kiểu:

```text
https://xxxx.ngrok-free.app/github-webhook/
```

5. **Tránh Jenkins tự chạy vì GitOps commit**

Pipeline của bạn đã có `preventLoop()` và GitOps commit có `[skip ci]`, tốt rồi. Nhưng vẫn nên đảm bảo job chỉ build branch source:

```text
*/devsecops-pipeline
```

Không nên để cùng job trigger cả `gitops-testing`, vì Jenkins push GitOps xong có thể tự kích hoạt lại.

Tóm lại: với job hiện tại, thường bạn chỉ cần bật `GitHub hook trigger for GITScm polling`, thêm webhook `http://<jenkins-url>/github-webhook/`, và đảm bảo Jenkins có public URL.