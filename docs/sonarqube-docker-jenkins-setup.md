# Hướng dẫn cài đặt SonarQube bằng Docker cùng network với Jenkins

Tài liệu này ghi lại cách dựng **SonarQube trên Docker**, kết nối với **Jenkins đang chạy bằng Docker** trên cùng network `jenkins`, và cấu hình để pipeline có thể dùng SonarQube làm **SAST / Quality Gate**.

---

## 1. Mục tiêu

Sau khi làm xong, bạn sẽ có:

- container `sonarqube` chạy bằng Docker
- SonarQube publish ra host tại `http://localhost:9000`
- SonarQube nằm cùng Docker network `jenkins` với Jenkins
- Jenkins cấu hình được SonarQube server tên `sqdocvault`
- Jenkins có credential token để scan
- SonarQube có webhook để `waitForQualityGate` hoạt động

---

## 2. Điều kiện trước khi bắt đầu

Đã có sẵn:

- Docker Desktop chạy ổn
- network Docker tên `jenkins`
- Jenkins container đang chạy, ví dụ:
  - `jenkins-blueocean`
  - `jenkins-docker` (nếu dùng Docker-in-Docker)

### Kiểm tra nhanh

Trong PowerShell:

```powershell
docker network ls
docker ps
```

Nếu chưa có network `jenkins`, tạo nó:

```powershell
docker network create jenkins
```

---

## 3. Tạo volumes cho SonarQube

Tạo 3 volume để dữ liệu không mất khi xóa container:

```powershell
docker volume create sonarqube_data
docker volume create sonarqube_extensions
docker volume create sonarqube_logs
```

Ý nghĩa:

- `sonarqube_data`: dữ liệu SonarQube
- `sonarqube_extensions`: plugins
- `sonarqube_logs`: logs

---

## 4. Chạy SonarQube trên cùng network với Jenkins

### Cách khuyến nghị cho local setup

```powershell
docker run -d --name sonarqube `
  --network jenkins `
  -p 9000:9000 `
  -v sonarqube_data:/opt/sonarqube/data `
  -v sonarqube_extensions:/opt/sonarqube/extensions `
  -v sonarqube_logs:/opt/sonarqube/logs `
  sonarqube:community
```

### Kiểm tra container

```powershell
docker ps
docker logs --tail 100 sonarqube
```

Khi SonarQube khởi động xong, mở trình duyệt:

```text
http://localhost:9000
```

---

## 5. Đăng nhập lần đầu

Thông tin mặc định:

- Username: `admin`
- Password: `admin`

Sau lần đăng nhập đầu tiên, đổi password ngay.

---

## 6. Tạo token cho Jenkins

Trong giao diện SonarQube:

1. Bấm avatar góc phải trên
2. Chọn **My Account**
3. Chọn tab **Security**
4. Tạo token mới, ví dụ tên: `jenkins-sonar-token`
5. Copy token lại ngay

> Token này chỉ hiện 1 lần sau khi tạo.

---

## 7. Tạo credential trong Jenkins

Trong Jenkins:

1. Vào **Manage Jenkins**
2. Chọn **Credentials**
3. Vào **System** → **Global credentials**
4. Bấm **Add Credentials**

Điền như sau:

- **Kind**: `Secret text`
- **Secret**: token vừa tạo từ SonarQube
- **ID**: `sonar-token`
- **Description**: `SonarQube token for DocVault`

---

## 8. Cấu hình SonarQube server trong Jenkins

Vào:

**Manage Jenkins → System → SonarQube servers**

Điền:

- **Name**: `sqdocvault`
- **Server URL**:
  - khuyến nghị khi Jenkins và SonarQube cùng Docker network: `http://sonarqube:9000`
  - fallback an toàn cho local host mapping: `http://host.docker.internal:9000`
- **Server authentication token**: chọn credential `sonar-token`

### Gợi ý chọn URL

