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
        selectorExpression = "*")
public class OrderEventConsumer implements RocketMQListener<OrderEvent> {

    private final OrderItemMapper orderItemMapper;
    private final ProductService productService;
    private final CouponService couponService;

    @Override
    public void onMessage(OrderEvent event) {
        log.info("收到订单事件: orderId={}, orderNo={}, eventType={}, amount={}",
                event.getOrderId(), event.getOrderNo(),
                event.getEventType(), event.getTotalAmount());

        switch (event.getEventType()) {
            case "CREATED" -> log.info("订单已创建: orderId={}, orderNo={}, userId={}, amount={}",
                    event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
            case "PAID" -> handlePaid(event);
            case "CANCELLED" -> {
                log.info("订单已取消: orderId={}, orderNo={}, userId={}, amount={}",
                        event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
                try {
                    couponService.restoreCoupon(event.getOrderId());
                } catch (Exception e) {
                    log.error("恢复优惠券失败: orderId={}", event.getOrderId(), e);
                }
            }
            default -> log.warn("未知事件类型: {}", event.getEventType());
        }
    }

    private void handlePaid(OrderEvent event) {
        log.info("订单已支付 - 更新商品销量: orderId={}", event.getOrderId());
        try {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getOrderId, event.getOrderId()));
            for (OrderItem item : items) {
                productService.incrementSales(item.getProductId(), item.getQuantity());
            }
            log.info("商品销量更新完成: orderId={}, items={}", event.getOrderId(), items.size());
        } catch (Exception e) {
            log.error("更新商品销量失败: orderId={}", event.getOrderId(), e);
        }
    }
}
