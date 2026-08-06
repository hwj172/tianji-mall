# 前端打磨 #167 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。
>
> **Commit 策略（本项目全局约定，高于技能默认）：** 不自动 `git commit`/`git push`，除非用户明确要求。每个后端任务完成且验证通过后，**向用户展示变更摘要并请求授权**再提交（一次后端改动一个 commit）。前端项目 `tianji-mall-frontend/` 尚未纳入 git 追踪，**前端任务只改文件，不执行 git 命令**，全部完成后统一处理前端追踪。
>
> **前端无单元测试框架**（无 vitest/jest），前端任务以 `npm run build` 编译验证 + 手动链路清单替代 TDD。后端任务遵循 TDD（H2 集成测试）。

**目标：** 修复前端 4 处假实现（加购/立即购买/购物车徽标/SKU 规格）+ 3 处体验缺陷（首页重复区块/结算地址手填/订单分页取巧）+ 构建优化（762KB 主 chunk），后端最小配合 3 处。

**架构：** 后端（mall-goods-order）三处独立改动：① `OrderCreateRequest` 增可选 `directItems` 支持立即购买直购；② `CartService.getCartList` 返回 `CartItemDTO`（含 `skuSpecs`）支持规格显示；③ `OrderController.list` 返回 `Page<Order>`（含 total）支持正确分页。前端按依赖顺序实现：先建购物车 store（徽标依赖）→ 商品详情加购/直购 → 结算页 buy 模式 → 购物车/首页/订单列表 → 构建优化。

**技术栈：** 后端 Java 17 + Spring Boot 3.2.5 + MyBatis-Plus（H2 测试）；前端 Vue3.5 + Vite 8 + Element Plus 2.14 + Pinia + Vue Router。

---

## 文件结构

**后端（mall-goods-order）：**
- 创建 `dto/DirectOrderItem.java` — 直购项（productId/skuId/quantity）
- 创建 `dto/CartItemDTO.java` — 购物车项 + skuSpecs
- 修改 `dto/OrderCreateRequest.java` — 加 `directItems`
- 修改 `service/OrderService.java` — createOrder 行解析分支
- 修改 `service/CartService.java` — getCartList 返回 DTO 拼 skuSpecs
- 修改 `controller/CartController.java` — list/listInternal 返回 DTO
- 修改 `controller/OrderController.java` — list 返回 Page
- 修改 4 个测试：`OrderServiceIntegrationTest`、`CartControllerTest`、`CartServiceIntegrationTest`、`CartServiceTest`、`OrderControllerTest`

**前端（tianji-mall-frontend/src/）：**
- 创建 `stores/cart.js` — 购物车徽标计数 store
- 修改 `layouts/DefaultLayout.vue` — 接入徽标
- 修改 `views/product/ProductDetail.vue` — 加购/立即购买补真
- 修改 `views/order/CheckoutPage.vue` — buy 模式 + 地区级联 + skuSpecs
- 修改 `views/cart/CartPage.vue` — skuSpecs + 徽标刷新
- 修改 `views/home/HomePage.vue` — 方案 A 去重
- 修改 `views/order/OrderList.vue` — 分页 total
- 修改 `vite.config.js` — manualChunks
- 修改根 `.gitignore` — 追加 `.superpowers/`

---

## 后端任务（Phase 1）

### 任务 1：直购支持（DirectOrderItem + OrderService 行解析）

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/DirectOrderItem.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/dto/OrderCreateRequest.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java`
- 测试：`mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceIntegrationTest.java`

- [ ] **步骤 1：在 `OrderServiceIntegrationTest` 末尾追加 5 个 direct 用例（先失败）**

在 `// ==================== helpers ====================` 段之前追加。测试类 `@BeforeEach` 已初始化 `addressId`（`insertAddress(1L, "张三", "13800000001")` 返回 Long）与 `productId`（`insertProduct("iPhone 15", 6999, 10)`，价格 6999）；`skuMapper`/`orderItemMapper`/`productMapper` 已注入。direct 用例构造 `OrderCreateRequest` 时用 `setDirectItems` 不用 `setCartItemIds`（复用现有 `productId`/`addressId` 字段，`productSku` 内联构造——照抄本类 line 189-196 的 SKU 测试风格）：

