# 服务 Docker 化 + 统一编排 — Batch 3 实现计划

> **面向 AI 代理的工作者：** 使用 `subagent-driven-development` 或 `executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 6 个微服务 Docker 化，通过 docker-compose 与现有中间件统一编排到 Linux VM（192.168.150.11），Nginx 作为统一入口。

**架构：** 所有容器共享 bridge 网络，通过容器名互访。Nginx :80 → gateway:8080 → 各微服务。敏感信息通过环境变量注入。

**技术栈：** Docker + docker-compose + Nginx Alpine + Spring Boot Actuator + SkyWalking Java Agent

---

## 文件结构说明

| 文件 | 职责 |
|------|------|
| `{6 services}/src/main/resources/application-docker.yml` | Docker profile：中间件地址改为容器名 |
| `{6 services}/pom.xml` | 添加 actuator 依赖（health check 端点） |
| `{6 services}/Dockerfile` | 增强：SkyWalking agent + HEALTHCHECK + JAVA_OPTS |
| `.dockerignore`（项目根目录 1 个） | 加速构建上下文传输，排除 src/target 无关文件 |
| `docker/nginx.conf` | Nginx 反向代理 gateway + 超时配置 |
| `docker/docker-compose.yml` | 激活 6 应用 + Nginx + 中间件 healthcheck |

**总计：20 个文件变更（6 + 6 + 6 + 1 + 1 + 1 - 注意 .dockerignore 是 1 个而非 6 个）**

---

### 任务 1：添加 Actuator 依赖

**文件：**
- 修改：`gateway/pom.xml`
- 修改：`user-service/pom.xml`
- 修改：`mall-goods-order/pom.xml`
- 修改：`pay-service/pom.xml`
- 修改：`mcp-server/pom.xml`
- 修改：`ai-chat-service/pom.xml`

在 6 个服务模块的 pom.xml 中添加 `spring-boot-starter-actuator` 依赖。版本由 `spring-boot-starter-parent` 统一管理，无需指定 `<version>`。

注意：gateway 使用 WebFlux，actuator 自动适配响应式；其他 5 个服务使用 WebMVC。

- [ ] **步骤 1：在 gateway/pom.xml 添加 actuator 依赖**

找到 gateway 的 `<dependencies>` 块末尾（`spring-boot-starter-test` 之前），插入：

```xml
        <!-- Actuator 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **步骤 2：在 user-service/pom.xml 添加 actuator 依赖**

```xml
        <!-- Actuator 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **步骤 3：在 mall-goods-order/pom.xml 添加 actuator 依赖**

```xml
        <!-- Actuator 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **步骤 4：在 pay-service/pom.xml 添加 actuator 依赖**

```xml
        <!-- Actuator 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **步骤 5：在 mcp-server/pom.xml 添加 actuator 依赖**

```xml
        <!-- Actuator 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **步骤 6：在 ai-chat-service/pom.xml 添加 actuator 依赖**

```xml
        <!-- Actuator 健康检查 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **步骤 7：编译验证**

运行：`mvn compile`
预期：BUILD SUCCESS，6 个服务均无依赖解析错误

- [ ] **步骤 8：Commit**

```bash
git add gateway/pom.xml user-service/pom.xml mall-goods-order/pom.xml pay-service/pom.xml mcp-server/pom.xml ai-chat-service/pom.xml
git commit -m "feat: add spring-boot-starter-actuator to all 6 services"
```

---

### 任务 2：创建 application-docker.yml（共 6 个）

**文件：**
- 创建：`gateway/src/main/resources/application-docker.yml`
- 创建：`user-service/src/main/resources/application-docker.yml`
- 创建：`mall-goods-order/src/main/resources/application-docker.yml`
- 创建：`pay-service/src/main/resources/application-docker.yml`
- 创建：`mcp-server/src/main/resources/application-docker.yml`
- 创建：`ai-chat-service/src/main/resources/application-docker.yml`

Docker profile 的核心差异：中间件地址从 `192.168.150.11` → Docker 容器名，敏感信息通过环境变量注入。

- [ ] **步骤 1：创建 gateway/src/main/resources/application-docker.yml**

gateway 只需要 Nacos + Sentinel，无需数据库/Redis/RocketMQ。

```yaml
# Docker 环境配置 — Nacos/Sentinel 地址改为容器名
# 激活方式: SPRING_PROFILES_ACTIVE=docker

spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        refresh-enabled: true
        fail-fast: false
    sentinel:
      transport:
        dashboard: sentinel:8858
      datasource:
        flow:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade

# JWT 密钥（docker-compose 环境变量注入）
jwt:
  secret: ${JWT_SECRET:}

internal:
  token: ${INTERNAL_TOKEN:}

# Actuator 健康检查端点
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **步骤 2：创建 user-service/src/main/resources/application-docker.yml**

user-service：有数据库，无 Redis/RocketMQ。

```yaml
# Docker 环境配置 — 中间件地址改为容器名
# 激活方式: SPRING_PROFILES_ACTIVE=docker

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/tianji_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        refresh-enabled: true
        fail-fast: false
    sentinel:
      transport:
        dashboard: sentinel:8858
      datasource:
        flow:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade

internal:
  token: ${INTERNAL_TOKEN:}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **步骤 3：创建 mall-goods-order/src/main/resources/application-docker.yml**

mall-goods-order：数据库 + Redis + RocketMQ，全栈。

```yaml
# Docker 环境配置 — 中间件地址改为容器名
# 激活方式: SPRING_PROFILES_ACTIVE=docker

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/tianji_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        refresh-enabled: true
        fail-fast: false
    sentinel:
      transport:
        dashboard: sentinel:8858
      datasource:
        flow:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade
  data:
    redis:
      host: redis
      port: 6379
      password: ${REDIS_PASSWORD:redis123}

rocketmq:
  name-server: rocketmq-namesrv:9876
  producer:
    group: mall-goods-order-producer
    access-key: ${ROCKETMQ_ACCESS_KEY:tianji-mall}
    secret-key: ${ROCKETMQ_SECRET_KEY:tianji-mall-secret}
  consumer:
    access-key: ${ROCKETMQ_ACCESS_KEY:tianji-mall}
    secret-key: ${ROCKETMQ_SECRET_KEY:tianji-mall-secret}
    max-reconsume-times: 3
    delay-level-when-next-consume: 3

file:
  upload-dir: /app/uploads

internal:
  token: ${INTERNAL_TOKEN:}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **步骤 4：创建 pay-service/src/main/resources/application-docker.yml**

```yaml
# Docker 环境配置 — 中间件地址改为容器名
# 激活方式: SPRING_PROFILES_ACTIVE=docker

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/tianji_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        refresh-enabled: true
        fail-fast: false
    sentinel:
      transport:
        dashboard: sentinel:8858
      datasource:
        flow:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade

internal:
  token: ${INTERNAL_TOKEN:}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **步骤 5：创建 mcp-server/src/main/resources/application-docker.yml**

```yaml
# Docker 环境配置 — 中间件地址改为容器名
# 激活方式: SPRING_PROFILES_ACTIVE=docker

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/tianji_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        refresh-enabled: true
        fail-fast: false
    sentinel:
      transport:
        dashboard: sentinel:8858
      datasource:
        flow:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade

internal:
  token: ${INTERNAL_TOKEN:}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **步骤 6：创建 ai-chat-service/src/main/resources/application-docker.yml**

ai-chat-service 额外需要 Milvus 地址变更。

```yaml
# Docker 环境配置 — 中间件地址改为容器名
# 激活方式: SPRING_PROFILES_ACTIVE=docker

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/tianji_mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: nacos:8848
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        refresh-enabled: true
        fail-fast: false
    sentinel:
      transport:
        dashboard: sentinel:8858
      datasource:
        flow:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        degrade:
          nacos:
            server-addr: nacos:8848
            username: ${NACOS_USERNAME:nacos}
            password: ${NACOS_PASSWORD:nacos}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: degrade

milvus:
  host: milvus
  port: 19530

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **步骤 7：编译验证 Docker profile 配置正确解析**

运行：`mvn compile`
预期：BUILD SUCCESS，6 个模块编译通过（application-docker.yml 只在运行时使用，编译阶段仅验证资源打包）

- [ ] **步骤 8：Commit**

```bash
git add gateway/src/main/resources/application-docker.yml \
        user-service/src/main/resources/application-docker.yml \
        mall-goods-order/src/main/resources/application-docker.yml \
        pay-service/src/main/resources/application-docker.yml \
        mcp-server/src/main/resources/application-docker.yml \
        ai-chat-service/src/main/resources/application-docker.yml
