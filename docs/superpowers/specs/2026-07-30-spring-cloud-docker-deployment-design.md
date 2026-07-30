# Spring Cloud 服务 Docker 化 — Batch 3 设计文档

> 状态：设计中 | 2026-07-30

## 目标

将 6 个微服务 Docker 化，通过 docker-compose 与现有中间件统一编排到 Linux VM（192.168.150.11），实现一键启动。

## 约束

- 单 Linux VM（7.6 GB，可扩容至 10-12 GB），单实例部署
- 中间件已有 docker-compose 编排（docker/docker-compose.yml）
- 6 个 Dockerfile 已存在，需增强
- SkyWalking agent 已下载到 `skywalking/agent/skywalking-agent.jar`（~21 MB）
- 敏感信息通过 `${ENV_VAR}` 模式注入（延续 Batch 1/2 做法）

## 架构

```
┌─────────────────────────────────────────────────┐
│  Linux VM (192.168.150.11)                      │
│                                                  │
│  docker-compose (单文件，应用+中间件)              │
│                                                  │
│  ┌─────────┐                                     │
│  │  nginx  │ :80 → gateway:8080                  │
│  └────┬────┘                                     │
│       │                                           │
│  ┌────▼────┐  Feign   ┌──────────────┐           │
│  │ gateway │─────────►│ user-service │           │
│  │  :8080  │          │    :8081     │           │
│  └────┬────┘          └──────────────┘           │
│       │                                           │
│  ┌────▼──────────┐  Feign   ┌──────────┐        │
│  │mall-goods-order│◄────────│pay-service│        │
│  │    :8082       │         │  :8083   │        │
│  └────┬───────────┘         └──────────┘        │
│       │                                           │
│  ┌────▼──────┐  Feign   ┌──────────────┐        │
│  │ mcp-server│◄────────│ai-chat-service│        │
│  │  :8084    │         │    :8085      │        │
│  └───────────┘         └──────────────┘        │
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │  中间件（已有）                            │   │
│  │  Nacos :8848  MySQL :3306  Redis :6379   │   │
│  │  RocketMQ :9876  Milvus :19530           │   │
│  │  SkyWalking :11800  Sentinel :8858       │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

- 所有容器共享 bridge 网络，通过容器名互访
- Nginx 对外暴露 8080 端口，管理端口（8848/8858/8090）按需暴露
- 中间件网络地址：`192.168.150.11` → 容器名（`mysql`、`nacos`、`redis`）

---

## 设计细节

### 1. 各服务新增/修改文件

#### 1.1 `application-docker.yml`（每服务 1 个，共 6 个）

Docker 专用 profile，核心差异是中间件地址从固定 IP 改为容器名：

```yaml
# {module}/src/main/resources/application-docker.yml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/tianji_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
      config:
        server-addr: nacos:8848
    sentinel:
      transport:
        dashboard: sentinel:8858
  data:
    redis:
      host: redis
      password: ${REDIS_PASSWORD:redis123}

rocketmq:
  name-server: rocketmq-namesrv:9876

# SkyWalking 通过 javaagent 挂载，这里配 service_name
# 在 Dockerfile ENTRYPOINT 中通过环境变量注入
```

各服务的差异化部分：
- **gateway**：无 DB/Redis/RocketMQ，只需 Nacos + Sentinel
- **user-service / pay-service / mcp-server**：有 DB，无 Redis/RocketMQ
- **mall-goods-order**：全都有（DB + Redis + RocketMQ）
- **ai-chat-service**：有 DB，额外需要 `milvus.host: milvus`

#### 1.2 `Dockerfile` 增强（每服务 1 个，共 6 个）

当前 Dockerfile 是最简形式，需要增强：

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# SkyWalking Java Agent
COPY ../../skywalking/agent /skywalking/agent

# JVM 参数（可被 docker-compose 环境变量覆盖）
ENV JAVA_OPTS="-Xms128m -Xmx256m"

# 应用 jar
COPY target/{module}-*.jar app.jar

EXPOSE {port}

# SkyWalking agent 挂载 + JVM 参数
ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:{service-name}} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:{port}/actuator/health || exit 1
```

**SkyWalking agent：** docker-compose `build.context` 设为项目根目录（`..`），Dockerfile 中 `COPY skywalking/agent /skywalking/agent` 直接从根目录复制。构建时只需 `mvn package -pl {module}` 生成 jar，不需要跨模块复制文件。

#### 1.3 `.dockerignore`（每模块 1 个，共 6 个）

```
target/original-*.jar
target/*-sources.jar
target/*-javadoc.jar
src/
.git/
*.iml
logs/
```

