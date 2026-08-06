package com.tianji.mall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartItemDTO {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private Long skuId;
    private Integer checked;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String skuSpecs;   // SKU 规格描述（如 "红色;XL"），无 SKU 为 null
    private BigDecimal price;  // 单价：SKU 商品取 SKU 价格，无 SKU 取商品默认价
}
