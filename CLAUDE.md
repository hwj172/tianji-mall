# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

天机商城（tianji-mall）— 基于 Spring Cloud Alibaba 的仿淘宝智能电商平台。

- **Java 17 + Spring Boot 3.2.5 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.1.0**
- **Maven 多模块**：1 个父 POM 聚合 7 个子模块
- **数据库**：MySQL 8.0 + MyBatis-Plus 3.5.7 + Druid 1.2.23
- **注册配置**：Nacos
- **消息队列**：RocketMQ（rocketmq-spring-boot-starter 2.3.1）
- **链路监控**：SkyWalking + Sentinel Dashboard
- **向量数据库**：Milvus
- **AI 模型**：DeepSeek（OpenAI 兼容 API）
- **前端**：Vue3（独立项目）
- 完整需求文档见 `remand.md`

## 部署拓扑

| 环境 | 组件 |
|------|------|
| **Windows 物理机** | IDEA 开发环境（6 个微服务）、natapp 内网穿透（→ localhost:8080）、前端项目 |
| **Linux VM**（192.168.150.11） | Docker 中间件：Nacos、MySQL、Redis、RocketMQ、Milvus、SkyWalking、Sentinel |

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

依赖：gateway 使用 `spring-cloud-alibaba-sentinel-gateway`（WebFlux 适配），其他 5 个服务使用 `spring-cloud-starter-alibaba-sentinel`（MVC 适配）。版本由父 POM dependencyManagement 管理（2023.0.1.0）。

application.yml 配置：
```yaml
spring.cloud.sentinel.transport.dashboard: 192.168.150.11:8858
spring.cloud.sentinel.eager: true
```

规则全部通过 Sentinel Dashboard 动态配置，不在代码中预设。Dashboard: http://192.168.150.11:8858（sentinel / sentinel）。

## 模块架构

```
tianji-mall (父 POM)
├── tianji-common          # 公共模块（jar，无启动类）
├── gateway                # API 网关 — 8080
├── user-service           # 用户服务 — 8081
├── mall-goods-order       # 商城核心（商品+购物车+订单+地址）— 8082
├── pay-service            # 支付宝沙盒支付 — 8083
├── mcp-server             # 工具网关（REST API，非 MCP 协议）— 8084
└── ai-chat-service        # AI 智能导购（DeepSeek + 工具调用）— 8085
```

**模块间调用**：Nacos 注册发现 + OpenFeign。ai-chat-service 通过 Feign 调用 mcp-server 的 REST 工具端点，mcp-server 通过 Feign 调用 mall-goods-order 内部端点（`X-Internal-Token` 请求头鉴权）。

## 常用命令

```bash
mvn compile          # 全模块编译
mvn test             # 全模块测试
mvn package          # 全模块打包

# 单模块编译/测试
mvn compile -pl user-service
mvn test -pl mall-goods-order

# 从指定模块恢复构建（依赖已下载完成时）
mvn compile -rf :pay-service

# 打包跳过测试
mvn package -DskipTests
```

## 子模块依赖速查

| 模块 | 关键依赖 |
|------|----------|
| tianji-common | Lombok, Jackson, Jakarta Validation, jjwt 0.12.6 — **无 spring-boot-maven-plugin** |
| gateway | spring-cloud-starter-gateway, Nacos, LoadBalancer, jjwt, **spring-cloud-alibaba-sentinel-gateway** — **不是 spring-boot-starter-web**, **test:** spring-boot-starter-test + reactor-test |
| user-service | spring-boot-starter-web, MyBatis-Plus, MySQL, Druid, Nacos, jjwt 0.12.6, **Sentinel**, **test:** spring-boot-starter-test + H2 |
| mall-goods-order | 同 user-service + OpenFeign + **spring-boot-starter-data-redis + Redisson 3.32.0 + commons-pool2**（Redis 缓存 + 分布式锁）+ **RocketMQ**（订单事件异步消息）+ **Sentinel**, **test:** spring-boot-starter-test + H2 |
| pay-service | 同 user-service + OpenFeign + LoadBalancer + 支付宝 SDK（需手动安装到本地仓库，见父 POM 注释）+ **Sentinel**, **test:** spring-boot-starter-test + H2 |
| mcp-server | spring-boot-starter-web, MyBatis-Plus, MySQL, Druid, Nacos, OpenFeign + LoadBalancer, **Sentinel**, **test:** spring-boot-starter-test + H2 |
| ai-chat-service | spring-boot-starter-web, MyBatis-Plus, MySQL, Druid, Nacos, OpenFeign, LoadBalancer, jjwt + DeepSeek API（RestTemplate）+ milvus-sdk-java 2.3.4 + SiliconFlow Embedding（RestTemplate）+ **Sentinel**, **test:** spring-boot-starter-test |

