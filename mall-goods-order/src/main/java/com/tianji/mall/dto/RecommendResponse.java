package com.tianji.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponse {

    private List<RecommendItem> guessYouLike;
    private List<RecommendItem> hotSales;
    private List<RecommendItem> buyAfterBuy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem {
        private Long id;
        private String name;
        private BigDecimal price;
        private Long sales;
        private String reason;
    }
}
