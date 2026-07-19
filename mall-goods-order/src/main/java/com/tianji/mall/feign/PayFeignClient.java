package com.tianji.mall.feign;

import com.tianji.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "pay-service")
public interface PayFeignClient {

    @PostMapping("/api/pay/internal/refund")
    R<Void> refundOrder(@RequestParam("orderId") Long orderId,
                        @RequestParam("userId") Long userId,
                        @RequestParam("amount") BigDecimal amount,
                        @RequestParam("reason") String reason);
}
