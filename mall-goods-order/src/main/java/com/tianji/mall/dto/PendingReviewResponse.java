package com.tianji.mall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PendingReviewResponse {
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal price;
    private Long skuId;
    private String skuSpecs;
    private LocalDateTime orderCreateTime;
}
