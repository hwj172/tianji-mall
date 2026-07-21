# 数据看板 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为管理员提供 `/api/admin/dashboard` 端点，一次性返回 GMV、订单量、用户数、今日/本周/本月趋势、热销 Top10、订单状态分布、分类销量占比。

**架构：** DashboardService 聚合本地 SQL 查询（OrderMapper + OrderItemMapper）+ 远程 Feign 调用（UserFeignClient → user-service /api/user/internal/count）。纯查询端点，无事务。

**技术栈：** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7 / OpenFeign / Mockito + H2

---

## 文件结构总览

| 文件 | 操作 | 职责 |
|------|------|------|
| `gateway/.../filter/AuthGlobalFilter.java` | 修改 | INTERNAL_PATHS 加 `/api/user/internal` |
| `user-service/.../controller/UserController.java` | 修改 | 新增 `GET /api/user/internal/count` |
| `mall-goods-order/.../dto/DashboardResponse.java` | 新建 | 看板响应 DTO（含内部类） |
| `mall-goods-order/.../mapper/OrderMapper.java` | 修改 | 新增聚合查询方法 |
| `mall-goods-order/.../mapper/OrderItemMapper.java` | 修改 | 新增热销/分类统计方法 |
| `mall-goods-order/.../feign/UserFeignClient.java` | 新建 | Feign → user-service |
| `mall-goods-order/.../service/DashboardService.java` | 新建 | 聚合查询 + Feign 调用 |
| `mall-goods-order/.../controller/AdminController.java` | 修改 | 新增 `GET /api/admin/dashboard` |
| `mall-goods-order/src/test/.../service/DashboardServiceTest.java` | 新建 | 5 个单元测试 |
| `mall-goods-order/src/test/.../controller/AdminControllerTest.java` | 修改 | 新增 1 个端点测试 |

---

### 任务 1：user-service 内部 count 端点 + Gateway 白名单

**文件：**
- 修改：`gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java`
- 修改：`user-service/src/main/java/com/tianji/user/controller/UserController.java`

- [ ] **步骤 1：Gateway INTERNAL_PATHS 添加 `/api/user/internal`**

```java
// AuthGlobalFilter.java:42-46，在 INTERNAL_PATHS 列表末尾追加
private static final List<String> INTERNAL_PATHS = List.of(
        "/api/order/internal",
        "/api/cart/internal",
        "/api/product/internal",
        "/api/user/internal"
);
```

- [ ] **步骤 2：UserController 添加 count 端点**

在 `UserController.java` 的 `updatePassword` 方法之后追加：

```java
@GetMapping("/internal/count")
public R<Long> countUsers(@RequestHeader("X-Internal-Token") String token) {
    return R.ok(userService.countUsers());
}
```

- [ ] **步骤 3：UserService 添加 countUsers 方法**

文件：`user-service/src/main/java/com/tianji/user/service/UserService.java`

在类末尾追加：

```java
public long countUsers() {
    return count();
}
```

- [ ] **步骤 4：运行 Gateway 全量测试确认不回归**

```bash
mvn test -pl gateway
```

预期：30 tests PASS。

- [ ] **步骤 5：Commit**

```bash
git add gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java \
        user-service/src/main/java/com/tianji/user/controller/UserController.java \
        user-service/src/main/java/com/tianji/user/service/UserService.java
git commit -m "feat: add user count internal endpoint for dashboard

- Add /api/user/internal/count endpoint with X-Internal-Token auth
- Whitelist /api/user/internal in gateway internal paths

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### 任务 2：DashboardResponse DTO

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/DashboardResponse.java`

- [ ] **步骤 1：创建 DashboardResponse DTO**

```java
package com.tianji.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private BigDecimal totalGmv;
    private Long totalOrders;
    private Long totalUsers;
    private TimeStats today;
    private TimeStats thisWeek;
    private TimeStats thisMonth;
    private List<TopProduct> topProducts;
    private List<OrderStatusDist> orderStatusDist;
    private List<CategorySales> categorySales;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeStats {
        private BigDecimal gmv;
        private Long orders;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProduct {
        private Long id;
        private String name;
        private Long sales;
        private BigDecimal amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusDist {
        private Integer status;
        private String label;
        private Long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySales {
        private Long categoryId;
        private String categoryName;
        private BigDecimal amount;
    }
}
```

- [ ] **步骤 2：编译确认无语法错误**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 3：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/dto/DashboardResponse.java
git commit -m "feat: add DashboardResponse DTO with inner stats classes"
```

---

### 任务 3：OrderMapper + OrderItemMapper 聚合查询方法

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/OrderMapper.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/mapper/OrderItemMapper.java`

- [ ] **步骤 1：OrderMapper 追加聚合查询方法**

