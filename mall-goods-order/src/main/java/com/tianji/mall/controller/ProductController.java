package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final RecommendService recommendService;
    private final SeckillService seckillService;
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public R<Page<Product>> list(@RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) BigDecimal minPrice,
                                  @RequestParam(required = false) BigDecimal maxPrice,
                                  @RequestParam(required = false) String sortBy,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return R.ok(productService.getProductPage(categoryId, keyword,
                minPrice, maxPrice, sortBy, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return R.ok(productService.getProductDetail(id));
    }

    @PostMapping("/batch")
    public R<List<Product>> batch(@RequestBody List<Long> ids) {
        return R.ok(productService.getProductBatch(ids));
    }

    // ===== 内部端点（网关 X-Internal-Token 鉴权，不暴露给前端）=====

    @PostMapping("/internal/sync-vectors")
    public R<Map<String, Integer>> syncVectors() {
        return R.ok(productService.syncAllVectors());
    }

    // ===== 秒杀端点 =====

    @GetMapping("/seckill/list")
    public R<Page<Product>> seckillList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(seckillService.getSeckillList(page, size));
    }

    // ===== 推荐端点 =====

    @GetMapping("/recommend")
    public R<RecommendResponse> recommend(
            @RequestParam(value = "count", defaultValue = "10") int count,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userId = jwtUtil.getUserId(authHeader.substring(7));
        }
        return R.ok(recommendService.recommend(userId, count));
    }
}
