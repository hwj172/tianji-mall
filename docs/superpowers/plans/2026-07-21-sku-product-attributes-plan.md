# 商品 SKU + 属性参数 实现计划

> **面向 AI 代理的工作者：** 使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为商品增加多规格（SKU）和属性参数能力——新增 product_sku/product_attribute 表，改造 cart_item/order_item 支持 SKU，库存从 product 级别下沉到 SKU 级别。

**架构：** 新增 `product_sku`（SKU 规格组合，含独立 price/stock/sales）和 `product_attribute`（商品属性参数）两张表。cart_item 加 `sku_id`，order_item 加 `sku_id` + `sku_specs` 快照。下单流程从 product 锁改为 product+SKU 混合锁。无 SKU 的旧商品完全兼容。

**技术栈：** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7 / MySQL 8.0 / Redisson 3.32.0

---

## 文件结构总览

| 文件 | 操作 | 职责 |
|------|------|------|
| `sql/init.sql` | 修改 | 新增 product_sku、product_attribute 表；cart_item 加 sku_id；order_item 加 sku_id + sku_specs |
| `mall-goods-order/src/test/resources/schema.sql` | 修改 | H2 同步上述 DDL |
| `mall-goods-order/.../entity/ProductSku.java` | 新建 | SKU 实体 |
| `mall-goods-order/.../entity/ProductAttribute.java` | 新建 | 属性实体 |
| `mall-goods-order/.../entity/CartItem.java` | 修改 | 加 skuId 字段 |
| `mall-goods-order/.../entity/OrderItem.java` | 修改 | 加 skuId、skuSpecs 字段 |
| `mall-goods-order/.../mapper/ProductSkuMapper.java` | 新建 | SKU Mapper（含原子扣减/恢复/销量方法） |
| `mall-goods-order/.../mapper/ProductAttributeMapper.java` | 新建 | 属性 Mapper |
| `mall-goods-order/.../dto/CartAddRequest.java` | 修改 | 加 skuId 字段 |
| `mall-goods-order/.../dto/OrderItemResponse.java` | 修改 | 加 skuId、skuSpecs 字段 |
| `mall-goods-order/.../dto/SkuRequest.java` | 新建 | Admin SKU 创建/更新请求 |
| `mall-goods-order/.../service/ProductSkuService.java` | 新建 | SKU CRUD + 库存操作 |
| `mall-goods-order/.../service/ProductAttributeService.java` | 新建 | 属性 CRUD |
| `mall-goods-order/.../service/ProductService.java` | 修改 | getProductById 返回 SKU 列表+属性；deductStock/restoreStock 改为调用 SKU 服务 |
| `mall-goods-order/.../service/CartService.java` | 修改 | addItem 支持 skuId、含 SKU 去重逻辑 |
| `mall-goods-order/.../service/OrderService.java` | 修改 | createOrder/cancelOrder/cancelOrderByTimeout 支持 SKU 级别库存和价格 |
| `mall-goods-order/.../controller/AdminController.java` | 修改 | 新增 SKU/属性 CRUD 端点 |
| `mall-goods-order/.../controller/ProductController.java` | 修改 | detail 端点返回 SKU 列表+属性 |
| 测试文件（7 个） | 新建/修改 | 见任务分解 |

---

### 任务 1：SQL DDL — 新建表和修改现有表

**文件：**
- 修改：`sql/init.sql`
- 修改：`mall-goods-order/src/test/resources/schema.sql`

- [ ] **步骤 1：在 init.sql 末尾追加 product_sku 和 product_attribute 建表语句**

在文件末尾追加：

```sql
-- ============================================================
-- 商品 SKU（规格组合）
-- ============================================================
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
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

-- ============================================================
-- 商品属性参数
-- ============================================================
CREATE TABLE IF NOT EXISTS `product_attribute` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '属性ID',
  `product_id`  BIGINT       NOT NULL COMMENT '商品ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '属性名（如"屏幕尺寸"）',
  `value`       VARCHAR(256) NOT NULL COMMENT '属性值（如"6.1英寸"）',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品属性表';
```

- [ ] **步骤 2：修改 init.sql 中 cart_item 和 order_item 建表语句**

找到 `cart_item` 的 CREATE TABLE，在 `quantity` 后加：
```sql
`sku_id`       BIGINT   DEFAULT NULL COMMENT 'SKU ID（有规格时必须选）',
```

找到 `order_item` 的 CREATE TABLE，在 `quantity` 后加：
```sql
`sku_id`       BIGINT        DEFAULT NULL COMMENT 'SKU ID',
`sku_specs`    VARCHAR(512)  DEFAULT NULL COMMENT 'SKU规格快照',
```

> **注意：** 直接改 CREATE TABLE 而非 ALTER TABLE，MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`。已有数据库如需加列需手动执行 ALTER TABLE。

- [ ] **步骤 3：同步 H2 schema.sql**

在 `mall-goods-order/src/test/resources/schema.sql` 末尾追加 product_sku 和 product_attribute 建表语句。同时修改 cart_item 和 order_item 的 CREATE TABLE 加入新列：

**新增表（追加到文件末尾）：**
```sql
CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(128),
    specs VARCHAR(512) NOT NULL,
    price DECIMAL(10,2),
    stock INT DEFAULT 0,
    sales INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_attribute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    value VARCHAR(256) NOT NULL,
    sort INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**修改 cart_item 建表语句** — 在 `quantity INT` 之后加 `sku_id BIGINT,`：
```sql
CREATE TABLE IF NOT EXISTS cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    product_id BIGINT,
    quantity INT,
    sku_id BIGINT,
    checked INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**修改 order_item 建表语句** — 在 `quantity INT` 之后加 `sku_id BIGINT,` 和 `sku_specs VARCHAR(512),`：
```sql
CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(128),
    price DECIMAL(10,2),
    quantity INT,
    sku_id BIGINT,
    sku_specs VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **步骤 4：验证 DDL 语法**

```bash
# 确认 init.sql 无语法错误（可选，仅在 MySQL 可用时）
# 验证 schema.sql 可被 H2 解析（运行任意 @SpringBootTest）
mvn test -pl mall-goods-order -Dtest=ProductServiceIntegrationTest -DfailIfNoTests=false
```

预期：TestContext 启动成功（H2 解析新 DDL 不报错）。

- [ ] **步骤 5：Commit**

```bash
git add sql/init.sql mall-goods-order/src/test/resources/schema.sql
git commit -m "feat: add product_sku and product_attribute tables, extend cart_item and order_item for SKU support"
```

---

### 任务 2：新建实体类 ProductSku 和 ProductAttribute

