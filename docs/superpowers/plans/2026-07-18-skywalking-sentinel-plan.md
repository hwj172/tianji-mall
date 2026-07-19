# SkyWalking + Sentinel 接入实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 VM Docker 上的 SkyWalking OAP 9.7.0 + Sentinel Dashboard 1.8.6 接入 6 个微服务（零业务逻辑改动，纯依赖/配置变更）。

**架构：** SkyWalking — javaagent 字节码增强，零 Maven 依赖；Sentinel — SCA Starter，Dashboard 动态规则。

**技术栈：** SkyWalking Java Agent 9.7.0, Sentinel 1.8.6 (SCA BOM 2023.0.1.0 管理)

---

### 任务 1： Agent 基础设施

**文件：**
- 修改：`.gitignore`
- 创建：`skywalking/README.md`

- [ ] **步骤 1：.gitignore 添加 skywalking 排除**

在 `.gitignore` 末尾添加：

```gitignore
# SkyWalking
skywalking/
```

- [ ] **步骤 2：创建 skywalking/README.md**

```markdown
# SkyWalking Java Agent

## 下载 Agent

```bash
curl -L https://archive.apache.org/dist/skywalking/9.7.0/apache-skywalking-java-agent-9.7.0.tgz -o /tmp/sw.tgz
mkdir -p skywalking/agent
tar xzf /tmp/sw.tgz -C skywalking/
mv skywalking/skywalking-agent skywalking/agent
```

## IDEA VM Options 模板

每个服务的 Run Configuration → Modify options → Add VM options：

```
-javaagent:D:\TEST\tianji-mall\skywalking\agent\skywalking-agent.jar
-Dskywalking.agent.service_name={service-name}
-Dskywalking.collector.backend_service=192.168.150.11:11800
```

| 服务 | service_name |
|------|-------------|
| gateway | gateway |
| user-service | user-service |
| mall-goods-order | mall-goods-order |
| pay-service | pay-service |
| mcp-server | mcp-server |
| ai-chat-service | ai-chat-service |

service_name 必须与 spring.application.name 一致。

## 验证

```bash
java -jar skywalking/agent/skywalking-agent.jar 2>&1 | head -3
# 预期输出：SkyWalking Agent 版本信息
```

启动服务并发送请求后，访问 http://192.168.150.11:8090 查看链路。
```

- [ ] **步骤 3：Commit**

```bash
git add .gitignore skywalking/README.md
git commit -m "feat: add SkyWalking agent infrastructure (gitignore + docs)"
```

---

### 任务 2：父 POM Sentinel 版本管理

**文件：**
- 修改：`pom.xml`（父）

- [ ] **步骤 1：dependencyManagement 添加 Sentinel 声明**

在父 POM 的 `<dependencyManagement>` 中，RocketMQ 覆盖之后（约第 138 行 `</dependency>` 之后）添加：

```xml
            <!-- Sentinel 流量管控（版本由 SCA BOM 管理，此处仅集中声明便于发现） -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
            </dependency>
```

不写 `<version>`——SCA BOM 2023.0.1.0 已管理版本（sentinel-core 1.8.6）。

- [ ] **步骤 2：验证编译**

```bash
mvn validate
# 期望：BUILD SUCCESS，POM 解析无错误
```

- [ ] **步骤 3：Commit**

```bash
git add pom.xml
git commit -m "feat: add Sentinel version declarations to parent POM dependencyManagement"
```

---

### 任务 3：Gateway Sentinel 接入

**文件：**
- 修改：`gateway/pom.xml`
- 修改：`gateway/src/main/resources/application.yml`
- 修改：`gateway/src/test/resources/application-test.yml`

- [ ] **步骤 1：gateway/pom.xml 添加依赖**

在 Nacos config 依赖之后（约第 53 行 `</dependency>` 之后）添加：

```xml
        <!-- Sentinel 网关流控（WebFlux 适配） -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
        </dependency>
```

Gateway 使用 `spring-cloud-alibaba-sentinel-gateway`（WebFlux/SCG 适配），**不是** `spring-cloud-starter-alibaba-sentinel`（那个会带 MVC 适配器，与 WebFlux 冲突）。

- [ ] **步骤 2：gateway application.yml 添加配置**

在 `spring.cloud.nacos.config` 块之后、`spring.cloud.gateway` 之前添加：

```yaml
    sentinel:
      transport:
        dashboard: 192.168.150.11:8858
      eager: true
```

- [ ] **步骤 3：gateway application-test.yml 禁用 Sentinel**

在 `application-test.yml` 的 `spring` 块末尾添加：

