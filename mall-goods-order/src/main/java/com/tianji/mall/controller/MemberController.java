package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.MemberInfoResponse;
import com.tianji.mall.dto.SignInResponse;
import com.tianji.mall.entity.PointsLog;
import com.tianji.mall.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final JwtUtil jwtUtil;

    @GetMapping("/info")
    public R<MemberInfoResponse> info(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(memberService.getMemberInfo(userId));
    }

    @PostMapping("/sign")
    public R<SignInResponse> sign(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(memberService.signIn(userId));
    }

    @GetMapping("/points-log")
    public R<Page<PointsLog>> pointsLog(@RequestHeader("Authorization") String authHeader,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(memberService.getPointsLog(userId, page, size));
    }
}
