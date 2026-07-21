# 商品属性 + SKU/规格 设计文档

> **目标：** 为商品增加多规格（SKU）和属性参数能力，支持淘宝级的规格组合选购。

**架构：** 新增 `product_sku` 和 `product_attribute` 两张表，改造 `cart_item`/`order_item` 增加 SKU 关联，商品库存从 product 级别下沉到 SKU 级别。

**技术栈：** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7 / MySQL 8.0

---

## 1. 数据模型

### 1.1 新增表：product_sku（SKU/规格组合）

```sql
CREATE TABLE IF NOT EXISTS `product_sku` (
  `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id`  BIGINT         NOT NULL COMMENT '商品ID',
  `sku_code`    VARCHAR(128)   DEFAULT NULL COMMENT '商家自定义SKU编码',
  `specs`       VARCHAR(512)   NOT NULL COMMENT '规格组合（如"颜色:深空黑;容量:256G"）',
  `price`       DECIMAL(10,2)  DEFAULT NULL COMMENT 'SKU价格（NULL=使用商品默认价）',
  `stock`       INT            NOT NULL DEFAULT 0 COMMENT 'SKU库存',
  `sales`       INT            NOT NULL DEFAULT 0 COMMENT 'SKU销量',
  `status`      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';
```

### 1.2 新增表：product_attribute（商品属性/规格参数）

```sql
CREATE TABLE IF NOT EXISTS `product_attribute` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '属性ID',
  `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '属性名（如"屏幕尺寸"、"电池容量"）',
  `value`       VARCHAR(256) NOT NULL COMMENT '属性值（如"6.1英寸"、"4000mAh"）',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品属性表';
```

### 1.3 修改现有表

**cart_item 表 — 加 sku_id 列：**
```sql
ALTER TABLE cart_item ADD COLUMN `sku_id` BIGINT DEFAULT NULL COMMENT 'SKU ID（有规格时必须选）';
ALTER TABLE cart_item ADD KEY `idx_sku_id` (`sku_id`);
```

**order_item 表 — 加 SKU 快照列：**
```sql
ALTER TABLE order_item ADD COLUMN `sku_id`   BIGINT       DEFAULT NULL COMMENT 'SKU ID';
ALTER TABLE order_item ADD COLUMN `sku_specs` VARCHAR(512) DEFAULT NULL COMMENT 'SKU规格快照（如"颜色:红;尺寸:XL"）';
```

**product 表 — 保留 stock/price 作为默认值：**
- 无 SKU 的简单商品继续用 product.stock / product.price
- 有 SKU 的商品 product.stock 可用于快速判断"是否有货"（聚合值）
- 不做结构变更

---

## 2. 实体 & DTO

### 2.1 ProductSku（Entity）

```
id, productId, skuCode, specs, price(BigDecimal), stock, sales, status, createTime, updateTime
```

### 2.2 ProductAttribute（Entity）

```
id, productId, name, value, sort, createTime
```

### 2.3 新增请求对象

**SkuStockRequest（Admin 创建/更新 SKU）：**
```java
- String skuCode;       // 可选
- String specs;          // "颜色:深空黑;容量:256G"
- BigDecimal price;      // 可选，null=用商品默认价
- Integer stock;
```

**AddToCartRequest（修改，加 skuId）：**
```java
- Long productId;
- Long skuId;        // 新增，可选
- Integer quantity;
// 删除 checked 字段（前端可以单独调选中接口）
// 实际只加 skuId，checked 保留不动
```

**OrderItemRequest（修改，加 skuId）：**
```java
- Long productId;
- Long skuId;        // 新增，可选
- Integer quantity;
```

### 2.4 新增 Mapper

- `ProductSkuMapper extends BaseMapper<ProductSku>`
  - `@Update("UPDATE product_sku SET stock = stock - #{qty} WHERE id = #{skuId} AND stock >= #{qty}") int deductStock(skuId, qty)`
  - `@Update("UPDATE product_sku SET stock = stock + #{qty} WHERE id = #{skuId}") int restoreStock(skuId, qty)`
  - `@Update("UPDATE product_sku SET sales = sales + #{qty} WHERE id = #{skuId}") int incrementSales(skuId, qty)`

- `ProductAttributeMapper extends BaseMapper<ProductAttribute>`
  - 基础 CRUD，不需自定义方法

---

## 3. 业务逻辑

### 3.1 下单流程改造（OrderService.createOrder）

```
1. 遍历 orderItems
2. 有 skuId → 查 product_sku，校验 stock >= quantity
           → SKU 锁 key: "sku:stock:{skuId}"
           → 扣 sku.stock
  无 skuId → 查 product.stock（现有逻辑不变）
           → 锁 key: "product:stock:{productId}"
