package com.tianji.aichat.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话历史返回（含每轮 assistant 关联的商品卡片）。
 */
@Data
public class ConversationDTO {

    private Long id;
    private String sessionId;
    private String role;
    private String content;
    /** 本轮关联商品ID（逗号分隔） */
    private String productIds;
    /** 关联商品列表（历史回显商品卡片） */
    private List<Map<String, Object>> products;
    private LocalDateTime createTime;
}
