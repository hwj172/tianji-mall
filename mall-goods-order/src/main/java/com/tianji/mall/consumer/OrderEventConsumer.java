package com.tianji.mall.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.service.CouponService;
import com.tianji.mall.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "order-event-consumer",
        selectorExpression = "*",
        maxReconsumeTimes = 3)
public class OrderEventConsumer implements RocketMQListener<OrderEvent> {

    private final OrderItemMapper orderItemMapper;
    private final ProductService productService;
    private final CouponService couponService;
    private final MqConsumedGuard mqConsumedGuard;

    @Override
    public void onMessage(OrderEvent event) {
        log.info("收到订单事件: orderId={}, orderNo={}, eventType={}, amount={}",
                event.getOrderId(), event.getOrderNo(),
                event.getEventType(), event.getTotalAmount());

        // 幂等去重：重复消息（重投/重放）直接跳过，避免销量/优惠券被重复处理
        if (!mqConsumedGuard.isFirstConsumption(event.getOrderId(), event.getEventType())) {
            log.info("重复事件已跳过(幂等去重): orderId={}, eventType={}",
                    event.getOrderId(), event.getEventType());
            return;
        }

        try {
            switch (event.getEventType()) {
                case "CREATED" -> log.info("订单已创建: orderId={}, orderNo={}, userId={}, amount={}",
                        event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
                case "PAID" -> handlePaid(event);
                case "CANCELLED" -> {
                    log.info("订单已取消: orderId={}, orderNo={}, userId={}, amount={}",
                            event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
                    couponService.restoreCoupon(event.getOrderId());
                }
                default -> log.warn("未知事件类型: {}", event.getEventType());
            }
        } catch (RuntimeException e) {
            // 处理失败释放占用，允许 RocketMQ 重投重试（避免重投被幂等键永久跳过）
            mqConsumedGuard.release(event.getOrderId(), event.getEventType());
            throw e;
        }
    }

    private void handlePaid(OrderEvent event) {
        log.info("订单已支付 - 更新商品销量: orderId={}", event.getOrderId());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, event.getOrderId()));
        for (OrderItem item : items) {
            productService.incrementSales(item.getProductId(), item.getQuantity());
        }
        log.info("商品销量更新完成: orderId={}, items={}", event.getOrderId(), items.size());
    }
}
