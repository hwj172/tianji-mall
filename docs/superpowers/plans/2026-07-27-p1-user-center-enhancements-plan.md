# P1 用户中心 + 待评价列表 + 搜索热词 实现计划

> **面向 AI 代理的工作者：** 使用此计划逐任务实现。步骤使用复选框（`- [ ]`）语法。

**目标：** 实现用户中心聚合 API、待评价商品列表、搜索热词三个 P1 功能

**架构：** 全部在 mall-goods-order 模块内实现，用户信息通过 OpenFeign 从 user-service 获取。搜索热词通过新增 search_log 表记录。

**技术栈：** Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + OpenFeign + H2 (test)

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `user-service/.../controller/UserController.java` | 修改 | 加 `GET /api/user/internal/{id}` 内部端点 |
| `mall-goods-order/.../feign/UserFeignClient.java` | 修改 | 加 `getUserById` Feign 方法 |
| `mall-goods-order/.../mapper/OrderMapper.java` | 修改 | 加 `selectOrderStats` |
| `mall-goods-order/.../mapper/UserCouponMapper.java` | 修改 | 加 `selectCountByUserId` |
| `mall-goods-order/.../mapper/FavoriteMapper.java` | 修改 | 加 `selectCountByUserId` |
| `mall-goods-order/.../mapper/CartItemMapper.java` | 修改 | 加 `selectCountByUserId` |
| `mall-goods-order/.../mapper/BrowsingHistoryMapper.java` | 修改 | 加 `selectCountByUserId` |
| `mall-goods-order/.../mapper/ShopFollowMapper.java` | 修改 | 加 `selectCountByUserId` |
| `mall-goods-order/.../controller/UserCenterController.java` | 新建 | 聚合端点 `GET /api/user/center` |
| `gateway/.../resources/application.yml` | 修改 | 加 `/api/user/center` 路由到 mall-goods-order（必须在 `/api/user/**` 之前） |
| `mall-goods-order/.../mapper/ReviewMapper.java` | 修改 | 加 `selectPendingReviews` |
| `mall-goods-order/.../service/ReviewService.java` | 修改 | 加 `getPendingReviews` |
| `mall-goods-order/.../controller/ReviewController.java` | 修改 | 加 `GET /api/review/pending` |
| `mall-goods-order/.../dto/PendingReviewResponse.java` | 新建 | 待评价响应 DTO |
| `mall-goods-order/.../entity/SearchLog.java` | 新建 | 搜索日志实体 |
| `mall-goods-order/.../mapper/SearchLogMapper.java` | 新建 | 搜索日志 Mapper |
| `mall-goods-order/.../service/ProductService.java` | 修改 | 搜索时记录日志 |
| `mall-goods-order/.../controller/ProductController.java` | 修改 | 加热词端点 |
| `gateway/.../filter/AuthGlobalFilter.java` | 修改 | 白名单加 `/api/product/search/hot` |
| `mall-goods-order/src/test/resources/schema.sql` | 修改 | 加 search_log DDL |
| `sql/init.sql` | 修改 | 加 search_log DDL |

---

### 任务 1：基础设施 — SearchLog 实体 + Mapper + DDL + PendingReviewResponse DTO

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/entity/SearchLog.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/SearchLogMapper.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/PendingReviewResponse.java`
- 修改：`mall-goods-order/src/test/resources/schema.sql`
- 修改：`sql/init.sql`

- [ ] **步骤 1：创建 SearchLog 实体**

```java
package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("search_log")
public class SearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private Long userId;
    private LocalDateTime createTime;
}
```

- [ ] **步骤 2：创建 SearchLogMapper**

```java
package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.SearchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    @Select("SELECT keyword, COUNT(*) AS count FROM search_log " +
            "WHERE create_time > DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "GROUP BY keyword ORDER BY count DESC LIMIT 10")
    List<java.util.Map<String, Object>> selectHotKeywords();
}
```

- [ ] **步骤 3：创建 PendingReviewResponse DTO**

```java
package com.tianji.mall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PendingReviewResponse {
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal price;
    private Long skuId;
    private String skuSpecs;
    private LocalDateTime orderCreateTime;
}
```

- [ ] **步骤 4：schema.sql 加 search_log 表**

在 `mall-goods-order/src/test/resources/schema.sql` 末尾加：

```sql
CREATE TABLE IF NOT EXISTS search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(128) NOT NULL,
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_time (create_time)
);
```

- [ ] **步骤 5：sql/init.sql 加 search_log 表**

在 `sql/init.sql` 末尾加（同步骤 4 的 DDL，去掉 `IF NOT EXISTS` 的 MySQL 原生语法即可）：

```sql
CREATE TABLE IF NOT EXISTS search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(128) NOT NULL,
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **步骤 6：验证编译通过**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS

- [ ] **步骤 7：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/entity/SearchLog.java \
        mall-goods-order/src/main/java/com/tianji/mall/mapper/SearchLogMapper.java \
        mall-goods-order/src/main/java/com/tianji/mall/dto/PendingReviewResponse.java \
        mall-goods-order/src/test/resources/schema.sql \
        sql/init.sql
git commit -m "feat: add SearchLog entity/mapper and PendingReviewResponse DTO"
```

---

### 任务 2：user-service 内部端点 + Feign 客户端

**文件：**
- 修改：`user-service/src/main/java/com/tianji/user/controller/UserController.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java`

- [ ] **步骤 1：user-service 加 `GET /api/user/internal/{id}`**

在 UserController 的 `// ===== 内部端点 =====` 区域加（`promoteToSeller` 之后）：

```java
@GetMapping("/internal/{id}")
public R<Map<String, Object>> getUserById(@PathVariable("id") Long id) {
    User user = userService.getUserById(id);
    Map<String, Object> map = new java.util.HashMap<>();
    map.put("id", user.getId());
    map.put("username", user.getUsername());
    map.put("nickname", user.getNickname());
    map.put("avatar", user.getAvatar());
    map.put("phone", user.getPhone());
    map.put("role", user.getRole());
    return R.ok(map);
}
```

注意：需要添加 `import java.util.Map;` 和 `import java.util.HashMap;`（二者已存在 `HashMap` 和 `Map` import）。

- [ ] **步骤 2：UserFeignClient 加 getUserById**

```java
@GetMapping("/api/user/internal/{id}")
R<java.util.Map<String, Object>> getUserById(@PathVariable("id") Long id);
```

- [ ] **步骤 3：验证 user-service 编译**

```bash
mvn compile -pl user-service
```

预期：BUILD SUCCESS

- [ ] **步骤 4：验证 mall-goods-order 编译**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add user-service/src/main/java/com/tianji/user/controller/UserController.java \
        mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java
git commit -m "feat: add user-service internal endpoint GET /api/user/internal/{id} and Feign client"
```

---

### 任务 3：Mapper 计数方法 — 5 个 Mapper 加 @Select

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/UserCouponMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/FavoriteMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/CartItemMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/BrowsingHistoryMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/ShopFollowMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/OrderMapper.java`

- [ ] **步骤 1：UserCouponMapper 加 selectCountByUserId**

在原有方法后添加：

```java
@Select("SELECT COUNT(*) FROM user_coupon WHERE user_id = #{userId} AND status = 'UNUSED'")
long selectCountByUserId(@Param("userId") Long userId);
```

需要添加 import：`import org.apache.ibatis.annotations.Select;`

- [ ] **步骤 2：FavoriteMapper 加 selectCountByUserId**

```java
@Select("SELECT COUNT(*) FROM favorite WHERE user_id = #{userId}")
long selectCountByUserId(@Param("userId") Long userId);
```

需要添加 import：`import org.apache.ibatis.annotations.Param;` 和 `import org.apache.ibatis.annotations.Select;`

- [ ] **步骤 3：CartItemMapper 加 selectCountByUserId**

```java
@Select("SELECT COUNT(*) FROM cart_item WHERE user_id = #{userId}")
long selectCountByUserId(@Param("userId") Long userId);
```

需要添加 import：`import org.apache.ibatis.annotations.Param;` 和 `import org.apache.ibatis.annotations.Select;`

- [ ] **步骤 4：BrowsingHistoryMapper 加 selectCountByUserId**

在原有 `clearAll` 方法后添加：

```java
@Select("SELECT COUNT(*) FROM browsing_history WHERE user_id = #{userId}")
long selectCountByUserId(@Param("userId") Long userId);
```

`@Select` 和 `@Param` import 已存在。

- [ ] **步骤 5：ShopFollowMapper 加 selectCountByUserId**

```java
@Select("SELECT COUNT(*) FROM shop_follow WHERE user_id = #{userId}")
long selectCountByUserId(@Param("userId") Long userId);
```

