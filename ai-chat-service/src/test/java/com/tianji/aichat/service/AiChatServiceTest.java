package com.tianji.aichat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.aichat.dto.ProductDTO;
import com.tianji.aichat.entity.AiConversation;
import com.tianji.aichat.feign.McpFeignClient;
import com.tianji.aichat.feign.ProductFeignClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private McpFeignClient mcpFeignClient;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private ProductFeignClient productFeignClient;
    @Mock
    private AiConversationMapper aiConversationMapper;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(restTemplate, mcpFeignClient, vectorSearchService, productFeignClient);
        ReflectionTestUtils.setField(aiChatService, "baseMapper", aiConversationMapper);
        ReflectionTestUtils.setField(aiChatService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(aiChatService, "model", "deepseek-chat");
        ReflectionTestUtils.setField(aiChatService, "apiUrl", "https://api.test.com/v1/chat/completions");
    }

    // ==================== buildMessages ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildMessagesWithSystemPrompt() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        // 历史为空
        List<Map<String, Object>> messages = (List<Map<String, Object>>) method.invoke(aiChatService, List.of(), List.of());

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content").toString()).contains("天机商城");
        assertThat(messages.get(0).get("content").toString()).contains("search_products");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildMessagesFromHistory() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        // 模拟按 createTime DESC 排序的历史（getRecentHistory 返回的顺序）
        List<AiConversation> history = new ArrayList<>();
        AiConversation assistantMsg = buildConversation(3L, "assistant", "你好，需要什么帮助？", LocalDateTime.now().plusMinutes(10));
        AiConversation userMsg = buildConversation(2L, "user", "推荐手机", LocalDateTime.now().plusMinutes(5));
        AiConversation earlierAssistant = buildConversation(1L, "assistant", "欢迎光临", LocalDateTime.now());
        history.add(assistantMsg);   // 最新
        history.add(userMsg);
        history.add(earlierAssistant); // 最早

        List<Map<String, Object>> messages = (List<Map<String, Object>>) method.invoke(aiChatService, history, List.of());

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
        Method method = AiChatService.class.getDeclaredMethod("getSystemPrompt", List.class);
        method.setAccessible(true);

        String prompt = (String) method.invoke(aiChatService, List.of());

        assertThat(prompt).contains("天机商城");
        assertThat(prompt).contains("search_products");
        assertThat(prompt).contains("get_product");
        assertThat(prompt).contains("add_to_cart");
        assertThat(prompt).contains("get_orders");
    }

    @Test
    void shouldInjectRagProductsIntoSystemPrompt() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("getSystemPrompt", List.class);
        method.setAccessible(true);

        ProductDTO product = new ProductDTO();
        product.setId(1L);
        product.setName("华为Mate 60 Pro");
        product.setPrice(new java.math.BigDecimal("6999.00"));
        product.setStock(100);
        product.setDescription("徕卡摄像头旗舰手机");

        String prompt = (String) method.invoke(aiChatService, List.of(product));

        assertThat(prompt).contains("[RAG检索结果");
        assertThat(prompt).contains("华为Mate 60 Pro");
        assertThat(prompt).contains("6999.00");
    }

    @Test
    void shouldNotInjectRagSectionWhenEmpty() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("getSystemPrompt", List.class);
        method.setAccessible(true);

        String prompt = (String) method.invoke(aiChatService, List.of());

        // base prompt 规则文案中提到 [RAG检索结果]，但不应有实际数据段落
        assertThat(prompt).doesNotContain("[RAG检索结果 — 以下为真实商品数据]");
        assertThat(prompt).doesNotContain("请优先基于以上真实商品数据回复用户");
    }

    // ==================== buildRequestBody ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildRequestBodyWithCorrectStructure() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("buildRequestBody", List.class);
        method.setAccessible(true);

        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "你好"));
        Map<String, Object> body = (Map<String, Object>) method.invoke(aiChatService, messages);

        assertThat(body.get("model")).isEqualTo("deepseek-chat");
        assertThat(body.get("max_tokens")).isEqualTo(2048);
        assertThat(body.get("messages")).isEqualTo(messages);
        assertThat(body.get("tools")).isNotNull();
        assertThat(body.get("tool_choice")).isEqualTo("auto");
    }

    // ==================== executeTool ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteToolSuccessfully() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("executeTool",
                String.class, Map.class, Long.class);
        method.setAccessible(true);

        Map<String, Object> toolResult = Map.of("success", true, "data", Map.of("id", 1, "name", "商品"));
        when(mcpFeignClient.executeTool(any())).thenReturn(toolResult);

        String result = (String) method.invoke(aiChatService, "search_products",
                Map.of("keyword", "手机"), 1L);

        assertThat(result).contains("id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteToolReturnErrorWhenNotSuccess() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("executeTool",
                String.class, Map.class, Long.class);
        method.setAccessible(true);

        Map<String, Object> toolResult = Map.of("success", false, "error", "商品不存在");
        when(mcpFeignClient.executeTool(any())).thenReturn(toolResult);

        String result = (String) method.invoke(aiChatService, "get_product",
                Map.of("productId", 999L), 1L);

        assertThat(result).contains("工具执行失败");
        assertThat(result).contains("商品不存在");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteToolCatchFeignException() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("executeTool",
                String.class, Map.class, Long.class);
        method.setAccessible(true);

        when(mcpFeignClient.executeTool(any()))
                .thenThrow(new RuntimeException("下游服务不可用"));

        String result = (String) method.invoke(aiChatService, "search_products",
                Map.of("keyword", "手机"), 1L);

        assertThat(result).contains("工具调用异常");
    }

    // ==================== searchProductsByRag ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleRagFailureGracefully() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("searchProductsByRag", String.class);
        method.setAccessible(true);

        when(vectorSearchService.searchSimilar(anyString(), eq(5)))
                .thenThrow(new RuntimeException("Milvus 连接失败"));

        List<ProductDTO> result = (List<ProductDTO>) method.invoke(aiChatService, "推荐手机");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyWhenNoVectorResults() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("searchProductsByRag", String.class);
        method.setAccessible(true);

        when(vectorSearchService.searchSimilar(anyString(), eq(5))).thenReturn(List.of());

        List<ProductDTO> result = (List<ProductDTO>) method.invoke(aiChatService, "推荐手机");

        assertThat(result).isEmpty();
    }

    // ==================== getToolDefinitions ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldDefineAllEightTools() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("getToolDefinitions");
        method.setAccessible(true);

        List<Map<String, Object>> tools = (List<Map<String, Object>>) method.invoke(aiChatService);

        assertThat(tools).hasSize(8);
        // 验证各工具名称
        List<String> toolNames = tools.stream()
                .map(t -> (Map<String, Object>) t.get("function"))
                .map(f -> (String) f.get("name"))
                .toList();
        assertThat(toolNames).containsExactlyInAnyOrder(
                "search_products", "get_product", "get_orders", "get_order_detail",
                "get_cart", "add_to_cart", "create_order", "pay_order"
        );
    }

    // ==================== sendToDeepSeek ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnNullWhenDeepSeekApiFails() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("sendToDeepSeek", Map.class);
        method.setAccessible(true);

        when(restTemplate.postForEntity(anyString(), any(), any(), (Class<?>) any()))
                .thenThrow(new RuntimeException("连接超时"));

        Map<String, Object> result = (Map<String, Object>) method.invoke(aiChatService,
                Map.of("model", "test"));

        assertThat(result).isNull();
    }

    // ==================== callDeepSeekWithTools ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnFallbackWhenResponseIsNull() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("callDeepSeekWithTools",
                List.class, Long.class);
        method.setAccessible(true);

        when(restTemplate.postForEntity(anyString(), any(), any(), (Class<?>) any()))
                .thenReturn(null);

        String result = (String) method.invoke(aiChatService,
                List.of(Map.of("role", "user", "content", "你好")), 1L);

        assertThat(result).isEqualTo("抱歉，AI 服务暂时不可用，请稍后再试。");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReturnFallbackWhenChoicesEmpty() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("callDeepSeekWithTools",
                List.class, Long.class);
        method.setAccessible(true);

        org.springframework.http.ResponseEntity responseEntity =
                org.springframework.http.ResponseEntity.ok(Map.of("choices", List.of()));
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(responseEntity);

        String result = (String) method.invoke(aiChatService,
                List.of(Map.of("role", "user", "content", "你好")), 1L);

        assertThat(result).isEqualTo("抱歉，我暂时无法回复，请稍后再试。");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReturnFallbackWhenMessageIsNull() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("callDeepSeekWithTools",
                List.class, Long.class);
        method.setAccessible(true);

        // choices[0].message = null
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", null);
        org.springframework.http.ResponseEntity responseEntity =
                org.springframework.http.ResponseEntity.ok(Map.of("choices", List.of(choice)));
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(responseEntity);

        String result = (String) method.invoke(aiChatService,
                List.of(Map.of("role", "user", "content", "你好")), 1L);

        assertThat(result).isEqualTo("抱歉，我暂时无法回复，请稍后再试。");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReturnFallbackWhenContentIsNull() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("callDeepSeekWithTools",
                List.class, Long.class);
        method.setAccessible(true);

        // choices[0].message.content = null, no tool_calls
        Map<String, Object> messageMap = new LinkedHashMap<>();
        messageMap.put("role", "assistant");
        messageMap.put("content", null);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", messageMap);
        org.springframework.http.ResponseEntity responseEntity =
                org.springframework.http.ResponseEntity.ok(Map.of("choices", List.of(choice)));
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(responseEntity);

        String result = (String) method.invoke(aiChatService,
                List.of(Map.of("role", "user", "content", "你好")), 1L);

        assertThat(result).isEqualTo("抱歉，我暂时无法回复，请稍后再试。");
    }

    // ==================== searchProductsByRag success path ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnProductsWhenRagSucceeds() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("searchProductsByRag", String.class);
        method.setAccessible(true);

        when(vectorSearchService.searchSimilar(anyString(), eq(5))).thenReturn(List.of(1L, 2L));

        Map<String, Object> product1 = new LinkedHashMap<>();
        product1.put("id", 1);
        product1.put("name", "华为Mate 60");
        product1.put("description", "旗舰手机");
        product1.put("price", 6999);
        product1.put("stock", 100);
        product1.put("images", "img1.jpg");

        Map<String, Object> product2 = new LinkedHashMap<>();
        product2.put("id", 2);
        product2.put("name", "iPhone 15");
        product2.put("description", "苹果手机");
        product2.put("price", 7999);
        product2.put("stock", 50);
        product2.put("images", "img2.jpg");

        when(productFeignClient.getProductBatch(any()))
                .thenReturn(Map.of("data", List.of(product1, product2)));

        List<ProductDTO> result = (List<ProductDTO>) method.invoke(aiChatService, "推荐手机");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("华为Mate 60");
        assertThat(result.get(1).getName()).isEqualTo("iPhone 15");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyWhenFeignReturnsNullData() throws Exception {
        Method method = AiChatService.class.getDeclaredMethod("searchProductsByRag", String.class);
        method.setAccessible(true);

        when(vectorSearchService.searchSimilar(anyString(), eq(5))).thenReturn(List.of(1L));
        when(productFeignClient.getProductBatch(any())).thenReturn(Map.of()); // no "data" key

        List<ProductDTO> result = (List<ProductDTO>) method.invoke(aiChatService, "推荐手机");

        assertThat(result).isEmpty();
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