```java
    // ==================== createOrder direct（立即购买） ====================

    @Test
    void shouldCreateOrderWithDirectItem() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        DirectOrderItem d = new DirectOrderItem();
        d.setProductId(productId);
        d.setQuantity(2);
        req.setDirectItems(List.of(d));

        Order order = orderService.createOrder(1L, req);

        assertThat(order).isNotNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(13998));
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(productId);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getStock()).isEqualTo(8);
    }

    @Test
    void shouldCreateOrderWithDirectSkuItem() {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecs("颜色:红;容量:256G");
        sku.setPrice(BigDecimal.valueOf(7999));
        sku.setStock(5);
        sku.setStatus(1);
        skuMapper.insert(sku);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        DirectOrderItem d = new DirectOrderItem();
        d.setProductId(productId);
        d.setSkuId(sku.getId());
        d.setQuantity(2);
        req.setDirectItems(List.of(d));

        Order order = orderService.createOrder(1L, req);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        assertThat(items.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(7999));
        assertThat(items.get(0).getSkuSpecs()).isEqualTo("颜色:红;容量:256G");
    }

    @Test
    void shouldThrowStockInsufficientForDirectItem() {
        Long lowStockId = insertProduct("库存不足", BigDecimal.valueOf(100), 3);
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        DirectOrderItem d = new DirectOrderItem();
        d.setProductId(lowStockId);
        d.setQuantity(10);
        req.setDirectItems(List.of(d));

        assertThatThrownBy(() -> orderService.createOrder(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void shouldThrowProductNotFoundForDirectItem() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);
        DirectOrderItem d = new DirectOrderItem();
        d.setProductId(999999L);
        d.setQuantity(1);
        req.setDirectItems(List.of(d));

        assertThatThrownBy(() -> orderService.createOrder(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void shouldThrowCartItemNotFoundWhenBothEmpty() {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(addressId);

        assertThatThrownBy(() -> orderService.createOrder(1L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("购物车");
    }
```

新增 import：`com.tianji.mall.dto.DirectOrderItem`（`OrderCreateRequest` import 行旁）。`ProductSku`/`Product`/`OrderItem` 已被 `import com.tianji.mall.entity.*`（line 6）覆盖，`skuMapper`/`orderItemMapper`/`productMapper` 已注入，`BigDecimal`/`List`/`assertThat`/`assertThatThrownBy` 已 import——无需其他改动。

- [ ] **步骤 2：运行测试确认失败**

```bash
cd /d/TEST/tianji-mall && mvn test -pl mall-goods-order -Dtest=OrderServiceIntegrationTest
```

预期：编译失败（`DirectOrderItem` 不存在）或 `shouldCreateOrderWithDirectItem` 失败（`@NotEmpty` 拦下 cartItemIds 空 → 400）。

- [ ] **步骤 3：创建 `DirectOrderItem.java`**

```java
package com.tianji.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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

- [ ] **步骤 4：修改 `OrderCreateRequest.java` — 加 `directItems`**

`cartItemIds` 的 `@NotEmpty` 校验去掉（directItems 模式允许 cartItemIds 为空），改为 Service 层二选一校验：

```java
    @NotNull(message = "购物车项不能为空")
    private List<Long> cartItemIds;

    private List<DirectOrderItem> directItems;  // 立即购买直购项，与 cartItemIds 二选一
```

原 `@NotEmpty` 改为 `@NotNull`（`@NotEmpty` 会拦截空 list；Service 层已有"两者皆空 → CART_ITEM_NOT_FOUND"兜底）。`@NotNull` 的 import 已存在。

- [ ] **步骤 5：修改 `OrderService.createOrder` — 行解析分支**

类内新增私有 record（放在类尾部，`createOrder` 之后）：

```java
    /** 订单行：购物车结算与立即购买直购的统一中间结构 */
    private record OrderLine(Long productId, Long skuId, Integer quantity) {}
