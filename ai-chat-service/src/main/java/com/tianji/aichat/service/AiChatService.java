package com.tianji.aichat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aichat.dto.ChatResponse;
import com.tianji.aichat.dto.ConversationDTO;
import com.tianji.aichat.dto.ProductDTO;
import com.tianji.aichat.entity.AiConversation;
import com.tianji.aichat.feign.McpFeignClient;
import com.tianji.aichat.feign.ProductFeignClient;
import com.tianji.aichat.mapper.AiConversationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiChatService extends ServiceImpl<AiConversationMapper, AiConversation> {

    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_HISTORY = 20;

    private final RestTemplate restTemplate;
    private final McpFeignClient mcpFeignClient;
    private final VectorSearchService vectorSearchService;
    private final ProductFeignClient productFeignClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    @Value("${deepseek.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    public AiChatService(RestTemplate restTemplate, McpFeignClient mcpFeignClient,
                         VectorSearchService vectorSearchService, ProductFeignClient productFeignClient) {
        this.restTemplate = restTemplate;
        this.mcpFeignClient = mcpFeignClient;
        this.vectorSearchService = vectorSearchService;
        this.productFeignClient = productFeignClient;
    }

    /**
     * 处理用户对话消息，返回 AI 回复（含推荐商品列表）
     */
    @Transactional
    public ChatResponse chat(Long userId, String sessionId, String message) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        // 1. 保存用户消息
        AiConversation userMsg = new AiConversation();
        userMsg.setUserId(userId);
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(message);
        save(userMsg);

        // 2. 加载对话历史
        List<AiConversation> history = getRecentHistory(userId, sessionId);

        // 3. RAG 预检索（失败降级为空列表，fallback 到工具调用）
        List<ProductDTO> ragProducts = searchProductsByRag(message);

        // 3.5 收集推荐商品（RAG 命中 + 工具调用命中，按 id 去重）
        List<Map<String, Object>> products = new ArrayList<>();
        if (ragProducts != null) {
            for (ProductDTO p : ragProducts) {
                products.add(productToMap(p));
            }
        }

        // 4. 构建 messages（system prompt 含 RAG 上下文 + 历史 + 当前消息）
        List<Map<String, Object>> messages = buildMessages(history, ragProducts);

        // 5. 调用 DeepSeek（含工具调用循环，工具命中的商品追加到 products；记录工具执行过程）
        List<Map<String, Object>> toolExecutions = new ArrayList<>();
        String reply = callDeepSeekWithTools(messages, userId, products, toolExecutions);

        // 6. 保存 assistant 回复（携带本轮关联商品 ID，供历史回显商品卡片）
        List<Map<String, Object>> finalProducts = dedupeProducts(products);
        AiConversation assistantMsg = new AiConversation();
        assistantMsg.setUserId(userId);
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        if (!finalProducts.isEmpty()) {
            assistantMsg.setProductIds(finalProducts.stream()
                    .map(p -> String.valueOf(p.get("id"))).collect(Collectors.joining(",")));
        }
        save(assistantMsg);

        return new ChatResponse(sessionId, reply, finalProducts, toolExecutions);
    }

    /**
     * 查询对话历史（assistant 消息附带商品卡片）
     */
    public List<ConversationDTO> getHistoryWithProducts(Long userId, String sessionId) {
        return list(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getSessionId, sessionId)
                .orderByAsc(AiConversation::getCreateTime))
                .stream().map(this::toConversationDTO).toList();
    }

    /**
     * 会话列表：按 sessionId 分组，返回每个会话的首条消息摘要 + 最新消息时间 + 消息数
     */
    public List<Map<String, Object>> getSessions(Long userId) {
        List<AiConversation> all = list(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getCreateTime));
        // 按 sessionId 分组（保持最近使用在前）
        Map<String, List<AiConversation>> bySession = new LinkedHashMap<>();
        for (AiConversation c : all) {
            bySession.computeIfAbsent(c.getSessionId(), k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Map.Entry<String, List<AiConversation>> entry : bySession.entrySet()) {
            List<AiConversation> list = entry.getValue();
            AiConversation first = list.get(list.size() - 1); // 最早一条
            AiConversation latest = list.get(0);              // 最新一条
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("sessionId", entry.getKey());
            s.put("title", first.getContent() != null
                    ? first.getContent().replaceAll("\\s+", " ").trim()
                    : "新会话");
            if (s.get("title") != null && ((String) s.get("title")).length() > 20) {
                s.put("title", ((String) s.get("title")).substring(0, 20) + "…");
            }
            s.put("lastTime", latest.getCreateTime());
            s.put("messageCount", list.size());
            sessions.add(s);
        }
        return sessions;
    }

    private ConversationDTO toConversationDTO(AiConversation conv) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conv.getId());
        dto.setSessionId(conv.getSessionId());
        dto.setRole(conv.getRole());
        dto.setContent(conv.getContent());
        dto.setProductIds(conv.getProductIds());
        dto.setCreateTime(conv.getCreateTime());
        // assistant 消息且有关联商品时，批量回填商品卡片
        if (conv.getProductIds() != null && !conv.getProductIds().isBlank()) {
            List<Long> ids = Arrays.stream(conv.getProductIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::valueOf).toList();
            dto.setProducts(fetchProductsByIds(ids));
        }
        return dto;
    }

    /**
     * 批量查询商品（Feign，best-effort 失败返回空）
     */
    private List<Map<String, Object>> fetchProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> result = productFeignClient.getProductBatch(ids);
            Object data = result != null ? result.get("data") : null;
            if (data == null) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) data;
            return list;
        } catch (Exception e) {
            log.warn("批量查询商品失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ===== 私有方法 =====

    private List<AiConversation> getRecentHistory(Long userId, String sessionId) {
        return list(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getSessionId, sessionId)
                .orderByDesc(AiConversation::getCreateTime)
                .last("LIMIT " + MAX_HISTORY));
    }

    /**
     * RAG 向量搜索商品：embedding → Milvus → Feign 批量查询。失败返回空列表不抛异常。
     */
    private List<ProductDTO> searchProductsByRag(String message) {
        try {
            List<Long> ids = vectorSearchService.searchSimilar(message, 5);
            if (ids.isEmpty()) {
                return List.of();
            }
            Map<String, Object> result = productFeignClient.getProductBatch(ids);
            Object data = result != null ? result.get("data") : null;
            if (data == null) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) data;
            return list.stream().map(m -> {
                ProductDTO dto = new ProductDTO();
                dto.setId(toLong(m.get("id")));
                dto.setName((String) m.get("name"));
                dto.setDescription((String) m.get("description"));
                dto.setPrice(m.get("price") != null
                        ? new java.math.BigDecimal(m.get("price").toString()) : null);
                dto.setStock(m.get("stock") != null ? ((Number) m.get("stock")).intValue() : null);
                dto.setImages((String) m.get("images"));
                return dto;
            }).toList();
        } catch (Exception e) {
            log.warn("RAG 搜索失败（fallback 到工具调用）: {}", e.getMessage());
            return List.of();
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value != null ? Long.valueOf(value.toString()) : null;
    }

    /**
     * 构建 messages 列表（OpenAI 格式），system prompt 放在第一条（含 RAG 商品上下文）
     */
    private List<Map<String, Object>> buildMessages(List<AiConversation> history,
                                                    List<ProductDTO> ragProducts) {
        List<AiConversation> sorted = new ArrayList<>(history);
        Collections.reverse(sorted);

        List<Map<String, Object>> messages = new ArrayList<>();

        // system prompt 作为第一条消息
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", getSystemPrompt(ragProducts));
        messages.add(systemMsg);

        // 历史对话
        for (AiConversation conv : sorted) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", conv.getRole());
            msg.put("content", conv.getContent());
            messages.add(msg);
        }

        return messages;
    }

    /**
     * 调用 DeepSeek API，自动处理工具调用循环
     */
    private String callDeepSeekWithTools(List<Map<String, Object>> messages, Long userId,
                                         List<Map<String, Object>> products,
                                         List<Map<String, Object>> toolExecutions) {
        List<Map<String, Object>> conversation = new ArrayList<>(messages);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> requestBody = buildRequestBody(conversation);
            Map<String, Object> response = sendToDeepSeek(requestBody);

            if (response == null) {
                return "抱歉，AI 服务暂时不可用，请稍后再试。";
            }

            // 解析 OpenAI 格式响应: choices[0].message
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return "抱歉，我暂时无法回复，请稍后再试。";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                return "抱歉，我暂时无法回复，请稍后再试。";
            }

            // 检查是否有 tool_calls
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

            if (toolCalls == null || toolCalls.isEmpty()) {
                // 纯文本回复
                String content = (String) message.get("content");
                return content != null ? content : "抱歉，我暂时无法回复，请稍后再试。";
            }

            // ---- 处理工具调用 ----
            // 1. 将 assistant 消息（含 tool_calls）加入对话
            conversation.add(message);

            // 2. 执行每个工具调用
            for (Map<String, Object> toolCall : toolCalls) {
                String toolCallId = (String) toolCall.get("id");

                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                String toolName = (String) function.get("name");

                Map<String, Object> input = null;
                try {
                    String argsJson = (String) function.get("arguments");
                    if (argsJson != null && !argsJson.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = objectMapper.readValue(argsJson, Map.class);
                        input = parsed;
                    }
                } catch (Exception e) {
                    log.error("解析工具参数失败: tool={}", toolName, e);
                    input = Collections.emptyMap();
                }
                if (input == null) input = Collections.emptyMap();

                log.info("执行工具: tool={}, input={}", toolName, input);
                String toolResult = executeTool(toolName, input, userId, products, toolExecutions);

                // 3. 将 tool 结果消息加入对话
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                toolMsg.put("content", toolResult);
                conversation.add(toolMsg);
            }
        }

        return "抱歉，处理您的请求时出现了问题，请稍后再试。";
    }

    /**
     * 构建 DeepSeek（OpenAI 格式）请求体
     */
    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);
        body.put("messages", messages);
        body.put("tools", getToolDefinitions());
        body.put("tool_choice", "auto");
        return body;
    }

    /**
     * 发送 HTTP 请求到 DeepSeek API
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sendToDeepSeek(Map<String, Object> requestBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            return null;
        }
    }

    /**
     * 通过 Feign 调用 mcp-server 执行工具
     */
    private String executeTool(String toolName, Map<String, Object> input, Long userId,
                               List<Map<String, Object>> products,
                               List<Map<String, Object>> toolExecutions) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("tool", toolName);
            request.put("parameters", input != null ? input : Collections.emptyMap());
            request.put("userId", userId);

            Map<String, Object> result = mcpFeignClient.executeTool(request);
            boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
            // 记录工具执行过程（供前端展示 chip）
            if (toolExecutions != null) {
                Map<String, Object> exec = new LinkedHashMap<>();
                exec.put("tool", toolName);
                exec.put("status", success ? "success" : "error");
                exec.put("action", toolActionName(toolName));
                toolExecutions.add(exec);
            }
            if (success) {
                Object data = result.get("data");
                // 工具命中商品时，收集进推荐列表（供前端渲染卡片）
                if ("search_products".equals(toolName) || "get_product".equals(toolName)) {
                    collectToolProducts(data, products);
                }
                return data != null ? objectMapper.writeValueAsString(data) : "操作成功";
            }
            return "工具执行失败: " + (result != null ? result.get("error") : "未知错误");
        } catch (Exception e) {
            log.error("工具调用异常: tool={}", toolName, e);
            return "工具调用异常: " + e.getMessage();
        }
    }

    /** 工具名 → 中文动作摘要 */
    private String toolActionName(String toolName) {
        return switch (toolName) {
            case "search_products" -> "搜索商品";
            case "get_product" -> "查看商品";
            case "get_orders" -> "查询订单";
            case "get_order_detail" -> "查看订单详情";
            case "get_cart" -> "查看购物车";
            case "add_to_cart" -> "加入购物车";
            case "create_order" -> "创建订单";
            case "pay_order" -> "支付订单";
            default -> "调用工具";
        };
    }

    /**
     * 从工具返回的数据中提取商品列表，追加到推荐列表（按 id 去重交给 dedupeProducts）
     */
    @SuppressWarnings("unchecked")
    private void collectToolProducts(Object data, List<Map<String, Object>> products) {
        try {
            if (data == null) return;
            List<Object> items = new ArrayList<>();
            if (data instanceof Map) {
                Object records = ((Map<String, Object>) data).get("records");
                if (records instanceof List) {
                    items.addAll((List<Object>) records);
                } else {
                    items.add(data); // get_product 返回单个商品 Map
                }
            } else if (data instanceof List) {
                items.addAll((List<Object>) data);
            }
            for (Object item : items) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) item;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", m.get("id"));
                p.put("name", m.get("name"));
                p.put("price", m.get("price"));
                p.put("images", m.get("images"));
                p.put("description", m.get("description"));
                if (p.get("id") != null) {
                    products.add(p);
                }
            }
        } catch (Exception e) {
            log.warn("解析工具商品数据失败: {}", e.getMessage());
        }
    }

    /**
     * 按 id 去重推荐商品列表
     */
    private List<Map<String, Object>> dedupeProducts(List<Map<String, Object>> products) {
        if (products == null || products.isEmpty()) return products;
        Map<Object, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> p : products) {
            byId.putIfAbsent(p.get("id"), p);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 将 RAG 命中的商品转为前端卡片所需的 Map
     */
    private Map<String, Object> productToMap(ProductDTO p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("price", p.getPrice());
        map.put("images", p.getImages());
        map.put("description", p.getDescription());
        return map;
    }

    private String getSystemPrompt(List<ProductDTO> ragProducts) {
        String base = """
                你是天机商城的AI智能导购助手。你可以帮助用户完成以下操作：
                - 搜索和浏览商品（search_products）
                - 查看商品详情（get_product）
                - 管理购物车（add_to_cart / get_cart）
                - 创建订单（create_order）
                - 支付订单（pay_order）
                - 查询订单状态和详情（get_orders / get_order_detail）

                核心规则（必须遵守）：
                1. 当用户提到任何商品相关需求（推荐、搜索、比较、价格、库存等），优先使用下方[RAG检索结果]中的真实商品数据
                2. 如果[RAG检索结果]不存在或其中的商品不匹配用户需求，调用 search_products 工具搜索
                3. 不要凭空猜测商品信息（价格、名称、库存），一切以实际数据为准
                4. 用户下单前，先确认收货地址和购物车内容。不要跳过确认直接下单
                5. 用热情、专业的中文回复。基于真实数据简要说明推荐理由
                6. 【商品卡片能力】当你调用 search_products 或 get_product 工具返回商品后，系统会自动在对话中为用户展示该商品的信息卡片（含商品图片、名称、价格、简介）。当用户想看商品图片、外观、或问"能不能发图片"时，回答"我可以为您展示商品卡片（含图片）"，然后立即调用 search_products 或 get_product 返回相关商品。绝不要说自己"无法发送图片"——你的能力是展示商品卡片，卡片会包含商品图片。
                """;

        if (ragProducts == null || ragProducts.isEmpty()) {
            return base;
        }

        StringBuilder sb = new StringBuilder(base);
        sb.append("\n[RAG检索结果 — 以下为真实商品数据]\n");
        for (int i = 0; i < ragProducts.size(); i++) {
            ProductDTO p = ragProducts.get(i);
            sb.append(String.format("%d. %s — ¥%s — 库存: %s — %s%n",
                    i + 1, p.getName(), p.getPrice(), p.getStock(),
                    p.getDescription() != null ? p.getDescription() : ""));
        }
        sb.append("\n请优先基于以上真实商品数据回复用户。");
        return sb.toString();
    }

    private List<Map<String, Object>> getToolDefinitions() {
        return List.of(
                createTool("search_products", "搜索商品，根据关键词查找相关商品",
                        Map.of(
                                "keyword", Map.of("type", "string", "description", "搜索关键词"),
                                "categoryId", Map.of("type", "integer", "description", "分类ID（可选）"),
                                "page", Map.of("type", "integer", "description", "页码，默认1"),
                                "size", Map.of("type", "integer", "description", "每页数量，默认10")
                        ),
                        List.of("keyword")),

                createTool("get_product", "查看商品详情",
                        Map.of("productId", Map.of("type", "integer", "description", "商品ID")),
                        List.of("productId")),

                createTool("get_orders", "查询当前用户的订单列表",
                        Map.of(),
                        List.of()),

                createTool("get_order_detail", "查看订单详情",
                        Map.of("orderId", Map.of("type", "integer", "description", "订单ID")),
                        List.of("orderId")),

                createTool("get_cart", "查看当前用户的购物车",
                        Map.of(),
                        List.of()),

                createTool("add_to_cart", "添加商品到购物车",
                        Map.of(
                                "productId", Map.of("type", "integer", "description", "商品ID"),
                                "quantity", Map.of("type", "integer", "description", "数量，默认1")
                        ),
                        List.of("productId")),

                createTool("create_order", "从购物车创建订单。需要用户已添加商品到购物车并确认收货地址。",
                        Map.of(
                                "addressId", Map.of("type", "integer", "description", "收货地址ID，先让用户确认地址"),
                                "cartItemIds", Map.of("type", "array",
                                        "items", Map.of("type", "integer"),
                                        "description", "购物车项ID列表，从get_cart获取"),
                                "couponId", Map.of("type", "integer", "description", "优惠券ID（可选）")
                        ),
                        List.of("addressId", "cartItemIds")),

                createTool("pay_order", "支付订单。用户确认下单后调用。",
                        Map.of("orderId", Map.of("type", "integer", "description", "订单ID，从create_order或get_orders获取")),
                        List.of("orderId"))
        );
    }

    /**
     * 创建 OpenAI function-calling 格式的工具定义
     */
    private Map<String, Object> createTool(String name, String description,
                                            Map<String, Object> properties,
                                            List<String> required) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", required);
        function.put("parameters", params);
        tool.put("function", function);
        return tool;
    }
}
