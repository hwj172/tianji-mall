package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private AddressMapper addressMapper;

    private Long addressId;
    private Long productId;

    @BeforeEach
    void setUp() throws InterruptedException {
        orderItemMapper.delete(new LambdaQueryWrapper<>());
        orderMapper.delete(new LambdaQueryWrapper<>());
        cartItemMapper.delete(new LambdaQueryWrapper<>());
        productMapper.delete(new LambdaQueryWrapper<>());
        addressMapper.delete(new LambdaQueryWrapper<>());

        // 分布式锁 mock：所有锁操作默认成功
        RLock mockLock = mock(RLock.class);
        when(mockLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        when(redissonClient.getMultiLock(any())).thenReturn(mockLock);

        addressId = insertAddress(1L, "张三", "13800000001");
        productId = insertProduct("iPhone 15", BigDecimal.valueOf(6999), 10);
    }

    // ==================== createOrder ====================

    @Test
    void shouldCreateOrderSuccessfully() {
        Long cartItemId = insertCartItem(1L, productId, 2);
        OrderCreateRequest req = buildCreateRequest(addressId, List.of(cartItemId));

        Order order = orderService.createOrder(1L, req);

        // 订单已创建
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(13998));

        // 订单项已创建
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductName()).isEqualTo("iPhone 15");
        assertThat(items.get(0).getQuantity()).isEqualTo(2);

        // 库存已扣减
        Product product = productMapper.selectById(productId);
        assertThat(product.getStock()).isEqualTo(8);

        // 购物车已清空
        List<CartItem> cartItems = cartItemMapper.selectList(new LambdaQueryWrapper<>());
        assertThat(cartItems).isEmpty();
    }

    @Test
    void shouldThrowWhenStockInsufficient() {
        // 只有 1 件库存，但购物车要 5 件
        Long lowStockId = insertProduct("限量商品", BigDecimal.valueOf(100), 1);
        Long cartItemId = insertCartItem(1L, lowStockId, 5);
        OrderCreateRequest req = buildCreateRequest(addressId, List.of(cartItemId));

        assertThatThrownBy(() -> orderService.createOrder(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");

        // 订单未创建
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<>());
        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldThrowWhenAddressNotFound() {
        Long cartItemId = insertCartItem(1L, productId, 2);
        OrderCreateRequest req = buildCreateRequest(99999L, List.of(cartItemId));

        assertThatThrownBy(() -> orderService.createOrder(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("收货地址不存在");
    }

    // ==================== cancelOrder ====================

    @Test
    void shouldCancelOrderAndRestoreStock() {
        Long cartItemId = insertCartItem(1L, productId, 2);
        OrderCreateRequest req = buildCreateRequest(addressId, List.of(cartItemId));
        Order order = orderService.createOrder(1L, req);

        orderService.cancelOrder(1L, order.getId());

        // 状态已变更
        Order cancelled = orderMapper.selectById(order.getId());
        assertThat(cancelled.getStatus()).isEqualTo(5);

        // 库存已恢复
        Product product = productMapper.selectById(productId);
        assertThat(product.getStock()).isEqualTo(10);
    }

    // ==================== helpers ====================

    private Long insertAddress(Long userId, String name, String phone) {
        Address addr = new Address();
        addr.setUserId(userId);
        addr.setReceiverName(name);
        addr.setPhone(phone);
        addr.setProvince("广东省");
        addr.setCity("深圳市");
        addr.setDistrict("南山区");
        addr.setDetail("科技园");
        addr.setIsDefault(1);
        addressMapper.insert(addr);
        return addr.getId();
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

    private Long insertCartItem(Long userId, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setChecked(1);
        cartItemMapper.insert(item);
        return item.getId();
    }

    private OrderCreateRequest buildCreateRequest(Long addressId, List<Long> cartItemIds) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        req.setCartItemIds(cartItemIds);
        return req;
    }
}
