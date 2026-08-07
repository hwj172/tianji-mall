# 前端综合打磨 设计文档（商品详情 / 商品列表 / 个人中心 / 全局微交互）

**目标：** 四个方向的纯前端视觉与交互打磨，系统性提升电商体验感知质量。后端零改动（个别项依赖现有接口字段，未确认字段则不做）。

**范围：** `tianji-mall-frontend` 约 12 个文件 + 3 个新通用组件。按 4 批推进，每批 build + 浏览器验证。

---

## 批次 A：全局微交互

### A1 全局顶部 loading bar
- **文件：** `src/api/request.js`
- 请求拦截器维护全局计数（`window.__reqCount`），>0 时显示顶部 2px 品牌橙进度条；响应/错误拦截器递减，=0 时隐藏。轻量自实现（不引 nprogress 依赖）：
  - `#global-loading-bar` 元素：`position:fixed; top:0; left:0; height:2px; background:#ff5000; z-index:9999; transition:width .2s, opacity .3s;`
  - 请求开始 `width:30%`，结束时 `width:100%` → 300ms 后 `opacity:0`。
- 401/业务错误分支同样递减计数。

### A2 商品网格骨架屏
- **新建：** `src/components/common/ProductGridSkeleton.vue`（props: `cols`，默认 4）
  - 渲染 `cols` 个骨架卡片：图片块（aspect-ratio 1:1 灰底）+ 两行文字块，用 `el-skeleton` 或纯 CSS 渐变 shimmer 动画。
- **替换：** `src/views/product/ProductList.vue`（`v-loading` → `v-if="loading"` 渲染骨架，数据到后换网格）、`src/views/home/HomePage.vue`（热销/推荐区 loading 时骨架）。

### A3 空态闪烁修复
- **文件：** `src/views/cart/CartPage.vue:47`、`src/views/coupon/CouponCenter.vue:34/58`、`src/views/order/CheckoutPage.vue:82`、`src/views/user/AddressPage.vue:24`
- `el-empty v-else` → `el-empty v-else-if="!loading && !items.length"`，补 `loading` ref（已定义则直接复用）。

### A4 路由过渡动效
- **文件：** `src/App.vue`、`src/layouts/DefaultLayout.vue`（及 Admin/Seller layout 的 `router-view`）
- 包裹 `<transition name="fade-slide" mode="out-in">`，CSS：`fade-slide-enter-active/leave-active { transition: opacity .2s, transform .2s }`，`enter-from { opacity:0; transform:translateY(8px) }`。

---

## 批次 B：商品列表页（ProductList.vue + ProductCard.vue）

- **B1 价格 tab 翻转**：`sortOptions` 去掉 `price_asc`/`price_desc` 两个 tab，只保留一个「价格」tab；`changeSort` 收到 `price` 时在 `price_asc ⇄ price_desc` 翻转；tab 图标箭头随方向显示 ↑/↓。
- **B2 URL 状态回写**：`changeSort`/`applyPrice`/分页 `current-change` 统一 `router.replace({ query: { ...route.query, sort, minPrice, maxPrice, page } })`（删空值 key）；`loadProducts` 只从 `route.query` 读取（单一数据源），去掉本地 ref 触发加载的重复路径。
- **B3 原价+折扣角标**：ProductList 传 `:show-original-price="true"`；ProductCard 价格统一 `Number(price).toFixed(2)`，`product.originalPrice`（或 SKU 原价）存在时划线显示 + 右上角「-N%」角标（`Math.round((1 - price/original)*100)`）。
- **B4 两行标题**：ProductCard 标题改 `display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;` + 内容区 `min-height` 对齐。
- **B5 滚动回顶**：`loadProducts` 成功后 `window.scrollTo({ top: 0, behavior: 'smooth' })`（仅分页/筛选变化时）。
- **B6 空态出路**：`el-empty` 下加「清除筛选」按钮（`router.replace` 清空 sort/price/keyword）+「返回首页」。

---

## 批次 C：商品详情页（ProductDetail.vue）

- **C1 主图灯箱**：点击主图打开 `el-dialog`（或 `el-image-viewer`）全屏预览，支持左右切换/缩略图。用 Element Plus `el-image-viewer` 组件（内置缩放/切换），`src-list` 为全部图片。
- **C2 响应式断点**：`.detail-main` 加 `@media (max-width: 992px)` 堆叠单列；`.detail-gallery` 宽度自适应；`.action-row` 按钮 `flex:1`。
- **C3 价格格式**：封装 `formatPrice(n)` → `Number(n).toFixed(2)`（或整数去尾 0），价格行统一调用；有原价时划线显示。
- **C4 星级 0.5 档**：`el-rate` 加 `allow-half`，评分用 `Math.round(avg*2)/2`；好评率保留一位小数。
- **C5 SKU 默认预选**：`loadDetail` 后若 `skuMatrix/specTree` 存在，按首个可购组合自动填 `selectedSpecs` + `currentSkuInfo`（回填库存/价格/数量上限）；未选全时显示「请选择规格」提示条（替代整块隐藏）。
- **C6 秒杀倒计时**：`isSeckill` 时在价格框显示 `setInterval` 每秒更新的「距结束 时:分:秒」倒计时，结束自动刷新回普通价。

---

## 批次 D：个人中心（UserCenter.vue）

- **D1 图标统一**：统计/入口 emoji 全改 el-icon（`CreditCard/Box/Truck/Stamp/ShoppingCart/Ticket/MapLocation/Document/Star/Clock/Service/Bell/User/Close` 等），删除 `.ql-emoji`。
- **D2 分组 + 退出移出**：10 个入口按「购物（购物车/优惠券/收藏/足迹）」「服务（地址/评价/退款/消息）」「账户（编辑资料/退出登录）」加小标题分组；退出登录用 danger 色，独立成组。
- **D3 待评价去重**：删除快捷入口中的「待评价」，保留订单统计里的（路由 `/order/list?status=4`）。
- **D4 数据补齐**：用户卡下方加一行「关注店铺 X · 收藏 Y · 足迹 Z」计数（用已返回的 `followShopCount`/`favoriteCount`/`historyCount`）；浏览足迹入口 badge 挂 `historyCount`。
- **D5 表单校验**：`el-form` 加 rules（username 必填、phone 正则 `/^1[3-9]\d{9}$/`、email 格式），`saveProfile` 前 `validate()`；头像上传限制 `image/jpeg|png|webp` ≤2MB，上传时按钮 loading。

---

## 验证

- 每批 `cd /d/TEST/tianji-mall/tianji-mall-frontend && npm run build` 通过。
- 浏览器 `http://localhost:5173` 逐批人工确认（列表筛选/翻页、详情灯箱/规格、个人中心分组、全局 loading bar/骨架/过渡）。
- 不引入新依赖；不改后端。

## 不做（YAGNI）
- 详情页猜你喜欢、评价星级筛选、吸底购买栏（需新接口/新组件，量大）
- 列表评分/秒杀角标（后端字段未确认）
- 键盘无障碍全面改造（项目现状一致，非本次目标）
