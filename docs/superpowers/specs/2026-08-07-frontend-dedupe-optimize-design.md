# 前端功能去重 + 全面优化 设计文档

**目标：** 消除前端功能重复（AI 客服等）、修复已知 bug、补体验缺口、优化性能、收敛重复代码，纯前端零后端改动。

**范围：** `tianji-mall-frontend` 约 40 个文件 + 8 个新文件。5 批推进，每批 build 验证。

---

## 批次 1：功能去重（commit `ef16684`）
- **AI 客服 ChatPage**：历史改后端 `getChatHistory`（删 localStorage 消息存取）；推荐商品复用 `ProductCard`（删自建 rec-card/getFirstImage/onImgError）；快捷提问改拉 `getHotKeywords`（删硬编码 hints）。
- **导航合并**：删二级导航 sub-nav（首页 quick-grid 已覆盖）；顶栏删 🏠/👤/🏪 独立按钮（下拉菜单已有）。
- **死 API**：删 `getProductRecommend`（grep 确认 0 调用）。

## 批次 2：Bug 修复（commit `9ca7b95`）
1. GroupBuyList 折扣 `*100`→`*10`（"90折"→"9折"）
2. CheckoutPage 重复 key（购物车模式 `item.cart.id`）
3. CouponCenter 领券后刷新「我的优惠券」
4. el-image `@error` 不冒泡 → 改 `<img @error>`（OrderDetail/PendingReviews）
5. AddressPage isDefault 兼容布尔+数字
6. ProductDetail 收藏状态：后端无轻量接口，保留全量判断（待后端补 `favorited` 字段）

## 批次 3：体验补漏（commit `6295544`）
- 抽 `src/utils/format.js` `fmtPrice`，统一 9 页 + ProductCard 金额两位小数
- 10 个页面补 `v-loading`（购物车/结算/订单×2/退款/评价×2/通知/领券/地址）+ 2 个看板
- 通知点击按 relatedOrderId 跳订单详情
- 登录/注册品牌视觉（橙渐变背景 + Logo + 橙色主按钮）
- 秒杀页活动状态角标 + 倒计时 + `??` 修复（0 元秒杀）
- admin 用户管理危险操作二次确认
- 收藏/足迹分页：后端接口不支持，跳过

## 批次 4：性能优化（commit `9629718`）
- 购物车全选/批量删除串行→`Promise.all`
- 并行化：CouponCenter 3 请求、OrderDetail 商品+物流、ProductDetail 收藏状态
- 地区树懒加载（仅开弹窗）+ 模块缓存
- 热门词模块缓存 + keyword 存在跳过
- 首页数据 sessionStorage + 5min TTL
- `getProductBatch` 模块级 Map 缓存（调用方零改动）
- 列表 img 加 `loading="lazy"`（详情主图保持 eager）
- 购物车角标去重：store list 缓存 + `setList` 本地同步

## 批次 5a：主题 + utils + 死代码（commit `0a08185`）
- `--el-color-primary` 品牌橙变量，全站 primary 按钮统一橙色（消除橙蓝混用）
- 抽 `utils/image.js`（getFirstImage/imageOnError）、`date.js`（fmtTime）、`order.js`（状态映射）、`discount.js`，替换重复实现（getFirstImage×5、fmtTime×13、占位×7、状态×4、折扣×5）
- 删 22 个死 API + OrderDetail 未用 import

## 批次 5b：公共组件抽取（commit `0804dea`）
- 抽 `EmptyState.vue`（替换 3 处带按钮空态）
- 抽 `useProductBatch.js`（FavoriteList/BrowsingHistory；CartPage/CheckoutPage/OrderDetail 结构差异大保留原逻辑）
- 抽 `usePagedList.js`（MyReviews/PendingReviews）

## 未做（风险/成本权衡）
- 收藏/足迹分页（后端不支持）
- ProductDetail 收藏状态轻量接口（待后端补字段）
- admin/seller 表格/布局合并（ProductManageTable/OrderManageTable/BaseManageLayout）——改动最大、纯维护性收益，风险高，建议单独评估
- ProductGrid/OrderItemRow 组件抽取（6/3 处布局差异，风险中高）

## 验证
- 每批 `npm run build` 通过
- 浏览器逐项人工验收（见批次汇报）
