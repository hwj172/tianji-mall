# Spring Cloud 韧性加固 — 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 对 OpenFeign、Gateway、RocketMQ、Redis 四个组件做生产级韧性加固。

**架构：** 分层超时（Gateway 30s > Feign 10s > 连接 3s）+ Sentinel 断路器 + RocketMQ 消费者重试/DLQ + Redis 连接池。

**技术栈：** Spring Cloud OpenFeign 4.1.x + Sentinel 1.8.6 + Spring Cloud Gateway 4.1.x + RocketMQ 5.3.0 + Lettuce

**规格文档：** `docs/superpowers/specs/2026-07-29-spring-cloud-resilience-design.md`

---

## 文件结构

```
tianji-common/src/main/java/com/tianji/common/
├── exception/
│   └── ServiceUnavailableException.java     # 新增 — 服务不可用异常（区分于业务异常）
└── feign/
    ├── FeignRetryer.java                    # 新增 — 不重试策略
    └── FeignErrorDecoder.java               # 新增 — 统一 Feign 错误解码

mall-goods-order/src/main/java/com/tianji/mall/
├── feign/fallback/
│   ├── PayFeignClientFallback.java          # 新增
│   ├── UserFeignClientFallback.java         # 新增
│   └── AiChatFeignClientFallback.java       # 新增
├── feign/
│   ├── PayFeignClient.java                  # 修改 — 添加 fallbackFactory
│   ├── UserFeignClient.java                 # 修改 — 添加 fallbackFactory
│   └── AiChatFeignClient.java               # 修改 — 添加 fallbackFactory
├── consumer/
│   ├── OrderEventConsumer.java              # 修改 — 移除吞异常
│   ├── NotificationConsumer.java            # 修改 — 移除吞异常（not needed, it has no try-catch anyway）
│   └── GroupBuyTimeoutConsumer.java         # 修改 — 移除吞异常（only in inner loop）
└── resources/application.yml                # 修改 — Feign + RocketMQ consumer + Redis pool 配置

pay-service/src/main/java/com/tianji/pay/
├── feign/fallback/
│   └── OrderFeignClientFallback.java        # 新增
├── feign/OrderFeignClient.java              # 修改 — 添加 fallbackFactory
└── resources/application.yml                # 修改 — Feign 配置

mcp-server/src/main/java/com/tianji/mcp/
├── feign/fallback/
│   └── MallFeignClientFallback.java         # 新增
├── feign/MallFeignClient.java               # 修改 — 添加 fallbackFactory
└── resources/application.yml                # 修改 — Feign 配置

ai-chat-service/src/main/java/com/tianji/aichat/
├── feign/fallback/
│   ├── McpFeignClientFallback.java          # 新增
│   └── ProductFeignClientFallback.java      # 新增
├── feign/
│   ├── McpFeignClient.java                  # 修改 — 添加 fallbackFactory
│   └── ProductFeignClient.java              # 修改 — 添加 fallbackFactory
└── resources/application.yml                # 修改 — Feign 配置

gateway/src/main/resources/application.yml    # 修改 — httpclient + retry
```

---

### 任务 1：tianji-common — ServiceUnavailableException + FeignRetryer + FeignErrorDecoder

**文件：**
- 创建：`tianji-common/src/main/java/com/tianji/common/exception/ServiceUnavailableException.java`
- 创建：`tianji-common/src/main/java/com/tianji/common/feign/FeignRetryer.java`
- 创建：`tianji-common/src/main/java/com/tianji/common/feign/FeignErrorDecoder.java`
- 测试：`tianji-common/src/test/java/com/tianji/common/feign/FeignRetryerTest.java`
- 测试：`tianji-common/src/test/java/com/tianji/common/feign/FeignErrorDecoderTest.java`

- [ ] **步骤 1：创建 ServiceUnavailableException**

```java
package com.tianji.common.exception;

/**
 * 服务不可用异常 — 下游服务调用失败时抛出，区分于业务校验异常。
 * 上游可据此判断是否重试。
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **步骤 2：创建 FeignRetryer — 不重试策略**

```java
package com.tianji.common.feign;

