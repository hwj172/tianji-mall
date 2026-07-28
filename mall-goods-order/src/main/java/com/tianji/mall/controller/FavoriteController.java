package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.FavoriteToggleRequest;
import com.tianji.mall.entity.Favorite;
import com.tianji.mall.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final JwtUtil jwtUtil;

    @PostMapping("/toggle")
    public R<Map<String, Object>> toggle(@RequestHeader("Authorization") String authHeader,
                                          @RequestBody @Valid FavoriteToggleRequest body) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(favoriteService.toggle(userId, body.getProductId()));
    }

    @GetMapping("/list")
    public R<List<Favorite>> list(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(favoriteService.listByUser(userId));
    }
}
