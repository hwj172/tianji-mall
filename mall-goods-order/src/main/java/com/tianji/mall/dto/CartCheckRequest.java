package com.tianji.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartCheckRequest {

    @NotNull(message = "购物车项ID不能为空")
    private Long cartItemId;

    @NotNull(message = "选中状态不能为空")
    private Integer checked;
}
