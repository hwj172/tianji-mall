# Milvus RAG 向量搜索 — 实现计划

> **面向 AI 代理的工作者：** 使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 逐任务实现。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** AI 导购从纯关键词搜索升级为 Spring AI Embedding + Milvus 向量搜索全链路 RAG

**架构：** 用户消息 → SiliconFlow Embedding → Milvus 向量相似度搜索 → Feign 批量获取商品 → 注入 System Prompt → DeepSeek 直接推荐

**技术栈：** Spring AI 1.0.0-M5（OpenAI Embedding）、milvus-sdk-java 2.3.4、SiliconFlow BAAI/bge-large-zh-v1.5、OpenFeign

---

### 任务 1：ai-chat-service 依赖与配置

**文件：**
- 修改：`ai-chat-service/pom.xml`
- 修改：`ai-chat-service/src/main/resources/application.yml`
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/config/EmbeddingConfig.java`

- [ ] **步骤 1：添加依赖**

在 `ai-chat-service/pom.xml` 的 `</dependencies>` 前添加：

```xml
<!-- Spring AI OpenAI Starter（Embedding API） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<!-- Milvus 原生 SDK -->
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>2.3.4</version>
</dependency>
```

- [ ] **步骤 2：验证依赖下载**

```bash
mvn compile -pl ai-chat-service
```

预期：BUILD SUCCESS，jar 下载成功。

- [ ] **步骤 3：添加 Embedding + Milvus 配置到 application.yml**

在 `application.yml` 末尾追加：

```yaml
spring:
  ai:
    openai:
      api-key: ${SILICONFLOW_API_KEY:sk-placeholder}
      base-url: https://api.siliconflow.cn/v1
      embedding:
        options:
          model: BAAI/bge-large-zh-v1.5

# Milvus 向量数据库连接
milvus:
  host: 192.168.150.11
  port: 19530
```

- [ ] **步骤 4：创建 EmbeddingConfig**

创建 `ai-chat-service/src/main/java/com/tianji/aichat/config/EmbeddingConfig.java`：

```java
package com.tianji.aichat.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;

@Configuration
public class EmbeddingConfig {

    private final OpenAiApi openAiApi;

    public EmbeddingConfig(OpenAiApi openAiApi) {
        this.openAiApi = openAiApi;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new OpenAiEmbeddingModel(openAiApi);
    }
}
```

- [ ] **步骤 5：Commit**

```bash
git add ai-chat-service/pom.xml ai-chat-service/src/main/resources/application.yml \
        ai-chat-service/src/main/java/com/tianji/aichat/config/EmbeddingConfig.java
git commit -m "feat(ai-chat-service): add Spring AI OpenAI + Milvus SDK dependencies and config"
```

---

### 任务 2：VectorSearchService（向量搜索核心）

**文件：**
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/service/VectorSearchService.java`
- 创建：`ai-chat-service/src/test/java/com/tianji/aichat/service/VectorSearchServiceTest.java`

- [ ] **步骤 1：编写 VectorSearchServiceTest（先写失败测试）**

创建 `ai-chat-service/src/test/java/com/tianji/aichat/service/VectorSearchServiceTest.java`：

