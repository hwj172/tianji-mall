package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

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
                productMapper, orderMapper, orderItemMapper, similarityMapper,
                new HotSalesCacheService(productMapper, favoriteMapper, reviewMapper));
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
        assertThat(resp.getHotSales().get(0).getId()).isEqualTo(1L);
        // 未登录也有冷启动混合推荐（热销兜底）
        assertThat(resp.getGuessYouLike()).isNotEmpty();
        assertThat(resp.getBuyAfterBuy()).isEmpty();
    }

    // ==================== 测试 2：猜你喜欢（有购买记录）====================

    @Test
    void shouldReturnGuessYouLikeBasedOnCategoryPreference() {
        Order order = buildOrder(1L, 1L);
        OrderItem item = buildOrderItem(1L, 1L);
        // 第一次 selectList: computeHotSales 返回全量; 第二次: 同品类过滤
        when(productMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(p1, p2, p3))
                .thenReturn(List.of(p2));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));
        when(productMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(p1));

        RecommendResponse resp = recommendService.recommend(1L, 5);

        assertThat(resp.getGuessYouLike()).isNotEmpty();
        assertThat(resp.getGuessYouLike()).allMatch(r -> !r.getId().equals(1L));
    }

    // ==================== 测试 3：猜你喜欢退化为热销榜（无购买记录）====================

    @Test
    void shouldFallbackToHotSalesWhenNoPurchaseHistory() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(similarityMapper.selectByProductId(anyLong(), eq(5))).thenReturn(List.of());

        RecommendResponse resp = recommendService.recommend(1L, 5);

        assertThat(resp.getGuessYouLike()).hasSize(2);
        assertThat(resp.getBuyAfterBuy()).isEmpty();
    }

    // ==================== 测试 4：买了还买（有关联规则）====================

    @Test
    void shouldReturnBuyAfterBuyWithReasons() {
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

        // 直接测 getBuyAfterBuy（recommend 会因跨块去重排除热销中商品，此处验证独立逻辑与 reason）
        List<RecommendResponse.RecommendItem> list = recommendService.getBuyAfterBuy(1L, 5, Set.of());

        assertThat(list).isNotEmpty();
        assertThat(list.get(0).getReason()).contains("iPhone");
    }

    // ==================== 测试 5：无商品时返回空 ====================

    @Test
    void shouldReturnEmptyWhenNoProducts() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

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
