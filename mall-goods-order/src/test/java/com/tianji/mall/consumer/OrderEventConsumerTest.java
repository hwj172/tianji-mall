package com.tianji.mall.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.service.CouponService;
import com.tianji.mall.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductService productService;

    @Mock
    private CouponService couponService;

    @Mock
    private MqConsumedGuard mqConsumedGuard;

    @InjectMocks
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        // 默认首次消费（放行）；具体去重场景在测试内覆盖
        when(mqConsumedGuard.isFirstConsumption(anyLong(), anyString())).thenReturn(true);
    }

    @Test
    void shouldHandleCreatedEvent() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "CREATED", LocalDateTime.now());

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldIncrementSalesOnPaidEvent() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        OrderItem item = new OrderItem();
        item.setProductId(10L);
        item.setQuantity(3);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

        consumer.onMessage(event);

        verify(productService).incrementSales(10L, 3);
    }

    @Test
    void shouldHandlePaidEventWithMultipleItems() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        OrderItem item1 = new OrderItem();
        item1.setProductId(10L);
        item1.setQuantity(2);
        OrderItem item2 = new OrderItem();
        item2.setProductId(20L);
        item2.setQuantity(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item1, item2));

        consumer.onMessage(event);

        verify(productService).incrementSales(10L, 2);
        verify(productService).incrementSales(20L, 1);
    }

    @Test
    void shouldHandlePaidEventWithNoItems() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        consumer.onMessage(event);

        verify(productService, never()).incrementSales(anyLong(), anyInt());
    }

    @Test
    void shouldPropagateExceptionDuringSalesUpdate() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        when(orderItemMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> consumer.onMessage(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }

    @Test
    void shouldHandleCancelledEvent() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "CANCELLED", LocalDateTime.now());

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();

        verify(couponService).restoreCoupon(1L);
    }

    @Test
    void shouldPropagateExceptionDuringCouponRestore() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "CANCELLED", LocalDateTime.now());
        doThrow(new RuntimeException("DB down")).when(couponService).restoreCoupon(1L);

        assertThatThrownBy(() -> consumer.onMessage(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }

    @Test
    void shouldHandleUnknownEventType() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "UNKNOWN", LocalDateTime.now());

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipDuplicatePaidEvent() {
        // 重复事件（重投/重放）：幂等键已存在 → 跳过，不更新销量
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        when(mqConsumedGuard.isFirstConsumption(1L, "PAID")).thenReturn(false);

        consumer.onMessage(event);

        verify(mqConsumedGuard).isFirstConsumption(1L, "PAID");
        verify(productService, never()).incrementSales(anyLong(), anyInt());
    }

    @Test
    void shouldReleaseClaimWhenProcessingFails() {
        // 处理失败需释放幂等键，使 RocketMQ 重投的消息可再次处理
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        when(orderItemMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> consumer.onMessage(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");

        verify(mqConsumedGuard).release(1L, "PAID");
    }
}
