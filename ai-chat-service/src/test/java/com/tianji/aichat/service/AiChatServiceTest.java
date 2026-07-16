package com.tianji.aichat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.aichat.entity.AiConversation;
import com.tianji.aichat.feign.McpFeignClient;
import com.tianji.aichat.mapper.AiConversationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private McpFeignClient mcpFeignClient;
    @Mock
    private AiConversationMapper aiConversationMapper;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(restTemplate, mcpFeignClient);
        ReflectionTestUtils.setField(aiChatService, "baseMapper", aiConversationMapper);
        ReflectionTestUtils.setField(aiChatService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(aiChatService, "model", "deepseek-chat");
        ReflectionTestUtils.setField(aiChatService, "apiUrl", "https://api.test.com/v1/chat/completions");
    }

    // ==================== buildMessages ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildMessagesWithSystemPrompt() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("buildMessages", List.class);
        method.setAccessible(true);

        // 历史为空
        List<Map<String, Object>> messages = (List<Map<String, Object>>) method.invoke(aiChatService, List.of());

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content").toString()).contains("天机商城");
        assertThat(messages.get(0).get("content").toString()).contains("search_products");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildMessagesFromHistory() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("buildMessages", List.class);
        method.setAccessible(true);

        // 模拟按 createTime DESC 排序的历史（getRecentHistory 返回的顺序）
        List<AiConversation> history = new ArrayList<>();
        AiConversation assistantMsg = buildConversation(3L, "assistant", "你好，需要什么帮助？", LocalDateTime.now().plusMinutes(10));
        AiConversation userMsg = buildConversation(2L, "user", "推荐手机", LocalDateTime.now().plusMinutes(5));
        AiConversation earlierAssistant = buildConversation(1L, "assistant", "欢迎光临", LocalDateTime.now());
        history.add(assistantMsg);   // 最新
        history.add(userMsg);
        history.add(earlierAssistant); // 最早

        List<Map<String, Object>> messages = (List<Map<String, Object>>) method.invoke(aiChatService, history);

        // system prompt 第一条
        assertThat(messages.get(0).get("role")).isEqualTo("system");

        // 历史按时间正序：早 → 晚
        assertThat(messages.get(1).get("role")).isEqualTo("assistant");
        assertThat(messages.get(1).get("content")).isEqualTo("欢迎光临");
        assertThat(messages.get(2).get("role")).isEqualTo("user");
        assertThat(messages.get(2).get("content")).isEqualTo("推荐手机");
        assertThat(messages.get(3).get("role")).isEqualTo("assistant");
        assertThat(messages.get(3).get("content")).isEqualTo("你好，需要什么帮助？");

        assertThat(messages).hasSize(4);
    }

    // ==================== createTool ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateToolDefinition() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("createTool",
                String.class, String.class, Map.class, List.class);
        method.setAccessible(true);

        Map<String, Object> props = Map.of(
                "keyword", Map.of("type", "string", "description", "搜索关键词"),
                "page", Map.of("type", "integer", "description", "页码")
        );
        List<String> required = List.of("keyword");

        Map<String, Object> tool = (Map<String, Object>) method.invoke(aiChatService, "search", "搜索商品", props, required);

        assertThat(tool.get("type")).isEqualTo("function");

        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) tool.get("function");
        assertThat(function.get("name")).isEqualTo("search");
        assertThat(function.get("description")).isEqualTo("搜索商品");

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
        assertThat(parameters.get("type")).isEqualTo("object");
        assertThat(parameters.get("required")).isEqualTo(required);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        assertThat(properties).containsKeys("keyword", "page");
    }

    // ==================== getHistory ====================

    @Test
    void shouldGetHistory() {
        List<AiConversation> conversations = List.of(
                buildConversation(1L, "user", "推荐手机", LocalDateTime.now()),
                buildConversation(2L, "assistant", "为您找到以下手机...", LocalDateTime.now().plusMinutes(1))
        );
        when(aiConversationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(conversations);

        List<AiConversation> result = aiChatService.getHistory(1L, "session-123");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(1).getRole()).isEqualTo("assistant");
    }

    // ==================== getSystemPrompt ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldContainToolInstructions() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("getSystemPrompt");
        method.setAccessible(true);

        String prompt = (String) method.invoke(aiChatService);

        assertThat(prompt).contains("天机商城");
        assertThat(prompt).contains("search_products");
        assertThat(prompt).contains("get_product");
        assertThat(prompt).contains("add_to_cart");
        assertThat(prompt).contains("get_orders");
    }

    // ==================== helpers ====================

    private AiConversation buildConversation(Long id, String role, String content, LocalDateTime createTime) {
        AiConversation conv = new AiConversation();
        conv.setId(id);
        conv.setUserId(1L);
        conv.setSessionId("session-123");
        conv.setRole(role);
        conv.setContent(content);
        conv.setCreateTime(createTime);
        return conv;
    }
}