```

`createOrder` 改造（保持地址校验第 1 步不动）：

**第 2 步「查询选中的购物车项」整段替换为：**
```java
        // 2. 解析订单行（购物车结算 或 立即购买直购）
        List<Long> cartItemIds = req.getCartItemIds();
        boolean fromCart = cartItemIds != null && !cartItemIds.isEmpty();
        List<OrderLine> lines;
        if (fromCart) {
            List<CartItem> cartItems = cartService.listByIds(cartItemIds);
            if (cartItems.isEmpty()) {
                throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
            }
            for (CartItem item : cartItems) {
                if (!item.getUserId().equals(userId)) {
                    throw new BizException(BizErrorCode.CART_ITEM_NOT_OWNER);
                }
                if (item.getChecked() != 1) {
                    throw new BizException(BizErrorCode.CART_ITEM_NOT_CHECKED);
                }
            }
            lines = cartItems.stream()
                    .map(ci -> new OrderLine(ci.getProductId(), ci.getSkuId(), ci.getQuantity()))
                    .toList();
        } else {
            if (req.getDirectItems() == null || req.getDirectItems().isEmpty()) {
                throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
            }
            lines = req.getDirectItems().stream()
                    .map(d -> new OrderLine(d.getProductId(), d.getSkuId(), d.getQuantity()))
                    .toList();
        }
```

**第 3 步「获取商品 ID 列表」替换（cartItems → lines）：**
```java
        // 3. 获取商品 ID 列表
        List<Long> productIds = lines.stream()
                .map(OrderLine::productId)
                .distinct()
                .toList();
```

**第 3.5 步「锁 key」循环体替换（cartItem → line）：**
```java
        for (OrderLine line : lines) {
            if (line.skuId() != null) {
                lockKeyStrings.add("lock:sku:" + line.skuId());
            } else {
                lockKeyStrings.add("lock:product:" + line.productId());
            }
        }
```

**第 5 步「校验库存并计算金额」循环体替换（cartItem → line，字段访问改用 record accessor）：**
```java
            for (OrderLine line : lines) {
                Product product = productMap.get(line.productId());
                if (product == null || product.getStatus() == 0) {
                    throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND, product != null ? product.getName() : "未知");
                }

                BigDecimal itemPrice;
                String skuSpecs = null;

                if (line.skuId() != null) {
                    ProductSku sku = skuService.getById(line.skuId());
                    if (sku == null || !sku.getProductId().equals(line.productId())) {
                        throw new BizException(BizErrorCode.SKU_NOT_FOUND, product.getName());
                    }
                    if (sku.getStock() < line.quantity()) {
                        throw new BizException(BizErrorCode.STOCK_INSUFFICIENT, product.getName());
                    }
                    itemPrice = sku.getPrice() != null ? sku.getPrice() : product.getPrice();
                    skuSpecs = sku.getSpecs();
                } else {
                    if (seckillService.isSeckillActive(product)) {
                        if (product.getSeckillStock() < line.quantity()) {
                            throw new BizException(BizErrorCode.SECKILL_STOCK_INSUFFICIENT, product.getName());
                        }
                        itemPrice = product.getSeckillPrice();
                    } else {
                        if (product.getStock() < line.quantity()) {
                            throw new BizException(BizErrorCode.STOCK_INSUFFICIENT, product.getName());
                        }
                        itemPrice = product.getPrice();
                    }
                }

                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setPrice(itemPrice);
                orderItem.setQuantity(line.quantity());
                orderItem.setSkuId(line.skuId());
                orderItem.setSkuSpecs(skuSpecs);
                orderItems.add(orderItem);

                totalAmount = totalAmount.add(itemPrice.multiply(BigDecimal.valueOf(line.quantity())));
            }
```

**第 11 步「清购物车」加条件（仅购物车结算时清）：**
```java
            // 11. 清购物车（仅购物车结算；直购不产生购物车项）
            if (fromCart) {
                cartService.removeByIds(cartItemIds);
            }
