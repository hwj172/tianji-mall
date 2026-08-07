# 天机商城 前端

天机商城（仿淘宝智能电商平台）的前端项目，基于 Vue 3 + Element Plus + Pinia + Vite。

## 技术栈

- **Vue 3** `<script setup>` + Vite 8
- **Element Plus**（组件库，品牌橙 `#ff5000` 已通过 `--el-color-primary` 覆盖主色）
- **Pinia**（状态管理：user / cart）
- **Vue Router**（路由守卫：登录态 + 角色 `meta.role`）
- **Axios**（`src/api/request.js` 统一封装：token 注入、全局 loading bar、错误提示、401 登出）

## 目录结构

```
src/
├── api/                 # Axios 实例 + 全部接口定义
├── assets/styles/       # 全局样式（含品牌主题变量）
├── components/
│   ├── common/          # 公共组件
│   │   ├── ProductCard.vue        # 商品卡（图片/原价/折扣/懒加载）
│   │   ├── ProductGridSkeleton.vue# 商品网格骨架屏
│   │   └── EmptyState.vue         # 空态（可带操作按钮）
│   └── home/            # 首页组件（HomeBanner 等）
├── composables/         # 组合式函数
│   ├── useProductBatch.js   # 商品批量回填
│   └── usePagedList.js      # 加载更多分页
├── layouts/             # 布局（DefaultLayout / AdminLayout / SellerLayout）
├── router/              # 路由 + 守卫
├── stores/              # Pinia（user / cart）
├── utils/               # 工具函数
│   ├── format.js    # fmtPrice 金额两位小数
│   ├── image.js     # getFirstImage / imageOnError 占位
│   ├── date.js      # fmtTime 时间格式化
│   ├── order.js     # 订单状态映射
│   └── discount.js  # 折扣格式化
└── views/              # 页面（home/product/order/cart/user/seller/admin/chat/groupbuy/coupon/refund/review/notification/favorite/history/seckill/auth）
```

## 常用命令

```bash
npm install        # 安装依赖
npm run dev        # 开发服务器（默认 5173，proxy → VM gateway）
npm run build      # 生产构建（验证用，必须 ✓ built）
```

**验证方式**：本项目无单测框架，改动后用 `npm run build` + 浏览器视觉确认（http://localhost:5173）。

## 环境与代理

开发时 `vite.config.js` 将 `/api`、`/uploads` 代理到 VM gateway `http://192.168.150.11:8080`。本地需先建立 SSH 隧道或运行仓库根目录的 `bash start-all.sh`（一键启动：隧道 + VM 恢复 + 前端）。

## 账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |
| seller_demo | 123456 | 商家（已开店「演示商城旗舰店」） |

普通用户可在「个人中心 → 账户 → 注册开店」成为商家（开店后需重新登录激活商家权限）。

## 前端关键约定

- 金额显示统一 `fmtPrice`、时间统一 `fmtTime`；空态用 `EmptyState`；列表 loading 用 `ProductGridSkeleton`
- 商品图片用 `getFirstImage` 解析、加载失败用 `imageOnError` 占位
- 订单状态文案/颜色统一走 `utils/order.js`
- AI 客服历史走后端 `getChatHistory`，localStorage 只存 sessionId
- 顶栏入口：logo/🏠 回首页、🏪 商家中心（isSeller）、🛒 购物车、🤖 AI 导购、用户下拉