import feign.RetryableException;
import feign.Retryer;

/**
 * Feign 不重试策略 — 失败直接抛异常，由上游 Sentinel 断路器处理。
 * 不自动重试的原因是 Feign 调用涉及非幂等操作（创建订单、退款等）。
 */
public class FeignRetryer implements Retryer {

    @Override
    public void continueOrPropagate(RetryableException e) {
        throw e;
    }

    @Override
    public Retryer clone() {
        return this;
    }
}
```

- [ ] **步骤 3：创建 FeignErrorDecoder**

```java
package com.tianji.common.feign;

import com.tianji.common.exception.BizException;
import com.tianji.common.exception.ServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一 Feign 错误解码器。
 * 4xx → BizException（不可重试）
 * 5xx / IOException → ServiceUnavailableException（可重试）
 */
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.warn("Feign 调用失败: method={}, status={}", methodKey, status);

        if (status >= 400 && status < 500) {
            return new BizException(400, "下游服务请求错误: " + status);
        }
        if (status >= 500) {
            return new ServiceUnavailableException("下游服务异常: " + status);
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
```

- [ ] **步骤 4：编写 FeignRetryerTest**

```java
package com.tianji.common.feign;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FeignRetryerTest {

    @Test
    void shouldPropagateExceptionWithoutRetry() {
        FeignRetryer retryer = new FeignRetryer();
        RetryableException ex = new RetryableException(
                503, "Service Unavailable", Request.HttpMethod.GET, null,
                Request.create(Request.HttpMethod.GET, "/test",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8, null));

        assertThrows(RetryableException.class, () -> retryer.continueOrPropagate(ex));
    }

    @Test
    void cloneShouldReturnSameType() {
        FeignRetryer retryer = new FeignRetryer();
        assertThrows(RetryableException.class, () -> {
            RetryableException ex = new RetryableException(
                    503, "Service Unavailable", Request.HttpMethod.GET, null,
                    Request.create(Request.HttpMethod.GET, "/test",
                            Collections.emptyMap(), null, StandardCharsets.UTF_8, null));
            retryer.clone().continueOrPropagate(ex);
        });
    }
}
```

- [ ] **步骤 5：编写 FeignErrorDecoderTest**

```java
package com.tianji.common.feign;

import com.tianji.common.exception.BizException;
import com.tianji.common.exception.ServiceUnavailableException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    private Response response(int status) {
        return Response.builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "/test",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
                .headers(new LinkedHashMap<>())
                .build();
    }

    @Test
    void shouldThrowBizExceptionFor4xx() {
        Exception e = decoder.decode("TestClient#method()", response(404));
        assertInstanceOf(BizException.class, e);
        assertTrue(e.getMessage().contains("404"));
    }

    @Test
    void shouldThrowServiceUnavailableExceptionFor5xx() {
        Exception e = decoder.decode("TestClient#method()", response(503));
        assertInstanceOf(ServiceUnavailableException.class, e);
        assertTrue(e.getMessage().contains("503"));
    }

    @Test
    void shouldThrowBizExceptionFor400() {
        Exception e = decoder.decode("TestClient#method()", response(400));
        assertInstanceOf(BizException.class, e);
    }

    @Test
    void shouldThrowServiceUnavailableExceptionFor500() {
        Exception e = decoder.decode("TestClient#method()", response(500));
        assertInstanceOf(ServiceUnavailableException.class, e);
    }
}
```

- [ ] **步骤 6：运行 tianji-common 测试验证**

运行：`mvn test -pl tianji-common`
预期：PASS — 新增 6 tests（FeignRetryerTest 2 + FeignErrorDecoderTest 4）

- [ ] **步骤 7：Commit**

```bash
git add tianji-common/src/main/java/com/tianji/common/exception/ServiceUnavailableException.java \
        tianji-common/src/main/java/com/tianji/common/feign/ \
        tianji-common/src/test/java/com/tianji/common/feign/
