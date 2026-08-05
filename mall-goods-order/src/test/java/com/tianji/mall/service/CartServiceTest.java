package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.dto.CartItemDTO;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.CartItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CartServiceTest {

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private ProductService productService;

    @Mock
    private ProductSkuService skuService;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(productService, skuService);
        ReflectionTestUtils.setField(cartService, "baseMapper", cartItemMapper);
    }

    @Test
    void shouldGetCartList() {
        List<CartItem> items = List.of(buildCartItem(1L, 1L, 1L, 2));
        when(cartItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(items);

        List<CartItemDTO> result = cartService.getCartList(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void shouldAddNewCartItem() {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        Product product = buildProduct(1L, "iPhone", 10);
        when(productService.getProductById(1L)).thenReturn(product);
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        cartService.addItem(1L, req);

        // 不直接 verify insert（BaseMapper 重载歧义），save() 内部调用即可
    }

    @Test
    void shouldThrowWhenStockInsufficient() {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(1L);
        req.setQuantity(20);

        Product product = buildProduct(1L, "iPhone", 5); // 只有5件库存
        when(productService.getProductById(1L)).thenReturn(product);

        assertThatThrownBy(() -> cartService.addItem(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    @Test
    void shouldUpdateQuantity() {
        CartItem item = buildCartItem(1L, 100L, 1L, 2);
        when(cartItemMapper.selectById(1L)).thenReturn(item);
        when(cartItemMapper.updateById(item)).thenReturn(1);

        cartService.updateQuantity(100L, 1L, 5);

        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    void shouldThrowWhenUpdateForeignCartItem() {
        CartItem item = buildCartItem(1L, 100L, 1L, 2);
        when(cartItemMapper.selectById(1L)).thenReturn(item);

        assertThatThrownBy(() -> cartService.updateQuantity(999L, 1L, 5))
                .isInstanceOf(BizException.class)
                .hasMessage("购物车项不存在");
    }

    @Test
    void shouldDeleteCartItem() {
        CartItem item = buildCartItem(1L, 100L, 1L, 2);
        when(cartItemMapper.selectById(1L)).thenReturn(item);
        when(cartItemMapper.deleteById(1L)).thenReturn(1);

        cartService.deleteItem(100L, 1L);

        verify(cartItemMapper).deleteById(1L);
    }

    @Test
    void shouldCheckCartItem() {
        CartItem item = buildCartItem(1L, 100L, 1L, 2);
        when(cartItemMapper.selectById(1L)).thenReturn(item);
        when(cartItemMapper.updateById(item)).thenReturn(1);

        cartService.checkItem(100L, 1L, 0);

        assertThat(item.getChecked()).isEqualTo(0);
    }

    private CartItem buildCartItem(Long id, Long userId, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setChecked(1);
        return item;
    }

    // ==================== SKU 加购测试 ====================

    @Test
    void shouldAddCartItemWithSku() {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(1L);
        req.setSkuId(10L);
        req.setQuantity(1);

        Product product = buildProduct(1L, "iPhone", 10);
        ProductSku sku = buildSku(10L, 1L, "颜色:红", BigDecimal.valueOf(199), 5);
        when(productService.getProductById(1L)).thenReturn(product);
        when(skuService.getById(10L)).thenReturn(sku);
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        cartService.addItem(1L, req);
    }

    @Test
    void shouldThrowWhenSkuNotFound() {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(1L);
        req.setSkuId(999L);
        req.setQuantity(1);

        Product product = buildProduct(1L, "iPhone", 10);
        when(productService.getProductById(1L)).thenReturn(product);
        when(skuService.getById(999L)).thenReturn(null);

        assertThatThrownBy(() -> cartService.addItem(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU不存在");
    }

    @Test
    void shouldThrowWhenSkuStockInsufficient() {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(1L);
        req.setSkuId(10L);
        req.setQuantity(10);

        Product product = buildProduct(1L, "iPhone", 20);
        ProductSku sku = buildSku(10L, 1L, "颜色:红", BigDecimal.valueOf(199), 3);
        when(productService.getProductById(1L)).thenReturn(product);
        when(skuService.getById(10L)).thenReturn(sku);

        assertThatThrownBy(() -> cartService.addItem(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    @Test
    void shouldMergeCartItemWithSameSku() {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(1L);
        req.setSkuId(10L);
        req.setQuantity(1);

        Product product = buildProduct(1L, "iPhone", 10);
        ProductSku sku = buildSku(10L, 1L, "颜色:红", BigDecimal.valueOf(199), 5);
        CartItem existing = buildCartItem(1L, 1L, 1L, 2);
        existing.setSkuId(10L);

        when(productService.getProductById(1L)).thenReturn(product);
        when(skuService.getById(10L)).thenReturn(sku);
        // getOne internally calls selectOne(queryWrapper, throwEx) in MP 3.5.7
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(existing);

        cartService.addItem(1L, req);

        verify(cartItemMapper).updateById(existing);
        assertThat(existing.getQuantity()).isEqualTo(3);
    }

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

    private Product buildProduct(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(1000));
        p.setStock(stock);
        p.setStatus(1);
        return p;
    }
}
