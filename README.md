# 天机商城（tianji-mall）

基于 **Spring Cloud Alibaba** 的仿淘宝智能电商平台。微服务 + 向量检索 + DeepSeek AI 导购，前后端完整闭环。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17 / Spring Boot 3.2.5 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.1.0 |
| 数据 | MySQL 8.0 + MyBatis-Plus 3.5.7 + Druid / Redis + Redisson（缓存 + 分布式锁） |
| 中间件 | Nacos（注册配置）/ RocketMQ（订单事件 / 超时延迟 / 拼团超时）/ Milvus（向量库） |
| 监控 | SkyWalking（链路追踪）/ Sentinel（流量管控，规则 Nacos 持久化） |
| AI | DeepSeek（对话 + 工具调用）+ SiliconFlow Embedding（RAG 向量检索）+ Milvus |
| 前端 | Vue 3 + Element Plus + Pinia + Vite / ECharts（看板图表）/ GSAP（动效）/ Orbitron 数字字体，影院级深色主题 |

## 功能特性

- **商城交易全链路**：浏览 / 搜索（含热词）/ 加购 / 结算（满减 + 优惠券叠加）/ 下单 / 支付宝沙盒支付 / 订单 / 退款（仅退款 + 退货退款）/ 评价晒图 / 物流轨迹 / 消息通知 / 收藏 / 浏览足迹
- **智能电商**：AI 导购对话、**传图搜商品**（VL 向量检索）、RAG 语义搜索、MCP 工具调用闭环（查订单 / 加购 / 下单 / 支付）
- **营销**：限时秒杀（原子扣减 + 窗口判定）、阶梯拼团（CAS 原子参团 + 超时取消 + 防自刷）、优惠券（领券中心 / 适用范围 / 自动过期）、满减活动
- **商家中心**：店铺管理、商品上架（强制审核）、订单发货、评价回复、退款处理
- **管理后台**：数据看板（GMV / 订单 / 用户趋势 + ECharts）、商品 / 分类 / 优惠券 / 满减 / 公告 / 店铺 / 用户 / Banner / SKU / 秒杀 / 拼团管理、**商品上架审核**、**用户资料审核**
- **安全保障**：JWT + 网关角色鉴权（`X-User-Role` 注入）、内部接口 token 鉴权、IDOR 防护、库存 / 积分 / 优惠券原子操作、退款按实付比例分摊、GMV 剔除退款

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
└── tianji-mall-frontend   # 前端（Vue3，独立项目）
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

重灌演示数据：

```bash
scp sql/seed-current.sql root@192.168.150.11:/tmp/
ssh root@192.168.150.11 "docker exec -i mysql mysql -uroot -proot tianji_mall < /tmp/seed-current.sql"
```

启动后访问：前端 http://localhost:5173 / 网关 http://localhost:8080

## 演示数据

`sql/seed-current.sql` 为干净演示 seed（自 TRUNCATE + 灌入，密码统一 `123456`）：

- **商品图用真实 Unsplash 照片**（12 商品 + 3 banner + 店 logo + 评论晒图按类目贴合，非占位图）
- 6 用户 + 2 店铺 + 9 分类 + 13 商品（含 SKU / 秒杀 / 拼团 / 待审核）+ 优惠券 + 满减 + 6 精选演示订单（覆盖 5 状态）+ 退款 / 评价 / 物流 / 通知
- 待审核商品（iPad）与待审核用户（buyer_zhao）供管理员走审核流程

## 账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |
| seller_demo | 123456 | 商家（已开店） |
| testuser | 123456 | 普通用户 |
| buyer_wang | 123456 | 买家（已领券） |
| seller_li | 123456 | 商家 |
| buyer_zhao | 123456 | 普通用户（资料待审核） |

普通用户可在个人中心「注册开店」成为商家（开店后需重新登录激活权限）。

## 测试

```bash
mvn test    # 全模块测试（711 个：Common 39 + Gateway 42 + User 40 + Mall 487 + Pay 28 + MCP 34 + AI-Chat 41）
```

## 监控控制台

| 组件 | 地址 |
|------|------|
| Nacos | http://192.168.150.11:8848/nacos |
| Sentinel | http://192.168.150.11:8858（sentinel/sentinel） |
| SkyWalking | http://192.168.150.11:8090 |

## 文档索引

- `CLAUDE.md` — 开发指导（架构 / 测试约定 / 后端与前端关键约定 / 换肤与部署要点）
- `remand.md` — 需求文档
- `sql/init.sql` — 全量建表脚本；`sql/seed-current.sql` — 演示数据
- `docs/superpowers/` — 设计与实现计划