git commit -m "feat(common): add FeignRetryer, FeignErrorDecoder and ServiceUnavailableException"
```

---

### 任务 2：mall-goods-order — Feign Fallbacks + 修改 FeignClient 接口

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/feign/fallback/PayFeignClientFallback.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/feign/fallback/UserFeignClientFallback.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/feign/fallback/AiChatFeignClientFallback.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/feign/PayFeignClient.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/feign/AiChatFeignClient.java`
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/feign/fallback/PayFeignClientFallbackTest.java`

- [ ] **步骤 1：创建 PayFeignClientFallback**

```java
package com.tianji.mall.feign.fallback;

import com.tianji.common.result.R;
import com.tianji.mall.feign.PayFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class PayFeignClientFallback implements FallbackFactory<PayFeignClient> {

    @Override
    public PayFeignClient create(Throwable cause) {
        log.error("PayFeignClient 调用失败，触发降级", cause);
        return new PayFeignClient() {
            @Override
            public R<Void> refundOrder(Long orderId, Long userId, BigDecimal amount, String reason) {
                log.warn("退款降级: orderId={}, amount={}", orderId, amount);
                return R.error(500, "支付服务暂不可用，请稍后重试");
            }
        };
    }
}
```

- [ ] **步骤 2：创建 UserFeignClientFallback**

```java
package com.tianji.mall.feign.fallback;

import com.tianji.common.result.R;
import com.tianji.mall.feign.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class UserFeignClientFallback implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient 调用失败，触发降级", cause);
        return new UserFeignClient() {
            @Override
            public R<Map<String, Object>> getUserById(Long id) {
                return R.error(500, "用户服务暂不可用");
            }

            @Override
            public R<Long> countUsers() {
                return R.ok(0L);
            }

            @Override
            public R<Void> promoteToSeller(Long userId) {
                return R.error(500, "用户服务暂不可用");
            }

            @Override
            public R<Map> listUsers(int page, int size, String keyword, String role, Integer status) {
                Map<String, Object> empty = new java.util.HashMap<>();
                empty.put("records", Collections.emptyList());
                empty.put("total", 0);
                return R.ok(empty);
            }

            @Override
            public R<Void> updateUserStatus(Long id, Integer status) {
                return R.error(500, "用户服务暂不可用");
            }

            @Override
            public R<Void> updateUserRole(Long id, String role) {
                return R.error(500, "用户服务暂不可用");
            }
        };
    }
}
```

- [ ] **步骤 3：创建 AiChatFeignClientFallback**

```java
package com.tianji.mall.feign.fallback;

import com.tianji.mall.feign.AiChatFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AiChatFeignClientFallback implements FallbackFactory<AiChatFeignClient> {

    @Override
    public AiChatFeignClient create(Throwable cause) {
        log.error("AiChatFeignClient 调用失败，触发降级", cause);
        return body -> log.warn("向量同步降级跳过");
    }
}
```

- [ ] **步骤 4：修改 PayFeignClient — 添加 fallbackFactory**

修改 `mall-goods-order/src/main/java/com/tianji/mall/feign/PayFeignClient.java` 第 10 行：

```java
@FeignClient(name = "pay-service", fallbackFactory = PayFeignClientFallback.class)
```

- [ ] **步骤 5：修改 UserFeignClient — 添加 fallbackFactory**

修改 `mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java` 第 9 行：

```java
@FeignClient(name = "user-service", fallbackFactory = UserFeignClientFallback.class)
```

- [ ] **步骤 6：修改 AiChatFeignClient — 添加 fallbackFactory**

修改 `mall-goods-order/src/main/java/com/tianji/mall/feign/AiChatFeignClient.java` 第 9 行：

```java
@FeignClient(name = "ai-chat-service", fallbackFactory = AiChatFeignClientFallback.class)
```

- [ ] **步骤 7：编写 PayFeignClientFallbackTest**

```java
package com.tianji.mall.feign.fallback;