```yaml
  cloud:
    sentinel:
      enabled: false
```

- [ ] **步骤 4：编译验证**

```bash
mvn compile -pl gateway
# 期望：BUILD SUCCESS
```

- [ ] **步骤 5：运行 Gateway 测试**

```bash
mvn test -pl gateway
# 期望：30 tests pass（AuthGlobalFilterTest 26 + CorsConfigTest 4）
```

- [ ] **步骤 6：Commit**

```bash
git add gateway/pom.xml gateway/src/main/resources/application.yml gateway/src/test/resources/application-test.yml
git commit -m "feat: integrate Sentinel into gateway module"
```

---

### 任务 4：5 个业务模块 Sentinel 接入

所有业务模块使用 `spring-cloud-starter-alibaba-sentinel`（MVC 适配）。每个模块的修改模式完全相同。

**文件：**
- 修改：`user-service/pom.xml`、`user-service/src/main/resources/application.yml`、`user-service/src/test/resources/application-test.yml`
- 修改：`mall-goods-order/pom.xml`、`mall-goods-order/src/main/resources/application.yml`、`mall-goods-order/src/test/resources/application-test.yml`
- 修改：`pay-service/pom.xml`、`pay-service/src/main/resources/application.yml`、`pay-service/src/test/resources/application-test.yml`
- 修改：`mcp-server/pom.xml`、`mcp-server/src/main/resources/application.yml`、`mcp-server/src/test/resources/application-test.yml`
- 修改：`ai-chat-service/pom.xml`、`ai-chat-service/src/main/resources/application.yml`
- 不需要：`ai-chat-service/src/test/resources/application-test.yml`（无 Spring Context 测试）

- [ ] **步骤 1：user-service**

pom.xml 在 Nacos config 依赖之后添加：
```xml
        <!-- Sentinel 流量管控 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
        </dependency>
```

application.yml 在 `spring.cloud.nacos` 块后添加：
```yaml
    sentinel:
      transport:
        dashboard: 192.168.150.11:8858
      eager: true
```

application-test.yml 在 `spring` 块末尾添加：
```yaml
  cloud:
    sentinel:
      enabled: false
```

- [ ] **步骤 2：mall-goods-order**

同步骤 1 模式，pom.xml 添加 sentinel 依赖；application.yml 添加 sentinel 配置；application-test.yml 添加 `sentinel.enabled: false`。

- [ ] **步骤 3：pay-service**

同步骤 1 模式。

- [ ] **步骤 4：mcp-server**

同步骤 1 模式。

- [ ] **步骤 5：ai-chat-service**

pom.xml 添加 sentinel 依赖（同步骤 1）。application.yml 添加 sentinel 配置（同步骤 1）。

**跳过 application-test.yml**——ai-chat-service 3 个测试全是 `@ExtendWith(MockitoExtension.class)`，不加载 Spring Context，无 Sentinel 自动配置风险。

- [ ] **步骤 6：全模块编译**

```bash
mvn compile
# 期望：全部 7 模块 BUILD SUCCESS
```

- [ ] **步骤 7：全模块测试**

```bash
mvn test
# 期望：全部 178 tests pass，零失败
```

验证 Sentinel 在测试中确实被禁用——输出中不应出现 `SentinelWebInterceptor` 或 `SentinelAutoConfiguration` 相关日志。

- [ ] **步骤 8：Commit**

```bash
git add user-service/pom.xml user-service/src/main/resources/application.yml user-service/src/test/resources/application-test.yml
git add mall-goods-order/pom.xml mall-goods-order/src/main/resources/application.yml mall-goods-order/src/test/resources/application-test.yml
git add pay-service/pom.xml pay-service/src/main/resources/application.yml pay-service/src/test/resources/application-test.yml
git add mcp-server/pom.xml mcp-server/src/main/resources/application.yml mcp-server/src/test/resources/application-test.yml
git add ai-chat-service/pom.xml ai-chat-service/src/main/resources/application.yml
git commit -m "feat: integrate Sentinel into 5 business service modules"
```

---

### 任务 5：CLAUDE.md 文档同步

**文件：**
- 修改：`CLAUDE.md`

- [ ] **步骤 1：添加监控组件章节**

在"部署拓扑"表格之后、"常用命令"之前插入：