**文件：**
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/entity/ProductSku.java`
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/entity/ProductAttribute.java`

- [ ] **步骤 1：创建 ProductSku 实体**

```java
package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String skuCode;
    private String specs;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **步骤 2：创建 ProductAttribute 实体**

```java
package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_attribute")
public class ProductAttribute {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    private String value;
    private Integer sort;
    private LocalDateTime createTime;
}
```

- [ ] **步骤 3：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/entity/ProductSku.java mall-goods-order/src/main/java/com/tianji/mall/entity/ProductAttribute.java
git commit -m "feat: add ProductSku and ProductAttribute entity classes"
```

---

### 任务 3：修改 CartItem 和 OrderItem 实体

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/entity/CartItem.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/entity/OrderItem.java`

- [ ] **步骤 1：CartItem 加 skuId 字段**

在 `CartItem.java` 的 `productId` 字段之后添加：

```java
private Long skuId;
```

- [ ] **步骤 2：OrderItem 加 skuId 和 skuSpecs 字段**

在 `OrderItem.java` 的 `quantity` 字段之后添加：

```java
private Long skuId;
private String skuSpecs;
```

- [ ] **步骤 3：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/entity/CartItem.java mall-goods-order/src/main/java/com/tianji/mall/entity/OrderItem.java
git commit -m "feat: add skuId to CartItem, skuId and skuSpecs to OrderItem"
```

---

### 任务 4：新建 Mapper 接口

**文件：**
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductSkuMapper.java`
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductAttributeMapper.java`

- [ ] **步骤 1：创建 ProductSkuMapper**

```java
package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /** 原子扣减 SKU 库存（stock >= qty 时才执行） */
    @Update("UPDATE product_sku SET stock = stock - #{qty}, sales = sales + #{qty} WHERE id = #{skuId} AND stock >= #{qty}")
    int deductStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    /** 恢复 SKU 库存（取消订单/超时取消） */
    @Update("UPDATE product_sku SET stock = stock + #{qty}, sales = sales - #{qty} WHERE id = #{skuId}")
    int restoreStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}
```

- [ ] **步骤 2：创建 ProductAttributeMapper**

```java
package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.ProductAttribute;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductAttributeMapper extends BaseMapper<ProductAttribute> {
}
```

- [ ] **步骤 3：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductSkuMapper.java mall-goods-order/src/main/java/com/tianji/mall/mapper/ProductAttributeMapper.java
git commit -m "feat: add ProductSkuMapper and ProductAttributeMapper"
```

---

### 任务 5：新建 DTO — SkuRequest，修改 CartAddRequest 和 OrderItemResponse

**文件：**
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/dto/SkuRequest.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/dto/CartAddRequest.java`
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/dto/OrderItemResponse.java`

- [ ] **步骤 1：创建 SkuRequest**

```java
package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuRequest {

    private String skuCode;

    @NotBlank(message = "规格不能为空")
    private String specs;

    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
}
```

- [ ] **步骤 2：CartAddRequest 加 skuId**

在 `CartAddRequest.java` 的 `quantity` 字段之后添加：

```java
private Long skuId;
```

- [ ] **步骤 3：OrderItemResponse 加 skuId 和 skuSpecs，添加 all-args 构造函数适配**

在 `OrderItemResponse.java` 中添加字段，并把 `@AllArgsConstructor` 换成显式两构造函数：

```java
package com.tianji.mall.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {

    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private Long skuId;
    private String skuSpecs;

    // 无 SKU 的简洁构造（向后兼容现有调用方）
    public OrderItemResponse(Long productId, String productName, BigDecimal price, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    // 含 SKU 的完整构造
    public OrderItemResponse(Long productId, String productName, BigDecimal price, Integer quantity,
                             Long skuId, String skuSpecs) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.skuId = skuId;
        this.skuSpecs = skuSpecs;
    }
}
```

- [ ] **步骤 4：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。OrderService.getOrderDetail 中现有 4 参数构造调用编译通过。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/dto/SkuRequest.java mall-goods-order/src/main/java/com/tianji/mall/dto/CartAddRequest.java mall-goods-order/src/main/java/com/tianji/mall/dto/OrderItemResponse.java
git commit -m "feat: add SkuRequest DTO, extend CartAddRequest and OrderItemResponse for SKU"
```

---

### 任务 6：新建 ProductSkuService（TDD）

**文件：**
- 新建：`mall-goods-order/src/test/java/com/tianji/mall/service/ProductSkuServiceTest.java`
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductSkuService.java`

- [ ] **步骤 1：编写失败的单元测试**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSkuServiceTest {

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Mock
    private ProductService productService;

    private ProductSkuService skuService;

    @BeforeEach
    void setUp() {
        skuService = new ProductSkuService(productSkuMapper, productService);
    }

    // ==================== CRUD ====================

    @Test
    void shouldCreateSku() {
        when(productSkuMapper.insert(any(ProductSku.class))).thenReturn(1);

        ProductSku sku = skuService.create(1L, "颜色:红;尺寸:XL", "SKU001", BigDecimal.valueOf(199), 100);

        assertThat(sku.getProductId()).isEqualTo(1L);
        assertThat(sku.getSpecs()).isEqualTo("颜色:红;尺寸:XL");
        assertThat(sku.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(199));
    }

    @Test
    void shouldListSkusByProductId() {
        ProductSku sku1 = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 10);
        ProductSku sku2 = buildSku(2L, 1L, "颜色:蓝", BigDecimal.valueOf(199), 5);
        when(productSkuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sku1, sku2));

        List<ProductSku> skus = skuService.listByProductId(1L);

        assertThat(skus).hasSize(2);
    }

    @Test
    void shouldUpdateSku() {
        ProductSku existing = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 10);
        when(productSkuMapper.selectById(1L)).thenReturn(existing);
        when(productSkuMapper.updateById(existing)).thenReturn(1);

        skuService.update(1L, 1L, "颜色:红;尺寸:XL", "SKU001", BigDecimal.valueOf(299), 20);

        assertThat(existing.getSpecs()).isEqualTo("颜色:红;尺寸:XL");
        assertThat(existing.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(299));
        assertThat(existing.getStock()).isEqualTo(20);
    }

    @Test
    void shouldThrowWhenUpdateNonExistentSku() {
        when(productSkuMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> skuService.update(1L, 999L, "x", null, null, 0))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU不存在");
    }

    @Test
    void shouldDeleteSkuWhenStockIsZero() {
        ProductSku sku = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 0);
        when(productSkuMapper.selectById(1L)).thenReturn(sku);
        when(productSkuMapper.deleteById(1L)).thenReturn(1);

        skuService.delete(1L, 1L);

        verify(productSkuMapper).deleteById(1L);
    }

    @Test
    void shouldThrowWhenDeleteSkuWithStock() {
        ProductSku sku = buildSku(1L, 1L, "颜色:红", BigDecimal.valueOf(199), 10);
        when(productSkuMapper.selectById(1L)).thenReturn(sku);

        assertThatThrownBy(() -> skuService.delete(1L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("库存不为0，无法删除SKU");
    }

    // ==================== 库存操作 ====================

    @Test
    void shouldDeductStockSuccessfully() {
        when(productSkuMapper.deductStock(1L, 3)).thenReturn(1);

        skuService.deductStock(1L, 1L, 3);

        verify(productSkuMapper).deductStock(1L, 3);
    }

    @Test
    void shouldThrowWhenDeductStockInsufficient() {
        when(productSkuMapper.deductStock(1L, 100)).thenReturn(0);

        assertThatThrownBy(() -> skuService.deductStock(1L, 1L, 100))
                .isInstanceOf(BizException.class)
                .hasMessage("SKU库存不足");
    }

    @Test
    void shouldRestoreStock() {
        when(productSkuMapper.restoreStock(1L, 2)).thenReturn(1);

        skuService.restoreStock(1L, 1L, 2);

        verify(productSkuMapper).restoreStock(1L, 2);
    }

    // ==================== helpers ====================

    private ProductSku buildSku(Long id, Long productId, String specs, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setProductId(productId);
        sku.setSpecs(specs);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);
        return sku;
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn test -pl mall-goods-order -Dtest=ProductSkuServiceTest
```

