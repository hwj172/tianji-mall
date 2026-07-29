package com.tianji.pay.feign.fallback;

import com.tianji.common.result.R;
import com.tianji.pay.dto.OrderDTO;
import com.tianji.pay.feign.OrderFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderFeignClientFallback implements FallbackFactory<OrderFeignClient> {

    @Override
    public OrderFeignClient create(Throwable cause) {
        log.error("OrderFeignClient 调用失败，触发降级", cause);
        return new OrderFeignClient() {
            @Override
            public R<OrderDTO> getOrder(Long id) {
                return R.fail(500, "订单服务暂不可用");
            }

            @Override
            public R<Void> payOrder(Long id, Long userId) {
                return R.fail(500, "订单服务暂不可用");
            }
        };
    }
}
