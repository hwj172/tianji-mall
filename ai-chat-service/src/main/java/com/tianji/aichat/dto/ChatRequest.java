package com.tianji.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    /** 会话ID（可选，不传则自动生成） */
    private String sessionId;

    /** 用户消息 */
    @NotBlank(message = "消息不能为空")
    private String message;
}
