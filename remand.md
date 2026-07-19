# 天机商城（tianji-mall）需求与部署方案

## 项目概述

基于 Spring Cloud Alibaba 与 MCP 协议实现的仿淘宝智能电商平台，支持完整商城交易 + AI 智能导购 + 支付宝沙盒支付。

**技术栈**：Vue3 + Spring Boot3/Spring Cloud Alibaba + MySQL + Redis + Milvus + Spring AI + Claude + MCP + 支付宝沙盒

## 核心功能（MVP）

1. 仿淘宝基础商城：用户、商品、购物车、订单、收货地址
2. 支付宝沙盒支付微服务，支持下单-支付-回调全流程
3. AI 智能导购：基于 Claude + MCP + RAG 实现对话式商品咨询、加购、下单、支付闭环

---

## 部署分层架构

### Windows 物理主机

- 前端商城页面（仿淘宝用户端）
- 开发调试工具：IDEA、Postman
- 内网穿透工具 Natapp
  - 暴露 pay-service 支付宝异步回调地址

### Linux 虚拟机（NAT静态IP）

#### 容器中间件组（Docker Compose 启动）

| 组件 | 端口 | 用途 |
|------|------|------|
| Nacos | 8848 | 注册配置中心 |
| MySQL | 3306 | 业务数据库 |
| Redis | 6379 | 缓存会话 |
| RocketMQ | 9876 | 消息队列 |
| Milvus | 19530 | 向量数据库 |
| SkyWalking | 11800/8090 | 链路监控 |
| Sentinel Dashboard | 8858 | 流量管控控制台 |

#### 6 个 Java 微服务（容器打包运行）

| 服务 | 端口 | 说明 |
|------|------|------|
| gateway | 8080 | 网关服务 |
| user-service | 8081 | 用户服务 |
| mall-goods-order | 8082 | 商城核心服务（商品+购物车+订单+地址） |
| pay-service | 8083 | 支付宝支付服务 |
| mcp-server | 8084 | MCP 工具中间服务 |
| ai-chat-service | 8085 | AI 智能导购服务 |

---

## 网络通信链路

1. Windows 前端 → 本机 Gateway 网关
2. IDEA 本地调试 → 远程连接虚拟机 Nacos、MySQL
3. 虚拟机内部微服务互通：Nacos 注册发现 + OpenFeign
4. MCP Client(ai-chat-service) ↔ MCP Server(mcp-server) 跨服务调用
5. 支付宝沙盒 → natapp(Windows) → 转发到本机 pay-service 回调接口

---

## 部署执行流程

1. Linux 虚拟机安装 Docker（    
   failed to dial "/run/containerd/containerd.sock": context deadline exceeded

为什么：Docker 依赖 containerd 作为容器运行时。docker.service 启动 → dockerd 尝试连接 containerd.sock → containerd 进程不存在/socket 未就绪 → 超时退出。然后 systemd
又自动重试，进入死循环。

以后 VM 重启后的正确操作：

systemctl start containerd   # 先拉起运行时
systemctl start docker        # 再拉 Docker  ）
、Docker Compose
2. Compose 一键启动全部中间件
3. Java 项目打包 jar，制作 Docker 镜像，启动 6 个微服务容器
4. Windows 启动内网穿透，配置支付宝 notify 回调地址
5. Windows 启动前端页面，访问虚拟机网关进行全流程测试

---

## 毕设部署优势

1. 开发环境与服务环境分离，贴近企业生产部署规范
2. 所有后端组件统一 Linux 容器化，一键启停，演示方便
3. 虚拟机独立环境，不污染 Windows 本机
4. 架构分层清晰，论文可绘制「主机-虚拟机分离部署拓扑图」

---

## 避坑关键点

- 虚拟机放行所有端口，关闭防火墙或开放端口
- Nacos 配置文件统一填写虚拟机静态 IP，不能用 127.0.0.1
- 支付宝回调依赖 Windows 内网穿透，虚拟机无法被公网直接访问
- Milvus、RocketMQ 资源占用高，虚拟机分配至少 4 核 8G 内存

---

## 项目目录结构

