# 前端综合打磨 实现计划（4 批次）

> **面向 AI 代理的工作者：** 按批次执行，每批完成 → `npm run build` 验证 → 浏览器确认 → 提交。步骤使用复选框（`- [ ]`）语法。
> **参考规格：** `docs/superpowers/specs/2026-08-07-frontend-polish-design.md`（含每项详细改动要点）

**目标：** 商品详情/列表/个人中心 + 全局微交互四方向纯前端打磨。
**技术栈：** Vue3 `<script setup>` + Element Plus + scoped CSS。不引新依赖、不改后端。
**工作目录：** `tianji-mall-frontend`（build 命令：`cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build`）

---

## 批次 A：全局微交互

**文件：**
- 修改：`src/api/request.js`
- 新建：`src/components/common/ProductGridSkeleton.vue`
- 修改：`src/views/product/ProductList.vue`、`src/views/home/HomePage.vue`
- 修改：`src/views/cart/CartPage.vue`、`src/views/coupon/CouponCenter.vue`、`src/views/order/CheckoutPage.vue`、`src/views/user/AddressPage.vue`
- 修改：`src/App.vue`、`src/layouts/DefaultLayout.vue`（+ Admin/Seller layout 若存在）

- [ ] A1 全局 loading bar（request.js 计数 + 顶部进度条）
- [ ] A2 ProductGridSkeleton 组件 + 列表/首页骨架替换遮罩
- [ ] A3 空态闪烁修复（el-empty 加 `!loading` 守卫，5 个页面）
- [ ] A4 路由过渡（App.vue + DefaultLayout 的 router-view transition）
- [ ] build 通过 + 浏览器确认 → commit `feat(frontend): global loading bar, skeleton, empty-state fix, route transition`

---

## 批次 B：商品列表页

**文件：**
- 修改：`src/views/product/ProductList.vue`
- 修改：`src/components/common/ProductCard.vue`

- [ ] B1 价格排序单 tab 升/降翻转
- [ ] B2 排序/价格/页码写回 URL（单一数据源 route.query）
- [ ] B3 原价划线 + 折扣角标 + 价格两位小数
- [ ] B4 标题两行省略 + 卡片对齐
- [ ] B5 翻页/筛选滚动回顶
- [ ] B6 空态清除筛选/返回首页按钮
- [ ] build 通过 + 浏览器确认 → commit `feat(frontend): product list sort toggle, URL state, discount badge, empty-state action`

---

## 批次 C：商品详情页

**文件：**
- 修改：`src/views/product/ProductDetail.vue`

- [ ] C1 主图 el-image-viewer 全屏灯箱
- [ ] C2 响应式断点（≤992px 单列）
- [ ] C3 价格 formatPrice + 划线原价
- [ ] C4 星级 0.5 档（allow-half + round half）
- [ ] C5 SKU 默认预选可购组合 + 未选全提示
- [ ] C6 秒杀倒计时
- [ ] build 通过 + 浏览器确认 → commit `feat(frontend): product detail lightbox, responsive, sku preselect, seckill countdown`

---

## 批次 D：个人中心

**文件：**
- 修改：`src/views/user/UserCenter.vue`

- [ ] D1 图标统一 el-icon
- [ ] D2 入口分组（购物/服务/账户）+ 退出登录独立 danger 组
- [ ] D3 待评价去重（删快捷入口项）
- [ ] D4 补齐关注/收藏/足迹计数（用户卡数据行 + 足迹 badge）
- [ ] D5 表单校验 + 头像上传校验/loading
- [ ] build 通过 + 浏览器确认 → commit `feat(frontend): user center icon consistency, grouped entries, data row, form validation`

---

## 收尾

- [ ] 全量 build 最终确认
- [ ] 浏览器逐项验收 4 批效果
- [ ] 提交设计/计划文档 `docs: add frontend polish batch design and plan`
