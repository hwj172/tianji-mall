package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家回复评价请求。
 */
@Data
public class ReviewReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容最多 500 字")
    private String reply;
}
