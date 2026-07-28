package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
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
                .collect(Collectors.toList());
    }

    public List<RecommendResponse.RecommendItem> getHotSales(int count) {
        List<RecommendResponse.RecommendItem> all = computeHotSales();
        return all.size() > count ? all.subList(0, count) : all;
    }

    // ==================== 猜你喜欢 ====================

    public List<RecommendResponse.RecommendItem> getGuessYouLike(Long userId, int count) {
        List<Long> purchasedIds = getPurchasedProductIds(userId);
        if (purchasedIds.isEmpty()) {
            return getHotSales(count);
        }

        Map<Long, Long> categoryWeight = new HashMap<>();
        for (Product p : productMapper.selectBatchIds(purchasedIds)) {
            if (p.getCategoryId() != null) {
                categoryWeight.merge(p.getCategoryId(), 1L, Long::sum);
            }
        }

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
                            .sorted(java.util.Comparator.comparing(
                                    Product::getSales,
                                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                            .limit(count - result.size()).toList()) {
                        result.add(new RecommendResponse.RecommendItem(
                                p.getId(), p.getName(), p.getPrice(), (long) p.getSales(), ""));
                        excluded.add(p.getId());
                    }
                });

        return result;
    }

    // ==================== 买了还买 ====================

    public List<RecommendResponse.RecommendItem> getBuyAfterBuy(Long userId, int count) {
        List<Long> seedIds;
        if (userId != null) {
            seedIds = getPurchasedProductIds(userId);
        } else {
            seedIds = null;
        }
        if (seedIds == null || seedIds.isEmpty()) {
            seedIds = getHotSales(10).stream().map(RecommendResponse.RecommendItem::getId).collect(Collectors.toList());
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

    private Long countFavorites(Long productId) {
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getProductId, productId));
    }

    private Long countReviews(Long productId) {
        return reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getProductId, productId));
    }
}
