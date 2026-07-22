package com.tianji.mall.service;

import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private ProductMapper productMapper;

    private SeckillService seckillService;

    private Product product;

    @BeforeEach
    void setUp() {
        seckillService = new SeckillService(productMapper);
        product = buildProduct(1L, "iPhone", 100);
    }

    // 1. 秒杀进行中
    @Test
    void shouldDetectActiveSeckill() {
        setupSeckill(product, BigDecimal.valueOf(2999), 10,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(50));

        assertThat(seckillService.isSeckillActive(product)).isTrue();
    }

    // 2. 非秒杀窗口（未开始）
    @Test
    void shouldDetectInactiveSeckill() {
        product.setSeckillPrice(BigDecimal.valueOf(2999));
        product.setSeckillStock(10);
        product.setSeckillStartTime(LocalDateTime.now().plusHours(1));
        product.setSeckillEndTime(LocalDateTime.now().plusHours(2));

        assertThat(seckillService.isSeckillActive(product)).isFalse();
    }

    // 3. 秒杀库存为 0
    @Test
    void shouldReturnFalseWhenStockZero() {
        setupSeckill(product, BigDecimal.valueOf(2999), 0,
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(50));

        assertThat(seckillService.isSeckillActive(product)).isFalse();
    }

    // 4. 设置秒杀
    @Test
    void shouldSetSeckill() {
        when(productMapper.selectById(1L)).thenReturn(product);

        seckillService.setSeckill(1L, BigDecimal.valueOf(1999), 5,
                LocalDateTime.now(), LocalDateTime.now().plusHours(2));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSeckillPrice()).isEqualByComparingTo("1999");
        assertThat(captor.getValue().getSeckillStock()).isEqualTo(5);
    }

    // 5. 秒杀库存超商品库存
    @Test
    void shouldThrowWhenSeckillStockExceedsProductStock() {
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThatThrownBy(() ->
                seckillService.setSeckill(1L, BigDecimal.valueOf(1999), 200,
                        LocalDateTime.now(), LocalDateTime.now().plusHours(2)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("秒杀库存不能超过商品库存");
    }

    // ==================== helpers ====================

    private Product buildProduct(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(6999));
        p.setStock(stock);
        p.setStatus(1);
        p.setCategoryId(1L);
        return p;
    }

    private void setupSeckill(Product p, BigDecimal price, int stock,
                              LocalDateTime start, LocalDateTime end) {
        p.setSeckillPrice(price);
        p.setSeckillStock(stock);
        p.setSeckillStartTime(start);
        p.setSeckillEndTime(end);
    }
}
