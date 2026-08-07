import request from './request'

export function getHomeData() {
  return request.get('/home')
}

export function getProductList(params) {
  return request.get('/product/list', { params })
}

export function getHotKeywords() {
  return request.get('/product/search/hot')
}

export function getProductDetail(id) {
  return request.get(`/product/${id}`)
}

// 购物车
export function getCartList() {
  return request.get('/cart/list')
}

export function addToCart(data) {
  return request.post('/cart/add', data)
}

export function updateCartItem(id, data) {
  return request.put(`/cart/${id}`, data)
}

export function deleteCartItem(id) {
  return request.delete(`/cart/${id}`)
}

export function checkCartItem(data) {
  return request.put('/cart/check', data)
}

// 商品批量查询（模块级 Map 缓存：session 内同一批商品只拉一次，跨页面复用）
const productBatchCache = new Map()

export async function getProductBatch(ids) {
  const list = Array.isArray(ids) ? ids : []
  const misses = []
  for (const id of list) {
    if (id == null) continue
    if (!productBatchCache.has(id)) misses.push(id)
  }
  if (misses.length) {
    const res = await request.post('/product/batch', misses)
    if (res.data && Array.isArray(res.data)) {
      res.data.forEach(p => { if (p && p.id != null) productBatchCache.set(p.id, p) })
    }
    // 未返回的商品（已下架/删除）标记为不存在，避免每次重复请求
    misses.forEach(id => { if (!productBatchCache.has(id)) productBatchCache.set(id, null) })
    return res
  }
  // 全部命中缓存，无需请求
  const data = list.map(id => productBatchCache.get(id)).filter(Boolean)
  return { code: 200, data }
}

// 地址
export function getAddressList() {
  return request.get('/address/list')
}

export function addAddress(data) {
  return request.post('/address', data)
}

// 订单
export function createOrder(data) {
  return request.post('/order/create', data)
}

export function getOrderDetail(id) {
  return request.get(`/order/${id}`)
}

export function getOrderList(params) {
  return request.get('/order/list', { params })
}

// 用户中心
export function getUserCenter() {
  return request.get('/user/center')
}

