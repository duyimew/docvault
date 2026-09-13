# Chạy Jenkins Bằng Docker Cho Pipeline DevSecOps

Tài liệu này hướng dẫn chạy Jenkins controller bằng Docker theo mô hình dùng
`docker:dind` và custom Jenkins image có Docker CLI. Cách này phù hợp với pipeline
cần `docker build`, `docker scan` và `docker push`.

## 1. Kiến Trúc Container

Mô hình này chạy 2 container:

| Container | Vai trò |
|---|---|
| `jenkins-blueocean` | Jenkins controller, giao diện web, job, credentials, plugins và pipeline |
| `jenkins-docker` | Docker daemon dùng bởi Jenkins để build/push Docker image |

Luồng hoạt động:

```text
Jenkins pipeline
-> Docker CLI trong container jenkins-blueocean
-> Docker daemon trong container jenkins-docker
-> build/push image
```

Lý do cần 2 container: image `jenkins/jenkins` chính thức không kèm Docker CLI
và không chạy Docker daemon bên trong. Vì vậy Jenkins cần một Docker daemon riêng
để thực thi các bước build image.

## 2. Tạo Docker Network

Tạo network riêng để Jenkins controller kết nối tới Docker daemon qua hostname
`docker`.

```bash
docker network create jenkins
```

## 3. Chạy Docker-in-Docker

Container này chạy Docker daemon cho Jenkins. Volume `jenkins-docker-certs` lưu
TLS client certificates, còn `jenkins-data` giữ dữ liệu Jenkins giữa các lần chạy.

```bash
docker run --name jenkins-docker --rm --detach \
  --privileged \
  --network jenkins \
  --network-alias docker \
  --env DOCKER_TLS_CERTDIR=/certs \
  --volume jenkins-docker-certs:/certs/client \
  --volume jenkins-data:/var/jenkins_home \
  --publish 2376:2376 \
  docker:dind --storage-driver overlay2
```

Ghi chú: `--privileged` là yêu cầu thực tế của `docker:dind`. Chỉ dùng mô hình
này cho môi trường local/dev hoặc lab có kiểm soát.

## 4. Tạo Custom Jenkins Image Có Docker CLI

Tạo file `Dockerfile.jenkins` ở root repo:

```dockerfile
FROM jenkins/jenkins:2.555.1-jdk21

USER root

RUN apt-get update && apt-get install -y lsb-release ca-certificates curl && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc && \
    chmod a+r /etc/apt/keyrings/docker.asc && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
    https://download.docker.com/linux/debian \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update && apt-get install -y docker-ce-cli

USER jenkins
```

Build Jenkins image:

```bash
docker build -t myjenkins-blueocean:2.555.1-1 -f Dockerfile.jenkins .
```

## 5. Chạy Jenkins Controller

Container này chạy Jenkins UI tại `http://localhost:8080` và kết nối tới
`jenkins-docker` qua biến `DOCKER_HOST=tcp://docker:2376`.

```bash
docker run --name jenkins-blueocean --restart=on-failure --detach \
  --network jenkins \
  --env DOCKER_HOST=tcp://docker:2376 \
  --env DOCKER_CERT_PATH=/certs/client \
  --env DOCKER_TLS_VERIFY=1 \
  --publish 8080:8080 \
  --publish 50000:50000 \
  --volume jenkins-data:/var/jenkins_home \
  --volume jenkins-docker-certs:/certs/client:ro \
  myjenkins-blueocean:2.555.1-1
```

## 6. Lấy Initial Admin Password

```bash
docker exec jenkins-blueocean cat /var/jenkins_home/secrets/initialAdminPassword
```

Sau đó mở Jenkins:

```text
http://localhost:8080
```

## 7. Kiểm Tra Docker CLI Trong Jenkins

Sau khi vào Jenkins, tạo một Pipeline test hoặc vào container Jenkins để kiểm tra
Docker CLI có kết nối được tới Docker daemon hay không:

```bash
docker exec -it jenkins-blueocean docker version
```

Nếu lệnh trả về cả `Client` và `Server`, Jenkins đã sẵn sàng chạy các bước
`docker build` và `docker push` trong pipeline.

## 8. Dọn Dẹp Khi Cần

Dừng Jenkins controller:

```bash
docker stop jenkins-blueocean
```

Dừng Docker daemon:

```bash
docker stop jenkins-docker
```

Xóa volume chỉ khi muốn mất toàn bộ dữ liệu Jenkins và certificates:

```bash
docker volume rm jenkins-data jenkins-docker-certs
```
