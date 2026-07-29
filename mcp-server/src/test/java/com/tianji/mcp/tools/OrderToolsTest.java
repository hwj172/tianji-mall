package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderToolsTest {

    @Mock
    private MallFeignClient mallFeignClient;

    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        orderTools = new OrderTools(mallFeignClient);
    }

    @Test
    void shouldGetOrdersSuccessfully() {
        when(mallFeignClient.getOrderList(anyLong()))
                .thenReturn(Map.of("data", Map.of("records", new Object[0], "total", 0)));

        ToolResponse result = orderTools.getOrders(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void shouldGetOrderDetailSuccessfully() {
        when(mallFeignClient.getOrder(anyLong()))
                .thenReturn(Map.of("data", Map.of("id", 100, "orderNo", "TEST001")));

        ToolResponse result = orderTools.getOrderDetail(100L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNotNull();
    }

    @Test
    void shouldCreateOrderWithCouponId() {
        when(mallFeignClient.createOrder(eq(1L), anyMap()))
                .thenReturn(Map.of("data", Map.of("id", 200, "orderNo", "ORD002")));

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("addressId", 1L);
        params.put("cartItemIds", List.of(1, 2));
        params.put("couponId", 5L);
        ToolResponse result = orderTools.createOrder(1L, params);

        assertThat(result.isSuccess()).isTrue();
        verify(mallFeignClient).createOrder(eq(1L), anyMap());
    }

    @Test
    void shouldCreateOrderWithoutCouponId() {
        when(mallFeignClient.createOrder(eq(1L), anyMap()))
                .thenReturn(Map.of("data", Map.of("id", 201, "orderNo", "ORD003")));

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("addressId", 1L);
        params.put("cartItemIds", List.of(1));
        ToolResponse result = orderTools.createOrder(1L, params);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldPayOrderSuccessfully() {
        when(mallFeignClient.payOrder(eq(100L), eq(1L)))
                .thenReturn(Map.of("data", Map.of("paymentNo", "PAY001")));

        Map<String, Object> params = Map.of("orderId", 100L);
        ToolResponse result = orderTools.payOrder(1L, params);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldHandleFeignExceptionOnGetOrders() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(503);
        when(mallFeignClient.getOrderList(anyLong())).thenThrow(fe);

        ToolResponse result = orderTools.getOrders(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("暂不可用");
    }

    @Test
    void shouldHandleGeneralExceptionOnCreateOrder() {
        when(mallFeignClient.createOrder(eq(1L), anyMap()))
                .thenThrow(new RuntimeException("库存不足"));

        Map<String, Object> params = Map.of("addressId", 1L, "cartItemIds", List.of(1));
        ToolResponse result = orderTools.createOrder(1L, params);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("失败");
    }

    // ==================== 额外异常分支 ====================

    @Test
    void shouldHandleFeignExceptionOnGetOrderDetail() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(503);
        when(mallFeignClient.getOrder(anyLong())).thenThrow(fe);

        ToolResponse result = orderTools.getOrderDetail(100L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("暂不可用");
    }

    @Test
    void shouldHandleGeneralExceptionOnGetOrders() {
        when(mallFeignClient.getOrderList(anyLong()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        ToolResponse result = orderTools.getOrders(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("失败");
    }

    @Test
    void shouldHandleGeneralExceptionOnGetOrderDetail() {
        when(mallFeignClient.getOrder(anyLong()))
                .thenThrow(new RuntimeException("订单不存在"));

        ToolResponse result = orderTools.getOrderDetail(100L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("失败");
    }

    @Test
    void shouldHandleFeignExceptionOnPayOrder() {
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(503);
        when(mallFeignClient.payOrder(eq(100L), eq(1L))).thenThrow(fe);

        Map<String, Object> params = Map.of("orderId", 100L);
        ToolResponse result = orderTools.payOrder(1L, params);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("暂不可用");
    }

    @Test
    void shouldHandleGeneralExceptionOnPayOrder() {
        when(mallFeignClient.payOrder(eq(100L), eq(1L)))
                .thenThrow(new RuntimeException("支付网关超时"));

        Map<String, Object> params = Map.of("orderId", 100L);
        ToolResponse result = orderTools.payOrder(1L, params);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("失败");
    }
}
