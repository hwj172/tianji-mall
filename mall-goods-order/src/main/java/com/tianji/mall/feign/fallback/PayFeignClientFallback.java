package com.tianji.mall.feign.fallback;

import com.tianji.common.result.R;
import com.tianji.mall.feign.PayFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class PayFeignClientFallback implements FallbackFactory<PayFeignClient> {

    @Override
    public PayFeignClient create(Throwable cause) {
        log.error("PayFeignClient 调用失败，触发降级", cause);
        return (orderId, userId, amount, reason) -> {
            log.warn("退款降级: orderId={}, amount={}", orderId, amount);
            return R.fail(500, "支付服务暂不可用，请稍后重试");
        };
    }
}