// ========== 用户资料 ==========
export function updateProfile(data) {
  return request.put('/user/profile', data)
}
export function uploadAvatar(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.put('/user/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// ========== 管理后台 ==========

// Dashboard
export function getAdminDashboard() {
  return request.get('/admin/dashboard')
}

// 分类
export function getAdminCategories() {
  return request.get('/admin/category')
}

export function createCategory(data) {
  return request.post('/admin/category', data)
}

export function updateCategory(id, data) {
  return request.put(`/admin/category/${id}`, data)
}

export function deleteCategory(id) {
  return request.delete(`/admin/category/${id}`)
}

// 商品
export function getAdminProducts(params) {
  return request.get('/admin/product', { params })
}

export function createAdminProduct(data) {
  return request.post('/admin/product', data)
}

export function updateAdminProduct(id, data) {
  return request.put(`/admin/product/${id}`, data)
}

export function deleteAdminProduct(id) {
  return request.delete(`/admin/product/${id}`)
}

// 订单管理
export function getAdminOrders(params) {
  return request.get('/admin/order', { params })
}

export function completeOrder(id) {
  return request.put(`/admin/order/${id}/complete`)
}

// 优惠券
export function getAdminCoupons(params) {
  return request.get('/admin/coupon', { params })
}

export function createCoupon(data) {
  return request.post('/admin/coupon', data)
}

export function updateCoupon(id, data) {
  return request.put(`/admin/coupon/${id}`, data)
}

export function deleteCoupon(id) {
  return request.delete(`/admin/coupon/${id}`)
}

// 用户管理
export function getAdminUsers(params) {
  return request.get('/admin/user/list', { params })
}

export function updateUserStatus(id, status) {
  return request.put(`/admin/user/${id}/status`, null, { params: { status } })
}

export function updateUserRole(id, role) {
  return request.put(`/admin/user/${id}/role`, null, { params: { role } })
}

// 店铺管理
export function getAdminShops() {
  return request.get('/admin/shop/list')
}

export function updateShopStatus(id, status) {
  return request.put(`/admin/shop/${id}/status`, null, { params: { status } })
}

export function deleteShop(id) {
  return request.delete(`/admin/shop/${id}`)
}

// ========== 商家中心 ==========

export function getSellerShop() {
  return request.get('/seller/shop')
}

export function updateSellerShop(data) {
  return request.put('/seller/shop', data)
}

export function getSellerProducts(params) {
  return request.get('/seller/products', { params })
}

export function createSellerProduct(data) {
  return request.post('/seller/product', data)
}

export function updateSellerProduct(id, data) {
  return request.put(`/seller/product/${id}`, data)
}

export function deleteSellerProduct(id) {
  return request.delete(`/seller/product/${id}`)
}

export function getSellerOrders(params) {
  return request.get('/seller/orders', { params })
}

export function sellerShipOrder(id, data) {
  return request.put(`/seller/order/${id}/ship`, data)
}

export function getSellerDashboard() {
  return request.get('/seller/dashboard')
}

// ========== 地址 ==========
export function updateAddress(id, data) {
  return request.put(`/address/${id}`, data)
}

export function deleteAddress(id) {
  return request.delete(`/address/${id}`)
}

// ========== 评价 ==========
export function getPendingReviews(params) {
  return request.get('/review/pending', { params })
}

export function getMyReviews(params) {
  return request.get('/review/my', { params })
}

export function createReview(data) {
  return request.post('/review', data)
}

// ========== 优惠券 ==========
export function getCouponCenter() {
  return request.get('/coupon/center')
}

export function claimCoupon(id) {
  return request.post(`/coupon/${id}/claim`)
}

export function getMyCoupons() {
  return request.get('/coupon/my')
}

// ========== 秒杀 ==========
export function getSeckillList(params) {
  return request.get('/product/seckill/list', { params })
}

// ========== 拼团 ==========
export function getGroupBuyList() {
  return request.get('/group-buy/list')
}

export function getGroupBuyDetail(id) {
  return request.get(`/group-buy/${id}`)
}

export function startGroupBuy(data) {
  return request.post('/group-buy/start', data)
}

export function joinGroupBuy(groupId, data) {
  return request.post(`/group-buy/join/${groupId}`, data)
}

// ========== 店铺 ==========
export function getShopDetail(id, params) {
  return request.get(`/shop/${id}`, { params })
}

export function followShop(id) {
  return request.post(`/shop/${id}/follow`)
}

export function registerShop(data) {
  return request.post('/shop/register', data)
}

// ========== AI 导购 ==========
export function sendChatMessage(data) {
  return request.post('/chat/send', data)
}

export function getChatHistory(sessionId) {
  return request.get(`/chat/history/${sessionId}`)
}

// ========== 订单操作 ==========
export function cancelOrder(id) {
  return request.put(`/order/${id}/cancel`)
}

export function receiveOrder(id) {
  return request.put(`/order/${id}/receive`)
}

export function getOrderLogistics(id) {
  return request.get(`/order/${id}/logistics`)
}

// ========== 支付 ==========
export function createPay(params) {
  return request.post('/pay/create', null, { params })
}


// ========== 浏览足迹 ==========
export function getBrowsingHistory(params) {
  return request.get('/product/history', { params })
}

export function clearBrowsingHistory() {
  return request.delete('/product/history')
}

// ========== 收藏 ==========
export function toggleFavorite(productId) {
  return request.post('/favorite/toggle', { productId })
}

export function getFavorites(params) {
  return request.get('/favorite/list', { params })
}

// ========== 退款 ==========
export function getRefundDetail(id) {
  return request.get(`/refund/${id}`)
}

export function getMyRefunds(params) {
  return request.get('/refund/my', { params })
}

export function requestRefund(orderId, data) {
  return request.post(`/order/${orderId}/refund`, data)
}

export function shipRefund(id, data) {
  return request.put(`/refund/${id}/ship`, data)
}

export function receiveRefund(id) {
  return request.put(`/refund/${id}/receive`)
}

// ========== 消息通知 ==========
export function getNotifications(params) {
  return request.get('/notification/list', { params })
}

export function getUnreadCount() {
  return request.get('/notification/unread-count')
}

export function markRead(id) {
  return request.put(`/notification/${id}/read`)
}

export function markAllRead() {
  return request.put('/notification/read-all')
}

// ========== 优惠券补充 ==========
export function getCouponCount() {
  return request.get('/coupon/count')
}

// ========== 评价补充 ==========
export function getProductReviews(productId, params) {
  return request.get(`/review/product/${productId}`, { params })
}

// ========== 地区 ==========
export function getRegionTree() {
  return request.get('/region/tree')
}

// ========== 上传 ==========
export function uploadImage(data) {
  return request.post('/upload/image', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