#### 1.4 `pom.xml` — 添加 Actuator（每服务模块，共 6 个）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

父 POM `dependencyManagement` 由 `spring-boot-starter-parent` 管理版本，不需额外指定。

### 2. docker-compose.yml 变更

#### 2.1 Nginx 服务（新增）

```yaml
nginx:
  image: nginx:alpine
  container_name: nginx
  depends_on:
    - gateway
  ports:
    - "8080:80"
  volumes:
    - ./nginx.conf:/etc/nginx/conf.d/default.conf
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "wget", "-qO-", "http://localhost:80/health"]
    interval: 30s
    retries: 3
```

#### 2.2 应用服务（取消注释 + 补全，共 6 个）

模式一致，以 gateway 为例：

```yaml
gateway:
  build:
    context: ..
    dockerfile: gateway/Dockerfile
  container_name: gateway
  depends_on:
    nacos:
      condition: service_healthy
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - SW_SERVICE_NAME=gateway
    - JWT_SECRET=${JWT_SECRET}
    - NACOS_USERNAME=${NACOS_USERNAME:nacos}
    - NACOS_PASSWORD=${NACOS_PASSWORD:nacos}
  restart: unless-stopped
```

mall-goods-order 额外环境变量：
```yaml
    - REDIS_PASSWORD=${REDIS_PASSWORD:redis123}
    - ROCKETMQ_ACCESS_KEY=${ROCKETMQ_ACCESS_KEY:tianji-mall}
    - ROCKETMQ_SECRET_KEY=${ROCKETMQ_SECRET_KEY:tianji-mall-secret}
```

#### 2.3 中间件健康检查补充

为 MySQL、Redis、Nacos 添加 `healthcheck`（供 `depends_on: condition: service_healthy` 使用）：
- **MySQL**：`CMD mysqladmin ping -h localhost`
- **Redis**：`CMD redis-cli ping`
- **Nacos**：`CMD curl -f http://localhost:8848/nacos/v1/console/health/readiness`

### 3. nginx.conf（新增 `docker/nginx.conf`）

```nginx
upstream gateway {
    server gateway:8080;
}

server {
    listen 80;

    # 健康检查端点
    location /health {
        return 200 'OK';
        add_header Content-Type text/plain;
    }

    # API 路由
    location / {
        proxy_pass http://gateway;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时配置
        proxy_connect_timeout 3s;
        proxy_read_timeout 30s;
    }
}
```

### 4. actuator 健康检查配置

`application-docker.yml` 中暴露 health 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

---

## 变更汇总

| 操作 | 文件 | 说明 |
|------|------|------|
| 新增 | `{6 services}/application-docker.yml` | Docker profile 配置 |
| 修改 | `{6 services}/Dockerfile` | 添加 SkyWalking agent + HEALTHCHECK + JAVA_OPTS |
| 新增 | `{6 services}/.dockerignore` | 排除不必要文件 |
| 修改 | `{6 services}/pom.xml` | 添加 actuator 依赖 |
| 新增 | `docker/nginx.conf` | Nginx 反向代理配置 |
| 修改 | `docker/docker-compose.yml` | 激活 6 个应用服务 + Nginx + 中间件健康检查 |

**总计：28 个文件变更（6 新增 + 12 新增 + 6 修改 + 4 新增 + 6 修改 + 1 新增 + 1 修改）**

细化：6 application-docker.yml（新）+ 6 .dockerignore（新）+ 6 Dockerfile（改）+ 6 pom.xml（改）+ 1 nginx.conf（新）+ 1 docker-compose.yml（改）= **26 文件**

### 不涉及的文件

- `tianji-common`：无启动类，不部署为独立容器
- 业务代码（Controller/Service/Mapper）：仅配置层变更
- 测试代码：不受影响

---

## 验证

1. `mvn compile` BUILD SUCCESS
2. `mvn test` 全部 613 tests PASS（actuator 不影响测试，测试用 H2 + exclude）
3. 手动验证（需 Docker 环境）：
   - `docker compose up -d` 所有容器正常启动
   - `docker ps` 显示所有容器 healthy
   - `curl http://192.168.150.11:8080/api/home` 正常返回
4. SkyWalking UI `http://192.168.150.11:8090` 显示 6 个服务拓扑

---

## 与 Batch 1/2 的关系

- Batch 1（韧性）：Feign 超时/断路器、Gateway httpclient、RocketMQ 重试 → **在 Docker profile 中保留**
- Batch 2（安全）：Nacos/Redis/Sentinel/RocketMQ 认证、CORS → **通过环境变量在 Docker 环境生效，默认值与 docker-compose 一致**
