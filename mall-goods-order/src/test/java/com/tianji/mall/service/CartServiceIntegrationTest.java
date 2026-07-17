package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.CartItemMapper;
import com.tianji.mall.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartServiceIntegrationTest {

    @Autowired
    private CartService cartService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        cartItemMapper.delete(new LambdaQueryWrapper<>());
        productMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== addItem ====================

    @Test
    void shouldAddNewCartItem() {
        Product product = insertProduct("iPhone", 10);
        CartAddRequest req = buildAddRequest(product.getId(), 2);

        cartService.addItem(1L, req);

        List<CartItem> items = cartService.getCartList(1L);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(product.getId());
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void shouldIncrementQuantityWhenProductAlreadyInCart() {
        Product product = insertProduct("iPhone", 10);
        CartAddRequest req = buildAddRequest(product.getId(), 2);

        cartService.addItem(1L, req);
        cartService.addItem(1L, req);

        List<CartItem> items = cartService.getCartList(1L);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(4);
    }

    @Test
    void shouldThrowWhenStockInsufficient() {
        Product product = insertProduct("iPhone", 3);
        CartAddRequest req = buildAddRequest(product.getId(), 10);

        assertThatThrownBy(() -> cartService.addItem(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不足");
    }

    // ==================== getCartList ====================

    @Test
    void shouldGetCartList() {
        Product p1 = insertProduct("iPhone", 10);
        Product p2 = insertProduct("MacBook", 5);
        insertCartItem(1L, p1.getId(), 2);
        insertCartItem(1L, p2.getId(), 1);

        List<CartItem> items = cartService.getCartList(1L);

        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> i.getUserId().equals(1L));
    }

    // ==================== helpers ====================

    private Product insertProduct(String name, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(1000));
        p.setStock(stock);
        p.setCategoryId(1L);
        p.setStatus(1);
        productMapper.insert(p);
        return p;
    }

    private void insertCartItem(Long userId, Long productId, int quantity) {
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setChecked(1);
        cartItemMapper.insert(item);
    }

    private CartAddRequest buildAddRequest(Long productId, int quantity) {
        CartAddRequest req = new CartAddRequest();
        req.setProductId(productId);
        req.setQuantity(quantity);
        return req;
    }
}
