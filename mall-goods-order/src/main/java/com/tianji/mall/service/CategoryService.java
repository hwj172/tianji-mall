package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.entity.Category;
import com.tianji.mall.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {

    private final ProductService productService;

    /**
     * 返回分类树（一级 + 二级子节点）。
     */
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> all = list(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort));

        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> !Long.valueOf(0).equals(c.getParentId()))
                .collect(Collectors.groupingBy(Category::getParentId));

        return all.stream()
                .filter(c -> Long.valueOf(0).equals(c.getParentId()))
                .map(c -> new CategoryTreeResponse(
                        c.getId(), c.getName(), c.getParentId(), c.getSort(),
                        childrenMap.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    public Category createCategory(String name, Long parentId, Integer sort) {
        Category category = new Category();
        category.setName(name);
        category.setParentId(parentId != null ? parentId : 0L);
        category.setSort(sort != null ? sort : 0);
        save(category);
        return category;
    }

    public Category updateCategory(Long id, String name, Integer sort) {
        Category category = getById(id);
        if (category == null) {
            throw new BizException(BizErrorCode.CATEGORY_NOT_FOUND);
        }
        category.setName(name);
        category.setSort(sort != null ? sort : 0);
        updateById(category);
        return category;
    }

    public void deleteCategory(Long id) {
        Category category = getById(id);
        if (category == null) {
            throw new BizException(BizErrorCode.CATEGORY_NOT_FOUND);
        }
        // 检查是否有一级分类下有子分类
        long childCount = count(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount > 0) {
            throw new BizException(BizErrorCode.CATEGORY_HAS_CHILDREN);
        }
        // 检查是否有商品使用该分类
        long productCount = productService.countByCategoryId(id);
        if (productCount > 0) {
            throw new BizException(BizErrorCode.CATEGORY_HAS_PRODUCTS);
        }
        removeById(id);
    }
}
