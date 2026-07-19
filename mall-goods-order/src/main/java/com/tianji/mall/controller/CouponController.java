package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.UserCoupon;
import com.tianji.mall.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final JwtUtil jwtUtil;

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

    @GetMapping("/my")
    public R<List<UserCoupon>> myCoupons(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(couponService.getUserCoupons(userId));
    }
}
