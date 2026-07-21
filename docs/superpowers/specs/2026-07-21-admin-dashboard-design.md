# 数据看板设计

## 概述

为管理员提供 `/api/admin/dashboard` 端点，一次性返回核心经营数据：GMV、订单量、用户数、今日/本周/本月趋势对比、热销商品 Top10、订单状态分布、分类销量占比。

## 架构

```
GET /api/admin/dashboard (AdminController)
  → DashboardService.getDashboard()
      ├── orderMapper → 订单金额/数量/状态分布（本地）
      ├── orderItemMapper + productMapper → 热销 Top10（本地）
      ├── orderItemMapper + categoryMapper → 分类销量占比（本地）
      └── UserFeignClient → user-service /api/user/internal/count（远程）
  → DashboardResponse
```

- DashboardService 聚合 4 个数据源，组装为统一响应
- 用户数通过 Feign 跨服务获取（mall-goods-order → user-service），复用现有 `X-Internal-Token` 鉴权模式

## 文件清单

### 新建

| 文件 | 说明 |
|------|------|
| `mall-goods-order/.../dto/DashboardResponse.java` | 看板响应 DTO（含 TodayStats / TopProduct / OrderStatusDist / CategorySales 内部类） |
| `mall-goods-order/.../service/DashboardService.java` | 聚合查询 + Feign 调用 |
| `mall-goods-order/.../feign/UserFeignClient.java` | Feign 接口 → user-service |
| `mall-goods-order/src/test/java/.../service/DashboardServiceTest.java` | 单元测试（Mock Mapper + Feign） |
| `mall-goods-order/src/test/java/.../controller/AdminControllerTest.java` | 新增 1 个 dashboard 端点测试 |

### 修改

| 文件 | 变更 |
|------|------|
| `mall-goods-order/.../controller/AdminController.java` | 新增 `GET /api/admin/dashboard` |
| `user-service/.../controller/UserController.java` | 新增 `GET /api/user/internal/count`（内部端点） |

## 接口设计

### `GET /api/admin/dashboard`

需要 JWT + admin role（通过 `@RequireAdmin` + Gateway `AuthGlobalFilter` 校验）。

**响应示例：**

```json
{
  "code": 200,
  "data": {
    "totalGmv": 1289600.00,
    "totalOrders": 3280,
    "totalUsers": 1560,
    "today": { "gmv": 12345.00, "orders": 42 },
    "thisWeek": { "gmv": 87654.00, "orders": 287 },
    "thisMonth": { "gmv": 345678.00, "orders": 1123 },
    "topProducts": [
      { "id": 1, "name": "iPhone 15", "sales": 520, "amount": 3639480.00 }
    ],
    "orderStatusDist": [
      { "status": 1, "label": "待付款", "count": 123 },
      { "status": 2, "label": "已付款", "count": 89 },
      { "status": 3, "label": "已发货", "count": 45 },
      { "status": 4, "label": "已完成", "count": 520 },
      { "status": 5, "label": "已取消", "count": 67 }
    ],
    "categorySales": [
      { "categoryId": 1, "categoryName": "手机数码", "amount": 560000.00 }
    ]
  }
}
```

### `GET /api/user/internal/count`（user-service 内部端点）

请求头：`X-Internal-Token: ${internal.token}`

响应：
```json
{ "code": 200, "data": 1560 }
```

## 关键 SQL

统计范围：已付款/已发货/已完成订单（排除待付款和已取消）。

```sql
-- GMV 与订单总量
SELECT SUM(total_amount), COUNT(*) FROM `order` WHERE status IN (2,3,4);

-- 今日/本周/本月 GMV & 订单量（额外加时间条件）
SELECT SUM(total_amount), COUNT(*) FROM `order`
WHERE status IN (2,3,4) AND create_time >= #{startTime};

-- 热销 Top10
SELECT oi.product_id, p.name, SUM(oi.quantity) sales,
       SUM(oi.price * oi.quantity) amount
FROM order_item oi
JOIN product p ON oi.product_id = p.id
JOIN `order` o ON oi.order_id = o.id
WHERE o.status IN (2,3,4)
GROUP BY oi.product_id
ORDER BY sales DESC LIMIT 10;

-- 订单状态分布
SELECT status, COUNT(*) cnt FROM `order` GROUP BY status;

-- 分类销量占比
SELECT p.category_id, c.name category_name,
       SUM(oi.price * oi.quantity) amount
FROM order_item oi
JOIN product p ON oi.product_id = p.id
JOIN category c ON p.category_id = c.id
JOIN `order` o ON oi.order_id = o.id
WHERE o.status IN (2,3,4)
GROUP BY p.category_id;
```

## 错误处理

- UserFeignClient 调用失败时，totalUsers 降级为 0（不阻塞主流程），日志 warn
- 所有 SQL 查询无结果时返回 0 或空列表，不抛异常
- Gateway 侧 `/api/admin/**` 已校验 admin role，Controller 层仅需保留 `@RequireAdmin`

## 测试策略

| 测试类 | 新增测试 | 说明 |
|--------|---------|------|
| DashboardServiceTest | 5 | Mock Mapper + FeignClient 返回：正常聚合 / 空数据 / Feign 降级 / 时间范围过滤 / 热销排序验证 |
| AdminControllerTest | 1 | GET /api/admin/dashboard 返回 200 + 校验数据结构 |

DashboardServiceTest 使用 `@ExtendWith(MockitoExtension.class)` 纯单元测试，不启动 Spring Context。

## 测试中 Mock 要求

AdminControllerTest 新增 `@MockBean DashboardService`（不新增 Mapper Mock，DashboardService 已在 service 层隔离）。
