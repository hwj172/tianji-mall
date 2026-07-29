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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductToolsTest {

    @Mock
    private MallFeignClient mallFeignClient;

    private ProductTools productTools;

    @BeforeEach
    void setUp() {
        productTools = new ProductTools(mallFeignClient);
    }

    @Test
    void shouldSearchProductsSuccessfully() {
        when(mallFeignClient.searchProducts(anyMap()))
                .thenReturn(Map.of("data", Map.of("records", new Object[0], "total", 0)));

        ToolResponse result = productTools.searchProducts(Map.of("keyword", "手机"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void shouldGetProductDetailSuccessfully() {
        when(mallFeignClient.getProduct(anyLong()))
                .thenReturn(Map.of("data", Map.of("id", 1, "name", "测试商品")));

        ToolResponse result = productTools.getProductDetail(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void shouldHandleFeignExceptionOnSearch() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(503);
        when(mallFeignClient.searchProducts(anyMap())).thenThrow(fe);

        ToolResponse result = productTools.searchProducts(Map.of("keyword", "手机"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("暂不可用");
    }

    @Test
    void shouldHandleGeneralExceptionOnSearch() {
        when(mallFeignClient.searchProducts(anyMap()))
                .thenThrow(new RuntimeException("连接超时"));

        ToolResponse result = productTools.searchProducts(Map.of("keyword", "手机"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("失败");
    }

    @Test
    void shouldHandleFeignExceptionOnGetDetail() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(500);
        when(mallFeignClient.getProduct(anyLong())).thenThrow(fe);

        ToolResponse result = productTools.getProductDetail(99L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("暂不可用");
    }
}
