package com.tianji.user.feign;

import com.tianji.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 跨服务调用 mall-goods-order 新人注册自动发券（内部端点，X-Internal-Token 鉴权由 FeignConfig 自动添加）。
 */
@FeignClient(name = "mall-goods-order")
public interface NewbieCouponFeignClient {

    @PostMapping("/api/coupon/internal/newbie/{userId}")
    R<Integer> issueNewbieCoupon(@PathVariable("userId") Long userId);
}
