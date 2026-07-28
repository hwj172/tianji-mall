package com.tianji.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteToggleRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;
}