```java
package com.tianji.aichat.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorSearchService 单元测试")
class VectorSearchServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MilvusServiceClient milvusClient;

    @InjectMocks
    private VectorSearchService vectorSearchService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(vectorSearchService, "collectionName", "product_vectors");
    }

    @Test
    @DisplayName("向量相似搜索 — 正常返回商品 ID 列表")
    void shouldReturnProductIdsOnSuccess() {
        // Mock embedding 返回
        EmbeddingResponse embResp = mock(EmbeddingResponse.class);
        when(embResp.getResult()).thenReturn(
                new org.springframework.ai.embedding.Embedding(new float[1024], 0));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(embResp);

        // Mock Milvus 搜索返回
        SearchResultsWrapper wrapper = mock(SearchResultsWrapper.class);
        when(wrapper.getLongID(0)).thenReturn(1L);
        when(wrapper.getLongID(1)).thenReturn(2L);

        SearchResultData data = mock(SearchResultData.class);

        SearchResults results = mock(SearchResults.class);
        when(results.getResults()).thenReturn(data);

        R<SearchResults> r = new R<>();
        r.setData(results);

        when(milvusClient.search(any(SearchParam.class))).thenReturn(r);

        List<Long> ids = vectorSearchService.searchSimilar("拍照好的手机", 5);

        assertThat(ids).containsExactly(1L, 2L);
        verify(embeddingModel).call(any(EmbeddingRequest.class));
        verify(milvusClient).search(any(SearchParam.class));
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

```bash
mvn test -pl ai-chat-service -Dtest=VectorSearchServiceTest
```

预期：编译失败（VectorSearchService 不存在）。

- [ ] **步骤 3：实现 VectorSearchService**

创建 `ai-chat-service/src/main/java/com/tianji/aichat/service/VectorSearchService.java`：

```java
package com.tianji.aichat.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResultData;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VectorSearchService {

    private static final int DIMENSION = 1024;
    private static final String ID_FIELD = "product_id";
    private static final String VECTOR_FIELD = "vector";

    private final EmbeddingModel embeddingModel;
    private final MilvusServiceClient milvusClient;

    @Value("${milvus.collection-name:product_vectors}")
    private String collectionName;

    public VectorSearchService(EmbeddingModel embeddingModel,
                               @Value("${milvus.host}") String host,
                               @Value("${milvus.port}") int port) {
        this.embeddingModel = embeddingModel;
        this.milvusClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(host)
                        .withPort(port)
                        .build());
        initCollection();
    }

    private void initCollection() {
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collectionName).build());
        if (hasCollection.getData()) {
            return;
        }
        log.info("创建 Milvus collection: {}", collectionName);
        FieldType idField = FieldType.newBuilder()
                .withName(ID_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .build();
        FieldType vectorField = FieldType.newBuilder()
                .withName(VECTOR_FIELD)
                .withDataType(DataType.FloatVector)
                .withDimension(DIMENSION)
                .build();
        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .build();
        milvusClient.createCollection(param);
        log.info("Milvus collection 创建完成");
    }

    /**
     * 向量相似度搜索，返回匹配的商品 ID 列表
     */
    public List<Long> searchSimilar(String queryText, int topK) {
        try {
            // 1. 文本 → Embedding
            float[] embedding = embed(queryText);

            // 2. Milvus 搜索
            List<String> outFields = Collections.singletonList(ID_FIELD);
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withVectorFieldName(VECTOR_FIELD)
                    .withFloatVectors(Collections.singletonList(new java.util.ArrayList<>() {{
                        for (float v : embedding) add(v);
                    }}))
                    .withOutFields(outFields)
                    .withTopK(topK)
                    .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                    .withMetricType(MetricType.COSINE)
                    .build();

            R<io.milvus.grpc.SearchResults> result = milvusClient.search(searchParam);
            if (result.getStatus() != 0 || result.getData() == null) {
                log.warn("Milvus 搜索失败: status={}", result.getStatus());
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < wrapper.getRowRecords().size(); i++) {
                ids.add(wrapper.getLongID(i));
            }
            return ids;
        } catch (Exception e) {
            log.error("向量搜索异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 同步商品向量到 Milvus
     */
    public void upsertProduct(Long productId, String name, String description) {
        try {
            String text = (name != null ? name : "") + " " + (description != null ? description : "");
            float[] embedding = embed(text);

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field(ID_FIELD,
                    Collections.singletonList(String.valueOf(productId))));
            fields.add(new InsertParam.Field(VECTOR_FIELD,
                    Collections.singletonList(toFloatList(embedding))));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<io.milvus.grpc.MutationResult> result = milvusClient.insert(insertParam);
            if (result.getStatus() != 0) {
                log.warn("Milvus upsert 失败: productId={}, status={}", productId, result.getStatus());
            } else {
                log.debug("向量同步成功: productId={}", productId);
            }
        } catch (Exception e) {
            log.error("向量同步异常: productId={}", productId, e);
        }
    }

    private float[] embed(String text) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
        return embeddingModel.call(request).getResult().getOutput();
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) list.add(v);
        return list;
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

```bash
mvn test -pl ai-chat-service -Dtest=VectorSearchServiceTest
```

预期：1 test passed, BUILD SUCCESS。

- [ ] **步骤 5：Commit**

```bash
git add ai-chat-service/src/main/java/com/tianji/aichat/service/VectorSearchService.java \
        ai-chat-service/src/test/java/com/tianji/aichat/service/VectorSearchServiceTest.java
git commit -m "feat(ai-chat-service): add VectorSearchService with embedding + Milvus search"
```

---

### 任务 3：VectorController（向量同步端点）

**文件：**
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/controller/VectorController.java`
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/dto/VectorUpsertRequest.java`

- [ ] **步骤 1：创建 VectorUpsertRequest DTO**

```java
package com.tianji.aichat.dto;

import lombok.Data;

@Data
public class VectorUpsertRequest {
    private Long productId;
    private String name;
    private String description;
}
```

- [ ] **步骤 2：创建 VectorController**

```java
package com.tianji.aichat.controller;

import com.tianji.aichat.dto.VectorUpsertRequest;
import com.tianji.aichat.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
public class VectorController {

    private final VectorSearchService vectorSearchService;

    @PostMapping("/upsert")
    public void upsert(@RequestBody VectorUpsertRequest request) {
        vectorSearchService.upsertProduct(
                request.getProductId(), request.getName(), request.getDescription());
    }
}
```

- [ ] **步骤 3：编译验证**

```bash
mvn compile -pl ai-chat-service
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add ai-chat-service/src/main/java/com/tianji/aichat/controller/VectorController.java \
        ai-chat-service/src/main/java/com/tianji/aichat/dto/VectorUpsertRequest.java
git commit -m "feat(ai-chat-service): add VectorController for product vector sync"
```

---

### 任务 4：mall-goods-order 批量查询端点

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java`

- [ ] **步骤 1：ProductService 加批量查询方法**

在 `ProductService.java` 末尾添加：

```java
/**
 * 根据 ID 列表批量查询在售商品（供 AI RAG 管道使用，内部端点调用）
 */
public List<Product> getProductBatch(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
        return List.of();
    }
    return list(new LambdaQueryWrapper<Product>()
            .in(Product::getId, ids)
            .eq(Product::getStatus, 1));
}
```

- [ ] **步骤 2：ProductController 加批量查询端点**

在 `ProductController.java` 末尾添加：

```java
@PostMapping("/batch")
public R<List<Product>> batch(@RequestBody List<Long> ids) {
    return R.ok(productService.getProductBatch(ids));
}
```

- [ ] **步骤 3：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java \
        mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java
git commit -m "feat(mall-goods-order): add POST /api/product/batch for RAG pipeline"
```

