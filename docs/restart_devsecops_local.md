# Hướng dẫn khởi động lại môi trường local DevSecOps

Tài liệu này dùng để khởi động lại môi trường **Jenkins + Docker-in-Docker + SonarQube** local cho pipeline **DocVault** sau khi tắt máy, khởi động lại Docker Desktop, hoặc cần recreate container.

---

## 1. Thành phần hiện tại

### Containers chính

- `jenkins-blueocean`: Jenkins controller.
- `jenkins-docker`: Docker-in-Docker daemon cho Jenkins build/push image.
- `sonarqube`: SonarQube Community server.

### Network dùng chung

- `jenkins`

### Volumes quan trọng cần giữ lại

- `jenkins-data`: lưu Jenkins jobs, credentials, plugins, workspace, pipeline history.
- `jenkins-docker-certs`: lưu TLS certificates để Jenkins kết nối với Docker-in-Docker.
- `sonarqube_data`: lưu dữ liệu SonarQube.
- `sonarqube_extensions`: lưu extensions/plugins của SonarQube.
- `sonarqube_logs`: lưu logs SonarQube.

**Không xóa các volume trên nếu chưa backup.**

---

## 2. Image Jenkins đang dùng

Jenkins controller đang dùng custom image:

```powershell
myjenkins-blueocean:lts-jdk21
```

Image này được build từ file:

```text
Dockerfile.jenkins
```

Build lại image khi cần:

```powershell
docker build --pull -f .\Dockerfile.jenkins -t myjenkins-blueocean:lts-jdk21 .
```

Nếu muốn build sạch hoàn toàn, không dùng cache:

```powershell
docker build --no-cache --pull -f .\Dockerfile.jenkins -t myjenkins-blueocean:lts-jdk21 .
```

Lưu ý: không dùng lệnh sau nếu file Dockerfile của Jenkins đang tên là `Dockerfile.jenkins`:

```powershell
docker build -t myjenkins-blueocean:lts-jdk21 .
```

Vì Docker mặc định sẽ tìm file tên `Dockerfile`, dẫn đến lỗi:

```text
failed to read dockerfile: open Dockerfile: no such file or directory
```

---

## 3. Quy trình khởi động hằng ngày

Sau khi mở máy:

1. Mở **Docker Desktop**.
2. Chờ Docker Engine chạy ổn định.
3. Mở **PowerShell** tại thư mục project nếu cần.
4. Kiểm tra container:

```powershell
docker ps -a
```

Nếu containers vẫn còn tồn tại, chạy:

```powershell
docker start sonarqube jenkins-docker jenkins-blueocean
```

Kiểm tra lại:

```powershell
docker ps
```

Kết quả mong đợi là thấy đủ 3 container đang chạy:

- `sonarqube`
- `jenkins-docker`
- `jenkins-blueocean`

---

## 4. Truy cập UI

Trên trình duyệt của máy host:

```text
Jenkins:   http://localhost:8080
SonarQube: http://localhost:9000
```

Trong Jenkins pipeline hoặc bên trong Jenkins container, SonarQube phải dùng URL:

```text
http://sonarqube:9000
```

Không dùng:

```text
http://localhost:9000
```

Lý do: `localhost` bên trong Jenkins container là chính Jenkins container, không phải container SonarQube.

---

## 5. Recreate containers khi bị mất

Phần này dùng khi containers đã bị xóa, nhưng volumes vẫn còn.

### 5.1. Tạo network nếu chưa có

```powershell
docker network create jenkins
```

Nếu báo network đã tồn tại thì bỏ qua.

### 5.2. Recreate Docker-in-Docker

```powershell
docker run --name jenkins-docker `
  --detach `
  --restart unless-stopped `
  --privileged `
  --network jenkins `
  --network-alias docker `
  --env DOCKER_TLS_CERTDIR=/certs `
  --volume jenkins-docker-certs:/certs/client `
  --volume jenkins-data:/var/jenkins_home `
  --publish 2376:2376 `
  docker:dind `
  --storage-driver overlay2
