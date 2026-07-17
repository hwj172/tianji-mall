# Milvus RAG 向量搜索 — 设计文档

- **日期**: 2026-07-17
- **目标**: AI 导购从纯关键词搜索升级为 Spring AI + Milvus 全链路 RAG 语义搜索

## 一、现状

| 组件 | 状态 |
|------|------|
| Milvus v2.3.4 (Docker) | ✅ 运行中，19530 端口 |
| Spring AI 1.0.0-M5 (父 POM) | ✅ BOM 已声明，零模块使用 |
| 商品搜索 | ❌ MySQL `LIKE` 关键词匹配 |
| AI 导购 | DeepSeek tool calling → mcp-server → Feign → 关键词搜索 |

## 二、目标架构

```
用户: "推荐拍照好的手机"
    ↓
ai-chat-service RAG 管道:
  1. 用户消息 → Embedding (SiliconFlow BAAI/bge-large-zh-v1.5)
  2. 向量 → Milvus COSINE 相似度 → Top-5 商品 ID
  3. 商品 ID → Feign POST /api/product/batch → 完整商品数据
  4. 商品 JSON + 用户消息 → System Prompt
  5. DeepSeek → 直接回复（含商品推荐）
```

## 三、技术选型

| 层面 | 选择 | 理由 |
|------|------|------|
| Embedding 模型 | BAAI/bge-large-zh-v1.5 | 中文语义理解 SOTA，SiliconFlow 免费额度可用 |
| Embedding API | SiliconFlow（OpenAI 兼容） | 注册送 ¥14，开发测试基本免费 |
| 向量存储 | Milvus standalone | 已在 Docker 运行，Spring AI 有官方集成 |
| 向量维度 | 1024 | bge-large-zh v1.5 输出维度 |
| 相似度度量 | COSINE | 语义搜索标准选择 |
| 索引类型 | IVF_FLAT | 适合中小规模（<100K），准确度与速度平衡 |
| RAG 策略 | 预检索（用户消息 → embedding → 搜索 → 注入 prompt） | 减少 DeepSeek tool call 往返，语义匹配更直接 |

## 四、模块变更

### 4.1 ai-chat-service

**新增依赖：**
- `spring-ai-openai-spring-boot-starter`（Embedding Client）
- `milvus-sdk-java`（Spring AI Milvus VectorStore 底层 SDK）

**新增配置：**
```yaml
spring:
  ai:
    openai:
      api-key: ${SILICONFLOW_API_KEY:sk-placeholder}
      base-url: https://api.siliconflow.cn/v1
      embedding:
        options:
          model: BAAI/bge-large-zh-v1.5
    vectorstore:
      milvus:
        host: 192.168.150.11
        port: 19530
        collection-name: product_vectors
        dimension: 1024
        index-type: IVF_FLAT
        metric-type: COSINE
```

**新建文件：**

| 文件 | 职责 |
|------|------|
| `config/EmbeddingConfig.java` | 配置 SiliconFlow EmbeddingClient bean |
| `config/MilvusConfig.java` | 配置 MilvusServiceClient + VectorStore bean |
| `service/VectorSearchService.java` | `searchSimilar(query, topK)` + `upsertProduct(id, name, description)` |
| `controller/VectorController.java` | `POST /api/vector/upsert` — 供 mall-goods-order 调用 |
| `dto/ProductDTO.java` | 商品精简 DTO（id, name, description, price, images） |

**改造文件：**

| 文件 | 变更 |
|------|------|
| `service/AiChatService.java` | chat() 中增加 RAG 预检索阶段：embedding → Milvus → 商品数据 → system prompt 注入；工具调用保留作 fallback |
| `pom.xml` | 加 spring-ai-openai + milvus-sdk-java |

### 4.2 mall-goods-order

**新建端点：**

| 端点 | 说明 |
|------|------|
| `POST /api/product/batch` | Body: `[1, 2, 3]` → 返回 `List<Product>`，供 RAG 管道按 ID 批量获取商品 |

**新建 Feign 客户端：**
- `feign/AiChatFeignClient.java` — 调用 `POST /api/vector/upsert`，异步同步商品向量

**改造文件：**

