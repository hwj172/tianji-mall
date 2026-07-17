package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.entity.Product;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @Autowired
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        productMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== getProductPage ====================

    @Test
    void shouldPageProducts() {
        insertProduct("iPhone", BigDecimal.valueOf(6999), 1L, 10, 1);
        insertProduct("iPad", BigDecimal.valueOf(4999), 1L, 5, 1);
        insertProduct("已下架商品", BigDecimal.valueOf(100), 1L, 3, 0);

        Page<Product> page = productService.getProductPage(null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
    }

    @Test
    void shouldSearchByKeyword() {
        insertProduct("iPhone 15 Pro", BigDecimal.valueOf(7999), 1L, 10, 1);
        insertProduct("MacBook Pro", BigDecimal.valueOf(12999), 1L, 5, 1);

        Page<Product> page = productService.getProductPage(null, "iPhone", 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).contains("iPhone");
    }

    @Test
    void shouldFilterByCategory() {
        insertProduct("手机", BigDecimal.valueOf(5000), 1L, 10, 1);
        insertProduct("笔记本", BigDecimal.valueOf(8000), 2L, 5, 1);

        Page<Product> page = productService.getProductPage(2L, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).isEqualTo("笔记本");
    }

    // ==================== helpers ====================

    private void insertProduct(String name, BigDecimal price, Long categoryId, int stock, int status) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setCategoryId(categoryId);
        p.setStock(stock);
        p.setStatus(status);
        productMapper.insert(p);
    }
}
