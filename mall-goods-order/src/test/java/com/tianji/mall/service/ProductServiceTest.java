package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(aiChatFeignClient);
        ReflectionTestUtils.setField(productService, "baseMapper", productMapper);
    }

    @Test
    void shouldReturnProductPageWithKeywordFilter() {
        @SuppressWarnings("unchecked")
        Page<Product> mockPage = new Page<>(1, 10);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<Product> result = productService.getProductPage(null, "手机", null, null, null, 1, 10);

        assertThat(result).isNotNull();
        verify(productMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReturnProductPageWithCategoryFilter() {
        @SuppressWarnings("unchecked")
        Page<Product> mockPage = new Page<>(1, 10);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<Product> result = productService.getProductPage(1L, null, null, null, null, 1, 10);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnProductById() {
        Product product = buildProduct(1L, "iPhone 16", BigDecimal.valueOf(9999), 50, 1);
        when(productMapper.selectById(1L)).thenReturn(product);

        Product result = productService.getProductById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("iPhone 16");
        assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(9999));
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
    }

    @Test
    void shouldThrowWhenProductOffShelf() {
        Product product = buildProduct(1L, "已下架商品", BigDecimal.TEN, 10, 0);
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThatThrownBy(() -> productService.getProductById(1L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在或已下架");
    }

    // ==================== admin: getProductPageAdmin ====================

    @Test
    void shouldReturnAllProductsForAdmin() {
        @SuppressWarnings("unchecked")
        Page<Product> mockPage = new Page<>(1, 10);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<Product> result = productService.getProductPageAdmin(1, 10, null);

        assertThat(result).isNotNull();
        verify(productMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldFilterByCategoryForAdmin() {
        @SuppressWarnings("unchecked")
        Page<Product> mockPage = new Page<>(1, 10);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<Product> result = productService.getProductPageAdmin(1, 10, 1L);

        assertThat(result).isNotNull();
    }

    // ==================== admin: createProduct ====================

    @Test
    void shouldCreateProduct() {
        productService.createProduct("New Product", "desc", BigDecimal.valueOf(99.9), 100, 1L, "img.jpg");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("New Product");
        assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(99.9));
        assertThat(saved.getStock()).isEqualTo(100);
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    // ==================== admin: updateProduct ====================

    @Test
    void shouldUpdateProductAllFields() {
        Product existing = buildProduct(1L, "Old", BigDecimal.TEN, 10, 1);
        when(productMapper.selectById(1L)).thenReturn(existing);

        productService.updateProduct(1L, "New Name", "New Desc",
                BigDecimal.valueOf(199.9), 50, 2L, 0, "new.jpg");

        verify(productMapper).updateById(existing);
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getDescription()).isEqualTo("New Desc");
        assertThat(existing.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(199.9));
        assertThat(existing.getStock()).isEqualTo(50);
        assertThat(existing.getCategoryId()).isEqualTo(2L);
        assertThat(existing.getStatus()).isEqualTo(0);
        assertThat(existing.getImages()).isEqualTo("new.jpg");
    }

    @Test
    void shouldUpdateProductPartialFields() {
        Product existing = buildProduct(1L, "Old", BigDecimal.TEN, 10, 1);
        existing.setDescription("Old Desc"); // pre-set description
        when(productMapper.selectById(1L)).thenReturn(existing);

        productService.updateProduct(1L, "New Name", null, null, null, null, null, null);

        verify(productMapper).updateById(existing);
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getDescription()).isEqualTo("Old Desc"); // unchanged
    }

    @Test
    void shouldThrowWhenUpdateNonExistentProduct() {
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> productService.updateProduct(999L, "Name", null, null, null, null, null, null))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在");
    }

    // ==================== admin: deleteProduct ====================

    @Test
    void shouldSoftDeleteProduct() {
        Product existing = buildProduct(1L, "Product", BigDecimal.TEN, 10, 1);
        when(productMapper.selectById(1L)).thenReturn(existing);

        productService.deleteProduct(1L);

        assertThat(existing.getStatus()).isEqualTo(0);
        verify(productMapper).updateById(existing);
    }

    @Test
    void shouldThrowWhenDeleteNonExistentProduct() {
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(BizException.class)
                .hasMessage("商品不存在");
    }

    // ==================== syncAllVectors ====================

    @Test
    void shouldSyncAllVectorsWithAllSuccess() {
        Product p1 = buildProduct(1L, "iPhone 16", BigDecimal.valueOf(9999), 50, 1);
        Product p2 = buildProduct(2L, "小米 15", BigDecimal.valueOf(4999), 100, 1);
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));

        Map<String, Integer> result = productService.syncAllVectors();

        assertThat(result)
                .containsEntry("total", 2)
                .containsEntry("success", 2)
                .containsEntry("failed", 0);
        verify(aiChatFeignClient, times(2)).upsertProductVector(anyMap());
    }

    @Test
    void shouldCountFailedWhenSyncThrows() {
        Product p1 = buildProduct(1L, "iPhone 16", BigDecimal.valueOf(9999), 50, 1);
        Product p2 = buildProduct(2L, "小米 15", BigDecimal.valueOf(4999), 100, 1);
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));
        // 第一个商品同步抛异常，第二个成功
        doThrow(new RuntimeException("ai-chat-service down"))
                .doNothing()
                .when(aiChatFeignClient).upsertProductVector(anyMap());

        Map<String, Integer> result = productService.syncAllVectors();

        assertThat(result)
                .containsEntry("total", 2)
                .containsEntry("success", 1)
                .containsEntry("failed", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldQueryOnlyOnShelfProducts() {
        when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Integer> result = productService.syncAllVectors();

        assertThat(result).containsEntry("total", 0);
        // MP 3.5.7 中条件参数为惰性求值：需先初始化 TableInfo，再调用 getSqlSegment() 触发渲染，
        // paramNameValuePairs 才会填充
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);
        ArgumentCaptor<LambdaQueryWrapper<Product>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productMapper).selectList(captor.capture());
        LambdaQueryWrapper<Product> wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(1);
    }

    // ==================== incrementSales ====================

    @Test
    void shouldIncrementSales() {
        when(productMapper.incrementSales(1L, 5)).thenReturn(1);

        productService.incrementSales(1L, 5);

        verify(productMapper).incrementSales(1L, 5);
    }

    private Product buildProduct(Long id, String name, BigDecimal price, int stock, int status) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setStatus(status);
        p.setCreateTime(LocalDateTime.now());
        return p;
    }
}
