# 天机商城前端 — 首页设计规格

> **目标：** 实现仿淘宝电商首页，对接 `GET /api/home` 接口，展示轮播/分类/热销/推荐

**技术栈：** Vue3 + Element Plus + Axios + Pinia

**数据来源：** `GET /api/home` 返回 `{ banners, categories, hotProducts, recommend }`，公开端点无需 JWT

---

## 设计决策

| 决策 | 选择 |
|------|------|
| 布局 | 经典淘宝风 — 左侧分类 + 中间轮播 + 右侧快捷入口 |
| 配色 | 淘宝橙 `#ff5000`，Element Plus 默认主题叠加 |
| 组件库 | Element Plus（el-carousel / el-menu / el-card / el-input） |

## 页面结构

```
┌─────────────────────────────────────────────┐
│  顶部 Header                                 │
│  Logo | 搜索框 | 购物车 | AI导购 | 用户      │
├─────────────────────────────────────────────┤
│  二级导航：全部商品分类 | 秒杀 | 拼团 | ...   │
├──────┬──────────────────────────┬───────────┤
│ 左侧  │  轮播 Banner             │ 用户卡片  │
│ 分类  │  (el-carousel)          │ 秒杀入口  │
│ 列表  │                         │           │
├──────┴──────────────────────────┴───────────┤
│  🔥 热销排行榜 (4 列商品网格)               │
├─────────────────────────────────────────────┤
│  💝 猜你喜欢 (5 列商品网格)                  │
├─────────────────────────────────────────────┤
│  🛒 买了还买 (5 列商品网格)                  │
├─────────────────────────────────────────────┤
│  Footer © 2026 天机商城                     │
└─────────────────────────────────────────────┘
```

## 组件拆分

1. **`HomePage.vue`** — 页面容器，调用 `GET /api/home`，组装各区块
2. **`ProductCard.vue`** — 商品卡片（图片、名称、价格、销量），全局复用
3. **`HomeBanner.vue`** — 轮播图（Element Plus `el-carousel`），绑定 `banners[]`
4. **`CategoryNav.vue`** — 左侧分类树列表，hover 展开子分类
5. **`ProductGrid.vue`** — 商品网格容器，props: `products[]` + `columns`
6. **`RecommendSection.vue`** — 推荐区块（标题 + ProductGrid + "换一批"）

## 数据流

```
HomePage.vue
  └─ onMounted: GET /api/home
  └─ homeData.banners        → HomeBanner
  └─ homeData.categories     → CategoryNav
  └─ homeData.hotProducts    → ProductGrid (4 cols)
  └─ homeData.recommend.guessYouLike → RecommendSection
  └─ homeData.recommend.hotSales     → RecommendSection
  └─ homeData.recommend.buyAfterBuy  → RecommendSection
```

## 路由

`/` → `DefaultLayout.vue` → `HomePage.vue`（无需登录）

## 验收标准

- [ ] 页面加载后展示轮播 Banner（如果后端有数据）
- [ ] 左侧分类列表从 `categories` 树渲染
- [ ] 热销榜展示 8 个商品（4 列）
- [ ] 猜你喜欢/热销榜/买了还买三个推荐区块正确展示
- [ ] 搜索框输入关键词回车 → 跳转商品列表页
- [ ] 未登录状态正常展示（无 JWT 请求）
- [ ] 响应式：1200px 宽度下正常，移动端适配可后续补