需要添加 import：`import org.apache.ibatis.annotations.Param;` 和 `import org.apache.ibatis.annotations.Select;`

- [ ] **步骤 6：OrderMapper 加 selectOrderStats**

在原有方法后添加：

```java
@Select("SELECT status, COUNT(*) AS cnt FROM `order` WHERE user_id = #{userId} GROUP BY status")
java.util.List<java.util.Map<String, Object>> selectOrderStats(@Param("userId") Long userId);
```

- [ ] **步骤 7：验证编译**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS

- [ ] **步骤 8：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/mapper/
git commit -m "feat: add selectCountByUserId/selectOrderStats methods to mappers for user center"
```

---

### 任务 4：UserCenterController — 用户中心聚合端点 + 测试

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/controller/UserCenterController.java`
- 创建：`mall-goods-order/src/test/java/com/tianji/mall/controller/UserCenterControllerTest.java`
- 修改：`gateway/src/main/resources/application.yml`

- [ ] **步骤 1：编写失败的测试**

新建 `UserCenterControllerTest.java`：

```java
package com.tianji.mall.controller;

import com.tianji.common.util.JwtUtil;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.*;
import com.tianji.mall.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserCenterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserCouponMapper userCouponMapper;

    @MockBean
    private FavoriteMapper favoriteMapper;

    @MockBean
    private CartItemMapper cartItemMapper;

    @MockBean
    private BrowsingHistoryMapper browsingHistoryMapper;

    @MockBean
    private ShopFollowMapper shopFollowMapper;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private UserFeignClient userFeignClient;

    // all @SpringBootTest classes need these MockBeans (from CLAUDE.md convention)
    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private com.tianji.mall.feign.PayFeignClient payFeignClient;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private SeckillService seckillService;

    @MockBean
    private GroupBuyService groupBuyService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ShopService shopService;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    private String authHeader() {
        return "Bearer " + jwtUtil.generateToken(1L, "testuser", "user");
    }

    @Test
    void shouldReturn401WithoutJwt() throws Exception {
        mockMvc.perform(get("/api/user/center"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

```bash
mvn test -pl mall-goods-order -Dtest=UserCenterControllerTest -DfailIfNoTests=false
```

预期：缺少 401 → `/api/user/center` 路由不存在，返回 401 未授权（被 Gateway Filter 的 Mock 拦截）。

- [ ] **步骤 3：创建 UserCenterController**

```java
package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserCenterController {

    private final JwtUtil jwtUtil;
    private final UserFeignClient userFeignClient;
    private final OrderMapper orderMapper;
    private final UserCouponMapper userCouponMapper;
    private final FavoriteMapper favoriteMapper;
    private final CartItemMapper cartItemMapper;
    private final BrowsingHistoryMapper browsingHistoryMapper;
    private final ShopFollowMapper shopFollowMapper;

    @GetMapping("/center")
    public R<Map<String, Object>> center(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));

        Map<String, Object> result = new LinkedHashMap<>();

        // 用户信息
        try {
            R<Map<String, Object>> userResp = userFeignClient.getUserById(userId);
            if (userResp != null && userResp.getData() != null) {
                result.put("user", userResp.getData());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}", userId, e);
        }

        // 订单统计
        Map<String, Long> orderStats = new LinkedHashMap<>();
        orderStats.put("pendingPayment", 0L);
        orderStats.put("pendingShip", 0L);
        orderStats.put("pendingReceive", 0L);
        orderStats.put("pendingReview", 0L);
        try {
            List<Map<String, Object>> stats = orderMapper.selectOrderStats(userId);
            if (stats != null) {
                for (Map<String, Object> row : stats) {
                    int status = ((Number) row.get("status")).intValue();
                    long cnt = ((Number) row.get("cnt")).longValue();
                    switch (status) {
                        case 1 -> orderStats.put("pendingPayment", cnt);
                        case 2 -> orderStats.put("pendingShip", cnt);
                        case 3 -> orderStats.put("pendingReceive", cnt);
                        case 4 -> orderStats.put("pendingReview", cnt);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取订单统计失败: userId={}", userId, e);
        }
        result.put("orderStats", orderStats);

        // 计数（best-effort）
        result.put("couponCount", safeCount(() -> userCouponMapper.selectCountByUserId(userId)));
        result.put("favoriteCount", safeCount(() -> favoriteMapper.selectCountByUserId(userId)));
        result.put("followShopCount", safeCount(() -> shopFollowMapper.selectCountByUserId(userId)));
        result.put("cartCount", safeCount(() -> cartItemMapper.selectCountByUserId(userId)));
        result.put("historyCount", safeCount(() -> browsingHistoryMapper.selectCountByUserId(userId)));

        return R.ok(result);
    }

    private long safeCount(java.util.function.Supplier<Long> counter) {
        try {
            Long val = counter.get();
            return val != null ? val : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
```

- [ ] **步骤 4：Gateway 加 `/api/user/center` 路由**

在 `gateway/src/main/resources/application.yml` 的路由列表中，`id: user-service` 之前插入：

```yaml
        - id: mall-user-center
          uri: lb://mall-goods-order
          predicates:
            - Path=/api/user/center
```

必须在 `/api/user/**` 路由之前，否则会被 user-service 路由吞掉。

- [ ] **步骤 5：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=UserCenterControllerTest -DfailIfNoTests=false
```

预期：`shouldReturn401WithoutJwt` PASS

- [ ] **步骤 5：扩展测试 — 正常返回**

在 UserCenterControllerTest 中加：

```java
import static org.mockito.Mockito.when;
import com.tianji.common.result.R;
import java.util.Map;
import java.util.List;

@Test
void shouldReturnUserCenterData() throws Exception {
    when(userCouponMapper.selectCountByUserId(1L)).thenReturn(3L);
    when(favoriteMapper.selectCountByUserId(1L)).thenReturn(12L);
    when(cartItemMapper.selectCountByUserId(1L)).thenReturn(8L);
    when(browsingHistoryMapper.selectCountByUserId(1L)).thenReturn(50L);
    when(shopFollowMapper.selectCountByUserId(1L)).thenReturn(2L);
    when(orderMapper.selectOrderStats(1L)).thenReturn(List.of(
            Map.of("status", 1, "cnt", 5L),
            Map.of("status", 2, "cnt", 2L),
            Map.of("status", 3, "cnt", 3L),
            Map.of("status", 4, "cnt", 1L)
    ));

    Map<String, Object> userData = new java.util.HashMap<>();
    userData.put("id", 1L);
    userData.put("username", "testuser");
    userData.put("avatar", "https://example.com/avatar.jpg");
    userData.put("role", "user");
    when(userFeignClient.getUserById(1L)).thenReturn(R.ok(userData));

    mockMvc.perform(get("/api/user/center")
                    .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.username").value("testuser"))
            .andExpect(jsonPath("$.data.orderStats.pendingPayment").value(5))
            .andExpect(jsonPath("$.data.orderStats.pendingShip").value(2))
            .andExpect(jsonPath("$.data.orderStats.pendingReceive").value(3))
            .andExpect(jsonPath("$.data.orderStats.pendingReview").value(1))
            .andExpect(jsonPath("$.data.couponCount").value(3))
            .andExpect(jsonPath("$.data.favoriteCount").value(12))
            .andExpect(jsonPath("$.data.cartCount").value(8))
            .andExpect(jsonPath("$.data.historyCount").value(50))
            .andExpect(jsonPath("$.data.followShopCount").value(2));
}
```

- [ ] **步骤 6：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=UserCenterControllerTest
```

预期：2 tests PASS

- [ ] **步骤 7：扩展测试 — user-service 降级**

```java
@Test
void shouldDegradeWhenUserServiceFails() throws Exception {
    when(userCouponMapper.selectCountByUserId(1L)).thenReturn(0L);
    when(favoriteMapper.selectCountByUserId(1L)).thenReturn(0L);
    when(cartItemMapper.selectCountByUserId(1L)).thenReturn(0L);
    when(browsingHistoryMapper.selectCountByUserId(1L)).thenReturn(0L);
    when(shopFollowMapper.selectCountByUserId(1L)).thenReturn(0L);
    when(orderMapper.selectOrderStats(1L)).thenReturn(List.of());
    when(userFeignClient.getUserById(1L)).thenThrow(new RuntimeException("user-service down"));

    mockMvc.perform(get("/api/user/center")
                    .header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user").doesNotExist())
            .andExpect(jsonPath("$.data.couponCount").value(0));
}
```

- [ ] **步骤 8：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=UserCenterControllerTest
```

预期：3 tests PASS

- [ ] **步骤 9：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/UserCenterController.java \
        mall-goods-order/src/test/java/com/tianji/mall/controller/UserCenterControllerTest.java
git commit -m "feat: add user center aggregation API (GET /api/user/center)"
```

---

### 任务 5：待评价列表 — Mapper + Service + Controller + 测试

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/ReviewMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ReviewService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/ReviewController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/service/ReviewServiceTest.java`

- [ ] **步骤 1：ReviewMapper 加 selectPendingReviews**

```java
@Select("SELECT oi.order_id, oi.product_id, oi.product_name, oi.price, oi.sku_id, oi.sku_specs, " +
        "p.images AS product_image, o.create_time AS order_create_time " +
        "FROM order_item oi " +
        "JOIN `order` o ON o.id = oi.order_id " +
        "LEFT JOIN product p ON p.id = oi.product_id " +
        "LEFT JOIN review r ON r.user_id = o.user_id AND r.order_id = o.id AND r.product_id = oi.product_id " +
        "WHERE o.user_id = #{userId} AND o.status = 4 AND r.id IS NULL " +
        "ORDER BY o.create_time DESC " +
        "LIMIT #{offset}, #{limit}")
java.util.List<java.util.Map<String, Object>> selectPendingReviews(@Param("userId") Long userId,
                                                                    @Param("offset") int offset,
                                                                    @Param("limit") int limit);
```

- [ ] **步骤 2：ReviewService 加 getPendingReviews**

```java
public List<PendingReviewResponse> getPendingReviews(Long userId, int page, int size) {
    int offset = (page - 1) * size;
    List<Map<String, Object>> rows = baseMapper.selectPendingReviews(userId, offset, size);
    return rows.stream().map(row -> {
        PendingReviewResponse resp = new PendingReviewResponse();
        resp.setOrderId(((Number) row.get("order_id")).longValue());
        resp.setProductId(((Number) row.get("product_id")).longValue());
        resp.setProductName((String) row.get("product_name"));
        resp.setProductImage((String) row.get("product_image"));
        resp.setPrice(new java.math.BigDecimal(row.get("price").toString()));
        // SKU 字段可能为 null
        Number skuId = (Number) row.get("sku_id");
        if (skuId != null) {
            resp.setSkuId(skuId.longValue());
        }
        resp.setSkuSpecs((String) row.get("sku_specs"));
        resp.setOrderCreateTime(((java.sql.Timestamp) row.get("order_create_time")).toLocalDateTime());
        return resp;
    }).toList();
}
```

需要添加 import：`import com.tianji.mall.dto.PendingReviewResponse;`、`import java.math.BigDecimal;`、`import java.sql.Timestamp;`

- [ ] **步骤 3：ReviewController 加 pending 端点**

```java
@GetMapping("/pending")
public R<List<PendingReviewResponse>> pendingReviews(@RequestHeader("Authorization") String authHeader,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
    Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    return R.ok(reviewService.getPendingReviews(userId, page, size));
}
```

需要添加 import：`import com.tianji.mall.dto.PendingReviewResponse;`

- [ ] **步骤 4：验证编译**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS

- [ ] **步骤 5：运行现有测试确认回归**

```bash
mvn test -pl mall-goods-order -Dtest=ReviewControllerTest
```

预期：所有已有测试 PASS

- [ ] **步骤 6：编写 ReviewServiceTest 单元测试**

修改 `ReviewServiceTest.java`（MockitoExtension）. 在已有 @Mock 字段后加：

```java
@Mock
private ReviewMapper reviewMapper;
```

在 setUp 中修改构造函数：

```java
reviewService = new ReviewService(orderMapper, orderItemMapper);
ReflectionTestUtils.setField(reviewService, "baseMapper", reviewMapper);
```

添加测试方法：

```java
@Test
void shouldReturnPendingReviews() {
    List<Map<String, Object>> rows = List.of(
            Map.of("order_id", 100L, "product_id", 1L, "product_name", "iPhone 16",
                    "product_image", "img.jpg", "price", BigDecimal.valueOf(9999),
                    "sku_id", null, "sku_specs", null,
                    "order_create_time", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 7, 20, 10, 0, 0)))
    );
    when(reviewMapper.selectPendingReviews(1L, 0, 20)).thenReturn(rows);

    List<PendingReviewResponse> result = reviewService.getPendingReviews(1L, 1, 20);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getProductName()).isEqualTo("iPhone 16");
    assertThat(result.get(0).getOrderId()).isEqualTo(100L);
}

@Test
void shouldReturnEmptyWhenNoPendingReviews() {
    when(reviewMapper.selectPendingReviews(1L, 0, 20)).thenReturn(List.of());

    List<PendingReviewResponse> result = reviewService.getPendingReviews(1L, 1, 20);

    assertThat(result).isEmpty();
}

@Test
void shouldExcludeAlreadyReviewedItems() {
    // 该用户已评价所有已完订单的商品，selectPendingReviews 返回空
    when(reviewMapper.selectPendingReviews(1L, 0, 20)).thenReturn(List.of());

    List<PendingReviewResponse> result = reviewService.getPendingReviews(1L, 1, 20);

    assertThat(result).isEmpty();
    verify(reviewMapper).selectPendingReviews(1L, 0, 20);
}
```

需要添加 import：`import com.tianji.mall.dto.PendingReviewResponse;`、`import java.math.BigDecimal;`

- [ ] **步骤 7：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=ReviewServiceTest
```

预期：所有测试 PASS（新增 3 个 + 原有 3 个 = 6 个）

- [ ] **步骤 8：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/mapper/ReviewMapper.java \
        mall-goods-order/src/main/java/com/tianji/mall/service/ReviewService.java \
        mall-goods-order/src/main/java/com/tianji/mall/controller/ReviewController.java \
        mall-goods-order/src/test/java/com/tianji/mall/service/ReviewServiceTest.java
git commit -m "feat: add pending reviews list (GET /api/review/pending)"
```

---

### 任务 6：搜索热词 — 日志记录 + 端点 + 测试

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java`
- 修改：`gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java`

- [ ] **步骤 1：ProductService 加搜索日志记录**

在 ProductService 中加 `SearchLogMapper` 字段：

修改 `@RequiredArgsConstructor` 构造函数（已有 lombok，自动处理 final 字段），添加：

```java
private final SearchLogMapper searchLogMapper;
```

在 `getProductPage` 方法中，keyword 非空时异步记录日志（第 61 行 `if (StringUtils.hasText(keyword))` 内）：

在 `if (StringUtils.hasText(keyword))` 块末尾（第 68 行 `}` 之后）加：

```java
// 异步记录搜索日志（best-effort，不阻塞搜索）
try {
    com.tianji.mall.entity.SearchLog log = new com.tianji.mall.entity.SearchLog();
    log.setKeyword(keyword);
    // userId 无法从 getProductPage 参数获取（这是公开端点），设 null
    searchLogMapper.insert(log);
} catch (Exception e) {
    // ignore — 搜索日志记录失败不影响搜索功能
}
```

- [ ] **步骤 2：ProductController 加热词端点**

```java
@GetMapping("/search/hot")
public R<List<String>> hotKeywords() {
    List<Map<String, Object>> rows = productService.getHotKeywords();
    List<String> keywords = rows.stream()
            .map(r -> (String) r.get("keyword"))
            .toList();
    return R.ok(keywords);
}
```

需要添加 import：`import java.util.List;` 和 `import java.util.Map;`（已存在）

- [ ] **步骤 3：ProductService 加 getHotKeywords 方法**

```java
public List<Map<String, Object>> getHotKeywords() {
    return searchLogMapper.selectHotKeywords();
}
```

- [ ] **步骤 4：Gateway 白名单加 `/api/product/search/hot`**

在 `AuthGlobalFilter.java` 的 `PUBLIC_PATHS` 中添加 `"/api/product/search/hot"`：

```java
private static final List<String> PUBLIC_PATHS = List.of(
        "/api/user/login",
        "/api/user/register",
        "/api/product",
        "/api/region",
        "/api/shop",
        "/api/group-buy",
        "/api/home",
        "/api/product/search/hot",
        "/api/pay/notify"
);
```

- [ ] **步骤 5：验证编译**

```bash
mvn compile -pl mall-goods-order,gateway
```

预期：BUILD SUCCESS

- [ ] **步骤 6：运行现有 ProductControllerTest 确认回归**

```bash
mvn test -pl mall-goods-order -Dtest=ProductControllerTest
```

预期：原有测试 PASS

- [ ] **步骤 7：ProductControllerTest 加搜索热词测试**

在 `ProductControllerTest.java` 中加 `@MockBean`：

```java
@MockBean
private SearchLogMapper searchLogMapper;
```

加测试方法：

```java
@Test
void shouldReturnHotKeywords() throws Exception {
    when(searchLogMapper.selectHotKeywords()).thenReturn(List.of(
            Map.of("keyword", "手机", "count", 100L),
            Map.of("keyword", "耳机", "count", 50L)
    ));

    mockMvc.perform(get("/api/product/search/hot"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("手机"))
            .andExpect(jsonPath("$.data[1]").value("耳机"));
}

@Test
void shouldReturnEmptyHotKeywords() throws Exception {
    when(searchLogMapper.selectHotKeywords()).thenReturn(List.of());

    mockMvc.perform(get("/api/product/search/hot"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
}
```

需要添加 import：`import com.tianji.mall.mapper.SearchLogMapper;`

- [ ] **步骤 8：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=ProductControllerTest
```

预期：所有测试 PASS（原有 + 新增 2 个）

- [ ] **步骤 9：运行 Gateway 测试确认白名单更新**

```bash
mvn test -pl gateway
```

预期：`AuthGlobalFilterTest` 可能需要更新白名单计数。检查测试是否通过。

- [ ] **步骤 10：如果需要，更新 AuthGlobalFilterTest**

如果 AuthGlobalFilterTest 中有对 PUBLIC_PATHS 数量的断言，更新对应的数字。

- [ ] **步骤 11：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java \
        mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java \
        mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java \
        gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java
git commit -m "feat: add search hot keywords (GET /api/product/search/hot) with search log recording"
```

---

### 任务 7：全量回归 + CLAUDE.md 更新

- [ ] **步骤 1：全量回归测试**

```bash
mvn test
```

预期：全部 ~477 tests PASS（原有 463 + 新增 ~14）

- [ ] **步骤 2：更新 CLAUDE.md**

在 CLAUDE.md 中添加如下约定：

````markdown
- **用户中心**：`UserCenterController`（`/api/user/center`，JWT 鉴权）聚合 userInfo + orderStats（按 status 分组 count）+ couponCount/favoriteCount/followShopCount/cartCount/historyCount。各子查询 best-effort（异常降级为 0），Feign 调用 user-service `GET /api/user/internal/{id}`。`UserCenterControllerTest` 3 个端点测试（@SpringBootTest）。
- **待评价列表**：`GET /api/review/pending`（JWT 鉴权，分页）查询已完成订单(status=4)中未评价商品（LEFT JOIN review 排除已评价）。`ReviewMapper.selectPendingReviews` @Select 查询。`ReviewServiceTest` 新增 3 个单元测试。
- **搜索热词**：`search_log` 表（keyword, userId, createTime）+ `SearchLogMapper`。搜索时异步记录日志（best-effort，不阻塞搜索）。`GET /api/product/search/hot`（公开端点）返回最近 7 天 Top 10 热词，网关白名单已放行。`ProductControllerTest` 新增 2 个端点测试。
- **搜索日志**：`SearchLog` entity + `SearchLogMapper`（BaseMapper + `selectHotKeywords` @Select）。`ProductService.getProductPage` 中 keyword 非空时记录（best-effort，异常仅 log.warn）。
- **UserFeignClient**：新增 `getUserById(Long id)` 调用 `GET /api/user/internal/{id}`。
- **user-service**：新增 `GET /api/user/internal/{id}` 返回用户信息 Map（id/username/nickname/avatar/phone/role，不含 password）。
- **5 个 Mapper 计数方法**：`UserCouponMapper/FavoriteMapper/CartItemMapper/BrowsingHistoryMapper/ShopFollowMapper` 各加 `selectCountByUserId(Long userId)` @Select。
- **PendingReviewResponse** DTO：orderId/productId/productName/productImage/price/skuId/skuSpecs/orderCreateTime。
````

- [ ] **步骤 3：Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with P1 user center, pending reviews, and search hot keywords conventions"
```

- [ ] **步骤 4：最终验证**

```bash
mvn test
```

确认最终全量测试全部 PASS。

---

## 测试清单

| 测试类 | 新增 | 覆盖 |
|--------|------|------|
| `UserCenterControllerTest` | 3 | 正常返回 / 缺 JWT / user-service 降级 |
| `ReviewServiceTest` | +3 | 有待评价 / 空列表 / 已全部评价排除 |
| `ProductControllerTest` | +2 | 热词 / 空热词 |
| AuthGlobalFilterTest | 0-1 | 可能需更新白名单计数 |
| 全量回归 | ~477 | 所有 7 模块 |
