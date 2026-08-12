package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.RefundRequest;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Refund;
import com.tianji.mall.entity.RefundItem;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.RefundItemMapper;
import com.tianji.mall.mapper.RefundMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundMapper refundMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private RefundItemMapper refundItemMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private PayFeignClient payFeignClient;
    @Mock
    private ShopService shopService;
    @Mock
    private ProductMapper productMapper;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(orderMapper, orderItemMapper, refundItemMapper,
                orderService, payFeignClient, shopService, productMapper);
        ReflectionTestUtils.setField(refundService, "baseMapper", refundMapper);
    }

    // ============ requestRefund ============

    @Test
    void shouldRequestPerItemRefund() {
        Order order = buildOrder(1L, 100L, 2);
        // 无优惠订单：实付等于明细原价总额（price 500 × qty 2 = 1000），分摊比例 1:1
        order.setTotalAmount(BigDecimal.valueOf(1000));
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrderId(1L);
        item.setProductId(100L);
        item.setPrice(BigDecimal.valueOf(500));
        item.setQuantity(2);
        item.setProductName("Test Product");
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        when(refundMapper.insert(any(Refund.class))).thenReturn(1);
        when(refundItemMapper.insert(any(RefundItem.class))).thenReturn(1);

        RefundRequest req = new RefundRequest();
        req.setReason("不想要了");
        req.setRefundType("REFUND_ONLY");
        RefundRequest.RefundItemRequest itemReq = new RefundRequest.RefundItemRequest();
        itemReq.setOrderItemId(10L);
        itemReq.setProductId(100L);
        itemReq.setQuantity(1);
        req.setItems(List.of(itemReq));

        Refund refund = refundService.requestRefund(100L, 1L, req);

        assertThat(refund.getOrderId()).isEqualTo(1L);
        assertThat(refund.getUserId()).isEqualTo(100L);
        assertThat(refund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(refund.getRefundType()).isEqualTo("REFUND_ONLY");
        verify(refundMapper).insert(any(Refund.class));
        verify(refundItemMapper).insert(any(RefundItem.class));
    }

    @Test
    void shouldRequestReturnRefund() {
        Order order = buildOrder(1L, 100L, 3); // SHIPPED
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrderId(1L);
        item.setProductId(100L);
        item.setPrice(BigDecimal.valueOf(300));
        item.setQuantity(1);
        item.setProductName("Test Product");
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        when(refundMapper.insert(any(Refund.class))).thenReturn(1);
        when(refundItemMapper.insert(any(RefundItem.class))).thenReturn(1);

        RefundRequest req = new RefundRequest();
        req.setReason("质量有问题");
        req.setRefundType("RETURN_REFUND");
        RefundRequest.RefundItemRequest itemReq = new RefundRequest.RefundItemRequest();
        itemReq.setOrderItemId(10L);
        itemReq.setQuantity(1);
        req.setItems(List.of(itemReq));

        Refund refund = refundService.requestRefund(100L, 1L, req);

        assertThat(refund.getRefundType()).isEqualTo("RETURN_REFUND");
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderMapper.selectByIdForUpdate(999L)).thenReturn(null);

        RefundRequest req = new RefundRequest();
        req.setReason("reason");
        req.setItems(List.of(new RefundRequest.RefundItemRequest()));

        assertThatThrownBy(() -> refundService.requestRefund(100L, 999L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderNotBelongsToUser() {
        Order order = buildOrder(1L, 999L, 2);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);

        RefundRequest req = new RefundRequest();
        req.setReason("reason");
        req.setItems(List.of(new RefundRequest.RefundItemRequest()));

        assertThatThrownBy(() -> refundService.requestRefund(100L, 1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderIsPending() {
        Order order = buildOrder(1L, 100L, 1);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);

        RefundRequest req = new RefundRequest();
        req.setReason("reason");
        req.setItems(List.of(new RefundRequest.RefundItemRequest()));

        assertThatThrownBy(() -> refundService.requestRefund(100L, 1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("当前订单状态不可退款");
    }

    @Test
    void shouldThrowWhenRefundAlreadyExists() {
        Order order = buildOrder(1L, 100L, 2);
        when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        RefundRequest req = new RefundRequest();
        req.setReason("reason");
        req.setItems(List.of(new RefundRequest.RefundItemRequest()));

        assertThatThrownBy(() -> refundService.requestRefund(100L, 1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请已提交");
    }

    // ============ returnShip ============

    @Test
    void shouldReturnShip() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setUserId(100L);
        refund.setRefundType("RETURN_REFUND");
        refund.setStatus("processing");
        when(refundMapper.selectById(1L)).thenReturn(refund);
        when(refundMapper.updateById(any(Refund.class))).thenReturn(1);

        refundService.returnShip(100L, 1L, "SF12345678", "顺丰速运");

        verify(refundMapper).updateById(any(Refund.class));
    }

    @Test
    void shouldThrowWhenReturnShipOnWrongType() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setUserId(100L);
        refund.setRefundType("REFUND_ONLY");
        refund.setStatus("processing");
        when(refundMapper.selectById(1L)).thenReturn(refund);

        assertThatThrownBy(() -> refundService.returnShip(100L, 1L, "SF123", "顺丰"))
                .isInstanceOf(BizException.class)
                .hasMessage("仅退货退款类型可填写快递单号");
    }

    // ============ confirmReceive ============

    @Test
    void shouldConfirmReceive() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setUserId(100L);
        refund.setOrderId(10L);
        refund.setAmount(BigDecimal.valueOf(500));
        refund.setReason("test");
        refund.setRefundType("RETURN_REFUND");
        refund.setReturnStatus("SHIPPED");
        refund.setStatus("processing");
        when(refundMapper.selectById(1L)).thenReturn(refund);
        when(refundMapper.updateById(any(Refund.class))).thenReturn(1);

        // admin 操作（shopId=null）跳过归属校验
        refundService.confirmReceive(1L, null);

        // called twice: returnStatus update + executeRefund status update
        verify(refundMapper, times(2)).updateById(any(Refund.class));
    }

    @Test
    void shouldConfirmReceiveWithOwnedShop() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setUserId(100L);
        refund.setOrderId(10L);
        refund.setAmount(BigDecimal.valueOf(500));
        refund.setRefundType("RETURN_REFUND");
        refund.setReturnStatus("SHIPPED");
        refund.setStatus("processing");
        when(refundMapper.selectById(1L)).thenReturn(refund);
        when(refundMapper.updateById(any(Refund.class))).thenReturn(1);

        // 退款商品属于该店铺 → 通过校验
        RefundItem item = new RefundItem();
        item.setRefundId(1L);
        item.setProductId(100L);
        when(refundItemMapper.selectByRefundId(1L)).thenReturn(List.of(item));
        Product product = new Product();
        product.setId(100L);
        product.setShopId(9L);
        when(productMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(product));

        refundService.confirmReceive(1L, 9L);

        verify(refundMapper, times(2)).updateById(any(Refund.class));
    }

    @Test
    void shouldThrowWhenConfirmReceiveForeignShop() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setRefundType("RETURN_REFUND");
        refund.setReturnStatus("SHIPPED");
        when(refundMapper.selectById(1L)).thenReturn(refund);

        // 退款商品属于其他店铺 → 拒绝
        RefundItem item = new RefundItem();
        item.setRefundId(1L);
        item.setProductId(100L);
        when(refundItemMapper.selectByRefundId(1L)).thenReturn(List.of(item));
        Product product = new Product();
        product.setId(100L);
        product.setShopId(99L);
        when(productMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(product));

        assertThatThrownBy(() -> refundService.confirmReceive(1L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不属于本店");
    }

    @Test
    void shouldThrowWhenConfirmReceiveNotShipped() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setRefundType("RETURN_REFUND");
        refund.setReturnStatus(null);
        when(refundMapper.selectById(1L)).thenReturn(refund);

        assertThatThrownBy(() -> refundService.confirmReceive(1L, null))
                .isInstanceOf(BizException.class)
                .hasMessage("买家尚未寄回商品");
    }

    // ============ getRefundDetail ============

    @Test
    void shouldGetRefundDetail() {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setOrderId(10L);
        refund.setUserId(100L);
        refund.setAmount(BigDecimal.valueOf(500));
        when(refundMapper.selectById(1L)).thenReturn(refund);
        when(refundItemMapper.selectByRefundId(1L)).thenReturn(List.of());

        Map<String, Object> detail = refundService.getRefundDetail(100L, 1L);

        assertThat(detail).containsKeys("refund", "items");
    }

    // ============ getMyRefunds ============

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetMyRefunds() {
        Page<Refund> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(new Refund()));
        when(refundMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<Refund> result = refundService.getMyRefunds(100L, 1, 20, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    // ============ getSellerPendingRefunds ============

    @Test
    void shouldGetSellerPendingRefunds() {
        Shop shop = new Shop();
        shop.setId(5L);
        when(shopService.getBySellerId(100L)).thenReturn(shop);
        Refund refund = new Refund();
        refund.setId(1L);
        when(refundMapper.selectSellerPendingRefunds(5L)).thenReturn(List.of(refund));

        List<Refund> result = refundService.getSellerPendingRefunds(100L);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldReturnEmptyWhenSellerHasNoShop() {
        when(shopService.getBySellerId(100L)).thenReturn(null);

        assertThat(refundService.getSellerPendingRefunds(100L)).isEmpty();
    }

    // ============ helpers ============

    private Order buildOrder(Long id, Long userId, int status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("20260101000000" + String.format("%06d", id));
        order.setUserId(userId);
        order.setTotalAmount(BigDecimal.valueOf(2000));
        order.setStatus(status);
        order.setAddressId(1L);
        return order;
    }
}
