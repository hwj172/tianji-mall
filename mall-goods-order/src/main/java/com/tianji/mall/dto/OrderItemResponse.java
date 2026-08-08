package com.tianji.mall.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {

    /** 订单明细主键（退款申请需回传 orderItemId） */
    private Long id;
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

    // Full constructor with order item id
    public OrderItemResponse(Long id, Long productId, String productName, BigDecimal price, Integer quantity,
                             Long skuId, String skuSpecs) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.skuId = skuId;
        this.skuSpecs = skuSpecs;
    }
}
