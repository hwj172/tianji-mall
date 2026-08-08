package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductSimilarity;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.ProductSimilarityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSimilarityMapper similarityMapper;
    private final HotSalesCacheService hotSalesCacheService;

    private static final int DEFAULT_COUNT = 10;

    /**
     * 对外推荐入口。
     * @param userId 可空，null 表示未登录用户
     * @param count 每类推荐数量
     */
    public RecommendResponse recommend(Long userId, int count) {
        int n = count > 0 ? count : DEFAULT_COUNT;
        List<RecommendResponse.RecommendItem> hotSales = getHotSales(n);
        Set<Long> hotIds = hotSales.stream().map(RecommendResponse.RecommendItem::getId).collect(Collectors.toSet());

        // 猜你喜欢排除热销已出现商品（跨块去重）；未登录也返回冷启动混合推荐
        List<RecommendResponse.RecommendItem> guessYouLike = userId != null
                ? getGuessYouLike(userId, n, hotIds) : getColdStartMix(n, hotIds);
        Set<Long> guessIds = guessYouLike.stream().map(RecommendResponse.RecommendItem::getId).collect(Collectors.toSet());

        // 买了还买排除前两块已出现商品
        Set<Long> excludeIds = new HashSet<>(hotIds);
        excludeIds.addAll(guessIds);
        List<RecommendResponse.RecommendItem> buyAfterBuy = getBuyAfterBuy(userId, n, excludeIds);

        return new RecommendResponse(guessYouLike, hotSales, buyAfterBuy);
    }

    // ==================== 热销榜单 ====================

    public List<RecommendResponse.RecommendItem> getHotSales(int count) {
        // 通过独立 Bean 的 Spring 代理调用，@Cacheable 才会生效（本类 this 自调用会绕过代理导致缓存失效）
        List<RecommendResponse.RecommendItem> all = hotSalesCacheService.computeHotSales();
        return all.size() > count ? all.subList(0, count) : all;
    }

    // ==================== 猜你喜欢 ====================

    public List<RecommendResponse.RecommendItem> getGuessYouLike(Long userId, int count, Set<Long> excludeIds) {
        List<Long> purchasedIds = getPurchasedProductIds(userId);
        if (purchasedIds.isEmpty()) {
            // 冷启动兜底：热销 + 新品混合，而非空（排除已推荐商品，避免与热销块重复）
            return getColdStartMix(count, excludeIds);
        }

        Map<Long, Long> categoryWeight = new HashMap<>();
        List<Product> purchasedProducts = productMapper.selectBatchIds(purchasedIds);
        for (Product p : purchasedProducts) {
            if (p.getCategoryId() != null) {
                categoryWeight.merge(p.getCategoryId(), 1L, Long::sum);
            }
        }

        // 推荐理由：偏好度最高的品类中用户买过的一个商品
        String reason = "";
        if (!categoryWeight.isEmpty()) {
            Long topCategory = categoryWeight.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            if (topCategory != null) {
                Product bought = purchasedProducts.stream()
                        .filter(p -> topCategory.equals(p.getCategoryId())).findFirst().orElse(null);
                if (bought != null) {
                    reason = "因为您购买过「" + bought.getName() + "」";
                }
            }
        }

        List<RecommendResponse.RecommendItem> result = new ArrayList<>();
        List<Long> excluded = new ArrayList<>(purchasedIds);
        if (excludeIds != null) {
            excluded.addAll(excludeIds);
        }

        // lambda 引用需 effectively final
        final String finalReason = reason;

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
                            .sorted(java.util.Comparator.comparing(
                                    Product::getSales,
                                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                            .limit(count - result.size()).toList()) {
                        result.add(new RecommendResponse.RecommendItem(
                                p.getId(), p.getName(), p.getPrice(), (long) p.getSales(), finalReason, p.getImages()));
                        excluded.add(p.getId());
                    }
                });

        return result;
    }

    /** 冷启动兜底：热销（排除已推荐）+ 新品混合 */
    private List<RecommendResponse.RecommendItem> getColdStartMix(int count, Set<Long> excludeIds) {
        List<RecommendResponse.RecommendItem> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>(excludeIds != null ? excludeIds : Set.of());
        for (RecommendResponse.RecommendItem item : getHotSales(count)) {
            if (result.size() >= count) break;
            if (seen.add(item.getId())) {
                result.add(item);
            }
        }
        if (result.size() < count) {
            // 新品取热销之外的（排除已选，避免同页重复）
            List<Product> newProducts = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getStatus, 1)
                            .notIn(!seen.isEmpty(), Product::getId, seen)
                            .orderByDesc(Product::getCreateTime)
                            .last("LIMIT " + count));
            for (Product p : newProducts) {
                if (result.size() >= count) break;
                if (seen.add(p.getId())) {
                    result.add(new RecommendResponse.RecommendItem(
                            p.getId(), p.getName(), p.getPrice(), (long) p.getSales(), "新品上市", p.getImages()));
                }
            }
        }
        // 兜底：冷启动且排除后仍为空（如商品全在热销），返回热销完整列表
        if (result.isEmpty()) {
            result.addAll(getHotSales(count));
        }
        return result;
    }

    // ==================== 买了还买 ====================

    public List<RecommendResponse.RecommendItem> getBuyAfterBuy(Long userId, int count, Set<Long> excludeIds) {
        List<Long> seedIds;
        if (userId != null) {
            seedIds = getPurchasedProductIds(userId);
        } else {
            seedIds = null;
        }
        if (seedIds == null || seedIds.isEmpty()) {
            seedIds = getHotSales(10).stream().map(RecommendResponse.RecommendItem::getId).collect(Collectors.toList());
        }

        Set<Long> seen = new HashSet<>(excludeIds != null ? excludeIds : Set.of());
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
                                "和 " + getProductName(seedId) + " 一起买", p.getImages()));
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

    @Scheduled(cron = "0 5 * * * *")
    public void refreshSimilarity() {
        computeSimilarity();
    }

    // ==================== 关联规则计算 ====================

    @Transactional
    public int computeSimilarity() {
        log.info("计算关联规则矩阵...");
        similarityMapper.truncate();

        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().in(Order::getStatus, 2, 3, 4));
        if (orders.isEmpty()) return 0;

        Map<Long, Set<Long>> orderProducts = new HashMap<>();
        for (Order order : orders) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
            Set<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
            orderProducts.put(order.getId(), productIds);
        }

        Map<String, Integer> coCountMap = new HashMap<>();
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

        int totalOrders = orders.size();
        List<ProductSimilarity> batch = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : coCountMap.entrySet()) {
            String[] parts = entry.getKey().split("_");
            Long pid1 = Long.parseLong(parts[0]);
            Long pid2 = Long.parseLong(parts[1]);
            int coCount = entry.getValue();
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

        List<Long> orderIds = userOrders.stream().map(Order::getId).collect(Collectors.toList());
        return orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream().map(OrderItem::getProductId).distinct().collect(Collectors.toList());
    }

    private String getProductName(Long productId) {
        Product p = productMapper.selectById(productId);
        return p != null ? p.getName() : "商品";
    }
}
