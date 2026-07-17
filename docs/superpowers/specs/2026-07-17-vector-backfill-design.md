# 存量商品向量回填 + RAG 端到端验证 — 设计文档

## Context

Milvus RAG 管道已上线（commit `1de29d4` / `f430b19`），但 `product_vectors` collection 为空：

- `ProductService.syncVector` 已实现但**无任何调用方**（ProductController 只有读端点，商品无创建/修改入口，数据来自 `sql/seed.sql`）
- 种子数据 12 个上架商品，RAG 检索永远返回空列表，管道从未产生业务效果

本设计新增一次性可重复触发的**内部回填端点**，并定义全链路人工验收步骤。

## 关键前置发现

网关 `AuthGlobalFilter` 的 `PUBLIC_PATHS` 含 `/api/product`（前缀匹配），且**公开路径先于内部路径检查**。若不调整检查顺序，新端点 `/api/product/internal/sync-vectors` 会被当成公开路径放行，任何人可触发回填。

## 一、mall-goods-order 变更

### 1.1 `ProductService.syncVector` 返回值改造

`void` → `boolean`：成功返回 `true`，catch 到异常返回 `false`（日志行为不变）。用于回填统计。

### 1.2 `ProductService.syncAllVectors()` 新增

```java
public Map<String, Integer> syncAllVectors() {
    List<Product> products = list(new LambdaQueryWrapper<Product>()
            .eq(Product::getStatus, 1));
    int success = 0;
    for (Product p : products) {
        if (syncVector(p.getId(), p.getName(), p.getDescription())) {
            success++;
        }
    }
    return Map.of("total", products.size(),
                  "success", success,
                  "failed", products.size() - success);
}
```

- 只同步上架（`status=1`）商品，与 `getProductBatch` 过滤条件一致
- 单个失败不中断循环（`syncVector` 内部 catch），计入 `failed`
- 12 个商品 × 1 次 SiliconFlow embedding 调用，秒级完成，无需分批/异步

### 1.3 `ProductController` 新增内部端点

```java
// ===== 内部端点（网关 X-Internal-Token 鉴权，不暴露给前端）=====

@PostMapping("/internal/sync-vectors")
public R<Map<String, Integer>> syncVectors() {
    return R.ok(productService.syncAllVectors());
}
```

与 OrderController/CartController 的内部端点模式一致：服务层不做鉴权，鉴权在网关。

## 二、gateway 变更

`AuthGlobalFilter.filter` 中调整：

1. `INTERNAL_PATHS` 增加 `/api/product/internal`
2. **`isInternalPath` 检查移到 `isPublicPath` 之前**（更具体的规则先匹配）

顺序调整后现有行为不变：`/api/order/internal`、`/api/cart/internal` 原本就不在 PUBLIC_PATHS 前缀内，其余公开路径不含 `internal` 段。

## 三、错误处理

| 场景 | 行为 |
|------|------|
| 单个商品 embedding/upsert 失败 | 计入 `failed`，继续下一个 |
| ai-chat-service 整体不可用 | `failed = total`，接口仍返回 200 + 统计 |
| Milvus 不可用 | ai-chat-service 侧降级仅 warn（现有设计），回填统计会显示 success 但实际未写入——已知限制，由 E2E 验证步骤兜底发现 |

## 四、测试

| 测试类 | 新增用例 |
|--------|----------|
| `ProductServiceTest`（Mockito 单元测试，已存在） | ① 全部成功统计正确；② 部分失败统计正确（Mock AiChatFeignClient 对某商品抛异常）；③ 只同步 `status=1` 商品 |
| `ProductControllerTest` | ④ `POST /api/product/internal/sync-vectors` 路由 + 返回统计 |
| `AuthGlobalFilterTest`（gateway） | ⑤ `/api/product/internal` 无 token → 401；⑥ 有 token → 放行；⑦ `/api/product/list` 仍公开（回归保护顺序调整） |

## 五、E2E 验证步骤（人工执行，不进代码库）

1. 启动 gateway、mall-goods-order、mcp-server、ai-chat-service（IDEA）
2. 回填：
   ```bash
   curl -X POST -H "X-Internal-Token: tianji-internal-token-2024" \
     http://localhost:8080/api/product/internal/sync-vectors
   ```
   期望：`{"total":12,"success":12,"failed":0}`，ai-chat-service 日志出现 12 条 `商品向量已同步`
3. 登录获取 JWT → 通过网关发 chat 请求（如"推荐拍照好的手机"）
4. 验收标准：
   - ai-chat-service 日志显示 RAG 检索返回非空商品列表
   - AI 回复引用了种子数据中的真实商品（名称/价格与库中一致）

## 范围外

- `/api/product/batch` 公开端点收口（单独任务）
- 商品创建/修改端点与增量向量同步（当前不存在写入口，YAGNI）
- 回填的分批/异步/进度上报（12 个商品不需要）