```

### 5.3. Recreate Jenkins controller

```powershell
docker run --name jenkins-blueocean `
  --detach `
  --restart unless-stopped `
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

### 5.4. Recreate SonarQube

```powershell
docker run --name sonarqube `
  --detach `
  --restart unless-stopped `
  --network jenkins `
  --publish 9000:9000 `
  --volume sonarqube_data:/opt/sonarqube/data `
  --volume sonarqube_extensions:/opt/sonarqube/extensions `
  --volume sonarqube_logs:/opt/sonarqube/logs `
  sonarqube:community
```

---

## 6. Kiểm tra nhanh sau khi khởi động

### 6.1. Kiểm tra container

```powershell
docker ps
```

Cần thấy đủ:

```text
sonarqube
jenkins-docker
jenkins-blueocean
```

### 6.2. Kiểm tra logs

```powershell
docker logs --tail 50 sonarqube
docker logs --tail 50 jenkins-blueocean
docker logs --tail 50 jenkins-docker
```

### 6.3. Kiểm tra Jenkins có Docker CLI và kết nối được Docker daemon

```powershell
docker exec -it jenkins-blueocean sh -c "docker version && echo '---' && env | grep DOCKER"
```

Kết quả đúng cần có:

```text
DOCKER_HOST=tcp://docker:2376
DOCKER_CERT_PATH=/certs/client
DOCKER_TLS_VERIFY=1
```

### 6.4. Kiểm tra Jenkins gọi được SonarQube

```powershell
docker exec -it jenkins-blueocean sh -c "curl -I http://sonarqube:9000 || wget --spider http://sonarqube:9000"
```

Nếu lệnh trên trả về HTTP response hoặc không báo lỗi connection refused thì Jenkins đã nhìn thấy SonarQube qua Docker network.

---

## 7. Lỗi Jenkins plugin yêu cầu version mới hơn

Nếu Jenkins báo lỗi dạng:

```text
Jenkins 2.555 or higher required
Jenkins 2.555.1 or higher required
```

nghĩa là Jenkins core đang cũ hơn plugin đã cài.

Cách xử lý:

### 7.1. Build lại Jenkins image

```powershell
docker build --pull -f .\Dockerfile.jenkins -t myjenkins-blueocean:lts-jdk21 .
```

Nếu nghi ngờ Docker đang dùng cache cũ:

```powershell
docker build --no-cache --pull -f .\Dockerfile.jenkins -t myjenkins-blueocean:lts-jdk21 .
```

### 7.2. Xóa Jenkins container cũ, giữ volume

```powershell
docker rm -f jenkins-blueocean
```

Không xóa volume `jenkins-data`.

### 7.3. Chạy lại Jenkins bằng image mới

```powershell
docker run --name jenkins-blueocean `
  --detach `
  --restart unless-stopped `
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

### 7.4. Kiểm tra Jenkins version

```powershell
docker exec -it jenkins-blueocean sh -c "java -jar /usr/share/jenkins/jenkins.war --version"
```

Nếu version đã đủ mới, vào Jenkins:

```text
http://localhost:8080/manage
```

Sau đó kiểm tra lại phần plugin warning.

---

## 8. Lỗi SonarQube không start được

Nếu SonarQube báo lỗi liên quan database như:

```text
Unsupported database file version or invalid file header
/opt/sonarqube/data/sonar.mv.db
```

thì thường là do volume `sonarqube_data` chứa database H2 cũ không tương thích với image SonarQube hiện tại.

Có 2 hướng xử lý:

### Hướng A: Giữ dữ liệu cũ

Dùng lại đúng version SonarQube cũ nếu biết version trước đây.

Ví dụ:

```powershell
docker rm -f sonarqube

docker run --name sonarqube `
  --detach `
  --restart unless-stopped `
  --network jenkins `
  --publish 9000:9000 `
  --volume sonarqube_data:/opt/sonarqube/data `
  --volume sonarqube_extensions:/opt/sonarqube/extensions `
  --volume sonarqube_logs:/opt/sonarqube/logs `
  sonarqube:<version-cu>
