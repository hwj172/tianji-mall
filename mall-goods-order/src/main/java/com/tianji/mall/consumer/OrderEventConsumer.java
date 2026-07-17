package com.tianji.mall.consumer;

import com.tianji.mall.dto.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-event-consumer",
        selectorExpression = "*")
public class OrderEventConsumer implements RocketMQListener<OrderEvent> {

    @Override
    public void onMessage(OrderEvent event) {
        log.info("收到订单事件: orderId={}, orderNo={}, eventType={}, amount={}",
                event.getOrderId(), event.getOrderNo(),
                event.getEventType(), event.getTotalAmount());

        switch (event.getEventType()) {
            case "CREATED" -> log.info("订单已创建 — 预留：发送通知短信, orderId={}", event.getOrderId());
            case "PAID" -> log.info("订单已支付 — 预留：更新商品销量, orderId={}", event.getOrderId());
            case "CANCELLED" -> log.info("订单已取消 — 预留：恢复优惠券、通知用户, orderId={}", event.getOrderId());
            default -> log.warn("未知事件类型: {}", event.getEventType());
        }
    }
}
