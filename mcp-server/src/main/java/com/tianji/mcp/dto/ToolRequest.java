package com.tianji.mcp.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ToolRequest {

    /** 工具名: search_products | get_product | get_orders | get_cart | add_to_cart */
    private String tool;
    /** 工具参数 */
    private Map<String, Object> parameters;
    /** 用户ID（由 ai-chat-service 传入） */
    private Long userId;
}
