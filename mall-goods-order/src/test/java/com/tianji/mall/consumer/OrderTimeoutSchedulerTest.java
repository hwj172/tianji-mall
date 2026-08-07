package com.tianji.mall.consumer;

import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutSchedulerTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderTimeoutScheduler scheduler;

    @Test
    void shouldCancelAllExpiredOrders() {
        when(orderMapper.selectExpiredPendingOrderIds(any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L, 3L));

        scheduler.cancelExpiredOrders();

        verify(orderService).cancelOrderByTimeout(1L);
        verify(orderService).cancelOrderByTimeout(2L);
        verify(orderService).cancelOrderByTimeout(3L);
    }

    @Test
    void shouldDoNothingWhenNoExpiredOrders() {
        when(orderMapper.selectExpiredPendingOrderIds(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.cancelExpiredOrders();

        verify(orderService, never()).cancelOrderByTimeout(anyLong());
    }

    @Test
    void shouldHandleQueryFailureGracefully() {
        when(orderMapper.selectExpiredPendingOrderIds(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> scheduler.cancelExpiredOrders()).doesNotThrowAnyException();
        verify(orderService, never()).cancelOrderByTimeout(anyLong());
    }

    @Test
    void shouldContinueAfterSingleCancelFailure() {
        when(orderMapper.selectExpiredPendingOrderIds(any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("cancel failed")).when(orderService).cancelOrderByTimeout(1L);

        assertThatCode(() -> scheduler.cancelExpiredOrders()).doesNotThrowAnyException();
        verify(orderService).cancelOrderByTimeout(1L);
        verify(orderService).cancelOrderByTimeout(2L);
    }
}
