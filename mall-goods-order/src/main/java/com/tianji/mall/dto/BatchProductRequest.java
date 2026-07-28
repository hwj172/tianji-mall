package com.tianji.mall.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchProductRequest {

    @NotEmpty(message = "商品ID列表不能为空")
    private List<Long> ids;
}
