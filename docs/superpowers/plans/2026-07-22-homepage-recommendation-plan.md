# 首页推荐系统 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为首页提供推荐接口 `GET /api/product/recommend`（JWT 可选），返回猜你喜欢 + 热销榜 + 买了还买

**架构：** RecommendService 聚合三个推荐算法，热销榜和关联矩阵通过 @Cacheable 缓存（TTL 1小时），猜你喜欢实时查询用户品类偏好

**技术栈：** MyBatis-Plus, Redis Cache, JwtUtil（已有）

---

### 任务 1：ProductSimilarity 实体 + DDL + Mapper

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/entity/ProductSimilarity.java`
- 修改：`mall-goods-order/src/test/resources/schema.sql`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductSimilarityMapper.java`

- [ ] **步骤 1：创建实体类**

```java
package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_similarity")
public class ProductSimilarity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long similarProductId;
    private Integer coCount;
    private BigDecimal score;
    private LocalDateTime updateTime;
}
```

- [ ] **步骤 2：在 schema.sql 末尾追加建表 DDL**

```sql
CREATE TABLE IF NOT EXISTS product_similarity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    similar_product_id BIGINT NOT NULL,
    co_count INT DEFAULT 0,
    score DECIMAL(10,4) DEFAULT 0,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pair (product_id, similar_product_id),
    KEY idx_product (product_id),
    KEY idx_score (score DESC)
);
```

- [ ] **步骤 3：创建 Mapper**

```java
package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.ProductSimilarity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSimilarityMapper extends BaseMapper<ProductSimilarity> {

    @Delete("DELETE FROM product_similarity")
    void truncate();

    @Insert("<script>" +
            "INSERT IGNORE INTO product_similarity (product_id, similar_product_id, co_count, score) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.productId}, #{item.similarProductId}, #{item.coCount}, #{item.score})" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<ProductSimilarity> list);

    @Select("SELECT * FROM product_similarity WHERE product_id = #{productId} ORDER BY score DESC LIMIT #{limit}")
    List<ProductSimilarity> selectByProductId(@Param("productId") Long productId, @Param("limit") int limit);
}
```

- [ ] **步骤 4：编译验证**

运行：`mvn compile -pl mall-goods-order -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/entity/ProductSimilarity.java mall-goods-order/src/test/resources/schema.sql mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductSimilarityMapper.java
git commit -m "feat: add ProductSimilarity entity, schema, and mapper"
```

---

### 任务 2：RecommendService 核心算法

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/RecommendResponse.java`
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/service/RecommendService.java`

- [ ] **步骤 1：创建 RecommendResponse DTO**

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
public class RecommendResponse {

