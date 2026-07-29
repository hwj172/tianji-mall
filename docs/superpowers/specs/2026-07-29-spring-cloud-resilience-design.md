# Spring Cloud 韧性加固 — 设计规格

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。

**目标：** 对 OpenFeign、Gateway、RocketMQ、Redis 四个组件做生产级韧性加固，防止级联故障、消息丢失、连接泄漏。

**架构：** 分层超时（Gateway 30s > Feign 10s > 连接 3s）+ Sentinel 断路器 + RocketMQ 消费者重试/DLQ + Redis 连接池。

**技术栈：** Spring Cloud OpenFeign + Sentinel + Spring Cloud Gateway + RocketMQ + Lettuce

---

## 一、OpenFeign 韧性

### 1.1 超时与压缩

全局默认配置（`spring.cloud.openfeign.client.config.default`）：

| 配置项 | 值 | 理由 |
|--------|---|------|
| `connectTimeout` | 3000ms | 连接拒绝应快速失败 |
| `readTimeout` | 10000ms | 内部服务 10s 足够，超时即异常 |
| `compression.request.enabled` | true | 减少带宽 |
| `compression.response.enabled` | true | 减少带宽 |

### 1.2 重试策略

**不自动重试。** 原因：Feign 调用涉及非幂等操作（创建订单、退款），重试可能导致重复执行。幂等场景由上游（Gateway 或用户）控制重试。

自定义 `FeignRetryer`（tianji-common）：`maxAttempts=1, backoff=0`，即不重试。

### 1.3 Sentinel 断路器

开启 `feign.sentinel.enabled: true`，复用已有 Sentinel Dashboard + Nacos 持久化。

断路器规则通过 Sentinel Dashboard 配置（不在代码中预设）：
- 降级策略：慢调用比例（RT > 5000ms, 比例 50%, 窗口 10s, 熔断 30s）
- 各 FeignClient 独立熔断

### 1.4 Fallback

每个 `@FeignClient` 增加 `fallbackFactory`，返回 `R.error` 而非抛异常：

| 模块 | FeignClient | Fallback 行为 |
|------|------------|---------------|
| mall-goods-order | PayFeignClient | `R.error(500, "支付服务暂不可用")` |
| mall-goods-order | UserFeignClient | `R.error(500, "用户服务暂不可用")` |
| mall-goods-order | AiChatFeignClient | void 方法：log + return |
| pay-service | OrderFeignClient | `R.error(500, "订单服务暂不可用")` |
| ai-chat-service | McpFeignClient | 返回空 Map + log |
| ai-chat-service | ProductFeignClient | 返回空 Map + log |
| mcp-server | MallFeignClient | 返回空 Map + log |

### 1.5 ErrorDecoder

tianji-common 新增 `FeignErrorDecoder`：
- 4xx → `BizException`（不重试）
- 5xx / 连接超时 → `ServiceUnavailableException`（上游可判断重试）
- 未知异常 → `RuntimeException`

---

## 二、Gateway 韧性

### 2.1 HTTP 客户端

```yaml
spring.cloud.gateway.httpclient:
  connect-timeout: 3000
  response-timeout: 30000
  pool:
    type: elastic
    max-idle-time: 60s
    max-life-time: 300s
```

### 2.2 重试

仅对幂等 GET 请求重试 1 次，触发条件：502/503/504：

```yaml
spring.cloud.gateway.default-filters:
  - name: Retry
    args:
      retries: 1
      statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
      methods: GET
```

---

## 三、RocketMQ 消费者韧性

### 3.1 重试配置

```yaml
rocketmq:
  consumer:
    max-reconsume-times: 3
    delay-level-when-next-consume: 3
```

4 个消费者统一配置（OrderTimeoutConsumer / OrderEventConsumer / NotificationConsumer / GroupBuyTimeoutConsumer）。

### 3.2 异常传播

移除消费者中吞异常的模式：

- **可重试异常**（DB 连接断开、Redis 超时）→ 直接抛 → RocketMQ 重试 → 3 次后进 DLQ
- **不可重试异常**（业务校验失败，如关联订单不存在）→ 标记为标记为明确不可重试，log WARN + DLQ

