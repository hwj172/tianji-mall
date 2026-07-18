# 存量商品向量回填 + RAG 端到端验证 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新增内部回填端点 `POST /api/product/internal/sync-vectors`，把全部上架商品向量一次性同步到 Milvus，并通过网关 X-Internal-Token 鉴权保护；随后人工全链路验证 RAG 生效。

**架构：** mall-goods-order 侧 `ProductService.syncVector` 改为返回 `boolean`，新增 `syncAllVectors()` 遍历 `status=1` 商品逐个同步并统计 `{total, success, failed}`；`ProductController` 暴露内部端点。gateway 侧 `AuthGlobalFilter` 把内部路径检查移到公开路径检查**之前**（否则 `/api/product` 公开前缀会放行新端点），并把 `/api/product/internal` 加入 INTERNAL_PATHS。

**技术栈：** Spring Boot 3.2.5、MyBatis-Plus 3.5.7、OpenFeign、Spring Cloud Gateway (WebFlux)、JUnit 5 + Mockito + MockMvc。

**规格文档：** `docs/superpowers/specs/2026-07-17-vector-backfill-design.md`

**Git 注意（用户规则，优先于本计划默认行为）：** 每次 commit 前先向用户展示变更摘要并获得确认，不得自动 commit。

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java` | 修改 | `syncVector` void→boolean；新增 `syncAllVectors()` |
| `mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java` | 修改 | 新增 `POST /internal/sync-vectors` |
| `gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java` | 修改 | INTERNAL_PATHS 增加 `/api/product/internal`；内部检查提前 |
| `mall-goods-order/src/test/java/com/tianji/mall/service/ProductServiceTest.java` | 修改 | +3 测试（全部成功 / 部分失败 / 只查上架） |
| `mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java` | 修改 | +1 测试（内部端点路由 + 统计返回） |
| `gateway/src/test/java/com/tianji/gateway/filter/AuthGlobalFilterTest.java` | 修改 | +3 测试（无 token 401 / 有 token 放行 / list 仍公开） |
| `CLAUDE.md` | 修改 | 测试数 171→178 及相关表格、内部端点约定 |

---

### 任务 1：ProductService — syncVector 返回值 + syncAllVectors()

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java`
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/service/ProductServiceTest.java`

**背景（工作者须知）：** `ProductService extends ServiceImpl<ProductMapper, Product>`，测试中通过 `ReflectionTestUtils.setField(productService, "baseMapper", productMapper)` 注入 Mock Mapper（见现有 `setUp()`）。`ServiceImpl.list(wrapper)` 委托 `baseMapper.selectList(wrapper)`。`AiChatFeignClient.upsertProductVector(Map<String, Object>)` 返回 `void`，所以 Mock 抛异常要用 `doThrow(...).when(...)` 而非 `when(...).thenThrow(...)`。

- [ ] **步骤 1：编写 3 个失败的测试**

在 `ProductServiceTest.java` 末尾（`buildProduct` helper 之前）添加，并补充 import：

```java
// 新增 import（加到文件头部现有 import 区）
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
```

```java
// ==================== syncAllVectors ====================

@Test
void shouldSyncAllVectorsWithAllSuccess() {
    Product p1 = buildProduct(1L, "iPhone 16", BigDecimal.valueOf(9999), 50, 1);
    Product p2 = buildProduct(2L, "小米 15", BigDecimal.valueOf(4999), 100, 1);
    when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));

    Map<String, Integer> result = productService.syncAllVectors();

    assertThat(result)
            .containsEntry("total", 2)
            .containsEntry("success", 2)
            .containsEntry("failed", 0);
    verify(aiChatFeignClient, times(2)).upsertProductVector(anyMap());
}

@Test
void shouldCountFailedWhenSyncThrows() {
    Product p1 = buildProduct(1L, "iPhone 16", BigDecimal.valueOf(9999), 50, 1);
    Product p2 = buildProduct(2L, "小米 15", BigDecimal.valueOf(4999), 100, 1);
    when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));
    // 第一个商品同步抛异常，第二个成功
    doThrow(new RuntimeException("ai-chat-service down"))
            .doNothing()
            .when(aiChatFeignClient).upsertProductVector(anyMap());

    Map<String, Integer> result = productService.syncAllVectors();

    assertThat(result)
            .containsEntry("total", 2)
            .containsEntry("success", 1)
            .containsEntry("failed", 1);
}