git commit -m "feat: add application-docker.yml with container-name addresses for all 6 services"
```

---

### 任务 3：创建 .dockerignore

**文件：**
- 创建：`.dockerignore`（项目根目录，1 个）

Docker build context 设为项目根目录后，`.dockerignore` 排除 src 源码和 target 无关文件，加速构建上下文传输。

- [ ] **步骤 1：创建 .dockerignore**

```dockerignore
# Maven target — 只保留打包好的 jar，排除 sources/javadoc/original
**/target/original-*.jar
**/target/*-sources.jar
**/target/*-javadoc.jar
**/target/classes/
**/target/generated-sources/
**/target/test-classes/
**/target/maven-status/
**/target/maven-archiver/

# 源码和测试（Docker 不需要）
**/src/

# Git
.git/

# IDE
**/*.iml
.idea/

# 日志
**/logs/
```

- [ ] **步骤 2：Commit**

```bash
git add .dockerignore
git commit -m "feat: add .dockerignore to speed up Docker build context"
```

---

### 任务 4：增强 Dockerfile（共 6 个）

**文件：**
- 修改：`gateway/Dockerfile`
- 修改：`user-service/Dockerfile`
- 修改：`mall-goods-order/Dockerfile`
- 修改：`pay-service/Dockerfile`
- 修改：`mcp-server/Dockerfile`
- 修改：`ai-chat-service/Dockerfile`

增强内容：
1. 复制 SkyWalking Java Agent 到镜像
2. 添加 `JAVA_OPTS` 环境变量（`-Xms128m -Xmx256m`）
3. 添加 `HEALTHCHECK` 指令（wget `/actuator/health`）
4. `ENTRYPOINT` 添加 `-javaagent` + SkyWalking 配置
5. `COPY` 路径适配项目根目录 context

**重要：** `COPY target/{module}-*.jar` → `COPY {module}/target/{module}-*.jar`（build context 从模块目录改为项目根目录）

- [ ] **步骤 1：修改 gateway/Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# SkyWalking Java Agent
COPY skywalking/agent /skywalking/agent

# JVM 参数（可被 docker-compose 环境变量覆盖）
ENV JAVA_OPTS="-Xms128m -Xmx256m"

# 应用 jar（context 是项目根目录）
COPY gateway/target/gateway-*.jar app.jar

EXPOSE 8080

# SkyWalking agent + JVM 参数
ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:gateway} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

- [ ] **步骤 2：修改 user-service/Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY user-service/target/user-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:user-service} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
```

- [ ] **步骤 3：修改 mall-goods-order/Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY mall-goods-order/target/mall-goods-order-*.jar app.jar

EXPOSE 8082

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:mall-goods-order} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8082/actuator/health || exit 1
```

- [ ] **步骤 4：修改 pay-service/Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY pay-service/target/pay-service-*.jar app.jar

EXPOSE 8083

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:pay-service} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1
```

- [ ] **步骤 5：修改 mcp-server/Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY mcp-server/target/mcp-server-*.jar app.jar

EXPOSE 8084

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:mcp-server} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8084/actuator/health || exit 1
```

- [ ] **步骤 6：修改 ai-chat-service/Dockerfile**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY skywalking/agent /skywalking/agent

ENV JAVA_OPTS="-Xms128m -Xmx256m"

COPY ai-chat-service/target/ai-chat-service-*.jar app.jar

EXPOSE 8085

ENTRYPOINT java ${JAVA_OPTS} \
  -javaagent:/skywalking/agent/skywalking-agent.jar \
  -Dskywalking.agent.service_name=${SW_SERVICE_NAME:ai-chat-service} \
  -Dskywalking.collector.backend_service=${SW_OAP_ADDR:skywalking-oap:11800} \
  -jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD wget -qO- http://localhost:8085/actuator/health || exit 1
```

