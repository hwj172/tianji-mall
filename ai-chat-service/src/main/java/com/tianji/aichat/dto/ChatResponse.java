package com.tianji.aichat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {

    private String sessionId;
    private String reply;
    private List<Map<String, Object>> products;
    /** 工具调用过程（tool/status/action），供前端展示"已搜索商品"等 chip */
    private List<Map<String, Object>> toolExecutions;

    /** 兼容无工具调用场景的构造 */
    public ChatResponse(String sessionId, String reply, List<Map<String, Object>> products) {
        this(sessionId, reply, products, List.of());
    }
}
