# 后台管理 + 订单生命周期 + 用户中心 设计文档

> **目标：** 为天机商城补齐后台管理 CRUD（商品/分类管理、订单发货）、订单生命周期完善（发货→确认收货→退款→超时取消）、用户中心完善（修改资料/密码/头像）。

**架构：** 后台管理复用 mall-goods-order 模块（新增 `/api/admin/**` 控制器），管理员角色通过 User 表 `role` 字段区分。超时取消用 RocketMQ 延迟消息。退款走支付宝完整流程。

**技术栈：** Java 17, Spring Boot 3.2.5, MyBatis-Plus 3.5.7, RocketMQ 2.3.1, 支付宝沙盒 SDK

---

## 一、数据库变更

### 1.1 新建表：`category`

```sql
CREATE TABLE IF NOT EXISTS category (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL COMMENT '分类名称',
    parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID，0表示一级分类',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 1.2 新建表：`refund`

```sql
CREATE TABLE IF NOT EXISTS refund (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL COMMENT '订单ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    amount          DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    reason          VARCHAR(500) NOT NULL COMMENT '退款原因',
    status          VARCHAR(20)  NOT NULL DEFAULT 'processing' COMMENT 'processing/success/fail',
    alipay_refund_no VARCHAR(64) COMMENT '支付宝退款单号',
    fail_reason     VARCHAR(500) COMMENT '失败原因',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 1.3 修改表：`user`

```sql
ALTER TABLE `user` ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'user/admin';
ALTER TABLE `user` ADD COLUMN avatar VARCHAR(500) COMMENT '头像URL';
```

### 1.4 修改表：`order`

```sql
ALTER TABLE `order` ADD COLUMN logistics_company VARCHAR(64) COMMENT '物流公司';
ALTER TABLE `order` ADD COLUMN tracking_number  VARCHAR(64) COMMENT '物流单号';
ALTER TABLE `order` ADD COLUMN receive_time     DATETIME    COMMENT '确认收货时间';
```

订单状态流转：
```
PENDING → PAID → SHIPPED → RECEIVED
   ↓         ↓
CANCELLED  REFUNDING → REFUNDED
```

---

## 二、API 设计

### 2.1 后台管理（mall-goods-order `/api/admin/**`）

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/admin/category` | 分类树（含二级） |
| POST | `/api/admin/category` | 新增分类 |
| PUT | `/api/admin/category/{id}` | 编辑分类 |
| DELETE | `/api/admin/category/{id}` | 删除分类（无商品时允许） |
| GET | `/api/admin/product` | 商品列表（分页 + 分类筛选） |
| POST | `/api/admin/product` | 新增商品 |
| PUT | `/api/admin/product/{id}` | 编辑商品 |
| DELETE | `/api/admin/product/{id}` | 删除/下架商品 |
| PUT | `/api/admin/order/{id}/ship` | 发货 |
| PUT | `/api/admin/order/{id}/complete` | 手动完成订单 |

### 2.2 订单生命周期（mall-goods-order `/api/order` 扩展）

| 方法 | 端点 | 说明 |
|------|------|------|
| PUT | `/api/order/{id}/receive` | 用户确认收货 |
| POST | `/api/order/{id}/refund` | 用户申请退款 |

内部端点（供 mcp-server Feign 调用）：

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/order/internal/detail` | 查订单详情（已存在） |
| POST | `/api/refund/internal/query` | 查退款进度 |

### 2.3 用户中心（user-service `/api/user` 扩展）

| 方法 | 端点 | 说明 |
|------|------|------|
| PUT | `/api/user/profile` | 修改昵称/简介 |
| PUT | `/api/user/avatar` | 上传头像（Base64 data URL） |
| PUT | `/api/user/password` | 修改密码（需旧密码验证） |

---

## 三、关键实现细节

### 3.1 Admin 鉴权

Gateway `AuthGlobalFilter`：`/api/admin/**` 路径校验 JWT role claim 为 `admin`，非 admin 返回 403。

mall-goods-order 侧加 AOP 切面（`@RequireAdmin` 注解），从 `SecurityContextHolder` 获取当前用户 role 做二次校验。

不需要新建 admin 表，不需要独立登录——管理员账号预先在 User 表插入、role 手动设为 `admin`。

### 3.2 超时取消

`OrderService.createOrder()` 成功后 → RocketMQTemplate 发送延迟消息：

- Topic: `order-topic`
- Tag: `TIMEOUT_CHECK`
- 延迟级别: 16（约 30 分钟，RocketMQ 默认级别：1=1s 递增至 18=2h，16=30min）
- Body: orderId

消费者 `OrderTimeoutConsumer`：
1. 查订单 status
2. 仍为 `PENDING` → `cancelOrder()`（恢复库存 + 更新状态）
3. 已 `PAID` / 其他 → 无操作

### 3.3 退款流程

```
POST /api/order/{id}/refund
  → 校验订单 status=PAID 且属于当前用户
  → 创建 refund 记录 (status=processing)
  → 调用 AlipayTradeRefundRequest
     → 成功: refund status=success, order status=REFUNDED
     → 失败: refund status=fail + fail_reason
```

参考现有 `PayService` 的支付宝调用模式（`AlipayClient` Bean、`AlipayConfig`）。

### 3.4 头像上传

不引入 OSS。用户传 Base64 编码的图片字符串（最大 512KB），保存到 `user.avatar` 字段。`GET /api/user/info` 返回时带 `avatar` 字段，前端直接渲染 data URL。

### 3.5 分类管理

一级分类 `parent_id=0`，二级分类 `parent_id` 指向一级分类 ID。`GET /api/admin/category` 返回树形结构：

```json
[
  { "id": 1, "name": "电子产品", "children": [
      { "id": 3, "name": "手机", "parentId": 1 },
      { "id": 4, "name": "电脑", "parentId": 1 }
  ]},
  { "id": 2, "name": "服装", "children": [] }
]
```

删除分类时检查该分类下是否有商品（含子分类商品），有则拒绝并提示。

---

## 四、Gateway 白名单更新

`AuthGlobalFilter` 内部路径列表新增 `/api/admin/`（admin 鉴权路径，需 JWT + admin role）。

现有公开路径 `/api/product/**` 不受影响。内部路径 `/api/order/internal/**`、`/api/cart/internal/**` 仍用 `X-Internal-Token`。

---

## 五、受影响文件

| 模块 | 文件 | 操作 |
|------|------|------|
| **mall-goods-order** | `src/main/resources/schema.sql` | 修改：新增 category/refund 表 + order 表字段 |
| | `entity/Category.java` | 创建 |
| | `entity/Refund.java` | 创建 |
| | `entity/Order.java` | 修改：加 logisticsCompany/trackingNumber/receiveTime |
| | `entity/Product.java` | 修改：加 categoryId |
| | `mapper/CategoryMapper.java` | 创建 |
| | `mapper/RefundMapper.java` | 创建 |
| | `service/AdminService.java` + `impl/AdminServiceImpl.java` | 创建 |
| | `service/CategoryService.java` + `impl/CategoryServiceImpl.java` | 创建 |
| | `service/RefundService.java` + `impl/RefundServiceImpl.java` | 创建 |
| | `controller/AdminController.java` | 创建 |
| | `service/OrderService.java` | 修改：加发货/完成/超时取消消费者 |
| | `service/ProductService.java` | 修改：CRUD 扩展 |
| | `annotation/RequireAdmin.java` | 创建 |
| | `aop/AdminAspect.java` | 创建 |
| | `pom.xml` | 检查 RocketMQ 依赖（已有则不动） |
| | `src/main/resources/application.yml` | 检查 RocketMQ 配置 |
| **user-service** | `entity/User.java` | 修改：加 role/avatar |
| | `service/UserService.java` + `impl/UserServiceImpl.java` | 修改：加 profile/avatar/password |
| | `controller/UserController.java` | 修改：加 3 个端点 |
| | `src/main/resources/schema.sql` | 修改：user 表加字段 |
| **gateway** | `filter/AuthGlobalFilter.java` | 修改：`/api/admin/**` 鉴权 |
| **tianji-common** | `util/JwtUtil.java` | 修改：生成 token 时写入 role claim |

---

## 六、验证计划

### 6.1 编译验证
```bash
mvn compile
```
期望：全模块编译通过。

### 6.2 测试验证
```bash
mvn test
```
期望：全部测试通过，新增/修改功能的测试覆盖关键路径。

### 6.3 新增测试覆盖

| 模块 | 测试类 | 覆盖 |
|------|--------|------|
| mall-goods-order | AdminControllerTest | admin CRUD 端点 + 非 admin 拒绝 |
| mall-goods-order | OrderServiceTest | 超时取消消费者 + 退款流程 |
| user-service | UserControllerTest | 修改资料/密码/头像 |
| gateway | AuthGlobalFilterTest | admin 路径鉴权 |

---

## 七、不做

- 不引入 FastDFS / MinIO / OSS（头像存 Base64）
- 不引入 Spring Security（继续 JWT + AOP）
- 不新建 admin-service 模块（复用 mall-goods-order）
- 退款不支持部分退款（全单退）
- 分类不支持三级及以上（仅二级）
