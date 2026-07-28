package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminProductRequest {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private Long categoryId;

    private String images;

    private Integer status;
}
