package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        Page<Product> result = productService.getProductPage(null, "手机", 1, 10);

        assertThat(result).isNotNull();
        verify(productMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReturnProductPageWithCategoryFilter() {
        @SuppressWarnings("unchecked")
        Page<Product> mockPage = new Page<>(1, 10);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<Product> result = productService.getProductPage(1L, null, 1, 10);

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
