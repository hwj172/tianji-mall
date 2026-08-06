# 前端打磨 #167 — 设计规格

> **日期：** 2026-08-05
> **范围：** `tianji-mall-frontend/`（Vue3 + Element Plus + Pinia）+ `mall-goods-order`（2 处后端支持）
> **前置：** 视觉伴侣首页方案 A 已确认（brainstorming 2026-08-05）；打磨范围 A 核心功能补真 + B 体验打磨 + C 构建优化 已确认

## 1. 目标

修复前端构建通过但功能不完整的问题：4 处假实现（加购/立即购买/购物车徽标/SKU 规格）、3 处体验缺陷（首页重复区块/地址手填/订单分页取巧）、1 处构建体积问题（主 chunk 762KB）。后端最小配合：为"立即购买"和"SKU 规格显示"提供直购入参与购物车 DTO 支持。

## 2. 现状与问题清单

前端基于 Vue3 + Vite 8 + Element Plus 2.14 + Pinia + Vue Router + Axios，`vite.config.js` 代理 `/api` → `http://192.168.150.11:8080`（VM 网关）。响应拦截器：`res.code !== 200 && !== 0` 报错；401 自动 logout。Element Plus 全量引入（`main.js` 的 `app.use(ElementPlus)` + 全量 icons）。

| # | 问题 | 位置 |
|---|------|------|
| A1 | `createOrder` 只接受 `cartItemIds`，无直购入口，"立即购买"无后端支持 | `OrderCreateRequest.java`、`OrderService.createOrder` |
| A2 | `ProductDetail.buyNow` 跳 checkout 传 `productId/qty` 但 checkout 忽略 query | `ProductDetail.vue:261`、`CheckoutPage.vue:127` |
| A3 | `ProductDetail.addToCart` 是 TODO 假实现，只 `ElMessage.success` 不调接口 | `ProductDetail.vue:255` |
| A4 | `DefaultLayout.cartCount = ref(0)` 从未加载，购物车徽标恒 0 | `DefaultLayout.vue:58` |
| A5 | 购物车/结算页 `specs: ''` 简化版，SKU 规格不显示；`/api/product/batch` 返回纯 `Product` 实体无 SKU 信息 | `CartPage.vue:95`、`CheckoutPage.vue:145` |
| B1 | `/home` 的 recommend 与 `/product/recommend` 双渲染导致"猜你喜欢/买了还买"重复 | `HomePage.vue:53-96` |
| B2 | `CheckoutPage` 新增地址表单用 3 个文本框手填省/市/区（`AddressPage.vue` 已实现 el-cascader 级联，模式可照搬） | `CheckoutPage.vue:72-78` |
| B3 | `OrderList.vue` 分页"满页假设有下一页"；后端 `list` 只返回 records 不返回 total，前端无法正确分页 | `OrderController.java`、`OrderList.vue:98` |
| C1 | 主 chunk 762KB（gzip 240KB），Element Plus 全量引入，未代码分割 | `vite.config.js`、`main.js` |
| C2 | `.superpowers/` 未加入 `.gitignore`（视觉伴侣原型持久化在该目录） | 根 `.gitignore` |

## 3. 设计决策

### 3.1 立即购买走后端直购（directItems），不做纯前端临时加购

理由：纯前端"临时加购 + 结算后删除"会污染购物车（结算中途放弃则残留），代码绕，需维护"直购来源"标记。后端直购是电商标配：`OrderCreateRequest` 增加可选 `directItems` 与 `cartItemIds` 二选一，`OrderService.createOrder` 复用现有库存/SKU/秒杀/优惠券校验，**不触发清购物车**。前端 checkout 按 `mode` 分支，语义清晰。

### 3.2 SKU 规格走后端购物车 DTO，不做前端 N+1 反查

理由：前端无法从 `getProductBatch`（纯 `Product`）拿到 SKU 规格；逐项 `getProductDetail` 反查是 N+1 请求。后端 `CartService.getCartList` 已遍历购物车项，批量查询 `product_sku` 一次拼装 `skuSpecs` 成本最低，与 `OrderService` 直购时 `sku.getSpecs()` 同源。`CartController.list` 返回类型由 `List<CartItem>` 改为 `List<CartItemDTO>`（字段超集，前端现有读取 `cart.id/productId/skuId/quantity/checked` 不受影响）。