- Nếu Jenkins pipeline hoặc scanner chạy được tới hostname `sonarqube` trong Docker network, ưu tiên:
  - `http://sonarqube:9000`
- Nếu scanner chạy qua Docker-in-Docker và gặp lỗi reachability với hostname `sonarqube`, dùng fallback:
  - `http://host.docker.internal:9000`

> Trong local setup có Docker-in-Docker, `host.docker.internal` thường là lựa chọn ít rủi ro hơn cho scanner container.

Bấm **Save** sau khi điền xong.

---

## 9. Tạo webhook từ SonarQube về Jenkins

Webhook cần thiết nếu pipeline dùng `waitForQualityGate`.

Trong SonarQube:

1. Vào **Administration**
2. Chọn **Configuration**
3. Chọn **Webhooks**
4. Bấm **Create**

Điền:

- **Name**: `jenkins-quality-gate`
- **URL**:
  - nếu SonarQube và Jenkins cùng Docker network: `http://jenkins-blueocean:8080/sonarqube-webhook/`

Lưu lại.

---

## 10. Kiểm tra Jenkins có nhìn thấy SonarQube không

### Cách 1: test từ trong Jenkins container

```powershell
docker exec -it jenkins-blueocean sh
```

Bên trong container:

```sh
curl -I http://sonarqube:9000
curl -I http://host.docker.internal:9000
```

Nếu một trong hai lệnh trả về HTTP response, Jenkins đã reach được SonarQube.

### Cách 2: test từ scanner container

Nếu pipeline dùng Docker để chạy scanner, có thể test nhanh như sau:

```powershell
docker exec -it jenkins-blueocean sh -c "docker run --rm curlimages/curl:8.7.1 curl -I http://host.docker.internal:9000"
```

---

## 11. Gợi ý `sonarSast.groovy` cho repo DocVault

Mục tiêu:

- chỉ scan app code
- không scan `infra/**`
- không scan report folders
- không scan `Dockerfile.jenkins`

### File mẫu `vars/sonarSast.groovy`

```groovy
def call(Map cfg = [:]) {
    echo '>>> Running SAST Scan (SonarQube)...'

    String installationName   = cfg.sonarQubeInstallation ?: 'sqdocvault'
    String scannerImage       = cfg.sonarScannerImage ?: 'sonarsource/sonar-scanner-cli:latest'
    String projectKey         = cfg.sonarProjectKey ?: 'docvault'
    String projectName        = cfg.sonarProjectName ?: 'DocVault'
    String projectVersion     = cfg.sonarProjectVersion ?: (env.BUILD_NUMBER ?: 'local')
    String sources            = cfg.sonarSources ?: 'apps,services,libs'
    String exclusions         = cfg.sonarExclusions ?: '**/node_modules/**,**/.pnpm-store/**,**/dist/**,**/.next/**,**/coverage/**,infra/**,checkov-report/**,dependency-check-report/**,Dockerfile.jenkins,**/.scannerwork/**'
    String hostFallback       = cfg.sonarHostUrl ?: 'http://host.docker.internal:9000'
    String extraArgs          = cfg.extraArgs ?: ''

    withSonarQubeEnv(installationName) {
        sh """
            set -eu

            mkdir -p .sonar-cache
            SONAR_HOST="\${SONAR_HOST_URL:-${hostFallback}}"

            docker run --rm \\
                -v "${env.WORKSPACE}:/usr/src" \\
                -v "${env.WORKSPACE}/.sonar-cache:/opt/sonar-scanner/.sonar/cache" \\
                -w /usr/src \\
                -e SONAR_HOST_URL="\${SONAR_HOST}" \\
                -e SONAR_TOKEN="${env.SONAR_AUTH_TOKEN}" \\
                ${scannerImage} \\
                -Dsonar.projectKey="${projectKey}" \\
                -Dsonar.projectName="${projectName}" \\
                -Dsonar.projectVersion="${projectVersion}" \\
                -Dsonar.sources="${sources}" \\
                -Dsonar.exclusions="${exclusions}" \\
                -Dsonar.host.url="\${SONAR_HOST}" \\
                -Dsonar.token="${env.SONAR_AUTH_TOKEN}" \\
                ${extraArgs}
        """
    }
}
```