```

其余步骤（第 4 步锁、第 6 步优惠券、第 7-10 步、第 12-13 步）**不动**——第 10 步扣库存已遍历 `orderItems` 不依赖 cartItems。

- [ ] **步骤 6：运行测试确认通过**

```bash
cd /d/TEST/tianji-mall && mvn test -pl mall-goods-order -Dtest=OrderServiceIntegrationTest
```

预期：全部 PASS（含原有 6 个 + 新增 5 个 direct 用例）。`mvn compile` BUILD SUCCESS。

- [ ] **步骤 7：向用户展示变更摘要，请求授权后提交**

```bash
cd /d/TEST/tianji-mall && git add mall-goods-order/src/main/java/com/tianji/mall/dto/DirectOrderItem.java mall-goods-order/src/main/java/com/tianji/mall/dto/OrderCreateRequest.java mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceIntegrationTest.java
git commit -m "feat(order): support direct-purchase items in createOrder"
```

### 任务 2：购物车 DTO（CartItemDTO + skuSpecs）

**文件：**
- 创建：`mall-goods-order/src/main/java/com/tianji/mall/dto/CartItemDTO.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/CartService.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/CartController.java`
- 修改：`CartControllerTest.java`（2 处 mock 类型）
- 修改：`CartServiceIntegrationTest.java`（3 处返回类型 + 新增 2 用例）
- 修改：`CartServiceTest.java`（1 处返回类型）

- [ ] **步骤 1：创建 `CartItemDTO.java`**

```java
package com.tianji.mall.dto;

import lombok.Data;

import java.time.LocalDateTime;

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
    private String skuSpecs;   // SKU 规格描述（如 "红色;XL"），无 SKU 为 null
}
```

- [ ] **步骤 2：修改 `CartService.getCartList` — 返回 DTO + 拼 skuSpecs**

```java
    public List<CartItemDTO> getCartList(Long userId) {
        List<CartItem> items = list(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime));
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> skuIds = items.stream()
                .map(CartItem::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuService.listByIds(skuIds).stream()
                        .collect(Collectors.toMap(ProductSku::getId, s -> s));

        return items.stream().map(item -> {
            CartItemDTO dto = new CartItemDTO();
            dto.setId(item.getId());
            dto.setUserId(item.getUserId());
            dto.setProductId(item.getProductId());
            dto.setQuantity(item.getQuantity());
            dto.setSkuId(item.getSkuId());
            dto.setChecked(item.getChecked());
            dto.setCreateTime(item.getCreateTime());
            dto.setUpdateTime(item.getUpdateTime());
            ProductSku sku = item.getSkuId() != null ? skuMap.get(item.getSkuId()) : null;
            dto.setSkuSpecs(sku != null ? sku.getSpecs() : null);
            return dto;
        }).toList();
    }
```

新增 import：`com.tianji.mall.dto.CartItemDTO`、`java.util.Map`、`java.util.Objects`、`java.util.stream.Collectors`（`LambdaQueryWrapper`/`ProductSku`/`List` 已有）。

- [ ] **步骤 3：修改 `CartController.java` — list/listInternal 返回 DTO**

两处 `R<List<CartItem>>` → `R<List<CartItemDTO>>`（`list` 与 `listInternal`），同时 `CartItem` import 若仅这两处使用则替换为 `CartItemDTO`。

- [ ] **步骤 4：更新 3 个测试文件的返回类型**

`CartServiceIntegrationTest.java` 3 处（line 85/99/123）：
```java
        List<CartItemDTO> items = cartService.getCartList(1L);
```
新增 import `com.tianji.mall.dto.CartItemDTO`。`CartServiceTest.java` line 54 同样处理。

`CartControllerTest.shouldGetCartList`（line 80-91）：mock 对象改为 DTO：
```java
    @Test
    void shouldGetCartList() throws Exception {
        CartItem item = buildCartItem(1L, 100L, 2);
        CartItemDTO dto = new CartItemDTO();
        dto.setId(item.getId());
        dto.setProductId(item.getProductId());
        dto.setQuantity(item.getQuantity());
        when(cartService.getCartList(1L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/cart/list")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].productId").value(100))
                .andExpect(jsonPath("$.data[0].quantity").value(2));
    }
```
新增 import `com.tianji.mall.dto.CartItemDTO`。

- [ ] **步骤 5：`CartServiceIntegrationTest` 先补 SKU 基础设施，再追加 2 个 skuSpecs 用例**

该测试类现缺 `skuMapper` 注入（仅 `cartItemMapper`/`productMapper`）。在测试类内补三处：

新增 import（现有 import 均为单类）：
```java
import com.tianji.mall.dto.CartItemDTO;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.ProductSkuMapper;
```

`@Autowired` 字段（`productMapper` 旁）：
```java
    @Autowired
    private ProductSkuMapper skuMapper;