### 3.3 首页采用方案 A：单数据源干净 3 区块

热销榜 ← `GET /api/home` 的 `hotProducts`（4 列）；猜你喜欢 + 买了还买 ← `GET /api/product/recommend` 的 `recommend`/`alsoBuy`（5 列，JWT 可选个性化）。删除 `/home` recommend（guessYouLike/hotSales/buyAfterBuy）的渲染。

### 3.4 构建优化只做 chunk 拆分，不引入按需引入插件

不引入 `unplugin-vue-components`（新增依赖 + 重构所有组件引用，超范围）。`vite.config.js` `manualChunks` 拆 `element-plus` / `vendor`（vue/vue-router/pinia/axios）两个独立 chunk，主 chunk 预期降至 <300KB。

## 4. 详细设计

### 4.1 后端：直购支持（A1）

**`mall-goods-order/src/main/java/com/tianji/mall/dto/DirectOrderItem.java`（新增）：**
```java
@Data
public class DirectOrderItem {
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    private Long skuId;                          // 可选：无 SKU 商品不传
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
```

**`OrderCreateRequest.java`（修改）：** 增加字段
```java
private List<DirectOrderItem> directItems;     // 立即购买直购项，与 cartItemIds 二选一
```

**`OrderService.createOrder`（修改）：** 把现有第 2 步"查询选中的购物车项"改为"解析订单行"：

```
boolean fromCart = cartItemIds 非空;
List<OrderLine> lines;                       // OrderLine = (productId, skuId, quantity)
if (fromCart) {
    // 现有逻辑：listByIds + userId 所有权校验 + checked==1 校验，映射为 lines
} else {
    if (directItems 为空) throw BizException(CART_ITEM_NOT_FOUND);
    lines = directItems 直接映射;             // 无 checked 校验
}
```

后续步骤（分布式锁 key、product 校验、SKU/秒杀价格库存、金额、优惠券、订单落库、扣库存）全部改为遍历 `lines` 而非 `cartItems`。第 11 步清购物车仅在 `fromCart` 时执行。锁 key 构造（`lock:sku:{id}` / `lock:product:{id}`）与现有逻辑一致，对 `lines` 的 skuId 取非空分支。

**测试**（`OrderServiceIntegrationTest` 新增用例，H2 + `@MockBean` 现有集合不变）：
- direct 模式：普通商品下单成功，订单含正确 productId/quantity，库存被扣减
- direct 模式：SKU 商品下单，price 取 SKU 价
- direct 模式：库存不足 → `STOCK_INSUFFICIENT`
- direct 模式：商品 status=0 → `PRODUCT_NOT_FOUND`
- direct 模式：购物车为空且 directItems 为空 → `CART_ITEM_NOT_FOUND`

### 4.2 后端：购物车 DTO（A5）

**`mall-goods-order/src/main/java/com/tianji/mall/dto/CartItemDTO.java`（新增）：**
```java
@Data
public class CartItemDTO {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private Long skuId;
    private Integer checked;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String skuSpecs;                   // 新增：SKU 规格描述（如 "红色;XL"），无 SKU 为 null
}
```

**`CartService.getCartList`（修改）：** 查询 `List<CartItem>` 后，收集 `skuId != null` 的项 → `skuService.listByIds(skuIds)` → 构建 `Map<Long, ProductSku>` → 复制字段并填 `skuSpecs = sku.getSpecs()`。**`CartController.list`** 返回类型改为 `R<List<CartItemDTO>>`。

**测试**（`CartServiceIntegrationTest` 新增）：含 SKU 的购物车项返回 `skuSpecs`；无 SKU 项 `skuSpecs == null`。

### 4.3 前端：商品详情页（A2 + A3）

**`ProductDetail.vue`：**

`addToCart` 补真：
```js
async function addToCart() {
  const skuId = getSelectedSkuId()
  await addToCart({ productId: product.value.id, skuId, quantity: quantity.value })
  cartStore.refreshCount()                    // 刷新顶部徽标
  ElMessage.success('已加入购物车')
}
```

