package com.tianji.mall.consumer;

import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private MqConsumedGuard mqConsumedGuard;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @BeforeEach
    void setUp() {
        // 默认首次消费（放行）；具体去重场景在测试内覆盖
        when(mqConsumedGuard.isFirstConsumption(anyLong(), anyString())).thenReturn(true);
    }

    @Test
    void shouldCreateNotificationForShippedEvent() {
        OrderEvent event = buildEvent("SHIPPED");
        doNothing().when(notificationService).createNotification(anyLong(), anyString(), anyString(), anyString(), anyLong());

        notificationConsumer.onMessage(event);

        verify(notificationService).createNotification(
                eq(1L), eq("ORDER_SHIPPED"), eq("订单已发货"),
                contains("已发货"), eq(100L));
    }

    @Test
    void shouldCreateNotificationForCompletedEvent() {
        OrderEvent event = buildEvent("COMPLETED");
        doNothing().when(notificationService).createNotification(anyLong(), anyString(), anyString(), anyString(), anyLong());

        notificationConsumer.onMessage(event);

        verify(notificationService).createNotification(
                eq(1L), eq("ORDER_COMPLETED"), eq("订单已完成"),
                contains("已确认收货"), eq(100L));
    }

    @Test
    void shouldIgnoreUnknownEvent() {
        OrderEvent event = buildEvent("UNKNOWN");

        notificationConsumer.onMessage(event);

        verify(notificationService, never()).createNotification(anyLong(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldSkipDuplicateEvent() {
        // 重投消息：幂等键已存在 → 跳过，不重复生成通知
        OrderEvent event = buildEvent("SHIPPED");
        when(mqConsumedGuard.isFirstConsumption(100L, "SHIPPED")).thenReturn(false);

        notificationConsumer.onMessage(event);

        verify(mqConsumedGuard).isFirstConsumption(100L, "SHIPPED");
        verify(notificationService, never()).createNotification(anyLong(), anyString(), anyString(), anyString(), anyLong());
    }

    private OrderEvent buildEvent(String eventType) {
        return new OrderEvent(100L, 1L, "202407160001", BigDecimal.valueOf(6999), eventType, LocalDateTime.now());
    }
}
