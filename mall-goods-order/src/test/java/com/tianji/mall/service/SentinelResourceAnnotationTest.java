package com.tianji.mall.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.RefundRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证关键方法已加 @SentinelResource。
 * 测试中 Sentinel 被禁用（spring.cloud.sentinel.enabled=false），注解无运行时副作用，仅反射断言存在。
 */
class SentinelResourceAnnotationTest {

    @Test
    void createOrderHasSentinelResource() throws Exception {
        Method m = OrderService.class.getMethod("createOrder", Long.class, OrderCreateRequest.class);
        SentinelResource sr = m.getAnnotation(SentinelResource.class);
        assertThat(sr).isNotNull();
        assertThat(sr.value()).isEqualTo("createOrder");
        assertThat(sr.blockHandler()).isEqualTo("createOrderBlockHandler");
    }

    @Test
    void requestRefundHasSentinelResource() throws Exception {
        Method m = RefundService.class.getMethod("requestRefund", Long.class, Long.class, RefundRequest.class);
        SentinelResource sr = m.getAnnotation(SentinelResource.class);
        assertThat(sr).isNotNull();
        assertThat(sr.value()).isEqualTo("requestRefund");
        assertThat(sr.blockHandler()).isEqualTo("requestRefundBlockHandler");
    }
}