```
tianji-mall/
├── pom.xml                              # 父 POM（聚合 + 依赖管理）
├── .gitignore
├── docker/
│   ├── docker-compose.yml               # 中间件编排
│   └── docker-compose-services.yml      # 微服务编排（可选）
├── sql/
│   └── init.sql                         # 全量建表脚本
│
├── tianji-common/                       # 公共模块（jar，无启动类）
│   └── src/main/java/com/tianji/common/
│       ├── result/R.java
│       ├── entity/BaseEntity.java
│       ├── exception/BizException.java
│       └── exception/GlobalExceptionHandler.java
│
├── gateway/                             # API 网关
│   └── src/main/java/com/tianji/gateway/
│       ├── GatewayApplication.java
│       ├── config/CorsConfig.java
│       └── filter/AuthGlobalFilter.java
│
├── user-service/                        # 用户服务
│   └── src/main/java/com/tianji/user/
│       ├── UserApplication.java
│       ├── controller/UserController.java
│       ├── service/UserService.java
│       ├── mapper/UserMapper.java
│       └── entity/User.java
│
├── mall-goods-order/                    # 商城核心（商品+购物车+订单+地址）
│   └── src/main/java/com/tianji/mall/
│       ├── MallApplication.java
│       ├── controller/{Product,Cart,Order,Address}Controller.java
│       ├── service/{Product,Cart,Order,Address}Service.java
│       ├── mapper/{Product,Category,CartItem,Order,OrderItem,Address}Mapper.java
│       └── entity/{Product,Category,CartItem,Order,OrderItem,Address}.java
│
├── pay-service/                         # 支付宝沙盒支付
│   └── src/main/java/com/tianji/pay/
│       ├── PayApplication.java
│       ├── controller/PayController.java
│       ├── service/PayService.java
│       ├── mapper/PaymentMapper.java
│       └── entity/Payment.java
│
├── mcp-server/                          # MCP 工具中间服务
│   └── src/main/java/com/tianji/mcp/
│       ├── McpServerApplication.java
│       ├── config/McpServerConfig.java
│       └── tools/{ProductTools,OrderTools,CartTools}.java
│
└── ai-chat-service/                     # AI 智能导购（Claude + MCP Client + RAG）
    └── src/main/java/com/tianji/aichat/
        ├── AiChatApplication.java
        ├── controller/AiChatController.java
        ├── service/AiChatService.java
        ├── config/ClaudeConfig.java
        └── entity/AiConversation.java
```

## 技术版本

| 组件 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.3 |
| Spring Cloud Alibaba | 2023.0.1.0 |
| MyBatis-Plus | 3.5.7 |
| Spring AI | 1.0.0-M5 |
| MySQL | 8.0.33 |
| Druid | 1.2.23 |
| jjwt | 0.12.6 |
| RocketMQ | 5.2.0 |

## SQL 表设计（8 张表）

| 表名 | 所属模块 | 核心字段 |
|------|----------|----------|
| `user` | user-service | id, username, password, phone, email, avatar, status |
| `category` | mall-goods-order | id, name, parent_id, sort |
| `product` | mall-goods-order | id, name, description, price, stock, category_id, images, status |
| `cart_item` | mall-goods-order | id, user_id, product_id, quantity, checked |
| `order` | mall-goods-order | id, order_no, user_id, total_amount, status, pay_type, address_id |
| `order_item` | mall-goods-order | id, order_id, product_id, product_name, price, quantity |
| `address` | mall-goods-order | id, user_id, receiver_name, phone, province, city, district, detail, is_default |
| `payment` | pay-service | id, payment_no, order_id, user_id, amount, status, trade_no |
| `ai_conversation` | ai-chat-service | id, user_id, session_id, role, content, product_ids |

> 所有表使用 InnoDB 引擎，utf8mb4 字符集，包含 `create_time`/`update_time` 字段。



目标产出

- 父 POM 重构为多模块聚合项目
- 7 个子模块，每个带 pom.xml、Spring Boot 启动类、application.yml
- Docker 中间件编排文件
- MySQL 初始化建表脚本
- .gitignore 更新

1. 父 POM 重构 — pom.xml

完全重写现有 pom.xml：