```

`setUp` 内追加清理（`productMapper.delete(...)` 后）：
```java
        skuMapper.delete(new LambdaQueryWrapper<>());
```

然后在 `// ==================== helpers ====================` 段前追加 2 个用例（`insertProduct(name, int)` 返回 `Product`、`insertCartItem(userId, productId, qty)` 无 skuId 参数——SKU 项用 `cartItemMapper.insert` 内联构造）：

```java
    // ==================== getCartList skuSpecs ====================

    @Test
    void shouldReturnSkuSpecsForSkuItem() {
        Product product = insertProduct("SKU商品", 10);
        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSpecs("颜色:红;容量:256G");
        sku.setPrice(BigDecimal.valueOf(1200));
        sku.setStock(5);
        sku.setStatus(1);
        skuMapper.insert(sku);

        CartItem item = new CartItem();
        item.setUserId(1L);
        item.setProductId(product.getId());
        item.setSkuId(sku.getId());
        item.setQuantity(2);
        item.setChecked(1);
        cartItemMapper.insert(item);

        List<CartItemDTO> items = cartService.getCartList(1L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSkuId()).isEqualTo(sku.getId());
        assertThat(items.get(0).getSkuSpecs()).isEqualTo("颜色:红;容量:256G");
    }

    @Test
    void shouldReturnNullSkuSpecsForNoSkuItem() {
        Product product = insertProduct("普通商品", 10);
        insertCartItem(1L, product.getId(), 1);

        List<CartItemDTO> items = cartService.getCartList(1L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSkuSpecs()).isNull();
    }
```

`CartItem`/`Product` import 已有（line 6-7），`BigDecimal`/`assertThat`/`LambdaQueryWrapper` 已有，无需其他改动。

- [ ] **步骤 6：运行测试确认通过**

```bash
cd /d/TEST/tianji-mall && mvn test -pl mall-goods-order -Dtest=CartControllerTest,CartServiceIntegrationTest,CartServiceTest
```

预期：全部 PASS。`mvn compile` BUILD SUCCESS。

- [ ] **步骤 7：向用户展示变更摘要，请求授权后提交**

```bash
cd /d/TEST/tianji-mall && git add mall-goods-order/src/main/java/com/tianji/mall/dto/CartItemDTO.java mall-goods-order/src/main/java/com/tianji/mall/service/CartService.java mall-goods-order/src/main/java/com/tianji/mall/controller/CartController.java mall-goods-order/src/test/java/com/tianji/mall/controller/CartControllerTest.java mall-goods-order/src/test/java/com/tianji/mall/service/CartServiceIntegrationTest.java mall-goods-order/src/test/java/com/tianji/mall/service/CartServiceTest.java
git commit -m "feat(cart): return CartItemDTO with skuSpecs"
```

### 任务 3：OrderController.list 返回 Page

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/OrderController.java`
- 修改：`OrderControllerTest.java`

- [ ] **步骤 1：修改 `OrderController.list` 返回 `R<Page<Order>>`**

```java
    @GetMapping("/list")
    public R<Page<Order>> list(@RequestHeader("Authorization") String authHeader,
                                @RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(orderService.getOrderPage(userId, page, size));
    }
```

去掉 `.getRecords()`。import：`com.baomidou.mybatisplus.extension.plugins.pagination.Page`（`List` import 若其他地方仍用则保留）。

- [ ] **步骤 2：更新 `OrderControllerTest.shouldGetOrderList` 断言**

line 130 断言 `$.data[0].orderNo` → `$.data.records[0].orderNo`（mock 已是 `Page<Order>` 无需改）：

```java
                .andExpect(jsonPath("$.data.records[0].orderNo").value("202407160001"));
```

- [ ] **步骤 3：运行测试确认通过**

```bash
cd /d/TEST/tianji-mall && mvn test -pl mall-goods-order -Dtest=OrderControllerTest
```

预期：全部 PASS。

- [ ] **步骤 4：向用户展示变更摘要，请求授权后提交**

```bash
cd /d/TEST/tianji-mall && git add mall-goods-order/src/main/java/com/tianji/mall/controller/OrderController.java mall-goods-order/src/test/java/com/tianji/mall/controller/OrderControllerTest.java
git commit -m "feat(order): return Page with total in order list"
```

---

## 前端任务（Phase 2）

> 前端项目未 git 追踪，以下任务**只改文件不 commit**。

### 任务 4：购物车徽标 store + 布局接入（A4）

**文件：**
- 创建：`tianji-mall-frontend/src/stores/cart.js`
- 修改：`tianji-mall-frontend/src/layouts/DefaultLayout.vue`

- [ ] **步骤 1：创建 `stores/cart.js`**

```js
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getCartList } from '@/api'
import { useUserStore } from '@/stores/user'