预期：编译失败（ProductSkuService 类不存在）。

- [ ] **步骤 3：实现 ProductSkuService**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuService extends ServiceImpl<ProductSkuMapper, ProductSku> {

    private final ProductSkuMapper productSkuMapper;
    private final ProductService productService;

    public List<ProductSku> listByProductId(Long productId) {
        return list(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    @CacheEvict(value = "product", key = "#productId")
    @Transactional
    public ProductSku create(Long productId, String specs, String skuCode, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecs(specs);
        sku.setSkuCode(skuCode);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);
        save(sku);
        evictProductCache(productId);
        log.info("SKU创建成功: productId={}, specs={}, stock={}", productId, specs, stock);
        return sku;
    }

    @CacheEvict(value = "product", key = "#productId")
    @Transactional
    public void update(Long productId, Long skuId, String specs, String skuCode, BigDecimal price, Integer stock) {
        ProductSku sku = getById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BizException("SKU不存在");
        }
        if (specs != null) sku.setSpecs(specs);
        if (skuCode != null) sku.setSkuCode(skuCode);
        if (price != null) sku.setPrice(price);
        if (stock != null) sku.setStock(stock);
        updateById(sku);
        evictProductCache(productId);
    }

    @CacheEvict(value = "product", key = "#productId")
    @Transactional
    public void delete(Long productId, Long skuId) {
        ProductSku sku = getById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BizException("SKU不存在");
        }
        if (sku.getStock() > 0) {
            throw new BizException("库存不为0，无法删除SKU");
        }
        removeById(skuId);
        evictProductCache(productId);
    }

    // ==================== 库存操作 ====================

    public void deductStock(Long productId, Long skuId, int qty) {
        int rows = productSkuMapper.deductStock(skuId, qty);
        if (rows == 0) {
            throw new BizException("SKU库存不足");
        }
    }

    public void restoreStock(Long productId, Long skuId, int qty) {
        int rows = productSkuMapper.restoreStock(skuId, qty);
        if (rows == 0) {
            log.warn("恢复SKU库存失败: skuId={}", skuId);
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=ProductSkuServiceTest
```

预期：10 tests PASS。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/ProductSkuService.java mall-goods-order/src/test/java/com/tianji/mall/service/ProductSkuServiceTest.java
git commit -m "feat: add ProductSkuService with CRUD and atomic stock operations"
```

---

### 任务 7：新建 ProductAttributeService（TDD）

**文件：**
- 新建：`mall-goods-order/src/test/java/com/tianji/mall/service/ProductAttributeServiceTest.java`
- 新建：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductAttributeService.java`

- [ ] **步骤 1：编写失败的单元测试**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.mapper.ProductAttributeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAttributeServiceTest {

    @Mock
    private ProductAttributeMapper attributeMapper;

    private ProductAttributeService attributeService;

    @BeforeEach
    void setUp() {
        attributeService = new ProductAttributeService(attributeMapper);
    }

    @Test
    void shouldCreateAttribute() {
        when(attributeMapper.insert(any(ProductAttribute.class))).thenReturn(1);

        ProductAttribute attr = attributeService.create(1L, "屏幕尺寸", "6.1英寸", 0);

        assertThat(attr.getProductId()).isEqualTo(1L);
        assertThat(attr.getName()).isEqualTo("屏幕尺寸");
        assertThat(attr.getValue()).isEqualTo("6.1英寸");
    }

    @Test
    void shouldListAttributesByProductId() {
        ProductAttribute attr1 = buildAttr(1L, 1L, "屏幕尺寸", "6.1英寸");
        ProductAttribute attr2 = buildAttr(2L, 1L, "电池容量", "4000mAh");
        when(attributeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(attr1, attr2));

        List<ProductAttribute> attrs = attributeService.listByProductId(1L);

        assertThat(attrs).hasSize(2);
    }

    @Test
    void shouldUpdateAttribute() {
        ProductAttribute existing = buildAttr(1L, 1L, "屏幕尺寸", "6.1英寸");
        when(attributeMapper.selectById(1L)).thenReturn(existing);
        when(attributeMapper.updateById(existing)).thenReturn(1);

        attributeService.update(1L, 1L, "屏幕尺寸", "6.7英寸", 1);

        assertThat(existing.getValue()).isEqualTo("6.7英寸");
        assertThat(existing.getSort()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenUpdateNonExistentAttribute() {
        when(attributeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> attributeService.update(1L, 999L, "x", "y", 0))
                .isInstanceOf(BizException.class)
                .hasMessage("属性不存在");
    }

    @Test
    void shouldDeleteAttribute() {
        ProductAttribute attr = buildAttr(1L, 1L, "屏幕尺寸", "6.1英寸");
        when(attributeMapper.selectById(1L)).thenReturn(attr);
        when(attributeMapper.deleteById(1L)).thenReturn(1);

        attributeService.delete(1L, 1L);

        verify(attributeMapper).deleteById(1L);
    }

    private ProductAttribute buildAttr(Long id, Long productId, String name, String value) {
        ProductAttribute attr = new ProductAttribute();
        attr.setId(id);
        attr.setProductId(productId);
        attr.setName(name);
        attr.setValue(value);
        attr.setSort(0);
        return attr;
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
mvn test -pl mall-goods-order -Dtest=ProductAttributeServiceTest
```

预期：编译失败（ProductAttributeService 类不存在）。

- [ ] **步骤 3：实现 ProductAttributeService**

```java
package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.mapper.ProductAttributeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductAttributeService extends ServiceImpl<ProductAttributeMapper, ProductAttribute> {

    private final ProductAttributeMapper attributeMapper;

    public List<ProductAttribute> listByProductId(Long productId) {
        return list(new LambdaQueryWrapper<ProductAttribute>()
                .eq(ProductAttribute::getProductId, productId)
                .orderByAsc(ProductAttribute::getSort));
    }

    @Transactional
    public ProductAttribute create(Long productId, String name, String value, int sort) {
        ProductAttribute attr = new ProductAttribute();
        attr.setProductId(productId);
        attr.setName(name);
        attr.setValue(value);
        attr.setSort(sort);
        save(attr);
        return attr;
    }

    @Transactional
    public void update(Long productId, Long attrId, String name, String value, Integer sort) {
        ProductAttribute attr = getById(attrId);
        if (attr == null || !attr.getProductId().equals(productId)) {
            throw new BizException("属性不存在");
        }
        if (name != null) attr.setName(name);
        if (value != null) attr.setValue(value);
        if (sort != null) attr.setSort(sort);
        updateById(attr);
    }

    @Transactional
    public void delete(Long productId, Long attrId) {
        ProductAttribute attr = getById(attrId);
        if (attr == null || !attr.getProductId().equals(productId)) {
            throw new BizException("属性不存在");
        }
        removeById(attrId);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

```bash
mvn test -pl mall-goods-order -Dtest=ProductAttributeServiceTest
```

预期：5 tests PASS。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/ProductAttributeService.java mall-goods-order/src/test/java/com/tianji/mall/service/ProductAttributeServiceTest.java
git commit -m "feat: add ProductAttributeService with CRUD operations"
```

---

### 任务 8：修改 ProductService — getProductById 返回 SKU 列表和属性

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java`

- [ ] **步骤 1：ProductService 注入 ProductSkuService 和 ProductAttributeService**

在 `ProductService.java` 中，将 `@RequiredArgsConstructor` 的依赖改为：

```java
private final AiChatFeignClient aiChatFeignClient;
private final ProductSkuService skuService;
private final ProductAttributeService attributeService;
```

- [ ] **步骤 2：添加返回 SKU+属性的 getProductDetail 方法**

在 ProductService 中添加：

```java
public Map<String, Object> getProductDetail(Long id) {
    Product product = getProductById(id);
    List<ProductSku> skus = skuService.listByProductId(id);
    List<ProductAttribute> attrs = attributeService.listByProductId(id);
    return Map.of("product", product, "skus", skus, "attributes", attrs);
}
```

需要添加 import：
```java
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.entity.ProductAttribute;
```

- [ ] **步骤 3：修改 ProductController.detail 返回 SKU+属性**

在 `ProductController.java` 中修改 `detail` 方法：

```java
@GetMapping("/{id}")
public R<Map<String, Object>> detail(@PathVariable("id") Long id) {
    return R.ok(productService.getProductDetail(id));
}
```

添加 import：
```java
import java.util.Map;
```

- [ ] **步骤 4：更新 ProductControllerTest**

修改 `shouldGetProductDetail` 测试：

```java
@Test
void shouldGetProductDetail() throws Exception {
    Product product = buildProduct(1L, "iPhone", 6999);
    when(productService.getProductDetail(1L))
            .thenReturn(Map.of("product", product, "skus", List.of(), "attributes", List.of()));

    mockMvc.perform(get("/api/product/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.product.name").value("iPhone"))
            .andExpect(jsonPath("$.data.product.price").value(6999));
}
```

修改 `shouldReturnErrorWhenProductNotFound`：

```java
@Test
void shouldReturnErrorWhenProductNotFound() throws Exception {
    when(productService.getProductDetail(999L))
            .thenThrow(new BizException("商品不存在或已下架"));

    mockMvc.perform(get("/api/product/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value("商品不存在或已下架"));
}
```

需要添加 import：
```java
import java.util.List;
import java.util.Map;
```

- [ ] **步骤 5：编译验证**

```bash
mvn compile -pl mall-goods-order
```

预期：BUILD SUCCESS。

- [ ] **步骤 6：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=ProductControllerTest
```

预期：5 tests PASS。

- [ ] **步骤 7：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/ProductService.java mall-goods-order/src/main/java/com/tianji/mall/controller/ProductController.java mall-goods-order/src/test/java/com/tianji/mall/controller/ProductControllerTest.java
git commit -m "feat: product detail returns SKU list and attributes"
```

---

### 任务 9：修改 CartService — 支持 skuId 加购

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/CartService.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/service/CartServiceTest.java`

- [ ] **步骤 1：注入 ProductSkuService**

在 `CartService.java` 中添加依赖：

```java
private final ProductService productService;
private final ProductSkuService skuService;
```

- [ ] **步骤 2：修改 addItem 方法支持 skuId**

将 `addItem` 方法改为：

```java
@Transactional
public void addItem(Long userId, CartAddRequest req) {
    Product product = productService.getProductById(req.getProductId());

    // SKU 商品：校验 SKU 存在 + 库存
    if (req.getSkuId() != null) {
        ProductSku sku = skuService.getById(req.getSkuId());
        if (sku == null || !sku.getProductId().equals(req.getProductId())) {
            throw new BizException("SKU不存在");
        }
        if (sku.getStock() < req.getQuantity()) {
            throw new BizException("库存不足");
        }
    } else {
        // 无 SKU：使用商品级库存（向后兼容）
        if (product.getStock() < req.getQuantity()) {
            throw new BizException("库存不足");
        }
    }

    // 去重：productId + skuId 相同则合并数量
    LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<CartItem>()
            .eq(CartItem::getUserId, userId)
            .eq(CartItem::getProductId, req.getProductId());
    if (req.getSkuId() != null) {
        wrapper.eq(CartItem::getSkuId, req.getSkuId());
    } else {
        wrapper.isNull(CartItem::getSkuId);
    }
    CartItem existing = getOne(wrapper);
    if (existing != null) {
        existing.setQuantity(existing.getQuantity() + req.getQuantity());
        updateById(existing);
        return;
    }

    CartItem item = new CartItem();
    item.setUserId(userId);
    item.setProductId(req.getProductId());
    item.setSkuId(req.getSkuId());
    item.setQuantity(req.getQuantity());
    item.setChecked(1);
    save(item);
    log.info("加购成功: userId={}, productId={}, skuId={}, quantity={}",
            userId, req.getProductId(), req.getSkuId(), req.getQuantity());
}
```

需要添加 import：
```java
import com.tianji.mall.entity.ProductSku;
```

- [ ] **步骤 3：更新 CartServiceTest**

修改 `shouldAddNewCartItem` 测试——验证无 skuId 的场景仍正常工作：

```java
@Test
void shouldAddNewCartItem() {
    CartAddRequest req = new CartAddRequest();
    req.setProductId(1L);
    req.setQuantity(2);
    // skuId = null（无 SKU 商品，向后兼容）

    Product product = buildProduct(1L, "iPhone", 10);
    when(productService.getProductById(1L)).thenReturn(product);
    when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    cartService.addItem(1L, req);
}
```

添加新测试——有 skuId 的加购：

```java
@Test
void shouldAddCartItemWithSku() {
    CartAddRequest req = new CartAddRequest();
    req.setProductId(1L);
    req.setSkuId(10L);
    req.setQuantity(1);

    Product product = buildProduct(1L, "iPhone", 10);
    ProductSku sku = buildSku(10L, 1L, "颜色:红", 5);
    when(productService.getProductById(1L)).thenReturn(product);
    when(skuService.getById(10L)).thenReturn(sku);
    when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

    cartService.addItem(1L, req);
}

@Test
void shouldThrowWhenSkuNotFound() {
    CartAddRequest req = new CartAddRequest();
    req.setProductId(1L);
    req.setSkuId(999L);
    req.setQuantity(1);

    Product product = buildProduct(1L, "iPhone", 10);
    when(productService.getProductById(1L)).thenReturn(product);
    when(skuService.getById(999L)).thenReturn(null);

    assertThatThrownBy(() -> cartService.addItem(1L, req))
            .isInstanceOf(BizException.class)
            .hasMessage("SKU不存在");
}

@Test
void shouldThrowWhenSkuStockInsufficient() {
    CartAddRequest req = new CartAddRequest();
    req.setProductId(1L);
    req.setSkuId(10L);
    req.setQuantity(10);

    Product product = buildProduct(1L, "iPhone", 20);
    ProductSku sku = buildSku(10L, 1L, "颜色:红", 3); // SKU 只有 3 件
    when(productService.getProductById(1L)).thenReturn(product);
    when(skuService.getById(10L)).thenReturn(sku);

    assertThatThrownBy(() -> cartService.addItem(1L, req))
            .isInstanceOf(BizException.class)
            .hasMessage("库存不足");
}

@Test
void shouldMergeCartItemWithSameSku() {
    CartAddRequest req = new CartAddRequest();
    req.setProductId(1L);
    req.setSkuId(10L);
    req.setQuantity(1);

    Product product = buildProduct(1L, "iPhone", 10);
    ProductSku sku = buildSku(10L, 1L, "颜色:红", 5);
    CartItem existing = buildCartItem(1L, 1L, 1L, 2);
    existing.setSkuId(10L);

    when(productService.getProductById(1L)).thenReturn(product);
    when(skuService.getById(10L)).thenReturn(sku);
    when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
    when(cartItemMapper.updateById(existing)).thenReturn(1);

    cartService.addItem(1L, req);

    assertThat(existing.getQuantity()).isEqualTo(3);
}

private ProductSku buildSku(Long id, Long productId, String specs, int stock) {
    ProductSku sku = new ProductSku();
    sku.setId(id);
    sku.setProductId(productId);
    sku.setSpecs(specs);
    sku.setPrice(BigDecimal.valueOf(199));
    sku.setStock(stock);
    sku.setStatus(1);
    return sku;
}
```

需要添加 import：
```java
import com.tianji.mall.entity.ProductSku;
import java.math.BigDecimal;
```

并在 `@Mock` 区域添加：
```java
@Mock
private ProductSkuService skuService;
```

构造函数改为：
```java
cartService = new CartService(productService, skuService);
```

- [ ] **步骤 4：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=CartServiceTest
```

预期：9 tests PASS。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/CartService.java mall-goods-order/src/test/java/com/tianji/mall/service/CartServiceTest.java
git commit -m "feat: CartService supports skuId in addItem with SKU-level stock check and dedup"
```

---

### 任务 10：修改 OrderService — 支持 SKU 级别下单、锁、价格、库存

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceTest.java`

- [ ] **步骤 1：注入 ProductSkuService**

在 `OrderService.java` 中添加：

```java
private final ProductSkuService skuService;
```

构造器参数变为：
```java
public OrderService(OrderItemMapper orderItemMapper, CartService cartService,
                    ProductService productService, AddressService addressService,
                    CouponService couponService, ProductSkuService skuService,
                    RedissonClient redissonClient, RocketMQTemplate rocketMQTemplate) {
```

- [ ] **步骤 2：修改 createOrder 中价格+库存+锁+扣减逻辑**

在 `createOrder` 方法中，将步骤 5（校验库存并计算金额）的核心循环改为：

```java
// 5. 校验库存并计算金额
BigDecimal totalAmount = BigDecimal.ZERO;
List<OrderItem> orderItems = new ArrayList<>();
for (CartItem cartItem : cartItems) {
    Product product = productMap.get(cartItem.getProductId());
    if (product == null || product.getStatus() == 0) {
        throw new BizException("商品「" + (product != null ? product.getName() : "未知") + "」已下架");
    }

    BigDecimal itemPrice;
    String skuSpecs = null;

    if (cartItem.getSkuId() != null) {
        // SKU 商品：用 SKU 价格和库存
        ProductSku sku = skuService.getById(cartItem.getSkuId());
        if (sku == null || !sku.getProductId().equals(cartItem.getProductId())) {
            throw new BizException("商品「" + product.getName() + "」的规格已失效");
        }
        if (sku.getStock() < cartItem.getQuantity()) {
            throw new BizException("商品「" + product.getName() + "」库存不足");
        }
        itemPrice = sku.getPrice() != null ? sku.getPrice() : product.getPrice();
        skuSpecs = sku.getSpecs();
    } else {
        // 无 SKU：用商品级价格和库存（向后兼容）
        if (product.getStock() < cartItem.getQuantity()) {
            throw new BizException("商品「" + product.getName() + "」库存不足");
        }
        itemPrice = product.getPrice();
    }

    OrderItem orderItem = new OrderItem();
    orderItem.setProductId(product.getId());
    orderItem.setProductName(product.getName());
    orderItem.setPrice(itemPrice);
    orderItem.setQuantity(cartItem.getQuantity());
    orderItem.setSkuId(cartItem.getSkuId());
    orderItem.setSkuSpecs(skuSpecs);
    orderItems.add(orderItem);

    totalAmount = totalAmount.add(itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
}
```

需要添加 import：
```java
import com.tianji.mall.entity.ProductSku;
```

- [ ] **步骤 3：修改步骤 10 — SKU 库存扣减**

```java
// 10. 原子扣库存
for (OrderItem item : orderItems) {
    if (item.getSkuId() != null) {
        skuService.deductStock(item.getProductId(), item.getSkuId(), item.getQuantity());
    } else {
        productService.deductStock(item.getProductId(), item.getQuantity());
    }
}
```

**注意：** `skuService.deductStock` 需要通过 ProductSkuMapper 获取 productId 来构造 CacheEvict 的 key。修改 `ProductSkuService.deductStock` 方法——需要先查 SKU 拿到 productId：

```java
@CacheEvict(value = "product", key = "#productId")
public void deductStock(Long skuId, int qty) {
    int rows = productSkuMapper.deductStock(skuId, qty);
    if (rows == 0) {
        throw new BizException("SKU库存不足");
    }
}
```

但 CacheEvict 的 key 需要 productId，而 deductStock 只知道 skuId。需要改为先查 SKU：

```java
public void deductStock(Long skuId, int qty) {
    int rows = productSkuMapper.deductStock(skuId, qty);
    if (rows == 0) {
        throw new BizException("SKU库存不足");
    }
}

public void deductStockWithCacheEvict(Long skuId, int qty) {
    ProductSku sku = getById(skuId);
    deductStock(skuId, qty);
    // 手动清除 product 缓存
}
```

简化方案：直接用 `productService` 的缓存逐出模式——在 OrderService 中调用 `skuService.deductStock` 后，不依赖 CacheEvict 注解，而是在扣减循环中统一处理。

保持简单：`ProductSkuService.deductStock` 改为不带 `@CacheEvict`，改为在调用方（OrderService）中使用 `@CacheEvict`。

实际上最简单的是直接在 ProductSkuService 中去掉 CacheEvict 注解，sku 的扣减不涉及 product 缓存清理（因为 product 缓存存的是商品基本信息，不包含 SKU 库存）。

- [ ] **步骤 4：修改 cancelOrder 和 cancelOrderByTimeout — SKU 库存恢复**

在 `cancelOrder` 和 `cancelOrderByTimeout` 的库存恢复循环中：

```java
for (OrderItem item : items) {
    if (item.getSkuId() != null) {
        skuService.restoreStock(item.getProductId(), item.getSkuId(), item.getQuantity());
    } else {
        productService.restoreStock(item.getProductId(), item.getQuantity());
    }
}
```

- [ ] **步骤 5：修改分布式锁键——SKU 订单加 SKU 锁**

在步骤 3.5（获取分布式锁），将锁键从纯 product 改为按 skuId 区分：

```java
// 3.5 获取分布式锁（按 productId 和 skuId 排序，避免死锁）
List<String> lockKeyStrings = new ArrayList<>();
for (CartItem cartItem : cartItems) {
    if (cartItem.getSkuId() != null) {
        lockKeyStrings.add("lock:sku:" + cartItem.getSkuId());
    } else {
        lockKeyStrings.add("lock:product:" + cartItem.getProductId());
    }
}
List<String> sortedLockKeys = lockKeyStrings.stream().sorted().distinct().toList();
RLock[] lockArray = sortedLockKeys.stream()
        .map(key -> redissonClient.getLock(key))
        .toArray(RLock[]::new);
RLock multiLock = redissonClient.getMultiLock(lockArray);
```

- [ ] **步骤 6：修改 getOrderDetail — 返回 SKU 信息**

```java
List<OrderItemResponse> itemResponses = orderItems.stream()
        .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getPrice(), i.getQuantity(),
                i.getSkuId(), i.getSkuSpecs()))
        .toList();
```

- [ ] **步骤 7：更新 OrderServiceTest**

修改 `setUp` 构造函数：

```java
@Mock
private ProductSkuService skuService;

@BeforeEach
void setUp() throws InterruptedException {
    orderService = new OrderService(orderItemMapper, cartService, productService, addressService, couponService, skuService, redissonClient, rocketMQTemplate);
    ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
    // ... lock mocks unchanged
}
```

添加 SKU 下单测试：

```java
@Test
void shouldCreateOrderWithSkuItems() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setAddressId(1L);
    req.setCartItemIds(List.of(1L));

    Address addr = buildAddress(1L, 100L);
    CartItem cartItem = buildCartItem(1L, 100L, 1L, 2);
    cartItem.setSkuId(10L);
    Product product = buildProduct(1L, "iPhone", 100, 1);
    ProductSku sku = buildSku(10L, 1L, "颜色:红;容量:256G", BigDecimal.valueOf(7999), 10);

    when(addressService.getById(1L)).thenReturn(addr);
    when(cartService.listByIds(List.of(1L))).thenReturn(List.of(cartItem));
    when(productService.listByIds(List.of(1L))).thenReturn(List.of(product));
    when(skuService.getById(10L)).thenReturn(sku);
    when(orderMapper.insert(any(Order.class))).thenReturn(1);
    when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);

    Order order = orderService.createOrder(100L, req);

    assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(15998));
    verify(skuService).deductStock(1L, 10L, 2);
}

@Test
void shouldThrowWhenSkuStockInsufficientForOrder() {
    OrderCreateRequest req = new OrderCreateRequest();
    req.setAddressId(1L);
    req.setCartItemIds(List.of(1L));

    Address addr = buildAddress(1L, 100L);
    CartItem cartItem = buildCartItem(1L, 100L, 1L, 5);
    cartItem.setSkuId(10L);
    Product product = buildProduct(1L, "iPhone", 100, 1);
    ProductSku sku = buildSku(10L, 1L, "颜色:红", BigDecimal.valueOf(199), 3); // SKU 只有 3 件

    when(addressService.getById(1L)).thenReturn(addr);
    when(cartService.listByIds(List.of(1L))).thenReturn(List.of(cartItem));
    when(productService.listByIds(List.of(1L))).thenReturn(List.of(product));
    when(skuService.getById(10L)).thenReturn(sku);

    assertThatThrownBy(() -> orderService.createOrder(100L, req))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("库存不足");
}

private ProductSku buildSku(Long id, Long productId, String specs, BigDecimal price, int stock) {
    ProductSku sku = new ProductSku();
    sku.setId(id);
    sku.setProductId(productId);
    sku.setSpecs(specs);
    sku.setPrice(price);
    sku.setStock(stock);
    sku.setStatus(1);
    return sku;
}
```

需要添加 import：
```java
import com.tianji.mall.entity.ProductSku;
```

修改 `shouldCancelOrder` 验证——确认 SKU 库存恢复被调用（需要让 OrderItem 带 skuId），或新增一个测试：

```java
@Test
void shouldCancelOrderWithSkuAndRestoreSkuStock() {
    Order order = buildOrder(1L, 100L, 1);
    when(orderMapper.selectById(1L)).thenReturn(order);
    when(orderMapper.updateById(order)).thenReturn(1);
    OrderItem item = buildOrderItem(1L, 1L, 1L, 2);
    item.setSkuId(10L);
    when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

    orderService.cancelOrder(100L, 1L);

    verify(skuService).restoreStock(1L, 10L, 2);
}
```

- [ ] **步骤 8：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=OrderServiceTest
```

预期：约 24 tests PASS（原有 21 + 新增 3）。

- [ ] **步骤 9：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/service/OrderService.java mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceTest.java
git commit -m "feat: OrderService supports SKU-level pricing, stock check, lock, and deduction"
```

---

### 任务 11：AdminController — 新增 SKU 和属性管理端点

**文件：**
- 修改：`mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java`
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java`

- [ ] **步骤 1：AdminController 注入新服务**

在 `AdminController.java` 中添加：

```java
private final ProductSkuService skuService;
private final ProductAttributeService attributeService;
```

需要添加 import：
```java
import com.tianji.mall.dto.SkuRequest;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.service.ProductSkuService;
import com.tianji.mall.service.ProductAttributeService;
import jakarta.validation.Valid;
```

- [ ] **步骤 2：添加 SKU 管理端点**

在 AdminController 末尾（优惠券管理之后）添加：

```java
// ==================== SKU 管理 ====================

@GetMapping("/product/{productId}/sku")
public R<List<ProductSku>> listSkus(@PathVariable("productId") Long productId) {
    return R.ok(skuService.listByProductId(productId));
}

@PostMapping("/product/{productId}/sku")
public R<ProductSku> createSku(@PathVariable("productId") Long productId,
                                @Valid @RequestBody SkuRequest req) {
    return R.ok(skuService.create(productId, req.getSpecs(), req.getSkuCode(), req.getPrice(), req.getStock()));
}

@PutMapping("/product/{productId}/sku/{id}")
public R<Void> updateSku(@PathVariable("productId") Long productId,
                          @PathVariable("id") Long id,
                          @Valid @RequestBody SkuRequest req) {
    skuService.update(productId, id, req.getSpecs(), req.getSkuCode(), req.getPrice(), req.getStock());
    return R.ok();
}

@DeleteMapping("/product/{productId}/sku/{id}")
public R<Void> deleteSku(@PathVariable("productId") Long productId,
                          @PathVariable("id") Long id) {
    skuService.delete(productId, id);
    return R.ok();
}

// ==================== 属性管理 ====================

@GetMapping("/product/{productId}/attribute")
public R<List<ProductAttribute>> listAttributes(@PathVariable("productId") Long productId) {
    return R.ok(attributeService.listByProductId(productId));
}

@PostMapping("/product/{productId}/attribute")
public R<ProductAttribute> createAttribute(@PathVariable("productId") Long productId,
                                            @RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    String value = (String) body.get("value");
    int sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0;
    return R.ok(attributeService.create(productId, name, value, sort));
}

@PutMapping("/product/{productId}/attribute/{id}")
public R<Void> updateAttribute(@PathVariable("productId") Long productId,
                                @PathVariable("id") Long id,
                                @RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    String value = (String) body.get("value");
    Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : null;
    attributeService.update(productId, id, name, value, sort);
    return R.ok();
}

@DeleteMapping("/product/{productId}/attribute/{id}")
public R<Void> deleteAttribute(@PathVariable("productId") Long productId,
                                @PathVariable("id") Long id) {
    attributeService.delete(productId, id);
    return R.ok();
}
```

- [ ] **步骤 3：更新 AdminControllerTest**

添加 @MockBean：
```java
@MockBean
private ProductSkuService skuService;

@MockBean
private ProductAttributeService attributeService;
```

添加 import：
```java
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.service.ProductSkuService;
import com.tianji.mall.service.ProductAttributeService;
import java.math.BigDecimal;
```

添加 8 个新测试方法：

```java
// ==================== GET /api/admin/product/{productId}/sku ====================

@Test
void shouldListSkus() throws Exception {
    when(skuService.listByProductId(1L)).thenReturn(List.of());

    mockMvc.perform(get("/api/admin/product/1/sku")
                    .header("X-User-Role", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

// ==================== POST /api/admin/product/{productId}/sku ====================

@Test
void shouldCreateSku() throws Exception {
    ProductSku sku = new ProductSku();
    sku.setId(1L);
    sku.setSpecs("颜色:红;尺寸:XL");
    when(skuService.create(eq(1L), eq("颜色:红;尺寸:XL"), eq("SKU001"), any(), eq(100)))
            .thenReturn(sku);

    mockMvc.perform(post("/api/admin/product/1/sku")
                    .header("X-User-Role", "admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"specs\":\"颜色:红;尺寸:XL\",\"skuCode\":\"SKU001\",\"price\":199.9,\"stock\":100}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.specs").value("颜色:红;尺寸:XL"));
}

@Test
void shouldReturn400OnMissingSkuSpecs() throws Exception {
    mockMvc.perform(post("/api/admin/product/1/sku")
                    .header("X-User-Role", "admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"price\":199.9,\"stock\":100}"))
            .andExpect(status().isBadRequest());
}

// ==================== PUT /api/admin/product/{productId}/sku/{id} ====================

@Test
void shouldUpdateSku() throws Exception {
    mockMvc.perform(put("/api/admin/product/1/sku/10")
                    .header("X-User-Role", "admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"specs\":\"颜色:蓝\",\"stock\":50}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

// ==================== DELETE /api/admin/product/{productId}/sku/{id} ====================

@Test
void shouldDeleteSku() throws Exception {
    mockMvc.perform(delete("/api/admin/product/1/sku/10")
                    .header("X-User-Role", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

// ==================== GET /api/admin/product/{productId}/attribute ====================

@Test
void shouldListAttributes() throws Exception {
    when(attributeService.listByProductId(1L)).thenReturn(List.of());

    mockMvc.perform(get("/api/admin/product/1/attribute")
                    .header("X-User-Role", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

// ==================== POST /api/admin/product/{productId}/attribute ====================

@Test
void shouldCreateAttribute() throws Exception {
    ProductAttribute attr = new ProductAttribute();
    attr.setId(1L);
    attr.setName("屏幕尺寸");
    attr.setValue("6.1英寸");
    when(attributeService.create(eq(1L), eq("屏幕尺寸"), eq("6.1英寸"), eq(0)))
            .thenReturn(attr);

    mockMvc.perform(post("/api/admin/product/1/attribute")
                    .header("X-User-Role", "admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"屏幕尺寸\",\"value\":\"6.1英寸\",\"sort\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("屏幕尺寸"));
}

// ==================== DELETE /api/admin/product/{productId}/attribute/{id} ====================

@Test
void shouldDeleteAttribute() throws Exception {
    mockMvc.perform(delete("/api/admin/product/1/attribute/5")
                    .header("X-User-Role", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}
```

- [ ] **步骤 4：运行测试**

```bash
mvn test -pl mall-goods-order -Dtest=AdminControllerTest
```

预期：约 31 tests PASS（原有 23 + 新增 8）。

- [ ] **步骤 5：Commit**

```bash
git add mall-goods-order/src/main/java/com/tianji/mall/controller/AdminController.java mall-goods-order/src/test/java/com/tianji/mall/controller/AdminControllerTest.java
git commit -m "feat: add SKU and attribute CRUD endpoints to AdminController"
```

---

### 任务 12：集成测试 — OrderServiceIntegrationTest 含 SKU 下单场景

**文件：**
- 修改：`mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceIntegrationTest.java`

- [ ] **步骤 1：添加 SKU 下单集成测试**

在 `OrderServiceIntegrationTest` 中添加：

```java
@Autowired
private ProductSkuMapper skuMapper;

@Autowired
private ProductAttributeMapper attributeMapper;

@BeforeEach
void setUp() throws InterruptedException {
    // ... 现有清理逻辑 ...
    skuMapper.delete(new LambdaQueryWrapper<>());
    attributeMapper.delete(new LambdaQueryWrapper<>());
    // ...
}

@Test
void shouldCreateOrderWithSkuAndDeductSkuStock() {
    // 插入 SKU
    ProductSku sku = new ProductSku();
    sku.setProductId(productId);
    sku.setSpecs("颜色:红;容量:256G");
    sku.setPrice(BigDecimal.valueOf(7999));
    sku.setStock(5);
    sku.setStatus(1);
    skuMapper.insert(sku);

    // 购物车加 SKU
    CartItem cartItem = new CartItem();
    cartItem.setUserId(1L);
    cartItem.setProductId(productId);
    cartItem.setSkuId(sku.getId());
    cartItem.setQuantity(2);
    cartItem.setChecked(1);
    cartItemMapper.insert(cartItem);

    OrderCreateRequest req = buildCreateRequest(addressId, List.of(cartItem.getId()));

    Order order = orderService.createOrder(1L, req);

    assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(15998));

    // SKU 库存已扣减
    ProductSku updatedSku = skuMapper.selectById(sku.getId());
    assertThat(updatedSku.getStock()).isEqualTo(3);

    // order_item 记录了 SKU 信息
    List<OrderItem> items = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
    assertThat(items.get(0).getSkuId()).isEqualTo(sku.getId());
    assertThat(items.get(0).getSkuSpecs()).isEqualTo("颜色:红;容量:256G");
}

@Test
void shouldCancelOrderWithSkuAndRestoreSkuStock() {
    ProductSku sku = new ProductSku();
    sku.setProductId(productId);
    sku.setSpecs("颜色:蓝");
    sku.setPrice(BigDecimal.valueOf(5999));
    sku.setStock(10);
    sku.setStatus(1);
    skuMapper.insert(sku);

    CartItem cartItem = new CartItem();
    cartItem.setUserId(1L);
    cartItem.setProductId(productId);
    cartItem.setSkuId(sku.getId());
    cartItem.setQuantity(3);
    cartItem.setChecked(1);
    cartItemMapper.insert(cartItem);

    OrderCreateRequest req = buildCreateRequest(addressId, List.of(cartItem.getId()));
    Order order = orderService.createOrder(1L, req);

    orderService.cancelOrder(1L, order.getId());

    ProductSku restoredSku = skuMapper.selectById(sku.getId());
    assertThat(restoredSku.getStock()).isEqualTo(10);
}
```

需要添加 import：
```java
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.ProductSkuMapper;
import com.tianji.mall.mapper.ProductAttributeMapper;
```

- [ ] **步骤 2：运行集成测试**

```bash
mvn test -pl mall-goods-order -Dtest=OrderServiceIntegrationTest
```

预期：5 tests PASS（原有 3 + 新增 2）。

- [ ] **步骤 3：Commit**

```bash
git add mall-goods-order/src/test/java/com/tianji/mall/service/OrderServiceIntegrationTest.java
git commit -m "test: add SKU order integration tests for create and cancel scenarios"
```

---

### 任务 13：全模块回归测试

- [ ] **步骤 1：运行全模块测试**

```bash
mvn test -pl mall-goods-order
```

预期：所有测试通过，全绿。

- [ ] **步骤 2：确认全模块编译**

```bash
mvn compile
```

预期：8 模块 BUILD SUCCESS。

- [ ] **步骤 3：Commit（如有变更）**

```bash
git add -A
git commit -m "chore: finalize SKU implementation, all tests pass"
```

---

## 测试覆盖汇总

| 测试类 | 操作 | 测试数 |
|--------|------|--------|
| ProductSkuServiceTest | 新建 | 10 |
| ProductAttributeServiceTest | 新建 | 5 |
| CartServiceTest | 修改 | 原有 7 → 11 (+4) |
| OrderServiceTest | 修改 | 原有 21 → 24 (+3) |
| AdminControllerTest | 修改 | 原有 23 → 31 (+8) |
| ProductControllerTest | 修改 | 原有 5 → 5（逻辑不变） |
| OrderServiceIntegrationTest | 修改 | 原有 3 → 5 (+2) |
| **合计新增** | | **32 个新测试** |

## 验证方式

```bash
# 单模块测试
mvn test -pl mall-goods-order

# 全模块编译 + 测试
mvn test
```

- 所有现有测试（238）继续通过
- 新增 32 个测试全部通过
- 全模块 BUILD SUCCESS
