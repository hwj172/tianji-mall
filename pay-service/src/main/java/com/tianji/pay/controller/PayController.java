package com.tianji.pay.controller;

import com.tianji.common.result.R;
import com.tianji.pay.dto.PayResponse;
import com.tianji.pay.entity.Payment;
import com.tianji.pay.service.PayService;
import com.tianji.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final JwtUtil jwtUtil;

    @PostMapping("/create")
    public R<PayResponse> create(@RequestHeader("Authorization") String authHeader,
                                 @RequestParam Long orderId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(payService.createPayment(userId, orderId));
    }

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, String> params) {
        log.info("收到支付宝回调: {}", params);
        try {
            payService.handleNotify(params);
            return "success";
        } catch (Exception e) {
            log.error("回调处理失败", e);
            return "fail";
        }
    }

    @GetMapping("/query/{orderId}")
    public R<Payment> query(@RequestHeader("Authorization") String authHeader,
                            @PathVariable Long orderId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(payService.queryPayment(userId, orderId));
    }
}
