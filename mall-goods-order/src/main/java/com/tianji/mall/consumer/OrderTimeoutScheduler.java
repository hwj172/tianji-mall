package com.tianji.mall.consumer;

import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时取消兜底轮询。
 * <p>RocketMQ 延迟消息（30min）为主，本定时任务每分钟扫描一次兜底：延迟消息丢失/发送失败时，
 * 仍能保证超时待付款订单被取消。
 * <p>复用 {@link OrderService#cancelOrderByTimeout}，其内部 CAS（status=1→5）保证幂等，
 * 与延迟消息路径并发时不会重复恢复库存/优惠券。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000)
    public void cancelExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(TIMEOUT);
        List<Long> expiredIds;
        try {
            expiredIds = orderMapper.selectExpiredPendingOrderIds(cutoff);
        } catch (Exception e) {
            log.error("超时取消兜底扫描查询失败", e);
            return;
        }
        if (expiredIds.isEmpty()) {
            return;
        }
        log.info("超时取消兜底扫描: 发现 {} 个超时待付款订单", expiredIds.size());
        for (Long orderId : expiredIds) {
            try {
                orderService.cancelOrderByTimeout(orderId);
            } catch (Exception e) {
                log.error("超时取消订单失败: orderId={}", orderId, e);
            }
        }
    }
}
