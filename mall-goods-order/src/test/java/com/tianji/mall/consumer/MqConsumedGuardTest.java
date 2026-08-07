package com.tianji.mall.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqConsumedGuardTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @InjectMocks
    private MqConsumedGuard guard;

    @BeforeEach
    void setUp() {
        // doReturn 避免 RedissonClient#getBucket 泛型推断（RBucket<Object> vs RBucket<String>）的编译问题
        doReturn(bucket).when(redissonClient).getBucket(anyString());
    }

    @Test
    void shouldAllowFirstConsumption() {
        when(bucket.trySet("1", 24, TimeUnit.HOURS)).thenReturn(true);

        assertThat(guard.isFirstConsumption(1L, "PAID")).isTrue();

        verify(redissonClient).getBucket("mq:consumed:1:PAID");
        verify(bucket).trySet("1", 24, TimeUnit.HOURS);
    }

    @Test
    void shouldSkipDuplicateConsumption() {
        when(bucket.trySet("1", 24, TimeUnit.HOURS)).thenReturn(false);

        assertThat(guard.isFirstConsumption(1L, "PAID")).isFalse();
    }

    @Test
    void shouldDegradeWhenRedisFails() {
        when(bucket.trySet(anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("redis down"));

        // Redis 异常降级为放行，不阻塞消费
        assertThat(guard.isFirstConsumption(1L, "PAID")).isTrue();
    }

    @Test
    void shouldReleaseClaim() {
        guard.release(1L, "PAID");

        verify(redissonClient).getBucket("mq:consumed:1:PAID");
        verify(bucket).delete();
    }
}
