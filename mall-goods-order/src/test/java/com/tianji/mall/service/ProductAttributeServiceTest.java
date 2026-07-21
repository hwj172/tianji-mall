package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.mapper.ProductAttributeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAttributeServiceTest {

    @Mock
    private ProductAttributeMapper attributeMapper;

    private ProductAttributeService attributeService;

    @BeforeEach
    void setUp() {
        attributeService = new ProductAttributeService(attributeMapper);
        ReflectionTestUtils.setField(attributeService, "baseMapper", attributeMapper);
    }

    @Test
    void shouldCreateAttribute() {
        when(attributeMapper.insert(any(ProductAttribute.class))).thenReturn(1);

        ProductAttribute attr = attributeService.create(1L, "屏幕尺寸", "6.1英寸", 0);

        assertThat(attr.getProductId()).isEqualTo(1L);
        assertThat(attr.getName()).isEqualTo("屏幕尺寸");
        assertThat(attr.getValue()).isEqualTo("6.1英寸");
    }

    @Test
    void shouldListAttributesByProductId() {
        ProductAttribute attr1 = buildAttr(1L, 1L, "屏幕尺寸", "6.1英寸");
        ProductAttribute attr2 = buildAttr(2L, 1L, "电池容量", "4000mAh");
        when(attributeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(attr1, attr2));

        List<ProductAttribute> attrs = attributeService.listByProductId(1L);

        assertThat(attrs).hasSize(2);
    }

    @Test
    void shouldUpdateAttribute() {
        ProductAttribute existing = buildAttr(1L, 1L, "屏幕尺寸", "6.1英寸");
        when(attributeMapper.selectById(1L)).thenReturn(existing);
        when(attributeMapper.updateById(existing)).thenReturn(1);

        attributeService.update(1L, 1L, "屏幕尺寸", "6.7英寸", 1);

        assertThat(existing.getValue()).isEqualTo("6.7英寸");
        assertThat(existing.getSort()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenUpdateNonExistentAttribute() {
        when(attributeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> attributeService.update(1L, 999L, "x", "y", 0))
                .isInstanceOf(BizException.class)
                .hasMessage("属性不存在");
    }

    @Test
    void shouldDeleteAttribute() {
        ProductAttribute attr = buildAttr(1L, 1L, "屏幕尺寸", "6.1英寸");
        when(attributeMapper.selectById(1L)).thenReturn(attr);
        when(attributeMapper.deleteById(1L)).thenReturn(1);

        attributeService.delete(1L, 1L);

        verify(attributeMapper).deleteById(1L);
    }

    private ProductAttribute buildAttr(Long id, Long productId, String name, String value) {
        ProductAttribute attr = new ProductAttribute();
        attr.setId(id);
        attr.setProductId(productId);
        attr.setName(name);
        attr.setValue(value);
        attr.setSort(0);
        return attr;
    }
}
