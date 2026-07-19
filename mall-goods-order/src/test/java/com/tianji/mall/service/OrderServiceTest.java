package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.OrderDetailResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private CartService cartService;
    @Mock
    private ProductService productService;
    @Mock
    private AddressService addressService;
    @Mock
    private org.redisson.api.RedissonClient redissonClient;
    @Mock
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() throws InterruptedException {
        orderService = new OrderService(orderItemMapper, cartService, productService, addressService, redissonClient, rocketMQTemplate);
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);

        // 分布式锁 mock：所有锁操作默认成功（lenient 避免非锁路径报 UnnecessaryStubbing）
        RLock mockLock = mock(RLock.class);
        lenient().when(mockLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        lenient().when(redissonClient.getMultiLock(any())).thenReturn(mockLock);
    }

    // ==================== createOrder ====================

    @Test
    void shouldThrowWhenAddressNotFound() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(999L);
        req.setCartItemIds(List.of(1L));

        when(addressService.getById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    @Test
    void shouldThrowWhenAddressNotBelongsToUser() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(1L);
        req.setCartItemIds(List.of(1L));

        Address foreignAddr = buildAddress(1L, 999L);
        when(addressService.getById(1L)).thenReturn(foreignAddr);

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    @Test
    void shouldThrowWhenCartItemsEmpty() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(1L);
        req.setCartItemIds(List.of(1L));

        Address addr = buildAddress(1L, 100L);
        when(addressService.getById(1L)).thenReturn(addr);
        when(cartService.listByIds(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车项不存在");
    }

    @Test
    void shouldThrowWhenCartItemNotBelongsToUser() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(1L);
        req.setCartItemIds(List.of(1L));

        Address addr = buildAddress(1L, 100L);
        CartItem foreignItem = buildCartItem(1L, 999L, 1L, 1);
        when(addressService.getById(1L)).thenReturn(addr);
        when(cartService.listByIds(List.of(1L))).thenReturn(List.of(foreignItem));

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车项不属于当前用户");
    }

    @Test
    void shouldThrowWhenCartItemNotChecked() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(1L);
        req.setCartItemIds(List.of(1L));

        Address addr = buildAddress(1L, 100L);
        CartItem uncheckedItem = buildCartItem(1L, 100L, 1L, 1);
        uncheckedItem.setChecked(0);
        when(addressService.getById(1L)).thenReturn(addr);
        when(cartService.listByIds(List.of(1L))).thenReturn(List.of(uncheckedItem));

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("请先选中商品");
    }

    @Test
    void shouldThrowWhenProductOffShelf() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(1L);
        req.setCartItemIds(List.of(1L));

        Address addr = buildAddress(1L, 100L);
        CartItem cartItem = buildCartItem(1L, 100L, 1L, 1);
        Product offShelfProduct = buildProduct(1L, "下架商品", 10, 0);

        when(addressService.getById(1L)).thenReturn(addr);
        when(cartService.listByIds(List.of(1L))).thenReturn(List.of(cartItem));
        when(productService.listByIds(List.of(1L))).thenReturn(List.of(offShelfProduct));

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已下架");
    }

    @Test
    void shouldThrowWhenStockInsufficient() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(1L);
        req.setCartItemIds(List.of(1L));

        Address addr = buildAddress(1L, 100L);
        CartItem cartItem = buildCartItem(1L, 100L, 1L, 20); // want 20
        Product product = buildProduct(1L, "iPhone", 5, 1); // only 5 in stock

        when(addressService.getById(1L)).thenReturn(addr);
        when(cartService.listByIds(List.of(1L))).thenReturn(List.of(cartItem));
        when(productService.listByIds(List.of(1L))).thenReturn(List.of(product));

        assertThatThrownBy(() -> orderService.createOrder(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
    }

    // ==================== cancelOrder ====================

    @Test
    void shouldCancelOrder() {
        Order order = buildOrder(1L, 100L, 1);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(order)).thenReturn(1);
        // 订单内有 1 个商品需要恢复库存
        OrderItem item = buildOrderItem(1L, 1L, 1L, 2);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));
        orderService.cancelOrder(100L, 1L);

        assertThat(order.getStatus()).isEqualTo(5); // 已取消
        verify(productService).restoreStock(1L, 2);
    }

    @Test
    void shouldThrowWhenCancelNonPendingOrder() {
        Order order = buildOrder(1L, 100L, 2); // 已付款状态
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(100L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("仅待付款订单可取消");
    }

    // ==================== payOrder ====================

    @Test
    void shouldPayOrder() {
        Order order = buildOrder(1L, 100L, 1);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(order)).thenReturn(1);

        orderService.payOrder(1L, 100L);

        assertThat(order.getStatus()).isEqualTo(2); // 已付款
        assertThat(order.getPayType()).isEqualTo(1); // 支付宝
    }

    @Test
    void shouldThrowWhenPayNonPendingOrder() {
        Order order = buildOrder(1L, 100L, 2); // already paid
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.payOrder(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单状态不允许支付");
    }

    @Test
    void shouldThrowWhenPayForeignOrder() {
        Order order = buildOrder(1L, 999L, 1); // belongs to user 999
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.payOrder(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不属于当前用户");
    }

    // ==================== getOrderList ====================

    @Test
    void shouldGetOrderList() {
        List<Order> orders = List.of(buildOrder(1L, 100L, 1), buildOrder(2L, 100L, 2));
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(orders);

        List<Order> result = orderService.getOrderList(100L);

        assertThat(result).hasSize(2);
    }

    // ==================== helpers ====================

    // ==================== shipOrder ====================

    @Test
    void shouldShipOrder() {
        Order order = buildOrder(1L, 100L, 2); // PAID
        when(orderMapper.selectById(1L)).thenReturn(order);

        orderService.shipOrder(1L, "顺丰", "SF123456");

        assertThat(order.getStatus()).isEqualTo(3);
        assertThat(order.getLogisticsCompany()).isEqualTo("顺丰");
        assertThat(order.getTrackingNumber()).isEqualTo("SF123456");
        verify(orderMapper).updateById(order);
    }

    @Test
    void shouldThrowWhenShipNonExistentOrder() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.shipOrder(999L, "顺丰", "SF123"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenShipNonPaidOrder() {
        Order order = buildOrder(1L, 100L, 1); // PENDING
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.shipOrder(1L, "顺丰", "SF123"))
                .isInstanceOf(BizException.class)
                .hasMessage("仅已付款订单可发货");
    }

    // ==================== getOrderListAdmin ====================

    @Test
    void shouldGetOrderListForAdmin() {
        @SuppressWarnings("unchecked")
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> mockPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        when(orderMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> result =
                orderService.getOrderListAdmin(1, 10, null);

        assertThat(result).isNotNull();
        verify(orderMapper).selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
                any(LambdaQueryWrapper.class));
    }

    // ==================== completeOrder ====================

    @Test
    void shouldCompleteOrder() {
        Order order = buildOrder(1L, 100L, 3); // SHIPPED
        when(orderMapper.selectById(1L)).thenReturn(order);

        orderService.completeOrder(1L);

        assertThat(order.getStatus()).isEqualTo(4);
        assertThat(order.getReceiveTime()).isNotNull();
        verify(orderMapper).updateById(order);
    }

    @Test
    void shouldThrowWhenCompleteNonExistentOrder() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.completeOrder(999L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenCompleteNonShippedOrder() {
        Order order = buildOrder(1L, 100L, 2); // PAID, not SHIPPED
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.completeOrder(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("仅已发货订单可完成");
    }

    // ==================== confirmReceive ====================

    @Test
    void shouldConfirmReceive() {
        Order order = buildOrder(1L, 100L, 3); // SHIPPED
        when(orderMapper.selectById(1L)).thenReturn(order);

        orderService.confirmReceive(100L, 1L);

        assertThat(order.getStatus()).isEqualTo(4);
        assertThat(order.getReceiveTime()).isNotNull();
        verify(orderMapper).updateById(order);
    }

    @Test
    void shouldThrowWhenConfirmReceiveNonExistentOrder() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.confirmReceive(100L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenConfirmReceiveForeignOrder() {
        Order order = buildOrder(1L, 999L, 3);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.confirmReceive(100L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenConfirmReceiveNonShippedOrder() {
        Order order = buildOrder(1L, 100L, 2); // PAID
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.confirmReceive(100L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("仅已发货订单可确认收货");
    }

    private Address buildAddress(Long id, Long userId) {
        Address addr = new Address();
        addr.setId(id);
        addr.setUserId(userId);
        addr.setReceiverName("张三");
        addr.setPhone("13800000000");
        addr.setProvince("广东");
        addr.setCity("深圳");
        addr.setDistrict("南山");
        addr.setDetail("科技园");
        return addr;
    }

    private CartItem buildCartItem(Long id, Long userId, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setChecked(1);
        return item;
    }

    private Product buildProduct(Long id, String name, int stock, int status) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(1000));
        p.setStock(stock);
        p.setStatus(status);
        return p;
    }

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

    private OrderItem buildOrderItem(Long id, Long orderId, Long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName("测试商品");
        item.setPrice(BigDecimal.valueOf(1000));
        item.setQuantity(quantity);
        return item;
    }
}