`buyNow` 链路：
```js
function getSelectedSkuId() {
  if (!skuMatrix.value || !specTree.value?.length) return null
  const key = specTree.value.map(s => selectedSpecs[s.name] || '').join(';')
  return skuMatrix.value[key]?.id ?? null
}
function buyNow() {
  const skuId = getSelectedSkuId()
  router.push({
    name: 'checkout',
    query: { mode: 'buy', productId: product.value.id, skuId: skuId || '', qty: quantity.value }
  })
}
```

`getSelectedSkuId` 返回 null 分两种情形，必须区分：
- 商品**无 SKU**（`specTree` 为 null）：返回 null 属正常，直接按无 SKU 直购
- 商品**有 SKU**（`specTree` 非 null）但 key 无匹配（规格未选全）：`buyNow` 弹 `ElMessage.warning('请先选择商品规格')` 并 return，**禁止**按无 SKU 商品直购（否则以 product 默认价错误下单）。`isSpecDisabled` 只禁已确定冲突的规格，不保证用户必选全，故此处必须显式校验。

### 4.4 前端：结算页（A2 + A5 + B2）

**`CheckoutPage.vue`：**

- `onMounted` 判断 `route.query.mode === 'buy'` 分支 `loadBuyItem()`：
  - `getProductDetail(productId)` → `{ product, skuMatrix }`；有 `skuId` 时 `Object.values(skuMatrix).find(s => s.id === skuId)` 取 `price` + 拼 `specs`（key 的 `;` 分隔反转义），无 `skuId` 用 `product.price`
  - 构造 `items` 单元素：`{ product, image: getFirstImage(product.images), price, specs, cart: { id: null, quantity: qty } }`
- `submitOrder` 按 mode 分支：
  - buy 模式：`createOrder({ addressId, directItems: [{ productId, skuId, quantity: qty }] })`
  - cart 模式：现有 `cartItemIds`
- 下单成功两种模式都 `cartStore.refreshCount()`
- `item-spec` 渲染由 `item.specs` 改为优先 `item.cart.skuSpecs`（后端 DTO 已带，buy 模式用本地拼的 specs）
- **地址级联（B2）：** 新增地址 dialog 中，"所在地区"由 3 个 `el-input` 改为 `el-cascader`。**模式照搬 `AddressPage.vue:31-40,61-72,100-105`**（`regionTree`/`regionPath`/`loadRegionTree`/`handleSave` 提取路径回填 `newAddr`），`AddressPage.vue` 无需改动
- `getProductDetail`、`getRegionTree` 已存在于 `api/index.js`，无新增 API

### 4.5 前端：购物车页（A5）

**`CartPage.vue`：** `specs: ''` 改为 `specs: ci.skuSpecs || ''`（cart item 已是 `CartItemDTO`，`skuSpecs` 直接可用）。删除数量/勾选/删除后调 `cartStore.refreshCount()`。

### 4.6 前端：购物车徽标（A4）

**`stores/cart.js`（新增）：**
```js
export const useCartStore = defineStore('cart', {
  state: () => ({ count: 0 }),
  actions: {
    async refreshCount() {
      if (!useUserStore().isLoggedIn) { this.count = 0; return }
      try {
        const res = await getCartList()
        this.count = (res.data || []).reduce((s, i) => s + (i.quantity || 0), 0)
      } catch { this.count = 0 }
    }
  }
})
```

**`DefaultLayout.vue`：** 模板用 `cartStore.count`（保留 `:hidden="!cartCount"`）；`onMounted` 且登录态为真时 `cartStore.refreshCount()`。加购（A3）、下单（A4.4）、购物车变更（A4.5）均触发 refresh。

### 4.7 前端：首页去重（B1）

**`HomePage.vue`：** 删除 `homeData.recommend` 的 guessYouLike/hotSales/buyAfterBuy 三区块渲染。保留热销榜（`homeData.hotProducts`，4 列）；猜你喜欢 + 买了还买 渲染 `personalRecommend`/`personalAlsoBuy`（来自 `getProductRecommend`，5 列）。`personalRecommend`/`personalAlsoBuy` 的加载逻辑不变（JWT 可选，未登录时接口仍返回热销榜兜底）。

### 4.8 前端：订单分页（B3）