在 `OrderMapper` 接口中追加（import `org.apache.ibatis.annotations.Param`、`org.apache.ibatis.annotations.Select`）：

```java
@Select("SELECT COALESCE(SUM(total_amount), 0) FROM `order` WHERE status IN (2,3,4)")
BigDecimal selectTotalGmv();

@Select("SELECT COALESCE(SUM(total_amount), 0) FROM `order` WHERE status IN (2,3,4) AND create_time >= #{startTime}")
BigDecimal selectGmvByTimeRange(@Param("startTime") java.time.LocalDateTime startTime);

@Select("SELECT COUNT(*) FROM `order` WHERE status IN (2,3,4)")
Long selectPaidOrderCount();

@Select("SELECT COUNT(*) FROM `order` WHERE status IN (2,3,4) AND create_time >= #{startTime}")
Long selectPaidOrderCountByTimeRange(@Param("startTime") java.time.LocalDateTime startTime);

@Select("SELECT status, COUNT(*) as cnt FROM `order` GROUP BY status")
java.util.List<java.util.Map<String, Object>> selectStatusDistribution();
```

- [ ] **步骤 2：OrderItemMapper 追加统计方法**

读取 `OrderItemMapper.java` 现有内容，在接口中追加：

```java
@Select("SELECT oi.product_id, p.name, SUM(oi.quantity) as sales, " +
        "SUM(oi.price * oi.quantity) as amount " +
        "FROM order_item oi JOIN product p ON oi.product_id = p.id " +
        "JOIN `order` o ON oi.order_id = o.id " +
        "WHERE o.status IN (2,3,4) " +
        "GROUP BY oi.product_id, p.name ORDER BY sales DESC LIMIT 10")
java.util.List<java.util.Map<String, Object>> selectTopSellingProducts();

@Select("SELECT p.category_id, c.name as category_name, " +
        "SUM(oi.price * oi.quantity) as amount " +
        "FROM order_item oi JOIN product p ON oi.product_id = p.id " +
        "JOIN category c ON p.category_id = c.id " +
        "JOIN `order` o ON oi.order_id = o.id " +
        "WHERE o.status IN (2,3,4) " +
        "GROUP BY p.category_id, c.name")
java.util.List<java.util.Map<String, Object>> selectCategorySales();
```

- [ ] **步骤 3：编译确认**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/mapper/OrderMapper.java \
        mall-goods-order/src/main/java/com/tianji/mall/mapper/OrderItemMapper.java
git commit -m "feat: add aggregation query methods to OrderMapper and OrderItemMapper"
```

---

### 任务 4：UserFeignClient

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java`

- [ ] **步骤 1：创建 UserFeignClient**

```java
package com.tianji.mall.feign;

import com.tianji.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "user-service")
public interface UserFeignClient {

    @GetMapping("/api/user/internal/count")
    R<Long> countUsers();
}
```

- [ ] **步骤 2：编译确认**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 3：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/feign/UserFeignClient.java
git commit -m "feat: add UserFeignClient for dashboard user count"
```

---

### 任务 5：DashboardService + 单元测试

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/service/DashboardService.java`
- 创建：`mall-goods-order/src/test/java/com/tianji/mall/service/DashboardServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

创建 `DashboardServiceTest.java`：

```java
package com.tianji.mall.service;