- [ ] **步骤 7：编译 + 打包验证**

运行：`mvn package -DskipTests`
预期：BUILD SUCCESS，6 个模块 jar 在各自 target/ 目录生成

- [ ] **步骤 8：Commit**

```bash
git add gateway/Dockerfile user-service/Dockerfile mall-goods-order/Dockerfile \
        pay-service/Dockerfile mcp-server/Dockerfile ai-chat-service/Dockerfile
git commit -m "feat: enhance Dockerfiles with SkyWalking agent, HEALTHCHECK, and JVM opts"
```

---

### 任务 5：Nginx 配置 + docker-compose 编排

**文件：**
- 创建：`docker/nginx.conf`
- 修改：`docker/docker-compose.yml`

- [ ] **步骤 1：创建 docker/nginx.conf**

```nginx
upstream gateway {
    server gateway:8080;
}

server {
    listen 80;

    # 健康检查端点（供 docker-compose healthcheck 使用）
    location /health {
        return 200 'OK';
        add_header Content-Type text/plain;
    }

    # 所有 API 请求反向代理到 gateway
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

- [ ] **步骤 2：修改 docker/docker-compose.yml — 取消 6 个应用服务注释并补全**

将原有的注释服务改为实际配置。

**gateway（替换注释行 176-183）：**
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
      - INTERNAL_TOKEN=${INTERNAL_TOKEN}
    restart: unless-stopped
```

**user-service（替换注释行 187-198）：**
```yaml
  user-service:
    build:
      context: ..
      dockerfile: user-service/Dockerfile
    container_name: user-service
    depends_on:
      nacos:
        condition: service_healthy
      mysql:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SW_SERVICE_NAME=user-service
      - MYSQL_USER=root
      - MYSQL_PASSWORD=${MYSQL_PASSWORD:root}
      - NACOS_USERNAME=${NACOS_USERNAME:nacos}
      - NACOS_PASSWORD=${NACOS_PASSWORD:nacos}
      - INTERNAL_TOKEN=${INTERNAL_TOKEN}
    restart: unless-stopped
```

**mall-goods-order（替换注释行 200-216）：**
```yaml
  mall-goods-order:
    build:
      context: ..
      dockerfile: mall-goods-order/Dockerfile
    container_name: mall-goods-order
    depends_on:
      nacos:
        condition: service_healthy
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      rocketmq-namesrv:
        condition: service_healthy
      rocketmq-broker:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SW_SERVICE_NAME=mall-goods-order
      - MYSQL_USER=root
      - MYSQL_PASSWORD=${MYSQL_PASSWORD:root}
      - REDIS_PASSWORD=${REDIS_PASSWORD:redis123}
      - ROCKETMQ_ACCESS_KEY=${ROCKETMQ_ACCESS_KEY:tianji-mall}
      - ROCKETMQ_SECRET_KEY=${ROCKETMQ_SECRET_KEY:tianji-mall-secret}
      - NACOS_USERNAME=${NACOS_USERNAME:nacos}
      - NACOS_PASSWORD=${NACOS_PASSWORD:nacos}
      - INTERNAL_TOKEN=${INTERNAL_TOKEN}
    restart: unless-stopped
    volumes:
      - uploads-data:/app/uploads
```

**pay-service（替换注释行 218-229）：**
```yaml
  pay-service:
    build:
      context: ..
      dockerfile: pay-service/Dockerfile
    container_name: pay-service
    depends_on:
      nacos:
        condition: service_healthy
      mysql:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SW_SERVICE_NAME=pay-service
      - MYSQL_USER=root
      - MYSQL_PASSWORD=${MYSQL_PASSWORD:root}
      - NACOS_USERNAME=${NACOS_USERNAME:nacos}
      - NACOS_PASSWORD=${NACOS_PASSWORD:nacos}
      - INTERNAL_TOKEN=${INTERNAL_TOKEN}
    restart: unless-stopped
```

