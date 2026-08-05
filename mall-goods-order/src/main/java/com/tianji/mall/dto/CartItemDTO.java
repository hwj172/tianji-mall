package com.tianji.mall.dto;

import lombok.Data;

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
}