import com.tianji.common.result.R;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PayFeignClientFallbackTest {

    @Test
    void shouldReturnErrorOnRefundFailure() {
        PayFeignClientFallback fallback = new PayFeignClientFallback();
        var client = fallback.create(new RuntimeException("connection refused"));
        R<Void> result = client.refundOrder(1L, 1L, BigDecimal.TEN, "test");
        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("暂不可用"));
    }
}
```

- [ ] **步骤 8：运行 mall-goods-order 测试验证**

运行：`mvn test -pl mall-goods-order`
预期：PASS

- [ ] **步骤 9：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/feign/fallback/ \
        mall-goods-order/src/main/java/com/tianji/mall/feign/PayFeignClient.java \
        mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java \
        mall-goods-order/src/main/java/com/tianji/mall/feign/AiChatFeignClient.java \
        mall-goods-order/src/test/java/com/tianji/mall/feign/fallback/
git commit -m "feat(mall-goods-order): add Feign fallbacks for Pay/User/AiChat clients"
```

---

### 任务 3：pay-service — Feign Fallback

**文件：**
- 创建：`pay-service/src/main/java/com/tianji/pay/feign/fallback/OrderFeignClientFallback.java`
- 修改：`pay-service/src/main/java/com/tianji/pay/feign/OrderFeignClient.java`
- 测试：`pay-service/src/test/java/com/tianji/pay/feign/fallback/OrderFeignClientFallbackTest.java`

- [ ] **步骤 1：创建 OrderFeignClientFallback**

```java
package com.tianji.pay.feign.fallback;

import com.tianji.common.result.R;
import com.tianji.pay.dto.OrderDTO;
import com.tianji.pay.feign.OrderFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderFeignClientFallback implements FallbackFactory<OrderFeignClient> {

    @Override
    public OrderFeignClient create(Throwable cause) {
        log.error("OrderFeignClient 调用失败，触发降级", cause);
        return new OrderFeignClient() {
            @Override
            public R<OrderDTO> getOrder(Long id) {
                return R.error(500, "订单服务暂不可用");
            }

            @Override
            public R<Void> payOrder(Long id, Long userId) {
                return R.error(500, "订单服务暂不可用");
            }
        };
    }
}
```

- [ ] **步骤 2：修改 OrderFeignClient — 添加 fallbackFactory**

修改 `pay-service/src/main/java/com/tianji/pay/feign/OrderFeignClient.java` 第 11 行：

```java
@FeignClient(name = "mall-goods-order", fallbackFactory = OrderFeignClientFallback.class)
```

- [ ] **步骤 3：编写 OrderFeignClientFallbackTest**

```java
package com.tianji.pay.feign.fallback;

import com.tianji.common.result.R;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderFeignClientFallbackTest {

    @Test
    void shouldReturnErrorOnGetOrderFailure() {
        OrderFeignClientFallback fallback = new OrderFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        R<?> result = client.getOrder(1L);
        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("暂不可用"));
    }

    @Test
    void shouldReturnErrorOnPayOrderFailure() {
        OrderFeignClientFallback fallback = new OrderFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        R<?> result = client.payOrder(1L, 1L);
        assertEquals(500, result.getCode());
    }
}
```

- [ ] **步骤 4：运行 pay-service 测试验证**

运行：`mvn test -pl pay-service`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add pay-service/
git commit -m "feat(pay-service): add Feign fallback for OrderFeignClient"
```

---

### 任务 4：ai-chat-service — Feign Fallbacks

**文件：**
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/feign/fallback/McpFeignClientFallback.java`
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/feign/fallback/ProductFeignClientFallback.java`
- 修改：`ai-chat-service/src/main/java/com/tianji/aichat/feign/McpFeignClient.java`
- 修改：`ai-chat-service/src/main/java/com/tianji/aichat/feign/ProductFeignClient.java`
- 测试：`ai-chat-service/src/test/java/com/tianji/aichat/feign/fallback/McpFeignClientFallbackTest.java`

- [ ] **步骤 1：创建 McpFeignClientFallback**

```java
package com.tianji.aichat.feign.fallback;

