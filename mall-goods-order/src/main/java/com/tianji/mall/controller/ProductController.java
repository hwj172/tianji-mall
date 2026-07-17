package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/list")
    public R<Page<Product>> list(@RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return R.ok(productService.getProductPage(categoryId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public R<Product> detail(@PathVariable("id") Long id) {
        return R.ok(productService.getProductById(id));
    }

    @PostMapping("/batch")
    public R<List<Product>> batch(@RequestBody List<Long> ids) {
        return R.ok(productService.getProductBatch(ids));
    }
}