```

### Hướng B: Reset SonarQube

Chỉ dùng nếu chấp nhận mất dữ liệu SonarQube cũ như project, token, rule config.

```powershell
docker rm -f sonarqube
docker volume rm sonarqube_data
docker volume create sonarqube_data
```

Sau đó chạy lại:

```powershell
docker run --name sonarqube `
  --detach `
  --restart unless-stopped `
  --network jenkins `
  --publish 9000:9000 `
  --volume sonarqube_data:/opt/sonarqube/data `
  --volume sonarqube_extensions:/opt/sonarqube/extensions `
  --volume sonarqube_logs:/opt/sonarqube/logs `
  sonarqube:community
```

Sau khi reset SonarQube, cần tạo lại token và cập nhật lại Jenkins credentials nếu pipeline dùng SonarQube token cũ.

---

## 9. Dọn dẹp Docker an toàn

### 9.1. Kiểm tra dung lượng Docker

```powershell
docker system df -v
```

### 9.2. Dọn image, container, build cache không dùng

```powershell
docker system prune -a
docker builder prune -a
```

### 9.3. Cẩn thận với volume prune

Không chạy lệnh sau nếu chưa chắc chắn:

```powershell
docker volume prune
```

Vì lệnh này có thể xóa toàn bộ volume không còn container tham chiếu.

Các volume quan trọng cần giữ:

```text
jenkins-data
jenkins-docker-certs
sonarqube_data
sonarqube_extensions
sonarqube_logs
```

### 9.4. Kiểm tra volume lạ trước khi xóa

Nếu thấy volume lạ chiếm nhiều dung lượng, kiểm tra trước:

```powershell
docker ps -a --filter volume=<TEN_VOLUME>
docker volume inspect <TEN_VOLUME>
```

Nếu không có container nào dùng volume đó thì có thể xóa riêng:

```powershell
docker volume rm <TEN_VOLUME>
```

---

## 10. Checklist mở máy làm việc

- [ ] Mở Docker Desktop.
- [ ] Chờ Docker Engine chạy ổn định.
- [ ] Chạy `docker ps -a`.
- [ ] Chạy `docker start sonarqube jenkins-docker jenkins-blueocean`.
- [ ] Chạy `docker ps` để xác nhận đủ 3 container.
- [ ] Vào Jenkins: `http://localhost:8080`.
- [ ] Vào SonarQube: `http://localhost:9000`.
- [ ] Kiểm tra Jenkins pipeline dùng SonarQube URL: `http://sonarqube:9000`.
- [ ] Chạy lại pipeline DocVault.

---

## 11. Lệnh nhanh

### Khởi động lại môi trường

```powershell
docker start sonarqube jenkins-docker jenkins-blueocean
docker ps
```

### Kiểm tra Docker trong Jenkins

```powershell
docker exec -it jenkins-blueocean sh -c "docker version && echo '---' && env | grep DOCKER"
```

### Kiểm tra Jenkins gọi SonarQube

```powershell
docker exec -it jenkins-blueocean sh -c "curl -I http://sonarqube:9000 || wget --spider http://sonarqube:9000"
```

### Kiểm tra logs nhanh

```powershell
docker logs --tail 30 sonarqube
docker logs --tail 30 jenkins-blueocean
docker logs --tail 30 jenkins-docker
```

---

## 12. Ghi chú quan trọng

- Không dùng `--rm` cho `jenkins-docker` nếu muốn container tồn tại sau reboot.
- Không xóa `jenkins-data` nếu chưa backup.
- Không xóa `sonarqube_data` nếu muốn giữ dữ liệu SonarQube.
- Không dùng `localhost:9000` trong Jenkins pipeline để gọi SonarQube.
- Nên dùng `http://sonarqube:9000` trong pipeline vì Jenkins và SonarQube nằm cùng Docker network `jenkins`.
- Jenkins hiện dùng Java 21 thông qua image `jenkins/jenkins:lts-jdk21`.
- Nếu Jenkins plugin yêu cầu version mới hơn, build lại `myjenkins-blueocean:lts-jdk21` từ `Dockerfile.jenkins`.
