package com.tianji.mall.consumer;

import com.tianji.mall.entity.Order;
import com.tianji.mall.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutConsumerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderTimeoutConsumer consumer;

    @Test
    void shouldCancelPendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(1); // PENDING
        when(orderService.getById(1L)).thenReturn(order);

        consumer.onMessage("1");

        verify(orderService).cancelOrderByTimeout(1L);
    }

    @Test
    void shouldSkipNonPendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(2); // PAID
        when(orderService.getById(1L)).thenReturn(order);

        consumer.onMessage("1");

        verify(orderService, never()).cancelOrderByTimeout(anyLong());
    }

    @Test
    void shouldSkipNonExistentOrder() {
        when(orderService.getById(999L)).thenReturn(null);

        consumer.onMessage("999");

        verify(orderService, never()).cancelOrderByTimeout(anyLong());
    }
}
