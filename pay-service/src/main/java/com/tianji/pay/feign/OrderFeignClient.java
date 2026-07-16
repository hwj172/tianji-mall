package com.tianji.pay.feign;

import com.tianji.common.result.R;
import com.tianji.pay.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mall-goods-order")
public interface OrderFeignClient {

    @GetMapping("/api/order/internal/{id}")
    R<OrderDTO> getOrder(@PathVariable("id") Long id);

    @PutMapping("/api/order/internal/{id}/pay")
    R<Void> payOrder(@PathVariable("id") Long id, @RequestParam("userId") Long userId);
}