---

### 任务 5：mall-goods-order 向量同步（Feign）

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/feign/AiChatFeignClient.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java`

- [ ] **步骤 1：创建 AiChatFeignClient**

```java
package com.tianji.mall.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-chat-service")
public interface AiChatFeignClient {

    @PostMapping("/api/vector/upsert")
    void upsertProductVector(@RequestBody Map<String, Object> body);
}
```

- [ ] **步骤 2：改造 ProductService 添加向量同步**

在 `ProductService.java` 中注入 `AiChatFeignClient` 并添加同步方法：

```java
// 字段注入
private final AiChatFeignClient aiChatFeignClient;

// 在 saveProduct/createProduct 等方法中添加（找到 ProductService 中写操作的地方）：
// 如果 ProductService 没有 save/update 方法，在 Controller 层无法感知创建/更新时机，
// 则改为在 Controller 的创建/更新端点中调用同步方法。

// 在 ProductService 中添加：
/**
 * 异步同步商品向量到 Milvus（不阻塞主流程）
 */
public void syncVector(Long productId, String name, String description) {
    try {
        Map<String, Object> body = Map.of(
                "productId", productId,
                "name", name != null ? name : "",
                "description", description != null ? description : ""
        );
        aiChatFeignClient.upsertProductVector(body);
    } catch (Exception e) {
        log.warn("商品向量同步失败（不影响主流程）: productId={}", productId, e);
    }
}
```

注：查看 ProductService 中是否存在创建/更新商品的 Service 方法。如果创建/更新直接在 Controller 中调用 MyBatis-Plus 的 `save()`/`updateById()`，则向量同步调用加在 Controller 层。如果 ProductService 有封装方法，则加在 Service 层。

- [ ] **步骤 3：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：运行已有测试确认不破坏**

```bash
mvn test -pl mall-goods-order
```

预期：所有已有测试 PASS。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/feign/AiChatFeignClient.java \
        mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java
git commit -m "feat(mall-goods-order): add vector sync via Feign to ai-chat-service"
```

---

### 任务 6：MallFeignClient 新增批量查询接口

**文件：**
- 修改：`mcp-server/src/main/java/com/tianji/mcp/feign/MallFeignClient.java`

注：如果 RAG 管道直接走 Feign 调用 mall-goods-order 而非通过 mcp-server，则需要另外的 FeignClient。这里 RAG 管道在 ai-chat-service 中，需要直接调用 mall-goods-order 的 `/api/product/batch`。

- [ ] **步骤 1：创建 ai-chat-service 的 ProductFeignClient**

创建 `ai-chat-service/src/main/java/com/tianji/aichat/feign/ProductFeignClient.java`：