import com.tianji.aichat.feign.McpFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class McpFeignClientFallback implements FallbackFactory<McpFeignClient> {

    @Override
    public McpFeignClient create(Throwable cause) {
        log.error("McpFeignClient 调用失败，触发降级", cause);
        return request -> {
            log.warn("工具调用降级: tool={}", request.get("tool"));
            return Map.of("error", "工具服务暂不可用，请稍后重试");
        };
    }
}
```

- [ ] **步骤 2：创建 ProductFeignClientFallback**

```java
package com.tianji.aichat.feign.fallback;

import com.tianji.aichat.feign.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ProductFeignClientFallback implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("ProductFeignClient 调用失败，触发降级", cause);
        return ids -> {
            log.warn("商品批量查询降级: count={}", ids != null ? ids.size() : 0);
            return Collections.emptyMap();
        };
    }
}
```

- [ ] **步骤 3：修改 McpFeignClient — 添加 fallbackFactory**

修改 `ai-chat-service/src/main/java/com/tianji/aichat/feign/McpFeignClient.java` 第 9 行：

```java
@FeignClient(name = "mcp-server", fallbackFactory = McpFeignClientFallback.class)
```

- [ ] **步骤 4：修改 ProductFeignClient — 添加 fallbackFactory**

修改 `ai-chat-service/src/main/java/com/tianji/aichat/feign/ProductFeignClient.java` 第 10 行：

```java
@FeignClient(name = "mall-goods-order", fallbackFactory = ProductFeignClientFallback.class)
```

- [ ] **步骤 5：编写 McpFeignClientFallbackTest**

```java
package com.tianji.aichat.feign.fallback;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpFeignClientFallbackTest {

    @Test
    void shouldReturnErrorMapOnFailure() {
        McpFeignClientFallback fallback = new McpFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        Map<String, Object> result = client.executeTool(Map.of("tool", "search_products"));
        assertTrue(result.containsKey("error"));
        assertTrue(result.get("error").toString().contains("暂不可用"));
    }
}
```

- [ ] **步骤 6：运行 ai-chat-service 测试验证**

运行：`mvn test -pl ai-chat-service`
预期：PASS

- [ ] **步骤 7：Commit**

```bash
git add ai-chat-service/
git commit -m "feat(ai-chat-service): add Feign fallbacks for Mcp/Product clients"
```

---

### 任务 5：mcp-server — Feign Fallback

**文件：**
- 创建：`mcp-server/src/main/java/com/tianji/mcp/feign/fallback/MallFeignClientFallback.java`
- 修改：`mcp-server/src/main/java/com/tianji/mcp/feign/MallFeignClient.java`
- 测试：`mcp-server/src/test/java/com/tianji/mcp/feign/fallback/MallFeignClientFallbackTest.java`

- [ ] **步骤 1：创建 MallFeignClientFallback**

```java
package com.tianji.mcp.feign.fallback;

