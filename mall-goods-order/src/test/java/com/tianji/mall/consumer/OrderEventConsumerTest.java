package com.tianji.mall.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.service.CouponService;
import com.tianji.mall.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @InjectMocks
    private OrderEventConsumer consumer;

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
    void shouldCatchExceptionDuringSalesUpdate() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "PAID", LocalDateTime.now());
        when(orderItemMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleCancelledEvent() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "CANCELLED", LocalDateTime.now());

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();

        verify(couponService).restoreCoupon(1L);
    }

    @Test
    void shouldCatchExceptionDuringCouponRestore() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "CANCELLED", LocalDateTime.now());
        doThrow(new RuntimeException("DB down")).when(couponService).restoreCoupon(1L);

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleUnknownEventType() {
        OrderEvent event = new OrderEvent(1L, 100L, "ORD001", BigDecimal.valueOf(1000), "UNKNOWN", LocalDateTime.now());

        assertThatCode(() -> consumer.onMessage(event)).doesNotThrowAnyException();
    }
}
