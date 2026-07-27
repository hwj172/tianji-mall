# P1 用户中心增强 + 待评价列表 + 搜索热词 设计规格

> 日期：2026-07-27 | 范围：mall-goods-order 模块（少量涉及 user-service）

## 1. 用户中心聚合 API

### 端点
`GET /api/user/center` — JWT 鉴权

### 响应
```json
{
  "user": { "id": 1, "username": "xxx", "nickname": "xxx", "avatar": "...", "phone": "138****", "role": "user" },
  "orderStats": { "pendingPayment": 5, "pendingShip": 2, "pendingReceive": 3, "pendingReview": 1 },
  "couponCount": 3,
  "favoriteCount": 12,
  "followShopCount": 2,
  "cartCount": 8,
  "historyCount": 50
}
```

### 数据来源
| 字段 | 来源 | 方式 |
|------|------|------|
| user | user-service `/api/user/internal` | Feign（新增 `/api/user/internal/{id}` 端点） |
| orderStats | order 表 | OrderMapper.selectOrderStats(userId) — 按 status 分组 COUNT |
| couponCount | user_coupon 表 | UserCouponMapper.selectCount(userId, UNUSED) |
| favoriteCount | favorite 表 | FavoriteMapper.selectCount(userId) |
| followShopCount | shop_follow 表 | ShopFollowMapper.selectCount(userId) |
| cartCount | cart_item 表 | CartItemMapper.selectCount(userId) |
| historyCount | browsing_history 表 | BrowsingHistoryMapper.selectCount(userId) |

### 实现要点
- Controller 新建 `UserCenterController`（减少对现有 Controller 的侵入）
- user-service 需要新增 `GET /api/user/internal/{id}` 返回用户基本信息（不含 password）
- 所有计数查询各自 Mapper 的 `selectCount`，不走 JOIN
- `orderStats.pendingReview` = 已完成订单(4)中未评价的商品数（与功能 2 共享逻辑）
- 任何子查询失败不阻塞其他字段（best-effort），降级为 0

---

## 2. 待评价列表

### 端点
`GET /api/review/pending` — JWT 鉴权，分页（page/size 默认 1/20）

### 响应
```json
[
  {
    "orderId": 123,
    "productId": 1,
    "productName": "iPhone 16",
    "productImage": "...",
    "price": 9999.00,
    "skuId": null,
    "skuSpecs": null,
    "orderCreateTime": "2026-07-20T10:00:00"
  }
]
```

### 查询逻辑
```sql
SELECT oi.order_id, oi.product_id, oi.product_name, oi.price, oi.sku_id, oi.sku_specs,
       p.images AS product_image, o.create_time AS order_create_time
FROM order_item oi
JOIN `order` o ON o.id = oi.order_id
LEFT JOIN product p ON p.id = oi.product_id
LEFT JOIN review r ON r.user_id = o.user_id AND r.order_id = o.id AND r.product_id = oi.product_id
WHERE o.user_id = #{userId}
  AND o.status = 4
  AND r.id IS NULL
ORDER BY o.create_time DESC
```

### 实现要点
- `ReviewMapper.selectPendingReviews(userId)` @Select 注解
- `ReviewService.getPendingReviews(userId, page, size)` 方法
- `ReviewController` 加 `GET /api/review/pending`
- 返回 DTO 复用 `ReviewResponse` 或新建轻量 `PendingReviewResponse`

---

## 3. 搜索热词

### 端点
- `GET /api/product/search/hot` — 公开，返回 Top 10 热词
- `POST /api/admin/search-hot` — 管理可选（后续按需添加）

### 数据表
```sql
CREATE TABLE search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(128) NOT NULL,
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_time (create_time)
);
```

### 数据流
1. 用户搜索 → `ProductController.search` → `ProductService.getProductPage` → 异步记录 keyword 到 search_log
2. 热词查询 → `SearchLogMapper.selectHotKeywords` → 最近 7 天 GROUP BY keyword ORDER BY count DESC LIMIT 10

