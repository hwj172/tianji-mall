package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.ReviewCreateRequest;
import com.tianji.mall.dto.ReviewResponse;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Review;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(orderMapper, orderItemMapper);
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

        List<ReviewResponse> result = reviewService.getProductReviews(10L, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRating()).isEqualTo(5);
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
