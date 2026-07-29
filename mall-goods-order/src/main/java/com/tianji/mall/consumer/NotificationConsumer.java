package com.tianji.mall.consumer;

import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "notification-consumer",
        selectorExpression = "*",
        maxReconsumeTimes = 3)
public class NotificationConsumer implements RocketMQListener<OrderEvent> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(OrderEvent event) {
        log.info("通知消费者收到事件: orderId={}, eventType={}", event.getOrderId(), event.getEventType());

        switch (event.getEventType()) {
            case "SHIPPED" -> notificationService.createNotification(
                    event.getUserId(),
                    "ORDER_SHIPPED",
                    "订单已发货",
                    "您的订单 " + event.getOrderNo() + " 已发货，请注意查收",
                    event.getOrderId());
            case "COMPLETED" -> notificationService.createNotification(
                    event.getUserId(),
                    "ORDER_COMPLETED",
                    "订单已完成",
                    "您的订单 " + event.getOrderNo() + " 已确认收货",
                    event.getOrderId());
            case "CREATED" -> notificationService.createNotification(
                    event.getUserId(),
                    "ORDER_CREATED",
                    "下单成功",
                    "您的订单 " + event.getOrderNo() + " 已创建，请尽快付款",
                    event.getOrderId());
            default -> log.debug("忽略事件类型: {}", event.getEventType());
        }
    }
}
