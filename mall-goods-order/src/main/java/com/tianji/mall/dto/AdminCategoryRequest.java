package com.tianji.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminCategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "父分类ID，默认0表示一级分类")
    private Long parentId;

    @Schema(description = "排序号，默认0")
    private Integer sort;
}
