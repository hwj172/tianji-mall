package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartToolsTest {

    @Mock
    private MallFeignClient mallFeignClient;

    private CartTools cartTools;

    @BeforeEach
    void setUp() {
        cartTools = new CartTools(mallFeignClient);
    }

    @Test
    void shouldGetCartSuccessfully() {
        when(mallFeignClient.getCartList(anyLong()))
                .thenReturn(Map.of("data", Map.of("items", new Object[0])));

        ToolResponse result = cartTools.getCart(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void shouldAddToCartWithDefaultQuantity() {
        when(mallFeignClient.addToCart(anyMap()))
                .thenReturn(Map.of("data", Map.of("id", 1)));

        ToolResponse result = cartTools.addToCart(1L, Map.of("productId", 100L));

        assertThat(result.isSuccess()).isTrue();
        // 默认 quantity=1 已被设置到 body 中，verify Feign 调用包含此值
        verify(mallFeignClient).addToCart(anyMap());
    }

    @Test
    void shouldAddToCartWithCustomQuantity() {
        when(mallFeignClient.addToCart(anyMap()))
                .thenReturn(Map.of("data", Map.of("id", 1)));

        ToolResponse result = cartTools.addToCart(1L, Map.of("productId", 100L, "quantity", 5));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldHandleFeignExceptionOnGetCart() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(503);
        when(mallFeignClient.getCartList(anyLong())).thenThrow(fe);

        ToolResponse result = cartTools.getCart(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("暂不可用");
    }

    @Test
    void shouldHandleGeneralExceptionOnAddToCart() {
        when(mallFeignClient.addToCart(anyMap()))
                .thenThrow(new RuntimeException("内部错误"));

        ToolResponse result = cartTools.addToCart(1L, Map.of("productId", 100L, "quantity", 2));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("失败");
    }
}