### 3.3 DLQ

RocketMQ 内置 DLQ 机制：消费失败达到 maxReconsumeTimes 后自动进 `%DLQ%consumerGroup`。无需额外配置，但需记录 DLQ 监控。

---

## 四、Redis 连接池

### 4.1 Lettuce 连接池

```yaml
spring.data.redis:
  password: ${REDIS_PASSWORD:}
  lettuce:
    pool:
      max-active: 8
      max-idle: 8
      min-idle: 2
      max-wait: 2000ms
      time-between-eviction-runs: 30s
```

仅 mall-goods-order 使用 Redis（缓存 + 分布式锁），其他模块无需配置。

### 4.2 依赖

添加 `commons-pool2`（Lettuce 连接池需要），mall-goods-order 已有此依赖。

---

## 五、影响范围

| 模块 | 变更类型 | 说明 |
|------|---------|------|
| tianji-common | 新增 | FeignErrorDecoder, FeignRetryer |
| mall-goods-order | 修改 | Feign Fallback(×3), application.yml, RocketMQ consumer(×4), Redis pool |
| pay-service | 修改 | Feign Fallback(×1), application.yml |
| mcp-server | 修改 | Feign Fallback(×1), application.yml |
| ai-chat-service | 修改 | Feign Fallback(×2), application.yml |
| gateway | 修改 | application.yml（httpclient + retry） |
| 父 POM | 不修改 | 无新依赖 |

### 不涉及的模块

- user-service：无 Feign 依赖，无变更
- Docker Compose：无变更
- 前端：无变更

---

## 六、新增文件清单

```
tianji-common/src/main/java/com/tianji/common/feign/
├── FeignRetryer.java          # maxAttempts=1, backoff=0
└── FeignErrorDecoder.java     # 统一错误解码

mall-goods-order/src/main/java/com/tianji/mall/feign/fallback/
├── PayFeignClientFallback.java
├── UserFeignClientFallback.java
└── AiChatFeignClientFallback.java

pay-service/src/main/java/com/tianji/pay/feign/fallback/
└── OrderFeignClientFallback.java

ai-chat-service/src/main/java/com/tianji/aichat/feign/fallback/
├── McpFeignClientFallback.java
└── ProductFeignClientFallback.java

mcp-server/src/main/java/com/tianji/mcp/feign/fallback/
└── MallFeignClientFallback.java
```

## 七、测试策略

| 测试类型 | 覆盖内容 |
|---------|---------|
| FeignRetryer 单元测试 | 验证 maxAttempts=1 |
| FeignErrorDecoder 单元测试 | 4xx → BizException / 5xx → ServiceUnavailableException |
| Fallback 单元测试 | 每个 Fallback 返回 `R.error` |
| Consumer 单元测试 | 模拟可重试/不可重试异常，验证不再吞异常 |
| 现有测试 | 全部 596 个测试应继续通过 |

## 八、配置变更汇总

### gateway/application.yml
```yaml
spring.cloud.gateway:
  httpclient:
    connect-timeout: 3000
    response-timeout: 30000
    pool.max-idle-time: 60s
    pool.max-life-time: 300s
  default-filters:
    - name: Retry
      args:
        retries: 1
        statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
        methods: GET
```

### mall-goods-order/application.yml
```yaml
spring.cloud.openfeign:
  client.config.default:
    connectTimeout: 3000
    readTimeout: 10000
  compression.request.enabled: true
  compression.response.enabled: true
  sentinel.enabled: true

rocketmq.consumer:
  max-reconsume-times: 3
  delay-level-when-next-consume: 3

spring.data.redis.lettuce.pool:
  max-active: 8
  max-idle: 8
  min-idle: 2
  max-wait: 2000ms
  time-between-eviction-runs: 30s
```

### pay-service / mcp-server / ai-chat-service application.yml
```yaml
spring.cloud.openfeign:
  client.config.default:
    connectTimeout: 3000
    readTimeout: 10000
  compression.request.enabled: true
  compression.response.enabled: true
  sentinel.enabled: true
```
