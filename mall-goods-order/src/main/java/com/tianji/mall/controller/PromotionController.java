package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.mall.entity.Promotion;
import com.tianji.mall.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    /** 当前有效满减活动（公开，购物车/结算页展示） */
    @GetMapping("/current")
    public R<List<Promotion>> current() {
        return R.ok(promotionService.getCurrentActive());
    }
}