```java
package com.tianji.aichat.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mall-goods-order")
public interface ProductFeignClient {

    @PostMapping("/api/product/batch")
    Map<String, Object> getProductBatch(@RequestBody List<Long> ids);
}
```

- [ ] **步骤 2：编译验证**

```bash
mvn compile -pl ai-chat-service
```

预期：BUILD SUCCESS。

- [ ] **步骤 3：Commit**

```bash
git add ai-chat-service/src/main/java/com/tianji/aichat/feign/ProductFeignClient.java
git commit -m "feat(ai-chat-service): add ProductFeignClient for batch product query"
```

---

### 任务 7：AiChatService RAG 管道改造（核心）

**文件：**
- 创建：`ai-chat-service/src/main/java/com/tianji/aichat/dto/ProductDTO.java`
- 修改：`ai-chat-service/src/main/java/com/tianji/aichat/service/AiChatService.java`

- [ ] **步骤 1：创建 ProductDTO**

```java
package com.tianji.aichat.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String images;
    private Long categoryId;
    private Integer status;
}
```

- [ ] **步骤 2：改造 AiChatService — 注入依赖**

在 `AiChatService` 类中添加注入：

```java
private final VectorSearchService vectorSearchService;
private final ProductFeignClient productFeignClient;

// 修改构造函数（已有 RestTemplate + McpFeignClient，追加两个参数）
public AiChatService(RestTemplate restTemplate, McpFeignClient mcpFeignClient,
                     VectorSearchService vectorSearchService, ProductFeignClient productFeignClient) {
    this.restTemplate = restTemplate;
    this.mcpFeignClient = mcpFeignClient;
    this.vectorSearchService = vectorSearchService;
    this.productFeignClient = productFeignClient;
}
```

- [ ] **步骤 3：改造 AiChatService — chat() 方法增加 RAG 预检索**

修改 `chat()` 方法，在"构建 messages"之前插入 RAG 检索：

```java
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

    // 3. RAG 预检索（新增）
    List<ProductDTO> ragProducts = searchProductsByRag(message);

    // 4. 构建 messages（含 RAG 上下文）
    List<Map<String, Object>> messages = buildMessages(history, ragProducts);

    // 5. 调用 DeepSeek（含工具调用 fallback）
    String reply = callDeepSeekWithTools(messages, userId);

    // 6. 保存 assistant 回复
    AiConversation assistantMsg = new AiConversation();
    assistantMsg.setUserId(userId);
    assistantMsg.setSessionId(sessionId);
    assistantMsg.setRole("assistant");
    assistantMsg.setContent(reply);
    save(assistantMsg);

    return new ChatResponse(sessionId, reply, null);
}
```

- [ ] **步骤 4：添加 RAG 搜索私有方法**

在 `AiChatService` 中添加：

```java
/**
 * RAG 向量搜索商品，失败返回空列表不回抛异常
 */
private List<ProductDTO> searchProductsByRag(String message) {
    try {
        List<Long> ids = vectorSearchService.searchSimilar(message, 5);
        if (ids.isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = productFeignClient.getProductBatch(ids);
        Object data = result.get("data");
        if (data == null) {
            return List.of();
        }
        // Feign 返回的是 LinkedHashMap 列表，需手动转换
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
        log.warn("RAG 搜索失败（将 fallback 到工具调用）: {}", e.getMessage());
        return List.of();
    }
}

private Long toLong(Object value) {
    if (value instanceof Integer) return ((Integer) value).longValue();
    if (value instanceof Long) return (Long) value;
    return Long.valueOf(value.toString());
}
```

- [ ] **步骤 5：改造 buildMessages — 注入 RAG 商品上下文**

修改 `buildMessages` 方法签名和实现：

```java
private List<Map<String, Object>> buildMessages(List<AiConversation> history,
                                                 List<ProductDTO> ragProducts) {
    List<AiConversation> sorted = new ArrayList<>(history);
    Collections.reverse(sorted);

    List<Map<String, Object>> messages = new ArrayList<>();

    // system prompt 作为第一条消息（含 RAG 商品上下文）
    Map<String, Object> systemMsg = new LinkedHashMap<>();
    systemMsg.put("role", "system");
    systemMsg.put("content", getSystemPrompt(ragProducts));
    messages.add(systemMsg);

    for (AiConversation conv : sorted) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", conv.getRole());
        msg.put("content", conv.getContent());
        messages.add(msg);
    }

    return messages;
}
```

- [ ] **步骤 6：改造 getSystemPrompt — 注入 RAG 商品上下文**

