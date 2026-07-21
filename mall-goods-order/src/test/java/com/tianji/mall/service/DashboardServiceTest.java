package com.tianji.mall.service;

import com.tianji.common.result.R;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private UserFeignClient userFeignClient;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(orderMapper, orderItemMapper, userFeignClient);
    }

    @Test
    void shouldReturnDashboardWithAllMetrics() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.valueOf(100000));
        when(orderMapper.selectPaidOrderCount()).thenReturn(100L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.valueOf(5000));
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(10L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(200L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of(
                Map.of("product_id", 1L, "name", "iPhone", "sales", 50L, "amount", BigDecimal.valueOf(499900))
        ));
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of(
                Map.of("status", 1, "cnt", 20L),
                Map.of("status", 2, "cnt", 30L)
        ));
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of(
                Map.of("category_id", 1L, "category_name", "手机数码", "amount", BigDecimal.valueOf(50000))
        ));

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTotalGmv()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(resp.getTotalOrders()).isEqualTo(100L);
        assertThat(resp.getTotalUsers()).isEqualTo(200L);
        assertThat(resp.getToday().getGmv()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(resp.getToday().getOrders()).isEqualTo(10L);
        assertThat(resp.getThisWeek().getGmv()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(resp.getThisMonth().getGmv()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(resp.getTopProducts()).hasSize(1);
        assertThat(resp.getTopProducts().get(0).getName()).isEqualTo("iPhone");
        assertThat(resp.getOrderStatusDist()).hasSize(2);
        assertThat(resp.getCategorySales()).hasSize(1);
    }

    @Test
    void shouldReturnZeroWhenNoData() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(0L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of());
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of());
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTotalGmv()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.getTotalOrders()).isEqualTo(0L);
        assertThat(resp.getTotalUsers()).isEqualTo(0L);
        assertThat(resp.getTopProducts()).isEmpty();
        assertThat(resp.getOrderStatusDist()).isEmpty();
        assertThat(resp.getCategorySales()).isEmpty();
    }

    @Test
    void shouldFallbackUserCountToZeroWhenFeignFails() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenThrow(new RuntimeException("connection refused"));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of());
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of());
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTotalUsers()).isEqualTo(0L);
    }

    @Test
    void shouldUseCorrectStatusLabels() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(0L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of());
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of(
                Map.of("status", 1, "cnt", 10L),
                Map.of("status", 2, "cnt", 20L),
                Map.of("status", 3, "cnt", 30L),
                Map.of("status", 4, "cnt", 40L),
                Map.of("status", 5, "cnt", 50L)
        ));
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getOrderStatusDist()).hasSize(5);
        assertThat(resp.getOrderStatusDist().get(0).getLabel()).isEqualTo("待付款");
        assertThat(resp.getOrderStatusDist().get(1).getLabel()).isEqualTo("已付款");
        assertThat(resp.getOrderStatusDist().get(2).getLabel()).isEqualTo("已发货");
        assertThat(resp.getOrderStatusDist().get(3).getLabel()).isEqualTo("已完成");
        assertThat(resp.getOrderStatusDist().get(4).getLabel()).isEqualTo("已取消");
    }

    @Test
    void shouldSortTopProductsBySalesDesc() {
        when(orderMapper.selectTotalGmv()).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCount()).thenReturn(0L);
        when(orderMapper.selectGmvByTimeRange(any())).thenReturn(BigDecimal.ZERO);
        when(orderMapper.selectPaidOrderCountByTimeRange(any())).thenReturn(0L);
        when(userFeignClient.countUsers()).thenReturn(R.ok(0L));
        when(orderItemMapper.selectTopSellingProducts()).thenReturn(List.of(
                Map.of("product_id", 1L, "name", "A", "sales", 10L, "amount", BigDecimal.valueOf(100)),
                Map.of("product_id", 2L, "name", "B", "sales", 50L, "amount", BigDecimal.valueOf(500))
        ));
        when(orderMapper.selectStatusDistribution()).thenReturn(List.of());
        when(orderItemMapper.selectCategorySales()).thenReturn(List.of());

        DashboardResponse resp = dashboardService.getDashboard();

        assertThat(resp.getTopProducts().get(0).getName()).isEqualTo("A");
        assertThat(resp.getTopProducts().get(0).getSales()).isEqualTo(10L);
    }
}
