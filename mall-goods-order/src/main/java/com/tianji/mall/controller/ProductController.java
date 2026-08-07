package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.BrowsingHistory;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.BrowsingHistoryService;
import com.tianji.mall.service.FavoriteService;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final RecommendService recommendService;
    private final SeckillService seckillService;
    private final BrowsingHistoryService browsingHistoryService;
    private final FavoriteService favoriteService;
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
                minPrice, maxPrice, sortBy, null, null, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 记录浏览足迹 + 收藏状态（JWT 可选，未登录跳过）
        boolean favorited = false;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Long userId = jwtUtil.getUserId(authHeader.substring(7));
            browsingHistoryService.recordView(userId, id);
            favorited = favoriteService.isFavorited(userId, id);
        }
        Map<String, Object> result = new HashMap<>(productService.getProductDetail(id));
        result.put("favorited", favorited);
        return R.ok(result);
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

    // ===== 搜索热词 =====

    @GetMapping("/search/hot")
    public R<List<String>> hotKeywords() {
        List<Map<String, Object>> rows = productService.getHotKeywords();
        List<String> keywords = rows.stream()
                .map(r -> (String) r.get("keyword"))
                .toList();
        return R.ok(keywords);
    }

    // ===== 浏览足迹 =====

    @GetMapping("/history")
    public R<Page<BrowsingHistory>> history(@RequestHeader("Authorization") String authHeader,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(browsingHistoryService.getHistory(userId, page, size));
    }

    @DeleteMapping("/history")
    public R<Void> clearHistory(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        browsingHistoryService.clearHistory(userId);
        return R.ok();
    }
}
