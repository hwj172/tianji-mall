package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.mapper.ShopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopMapper shopMapper;

    private ShopService shopService;

    @BeforeEach
    void setUp() {
        shopService = new ShopService();
        ReflectionTestUtils.setField(shopService, "baseMapper", shopMapper);
        // userFeignClient is null (optional in tests)
    }

    @Test
    void shouldRegisterShop() {
        when(shopMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);
        when(shopMapper.insert(any(Shop.class))).thenReturn(1);

        Shop shop = shopService.register(1L, "测试店铺", null, "店铺描述");

        assertThat(shop.getName()).isEqualTo("测试店铺");
        assertThat(shop.getSellerId()).isEqualTo(1L);
        assertThat(shop.getStatus()).isEqualTo(1);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("测试店铺");
    }

    @Test
    void shouldRejectDuplicateShop() {
        Shop existing = new Shop();
        existing.setId(1L);
        existing.setSellerId(1L);
        when(shopMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(existing);

        assertThatThrownBy(() -> shopService.register(1L, "测试", null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已拥有店铺");
    }

    @Test
    void shouldGetBySellerId() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setSellerId(1L);
        shop.setName("测试");
        when(shopMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(shop);

        Shop result = shopService.getBySellerId(1L);

        assertThat(result.getName()).isEqualTo("测试");
    }

    @Test
    void shouldThrowWhenSellerHasNoShop() {
        when(shopMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> shopService.getBySellerId(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("还未开店");
    }

    @Test
    void shouldUpdateShopInfo() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setSellerId(1L);
        shop.setName("旧名");
        when(shopMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(shop);
        when(shopMapper.updateById(any(Shop.class))).thenReturn(1);

        shopService.updateShopInfo(1L, "新名", null, null, null);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名");
    }

    @Test
    void shouldUpdateShopNotice() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setSellerId(1L);
        when(shopMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(shop);
        when(shopMapper.updateById(any(Shop.class))).thenReturn(1);

        shopService.updateShopInfo(1L, null, null, null, "本店促销中");

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNotice()).isEqualTo("本店促销中");
    }
}