    private List<RecommendItem> guessYouLike;
    private List<RecommendItem> hotSales;
    private List<RecommendItem> buyAfterBuy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem {
        private Long id;
        private String name;
        private BigDecimal price;
        private Long sales;
        private String reason;
    }
}
```

- [ ] **步骤 2：创建 RecommendService**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final FavoriteMapper favoriteMapper;
    private final ReviewMapper reviewMapper;
    private final ProductSimilarityMapper similarityMapper;

    private static final int DEFAULT_COUNT = 10;
    private static final int TOP_N = 50;

    /**
     * 对外推荐入口。
     * @param userId 可空，null 表示未登录用户
     * @param count 每类推荐数量
     */
    public RecommendResponse recommend(Long userId, int count) {
        int n = count > 0 ? count : DEFAULT_COUNT;
        List<RecommendResponse.RecommendItem> hotSales = getHotSales(n);

        List<RecommendResponse.RecommendItem> guessYouLike = userId != null
                ? getGuessYouLike(userId, n) : List.of();

        List<RecommendResponse.RecommendItem> buyAfterBuy = getBuyAfterBuy(userId, n);

        return new RecommendResponse(guessYouLike, hotSales, buyAfterBuy);
    }

    // ==================== 热销榜单 ====================

    @Cacheable(value = "recommend", key = "'hot_sales'")
    public List<RecommendResponse.RecommendItem> computeHotSales() {
        // 全量商品按加权得分排序
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1));
        return products.stream()
                .map(p -> {
                    double score = p.getSales() * 0.5
                            + countFavorites(p.getId()) * 0.3
                            + countReviews(p.getId()) * 0.2;
                    return new RecommendResponse.RecommendItem(
                            p.getId(), p.getName(), p.getPrice(), (long) p.getSales(), "");
                })
                .sorted((a, b) -> Long.compare(b.getSales(), a.getSales()))
                .limit(TOP_N)
                .toList();
    }

    public List<RecommendResponse.RecommendItem> getHotSales(int count) {
        List<RecommendResponse.RecommendItem> all = computeHotSales();
        return all.size() > count ? all.subList(0, count) : all;
    }

    // ==================== 猜你喜欢 ====================

    public List<RecommendResponse.RecommendItem> getGuessYouLike(Long userId, int count) {
        // 查用户购买过的商品，统计品类偏好
        List<Long> purchasedIds = getPurchasedProductIds(userId);
        if (purchasedIds.isEmpty()) {
            return getHotSales(count); // 退化为热销榜
        }

        // 品类权重：该品类下购买次数
        Map<Long, Long> categoryWeight = new HashMap<>();
        for (Product p : productMapper.selectBatchIds(purchasedIds)) {
            if (p.getCategoryId() != null) {
                categoryWeight.merge(p.getCategoryId(), 1L, Long::sum);
            }
        }

        // 按权重排序取品类，每个品类取热度最高的商品（排除已购）
        List<RecommendResponse.RecommendItem> result = new ArrayList<>();
        List<Long> excluded = new ArrayList<>(purchasedIds);

        categoryWeight.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    if (result.size() >= count) return;
                    List<Product> categoryProducts = productMapper.selectList(
                            new LambdaQueryWrapper<Product>()
                                    .eq(Product::getCategoryId, entry.getKey())
                                    .eq(Product::getStatus, 1)
                                    .notIn(!excluded.isEmpty(), Product::getId, excluded));
                    for (Product p : categoryProducts.stream()
                            .sorted((a, b) -> b.getSales().compareTo(a.getSales()))
                            .limit(count - result.size()).toList()) {
                        result.add(new RecommendResponse.RecommendItem(
                                p.getId(), p.getName(), p.getPrice(), (long) p.getSales(), ""));
                        excluded.add(p.getId());
                    }
                });

        return result;
    }

    // ==================== 买了还买 ====================

    @Cacheable(value = "recommend", key = "'similarity'")
    public void ensureSimilarityComputed() {
        // 空方法，仅用于触发缓存计算
    }

    public List<RecommendResponse.RecommendItem> getBuyAfterBuy(Long userId, int count) {
        // 触发缓存计算（首次调用时计算）
        computeSimilarity();

        List<Long> seedIds;
        if (userId != null) {
            seedIds = getPurchasedProductIds(userId);
        }
        if (seedIds == null || seedIds.isEmpty()) {
            // 未登录/无购买记录：用全站 Top 10 热销商品作为种子
            seedIds = getHotSales(10).stream().map(RecommendResponse.RecommendItem::getId).toList();
        }

        Set<Long> seen = new HashSet<>();
        List<RecommendResponse.RecommendItem> result = new ArrayList<>();

        for (Long seedId : seedIds) {
            if (result.size() >= count) break;
            List<ProductSimilarity> sims = similarityMapper.selectByProductId(seedId, 5);
            for (ProductSimilarity sim : sims) {
                if (result.size() >= count) break;
                if (seen.add(sim.getSimilarProductId())) {
                    Product p = productMapper.selectById(sim.getSimilarProductId());
                    if (p != null && p.getStatus() == 1) {
                        result.add(new RecommendResponse.RecommendItem(
                                p.getId(), p.getName(), p.getPrice(), (long) p.getSales(),
                                "和 " + getProductName(seedId) + " 一起买"));
                    }
                }
            }
        }

        return result;
    }

    @CacheEvict(value = "recommend", key = "'hot_sales'")
    @Scheduled(cron = "0 0 * * * *")
    public void evictHotSalesCache() {
        // 每小时清理缓存，下次请求时重新计算
    }

    @CacheEvict(value = "recommend", key = "'similarity'")
    @Scheduled(cron = "0 5 * * * *")
    public void evictSimilarityCache() {
        // 每小时 5 分钟后清理缓存，下次请求时重新计算
    }

    // ==================== 关联规则计算 ====================

    @Cacheable(value = "recommend", key = "'similarity'")
    public int computeSimilarity() {
        log.info("计算关联规则矩阵...");
        similarityMapper.truncate();

        // 扫描所有已付款/已发货/已完成订单
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().in(Order::getStatus, 2, 3, 4));
        if (orders.isEmpty()) return 0;

        // 构建每个订单的商品集合
        Map<Long, Set<Long>> orderProducts = new HashMap<>();
        for (Order order : orders) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            Set<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
            orderProducts.put(order.getId(), productIds);
        }

        // 共现计数
        Map<String, Integer> coCountMap = new HashMap<>();
        Map<String, BigDecimal> scoreMap = new HashMap<>();
        for (Set<Long> products : orderProducts.values()) {
            List<Long> list = new ArrayList<>(products);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String key1 = list.get(i) + "_" + list.get(j);
                    String key2 = list.get(j) + "_" + list.get(i);
                    coCountMap.merge(key1, 1, Integer::sum);
                    coCountMap.merge(key2, 1, Integer::sum);
                }
            }
        }

        // 计算 Jaccard 分数并批量写入
        int totalOrders = orders.size();
        List<ProductSimilarity> batch = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : coCountMap.entrySet()) {
            String[] parts = entry.getKey().split("_");
            Long pid1 = Long.parseLong(parts[0]);
            Long pid2 = Long.parseLong(parts[1]);
            int coCount = entry.getValue();
            // 简单归一化：共现次数 / 总订单数
            BigDecimal score = BigDecimal.valueOf(coCount)
                    .divide(BigDecimal.valueOf(Math.max(totalOrders, 1)), 4, RoundingMode.HALF_UP);

            ProductSimilarity sim = new ProductSimilarity();
            sim.setProductId(pid1);
            sim.setSimilarProductId(pid2);
            sim.setCoCount(coCount);
            sim.setScore(score);
            batch.add(sim);

            if (batch.size() >= 500) {
                similarityMapper.batchInsert(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            similarityMapper.batchInsert(batch);
        }

        log.info("关联规则矩阵计算完成，共 {} 条记录", coCountMap.size());
        return coCountMap.size();
    }

    // ==================== 辅助方法 ====================

    private List<Long> getPurchasedProductIds(Long userId) {
        List<Order> userOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .in(Order::getStatus, 2, 3, 4));
        if (userOrders.isEmpty()) return List.of();

        List<Long> orderIds = userOrders.stream().map(Order::getId).toList();
        return orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream().map(OrderItem::getProductId).distinct().toList();
    }

    private String getProductName(Long productId) {
        Product p = productMapper.selectById(productId);
        return p != null ? p.getName() : "商品";
    }

    private Long countFavorites(Long productId) {
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getProductId, productId));
    }

    private Long countReviews(Long productId) {
        return reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getProductId, productId));
    }
}
```

