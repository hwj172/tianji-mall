package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.dto.GroupBuyStartRequest;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.service.GroupBuyService;
import jakarta.validation.Valid;
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
    public R<Map<String, Object>> start(@RequestBody @Valid GroupBuyStartRequest body,
                                         @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.startGroup(userId, body.getActivityId(), body.getTargetCount(), body));
    }

    @PostMapping("/join/{groupId}")
    public R<Map<String, Object>> join(@PathVariable("groupId") String groupId,
                                        @RequestHeader("Authorization") String authHeader,
                                        @RequestBody @Valid OrderCreateRequest orderReq) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.joinGroup(groupId, userId, orderReq));
    }

    @GetMapping("/my")
    public R<List<GroupBuyOrder>> myGroups(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.substring(7));
        return R.ok(groupBuyService.getMyGroups(userId));
    }
}
