package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.ShipRequest;
import com.tianji.mall.entity.Refund;
import com.tianji.mall.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final JwtUtil jwtUtil;

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@RequestHeader("Authorization") String authHeader,
                                         @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(refundService.getRefundDetail(userId, id));
    }

    @GetMapping("/my")
    public R<Page<Refund>> myRefunds(@RequestHeader("Authorization") String authHeader,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String status) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(refundService.getMyRefunds(userId, page, size, status));
    }

    @GetMapping("/seller-pending")
    public R<List<Refund>> sellerPending(@RequestHeader("Authorization") String authHeader,
                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        // 卖家/管理员确认退货：仅 seller / admin 可查看待确认退货单
        if (role == null || (!"seller".equals(role) && !"admin".equals(role))) {
            throw new BizException(BizErrorCode.FORBIDDEN);
        }
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(refundService.getSellerPendingRefunds(userId));
    }

    @PutMapping("/{id}/ship")
    public R<Void> returnShip(@RequestHeader("Authorization") String authHeader,
                               @PathVariable("id") Long id,
                               @RequestBody @Valid ShipRequest body) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        refundService.returnShip(userId, id, body.getTrackingNumber(), body.getTrackingCompany());
        return R.ok();
    }

    @PutMapping("/{id}/receive")
    public R<Void> confirmReceive(@RequestHeader(value = "X-User-Role", required = false) String role,
                                  @PathVariable("id") Long id) {
        // 卖家确认收货退款：仅 seller / admin 可操作（网关已注入 X-User-Role；无 header 视为未认证）
        if (role == null || (!"seller".equals(role) && !"admin".equals(role))) {
            throw new BizException(BizErrorCode.FORBIDDEN);
        }
        refundService.confirmReceive(id);
        return R.ok();
    }
}
