package com.tianji.aichat.controller;

import com.tianji.aichat.dto.ChatRequest;
import com.tianji.aichat.dto.ChatResponse;
import com.tianji.aichat.dto.ConversationDTO;
import com.tianji.aichat.service.AiChatService;
import com.tianji.common.util.JwtUtil;
import com.tianji.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final JwtUtil jwtUtil;

    /**
     * 发送对话消息
     */
    @PostMapping("/send")
    public R<ChatResponse> send(@RequestHeader("Authorization") String authHeader,
                                @Valid @RequestBody ChatRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(aiChatService.chat(userId, req.getSessionId(), req.getMessage()));
    }

    /**
     * 查询对话历史（assistant 消息含商品卡片）
     */
    @GetMapping("/history/{sessionId}")
    public R<List<ConversationDTO>> history(@RequestHeader("Authorization") String authHeader,
                                             @PathVariable("sessionId") String sessionId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(aiChatService.getHistoryWithProducts(userId, sessionId));
    }

    /**
     * 会话列表（按 sessionId 分组）
     */
    @GetMapping("/sessions")
    public R<List<Map<String, Object>>> sessions(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(aiChatService.getSessions(userId));
    }
}
