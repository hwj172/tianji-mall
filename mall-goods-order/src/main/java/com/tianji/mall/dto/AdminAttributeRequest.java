package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminAttributeRequest {

    @NotBlank(message = "属性名称不能为空")
    private String name;

    @NotBlank(message = "属性值不能为空")
    private String value;

    private Integer sort;
}
