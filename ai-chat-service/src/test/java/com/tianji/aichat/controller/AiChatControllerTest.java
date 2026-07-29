package com.tianji.aichat.controller;

import com.tianji.aichat.dto.ChatResponse;
import com.tianji.aichat.entity.AiConversation;
import com.tianji.aichat.service.AiChatService;
import com.tianji.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== POST /api/chat/send ====================

    @Test
    void shouldSendChatSuccessfully() throws Exception {
        ChatResponse resp = new ChatResponse("session-abc", "您好！为您推荐以下商品...", null);
        when(aiChatService.chat(eq(1L), isNull(), eq("推荐手机")))
                .thenReturn(resp);

        mockMvc.perform(post("/api/chat/send")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"推荐手机\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("session-abc"))
                .andExpect(jsonPath("$.data.reply").value("您好！为您推荐以下商品..."));
    }

    @Test
    void shouldSendChatWithSessionId() throws Exception {
        ChatResponse resp = new ChatResponse("existing-session", "继续为您服务", null);
        when(aiChatService.chat(eq(1L), eq("existing-session"), eq("再看看别的")))
                .thenReturn(resp);

        mockMvc.perform(post("/api/chat/send")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"existing-session\",\"message\":\"再看看别的\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("existing-session"));
    }

    @Test
    void shouldReturnErrorWhenMessageEmpty() throws Exception {
        mockMvc.perform(post("/api/chat/send")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== GET /api/chat/history/{sessionId} ====================

    @Test
    void shouldGetChatHistory() throws Exception {
        AiConversation conv1 = new AiConversation();
        conv1.setId(1L);
        conv1.setUserId(1L);
        conv1.setSessionId("session-123");
        conv1.setRole("user");
        conv1.setContent("推荐手机");
        conv1.setCreateTime(LocalDateTime.now());

        AiConversation conv2 = new AiConversation();
        conv2.setId(2L);
        conv2.setUserId(1L);
        conv2.setSessionId("session-123");
        conv2.setRole("assistant");
        conv2.setContent("为您找到以下手机...");
        conv2.setCreateTime(LocalDateTime.now().plusMinutes(1));

        when(aiChatService.getHistory(1L, "session-123")).thenReturn(List.of(conv1, conv2));

        mockMvc.perform(get("/api/chat/history/session-123")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"));
    }

    @Test
    void shouldReturnEmptyHistoryForNewSession() throws Exception {
        when(aiChatService.getHistory(1L, "new-session")).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/history/new-session")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