3. 多锁排序防死锁（product 锁 + sku 锁统一排序）
4. 锁内重新读取库存，原子扣减
```

### 3.2 取消/超时恢复库存

```
- 有 sku_id → restoreStock(skuId)
- 无 sku_id → restoreStock(productId)（现有逻辑）
```

### 3.3 购物车改造

```
POST /api/cart
  无 skuId → 现有逻辑不变（直接关联 productId）
  有 skuId → 校验 SKU 存在 + 属于该商品 + SKU 有库存
            → cart_item 记录 skuId

GET /api/cart/list
  返回购物车项时附带 SKU 信息（specs, price, stock）
```

### 3.4 商品详情改造

```
GET /api/product/{id}
  返回时附加：
    - skuList: 该商品所有 SKU（id, specs, price, stock）
    - attributes: 该商品所有属性（name, value）
```

### 3.5 Admin 管理 SKU

```
POST   /api/admin/product/{productId}/sku       创建 SKU
PUT    /api/admin/product/{productId}/sku/{id}  更新 SKU
DELETE /api/admin/product/{productId}/sku/{id}  删除 SKU（stock=0 才允许）
GET    /api/admin/product/{productId}/sku       列出所有 SKU
```

### 3.6 Admin 管理属性

```
POST   /api/admin/product/{productId}/attribute       添加属性
PUT    /api/admin/product/{productId}/attribute/{id}  更新属性
DELETE /api/admin/product/{productId}/attribute/{id}  删除属性
GET    /api/admin/product/{productId}/attribute       列出属性
```

### 3.7 搜索兼容

商品搜索（FULLTEXT）不变——搜的是 product.name + product.description，不涉及 SKU。搜索结果列表中每个商品显示最低价和总库存。

---

## 4. 测试计划

| 测试类 | 新增/修改 | 覆盖 |
|--------|----------|------|
| ProductSkuServiceTest | 新建 | SKU CRUD + 库存扣减/恢复 + 原子性验证 |
| ProductAttributeServiceTest | 新建 | 属性 CRUD |
| OrderServiceTest | 修改 | skuId 下单 + SKU 库存不足 + 混合下单(product+sku) |
| CartServiceTest | 修改 | skuId 加购 + SKU 规格校验 |
| AdminControllerTest | 修改 | 新增 SKU/属性管理端点 |
| ProductControllerTest | 修改 | 商品详情返回 SKU+属性 |
| OrderServiceIntegrationTest | 修改 | SKU 下单集成测试 |

---

## 5. 兼容性

| 场景 | 行为 |
|------|------|
| 无 SKU 的旧商品 | product.stock/price 生效，cart/order 的 sku_id=NULL |
| 有 SKU 的商品 | SKU 维度库存和价格，cart/order 必须选 sku_id |
| 删 SKU | 仅当 stock=0 + 无未完成订单引用 |
| 缓存 | product cache 需包含 SKU 列表，CacheEvict 扩展 sku 相关方法 |

---

## 6. 验收

- `mvn test` 全模块 BUILD SUCCESS
- 创建带 3 个颜色 × 2 个容量 = 6 个 SKU 的商品
- 选 SKU 加购物车 → 下单 → 扣对应 SKU 库存
- 取消订单 → 恢复 SKU 库存
- Admin 管理 SKU 和属性
- 商品详情返回 SKU 列表 + 属性列表
