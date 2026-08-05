package com.tianji.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DirectOrderItem {
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    private Long skuId;                          // 可选：无 SKU 商品不传
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
