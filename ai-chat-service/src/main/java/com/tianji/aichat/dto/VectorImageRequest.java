package com.tianji.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 图片向量请求：商品图回填（productId + imageUrl）与以图搜图（imageUrl）。
 */
@Data
public class VectorImageRequest {

    /** upsert 时必填的商品 ID */
    private Long productId;

    /** 图片 URL（商品图 / 用户上传图） */
    @NotBlank(message = "图片地址不能为空")
    private String imageUrl;
}
