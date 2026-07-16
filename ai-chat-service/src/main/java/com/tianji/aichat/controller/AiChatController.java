package com.tianji.aichat.controller;

import com.tianji.aichat.dto.ChatRequest;
import com.tianji.aichat.dto.ChatResponse;
import com.tianji.aichat.entity.AiConversation;
import com.tianji.aichat.service.AiChatService;
import com.tianji.common.util.JwtUtil;
import com.tianji.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 查询对话历史
     */
    @GetMapping("/history/{sessionId}")
    public R<List<AiConversation>> history(@RequestHeader("Authorization") String authHeader,
                                            @PathVariable("sessionId") String sessionId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(aiChatService.getHistory(userId, sessionId));
    }
}
