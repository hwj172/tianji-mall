package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.entity.Product;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.NotificationService;
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

    @MockBean
    private PayFeignClient payFeignClient;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    @Autowired
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        productMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== getProductPage ====================

    @Test
    void shouldPageProducts() {
        insertProduct("iPhone", "苹果手机", BigDecimal.valueOf(6999), 1L, 10, 1);
        insertProduct("iPad", "苹果平板", BigDecimal.valueOf(4999), 1L, 5, 1);
        insertProduct("已下架商品", "已下架", BigDecimal.valueOf(100), 1L, 3, 0);

        Page<Product> page = productService.getProductPage(null, null, null, null, null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
    }

    @Test
    void shouldSearchByKeyword() {
        insertProduct("iPhone 15 Pro", "苹果旗舰手机", BigDecimal.valueOf(7999), 1L, 10, 1);
        insertProduct("MacBook Pro", "苹果笔记本", BigDecimal.valueOf(12999), 1L, 5, 1);

        Page<Product> page = productService.getProductPage(null, "iPhone", null, null, null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).contains("iPhone");
    }

    @Test
    void shouldSearchByDescription() {
        insertProduct("MBP", "MacBook Pro 笔记本电脑", BigDecimal.valueOf(12999), 1L, 10, 1);
        insertProduct("iPad Air", "平板电脑", BigDecimal.valueOf(4999), 1L, 5, 1);

        Page<Product> page = productService.getProductPage(null, "笔记本", null, null, null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).isEqualTo("MBP");
    }

    @Test
    void shouldFilterByCategory() {
        insertProduct("手机", "手机", BigDecimal.valueOf(5000), 1L, 10, 1);
        insertProduct("笔记本", "笔记本", BigDecimal.valueOf(8000), 2L, 5, 1);

        Page<Product> page = productService.getProductPage(2L, null, null, null, null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).isEqualTo("笔记本");
    }

    @Test
    void shouldFilterByPriceRange() {
        insertProduct("便宜商品", "desc", BigDecimal.valueOf(99), 1L, 10, 1);
        insertProduct("中等商品", "desc", BigDecimal.valueOf(500), 1L, 10, 1);
        insertProduct("昂贵商品", "desc", BigDecimal.valueOf(5000), 1L, 10, 1);

        Page<Product> page = productService.getProductPage(null, null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(1000), null, null, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getName()).isEqualTo("中等商品");
    }

    @Test
    void shouldSortByPriceAscending() {
        insertProduct("B", "desc", BigDecimal.valueOf(200), 1L, 10, 1);
        insertProduct("A", "desc", BigDecimal.valueOf(100), 1L, 10, 1);
        insertProduct("C", "desc", BigDecimal.valueOf(300), 1L, 10, 1);

        Page<Product> page = productService.getProductPage(null, null,
                null, null, "price_asc", null, 1, 10);

        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getRecords().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(page.getRecords().get(2).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void shouldSortByPriceDescending() {
        insertProduct("B", "desc", BigDecimal.valueOf(200), 1L, 10, 1);
        insertProduct("A", "desc", BigDecimal.valueOf(100), 1L, 10, 1);

        Page<Product> page = productService.getProductPage(null, null,
                null, null, "price_desc", null, 1, 10);

        assertThat(page.getRecords().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    // ==================== helpers ====================

    private void insertProduct(String name, String description, BigDecimal price, Long categoryId, int stock, int status) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setCategoryId(categoryId);
        p.setStock(stock);
        p.setStatus(status);
        productMapper.insert(p);
    }
}