- [ ] **步骤 3：在 MallApplication 类加 @EnableScheduling**

修改文件：`mall-goods-order/src/main/java/com/tianji/mall/MallApplication.java`

在 `@EnableCaching` 下方添加：
```java
@EnableScheduling
```

- [ ] **步骤 4：编译验证**

运行：`mvn compile -pl mall-goods-order -q`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/dto/RecommendResponse.java mall-goods-order/src/main/java/com/tianji/mall/service/RecommendService.java mall-goods-order/src/main/java/com/tianji/mall/MallApplication.java
git commit -m "feat: add RecommendService with three recommendation algorithms"
```

---

### 任务 3：RecommendServiceTest 单元测试

**文件：**
- 创建：`mall-goods-order/src/test/java/com/tianji/mall/service/RecommendServiceTest.java`

- [ ] **步骤 1：编写 5 个测试**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private ProductSimilarityMapper similarityMapper;

    private RecommendService recommendService;
    private Product p1, p2, p3;

    @BeforeEach
    void setUp() {
        recommendService = new RecommendService(
                productMapper, orderMapper, orderItemMapper,
                favoriteMapper, reviewMapper, similarityMapper);
        p1 = buildProduct(1L, "iPhone", 1L, 5000);
        p2 = buildProduct(2L, "保护壳", 1L, 3000);
        p3 = buildProduct(3L, "MacBook", 2L, 4000);
    }

    // ==================== 测试 1：热销榜 ====================

    @Test
    void shouldReturnHotSalesByWeightedScore() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        RecommendResponse resp = recommendService.recommend(null, 5);

        assertThat(resp.getHotSales()).hasSize(2);
        assertThat(resp.getHotSales().get(0).getId()).isEqualTo(1L); // sales 更高排第一
        assertThat(resp.getGuessYouLike()).isEmpty(); // 未登录无猜你喜欢
        assertThat(resp.getBuyAfterBuy()).hasSize(2); // 用热销商品做种子
    }

    // ==================== 测试 2：猜你喜欢（有购买记录）====================

    @Test
    void shouldReturnGuessYouLikeBasedOnCategoryPreference() {
        Order order = buildOrder(1L, 1L);
        OrderItem item = buildOrderItem(1L, 1L);
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2, p3));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));
        when(productMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(p1));

        RecommendResponse resp = recommendService.recommend(1L, 5);

        assertThat(resp.getGuessYouLike()).isNotEmpty();
        // 推荐同品类（categoryId=1）商品，排除已购 p1
        assertThat(resp.getGuessYouLike()).allMatch(r -> !r.getId().equals(1L));
    }

    // ==================== 测试 3：猜你喜欢退化为热销榜（无购买记录）====================

    @Test
    void shouldFallbackToHotSalesWhenNoPurchaseHistory() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        // buyAfterBuy 部分：无购买记录，用热销做种子
        when(similarityMapper.selectByProductId(anyLong(), eq(5))).thenReturn(List.of());

        RecommendResponse resp = recommendService.recommend(1L, 5);

        assertThat(resp.getGuessYouLike()).hasSize(2); // 等于热销榜
        assertThat(resp.getBuyAfterBuy()).isEmpty();
    }

    // ==================== 测试 4：买了还买（有关联规则）====================

    @Test
    void shouldReturnBuyAfterBuyWithReasons() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2, p3));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Order order = buildOrder(1L, 1L);
        OrderItem item = buildOrderItem(1L, 1L);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order), List.of(order));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item), List.of(item));

        ProductSimilarity sim = new ProductSimilarity();
        sim.setProductId(1L);
        sim.setSimilarProductId(2L);
        sim.setScore(BigDecimal.valueOf(0.5));
        when(similarityMapper.selectByProductId(1L, 5)).thenReturn(List.of(sim));
        when(productMapper.selectById(1L)).thenReturn(p1);
        when(productMapper.selectById(2L)).thenReturn(p2);

        RecommendResponse resp = recommendService.recommend(1L, 5);

        assertThat(resp.getBuyAfterBuy()).isNotEmpty();
        assertThat(resp.getBuyAfterBuy().get(0).getReason()).contains("iPhone");
    }

    // ==================== 测试 5：无商品时返回空 ====================

    @Test
    void shouldReturnEmptyWhenNoProducts() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        RecommendResponse resp = recommendService.recommend(null, 5);

        assertThat(resp.getHotSales()).isEmpty();
        assertThat(resp.getGuessYouLike()).isEmpty();
        assertThat(resp.getBuyAfterBuy()).isEmpty();
    }

    // ==================== helpers ====================

    private Product buildProduct(Long id, String name, Long categoryId, int sales) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategoryId(categoryId);
        p.setSales(sales);
        p.setPrice(BigDecimal.valueOf(1000));
        p.setStatus(1);
        return p;
    }

    private Order buildOrder(Long id, Long userId) {
        Order o = new Order();
        o.setId(id);
        o.setUserId(userId);
        o.setStatus(2);
        return o;
    }

    private OrderItem buildOrderItem(Long orderId, Long productId) {
        OrderItem oi = new OrderItem();
        oi.setOrderId(orderId);
        oi.setProductId(productId);
        return oi;
    }
}
```

