package com.tianji.mall.service;

import com.tianji.common.result.R;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserFeignClient userFeignClient;

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            1, "待付款",
            2, "已付款",
            3, "已发货",
            4, "已完成",
            5, "已取消"
    );

    public DashboardResponse getDashboard() {
        BigDecimal totalGmv = orderMapper.selectTotalGmv();
        Long totalOrders = orderMapper.selectPaidOrderCount();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        DashboardResponse.TimeStats today = buildTimeStats(todayStart);
        DashboardResponse.TimeStats thisWeek = buildTimeStats(weekStart);
        DashboardResponse.TimeStats thisMonth = buildTimeStats(monthStart);

        Long totalUsers = 0L;
        try {
            R<Long> result = userFeignClient.countUsers();
            if (result != null && result.getData() != null) {
                totalUsers = result.getData();
            }
        } catch (Exception e) {
            log.warn("获取用户数失败，降级为 0", e);
        }

        List<DashboardResponse.TopProduct> topProducts = orderItemMapper.selectTopSellingProducts().stream()
                .map(row -> new DashboardResponse.TopProduct(
                        toLong(row.get("product_id")),
                        (String) row.get("name"),
                        toLong(row.get("sales")),
                        toBigDecimal(row.get("amount"))
                ))
                .toList();

        List<DashboardResponse.OrderStatusDist> statusDist = orderMapper.selectStatusDistribution().stream()
                .map(row -> new DashboardResponse.OrderStatusDist(
                        toInt(row.get("status")),
                        STATUS_LABELS.getOrDefault(toInt(row.get("status")), "未知"),
                        toLong(row.get("cnt"))
                ))
                .toList();

        List<DashboardResponse.CategorySales> categorySales = orderItemMapper.selectCategorySales().stream()
                .map(row -> new DashboardResponse.CategorySales(
                        toLong(row.get("category_id")),
                        (String) row.get("category_name"),
                        toBigDecimal(row.get("amount"))
                ))
                .toList();

        return new DashboardResponse(totalGmv, totalOrders, totalUsers,
                today, thisWeek, thisMonth, topProducts, statusDist, categorySales);
    }

    private DashboardResponse.TimeStats buildTimeStats(LocalDateTime start) {
        BigDecimal gmv = orderMapper.selectGmvByTimeRange(start);
        Long orders = orderMapper.selectPaidOrderCountByTimeRange(start);
        return new DashboardResponse.TimeStats(gmv != null ? gmv : BigDecimal.ZERO,
                orders != null ? orders : 0L);
    }

    private static Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private static Integer toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