import com.tianji.mcp.feign.MallFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class MallFeignClientFallback implements FallbackFactory<MallFeignClient> {

    @Override
    public MallFeignClient create(Throwable cause) {
        log.error("MallFeignClient 调用失败，触发降级", cause);
        return new MallFeignClient() {
            @Override
            public Map<String, Object> searchProducts(Map<String, Object> params) {
                return Map.of("records", Collections.emptyList(), "total", 0);
            }

            @Override
            public Map<String, Object> getProduct(Long id) {
                return Map.of("error", "商品服务暂不可用");
            }

            @Override
            public Map<String, Object> getOrderList(Long userId) {
                return Map.of("error", "订单服务暂不可用");
            }

            @Override
            public Map<String, Object> getOrder(Long id) {
                return Map.of("error", "订单服务暂不可用");
            }

            @Override
            public Map<String, Object> getCartList(Long userId) {
                return Map.of("error", "购物车服务暂不可用");
            }

            @Override
            public Map<String, Object> addToCart(Map<String, Object> body) {
                return Map.of("error", "购物车服务暂不可用");
            }

            @Override
            public Map<String, Object> createOrder(Long userId, Map<String, Object> body) {
                return Map.of("error", "订单服务暂不可用");
            }

            @Override
            public Map<String, Object> payOrder(Long id, Long userId) {
                return Map.of("error", "订单服务暂不可用");
            }
        };
    }
}
```

- [ ] **步骤 2：修改 MallFeignClient — 添加 fallbackFactory**

修改 `mcp-server/src/main/java/com/tianji/mcp/feign/MallFeignClient.java` 第 8 行：

```java
@FeignClient(name = "mall-goods-order", fallbackFactory = MallFeignClientFallback.class)
```

- [ ] **步骤 3：编写 MallFeignClientFallbackTest**

```java
package com.tianji.mcp.feign.fallback;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MallFeignClientFallbackTest {

    @Test
    void shouldReturnErrorOnGetProductFailure() {
        MallFeignClientFallback fallback = new MallFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        Map<String, Object> result = client.getProduct(1L);
        assertTrue(result.containsKey("error"));
    }

    @Test
    void shouldReturnEmptyRecordsOnSearchFailure() {
        MallFeignClientFallback fallback = new MallFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        Map<String, Object> result = client.searchProducts(Map.of("keyword", "test"));
        assertEquals(0, result.get("total"));
        assertEquals(Collections.emptyList(), result.get("records"));
    }
}
```

- [ ] **步骤 4：运行 mcp-server 测试验证**

运行：`mvn test -pl mcp-server`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add mcp-server/
git commit -m "feat(mcp-server): add Feign fallback for MallFeignClient"
```

---

### 任务 6：RocketMQ 消费者 — 异常传播 + 重试配置

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/consumer/OrderEventConsumer.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/consumer/OrderTimeoutConsumer.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/consumer/NotificationConsumer.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/consumer/GroupBuyTimeoutConsumer.java`
- 修改：`mall-goods-order/src/main/resources/application.yml`
- 测试：新增/修改消费者测试

- [ ] **步骤 1：修改 OrderEventConsumer — 移除吞异常 + 添加重试配置**

修改 `mall-goods-order/src/main/java/com/tianji/mall/consumer/OrderEventConsumer.java`：
- `@RocketMQMessageListener` 添加 `maxReconsumeTimes = 3`
- `onMessage` 中 `CANCELLED` 和 `PAID` 分支移除 try-catch，让异常传播

```java
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-event-consumer",
        selectorExpression = "*",
        maxReconsumeTimes = 3)
public class OrderEventConsumer implements RocketMQListener<OrderEvent> {
    // ... fields unchanged ...

    @Override
    public void onMessage(OrderEvent event) {
        log.info("收到订单事件: orderId={}, orderNo={}, eventType={}, amount={}",
                event.getOrderId(), event.getOrderNo(),
                event.getEventType(), event.getTotalAmount());

        switch (event.getEventType()) {
            case "CREATED" -> log.info("订单已创建: orderId={}, orderNo={}, userId={}, amount={}",
                    event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
            case "PAID" -> handlePaid(event);
            case "CANCELLED" -> {
                log.info("订单已取消: orderId={}, orderNo={}, userId={}, amount={}",
                        event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
                couponService.restoreCoupon(event.getOrderId());  // 移除 try-catch
            }
            default -> log.warn("未知事件类型: {}", event.getEventType());
        }
    }

    private void handlePaid(OrderEvent event) {
        log.info("订单已支付 - 更新商品销量: orderId={}", event.getOrderId());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, event.getOrderId()));
        for (OrderItem item : items) {
            productService.incrementSales(item.getProductId(), item.getQuantity());
        }
        log.info("商品销量更新完成: orderId={}, items={}", event.getOrderId(), items.size());
    }
}
```

- [ ] **步骤 2：修改 OrderTimeoutConsumer — 异常不吞（原本就没有 try-catch，仅添加 maxReconsumeTimes）**

修改 `mall-goods-order/src/main/java/com/tianji/mall/consumer/OrderTimeoutConsumer.java`：
- `@RocketMQMessageListener` 添加 `maxReconsumeTimes = 3`
- 订单不存在时抛异常（让 RocketMQ 重试，而非静默跳过）

```java
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-timeout-consumer",
        selectorExpression = "TIMEOUT_CHECK",
        maxReconsumeTimes = 3)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.parseLong(orderIdStr);
        log.info("超时检查: orderId={}", orderId);
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }
        if (order.getStatus() == 1) {
            orderService.cancelOrderByTimeout(orderId);
            log.info("订单超时自动取消: orderId={}", orderId);
        }
    }
}
```

- [ ] **步骤 3：修改 NotificationConsumer — 添加 maxReconsumeTimes（原有代码无 try-catch）**

修改参数即可：

```java
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "notification-consumer",
        selectorExpression = "*",
        maxReconsumeTimes = 3)