- groupId: com.tianji，artifactId: tianji-mall，packaging: pom
- modules: tianji-common, gateway, user-service, mall-goods-order, pay-service, mcp-server, ai-chat-service
- properties: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.3, Spring Cloud Alibaba 2023.0.1.0, MyBatis-Plus 3.5.7, Spring AI 1.0.0-M5, MySQL 8.0.33, Druid 1.2.23, jjwt 0.12.6,
  RocketMQ 5.2.0
- dependencyManagement: 导入 Spring Cloud BOM、Spring Cloud Alibaba BOM，声明所有子模块共用依赖版本

2. 子模块创建（7 个）

每个子模块目录结构：
{module}/
├── pom.xml          # 声明依赖（按角色：gateway 用 spring-cloud-gateway，普通服务用 spring-boot-starter-web + mybatis-plus 等）
└── src/main/
├── java/com/tianji/{package}/
│   └── {Module}Application.java  # @SpringBootApplication 启动类
└── resources/
└── application.yml           # server.port + spring.application.name + 配置项占位

各级子模块 POM 依赖分配：

┌──────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────┐
│       模块       │                                             依赖                                             │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ tianji-common    │ Lombok, Jackson, jakarta-validation (无启动类)                                               │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ gateway          │ spring-cloud-starter-gateway, spring-cloud-starter-loadbalancer, Nacos discovery (端口 8080) │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ user-service     │ spring-boot-starter-web, mybatis-plus, MySQL driver, Druid, Nacos, jjwt (端口 8081)          │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ mall-goods-order │ 同 user-service (端口 8082)                                                                  │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ pay-service      │ 同 user-service + 支付宝 SDK (端口 8083)                                                     │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ mcp-server       │ spring-boot-starter-web, Spring AI, Nacos (端口 8084)                                        │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ ai-chat-service  │ spring-boot-starter-web, Spring AI, Nacos, RocketMQ (端口 8085)                              │
└──────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────┘

3. Docker 编排 — docker/docker-compose.yml

定义中间件服务（version 默认最新 stable）：
- Nacos standalone（8848）
- MySQL 8.0.33（3306）
- Redis 7（6379）
- RocketMQ name-server + broker（9876）
- Milvus standalone（19530）
- SkyWalking OAP（11800/8090）
- Sentinel Dashboard（8858）

只定义服务，不含 volumes 持久化配置（后续按需添加）。

4. SQL 建表脚本 — sql/init.sql

创建数据库 tianji_mall，然后按 remand.md 定义创建 9 张表：
- user, category, product, cart_item, order, order_item, address, payment, ai_conversation

每张表含 id BIGINT AUTO_INCREMENT PRIMARY KEY、create_time DATETIME、update_time DATETIME，InnoDB + utf8mb4。

5. .gitignore 更新

追加内容忽略 docker/volumes/、*.log、.env 等。

验证方式

# 编译验证
mvn compile

# 确认所有模块编译通过（业务类为空，启动类可编译即可）
     │ 4. SQL 建表脚本 — sql/init.sql                                                                                                                                              │
     │                                                                                                                                                                             │
     │ 创建数据库 tianji_mall，然后按 remand.md 定义创建 9 张表：                                                                                                                  │
     │ - user, category, product, cart_item, order, order_item, address, payment, ai_conversation                                                                                  │
     │                                                                                                                                                                             │
     │ 每张表含 id BIGINT AUTO_INCREMENT PRIMARY KEY、create_time DATETIME、update_time DATETIME，InnoDB + utf8mb4。                                                               │
     │                                                                                                                                                                             │
     │ 5. .gitignore 更新                                                                                                                                                          │
     │                                                                                                                                                                             │
     │ 追加内容忽略 docker/volumes/、*.log、.env 等。                                                                                                                              │
     │                                                                                                                                                                             │
     │ 验证方式                                                                                                                                                                    │
     │                                                                                                                                                                             │
     │ # 编译验证                                                                                                                                                                  │
     │ mvn compile                                                                                                                                                                 │
     │                                                                                                                                                                             │
     │ # 确认所有模块编译通过（业务类为空，启动类可编译即可）                                                                                                                      │
     │ # 确认 docker-compose.yml 语法正确（可选：docker compose config）                                                                                                           │
     ╰─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯
