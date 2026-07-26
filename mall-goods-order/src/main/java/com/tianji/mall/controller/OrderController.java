package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.OrderDetailResponse;
import com.tianji.mall.entity.LogisticsTrack;
import com.tianji.mall.entity.Order;
import com.tianji.mall.service.LogisticsService;
import com.tianji.mall.service.OrderService;
import com.tianji.mall.service.RefundService;
import com.tianji.common.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RefundService refundService;
    private final JwtUtil jwtUtil;
    private final LogisticsService logisticsService;

    @PostMapping("/create")
    public R<Order> create(@RequestHeader("Authorization") String authHeader,
                           @Valid @RequestBody OrderCreateRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(orderService.createOrder(userId, req));
    }

    @GetMapping("/list")
    public R<List<Order>> list(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(orderService.getOrderList(userId));
    }

    @GetMapping("/{id}")
    public R<OrderDetailResponse> detail(@RequestHeader("Authorization") String authHeader,
                                         @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(orderService.getOrderDetail(userId, id));
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@RequestHeader("Authorization") String authHeader,
                          @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        orderService.cancelOrder(userId, id);
        return R.ok();
    }

    @PutMapping("/{id}/receive")
    public R<Void> receive(@RequestHeader("Authorization") String authHeader,
                           @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        orderService.confirmReceive(userId, id);
        return R.ok();
    }

    @PostMapping("/{id}/refund")
    public R<Void> refund(@RequestHeader("Authorization") String authHeader,
                          @PathVariable("id") Long id,
                          @RequestBody Map<String, String> body) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        refundService.requestRefund(userId, id, body.get("reason"));
        return R.ok();
    }

    @GetMapping("/{id}/logistics")
    public R<List<LogisticsTrack>> logistics(@RequestHeader("Authorization") String authHeader,
                                              @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Order order = orderService.getById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            return R.fail(500, "订单不存在");
        }
        if (order.getStatus() < 3) {
            return R.fail(500, "订单尚未发货");
        }
        return R.ok(logisticsService.getTracks(id));
    }

    // ===== 内部端点（供其他服务 Feign 调用，无需 JWT 鉴权）=====

    @GetMapping("/internal/{id}")
    public R<Order> getOrderInternal(@PathVariable("id") Long id) {
        return R.ok(orderService.getById(id));
    }

    @GetMapping("/internal/list/{userId}")
    public R<List<Order>> listInternal(@PathVariable("userId") Long userId) {
        return R.ok(orderService.getOrderList(userId));
    }

    @PutMapping("/internal/{id}/pay")
    public R<Void> payOrderInternal(@PathVariable("id") Long id, @RequestParam("userId") Long userId) {
        orderService.payOrder(id, userId);
        return R.ok();
    }

    @PostMapping("/internal/create/{userId}")
    public R<Map<String, Object>> createInternal(@PathVariable("userId") Long userId,
                                                  @Valid @RequestBody OrderCreateRequest req) {
        Order order = orderService.createOrder(userId, req);
        return R.ok(Map.of(
                "id", order.getId(),
                "orderNo", order.getOrderNo(),
                "totalAmount", order.getTotalAmount(),
                "status", order.getStatus()));
    }

    @PostMapping("/internal/pay/{id}")
    public R<Void> payInternal(@PathVariable("id") Long id, @RequestParam("userId") Long userId) {
        orderService.payOrder(id, userId);
        return R.ok();
    }
}