```

- [ ] **步骤 4：修改 GroupBuyTimeoutConsumer — 移除内层 try-catch + 添加 maxReconsumeTimes**

```java
@RocketMQMessageListener(topic = "group-buy-topic", consumerGroup = "group-buy-timeout-consumer",
        maxReconsumeTimes = 3)
public class GroupBuyTimeoutConsumer implements RocketMQListener<String> {
    // ... fields unchanged ...

    @Override
    public void onMessage(String groupOrderId) {
        Long id = Long.parseLong(groupOrderId);
        GroupBuyOrder gbo = groupBuyOrderMapper.selectById(id);
        if (gbo == null || !"OPEN".equals(gbo.getStatus())) return;
        if (gbo.getExpireTime().isBefore(LocalDateTime.now())) {
            gbo.setStatus("FAIL");
            groupBuyOrderMapper.updateById(gbo);
            log.info("拼团超时失败: groupId={}", gbo.getGroupId());

            List<GroupBuyParticipant> participants = participantMapper.selectByGroupBuyOrderId(id);
            for (GroupBuyParticipant p : participants) {
                orderService.cancelOrderByTimeout(p.getOrderId());  // 移除 try-catch
                log.info("拼团超时取消订单: orderId={}, gboId={}", p.getOrderId(), id);
            }
        }
    }
}
```

- [ ] **步骤 5：添加 RocketMQ consumer 配置到 application.yml**

在 `mall-goods-order/src/main/resources/application.yml` 的 `rocketmq` 块中添加：

```yaml
rocketmq:
  name-server: 192.168.150.11:9876
  producer:
    group: mall-goods-order-producer
  consumer:
    max-reconsume-times: 3
    delay-level-when-next-consume: 3
```

- [ ] **步骤 6：运行 mall-goods-order 测试验证**

运行：`mvn test -pl mall-goods-order`
注意：消费者测试可能涉及 `@MockBean RocketMQTemplate`，需确认不需要新增 mock。

预期：PASS

- [ ] **步骤 7：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/consumer/ \
        mall-goods-order/src/main/resources/application.yml
git commit -m "feat(mall-goods-order): propagate exceptions in RocketMQ consumers, add retry config"
```

---

### 任务 7：全模块 — application.yml Feign 配置

**文件：**
- 修改：`mall-goods-order/src/main/resources/application.yml`
- 修改：`pay-service/src/main/resources/application.yml`
- 修改：`mcp-server/src/main/resources/application.yml`
- 修改：`ai-chat-service/src/main/resources/application.yml`

- [ ] **步骤 1：在 4 个模块的 application.yml 中添加 Feign 全局配置**

在 `spring.cloud` 下添加（4 个模块内容相同）：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 3000
            readTimeout: 10000
      compression:
        request:
          enabled: true
        response:
          enabled: true
      sentinel:
        enabled: true
```

每个模块的插入位置：`spring.cloud.sentinel.datasource` 块之后。

- [ ] **步骤 2：全量编译验证**

运行：`mvn compile`
预期：SUCCESS

- [ ] **步骤 3：Commit**

```bash
git add mall-goods-order/src/main/resources/application.yml \
        pay-service/src/main/resources/application.yml \
        mcp-server/src/main/resources/application.yml \
        ai-chat-service/src/main/resources/application.yml