**mcp-server（替换注释行 231-242）：**
```yaml
  mcp-server:
    build:
      context: ..
      dockerfile: mcp-server/Dockerfile
    container_name: mcp-server
    depends_on:
      nacos:
        condition: service_healthy
      mysql:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SW_SERVICE_NAME=mcp-server
      - MYSQL_USER=root
      - MYSQL_PASSWORD=${MYSQL_PASSWORD:root}
      - NACOS_USERNAME=${NACOS_USERNAME:nacos}
      - NACOS_PASSWORD=${NACOS_PASSWORD:nacos}
      - INTERNAL_TOKEN=${INTERNAL_TOKEN}
    restart: unless-stopped
```

**ai-chat-service（替换注释行 244-257）：**
```yaml
  ai-chat-service:
    build:
      context: ..
      dockerfile: ai-chat-service/Dockerfile
    container_name: ai-chat-service
    depends_on:
      nacos:
        condition: service_healthy
      mysql:
        condition: service_healthy
      milvus:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SW_SERVICE_NAME=ai-chat-service
      - MYSQL_USER=root
      - MYSQL_PASSWORD=${MYSQL_PASSWORD:root}
      - NACOS_USERNAME=${NACOS_USERNAME:nacos}
      - NACOS_PASSWORD=${NACOS_PASSWORD:nacos}
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - SILICONFLOW_API_KEY=${SILICONFLOW_API_KEY}
    restart: unless-stopped
```

- [ ] **步骤 3：修改 docker/docker-compose.yml — 添加 Nginx 服务**

在 services 块末尾（sentinel 之后）添加：

```yaml
  # ==================== Nginx 反向代理 ====================
  nginx:
    image: nginx:alpine
    container_name: nginx
    depends_on:
      gateway:
        condition: service_healthy
    ports:
      - "8080:80"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:80/health"]
      interval: 30s
      timeout: 5s
      retries: 3
```

- [ ] **步骤 4：修改 docker/docker-compose.yml — 为中间件添加 healthcheck**

**Nacos**（在 environment 块后添加）：
```yaml
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/readiness"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
```

**MySQL**（在 command 块后添加）：
```yaml
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 40s
```

**Redis**（在 command 块后添加）：
```yaml
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "redis123", "ping"]
      interval: 20s
      timeout: 5s
      retries: 5
```

**RocketMQ namesrv**（在 command 块后添加）：
```yaml
    healthcheck:
      test: ["CMD", "sh", "-c", "ss -tlnp | grep 9876"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 30s
```

**RocketMQ broker**（在 command 块后添加）：
```yaml
    healthcheck:
      test: ["CMD", "sh", "-c", "ss -tlnp | grep 10911"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
```

- [ ] **步骤 5：修改 docker/docker-compose.yml — 添加 uploads 数据卷**

在 volumes 块末尾（`milvus-data:` 之后）添加：

```yaml
  uploads-data:
```

- [ ] **步骤 6：Commit**

```bash
git add docker/nginx.conf docker/docker-compose.yml
git commit -m "feat: add Nginx reverse proxy and activate all 6 services in docker-compose"
```

---

### 任务 6：全量验证

- [ ] **步骤 1：全模块编译**

运行：`mvn compile`
预期：BUILD SUCCESS

- [ ] **步骤 2：全模块打包（跳过测试）**

运行：`mvn package -DskipTests`
预期：BUILD SUCCESS，确认 6 个 jar 在各自 target/ 目录

- [ ] **步骤 3：全量测试**

运行：`mvn test`
预期：613 tests PASS，0 failures，BUILD SUCCESS

- [ ] **步骤 4：Commit（如编译/测试期间有修复）**

如有修复直接 amend 到对应 commit；如无变更则跳过。

---

## 自检

1. **规格覆盖度：** 所有设计文档中的需求均被覆盖 — actuator（任务 1）、application-docker.yml（任务 2）、.dockerignore（任务 3）、Dockerfile 增强（任务 4）、Nginx + docker-compose（任务 5）、验证（任务 6）。
2. **占位符扫描：** 所有配置代码均为完整内容，无 TODO/待定。
3. **类型一致性：** Dockerfile 中 `COPY {module}/target/{module}-*.jar` 路径与 build context（项目根目录）一致；环境变量名与 application-docker.yml 中 `${}` 引用一致。

**遗漏项：** 无。

---

## 执行交接

计划已完成并保存。两种执行方式：

1. **子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代
2. **内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
