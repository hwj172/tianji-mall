package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.entity.Category;
import com.tianji.mall.mapper.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ProductService productService;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(productService);
        ReflectionTestUtils.setField(categoryService, "baseMapper", categoryMapper);
    }

    // ==================== getCategoryTree ====================

    @Test
    void shouldReturnTreeStructure() {
        Category electronics = category(1L, "电子产品", 0L, 1);
        Category phone = category(2L, "手机", 1L, 2);
        Category computer = category(3L, "电脑", 1L, 3);
        Category clothing = category(4L, "服装", 0L, 4);
        when(categoryMapper.selectList(any()))
                .thenReturn(List.of(electronics, phone, computer, clothing));

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getName()).isEqualTo("电子产品");
        assertThat(tree.get(0).getChildren()).hasSize(2);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("手机");
        assertThat(tree.get(0).getChildren().get(1).getName()).isEqualTo("电脑");
        assertThat(tree.get(1).getName()).isEqualTo("服装");
        assertThat(tree.get(1).getChildren()).isEmpty();
    }

    @Test
    void shouldReturnEmptyTreeWhenNoCategories() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).isEmpty();
    }

    // ==================== createCategory ====================

    @Test
    void shouldCreateCategory() {
        categoryService.createCategory("新分类", 0L, 1);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("新分类");
        assertThat(saved.getParentId()).isEqualTo(0L);
        assertThat(saved.getSort()).isEqualTo(1);
    }

    @Test
    void shouldCreateCategoryWithDefaultParentIdAndSort() {
        categoryService.createCategory("子分类", null, null);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(captor.capture());
        Category saved = captor.getValue();
        assertThat(saved.getParentId()).isEqualTo(0L);
        assertThat(saved.getSort()).isEqualTo(0);
    }

    // ==================== updateCategory ====================

    @Test
    void shouldUpdateCategory() {
        Category existing = category(1L, "旧名称", 0L, 1);
        when(categoryMapper.selectById(1L)).thenReturn(existing);

        categoryService.updateCategory(1L, "新名称", 2);

        verify(categoryMapper).updateById(existing);
        assertThat(existing.getName()).isEqualTo("新名称");
        assertThat(existing.getSort()).isEqualTo(2);
    }

    @Test
    void shouldUpdateCategoryWithDefaultSort() {
        Category existing = category(1L, "旧名称", 0L, 1);
        when(categoryMapper.selectById(1L)).thenReturn(existing);

        categoryService.updateCategory(1L, "新名称", null);

        assertThat(existing.getSort()).isEqualTo(0);
    }

    @Test
    void shouldThrowWhenUpdateNonExistentCategory() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.updateCategory(999L, "新名称", 1))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
    }

    // ==================== deleteCategory ====================

    @Test
    void shouldDeleteCategory() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "分类", 0L, 1));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productService.countByCategoryId(1L)).thenReturn(0L);

        categoryService.deleteCategory(1L);

        verify(categoryMapper).deleteById(1L);
    }

    @Test
    void shouldThrowWhenDeleteNonExistentCategory() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                .isInstanceOf(BizException.class)
                .hasMessage("分类不存在");
    }

    @Test
    void shouldThrowWhenDeleteCategoryWithChildren() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "父分类", 0L, 1));
        when(categoryMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该分类下有子分类，无法删除");

        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void shouldThrowWhenDeleteCategoryWithProducts() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "分类", 0L, 1));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productService.countByCategoryId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该分类下有商品，无法删除");

        verify(categoryMapper, never()).deleteById(anyLong());
    }

    // ==================== helpers ====================

    private Category category(Long id, String name, Long parentId, Integer sort) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setParentId(parentId);
        c.setSort(sort);
        return c;
    }
}