export const useCartStore = defineStore('cart', () => {
  const count = ref(0)

  async function refreshCount() {
    if (!useUserStore().isLoggedIn) { count.value = 0; return }
    try {
      const res = await getCartList()
      count.value = (res.data || []).reduce((s, i) => s + (i.quantity || 0), 0)
    } catch {
      count.value = 0
    }
  }

  return { count, refreshCount }
})
```

- [ ] **步骤 2：修改 `DefaultLayout.vue`**

- 删除 `const cartCount = ref(0)`（line 58）
- 新增 import 与实例：
```js
import { onMounted } from 'vue'
import { useCartStore } from '@/stores/cart'
const cartStore = useCartStore()
onMounted(() => { if (userStore.isLoggedIn) cartStore.refreshCount() })
```
- 模板 line 14 徽标绑定替换：
```html
<router-link to="/cart"><el-badge :value="cartStore.count" :hidden="!cartStore.count"><el-button text>🛒 购物车</el-button></el-badge></router-link>
```

- [ ] **步骤 3：验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0。登录态手动验证：登录后徽标显示购物车总数量。

### 任务 5：商品详情 — 加购补真 + 立即购买直购（A2 + A3）

**文件：**
- 修改：`tianji-mall-frontend/src/views/product/ProductDetail.vue`

- [ ] **步骤 1：script 顶部新增 import**

```js
import { addToCart as apiAddToCart } from '@/api'
import { useCartStore } from '@/stores/cart'
const cartStore = useCartStore()
```

（`addToCart` 本地函数名与 API 冲突，import 必须重命名。）

- [ ] **步骤 2：替换 `addToCart` 与 `buyNow`（line 255-263）**

```js
async function addToCart() {
  if (specTree.value?.length && !currentSkuInfo.value) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  try {
    await apiAddToCart({
      productId: product.value.id,
      skuId: currentSkuInfo.value?.id || null,
      quantity: quantity.value
    })
    cartStore.refreshCount()
    ElMessage.success('已加入购物车')
  } catch { /* interceptor 处理错误 */ }
}

function buyNow() {
  if (specTree.value?.length && !currentSkuInfo.value) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  router.push({
    name: 'checkout',
    query: {
      mode: 'buy',
      productId: product.value.id,
      skuId: currentSkuInfo.value?.id || '',
      qty: quantity.value
    }
  })
}
```

`currentSkuInfo`（line 205-209）与 `specTree`/`quantity` 均已存在，直接复用。`apiAddToCart` 是已存在端点（`POST /api/cart/add`，body `{productId, skuId, quantity}`）。

- [ ] **步骤 3：验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0。

### 任务 6：结算页 — buy 模式 + 地区级联 + skuSpecs（A2 前端 + A5 + B2）

**文件：**
- 修改：`tianji-mall-frontend/src/views/order/CheckoutPage.vue`

- [ ] **步骤 1：新增 buy 参数与 region 状态（script）**

```js
import { getCartList, getProductBatch, getProductDetail, getAddressList, addAddress, createOrder, getRegionTree } from '@/api'
import { useCartStore } from '@/stores/cart'
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const regionTree = ref([])
const regionPath = ref([])

const buyParams = computed(() => route.query.mode === 'buy' ? {
  productId: Number(route.query.productId),
  skuId: route.query.skuId ? Number(route.query.skuId) : null,
  qty: Number(route.query.qty) || 1
} : null)
```

`ref`/`computed` 已 import（line 90）。`useRoute`/`useRouter` 由 `import { useRouter } from 'vue-router'`（line 91）改为 `import { useRoute, useRouter } from 'vue-router'`，并新增 `const route = useRoute()`——`router` 已定义保持不动。

- [ ] **步骤 2：onMounted 分支加载**

```js
onMounted(async () => {
  await Promise.all([loadAddresses(), loadRegionTree()])
  if (buyParams.value) await loadBuyItem()
  else await loadItems()
})

