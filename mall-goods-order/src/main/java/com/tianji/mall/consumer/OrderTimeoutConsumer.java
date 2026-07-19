package com.tianji.mall.consumer;

import com.tianji.mall.entity.Order;
import com.tianji.mall.service.OrderService;
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
        consumerGroup = "order-timeout-consumer",
        selectorExpression = "TIMEOUT_CHECK")
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.parseLong(orderIdStr);
        log.info("超时检查: orderId={}", orderId);
        Order order = orderService.getById(orderId);
        if (order == null) {
            log.warn("订单不存在: {}", orderId);
            return;
        }
        if (order.getStatus() == 1) { // 仍为 PENDING
            orderService.cancelOrderByTimeout(orderId);
            log.info("订单超时自动取消: orderId={}", orderId);
        }
    }
}
