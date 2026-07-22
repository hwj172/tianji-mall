# 首页推荐系统 设计文档

## 概述

为天机商城首页提供推荐功能：猜你喜欢（个性化）+ 热销榜单（通用）+ 买了还买（关联规则）。有 JWT 时返回个性化推荐，无 JWT 时仅返回通用推荐位。

## API

**端点：** `GET /api/product/recommend`（公开，JWT 可选）

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| count | int | 否 | 每类推荐数量，默认 10 |

**响应结构：**

```json
{
  "code": 200,
  "data": {
    "guessYouLike": [
      { "id": 1, "name": "iPhone", "price": 6999, "sales": 5000, "reason": "" }
    ],
    "hotSales": [
      { "id": 2, "name": "MacBook", "price": 12999, "sales": 8000, "reason": "" }
    ],
    "buyAfterBuy": [
      { "id": 3, "name": "保护壳", "price": 49, "sales": 3000, "reason": "和 iPhone 一起买" }
    ]
  }
}
```

- `guessYouLike`：仅 JWT 存在时返回
- `hotSales`：始终返回
- `buyAfterBuy`：始终返回（有 JWT 时基于用户购买记录关联，无 JWT 时基于全站 Top 商品关联）

## 推荐算法

### 热销榜单

**公式：** `score = sales × 0.5 + favorites × 0.3 + reviews × 0.2`

- 定时任务（每小时）计算 Top 50 商品
- 用 `@Cacheable` 缓存结果（TTL 1小时），无需 Redis ZSET
- 缓存 key：`recommend:hot_sales`

### 猜你喜欢

基于用户近 30 天购买的商品品类分布：
1. 查用户历史购买商品
2. 统计品类偏好权重（购买次数多的品类权重高）
3. 在各偏好品类中取热度最高商品（排除已购）
4. 无购买记录时退化为全站热门

### 买了还买

基于订单共现矩阵：
1. 预计算：扫描历史订单，同一订单中商品 A 和 B 同时出现则 co_count+1
2. 存储：`product_similarity` 表
3. 推荐时：查用户近 30 天购买的商品 → 查每件的 Top 5 关联商品 → 合并去重按 score 排序
4. 无购买记录时用全站 Top 10 热销商品作为种子

## 数据模型

### 新增表：product_similarity

```sql
CREATE TABLE IF NOT EXISTS product_similarity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  similar_product_id BIGINT NOT NULL,
  co_count INT DEFAULT 0,
  score DECIMAL(10,4) DEFAULT 0,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pair (product_id, similar_product_id),
  KEY idx_product (product_id),
  KEY idx_score (score DESC)
);
```

## 架构

```
定时任务（每小时）
├── HotSalesJob → 计算加权热度分 → @Cacheable recommend:hot_sales
└── SimilarityJob → 订单共现矩阵 → product_similarity 表

请求流程 GET /api/product/recommend
├── 查缓存热销榜
├── 有 JWT？→ 查用户购买记录
│   ├── 猜你喜欢：品类偏好 → 同品类热销商品
│   └── 买了还买：购买商品 → 关联商品 → 合并去重
└── 无 JWT？→ 全站热门商品作为种子做关联推荐
```

## 模块

- mall-goods-order：推荐 API、RecommendService、Mapper、定时任务
- tianji-common：JwtUtil（已有）

## 定时任务

使用 Spring `@Scheduled`，在 `RecommendService` 中实现：
- `computeHotSales()`：每小时执行，计算热销榜缓存
- `computeSimilarity()`：每小时执行，计算关联规则矩阵

## 测试

| 层级 | 内容 |
|------|------|
| Service 单元 | RecommendServiceTest：热销榜/猜你喜欢/买了还买/空数据/无 JWT 5 个测试 |
| Controller | ProductControllerTest：新增 recommend 端点测试（有 JWT/无 JWT） |
