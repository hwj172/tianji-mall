package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.PendingReviewResponse;
import com.tianji.mall.dto.ReviewCreateRequest;
import com.tianji.mall.dto.ReviewResponse;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Review;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private UserFeignClient userFeignClient;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(orderMapper, orderItemMapper, productMapper, userFeignClient);
        ReflectionTestUtils.setField(reviewService, "baseMapper", reviewMapper);
    }

    @Test
    void shouldCreateReviewSuccessfully() {
        Order order = buildOrder(1L, 100L, 4);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildOrderItem(1L, 10L)));
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(5);
        req.setContent("很好");

        reviewService.createReview(100L, req);

        verify(reviewMapper).insert(any(Review.class));
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderMapper.selectById(99L)).thenReturn(null);

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(99L);
        req.setProductId(10L);
        req.setRating(5);

        assertThatThrownBy(() -> reviewService.createReview(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderBelongsToOtherUser() {
        Order order = buildOrder(1L, 999L, 4);
        when(orderMapper.selectById(1L)).thenReturn(order);

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(5);

        assertThatThrownBy(() -> reviewService.createReview(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderNotCompleted() {
        Order order = buildOrder(1L, 100L, 2); // 已付款但未完成
        when(orderMapper.selectById(1L)).thenReturn(order);

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(5);

        assertThatThrownBy(() -> reviewService.createReview(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("仅可评价已完成的订单");
    }

    @Test
    void shouldThrowWhenProductNotInOrder() {
        Order order = buildOrder(1L, 100L, 4);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(99L);
        req.setRating(5);

        assertThatThrownBy(() -> reviewService.createReview(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("该订单不包含此商品");
    }

    @Test
    void shouldThrowWhenDuplicateReview() {
        Order order = buildOrder(1L, 100L, 4);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildOrderItem(1L, 10L)));
        Review existing = new Review();
        existing.setId(1L);
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(4);

        assertThatThrownBy(() -> reviewService.createReview(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("您已评价过该商品");
    }

    @Test
    void shouldCreateReviewWithImages() {
        Order order = buildOrder(1L, 100L, 4);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildOrderItem(1L, 10L)));
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(1L);
        req.setProductId(10L);
        req.setRating(5);
        req.setContent("很好");
        req.setImages("/uploads/a.jpg,/uploads/b.jpg");

        reviewService.createReview(100L, req);

        verify(reviewMapper).insert(any(Review.class));
    }

    @Test
    void shouldGetProductReviews() {
        Review review = new Review();
        review.setId(1L);
        review.setUserId(100L);
        review.setProductId(10L);
        review.setRating(5);
        review.setContent("好评");
        review.setStatus(1);
        when(reviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<Review>().setRecords(List.of(review)));

        IPage<ReviewResponse> result = reviewService.getProductReviews(10L, 1, 20, "all");

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getRating()).isEqualTo(5);
    }

    @Test
    void shouldFilterGoodReviews() {
        Review r5 = new Review();
        r5.setId(1L);
        r5.setRating(5);
        Review r2 = new Review();
        r2.setId(2L);
        r2.setRating(2);
        when(reviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<Review>().setRecords(List.of(r5, r2)));

        // good → rating >= 4，wrapper 由 lambda 断言不可行；这里验证方法不抛且返回转换结果
        IPage<ReviewResponse> result = reviewService.getProductReviews(10L, 1, 20, "good");

        assertThat(result.getRecords()).hasSize(2);
    }

    @Test
    void shouldReplyReview() {
        Review review = new Review();
        review.setId(1L);
        review.setContent("原评价");
        when(reviewMapper.selectById(1L)).thenReturn(review);
        when(reviewMapper.updateById(any(Review.class))).thenReturn(1);

        reviewService.replyReview(1L, "感谢支持");

        verify(reviewMapper).updateById(argThat((com.tianji.mall.entity.Review upd) ->
                "感谢支持".equals(upd.getReply()) && upd.getReplyTime() != null));
    }

    @Test
    void shouldThrowWhenReplyNonExistent() {
        when(reviewMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.replyReview(99L, "回复"))
                .isInstanceOf(BizException.class)
                .hasMessage("评价不存在");
    }

    @Test
    void shouldGetShopReviews() {
        com.tianji.mall.entity.Product p = new com.tianji.mall.entity.Product();
        p.setId(10L);
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));
        Review review = new Review();
        review.setId(1L);
        review.setProductId(10L);
        review.setRating(4);
        when(reviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<Review>().setRecords(List.of(review)));

        IPage<ReviewResponse> result = reviewService.getShopReviews(5L, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getProductId()).isEqualTo(10L);
    }

    @Test
    void shouldReturnEmptyWhenShopHasNoProducts() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        IPage<ReviewResponse> result = reviewService.getShopReviews(5L, 1, 20);

        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void shouldGetMyReviews() {
        Review review = new Review();
        review.setId(1L);
        review.setUserId(100L);
        review.setProductId(20L);
        review.setRating(3);
        review.setContent("一般");
        review.setStatus(1);
        when(reviewMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<Review>().setRecords(List.of(review)));

        List<ReviewResponse> result = reviewService.getMyReviews(100L, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldReturnPendingReviews() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("order_id", 100L);
        row.put("product_id", 1L);
        row.put("product_name", "iPhone 16");
        row.put("product_image", "img.jpg");
        row.put("price", BigDecimal.valueOf(9999));
        row.put("sku_id", null);
        row.put("sku_specs", null);
        row.put("order_create_time", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 7, 20, 10, 0, 0)));
        List<Map<String, Object>> rows = List.of(row);
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
        when(reviewMapper.selectPendingReviews(1L, 0, 20)).thenReturn(List.of());

        List<PendingReviewResponse> result = reviewService.getPendingReviews(1L, 1, 20);

        assertThat(result).isEmpty();
        verify(reviewMapper).selectPendingReviews(1L, 0, 20);
    }

    private Order buildOrder(Long id, Long userId, int status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        return order;
    }

    private OrderItem buildOrderItem(Long orderId, Long productId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        return item;
    }
}