| 文件 | 变更 |
|------|------|
| `service/ProductService.java` | 创建/更新商品后调用 AiChatFeignClient.upsert（异常 catch 不抛，log.warn） |

### 4.3 不涉及模块

gateway、user-service、pay-service、mcp-server 零改动。

## 五、Milvus Collection Schema

```
Collection: product_vectors

Field         Type          Description
─────────     ──────────    ─────────────────
product_id    VarChar(64)   primary key（商品 ID 字符串）
name          VarChar(512)  商品名称（调试用）
vector        FloatVector   bge-large-zh 向量（1024 维）

Index:
  Type: IVF_FLAT
  Metric: COSINE
  nlist: 128
```

**Embedding 文本**：`product.name + " " + product.description`

**初始加载**：启动时或手动触发，遍历现有商品（status=1）→ embedding → insert

## 六、RAG 管道流程

```java
// AiChatService.chat() 中新增 RAG 阶段
public ChatResponse chat(Long userId, String sessionId, String message) {
    // 1. 保存用户消息（不变）

    // 2. RAG 预检索（新增）
    List<ProductDTO> ragProducts = vectorSearchService.searchSimilar(message, 5);
    String ragContext = ragProducts.isEmpty() ? "" : formatRagContext(ragProducts);

    // 3. 构建 messages
    List<Map<String, Object>> messages = buildMessages(history, ragContext);

    // 4. DeepSeek（含工具调用）
    String reply = callDeepSeekWithTools(messages, userId);

    // 5. 保存回复（不变）
    return ...
}
```

**System Prompt 注入格式：**
```
[检索到以下相关商品（基于实际数据）]
1. 华为Mate 60 Pro — ¥6999 — 1200万像素徕卡摄像头...
2. 小米14 Ultra — ¥5999 — 5000万像素主摄...
...

请基于以上真实商品数据回复用户。如果检索结果与用户需求不匹配，可使用工具自行搜索。
```

## 七、商品向量同步

```
商品创建/更新 (mall-goods-order)
  → try { aiChatFeignClient.upsert(product) }
  → catch { log.warn("向量同步失败，不影响商品操作"); }
```

- 异步风格（try-catch 不阻塞主流程）
- 商品删除 → 软删除（status=0），向量不同步删除（搜索时过滤 status=0）

## 八、错误处理与 Fallback

| 场景 | 行为 |
|------|------|
| SiliconFlow 不可用 | log.error → RAG 跳过 → fallback 工具调用模式 |
| Milvus 不可用 | log.error → RAG 跳过 → fallback 工具调用模式 |
| 商品批量查询失败 | log.warn → 无商品上下文 → DeepSeek 自行使用工具 |
| 向量同步失败 | log.warn → 不影响商品操作主流程 |
| RAG 搜到已下架商品 | mall-goods-order /api/product/batch 仅返回 status=1 → 自然过滤 |

## 九、测试策略

| 层级 | 说明 |
|------|------|
| **VectorSearchServiceTest** | 纯 Unit，Mock EmbeddingClient + MilvusClient，验证搜索/upsert 逻辑 |
| **AiChatService 集成测试** | @SpringBootTest + @MockBean VectorSearchService，验证 RAG 上下文注入 + fallback |
| **ProductService 向量同步测试** | Mock AiChatFeignClient，验证异常不阻塞主流程 |

## 十、验证

```bash
# 编译
mvn compile -pl ai-chat-service,mall-goods-order

# 全量测试
mvn test

# 手动验证（启动后）
curl -X POST http://localhost:8085/api/chat \
  -H "Authorization: Bearer <token>" \
  -d '{"message": "推荐拍照好的手机"}'
# 预期：DeepSeek 基于 RAG 检索到的商品直接推荐
```

## 十一、风险与后续

- **Milvus 数据一致性**：向量同步是异步 best-effort，不保证实时一致。后续可加定时任务全量重建
- **Embedding 成本**：每条商品创建触发 1 次 embedding 调用，bge-large-zh 约 ¥0.0007/千 tokens，单条商品文本 <100 tokens → 几乎免费
- **搜索延迟**：embedding + Milvus 搜索 <200ms（局域网），比当前 Feign 链路更快
