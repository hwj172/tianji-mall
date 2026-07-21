package com.tianji.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private BigDecimal totalGmv;
    private Long totalOrders;
    private Long totalUsers;
    private TimeStats today;
    private TimeStats thisWeek;
    private TimeStats thisMonth;
    private List<TopProduct> topProducts;
    private List<OrderStatusDist> orderStatusDist;
    private List<CategorySales> categorySales;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeStats {
        private BigDecimal gmv;
        private Long orders;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProduct {
        private Long id;
        private String name;
        private Long sales;
        private BigDecimal amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusDist {
        private Integer status;
        private String label;
        private Long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySales {
        private Long categoryId;
        private String categoryName;
        private BigDecimal amount;
    }
}
