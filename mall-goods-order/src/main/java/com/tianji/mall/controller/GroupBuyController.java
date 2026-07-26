package com.tianji.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.dto.OrderCreateRequest;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public R<List<GroupBuy>> list() {
        return R.ok(groupBuyService.getActiveActivities());
    }

    @GetMapping("/{id}")
    public R<GroupBuyDetailResponse> detail(@PathVariable("id") Long id) {
        return R.ok(groupBuyService.getDetail(id));
    }

    @PostMapping("/start")
    public R<Map<String, Object>> start(@RequestBody Map<String, Object> body,
                                         @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        Long activityId = ((Number) body.remove("activityId")).longValue();
        int targetCount = ((Number) body.remove("targetCount")).intValue();
        OrderCreateRequest orderReq = objectMapper.convertValue(body, OrderCreateRequest.class);
        return R.ok(groupBuyService.startGroup(userId, activityId, targetCount, orderReq));
    }

    @PostMapping("/join/{groupId}")
    public R<Map<String, Object>> join(@PathVariable("groupId") String groupId,
                                        @RequestHeader("Authorization") String authHeader,
                                        @RequestBody OrderCreateRequest orderReq) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.joinGroup(groupId, userId, orderReq));
    }

    @GetMapping("/my")
    public R<List<GroupBuyOrder>> myGroups(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.getMyGroups(userId));
    }
}