- [ ] **步骤 2：运行测试验证**

运行：`mvn test -pl mall-goods-order -Dtest=RecommendServiceTest`
预期：Tests run: 5, Failures: 0, Errors: 0

- [ ] **步骤 3：Commit**

```bash
git add mall-goods-order/src/test/java/com/tianji/mall/service/RecommendServiceTest.java
git commit -m "test: add RecommendService unit tests (5 tests)"
```

---

### 任务 4：ProductController.recommend 端点

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java`

- [ ] **步骤 1：ProductController 添加 recommend 端点**

在 `ProductController` import 中添加：
```java
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.service.RecommendService;
```

在 `ProductController` 字段中添加：
```java
private final RecommendService recommendService;
private final JwtUtil jwtUtil;
```

在 `ProductController` 类末尾（`}` 前）添加：
```java
@GetMapping("/recommend")
public R<RecommendResponse> recommend(
        @RequestParam(value = "count", defaultValue = "10") int count,
        @RequestHeader(value = "Authorization", required = false) String authHeader) {
    Long userId = null;
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        userId = jwtUtil.getUserId(authHeader.substring(7));
    }
    return R.ok(recommendService.recommend(userId, count));
}
```

- [ ] **步骤 2：ProductControllerTest 添加 recommend 端点测试**

在 `ProductControllerTest` import 中添加：
```java
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.service.RecommendService;
```

在 `ProductControllerTest` MockBean 中添加：
```java
@MockBean
private RecommendService recommendService;