async function loadRegionTree() {
  try {
    const res = await getRegionTree()
    regionTree.value = res.data || []
  } catch { /* ignore */ }
}

async function loadBuyItem() {
  try {
    const { productId, skuId, qty } = buyParams.value
    const res = await getProductDetail(productId)
    const p = res.data.product
    if (!p) { items.value = []; return }
    let price = p.price
    let specs = ''
    if (skuId && res.data.skuMatrix) {
      const found = Object.entries(res.data.skuMatrix).find(([, s]) => s.id === skuId)
      if (found) {
        const [key, sku] = found
        price = sku.price != null ? sku.price : price
        specs = sku.specs || key   // key 即 "红;XL" 规格串
      }
    }
    items.value = [{
      cart: { id: 0, quantity: qty },
      product: p,
      image: getFirstImage(p.images),
      price,
      specs
    }]
  } catch (e) {
    console.error('加载直购商品失败', e)
  }
}
```

`loadItems`（cart 模式）中 line 145 的 `specs: ''` 改为 `specs: ci.skuSpecs || ''`（cart 模式后端已返回 DTO）。

- [ ] **步骤 3：submitOrder 按 mode 分支**

```js
    const payload = buyParams.value
      ? { addressId: selectedAddressId.value, directItems: [{ productId: buyParams.value.productId, skuId: buyParams.value.skuId, quantity: buyParams.value.qty }] }
      : { addressId: selectedAddressId.value, cartItemIds: items.value.map(i => i.cart.id) }
    const res = await createOrder(payload)
    ElMessage.success('下单成功')
    cartStore.refreshCount()
    router.push({ name: 'orderDetail', params: { id: res.data.id } })
```

- [ ] **步骤 4：新增地址 dialog 的"所在地区"换 el-cascader（照搬 AddressPage 模式）**

模板 line 72-78 替换：
```html
        <el-form-item label="所在地区">
          <el-cascader
            v-model="regionPath"
            :options="regionTree"
            :props="{ value: 'name', label: 'name', children: 'children' }"
            placeholder="请选择省/市/区"
            clearable
            style="width: 100%"
          />
        </el-form-item>
