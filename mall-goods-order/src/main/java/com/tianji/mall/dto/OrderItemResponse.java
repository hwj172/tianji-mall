package com.tianji.mall.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {

    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private Long skuId;
    private String skuSpecs;

    // No-SKU convenience constructor (backward compatible)
    public OrderItemResponse(Long productId, String productName, BigDecimal price, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    // Full constructor with SKU info
    public OrderItemResponse(Long productId, String productName, BigDecimal price, Integer quantity,
                             Long skuId, String skuSpecs) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.skuId = skuId;
        this.skuSpecs = skuSpecs;
    }
}