---

## 12. Ví dụ gọi trong `Jenkinsfile`

### Chỉ scan Sonar

```groovy
stage('SAST - SonarQube') {
    steps {
        sonarSast(
            sonarQubeInstallation: 'sqdocvault',
            sonarScannerImage: 'sonarsource/sonar-scanner-cli:latest',
            sonarProjectKey: 'docvault',
            sonarProjectName: 'DocVault',
            sonarHostUrl: 'http://host.docker.internal:9000'
        )
    }
}
```

### Scan + Quality Gate

```groovy
stage('SAST - SonarQube') {
    steps {
        sonarSast(
            sonarQubeInstallation: 'sqdocvault',
            sonarScannerImage: 'sonarsource/sonar-scanner-cli:latest',
            sonarProjectKey: 'docvault',
            sonarProjectName: 'DocVault',
            sonarHostUrl: 'http://host.docker.internal:9000'
        )
    }
}

stage('Quality Gate') {
    steps {
        timeout(time: 10, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
}
```

---

## 13. Cách dừng, chạy lại, xóa SonarQube

### Dừng container

```powershell
docker stop sonarqube
```

### Chạy lại container đã có

```powershell
docker start sonarqube
```

### Xóa container nhưng giữ dữ liệu

```powershell
docker rm -f sonarqube
```

### Xóa cả dữ liệu SonarQube

> Cẩn thận: lệnh này sẽ mất dữ liệu, logs, plugins.

```powershell
docker rm -f sonarqube
docker volume rm sonarqube_data
docker volume rm sonarqube_extensions
docker volume rm sonarqube_logs
```

---

## 14. Các lỗi thường gặp

### Lỗi 1: Jenkins báo không thấy `sqdocvault`

Nguyên nhân:

- chưa cấu hình SonarQube server trong Jenkins
- tên cấu hình khác với tên pipeline dùng

Cách sửa:

- đảm bảo **Name = `sqdocvault`** đúng 100%

### Lỗi 2: scanner không reach được SonarQube

Nguyên nhân:

- hostname `sonarqube` không resolve được trong runtime scanner
- scanner container không cùng network theo cách bạn tưởng

Cách sửa:

- ưu tiên fallback `http://host.docker.internal:9000`

### Lỗi 3: `waitForQualityGate` treo mãi

Nguyên nhân:

- chưa cấu hình webhook trong SonarQube
- webhook URL sai

Cách sửa:

- tạo webhook trỏ về `http://jenkins-blueocean:8080/sonarqube-webhook/`

### Lỗi 4: SonarQube lên chậm hoặc crash

Cách kiểm tra:

```powershell
docker logs --tail 200 sonarqube
```

---

## 15. Checklist ngắn để cài lại sau này

- [ ] Docker Desktop chạy ổn
- [ ] network `jenkins` tồn tại
- [ ] tạo 3 volumes của SonarQube
- [ ] chạy container `sonarqube`
- [ ] vào `http://localhost:9000`
- [ ] đổi password `admin`
- [ ] tạo token SonarQube
- [ ] thêm credential `sonar-token` trong Jenkins
- [ ] thêm SonarQube server `sqdocvault` trong Jenkins
- [ ] tạo webhook về Jenkins
- [ ] test scan từ pipeline
- [ ] test `waitForQualityGate`

---

## 16. Gợi ý cho repo DocVault

- SonarQube: scan `apps, services, libs`
- Checkov: chỉ scan `infra/k8s`, `infra/argocd-apps`, `infra/terraform`
- Trivy: quét image/container
- Không để SonarQube và Checkov cùng quét một scope quá rộng, sẽ rất nhiễu
