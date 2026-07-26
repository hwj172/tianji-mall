# P0 演示增强 — 设计规格

> 2026-07-26 | 4 项功能增强：评价统计、店铺信息、SKU 选择器、首页聚合

## 1. 商品详情页评价统计

**目标：** `getProductDetail` 返回评价统计（总数/平均分/好评率）

**ReviewMapper 新增：**
```java
@Select("SELECT COUNT(*) as count, COALESCE(AVG(rating), 0) as avgRating, " +
        "SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END) as goodCount " +
        "FROM review WHERE product_id = #{productId} AND status = 1")
ReviewStats selectStatsByProductId(@Param("productId") Long productId);
```

**tianji-common 新增 DTO：** `ReviewStatsDTO`（count, avgRating, goodRate）

**ProductService.getProductDetail：** 注入 ReviewMapper，调用统计查询，goodRate = goodCount / count（count=0 时为 0）

---

## 2. 商品详情页店铺信息

**目标：** 商品有 shopId 时返回店铺 id/name/logo

**ProductService.getProductDetail：** 注入 ShopMapper，shopId 非 null 时 `selectById(shopId)`，只返回 id/name/logo；null 时返回 null

---

## 3. SKU 规格选择器数据

**目标：** 将扁平 SKU 列表转为前端可直用的规格树 + 矩阵

**ProductSkuService 新增方法：**
```java
public Map<String, Object> buildSpecSelectorData(List<ProductSku> skus) {
    if (skus == null || skus.isEmpty()) return null;
    // 解析 specs ("颜色:深空黑;容量:256G") → LinkedHashMap<String, Set<String>>
    // specTree: [{"name":"颜色","values":["深空黑","银色"]}, ...]
    // skuMatrix: {"深空黑;256G": {"skuId":1,"price":6999,"stock":50}, ...}
}
```

**返回：** `getProductDetail` 的 skus 字段替换为 `{specTree, skuMatrix}`，无 SKU 时均为 null

---

## 4. 首页聚合 API

**新建 Banner 表：**
```sql
CREATE TABLE IF NOT EXISTS banner (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(128) NOT NULL,
  image_url   VARCHAR(512) NOT NULL,
  link_url    VARCHAR(512) DEFAULT NULL,
  sort        INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**新建文件：**
- `entity/Banner.java` — @TableName("banner")，字段 id/title/imageUrl/linkUrl/sort/status
- `mapper/BannerMapper.java` — @Mapper extends BaseMapper<Banner>
- `service/BannerService.java` — extends ServiceImpl<BannerMapper, Banner>，无需额外方法（管理端点直接用 ServiceImpl CRUD）
- `controller/HomeController.java` — GET /api/home（公开端点，JWT 可选，需加入网关白名单）
- `dto/HomeResponse.java` — banners + categories + hotProducts + recommend

**AdminController 新增：**
- GET /api/admin/banner（列表）
- POST /api/admin/banner（新增，body: title/imageUrl/linkUrl/sort）
- PUT /api/admin/banner/{id}（更新）
- DELETE /api/admin/banner/{id}（删除）

**GET /api/home 返回数据：**
- banners = BannerService.list(status=1, orderBy sort ASC)
- categories = CategoryService.listTree()（已有）
- hotProducts = ProductService.page(sortBy=sales, size=8)
- recommend = RecommendService.recommend(userId, count)（JWT 可选，复用现有逻辑）

**网关白名单：** `/api/home` 加入 AuthGlobalFilter PUBLIC_PATHS

---

## 测试变更

| 测试类 | 变更 |
|--------|------|
| ProductServiceTest | 新增：verifyReviewStatsInDetail, verifyShopInfoInDetail |
| ProductSkuServiceTest | 新增：specTree/skuMatrix 结构验证 |
| HomeControllerTest | 新文件：2 个测试（公开端点、需 JWT 可选） |
| AdminControllerTest | 新增：banner CRUD 4 个测试 |
| BannerServiceTest | 新文件：基本 CRUD（MockitoExtension） |
| ProductControllerTest | 修改：detail 验证 reviewStats/shop/specTree 返回 |
| AuthGlobalFilterTest | 新增：/api/home 公开放行 |

---

## 验收

```bash
mvn test -pl mall-goods-order
mvn test -pl gateway
mvn test  # 全量回归
```