import com.tianji.common.result.R;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private UserFeignClient userFeignClient;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(orderMapper, orderItemMapper, userFeignClient);
    }

    @Test
    void shouldReturnDashboardWithAllMetrics() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.valueOf(100000));
        when(orderMapper.selectPaidOrderCount()).thenReturn(100L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.valueOf(5000));
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(10L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(200L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of(
                Map.of("product_id", 1L, "name", "iPhone", "sales", 50L, "amount", BigDecimal.valueOf(499900))
        ));
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of(
                Map.of("status", 1, "cnt", 20L),
                Map.of("status", 2, "cnt", 30L)
        ));
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of(
                Map.of("category_id", 1L, "category_name", "手机数码", "amount", BigDecimal.valueOf(50000))
        ));

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTotalGmv()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(resp.getTotalOrders()).isEqualTo(100L);
        assertThat(resp.getTotalUsers()).isEqualTo(200L);
        assertThat(resp.getToday().getGmv()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(resp.getToday().getOrders()).isEqualTo(10L);
        assertThat(resp.getThisWeek().getGmv()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(resp.getThisMonth().getGmv()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(resp.getTopProducts()).hasSize(1);
        assertThat(resp.getTopProducts().get(0).getName()).isEqualTo("iPhone");
        assertThat(resp.getOrderStatusDist()).hasSize(2);
        assertThat(resp.getCategorySales()).hasSize(1);
    }

    @Test
    void shouldReturnZeroWhenNoData() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(0L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of());
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of());
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTotalGmv()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.getTotalOrders()).isEqualTo(0L);
        assertThat(resp.getTotalUsers()).isEqualTo(0L);
        assertThat(resp.getTopProducts()).isEmpty();
        assertThat(resp.getOrderStatusDist()).isEmpty();
        assertThat(resp.getCategorySales()).isEmpty();
    }

    @Test
    void shouldFallbackUserCountToZeroWhenFeignFails() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenThrow(new RuntimeException("connection refused"));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of());
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of());
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTotalUsers()).isEqualTo(0L);
    }

    @Test
    void shouldUseCorrectStatusLabels() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(0L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of());
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of(
                Map.of("status", 1, "cnt", 10L),
                Map.of("status", 2, "cnt", 20L),
                Map.of("status", 3, "cnt", 30L),
                Map.of("status", 4, "cnt", 40L),
                Map.of("status", 5, "cnt", 50L)
        ));
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getOrderStatusDist()).hasSize(5);
        assertThat(resp.getOrderStatusDist().get(0).getLabel()).isEqualTo("待付款");
        assertThat(resp.getOrderStatusDist().get(1).getLabel()).isEqualTo("已付款");
        assertThat(resp.getOrderStatusDist().get(2).getLabel()).isEqualTo("已发货");
        assertThat(resp.getOrderStatusDist().get(3).getLabel()).isEqualTo("已完成");
        assertThat(resp.getOrderStatusDist().get(4).getLabel()).isEqualTo("已取消");
    }

    @Test
    void shouldSortTopProductsBySalesDesc() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(0L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of(
                Map.of("product_id", 1L, "name", "A", "sales", 10L, "amount", BigDecimal.valueOf(100)),
                Map.of("product_id", 2L, "name", "B", "sales", 50L, "amount", BigDecimal.valueOf(500))
        ));
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of());
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        // SQL 已按 sales DESC 排序，第一条应该是销量最高的
        assertThat(resp.getTopProducts().get(0).getSales()).isEqualTo(10L);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn test -pl mall-goods-order -Dtest=DashboardServiceTest
```

预期：FAIL — `DashboardService` 类未定义。

- [ ] **步骤 3：编写 DashboardService 实现**

```java
package com.tianji.mall.service;

import com.tianji.common.result.R;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserFeignClient userFeignClient;

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            1, "待付款",
            2, "已付款",
            3, "已发货",
            4, "已完成",
            5, "已取消"
    );

    public DashboardResponse getDashboard() {
        // 1. 总量
        BigDecimal totalGmv = orderMapper.selectTotalGmv();
        Long totalOrders = orderMapper.selectPaidOrderCount();

        // 2. 时间范围起点
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        DashboardResponse.TimeStats today = buildTimeStats(todayStart);
        DashboardResponse.TimeStats thisWeek = buildTimeStats(weekStart);
        DashboardResponse.TimeStats thisMonth = buildTimeStats(monthStart);

        // 3. 用户数（Feign，降级为 0）
        Long totalUsers = 0L;
        try {
            R<Long> result = userFeignClient.countUsers();
            if (result != null && result.getData() != null) {
                totalUsers = result.getData();
            }
        } catch (Exception e) {
            log.warn("获取用户数失败，降级为 0", e);
        }

        // 4. 热销 Top10
        List<DashboardResponse.TopProduct> topProducts = orderItemMapper.selectTopSellingProducts().stream()
                .map(row -> new DashboardResponse.TopProduct(
                        toLong(row.get("product_id")),
                        (String) row.get("name"),
                        toLong(row.get("sales")),
                        toBigDecimal(row.get("amount"))
                ))
                .toList();

        // 5. 订单状态分布
        List<DashboardResponse.OrderStatusDist> statusDist = orderMapper.selectStatusDistribution().stream()
                .map(row -> new DashboardResponse.OrderStatusDist(
                        toInt(row.get("status")),
                        STATUS_LABELS.getOrDefault(toInt(row.get("status")), "未知"),
                        toLong(row.get("cnt"))
                ))
                .toList();

        // 6. 分类销量占比
        List<DashboardResponse.CategorySales> categorySales = orderItemMapper.selectCategorySales().stream()
                .map(row -> new DashboardResponse.CategorySales(
                        toLong(row.get("category_id")),
                        (String) row.get("category_name"),
                        toBigDecimal(row.get("amount"))
                ))
                .toList();

        return new DashboardResponse(totalGmv, totalOrders, totalUsers,
                today, thisWeek, thisMonth, topProducts, statusDist, categorySales);
    }

    private DashboardResponse.TimeStats buildTimeStats(LocalDateTime start) {
        BigDecimal gmv = orderMapper.selectGmvByTimeRange(start);
        Long orders = orderMapper.selectPaidOrderCountByTimeRange(start);
        return new DashboardResponse.TimeStats(gmv != null ? gmv : BigDecimal.ZERO,
                orders != null ? orders : 0L);
    }

    private static Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private static Integer toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=DashboardServiceTest