```

`saveAddress`（line 161-183）改为从 `regionPath` 提取（删除 3 个文本框绑定的 `newAddr.province/city/district` 手动输入逻辑）：
```js
async function saveAddress() {
  const [province, city, district] = regionPath.value || []
  if (!newAddr.receiverName || !newAddr.phone || !province || !newAddr.detail) {
    ElMessage.warning('请填写完整地址信息')
    return
  }
  savingAddr.value = true
  try {
    await addAddress({
      receiverName: newAddr.receiverName,
      phone: newAddr.phone,
      province: province || '',
      city: city || '',
      district: district || '',
      detail: newAddr.detail,
      isDefault: newAddr.isDefault ? 1 : 0
    })
    showAddAddress.value = false
    ElMessage.success('地址已添加')
    await loadAddresses()
    Object.assign(newAddr, { receiverName: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })
    regionPath.value = []
  } catch { /* interceptor 处理 */ }
  finally { savingAddr.value = false }
}
```

- [ ] **步骤 5：模板 line 36 `:key="item.cart.id"` 改为 `:key="item.product.id"`**（buy 模式 cart.id 为 0，用 product.id 更稳）

- [ ] **步骤 6：验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0。

### 任务 7：购物车页 — skuSpecs + 徽标刷新（A5 前端 + A4 联动）

**文件：**
- 修改：`tianji-mall-frontend/src/views/cart/CartPage.vue`

- [ ] **步骤 1：line 95 `specs: ''` 改为 `specs: ci.skuSpecs || ''`**

- [ ] **步骤 2：加购车变更后刷新徽标**

新增 import `useCartStore` 并实例化；在数量修改、删除、勾选的各 handler 成功路径末尾调用 `cartStore.refreshCount()`（对应现有 `updateCartItem`/`deleteCartItem`/`checkCartItem` API 调用处，参考现有函数结构逐个加一行）。

- [ ] **步骤 3：验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0。

### 任务 8：首页 — 方案 A 去重（B1）

**文件：**
- 修改：`tianji-mall-frontend/src/views/home/HomePage.vue`

- [ ] **步骤 1：删除模板中 3 个重复区块（line 52-80）**

删除 `猜你喜欢`（`homeData.recommend.guessYouLike`）、`热销推荐`（`homeData.recommend.hotSales`）、`买了还买`（`homeData.recommend.buyAfterBuy`）三个 `<section>`。保留：热销榜（line 42-50，`hotProducts`，4 列）+ 个性化推荐（line 82+，`personalRecommend`/`personalAlsoBuy`，5 列）。`homeData.recommend` 数据保留（不渲染，无副作用，script 不动）。

- [ ] **步骤 2：验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0。手动：首页三区块不重复。

### 任务 9：订单列表 — 分页 total（B3 前端）

**文件：**
- 修改：`tianji-mall-frontend/src/views/order/OrderList.vue`

- [ ] **步骤 1：line 92-105 替换**

```js
async function loadOrders() {
  loading.value = true
  try {
    const res = await getOrderList({ page: currentPage.value, size: pageSize.value })
    const data = res.data || {}
    orders.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    console.error('加载订单列表失败', e)
  } finally {
    loading.value = false
  }
}
```

`getOrderList` 返回结构已由任务 3 改为 `{records, total, ...}`。模板分页器若用 `total` 已有绑定则直接生效；若原用取巧值则检查分页器 `:total="total"` 与"没有更多"逻辑。

- [ ] **步骤 2：验证**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0。手动：订单列表翻页到底显示"没有更多"。

### 任务 10：构建优化 + gitignore（C1 + C2）

**文件：**
- 修改：`tianji-mall-frontend/vite.config.js`
- 修改：根 `.gitignore`

- [ ] **步骤 1：vite.config.js 加 manualChunks**

```js
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://192.168.150.11:8080',
        changeOrigin: true
      }
    }
  },
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
})
```

- [ ] **步骤 2：根 `.gitignore` 末尾追加**

```
.superpowers/
```

- [ ] **步骤 3：验证构建产物**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
ls -la dist/assets/
```

预期：exit 0；`dist/assets/` 出现 `element-plus.*.js`、`vendor.*.js` 独立 chunk，主 `index.*.js` 体积明显下降（目标 gzip < 300KB）。

---

## 验证任务（Phase 3）

### 任务 11：全量验证

- [ ] **步骤 1：后端全量测试**

```bash
cd /d/TEST/tianji-mall && mvn test -pl mall-goods-order
```

预期：全部 PASS，0 failures。

- [ ] **步骤 2：前端构建**

```bash
cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build
```

预期：exit 0，chunk 拆分生效。

- [ ] **步骤 3：手动链路（dev server + VM proxy）**

1. 商品详情选规格 → 立即购买 → 结算页单商品（价格=SKU 价、规格显示）→ 提交订单 → 订单详情正确
2. 商品详情选规格 → 加入购物车 → 顶部徽标 +1 → 购物车页显示规格（`红色;XL`）
3. 结算页新增地址 → el-cascader 级联选择省市区 → 提交
4. 订单列表翻页到底 → "没有更多"
5. 首页：热销榜/猜你喜欢/买了还买三区块不重复
6. 购物车删除/改数量 → 徽标联动刷新

---

## 自检记录

- **规格覆盖度：** A1/A2/A3/A4/A5/B1/B2/B3/C1/C2 全部映射到任务 1-10；任务 11 为验证。A2 的 SKU 未选全守卫、B2 的 AddressPage 已实现无需改动均在对应任务内处理。
- **类型一致性：** `directItems` 字段名、`DirectOrderItem` 字段（productId/skuId/quantity）、`CartItemDTO.skuSpecs`、`Page.records/total` 在前后端及任务间一致。`addToCart` API 与本地函数重名 → import 别名 `apiAddToCart` 已在任务 5 声明。
- **已知风险：** `insertSku`/`insertCartItem(skuId)` helper 是否存在需在任务 2 步骤 5 按同包现有模式补充；`skuMatrix` 值是否含 `specs` 字段不确定——任务 6 已写兜底（`sku.specs || key`），即使缺字段也能显示规格串。
