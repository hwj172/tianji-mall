package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统公告发布请求（平台 admin 面向全量用户广播，userId=0 标记）。
 */
@Data
public class NotificationRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "标题最多 128 字")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 512, message = "内容最多 512 字")
    private String content;
}
