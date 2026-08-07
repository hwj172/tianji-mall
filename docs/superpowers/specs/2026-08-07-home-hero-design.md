# 首页首屏打磨 设计文档

**目标：** 升级首页首屏（hero 区）视觉档次——分类侧栏加图标、Banner 渐变叠加、右侧改为「用户卡 + 快捷入口宫格 + 公告」，呈现仿淘宝电商首屏的层次感。

**范围：** 仅前端 `tianji-mall-frontend`，改动 `HomePage.vue` + `HomeBanner.vue`。**后端零改动**（分类图标用前端 emoji 映射，不新增字段）。

**技术栈：** Vue3 + Element Plus + scoped CSS。

---

## 1. 首屏 hero 三栏结构（现状 → 目标）

```
现状：  [分类侧栏 200px] [Banner flex:1] [用户卡 + 公告 200px]
目标：  [分类侧栏 200px] [Banner flex:1] [用户卡渐变 + 快捷入口4宫格 + 公告 220px]
```

三栏等高（banner 300px 高，与右侧高度对齐）。整体 max-width 1200px 不变。

## 2. 分类侧栏（HomePage.vue）

- **emoji 图标映射**：前端常量表 `CATEGORY_ICONS`，按名称关键词匹配（手机→📱、电脑/办公→💻、服饰/鞋包→👕、智能→⌚、家电/家居→🏠、运动→⚽、美妆→💄、食品→🍎、图书→📚、玩具→🧸、默认→📦）。
- **hover 高亮**：背景 `#fff5f0` + 文字 `#ff5000` + 加粗；现有 hover 逻辑保留（activeCategory 切换）。
- **标题**：`全部商品分类` 加底部 2px 品牌色渐变线（`linear-gradient(#ff5000, #ff7a3d)`）。
- 分类数量超过 10 时仍 `slice(0,10)`（现状逻辑）。

## 3. Banner（HomeBanner.vue）

- 保留 `el-carousel` 300px 高、圆角 8px。
- **渐变叠加层**：`.banner-link` 加 `::after`，底部 60px `linear-gradient(180deg, transparent, rgba(0,0,0,.25))`，提升文字可读性与质感。
- **指示器自定义**：`::v-deep` 覆盖 el-carousel 指示器——普通态 7px 圆点半透明白，active 态 18px 圆角条（白色），居中显示。
- placeholder 分支（无 banner 时）保持现有渐变样式。

## 4. 右侧用户卡 + 快捷入口 + 公告（HomePage.vue）

- **用户卡**：背景 `linear-gradient(135deg, #ff7a3d, #ff5000)`、白色文字、圆角 10px、底部阴影 `rgba(255,80,0,.2)`。
  - 未登录：头像 👤 + "Hi，欢迎光临" + 白色描边「登录 / 注册」按钮（→ /login）+ 标签（新人福利/领券中心）。
  - 已登录：头像 👤 + "Hi，{username}" + 标签（我的订单→/order/list、领券中心→/coupon/center）。
- **快捷入口 4 宫格**（`display:grid; grid-template-columns:1fr 1fr; gap:8px`，白底圆角卡片，hover `translateY(-2px)` + 阴影上浮）：
  - ⚡ 限时秒杀 → `/seckill`
  - 🎯 阶梯拼团 → `/groupbuy`
  - 🎫 领券中心 → `/coupon/center`
  - 🤖 AI 导购 → `/chat`
- **公告**：保留在右侧底部（flex:1 占位），文案与现状一致。

## 5. 文件改动清单

| 文件 | 改动 |
|------|------|
| `tianji-mall-frontend/src/views/home/HomePage.vue` | 分类侧栏 emoji/hover/标题线、右侧用户卡渐变 + 快捷入口宫格、hero 高度对齐 |
| `tianji-mall-frontend/src/components/home/HomeBanner.vue` | banner-link 渐变叠加层、轮播指示器自定义 |

## 6. 验证

- `npm run build` 通过（vue 编译 + 无未用 import 报错）。
- 浏览器 `http://localhost:5173` 查看首页首屏：
  - 分类侧栏图标 + hover 橙色高亮
  - banner 渐变叠加 + 指示器样式
  - 用户卡渐变（未登录/已登录两态）
  - 快捷入口 4 卡片 hover 上浮、点击跳转对应路由
  - hero 三栏等高不破版

## 7. 不做（YAGNI）

- 不动推荐区/热销区布局（本次只做首屏 hero）。
- 不新增后端分类图标字段。
- 不引入图标库，emoji 足够且零依赖。
