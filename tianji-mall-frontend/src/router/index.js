import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/home/HomePage.vue'), meta: { title: '天机商城' } },
      { path: 'product/list', name: 'productList', component: () => import('@/views/product/ProductList.vue'), meta: { title: '商品列表' } },
      { path: 'product/:id', name: 'productDetail', component: () => import('@/views/product/ProductDetail.vue'), meta: { title: '商品详情' } },
      { path: 'shop/:id', name: 'shopDetail', component: () => import('@/views/shop/ShopDetail.vue'), meta: { title: '店铺详情' } },
      { path: 'seckill', name: 'seckill', component: () => import('@/views/seckill/SeckillList.vue'), meta: { title: '限时秒杀' } },
      { path: 'groupbuy', name: 'groupbuy', component: () => import('@/views/groupbuy/GroupBuyList.vue'), meta: { title: '阶梯拼团' } },
      { path: 'groupbuy/:id', name: 'groupBuyDetail', component: () => import('@/views/groupbuy/GroupBuyDetail.vue'), meta: { title: '拼团详情', auth: true } },
      { path: 'cart', name: 'cart', component: () => import('@/views/cart/CartPage.vue'), meta: { title: '购物车', auth: true } },
      { path: 'checkout', name: 'checkout', component: () => import('@/views/order/CheckoutPage.vue'), meta: { title: '确认订单', auth: true } },
      { path: 'order/list', name: 'orderList', component: () => import('@/views/order/OrderList.vue'), meta: { title: '我的订单', auth: true } },
      { path: 'order/:id', name: 'orderDetail', component: () => import('@/views/order/OrderDetail.vue'), meta: { title: '订单详情', auth: true } },
      { path: 'user/center', name: 'userCenter', component: () => import('@/views/user/UserCenter.vue'), meta: { title: '用户中心', auth: true } },
      { path: 'user/address', name: 'address', component: () => import('@/views/user/AddressPage.vue'), meta: { title: '收货地址', auth: true } },
      { path: 'coupon/center', name: 'couponCenter', component: () => import('@/views/coupon/CouponCenter.vue'), meta: { title: '领券中心', auth: true } },
      { path: 'review/my', name: 'myReviews', component: () => import('@/views/review/MyReviews.vue'), meta: { title: '我的评价', auth: true } },
      { path: 'review/pending', name: 'pendingReviews', component: () => import('@/views/review/PendingReviews.vue'), meta: { title: '待评价', auth: true } },
      { path: 'favorite/list', name: 'favoriteList', component: () => import('@/views/favorite/FavoriteList.vue'), meta: { title: '我的收藏', auth: true } },
      { path: 'user/history', name: 'browsingHistory', component: () => import('@/views/history/BrowsingHistory.vue'), meta: { title: '浏览足迹', auth: true } },
      { path: 'refund/list', name: 'refundList', component: () => import('@/views/refund/RefundList.vue'), meta: { title: '退款/售后', auth: true } },
      { path: 'notification/list', name: 'notificationList', component: () => import('@/views/notification/NotificationList.vue'), meta: { title: '消息通知', auth: true } },
      { path: 'chat', name: 'chat', component: () => import('@/views/chat/ChatPage.vue'), meta: { title: 'AI 导购', auth: true } },
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { auth: true, role: 'admin' },
    children: [
      { path: '', name: 'adminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '管理后台' } },
      { path: 'products', name: 'adminProducts', component: () => import('@/views/admin/ProductManage.vue'), meta: { title: '商品管理' } },
      { path: 'orders', name: 'adminOrders', component: () => import('@/views/admin/OrderManage.vue'), meta: { title: '订单管理' } },
      { path: 'categories', name: 'adminCategories', component: () => import('@/views/admin/CategoryManage.vue'), meta: { title: '分类管理' } },
      { path: 'coupons', name: 'adminCoupons', component: () => import('@/views/admin/CouponManage.vue'), meta: { title: '优惠券管理' } },
      { path: 'users', name: 'adminUsers', component: () => import('@/views/admin/UserManage.vue'), meta: { title: '用户管理' } },
      { path: 'shops', name: 'adminShops', component: () => import('@/views/admin/ShopManage.vue'), meta: { title: '店铺管理' } },
    ]
  },
  {
    path: '/seller',
    component: () => import('@/layouts/SellerLayout.vue'),
    meta: { auth: true, role: 'seller' },
    children: [
      { path: '', name: 'sellerDashboard', component: () => import('@/views/seller/Dashboard.vue'), meta: { title: '商家中心' } },
      { path: 'products', name: 'sellerProducts', component: () => import('@/views/seller/ProductManage.vue'), meta: { title: '商品管理' } },
      { path: 'orders', name: 'sellerOrders', component: () => import('@/views/seller/OrderManage.vue'), meta: { title: '订单管理' } },
    ]
  },
  { path: '/login', name: 'login', component: () => import('@/views/auth/LoginPage.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('@/views/auth/RegisterPage.vue'), meta: { title: '注册' } },
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFound.vue'), meta: { title: '404' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// 路由守卫 — 鉴权 + 角色校验
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.auth && !userStore.token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }
  // 角色校验：userInfo 存在时拦截越权访问（刷新后 userInfo 未拉取时放行，由 fetchUserInfo 兜底）
  if (to.meta.role && userStore.userInfo) {
    const role = userStore.userInfo.role
    if (to.meta.role === 'admin' && role !== 'admin') {
      next('/')
      return
    }
    if (to.meta.role === 'seller' && role !== 'seller' && role !== 'admin') {
      next('/')
      return
    }
  }
  next()
})

// 设置页面标题
router.afterEach(to => {
  document.title = to.meta.title || '天机商城'
})

export default router
