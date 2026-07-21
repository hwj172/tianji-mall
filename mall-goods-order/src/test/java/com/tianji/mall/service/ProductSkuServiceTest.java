package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSkuServiceTest {

    @Mock
    private ProductSkuMapper productSkuMapper;

    private ProductSkuService skuService;

    @BeforeEach
    void setUp() {
        skuService = new ProductSkuService(productSkuMapper);
        ReflectionTestUtils.setField(skuService, "baseMapper", productSkuMapper);
    }

    // ==================== CRUD ====================

    @Test
    void shouldCreateSku() {
        when(productSkuMapper.insert(any(ProductSku.class))).thenReturn(1);

        ProductSku sku = skuService.create(1L, "颜色:红;尺寸:XL", "SKU001", BigDecimal.valueOf(199), 100);

        assertThat(sku.getProductId()).isEqualTo(1L);
        assertThat(sku.getSpecs()).isEqualTo("颜色:红;尺寸:XL");
        assertThat(sku.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(199));
        assertThat(sku.getStock()).isEqualTo(100);
    }

    @Test
    void shouldListSkusByProductId() {
        ProductSku sku1 = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 10);
        ProductSku sku2 = buildSku(2L, 1L, "颜色:蓝", BigDecimal.valueOf(199), 5);
        when(productSkuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sku1, sku2));

        List<ProductSku> skus = skuService.listByProductId(1L);

        assertThat(skus).hasSize(2);
    }

    @Test
    void shouldUpdateSku() {
        ProductSku existing = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 10);
        when(productSkuMapper.selectById(1L)).thenReturn(existing);
        when(productSkuMapper.updateById(existing)).thenReturn(1);

        skuService.update(1L, 1L, "颜色:红;尺寸:XL", "SKU001", BigDecimal.valueOf(299), 20);

        assertThat(existing.getSpecs()).isEqualTo("颜色:红;尺寸:XL");
        assertThat(existing.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(299));
        assertThat(existing.getStock()).isEqualTo(20);
    }

    @Test
    void shouldThrowWhenUpdateNonExistentSku() {
        when(productSkuMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> skuService.update(1L, 999L, "x", null, null, 0))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU不存在");
    }

    @Test
    void shouldDeleteSkuWhenStockIsZero() {
        ProductSku sku = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 0);
        when(productSkuMapper.selectById(1L)).thenReturn(sku);
        when(productSkuMapper.deleteById(1L)).thenReturn(1);

        skuService.delete(1L, 1L);

        verify(productSkuMapper).deleteById(1L);
    }

    @Test
    void shouldThrowWhenDeleteSkuWithStock() {
        ProductSku sku = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 10);
        when(productSkuMapper.selectById(1L)).thenReturn(sku);

        assertThatThrownBy(() -> skuService.delete(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不为0，无法删除SKU");
    }

    // ==================== 库存操作 ====================

    @Test
    void shouldDeductStockSuccessfully() {
        when(productSkuMapper.deductStock(1L, 3)).thenReturn(1);

        skuService.deductStock(1L, 1L, 3);

        verify(productSkuMapper).deductStock(1L, 3);
    }

    @Test
    void shouldThrowWhenDeductStockInsufficient() {
        when(productSkuMapper.deductStock(1L, 100)).thenReturn(0);

        assertThatThrownBy(() -> skuService.deductStock(1L, 1L, 100))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU库存不足");
    }

    @Test
    void shouldRestoreStock() {
        when(productSkuMapper.restoreStock(1L, 2)).thenReturn(1);

        skuService.restoreStock(1L, 1L, 2);

        verify(productSkuMapper).restoreStock(1L, 2);
    }

    // ==================== helpers ====================

    private ProductSku buildSku(Long id, Long productId, String specs, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setSpecs(specs);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);
        return sku;
    }
}
