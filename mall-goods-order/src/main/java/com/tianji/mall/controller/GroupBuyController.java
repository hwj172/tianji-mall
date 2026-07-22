package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/group-buy")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public R<List<GroupBuy>> list() {
        return R.ok(groupBuyService.getActiveActivities());
    }

    @GetMapping("/{id}")
    public R<GroupBuyDetailResponse> detail(@PathVariable("id") Long id) {
        return R.ok(groupBuyService.getDetail(id));
    }

    @PostMapping("/start")
    public R<GroupBuyOrder> start(@RequestBody Map<String, Object> body,
                                   @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        Long activityId = ((Number) body.get("activityId")).longValue();
        int targetCount = ((Number) body.get("targetCount")).intValue();
        Long addressId = body.get("addressId") != null ? ((Number) body.get("addressId")).longValue() : null;
        return R.ok(groupBuyService.startGroup(userId, activityId, targetCount, addressId));
    }

    @PostMapping("/join/{groupId}")
    public R<Void> join(@PathVariable("groupId") String groupId,
                         @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        groupBuyService.joinGroup(groupId, userId);
        return R.ok();
    }

    @GetMapping("/my")
    public R<List<GroupBuyOrder>> myGroups(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.getMyGroups(userId));
    }
}
