package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.UserCoupon;
import com.tianji.mall.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final JwtUtil jwtUtil;

    /** 领券中心：全部可领优惠券 + 已领状态 + 即将过期标记 */
    @GetMapping("/center")
    public R<Map<String, Object>> center(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(couponService.getCouponCenter(userId));
    }

    /** 可领优惠券数量（红点提示） */
    @GetMapping("/count")
    public R<Map<String, Object>> count(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(Map.of("unclaimed", couponService.getAvailableCount(userId)));
    }

    /** 可用优惠券列表（无需用户状态，供购物车/下单页展示） */
    @GetMapping("/list")
    public R<List<Coupon>> listAvailable() {
        return R.ok(couponService.listAvailable());
    }

    @PostMapping("/{id}/claim")
    public R<Void> claim(@RequestHeader("Authorization") String authHeader,
                         @PathVariable("id") Long couponId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        couponService.claimCoupon(userId, couponId);
        return R.ok();
    }

    /** 我的优惠券（含优惠券详情 + 状态） */
    @GetMapping("/my")
    public R<List<Map<String, Object>>> myCoupons(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(couponService.getUserCoupons(userId));
    }
}
