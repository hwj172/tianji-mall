# SkyWalking + Sentinel 接入设计文档

> **目标：** 将现有 VM Docker 中间件（SkyWalking OAP 9.7.0 + Sentinel Dashboard 1.8.6）接入 6 个微服务，实现链路追踪和流量管控。

**架构：** SkyWalking 通过 javaagent 字节码增强（零代码依赖），Sentinel 通过 SCA Starter 拦截 Controller 请求（Dashboard 动态规则）。

**技术栈：** SkyWalking Java Agent 9.7.0, Sentinel 1.8.6 (SCA BOM 2023.0.1.0 管理), Spring Cloud Gateway + WebFlux 适配

---

## 一、SkyWalking 接入

纯 javaagent 方式，零 Maven 依赖，零代码变更。

### 1.1 Agent 部署

```
skywalking/agent/          # gitignored，不提交
├── skywalking-agent.jar    # 核心探针
├── config/agent.config     # 默认配置（无需修改，通过 JVM 参数覆盖）
└── plugins/                # 插件 jar
```

下载命令（一次性）：
```bash
curl -L https://archive.apache.org/dist/skywalking/9.7.0/apache-skywalking-java-agent-9.7.0.tgz -o /tmp/sw.tgz
tar xzf /tmp/sw.tgz -C skywalking/
mv skywalking/skywalking-agent skywalking/agent
```

### 1.2 IDEA VM Options 模板

每个服务的 Run Configuration → Modify options → Add VM options：

```
-javaagent:D:\TEST\tianji-mall\skywalking\agent\skywalking-agent.jar
-Dskywalking.agent.service_name={service-name}
-Dskywalking.collector.backend_service=192.168.150.11:11800
```

| 服务 | `service_name` |
|------|---------------|
| gateway | gateway |
| user-service | user-service |
| mall-goods-order | mall-goods-order |
| pay-service | pay-service |
| mcp-server | mcp-server |
| ai-chat-service | ai-chat-service |

`service_name` 必须与 `spring.application.name` 一致。

### 1.3 上报端点

- Agent → OAP gRPC: `192.168.150.11:11800`
- UI 查询: `http://192.168.150.11:8090`

---

## 二、Sentinel 接入

通过 Spring Cloud Alibaba Sentinel Starter 接入，Dashboard 动态配置规则。

### 2.1 依赖分类

| 模块 | 依赖 | 原因 |
|------|------|------|
| `gateway` | `spring-cloud-alibaba-sentinel-gateway` | WebFlux/SCG 适配 |
| user-service, mall-goods-order, pay-service, mcp-server, ai-chat-service | `spring-cloud-starter-alibaba-sentinel` | MVC 适配，拦截 @RestController |
| tianji-common | **不加** | 纯 jar 库，无 Spring Boot 运行时 |

版本由 SCA BOM 2023.0.1.0 统一管理（sentinel-core 1.8.6），所有子模块不写 `<version>`。

父 POM dependencyManagement 集中声明（可选，便于发现）：
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
```

### 2.2 application.yml 配置（6 模块统一）

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: 192.168.150.11:8858
      eager: true
```

`eager: true` 确保启动即注册到 Dashboard，不等首次请求。

### 2.3 测试禁用

每个 `application-test.yml` 添加：
```yaml
spring:
  cloud:
    sentinel:
      enabled: false
```

Sentinel 所有自动配置类均使用 `@ConditionalOnProperty(name = "spring.cloud.sentinel.enabled", matchIfMissing = true)`，设 `false` 即可完全禁用，无需额外 exclude。

**例外**：ai-chat-service 无 `application-test.yml`（3 个测试全是 `@ExtendWith(MockitoExtension.class)`，不加载 Spring Context），无需处理。

### 2.4 规则配置

所有流控/熔断/降级规则通过 Sentinel Dashboard UI 动态配置，不在代码中预设。

Dashboard: `http://192.168.150.11:8858`（sentinel / sentinel）

---

## 三、受影响文件总览

| 文件 | 操作 | 内容 |
|------|------|------|
| `.gitignore` | 修改 | 添加 `skywalking/` |
| `pom.xml`（父） | 修改 | dependencyManagement 添加 2 个 sentinel 声明 |
| `gateway/pom.xml` | 修改 | 添加 `spring-cloud-alibaba-sentinel-gateway` |
| `gateway/src/main/resources/application.yml` | 修改 | 添加 `spring.cloud.sentinel` 配置 |
| `gateway/src/test/resources/application-test.yml` | 修改 | 添加 `spring.cloud.sentinel.enabled: false` |
| `user-service/pom.xml` | 修改 | 添加 `spring-cloud-starter-alibaba-sentinel` |
| `user-service/src/main/resources/application.yml` | 修改 | 添加 `spring.cloud.sentinel` 配置 |
| `user-service/src/test/resources/application-test.yml` | 修改 | 添加 `spring.cloud.sentinel.enabled: false` |
| `mall-goods-order/pom.xml` | 修改 | 添加 `spring-cloud-starter-alibaba-sentinel` |
| `mall-goods-order/src/main/resources/application.yml` | 修改 | 添加 `spring.cloud.sentinel` 配置 |
| `mall-goods-order/src/test/resources/application-test.yml` | 修改 | 添加 `spring.cloud.sentinel.enabled: false` |
| `pay-service/pom.xml` | 修改 | 添加 `spring-cloud-starter-alibaba-sentinel` |
| `pay-service/src/main/resources/application.yml` | 修改 | 添加 `spring.cloud.sentinel` 配置 |
| `pay-service/src/test/resources/application-test.yml` | 修改 | 添加 `spring.cloud.sentinel.enabled: false` |
| `mcp-server/pom.xml` | 修改 | 添加 `spring-cloud-starter-alibaba-sentinel` |
| `mcp-server/src/main/resources/application.yml` | 修改 | 添加 `spring.cloud.sentinel` 配置 |
| `mcp-server/src/test/resources/application-test.yml` | 修改 | 添加 `spring.cloud.sentinel.enabled: false` |
| `ai-chat-service/pom.xml` | 修改 | 添加 `spring-cloud-starter-alibaba-sentinel` |
| `ai-chat-service/src/main/resources/application.yml` | 修改 | 添加 `spring.cloud.sentinel` 配置 |
| `CLAUDE.md` | 修改 | 添加监控组件章节 + 依赖速查更新 |

**ai-chat-service 无需修改 test resources**（无 Spring Context 测试）。

---

## 四、验证计划

### 4.1 编译验证
```bash
mvn compile
```
期望：全模块编译通过，无依赖冲突。

### 4.2 测试验证
```bash
mvn test
```
期望：全部 178 个测试通过，Sentinel 禁用生效。

### 4.3 运行时验证

**Sentinel**：启动任一服务，日志中出现：
```
INFO ... SentinelWebInterceptor ... [Sentinel Starter] register SentinelWebInterceptor ...
```
Dashboard `http://192.168.150.11:8858` 服务列表中出现该服务。

**SkyWalking**：添加 VM options 启动服务，发送请求后访问 `http://192.168.150.11:8090`，拓扑图和服务调用链可见。
