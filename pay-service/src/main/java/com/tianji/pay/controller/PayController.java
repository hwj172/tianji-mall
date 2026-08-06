package com.tianji.pay.controller;

import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.pay.dto.PayResponse;
import com.tianji.pay.entity.Payment;
import com.tianji.pay.service.PayService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final JwtUtil jwtUtil;

    @PostMapping("/create")
    public R<PayResponse> create(@RequestHeader("Authorization") String authHeader,
                                 @RequestParam("orderId") @NotNull @Min(1) Long orderId,
                                 @RequestParam(value = "returnUrl", required = false) String returnUrl) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(payService.createPayment(userId, orderId, returnUrl));
    }

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, String> params) {
        log.info("收到支付宝回调: {}", params);
        try {
            payService.handleNotify(params);
            return "success";
        } catch (BizException e) {
            // 业务异常（验签失败/订单不存在）→ 返回 success 停止支付宝重试
            log.warn("回调业务异常（不可重试）: {}", e.getMessage());
            return "success";
        } catch (Exception e) {
            // 系统异常（网络/DB 故障）→ 返回 fail 触发支付宝重试
            log.error("回调系统异常（可重试）", e);
            return "fail";
        }
    }

    @GetMapping("/query/{orderId}")
    public R<Payment> query(@RequestHeader("Authorization") String authHeader,
                            @PathVariable("orderId") Long orderId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(payService.queryPayment(userId, orderId));
    }

    // ===== 内部端点（供其他服务 Feign 调用，无需 JWT 鉴权）=====

    @PostMapping("/internal/refund")
    public R<Void> refundOrder(@RequestParam("orderId") Long orderId,
                               @RequestParam("userId") Long userId,
                               @RequestParam("amount") BigDecimal amount,
                               @RequestParam("reason") String reason) {
        payService.refund(orderId, userId, amount, reason);
        return R.ok();
    }
}
