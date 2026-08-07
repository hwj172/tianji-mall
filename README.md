# 天机商城（tianji-mall）

基于 **Spring Cloud Alibaba** 的仿淘宝智能电商平台。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 / Spring Boot 3.2.5 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.1.0 |
| 数据 | MySQL 8.0 + MyBatis-Plus 3.5.7 + Druid / Redis + Redisson |
| 中间件 | Nacos（注册配置）/ RocketMQ / Milvus（向量库） |
| 监控 | SkyWalking / Sentinel Dashboard |
| AI | DeepSeek（对话）+ SiliconFlow Embedding（RAG） |
| 前端 | Vue 3 + Element Plus + Pinia + Vite（独立项目 `tianji-mall-frontend`） |

## 模块结构

```
tianji-mall (父 POM)
├── tianji-common          # 公共模块（jar：R/异常/JWT/Feign）
├── gateway                # API 网关 — 8080
├── user-service           # 用户服务 — 8081
├── mall-goods-order       # 商城核心（商品/购物车/订单/退款/评价/优惠券/秒杀/拼团/店铺）— 8082
├── pay-service            # 支付宝沙盒支付 — 8083
├── mcp-server             # 工具网关（REST API）— 8084
├── ai-chat-service        # AI 智能导购（DeepSeek + RAG）— 8085
└── tianji-mall-frontend   # 前端（Vue3）
```

## 部署拓扑

| 环境 | 组件 |
|------|------|
| **Windows 物理机** | IDEA 开发（6 微服务）、natapp 内网穿透（支付回调）、前端 dev server |
| **Linux VM**（192.168.150.11） | Docker 中间件（Nacos/MySQL/Redis/RocketMQ/Milvus/SkyWalking/Sentinel）+ 微服务容器 |

## 快速启动

```bash
# 一键启动（本机）：SSH 隧道 → VM 恢复(中间件+微服务) → 前端 dev → 验证
bash start-all.sh
```

启动后访问：前端 http://localhost:5173 / 网关 http://localhost:8080

## 账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |
| seller_demo | 123456 | 商家（已开店） |

普通用户可在个人中心「注册开店」成为商家（开店后需重新登录激活权限）。

## 监控控制台

| 组件 | 地址 |
|------|------|
| Nacos | http://192.168.150.11:8848/nacos |
| Sentinel | http://192.168.150.11:8858（sentinel/sentinel） |
| SkyWalking | http://192.168.150.11:8090 |

## 文档索引

- `CLAUDE.md` — 开发指导（架构 / 测试约定 / 后端与前端关键约定）
- `remand.md` — 需求文档
- `docs/superpowers/` — 设计与实现计划