## 测试约定

**当前测试总数：178 (Gateway 30 + Controller 42 + Service 集成 26 + Service 单元 80)，8 个模块全覆盖。**

### 测试分层

| 层级 | 注解 | 说明 |
|------|------|------|
| **Controller** | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@MockBean` | 加载完整 Context（H2），Mock Service 层，验证路由/JWT/@Valid/异常处理 |
| **Service 集成** | `@SpringBootTest` + `@ActiveProfiles("test")` | 加载完整 Context（H2），测试真实 MyBatis-Plus 查询（getOne/LambdaUpdateWrapper/分页/事务） |
| **Service 单元** | `@ExtendWith(MockitoExtension.class)` | 纯 Mockito，Mock Mapper/Service，验证业务逻辑分支 |
| **Gateway 单元** | `@ExtendWith(MockitoExtension.class)` | 纯 Mockito，Mock `ServerWebExchange`/`ServerHttpRequest`/`ServerHttpResponse`，测试 WebFlux GlobalFilter |

### 测试基础设施

每个需要测试的模块必须提供：

```
{module}/src/test/
├── resources/
│   ├── application-test.yml    # H2 数据源 + 禁用 Nacos/Druid/Feign + jwt.secret
│   └── schema.sql              # CREATE TABLE IF NOT EXISTS（H2 MODE=MySQL）
└── java/com/tianji/{module}/   # 测试类
```

**application-test.yml 模板要点：**
- `spring.datasource.url`: `jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=true;DB_CLOSE_DELAY=-1`
- 排除 Nacos、Druid、Feign 自动配置（防止加载外部依赖）
- `spring.sql.init.mode: always`（首次初始化 schema）
- pay-service 额外需要 `alipay.*` fake 值（AlipayConfig `@Value` 注入）
- `spring.cloud.sentinel.enabled: false`（禁用 Sentinel 自动配置，避免测试 Context 启动时连接 Dashboard）
- **gateway 测试**：无需 H2/schema.sql（无数据库），`application-test.yml` 禁用 Nacos 即可。使用 Mockito 模拟 WebFlux 组件（`ServerWebExchange`、`ServerHttpRequest`），借助 jjwt 生成测试 Token

**schema.sql 要点：**
- 全部使用 `CREATE TABLE IF NOT EXISTS`（`DB_CLOSE_DELAY=-1` 跨 Context 复用）
- H2 保留字（`user`、`order`）用反引号括起来，MyBatis-Plus 实体对应 `@TableName("\`xxx\`")`

### @WebMvcTest 不可用

由于所有 `@SpringBootApplication` 类均有 `@ComponentScan("com.tianji")`（扫描全部 tianji 包），`@WebMvcTest` 无法隔离单个 Controller。Controller 测试统一使用 `@SpringBootTest` + `@AutoConfigureMockMvc`。

### 主要 Controller 测试清单

| 模块 | 测试类 | Tests | 覆盖 |
|------|--------|-------|------|
| user-service | UserControllerTest | 5 | register/login/info + @Valid + 缺 Auth |
| mall-goods-order | ProductControllerTest | 5 | 公开端点（无需 JWT） + BizException + 内部回填端点 |
| mall-goods-order | CartControllerTest | 9 | CRUD + @Valid + 内部端点 + 缺 Auth |
| mall-goods-order | AddressControllerTest | 5 | CRUD + 缺 Auth |
| mall-goods-order | OrderControllerTest | 9 | create/list/detail/cancel + @Valid + 内部端点 + 缺 Auth |
| pay-service | PayControllerTest | 5 | create/notify/query + 回调异常 + 缺 Auth |
| mcp-server | ToolControllerTest | 4 | JWT 手动提取 + 工具路由 + 未知工具 + 未授权 |
| gateway | AuthGlobalFilterTest | 26 | 公开路径/内部路径/JWT 鉴权/非 API 路径/边界 + Mock WebFlux |
| gateway | CorsConfigTest | 4 | CORS 过滤器 Bean 创建 + 预检/GET/无 Origin |

## 关键约定

- `tianji-common` 是纯 jar 库，不要在它的 pom.xml 中加 spring-boot-maven-plugin
- Gateway 使用 WebFlux（spring-cloud-starter-gateway），**不能**引入 spring-boot-starter-web
- 所有业务服务继承父 POM 的依赖版本，不在子模块中写 `<version>`
- Nacos 地址统一填虚拟机静态 IP（当前：192.168.150.11:8848），不能用 127.0.0.1
- application.yml 中 `spring.application.name` 必须与 `pom.xml` 的 `artifactId` 一致
- 配置文件分离为 `application.yml`（通用，可提交）+ `application-local.yml`（密钥，gitignore）
- 各模块提供 `application-local.yml.example` 模板文件供其他开发者参考
- **JWT 鉴权**：`JwtUtil` 集中在 `tianji-common`，所有业务模块共享。jwt.secret 无默认值，未配置时启动报错
- **内部端点**：`/api/order/internal`、`/api/cart/internal`、`/api/product/internal` 通过 `X-Internal-Token` 请求头鉴权（非 JWT），网关中内部路径检查先于公开前缀匹配（`/api/product/internal` 是公开前缀 `/api/product` 的子路径）
- **库存扣减**：使用 `UPDATE ... WHERE stock >= #{qty}` 原子操作，禁止 Java 侧读-改-写
- **分布式锁**：`OrderService.createOrder` 使用 Redisson `getMultiLock`（锁 key 排序防死锁，waitTime=3s / leaseTime=10s），锁内重新读取库存。`cancelOrder` 恢复库存无并发竞争，不加锁
- **商品缓存**：`ProductService.getProductById` / `getProductPage` 使用 `@Cacheable`，`deductStock` / `restoreStock` 使用 `@CacheEvict`。序列化器 `GenericJackson2JsonRedisSerializer`，TTL 30min
- **RocketMQ**：`OrderService` 在 createOrder/cancelOrder/payOrder 后发送 `order-topic` 消息（Tag: CREATED/PAID/CANCELLED），`OrderEventConsumer` 消费并留日志。异常不阻塞主流程。
- **测试中 Redis/MQ**：`application-test.yml` 排除 `RedisAutoConfiguration` + `RocketMQAutoConfiguration` + `NacosConfigEndpointAutoConfiguration`，所有 `@SpringBootTest` 类需 `@MockBean RedissonClient` + `@MockBean RocketMQTemplate`（mall-goods-order 额外需 `@MockBean AiChatFeignClient`）
- **向量同步**：mall-goods-order 通过 `AiChatFeignClient` 调用 ai-chat-service 的 `POST /api/vector/upsert`，`ProductService.syncVector` best-effort（异常仅 warn，不阻塞主流程）。存量回填走 `POST /api/product/internal/sync-vectors`（`syncAllVectors`，只同步 status=1 商品，返回 `{total, success, failed}` 统计，Milvus upsert 幂等可重复触发）
- **RAG 管道**：`AiChatService.chat` 预检索 — 用户消息 → SiliconFlow Embedding（BAAI/bge-large-zh-v1.5, 1024 维）→ Milvus COSINE Top-5 → Feign 批量查商品 → 注入 System Prompt；RAG 失败降级为空列表，工具调用保留作 fallback。`VectorSearchService` 启动时自动建 collection（product_vectors, IVF_FLAT），Milvus 不可用时所有方法降级不抛异常
- **Embedding**：**禁止引入 Spring AI**（2.0.x 需要 Spring Boot 4 / Framework 7，与本项目 Boot 3.2.5 运行时不兼容，编译能过但启动报 `ClassNotFoundException: RetryTemplate`）。Embedding 由 `EmbeddingClient`（RestTemplate 直连 SiliconFlow `/v1/embeddings`，OpenAI 兼容）实现
- **ai-chat-service 依赖冲突 pin**：`protobuf-java 3.24.0`（mysql-connector-j 传递引入 3.21 与 milvus-sdk-java 冲突）+ `grpc-bom 1.59.1` dependencyManagement import（RocketMQ 传递引入 grpc 1.50.0，milvus-sdk-java 需要 1.59.1 的 `ForwardingChannelBuilder2`，就近解析选 1.50 会导致启动时 `NoClassDefFoundError`——这是 Error 不是 Exception，`@PostConstruct` 里的 `catch (Exception)` 兜不住）
- **mcp-server**：`ToolController` 从 JWT 提取真实 userId，不信任请求体中的 userId
- **支付回调幂等**：`PayController.notify` 中 BizException（不可重试）返回 `"success"` 终止重试，仅系统异常返回 `"fail"` 触发重试。`PaymentMapper.markPaid` 使用 `UPDATE ... WHERE status = 1` 原子操作
- **异常处理**：mcp-server tools 区分 `FeignException`（下游服务故障）与 `Exception`（未知异常）；`GlobalExceptionHandler` 对外不暴露内部类名
- **内部 Feign 调用**：需校验 userId 所有权（如 `payOrder`），Feign 接口返回 `R<OrderDTO>` 类型化对象而非 `Map`
- **RestTemplate**：必须设置 connectTimeout + readTimeout，避免请求永久挂起
- **Nacos Config 导入检查**：Spring Cloud 2023.x 强制要求 `spring.config.import`，不使用 Nacos 配置中心的服务需在 application.yml 设置 `spring.cloud.nacos.config.import-check.enabled: false`
- **Feign 注解参数名**：Spring 6 要求 `@PathVariable`、`@RequestParam` 显式写 value（如 `@PathVariable("id")`），不能省略
- **Druid 数据源**：所有使用 MySQL 的服务必须引入 `druid-spring-boot-3-starter`（application.yml 中 `spring.datasource.type` 指向 Druid）
- **LoadBalancer**：所有使用 OpenFeign 的服务必须引入 `spring-cloud-starter-loadbalancer`
- **Sentinel**：Gateway 使用 `spring-cloud-alibaba-sentinel-gateway`（WebFlux 适配），业务模块使用 `spring-cloud-starter-alibaba-sentinel`（MVC 适配）。版本由父 POM dependencyManagement 指定（2023.0.1.0）。测试中 `spring.cloud.sentinel.enabled: false`。规则全部通过 Dashboard 动态配置，不在代码中预设。
- **SkyWalking**：纯 javaagent 挂载，零代码依赖。Agent 下载和 IDEA VM Options 见 `skywalking/README.md`。

## 行为准则

**Tradeoff:** 这些准则偏向谨慎而非速度。简单任务可自行判断。

### 1. Think Before Coding

- 明确陈述假设。不确定时先问
- 存在多种解读时，列出选项而非默默选择
- 有更简单的方案直接说

### 2. Simplicity First

- 不写未要求的功能
- 不为单次使用创建抽象
- 不处理不可能发生的错误场景
- 如果 200 行代码可以精简到 50 行，重写它

### 3. Surgical Changes

- 不"优化"相邻代码、注释、格式
- 不重构没坏的东西
- 匹配现有风格
- 只清理自己的修改产生的孤儿代码（未使用的 import、变量等）
- 不删除已有的死代码，除非被要求

### 4. Goal-Driven Execution

- 将任务转化为可验证的目标
- 多步骤任务先列出简要计划

## 项目 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md`。执行相关任务时，使用 `Skill` 工具加载对应的 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。


<!-- superpowers-zh:begin (do not edit between these markers) -->
# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文 review 沟通参考——话术模板、分级标注（必须修复/建议修改/仅供参考）、国内团队常见反模式应对。仅在用户显式 /chinese-code-review 时调用，不要根据上下文自动触发。
- **chinese-commit-conventions**: 中文 commit 与 changelog 配置参考——Conventional Commits 中文适配、commitlint/husky/commitizen 中文模板、conventional-changelog 中文配置。仅在用户显式 /chinese-commit-conventions 时调用，不要根据上下文自动触发。
- **chinese-documentation**: 中文文档排版参考——中英文空格、全半角标点、术语保留、链接格式、中文文案排版指北约定。仅在用户显式 /chinese-documentation 时调用，不要根据上下文自动触发。
- **chinese-git-workflow**: 国内 Git 平台配置参考——Gitee、Coding.net、极狐 GitLab、CNB 的 SSH/HTTPS/凭据/CI 接入差异与镜像同步配置。仅在用户显式 /chinese-git-workflow 时调用，不要根据上下文自动触发。
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用——通过提供合并、PR 或清理等结构化选项来引导开发工作的收尾
- **mcp-builder**: MCP 服务器构建方法论 — 系统化构建生产级 MCP 工具，让 AI 助手连接外部能力
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发，或在执行实现计划之前使用——通过原生工具或 git worktree 回退机制确保隔离工作区存在
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能，要求在任何响应（包括澄清性问题）之前调用 Skill 工具
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流——无需 API key，使用当前会话的 LLM 作为执行引擎。当用户提供 .yaml 工作流文件或要求多角色协作完成任务时触发。
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。
<!-- superpowers-zh:end -->
