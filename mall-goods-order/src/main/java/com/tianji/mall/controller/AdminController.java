package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.mall.annotation.RequireAdmin;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
public class AdminController {

    private final CategoryService categoryService;

    // ==================== 分类管理 ====================

    @GetMapping("/category")
    public R<List<CategoryTreeResponse>> getCategoryTree() {
        return R.ok(categoryService.getCategoryTree());
    }

    @PostMapping("/category")
    public R<Void> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : 0L;
        Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0;
        categoryService.createCategory(name, parentId, sort);
        return R.ok();
    }

    @PutMapping("/category/{id}")
    public R<Void> updateCategory(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        categoryService.updateCategory(id, (String) body.get("name"),
                body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0);
        return R.ok();
    }

    @DeleteMapping("/category/{id}")
    public R<Void> deleteCategory(@PathVariable("id") Long id) {
        categoryService.deleteCategory(id);
        return R.ok();
    }
}
