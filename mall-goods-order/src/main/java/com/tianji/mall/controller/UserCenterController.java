package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserCenterController {

    private final JwtUtil jwtUtil;
    private final UserFeignClient userFeignClient;
    private final OrderMapper orderMapper;
    private final UserCouponMapper userCouponMapper;
    private final FavoriteMapper favoriteMapper;
    private final CartItemMapper cartItemMapper;
    private final BrowsingHistoryMapper browsingHistoryMapper;
    private final ShopFollowMapper shopFollowMapper;

    @GetMapping("/center")
    public R<Map<String, Object>> center(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));

        Map<String, Object> result = new LinkedHashMap<>();

        // 用户信息
        try {
            R<Map<String, Object>> userResp = userFeignClient.getUserById(userId);
            if (userResp != null && userResp.getData() != null) {
                result.put("user", userResp.getData());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}", userId, e);
        }

        // 订单统计
        Map<String, Long> orderStats = new LinkedHashMap<>();
        orderStats.put("pendingPayment", 0L);
        orderStats.put("pendingShip", 0L);
        orderStats.put("pendingReceive", 0L);
        orderStats.put("pendingReview", 0L);
        try {
            List<Map<String, Object>> stats = orderMapper.selectOrderStats(userId);
            if (stats != null) {
                for (Map<String, Object> row : stats) {
                    int status = ((Number) row.get("status")).intValue();
                    long cnt = ((Number) row.get("cnt")).longValue();
                    switch (status) {
                        case 1 -> orderStats.put("pendingPayment", cnt);
                        case 2 -> orderStats.put("pendingShip", cnt);
                        case 3 -> orderStats.put("pendingReceive", cnt);
                        case 4 -> orderStats.put("pendingReview", cnt);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取订单统计失败: userId={}", userId, e);
        }
        result.put("orderStats", orderStats);

        // 计数（best-effort）
        result.put("couponCount", safeCount(() -> userCouponMapper.selectCountByUserId(userId)));
        result.put("favoriteCount", safeCount(() -> favoriteMapper.selectCountByUserId(userId)));
        result.put("followShopCount", safeCount(() -> shopFollowMapper.selectCountByUserId(userId)));
        result.put("cartCount", safeCount(() -> cartItemMapper.selectCountByUserId(userId)));
        result.put("historyCount", safeCount(() -> browsingHistoryMapper.selectCountByUserId(userId)));

        return R.ok(result);
    }

    private long safeCount(Supplier<Long> counter) {
        try {
            Long val = counter.get();
            return val != null ? val : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
