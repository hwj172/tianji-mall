package com.tianji.mall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * MQ 消费端幂等去重守卫。
 * <p>基于 Redis SETNX（Redisson {@link RBucket#trySet}）对 (orderId, eventType) 去重：
 * 首次消费写入键并放行；重复消息 {@code trySet} 返回 false 直接跳过。
 * 消费失败时调用 {@link #release} 释放占用，使 RocketMQ 重投的消息可再次处理，
 * 避免"先 SETNX 后处理失败"导致重投被永久跳过。
 * <p>Redis 异常时降级为放行（best-effort），不阻塞消费主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqConsumedGuard {

    private static final String KEY_PREFIX = "mq:consumed:";
    private static final long TTL_HOURS = 24;

    private final RedissonClient redissonClient;

    /**
     * 判断是否为首次消费（应继续处理）。
     *
     * @return true=首次消费；false=已消费过，应跳过
     */
    public boolean isFirstConsumption(Long orderId, String eventType) {
        String key = buildKey(orderId, eventType);
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.trySet("1", TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // Redis 不可用时降级为放行，避免消费被阻塞（best-effort）
            log.warn("MQ 幂等去重检查失败，降级为继续处理: key={}", key, e);
            return true;
        }
    }

    /**
     * 消费失败时释放占用，使重投消息可以再次处理。
     */
    public void release(Long orderId, String eventType) {
        String key = buildKey(orderId, eventType);
        try {
            redissonClient.getBucket(key).delete();
        } catch (Exception e) {
            log.warn("MQ 幂等去重释放失败: key={}", key, e);
        }
    }

    private String buildKey(Long orderId, String eventType) {
        return KEY_PREFIX + orderId + ":" + eventType;
    }
}