**`OrderController.list`（后端配套，第 3 处后端改动）：** 当前返回 `R<List<Order>>`（`orderService.getOrderPage(...).getRecords()`），**不含 total**。改为返回 `R<Page<Order>>`（不取 `getRecords()`），`Page` 对象含 `records`/`total`/`current`/`size`。`OrderControllerTest` 的 list 断言同步从 `$.data[0]` 改为 `$.data.records[0]`。

**`OrderList.vue:98`：** 移除"满页假设有下一页"。`const res = await getOrderList({...})` 后 `orders.value = res.data.records || []`，`total.value = res.data.total || 0`，`hasMore = currentPage.value * pageSize.value < total.value`。订单对象结构不变，后续 cancel/pay/receive 逻辑不受影响。

### 4.9 前端：构建优化（C1 + C2）

**`vite.config.js`：**
```js
build: {
  rollupOptions: {
    output: {
      manualChunks: {
        'element-plus': ['element-plus', '@element-plus/icons-vue'],
        'vendor': ['vue', 'vue-router', 'pinia', 'axios']
      }
    }
  }
}
```

**根 `.gitignore`：** 追加 `.superpowers/`。

## 5. 测试与验证

### 后端

```bash
mvn test -pl mall-goods-order -Dtest='OrderServiceIntegrationTest,CartServiceIntegrationTest'
```
预期：新增用例全 PASS，既有 385 个 mall-goods-order 测试无回归。

### 前端

无自动化测试框架，验证标准：
1. `npm run build` 成功（exit 0）
2. 构建产物主 chunk < 300KB（`dist/assets/` 文件大小核对）
3. 手动链路（dev server + proxy 联调 VM）：
   - 商品详情选规格 → 立即购买 → 结算页单商品 → 提交订单 → 订单详情正确
   - 商品详情选规格 → 加入购物车 → 顶部徽标 +1 → 购物车页显示规格
   - 结算页新增地址 → 级联选择省市区 → 提交
   - 订单列表翻页到底 → "没有更多"
   - 首页：热销榜/猜你喜欢/买了还买三区块不重复

## 6. 文件变更汇总

| 位置 | 文件 | 变更 |
|------|------|------|
| mall-goods-order | `dto/DirectOrderItem.java` | 新增 |
| mall-goods-order | `dto/OrderCreateRequest.java` | 加 `directItems` |
| mall-goods-order | `service/OrderService.java` | createOrder 行解析分支 |
| mall-goods-order | `dto/CartItemDTO.java` | 新增 |
| mall-goods-order | `service/CartService.java` | getCartList 拼 skuSpecs |
| mall-goods-order | `controller/CartController.java` | list 返回 DTO |
| mall-goods-order | `controller/OrderController.java` | list 返回 `Page<Order>`（含 total） |
| mall-goods-order | `OrderServiceIntegrationTest` | +5 direct 用例 |
| mall-goods-order | `CartServiceIntegrationTest` | +2 规格用例 |
| mall-goods-order | `OrderControllerTest` | list 断言改 `$.data.records[0]` |
| 前端 | `views/product/ProductDetail.vue` | addToCart/buyNow |
| 前端 | `views/order/CheckoutPage.vue` | buy 模式 + 地址级联 + skuSpecs |
| 前端 | `views/cart/CartPage.vue` | skuSpecs + 徽标刷新 |
| 前端 | `layouts/DefaultLayout.vue` | 购物车徽标 |
| 前端 | `stores/cart.js` | 新增 |
| 前端 | `views/home/HomePage.vue` | 方案 A 去重 |
| 前端 | `views/order/OrderList.vue` | 分页 total（读 Page.records） |
| 前端 | `vite.config.js` | manualChunks |
| 根 | `.gitignore` | 追加 `.superpowers/` |

## 7. 范围外

- 不在本项目内：支付宝/支付页、富文本编辑器按需引入、Element Plus 按需引入插件化（C1 只做 chunk 拆分）
- 不在本项目内：`AddressPage.vue`（已实现地区级联，无需改动）
- 不处理：前端其他页面的假实现/占位（本清单之外的 TODO 不在本次范围）
- 不改动：gateway 鉴权、Nacos/Sentinel 配置、部署脚本