```markdown
## 监控组件

| 组件 | 端口 | 控制台 | 凭证 |
|------|------|--------|------|
| SkyWalking OAP | 11800 (gRPC) / 12800 (HTTP) | — | 无 |
| SkyWalking UI | 8090 | http://192.168.150.11:8090 | 无 |
| Sentinel Dashboard | 8858 | http://192.168.150.11:8858 | sentinel / sentinel |

### SkyWalking（链路追踪）

零代码侵入：javaagent 挂载，不需要 Maven 依赖。Agent 下载和配置见 `skywalking/README.md`。

IDEA VM Options（每个服务 Run Configuration）：
```
-javaagent:D:\TEST\tianji-mall\skywalking\agent\skywalking-agent.jar
-Dskywalking.agent.service_name={service-name}
-Dskywalking.collector.backend_service=192.168.150.11:11800
```

### Sentinel（流量管控）

依赖：gateway 使用 `spring-cloud-alibaba-sentinel-gateway`，其他 5 个服务使用 `spring-cloud-starter-alibaba-sentinel`，版本由 SCA BOM 管理。

application.yml 配置：
```
spring.cloud.sentinel.transport.dashboard: 192.168.150.11:8858
spring.cloud.sentinel.eager: true
```

规则全部通过 Dashboard 动态配置，不在代码中预设。
```

- [ ] **步骤 2：更新子模块依赖速查表**

gateway 行追加 `**spring-cloud-alibaba-sentinel-gateway**`。

user-service、mall-goods-order、pay-service、mcp-server、ai-chat-service 行各自追加 `**Sentinel**`。

- [ ] **步骤 3：更新测试约定**

在 `application-test.yml` 模板要点末尾添加：
```markdown
- `spring.cloud.sentinel.enabled: false`（禁止 Sentinel 自动配置加载）
```

- [ ] **步骤 4：更新关键约定**

在"关键约定"章节末尾添加：
```markdown
- **Sentinel**：Gateway 专用 `spring-cloud-alibaba-sentinel-gateway`（WebFlux 适配），业务模块用 `spring-cloud-starter-alibaba-sentinel`（MVC 适配）。版本由 SCA BOM 管理，子模块不写 `<version>`。测试中 `spring.cloud.sentinel.enabled: false`
```

- [ ] **步骤 5：更新测试总数**

确认 `mvn test` 后测试总数不变（仍为 178），如实际数字有变化则更新 CLAUDE.md 中测试总数声明。

- [ ] **步骤 6：Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add SkyWalking and Sentinel monitoring docs to CLAUDE.md"
```

---

### 任务 6：启动验证（手动）

> 此任务为手动验证，不涉及代码变更。

- [ ] **步骤 1：启动任一服务验证 Sentinel Dashboard 注册**

启动 mall-goods-order（或任一已加 sentinel 的服务），观察日志中：
```
INFO ... SentinelWebInterceptor ... [Sentinel Starter] register SentinelWebInterceptor ...
```

访问 http://192.168.150.11:8858，确认服务出现在"服务列表"中。

- [ ] **步骤 2：下载 SkyWalking Agent 并验证**

```bash
mkdir -p skywalking/agent
curl -L https://archive.apache.org/dist/skywalking/9.7.0/apache-skywalking-java-agent-9.7.0.tgz -o /tmp/sw.tgz
tar xzf /tmp/sw.tgz -C skywalking/
mv skywalking/skywalking-agent skywalking/agent
java -jar skywalking/agent/skywalking-agent.jar 2>&1 | head -3
```

- [ ] **步骤 3：添加 VM options 启动服务并验证链路**

为任一服务 IDEA Run Configuration 添加 VM options（参考 skywalking/README.md），启动后发送几个请求，访问 http://192.168.150.11:8090 确认拓扑图和调用链可见。

---

## 自检

**1. 规格覆盖度：**
- ✅ SkyWalking Agent 部署 → 任务 1（README + .gitignore）
- ✅ SkyWalking VM Options → 任务 1（README 中模板）+ 任务 5（CLAUDE.md）
- ✅ Sentinel 依赖分类 → 任务 2（父 POM）+ 任务 3（gateway）+ 任务 4（业务模块）
- ✅ Sentinel 配置 → 任务 3/4（application.yml）
- ✅ Sentinel 测试禁用 → 任务 3/4（application-test.yml）
- ✅ 规则配置（Dashboard 动态）→ 任务 5（CLAUDE.md 中说明）
- ✅ CLAUDE.md 更新 → 任务 5
- ✅ 编译/测试验证 → 任务 3 步骤 4-5 + 任务 4 步骤 6-7
- ✅ 运行时验证 → 任务 6

**2. 占位符扫描：** 无 TODO/TBD/占位符。

**3. 类型一致性：** 所有模块使用相同配置格式，无命名冲突。
