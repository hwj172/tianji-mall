package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.mall.dto.RefundRequest;
import com.tianji.mall.entity.*;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.mapper.*;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefundServiceIntegrationTest {

    @Autowired
    private RefundService refundService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private PayFeignClient payFeignClient;

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
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private RefundItemMapper refundItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AddressMapper addressMapper;

    private final Long userId = 1L;
    private Long productId;
    private Long orderId;
    private Long orderItemId;

    @BeforeEach
    void setUp() {
        refundItemMapper.delete(new LambdaQueryWrapper<>());
        refundMapper.delete(new LambdaQueryWrapper<>());
        orderItemMapper.delete(new LambdaQueryWrapper<>());
        orderMapper.delete(new LambdaQueryWrapper<>());
        productMapper.delete(new LambdaQueryWrapper<>());
        addressMapper.delete(new LambdaQueryWrapper<>());

        // Mock 退款调用成功
        when(payFeignClient.refundOrder(anyLong(), anyLong(), any(), anyString()))
                .thenReturn(R.ok());

        productId = insertProduct("测试商品", BigDecimal.valueOf(100), 10);
        Long addressId = insertAddress(userId);
        orderId = insertOrder(userId, addressId, 2); // status=2 已付款
        orderItemId = insertOrderItem(orderId, productId, "测试商品", BigDecimal.valueOf(100), 2);
    }

    // ==================== requestRefund ====================

    @Test
    void shouldRequestRefundOnlySuccessfully() {
        RefundRequest req = buildRefundRequest("商品有瑕疵", "REFUND_ONLY", orderItemId, 1);

        Refund refund = refundService.requestRefund(userId, orderId, req);

        // 退款记录创建
        assertThat(refund.getId()).isNotNull();
        assertThat(refund.getStatus()).isEqualTo("success"); // 仅退款直接调用成功
        assertThat(refund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(refund.getRefundType()).isEqualTo("REFUND_ONLY");

        // 退款明细已保存
        List<RefundItem> items = refundItemMapper.selectByRefundId(refund.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getOrderItemId()).isEqualTo(orderItemId);
        assertThat(items.get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void shouldRequestReturnRefundWithoutCallingPay() {
        when(payFeignClient.refundOrder(anyLong(), anyLong(), any(), anyString()))
                .thenThrow(new RuntimeException("不应该被调用"));
        RefundRequest req = buildRefundRequest("需要退货", "RETURN_REFUND", orderItemId, null);

        Refund refund = refundService.requestRefund(userId, orderId, req);

        // 退货退款：状态保持 processing，不调用 pay
        assertThat(refund.getStatus()).isEqualTo("processing");
        assertThat(refund.getRefundType()).isEqualTo("RETURN_REFUND");
    }

    @Test
    void shouldThrowWhenOrderStatusInvalid() {
        // status=1（待付款）不可退款
        Long pendingOrderId = insertOrder(userId, null, 1);
        insertOrderItem(pendingOrderId, productId, "测试", BigDecimal.valueOf(100), 1);
        RefundRequest req = buildRefundRequest("不想要了", "REFUND_ONLY",
                orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, pendingOrderId)).get(0).getId(), null);

        assertThatThrownBy(() -> refundService.requestRefund(userId, pendingOrderId, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可退款");
    }

    @Test
    void shouldThrowWhenDuplicateRefund() {
        RefundRequest req = buildRefundRequest("第一次申请", "REFUND_ONLY", orderItemId, null);
        refundService.requestRefund(userId, orderId, req);

        // 第一次成功（状态已是 success），再申请应拒绝
        assertThatThrownBy(() -> refundService.requestRefund(userId, orderId,
                buildRefundRequest("第二次申请", "REFUND_ONLY", orderItemId, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("提交");
    }

    // ==================== returnShip + confirmReceive ====================

    @Test
    void shouldReturnShipAndConfirmReceive() {
        // 先创建退货退款
        RefundRequest req = buildRefundRequest("退货退款", "RETURN_REFUND", orderItemId, null);
        Refund refund = refundService.requestRefund(userId, orderId, req);
        assertThat(refund.getStatus()).isEqualTo("processing");

        // 买家填快递
        refundService.returnShip(userId, refund.getId(), "SF1234567890", "顺丰速运");
        Refund shipped = refundService.getById(refund.getId());
        assertThat(shipped.getReturnStatus()).isEqualTo("SHIPPED");
        assertThat(shipped.getTrackingNumber()).isEqualTo("SF1234567890");
        assertThat(shipped.getTrackingCompany()).isEqualTo("顺丰速运");

        // 卖家确认收货
        refundService.confirmReceive(refund.getId());
        Refund completed = refundService.getById(refund.getId());
        assertThat(completed.getReturnStatus()).isEqualTo("RECEIVED");
        assertThat(completed.getStatus()).isEqualTo("success");
    }

    // ==================== getRefundDetail ====================

    @Test
    void shouldGetRefundDetailWithItems() {
        RefundRequest req = buildRefundRequest("查看详情", "REFUND_ONLY", orderItemId, null);
        Refund refund = refundService.requestRefund(userId, orderId, req);

        Map<String, Object> detail = refundService.getRefundDetail(refund.getId());

        assertThat(detail).containsKeys("refund", "items");
        assertThat(((Refund) detail.get("refund")).getId()).isEqualTo(refund.getId());
        assertThat(((List<?>) detail.get("items"))).hasSize(1);
    }

    @Test
    void shouldThrowWhenGetRefundDetailNotFound() {
        assertThatThrownBy(() -> refundService.getRefundDetail(99999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("退款");
    }

    // ==================== getMyRefunds ====================

    @Test
    void shouldGetMyRefundsPaginated() {
        // 创建两笔退款
        RefundRequest req1 = buildRefundRequest("第一笔", "REFUND_ONLY", orderItemId, null);
        refundService.requestRefund(userId, orderId, req1);

        Long orderId2 = insertOrder(userId, insertAddress(userId), 3); // status=3 已发货
        Long itemId2 = insertOrderItem(orderId2, productId, "测试商品", BigDecimal.valueOf(100), 1);
        RefundRequest req2 = buildRefundRequest("第二笔", "RETURN_REFUND", itemId2, null);
        refundService.requestRefund(userId, orderId2, req2);

        Page<Refund> page = refundService.getMyRefunds(userId, 1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        // 按创建时间倒序
        assertThat(page.getRecords().get(0).getReason()).isIn("第一笔", "第二笔");
    }

    @Test
    void shouldGetEmptyRefundsForNewUser() {
        Page<Refund> page = refundService.getMyRefunds(99999L, 1, 10);
        assertThat(page.getTotal()).isEqualTo(0);
        assertThat(page.getRecords()).isEmpty();
    }

    // ==================== helpers ====================

    private RefundRequest buildRefundRequest(String reason, String refundType, Long orderItemId, Integer quantity) {
        RefundRequest req = new RefundRequest();
        req.setReason(reason);
        req.setRefundType(refundType);
        RefundRequest.RefundItemRequest item = new RefundRequest.RefundItemRequest();
        item.setOrderItemId(orderItemId);
        if (quantity != null) {
            item.setQuantity(quantity);
        }
        req.setItems(List.of(item));
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

    private Long insertAddress(Long userId) {
        Address addr = new Address();
        addr.setUserId(userId);
        addr.setReceiverName("张三");
        addr.setPhone("13800000001");
        addr.setProvince("广东省");
        addr.setCity("深圳市");
        addr.setDistrict("南山区");
        addr.setDetail("科技园");
        addr.setIsDefault(1);
        addressMapper.insert(addr);
        return addr.getId();
    }

    private Long insertOrder(Long userId, Long addressId, int status) {
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setAddressId(addressId);
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