```

预期：5 tests PASS。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/DashboardService.java \
        mall-goods-order/src/test/java/com/tianji/mall/service/DashboardServiceTest.java
git commit -m "feat: add DashboardService with aggregation queries and Feign fallback"
```

---

### 任务 6：AdminController dashboard 端点 + 测试

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`

- [ ] **步骤 1：编写失败的端点测试**

在 `AdminControllerTest.java` 底部（最后一个测试之后，类结束 `}` 之前）追加：

```java
// ==================== GET /api/admin/dashboard ====================

@Test
void shouldGetDashboard() throws Exception {
    DashboardResponse resp = new DashboardResponse();
    resp.setTotalGmv(java.math.BigDecimal.valueOf(100000));
    resp.setTotalOrders(100L);
    resp.setTotalUsers(200L);
    resp.setToday(new DashboardResponse.TimeStats(java.math.BigDecimal.valueOf(5000), 10L));
    resp.setThisWeek(new DashboardResponse.TimeStats(java.math.BigDecimal.valueOf(30000), 60L));
    resp.setThisMonth(new DashboardResponse.TimeStats(java.math.BigDecimal.valueOf(100000), 200L));
    resp.setTopProducts(List.of());
    resp.setOrderStatusDist(List.of());
    resp.setCategorySales(List.of());
    when(dashboardService.getDashboard()).thenReturn(resp);

    mockMvc.perform(get("/api/admin/dashboard")
                    .header("X-User-Role", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalGmv").value(100000))
            .andExpect(jsonPath("$.data.totalOrders").value(100))
            .andExpect(jsonPath("$.data.totalUsers").value(200))
            .andExpect(jsonPath("$.data.today.gmv").value(5000))
            .andExpect(jsonPath("$.data.today.orders").value(10));
}
```

添加 import：

```java
import com.tianji.mall.dto.DashboardResponse;
```

添加 MockBean：

```java
@MockBean
private DashboardService dashboardService;
```

`AdminControllerTest` 类头部 import block 追加：

```java
import com.tianji.mall.dto.DashboardResponse;
```

成员变量区域追加：

```java
@MockBean
private DashboardService dashboardService;
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn test -pl mall-goods-order -Dtest=AdminControllerTest#shouldGetDashboard
```

预期：FAIL — `dashboardService` 字段在 AdminController 中不存在。

- [ ] **步骤 3：AdminController 添加 dashboard 端点**

在 `AdminController.java` 的 `deleteAttribute` 方法之后、类结束 `}` 之前追加：

```java
// ==================== 数据看板 ====================

private final DashboardService dashboardService;

@GetMapping("/dashboard")
public R<DashboardResponse> getDashboard() {
    return R.ok(dashboardService.getDashboard());
}
```

需要修改类头部构造器注入：`AdminController` 当前使用 `@RequiredArgsConstructor`（基于 final 字段自动生成构造器），新增的 `private final DashboardService dashboardService;` 字段会自动被 Lombok 包含，无需手动修改构造器。

添加 import：

```java
import com.tianji.mall.dto.DashboardResponse;
```

- [ ] **步骤 4：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=AdminControllerTest
```

预期：32 tests PASS（原有 31 + 新增 1）。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java \
        mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java
git commit -m "feat: add GET /api/admin/dashboard endpoint"
```

---

### 任务 7：全模块回归测试

- [ ] **步骤 1：运行 mall-goods-order 全量测试**

```bash
mvn test -pl mall-goods-order
```

预期：246 tests PASS（原有 240 + 新增 6）。

- [ ] **步骤 2：运行全模块编译**

```bash
mvn compile
```

预期：8 模块 BUILD SUCCESS。

- [ ] **步骤 3：运行 Gateway 测试确认不回归**

```bash
mvn test -pl gateway
```

预期：30 tests PASS。

- [ ] **步骤 4：Commit（如有变更）**

```bash
git add -A
git commit -m "chore: finalize admin dashboard, all tests pass"
```

---

## 测试覆盖汇总

| 测试类 | 操作 | 测试数 |
|--------|------|--------|
| DashboardServiceTest | 新建 | 5 |
| AdminControllerTest | 修改 | 原有 31 → 32 (+1) |
| **合计新增** | | **6 个新测试** |

## 验证方式

```bash
# 单模块测试
mvn test -pl mall-goods-order

# 全模块编译
mvn compile
```