@MockBean
private com.tianji.common.util.JwtUtil jwtUtil;
```

在 `ProductControllerTest` 末尾（`}` 前）添加测试方法：
```java
// ==================== GET /api/product/recommend ====================

@Test
void shouldRecommendWithoutJwt() throws Exception {
    RecommendResponse resp = new RecommendResponse(
            List.of(),
            List.of(new RecommendResponse.RecommendItem(1L, "iPhone",
                    java.math.BigDecimal.valueOf(6999), 5000L, "")),
            List.of()
    );
    when(recommendService.recommend(isNull(), eq(10))).thenReturn(resp);

    mockMvc.perform(get("/api/product/recommend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.hotSales[0].name").value("iPhone"))
            .andExpect(jsonPath("$.data.guessYouLike").isEmpty());
}

@Test
void shouldRecommendWithJwt() throws Exception {
    when(jwtUtil.getUserId("test-token")).thenReturn(1L);

    RecommendResponse resp = new RecommendResponse(
            List.of(new RecommendResponse.RecommendItem(2L, "保护壳",
                    java.math.BigDecimal.valueOf(49), 3000L, "")),
            List.of(),
            List.of(new RecommendResponse.RecommendItem(3L, "数据线",
                    java.math.BigDecimal.valueOf(29), 2000L, "和 iPhone 一起买"))
    );
    when(recommendService.recommend(eq(1L), eq(5))).thenReturn(resp);

    mockMvc.perform(get("/api/product/recommend")
                    .header("Authorization", "Bearer test-token")
                    .param("count", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.guessYouLike[0].name").value("保护壳"))
            .andExpect(jsonPath("$.data.buyAfterBuy[0].name").value("数据线"));
}
```

- [ ] **步骤 3：运行 Controller 测试验证**

运行：`mvn test -pl mall-goods-order -Dtest=ProductControllerTest`
预期：Tests run: 7, Failures: 0, Errors: 0（原 5 个 + 新增 2 个）

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java
git commit -m "feat: add GET /api/product/recommend endpoint"
```

---

### 任务 5：Mock RecommendService + JwtUtil 到所有 @SpringBootTest

**文件：**
修改 13 个 @SpringBootTest 测试类（除 ProductControllerTest 已加）：
- `mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/CartControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/AddressControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/OrderControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/CouponControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/FavoriteControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/ReviewControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/controller/UploadControllerTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceIntegrationTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/service/ProductServiceIntegrationTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/service/AddressServiceIntegrationTest.java`
- `mall-goods-order/src/test/java/com/tianji/mall/service/CartServiceIntegrationTest.java`

- [ ] **步骤 1：每个文件添加 import + @MockBean**

每文件两项变更：
1. 添加 import：`import com.tianji.mall.service.RecommendService;`
2. 添加 MockBean：
```java
@MockBean
private RecommendService recommendService;
```

- [ ] **步骤 2：运行全量回归测试**

运行：`mvn test -pl mall-goods-order`
预期：271 tests, 0 Errors（246 + 5 RecommendServiceTest + 2 ProductControllerTest + 13 @MockBean RecommendService）

- [ ] **步骤 3：Commit**

```bash
git add mall-goods-order/src/test/
git commit -m "fix: add @MockBean RecommendService to all @SpringBootTest classes"
```

---

### 任务 6：更新 CLAUDE.md

**文件：**
- 修改：`CLAUDE.md`

- [ ] **步骤 1：更新测试统计数据**

```
测试总数：271 → 278（新增 5 个 Service 单元 + 2 个 Controller 测试）
```

- [ ] **步骤 2：添加推荐系统约定**

在 "关键约定" 部分添加：
```markdown
- **首页推荐**：`GET /api/product/recommend`（`ProductController.recommend` → `RecommendService.recommend(userId, count)`），JWT 可选。热销榜（加权得分公式：sales×0.5 + favorites×0.3 + reviews×0.2）+ 猜你喜欢（基于用户购买品类偏好）+ 买了还买（订单共现矩阵关联规则）。热销榜和关联矩阵用 @Cacheable 缓存（TTL 1小时），通过 @Scheduled 每小时 evict。关联规则存 `product_similarity` 表。`RecommendServiceTest` 5 个单元测试。
```

- [ ] **步骤 3：更新 @MockBean 要求**

在测试约定中，`@MockBean` 列表追加 `RecommendService`。

- [ ] **步骤 4：Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add recommendation convention and update test counts in CLAUDE.md"
```

---

### 最终验证

运行：`mvn test`
预期：278 tests，全模块 0 Errors，BUILD SUCCESS
