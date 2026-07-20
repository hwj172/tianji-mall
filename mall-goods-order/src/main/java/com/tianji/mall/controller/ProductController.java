package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.ProductService;
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
    public R<Product> detail(@PathVariable("id") Long id) {
        return R.ok(productService.getProductById(id));
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
}