修改 `getSystemPrompt()` 为 `getSystemPrompt(List<ProductDTO> ragProducts)`：

```java
private String getSystemPrompt(List<ProductDTO> ragProducts) {
    String base = """
            你是天机商城的AI智能导购助手。你可以帮助用户完成以下操作：
            - 搜索和浏览商品（search_products）
            - 查看商品详情（get_product）
            - 管理购物车（add_to_cart / get_cart）
            - 查询订单状态和详情（get_orders / get_order_detail）

            核心规则（必须遵守）：
            1. 当用户提到任何商品相关需求（推荐、搜索、比较、价格、库存等），优先使用下方[RAG检索结果]中的真实商品数据
            2. 如果[RAG检索结果]中的商品不匹配用户需求，再调用 search_products 工具
            3. 不要凭空猜测商品信息（价格、名称、库存），一切以实际数据为准
            4. 用热情、专业的中文回复。基于真实数据简要说明推荐理由
            """;

    if (ragProducts != null && !ragProducts.isEmpty()) {
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n[RAG检索结果 — 以下为真实商品数据]\n");
        for (int i = 0; i < ragProducts.size(); i++) {
            ProductDTO p = ragProducts.get(i);
            sb.append(String.format("%d. %s — ¥%s — 库存: %d — %s\n",
                    i + 1, p.getName(), p.getPrice(), p.getStock(),
                    p.getDescription() != null ? p.getDescription() : ""));
        }
        sb.append("\n请基于以上真实商品数据回复用户。");
        return sb.toString();
    }

    return base + "\n\n（当前未检索到相关商品，请使用 search_products 工具搜索。）";
}
```

- [ ] **步骤 7：更新 buildMessages 调用点**

确保 `callDeepSeekWithTools` 调用 `buildMessages` 的地方传递空 RAG 列表（仅系统内部构建时）：

在 `callDeepSeekWithTools` 中如果调用了 `buildMessages`，改为 `buildMessages(history, List.of())`。如果 `callDeepSeekWithTools` 直接使用传入的 messages 列表（不是调用 buildMessages），则无需改动。

- [ ] **步骤 8：编译验证**

```bash
mvn compile -pl ai-chat-service
```

预期：BUILD SUCCESS。

- [ ] **步骤 9：Commit**

```bash
git add ai-chat-service/src/main/java/com/tianji/aichat/dto/ProductDTO.java \
        ai-chat-service/src/main/java/com/tianji/aichat/service/AiChatService.java
git commit -m "feat(ai-chat-service): inject RAG pipeline into chat flow"
```

---

### 任务 8：测试与全量验证

**文件：**
- 修改：`ai-chat-service/src/test/java/com/tianji/aichat/service/AiChatServiceTest.java`
- 修改：`mall-goods-order/src/test/resources/application-test.yml`

- [ ] **步骤 1：更新 AiChatServiceTest 适配新构造函数**

`AiChatService` 构造函数从 2 参数变为 4 参数，需要在测试中 Mock 新依赖：

```java
// 在测试类中添加
@Mock
private VectorSearchService vectorSearchService;

@Mock
private ProductFeignClient productFeignClient;

// 创建 AiChatService 时传入 4 个参数
AiChatService aiChatService = new AiChatService(
        restTemplate, mcpFeignClient, vectorSearchService, productFeignClient);
```

同时在 `@BeforeEach` 或相关测试方法中设置 `lenient()` stub：

```java
lenient().when(vectorSearchService.searchSimilar(anyString(), anyInt())).thenReturn(List.of());
```

- [ ] **步骤 2：mall-goods-order 测试配置排除 AiChatFeignClient**

在 `application-test.yml` 的 `spring.autoconfigure.exclude` 或测试类中添加 `@MockBean`：

由于 `AiChatFeignClient` 是新增的 Feign 接口，在测试中会自动创建一个 Feign 代理…实际上 Feign 在测试中被排除了（`spring.cloud.openfeign.feign` 排除），如果没有排除则会报错。确认 `mall-goods-order/src/test/resources/application-test.yml` 中已排除 `FeignAutoConfiguration`。如果没有，需要添加或使用 `@MockBean AiChatFeignClient`。

- [ ] **步骤 3：运行全量测试**

```bash
mvn test
```

预期：全部 157+ tests PASS，BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add ai-chat-service/src/test/java/com/tianji/aichat/service/AiChatServiceTest.java \
        mall-goods-order/src/test/resources/application-test.yml
git commit -m "test: update tests for RAG pipeline changes"
```