@Test
@SuppressWarnings("unchecked")
void shouldQueryOnlyOnShelfProducts() {
    when(productMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

    Map<String, Integer> result = productService.syncAllVectors();

    assertThat(result).containsEntry("total", 0);
    // 查询条件必须包含 status = 1（paramNameValuePairs 在 eq() 调用时即填充，无需 TableInfo 初始化）
    ArgumentCaptor<LambdaQueryWrapper<Product>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(productMapper).selectList(captor.capture());
    assertThat(captor.getValue().getParamNameValuePairs()).containsValue(1);
}
```

> 若步骤 4 中出现 stub 未命中（返回 null 导致 NPE）：MyBatis-Plus 部分版本的 `list(wrapper)` 走 `selectList(IPage, Wrapper)` 两参重载，此时把 stub 改为 `when(productMapper.selectList(isNull(), any(LambdaQueryWrapper.class)))`（`isNull()` 来自 `org.mockito.ArgumentMatchers`），captor 校验同理改为两参 `verify`。

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -pl mall-goods-order -Dtest=ProductServiceTest`
预期：编译失败，报错 `cannot find symbol: method syncAllVectors()`（编译失败即本步骤的"失败"证据）

- [ ] **步骤 3：实现最少代码**

`ProductService.java` 中，把现有 `syncVector` 方法整体替换为：

```java
    /**
     * 同步商品向量到 Milvus（best-effort，失败不影响主流程）
     *
     * @return 是否同步成功
     */
    public boolean syncVector(Long productId, String name, String description) {
        try {
            Map<String, Object> body = Map.of(
                    "productId", productId,
                    "name", name != null ? name : "",
                    "description", description != null ? description : ""
            );
            aiChatFeignClient.upsertProductVector(body);
            return true;
        } catch (Exception e) {
            log.warn("商品向量同步失败（不影响主流程）: productId={}", productId, e);
            return false;
        }
    }

    /**
     * 全量回填：把所有上架商品向量同步到 Milvus（Milvus upsert 幂等，可重复触发）
     */
    public Map<String, Integer> syncAllVectors() {
        List<Product> products = list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        int success = 0;
        for (Product p : products) {
            if (syncVector(p.getId(), p.getName(), p.getDescription())) {
                success++;
            }
        }
        log.info("商品向量回填完成: total={}, success={}", products.size(), success);
        return Map.of("total", products.size(),
                      "success", success,
                      "failed", products.size() - success);
    }
```

（`List`、`Map`、`LambdaQueryWrapper` 均已在该文件 import，无需新增。`syncVector` 现无任何调用方，改签名无连锁影响。）

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -pl mall-goods-order -Dtest=ProductServiceTest`
预期：`Tests run: 8, Failures: 0, Errors: 0`（原 5 + 新 3）

- [ ] **步骤 5：展示变更摘要，经用户确认后 commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java \
        mall-goods-order/src/test/java/com/tianji/mall/service/ProductServiceTest.java
git commit -m "feat(mall): add syncAllVectors for product vector backfill"
```

---

### 任务 2：ProductController — 内部回填端点

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java`
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java`

**背景（工作者须知）：** Controller 测试用 `@SpringBootTest` + `@AutoConfigureMockMvc` + `@MockBean ProductService`（`@WebMvcTest` 在本项目不可用）。该测试类已有 `@MockBean` RedissonClient / RocketMQTemplate / AiChatFeignClient，不要动。服务层不做鉴权——X-Internal-Token 只在网关校验（与 OrderController/CartController 内部端点模式一致）。

- [ ] **步骤 1：编写失败的测试**

在 `ProductControllerTest.java` 的 helpers 区之前添加，并补充 import：

```java
// 新增 import
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

```java
    // ==================== POST /api/product/internal/sync-vectors ====================

    @Test
    void shouldTriggerVectorBackfill() throws Exception {
        when(productService.syncAllVectors())
                .thenReturn(Map.of("total", 12, "success", 12, "failed", 0));

        mockMvc.perform(post("/api/product/internal/sync-vectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.success").value(12))
                .andExpect(jsonPath("$.data.failed").value(0));
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -pl mall-goods-order -Dtest=ProductControllerTest`
预期：`shouldTriggerVectorBackfill` FAIL — 404（路由不存在）

- [ ] **步骤 3：实现端点**

`ProductController.java` 中 `batch` 方法之后添加（文件头部补 `import java.util.Map;`）：

```java
    // ===== 内部端点（网关 X-Internal-Token 鉴权，不暴露给前端）=====

    @PostMapping("/internal/sync-vectors")
    public R<Map<String, Integer>> syncVectors() {
        return R.ok(productService.syncAllVectors());
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -pl mall-goods-order -Dtest=ProductControllerTest`
预期：`Tests run: 5, Failures: 0, Errors: 0`

- [ ] **步骤 5：运行模块全量测试（防回归）**

运行：`mvn test -pl mall-goods-order`
预期：全部通过，0 Failures

- [ ] **步骤 6：展示变更摘要，经用户确认后 commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java \
        mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java
git commit -m "feat(mall): add internal endpoint POST /api/product/internal/sync-vectors"
```

---

### 任务 3：AuthGlobalFilter — 内部路径检查提前 + 新增 /api/product/internal

**文件：**
- 修改：`gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java`
- 测试：`gateway/src/test/java/com/tianji/gateway/filter/AuthGlobalFilterTest.java`

**背景（工作者须知）：** 这是本次变更的安全关键点——`PUBLIC_PATHS` 含 `/api/product`（前缀匹配）且当前**先于**内部路径检查执行，若只加 INTERNAL_PATHS 不调顺序，回填端点会被当成公开路径放行。顺序调整对现有行为无影响：`/api/order/internal`、`/api/cart/internal` 不在任何公开前缀之下。测试类用纯 Mockito（`createExchange(path)` / `createExchange(path, headers)` helper、字段级 `response` mock、`INTERNAL_TOKEN` 常量已存在），`filter.filter(...)` 同步执行后直接 `verify`，无需订阅 Mono。

- [ ] **步骤 1：编写 3 个失败的测试**

在 `AuthGlobalFilterTest.java` 中添加一个新的 `@Nested` 类（放在现有 Nested 类之后、文件末尾大括号之前）：

```java
    // ==================== 商品内部路径（向量回填） ====================

    @Nested
    @DisplayName("/api/product/internal — 内部 token 鉴权（先于公开前缀匹配）")
    class ProductInternalPaths {

        @Test
        @DisplayName("/api/product/internal/sync-vectors 无 token 返回 401")
        void shouldRejectProductInternalWithoutToken() {
            ServerWebExchange exchange = createExchange("/api/product/internal/sync-vectors");

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("/api/product/internal/sync-vectors 携带正确 token 放行")
        void shouldPassProductInternalWithValidToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Internal-Token", INTERNAL_TOKEN);
            ServerWebExchange exchange = createExchange("/api/product/internal/sync-vectors", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/product/list 仍是公开路径（顺序调整回归保护）")
        void shouldKeepProductListPublic() {
            ServerWebExchange exchange = createExchange("/api/product/list");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }
    }
```

（`HttpHeaders`、`HttpStatus`、`Nested`、`never` 等 import 该文件已有，无需新增。）

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn test -pl gateway -Dtest=AuthGlobalFilterTest`
预期：`shouldRejectProductInternalWithoutToken` FAIL（当前被公开前缀放行，未返回 401）；`shouldPassProductInternalWithValidToken` 可能碰巧通过（公开放行也会调 chain.filter）；`shouldKeepProductListPublic` PASS

- [ ] **步骤 3：实现过滤器变更**

`AuthGlobalFilter.java` 两处修改：

① `INTERNAL_PATHS` 增加一项：

```java
    /** 内部服务调用路径，通过 X-Internal-Token 请求头鉴权 */
    private static final List<String> INTERNAL_PATHS = List.of(
            "/api/order/internal",
            "/api/cart/internal",
            "/api/product/internal"
    );
```

② `filter` 方法中，把内部路径检查块移到公开路径检查**之前**（两个代码块整体交换位置）：

```java
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 内部服务路径：检查 X-Internal-Token（必须先于公开路径检查——
        // /api/product/internal 是公开前缀 /api/product 的子路径，更具体的规则先匹配）
        if (isInternalPath(path)) {
            String token = exchange.getRequest().getHeaders().getFirst("X-Internal-Token");
            if (internalToken.equals(token)) {
                return chain.filter(exchange);
            }
            log.warn("内部接口 token 无效: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 公开路径直接放行
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 后续代码（非 API 路径放行、JWT 验证）保持不变
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn test -pl gateway -Dtest=AuthGlobalFilterTest`
预期：`Tests run: 26, Failures: 0, Errors: 0`（原 23 + 新 3）

- [ ] **步骤 5：运行 gateway 模块全量测试（防回归）**

运行：`mvn test -pl gateway`
预期：全部通过（含 CorsConfigTest 4 个），0 Failures

- [ ] **步骤 6：展示变更摘要，经用户确认后 commit**

```bash
git add gateway/src/main/java/com/tianji/gateway/filter/AuthGlobalFilter.java \
        gateway/src/test/java/com/tianji/gateway/filter/AuthGlobalFilterTest.java
git commit -m "fix(gateway): check internal paths before public prefixes, guard /api/product/internal"
```

---

### 任务 4：全模块回归 + 文档同步

**文件：**
- 修改：`CLAUDE.md`

- [ ] **步骤 1：全模块测试**

运行：`mvn test`
预期：8 个模块全部 BUILD SUCCESS，总测试数 178（171 + 7），0 Failures

- [ ] **步骤 2：更新 CLAUDE.md**

逐处修改（共 5 处）：

1. 测试总数行改为：
   `**当前测试总数：178 (Gateway 30 + Controller 42 + Service 集成 26 + Service 单元 80)，8 个模块全覆盖。**`
2. Controller 测试清单表 `ProductControllerTest` 行：Tests `4` → `5`，覆盖列末尾追加 `+ 内部回填端点`
3. Controller 测试清单表 `AuthGlobalFilterTest` 行：Tests `23` → `26`
4. 关键约定「**内部端点**」条目改为：
   `- **内部端点**：/api/order/internal、/api/cart/internal、/api/product/internal 通过 X-Internal-Token 请求头鉴权（非 JWT），网关中内部路径检查先于公开前缀匹配`
   （保留原条目的反引号格式：路径用反引号括起）
5. 关键约定「**向量同步**」条目末尾追加一句：
   `存量回填走 POST /api/product/internal/sync-vectors（syncAllVectors，返回 {total, success, failed} 统计，Milvus upsert 幂等可重复触发）`

- [ ] **步骤 3：展示变更摘要，经用户确认后 commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for vector backfill endpoint (178 tests)"
```

---

### 任务 5：E2E 人工验证（不进代码库，需用户配合）

前置：Linux VM（192.168.150.11）上 Nacos / MySQL / Redis / RocketMQ / Milvus 正常运行。

- [ ] **步骤 1：用户在 IDEA 启动 4 个服务**

gateway (8080)、mall-goods-order (8082)、mcp-server (8084)、ai-chat-service (8085)，各自出现 `Started XxxApplication`。

- [ ] **步骤 2：触发回填**

```bash
curl -X POST -H "X-Internal-Token: tianji-internal-token-2024" \
  http://localhost:8080/api/product/internal/sync-vectors
```

预期：
- 响应 `{"code":200,...,"data":{"total":12,"success":12,"failed":0}}`
- ai-chat-service 日志出现 12 条 `商品向量已同步: productId=...`

- [ ] **步骤 3：无 token 安全验证**

```bash
curl -X POST -i http://localhost:8080/api/product/internal/sync-vectors
```

预期：HTTP 401，无响应体。

- [ ] **步骤 4：RAG 全链路验证**

1. `POST /api/user/login` 获取 JWT
2. 携带 `Authorization: Bearer <token>` 通过网关发 chat 请求（如「推荐拍照好的手机」）
3. 验收标准：
   - ai-chat-service 日志显示 RAG 检索返回非空商品 ID 列表
   - AI 回复引用了 `sql/seed.sql` 中的真实商品（名称/价格与库中一致）

- [ ] **步骤 5：验证结果记录**

如 `failed > 0` 或 RAG 检索为空，按 systematic-debugging 流程排查（常见原因：SiliconFlow key 未配置、Milvus 不可用导致 ai-chat-service 侧降级——回填统计仍显示 success 但实际未写入，见规格「错误处理」一节的已知限制）。