git commit -m "feat: add Feign timeout (3s/10s), Sentinel circuit breaker, and compression config"
```

---

### 任务 8：Gateway — application.yml httpclient + retry

**文件：**
- 修改：`gateway/src/main/resources/application.yml`

- [ ] **步骤 1：添加 Gateway httpclient + retry 配置**

在 `gateway/src/main/resources/application.yml` 的 `spring.cloud.gateway` 块中添加：

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 3000
        response-timeout: 30000
        pool:
          type: elastic
          max-idle-time: 60s
          max-life-time: 300s
      default-filters:
        - name: Retry
          args:
            retries: 1
            statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT
            methods: GET
      discovery:
        locator:
          enabled: false
      routes:
        # ... 现有 22 条路由保持不变 ...
```

注意：`spring.cloud.gateway.httpclient` 和 `default-filters` 应插入在 `routes` 之前、`discovery` 之后。

- [ ] **步骤 2：运行 gateway 测试验证**

运行：`mvn test -pl gateway`
预期：PASS（gateway 测试为纯 Mockito，不加载 Spring Context）

- [ ] **步骤 3：Commit**

```bash
git add gateway/src/main/resources/application.yml
git commit -m "feat(gateway): add HTTP client timeout (3s/30s), connection pool, and retry for GET on 5xx"
```

---

### 任务 9：mall-goods-order — Redis 连接池

**文件：**
- 修改：`mall-goods-order/src/main/resources/application.yml`

- [ ] **步骤 1：添加 Lettuce 连接池配置**

在 `mall-goods-order/src/main/resources/application.yml` 的 `spring.data` 块中添加：

```yaml
spring:
  data:
    redis:
      host: 192.168.150.11
      port: 6379
      password: ${REDIS_PASSWORD:}
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: 2000ms
          time-between-eviction-runs: 30s
    cache:
      type: redis
      redis:
        time-to-live: 30m
        cache-null-values: false
```

注意：在已有 `spring.data.redis.host` 和 `spring.data.redis.port` 下方追加 `password` 和 `lettuce.pool` 配置。

- [ ] **步骤 2：验证依赖**

确认 `mall-goods-order/pom.xml` 已有 `commons-pool2`（Lettuce 连接池必需）：
```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```
（已知项目已有此依赖）

- [ ] **步骤 3：运行 mall-goods-order 测试验证**

运行：`mvn test -pl mall-goods-order`
注意：测试中 `RedisAutoConfiguration` 已被排除，连接池配置不影响测试。
预期：PASS

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/resources/application.yml
git commit -m "feat(mall-goods-order): add Redis password and Lettuce connection pool config"
```

---

### 任务 10：全量回归测试

- [ ] **步骤 1：运行全量测试**

```bash
mvn test
```

预期：全部 596 现有测试 + 新增测试全部 PASS，BUILD SUCCESS。

- [ ] **步骤 2：检查测试数量**

预期：596 + 新建 Fallback 测试（~8-10 个新测试），总计约 604-606 个测试。

- [ ] **步骤 3：Commit 最终确认（如有遗漏的临时变更）**

```bash
git status
git add -A
git commit -m "chore: final test verification after resilience hardening"  # 如无遗漏则跳过
```

---

## 任务依赖关系

```
任务 1 (tianji-common 基础类)
  └→ 任务 2-5 (所有 Fallback)  [可并行]
       └→ 任务 6 (RocketMQ consumer)
            └→ 任务 7 (Feign YAML 配置) + 任务 8 (Gateway YAML) + 任务 9 (Redis pool)  [可并行]
                 └→ 任务 10 (全量回归)
```

## 变更汇总

| 模块 | 新增文件 | 修改文件 | 测试文件 |
|------|---------|---------|---------|
| tianji-common | 3 | 0 | 2 |
| mall-goods-order | 3 | 8 | 1 |
| pay-service | 1 | 2 | 1 |
| mcp-server | 1 | 2 | 1 |
| ai-chat-service | 2 | 3 | 1 |
| gateway | 0 | 1 | 0 |
| **合计** | **10** | **16** | **6** |
