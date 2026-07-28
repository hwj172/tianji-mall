package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.ReviewCreateRequest;
import com.tianji.mall.dto.ReviewResponse;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Review;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewServiceIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private com.tianji.mall.feign.PayFeignClient payFeignClient;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ShopService shopService;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    private final Long userId = 1L;
    private Long orderId;
    private Long productId;

    @BeforeEach
    void setUp() {
        reviewMapper.delete(new LambdaQueryWrapper<>());
        orderItemMapper.delete(new LambdaQueryWrapper<>());
        orderMapper.delete(new LambdaQueryWrapper<>());
        productMapper.delete(new LambdaQueryWrapper<>());

        productId = insertProduct("测试商品", BigDecimal.valueOf(100), 10);
        orderId = insertOrder(userId, 4); // status=4 已完成
        insertOrderItem(orderId, productId, "测试商品", BigDecimal.valueOf(100), 2);
    }

    // ==================== createReview ====================

    @Test
    void shouldCreateReviewSuccessfully() {
        ReviewCreateRequest req = buildCreateRequest(orderId, productId, 5, "非常好用");

        reviewService.createReview(userId, req);

        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId));
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getRating()).isEqualTo(5);
        assertThat(reviews.get(0).getContent()).isEqualTo("非常好用");
        assertThat(reviews.get(0).getProductId()).isEqualTo(productId);
        assertThat(reviews.get(0).getOrderId()).isEqualTo(orderId);
        assertThat(reviews.get(0).getStatus()).isEqualTo(1); // 默认审核通过
    }

    @Test
    void shouldThrowWhenOrderNotCompleted() {
        // status=2 已付款，未完成
        Long pendingOrderId = insertOrder(userId, 2);
        insertOrderItem(pendingOrderId, productId, "测试商品", BigDecimal.valueOf(100), 1);
        ReviewCreateRequest req = buildCreateRequest(pendingOrderId, productId, 5, "评价");

        assertThatThrownBy(() -> reviewService.createReview(userId, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已完成");
    }

    @Test
    void shouldThrowWhenProductNotInOrder() {
        Long otherProductId = insertProduct("其他商品", BigDecimal.valueOf(50), 5);
        ReviewCreateRequest req = buildCreateRequest(orderId, otherProductId, 4, "评价");

        assertThatThrownBy(() -> reviewService.createReview(userId, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不包含此商品");
    }

    @Test
    void shouldThrowWhenDuplicateReview() {
        ReviewCreateRequest req = buildCreateRequest(orderId, productId, 5, "第一次评价");
        reviewService.createReview(userId, req);

        // 重复评价同一订单同一商品
        assertThatThrownBy(() -> reviewService.createReview(userId, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已评价");
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        ReviewCreateRequest req = buildCreateRequest(99999L, productId, 5, "评价");

        assertThatThrownBy(() -> reviewService.createReview(userId, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("订单");
    }

    // ==================== getProductReviews ====================

    @Test
    void shouldGetProductReviewsPaginated() {
        // 创建两条评价
        ReviewCreateRequest req1 = buildCreateRequest(orderId, productId, 5, "很好");
        reviewService.createReview(userId, req1);

        // 第二笔订单
        Long orderId2 = insertOrder(userId, 4);
        insertOrderItem(orderId2, productId, "测试商品", BigDecimal.valueOf(100), 1);
        ReviewCreateRequest req2 = buildCreateRequest(orderId2, productId, 3, "一般");
        reviewService.createReview(userId, req2);

        List<ReviewResponse> reviews = reviewService.getProductReviews(productId, 1, 10);

        assertThat(reviews).hasSize(2);
        assertThat(reviews).extracting(ReviewResponse::getRating).containsExactlyInAnyOrder(5, 3);
    }

    @Test
    void shouldGetEmptyReviewsForProduct() {
        List<ReviewResponse> reviews = reviewService.getProductReviews(productId, 1, 10);
        assertThat(reviews).isEmpty();
    }

    // ==================== getMyReviews ====================

    @Test
    void shouldGetMyReviewsPaginated() {
        ReviewCreateRequest req = buildCreateRequest(orderId, productId, 4, "还行");
        reviewService.createReview(userId, req);

        long otherUserId = 2L;
        Long otherOrderId = insertOrder(otherUserId, 4);
        insertOrderItem(otherOrderId, productId, "测试商品", BigDecimal.valueOf(100), 1);
        ReviewCreateRequest otherReq = buildCreateRequest(otherOrderId, productId, 2, "差评");
        reviewService.createReview(otherUserId, otherReq);

        List<ReviewResponse> myReviews = reviewService.getMyReviews(userId, 1, 10);

        assertThat(myReviews).hasSize(1);
        assertThat(myReviews.get(0).getRating()).isEqualTo(4);
        assertThat(myReviews.get(0).getContent()).isEqualTo("还行");
    }

    // ==================== helpers ====================

    private ReviewCreateRequest buildCreateRequest(Long orderId, Long productId, int rating, String content) {
        ReviewCreateRequest req = new ReviewCreateRequest();
        req.setOrderId(orderId);
        req.setProductId(productId);
        req.setRating(rating);
        req.setContent(content);
        return req;
    }

    private Long insertProduct(String name, BigDecimal price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setCategoryId(1L);
        p.setStatus(1);
        productMapper.insert(p);
        return p.getId();
    }

    private Long insertOrder(Long userId, int status) {
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis() + "_" + userId);
        order.setUserId(userId);
        order.setTotalAmount(BigDecimal.valueOf(200));
        order.setStatus(status);
        orderMapper.insert(order);
        return order.getId();
    }

    private Long insertOrderItem(Long orderId, Long productId, String productName, BigDecimal price, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName(productName);
        item.setPrice(price);
        item.setQuantity(quantity);
        orderItemMapper.insert(item);
        return item.getId();
    }
}
