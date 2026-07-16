package com.tianji.pay.feign;

import com.tianji.pay.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Map;

@FeignClient(name = "mall-goods-order")
public interface OrderFeignClient {

    @GetMapping("/api/order/internal/{id}")
    Map<String, Object> getOrder(@PathVariable Long id);

    @PutMapping("/api/order/internal/{id}/pay")
    Map<String, Object> payOrder(@PathVariable Long id);
}