### 热词 SQL
```sql
SELECT keyword, COUNT(*) AS count
FROM search_log
WHERE create_time > DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY keyword
ORDER BY count DESC
LIMIT 10
```

### 响应
```json
["手机", "iPhone", "耳机", "充电器", "小米", "华为", "电脑", "平板", "手表", "蓝牙"]
```

### 实现要点
- `SearchLog` entity + `SearchLogMapper`（BaseMapper + @Select 热词）
- `ProductService.getProductPage` 中 keyword 非空时记录日志（best-effort，异常不阻塞）
- `ProductController` 加热词端点 + 网关白名单放行 `/api/product/search/hot`
- 不提供 Admin 管理端点（YAGNI，先不做）
- 搜索日志不对外暴露（无分页查询、无管理端点）
- 表体积：按日活 100 人 × 平均 5 次搜索 = 每天 500 行，7 天 3500 行，很轻

---

## 4. 文件清单

### 新建
| 文件 | 说明 |
|------|------|
| `mall-goods-order/.../controller/UserCenterController.java` | 用户中心聚合 |
| `mall-goods-order/.../entity/SearchLog.java` | 搜索日志实体 |
| `mall-goods-order/.../mapper/SearchLogMapper.java` | 搜索日志 Mapper |
| `mall-goods-order/.../dto/PendingReviewResponse.java` | 待评价响应 DTO |

### 修改
| 文件 | 说明 |
|------|------|
| `mall-goods-order/.../mapper/OrderMapper.java` | 加 selectOrderStats @Select |
| `mall-goods-order/.../mapper/ReviewMapper.java` | 加 selectPendingReviews @Select |
| `mall-goods-order/.../service/ReviewService.java` | 加 getPendingReviews |
| `mall-goods-order/.../controller/ReviewController.java` | 加 pending 端点 |
| `mall-goods-order/.../service/ProductService.java` | 搜索时记录日志 |
| `mall-goods-order/.../controller/ProductController.java` | 加热词端点 |
| `gateway/.../filter/AuthGlobalFilter.java` | 白名单加 `/api/product/search/hot` |
| `user-service/.../controller/UserController.java` | 新增内部端点 `/api/user/internal/{id}` |
| `mall-goods-order/.../feign/UserFeignClient.java` | 加 getUserById |
| `mall-goods-order/.../mapper/UserCouponMapper.java` | 加 selectCountByUserId |
| `mall-goods-order/.../mapper/FavoriteMapper.java` | 加 selectCountByUserId |
| `mall-goods-order/.../mapper/CartItemMapper.java` | 加 selectCountByUserId（如未存在） |
| `mall-goods-order/.../mapper/BrowsingHistoryMapper.java` | 加 selectCountByUserId（如未存在） |
| `mall-goods-order/.../mapper/ShopFollowMapper.java` | 加 selectCountByUserId（如未存在） |
| `mall-goods-order/src/test/resources/schema.sql` | 加 search_log DDL |
| `sql/init.sql` | 加 search_log DDL |
| `CLAUDE.md` | 更新约定 |

---

## 5. 测试计划

| 测试类 | 新增 | 覆盖 |
|--------|------|------|
| UserCenterControllerTest（新建） | 6 | 正常返回 / 缺 JWT / JWT 无效 / user-service 降级 / 空数据边界 |
| ReviewControllerTest | +2 | pending 端点 / 空列表 |
| ReviewServiceTest | +3 | 有数据 / 空列表 / 已评价全部排除 |
| ProductControllerTest | +1 | search/hot 端点 |
| Gateway AuthGlobalFilterTest | 同步更新公开路径数 | 白名单包含新路径 |
| UserControllerTest（user-service） | +2 | internal 端点通过 ID 查用户 / 不返回 password |

**预计新增约 14 个测试，全模块回归 ~477 tests。**
