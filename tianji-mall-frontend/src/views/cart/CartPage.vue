<template>
  <div class="cart-page" v-loading="loading">
    <h2 class="page-title">购物车</h2>

    <!-- 购物车列表 -->
    <div class="cart-main" v-if="cartItems.length">
      <div class="cart-header">
        <span class="cart-header-hint">已选 <b>{{ checkedCount }}</b> 件商品</span>
      </div>

      <div class="cart-list">
        <div class="cart-item" v-for="item in cartItems" :key="item.cart.id" :class="{ invalid: item.invalid }">
          <el-checkbox v-model="item.checked" @change="onItemCheck(item)" :disabled="item.invalid" class="item-check" />
          <div class="item-image" @click="!item.invalid && $router.push(`/product/${item.product.id}`)">
            <AppImage :src="item.image" :alt="item.product.name || '商品已失效'" :size="80" />
          </div>
          <div class="item-info">
            <router-link v-if="!item.invalid" :to="`/product/${item.product.id}`" class="item-name">{{ item.product.name }}</router-link>
            <span v-else class="item-name invalid-name">商品已失效</span>
            <span class="item-sku" v-if="item.specs">{{ item.specs }}</span>
          </div>
          <div class="item-price">¥{{ item.invalid ? '—' : fmtPrice(item.price) }}</div>
          <div class="item-qty">
            <el-input-number v-model="item.cart.quantity" :min="1" :max="item.product.stock" size="small" @change="onQtyChange(item)" :disabled="item.invalid" />
          </div>
          <div class="item-subtotal">¥{{ item.invalid ? '—' : fmtPrice(item.price * item.cart.quantity) }}</div>
          <el-button text type="danger" @click="removeItem(item)" class="item-del">删除</el-button>
        </div>
      </div>

      <!-- 底部结算栏 -->
      <div class="cart-footer">
        <div class="footer-check">
          <el-checkbox v-model="selectAll" @change="onSelectAll">全选</el-checkbox>
          <span class="del-selected" @click="removeChecked">删除选中</span>
        </div>
        <div class="footer-right">
          <span class="total-label">已选 <b>{{ checkedCount }}</b> 件，合计：</span>
          <span class="total-price">¥{{ fmtPrice(totalPrice) }}</span>
          <el-button type="primary" size="large" :disabled="!checkedCount" @click="goCheckout" class="checkout-btn">
            去结算{{ checkedCount ? ` (${checkedCount})` : '' }}
          </el-button>
        </div>
      </div>
    </div>

    <EmptyState v-else-if="!loading && !cartItems.length" description="购物车空空如也">
      <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
    </EmptyState>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCartList, updateCartItem, deleteCartItem, checkCartItem, getProductBatch } from '@/api'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import AppImage from '@/components/common/AppImage.vue'
import { getFirstImage } from '@/utils/image'

const router = useRouter()
const cartStore = useCartStore()

// 合并后的数据：[{ cart: CartItem, product: Product, image, price, specs, checked }]
const cartItems = ref([])
const loading = ref(false)

const selectAll = ref(false)
const isIndeterminate = ref(false)

const checkedCount = computed(() => cartItems.value.filter(i => i.checked).length)

const totalPrice = computed(() => {
  let sum = 0
  cartItems.value.filter(i => i.checked).forEach(i => {
    sum += i.price * i.cart.quantity
  })
  return sum.toFixed(2)
})

onMounted(() => loadCart())

async function loadCart() {
  loading.value = true
  try {
    const res = await getCartList()
    const items = res.data || []
    // 同步角标缓存（本地计算，避免再次全量拉取购物车）
    cartStore.setList(items)
    if (!items.length) return

    // 批量查商品数据
    const productIds = [...new Set(items.map(i => i.productId))]
    const pRes = await getProductBatch(productIds)
    const productMap = {}
    if (pRes.data) pRes.data.forEach(p => { productMap[p.id] = p })

    cartItems.value = items.map(ci => {
      const product = productMap[ci.productId] || {}
      // 商品不存在（下架/删除）标记失效，置灰不可选
      const invalid = !product.id
      // 获取 SKU 描述（需从详情 API 异步获取，这里做个简化版）
      const image = getFirstImage(product.images)
      // 优先 CartItemDTO.price（SKU 商品为 SKU 价，无 SKU 为商品价）
      const price = ci.price ?? product.price ?? 0
      // 失效商品强制不勾选，避免计入合计 / 进入结算
      return { cart: ci, product, image, price, specs: ci.skuSpecs || '', checked: ci.checked === 1 && !invalid, invalid }
    })
    updateSelectAllState()
  } catch (e) {
    console.error('加载购物车失败', e)
  } finally {
    loading.value = false
  }
}

function updateSelectAllState() {
  const all = cartItems.value.every(i => i.checked)
  const some = cartItems.value.some(i => i.checked)
  selectAll.value = all
  isIndeterminate.value = some && !all
}

async function onSelectAll(val) {
  // 只勾选有效商品（失效项 disabled 无法手动取消，全选不得包含）
  cartItems.value.forEach(i => { if (!i.invalid) i.checked = val })
  isIndeterminate.value = false
  // 批量更新后端（并行请求）
  await Promise.all(cartItems.value.filter(i => !i.invalid).map(item =>
    checkCartItem({ cartItemId: item.cart.id, checked: val ? 1 : 0 }).catch(() => {})
  ))
  cartStore.setList(cartItems.value.map(i => i.cart))
  updateSelectAllState()
}

async function onItemCheck(item) {
  updateSelectAllState()
  try {
    await checkCartItem({ cartItemId: item.cart.id, checked: item.checked ? 1 : 0 })
    // 勾选不影响数量，缓存角标本地同步即可（不发请求）
    cartStore.setList(cartItems.value.map(i => i.cart))
  } catch { /* 回滚由用户手动处理 */ }
}

async function onQtyChange(item) {
  try {
    await updateCartItem(item.cart.id, { quantity: item.cart.quantity })
    cartStore.setList(cartItems.value.map(i => i.cart))
  } catch (e) {
    ElMessage.error('更新数量失败')
  }
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm('确定要删除该商品吗？', '提示', { type: 'warning' })
    await deleteCartItem(item.cart.id)
    cartItems.value = cartItems.value.filter(i => i.cart.id !== item.cart.id)
    updateSelectAllState()
    cartStore.setList(cartItems.value.map(i => i.cart))
    ElMessage.success('已删除')
  } catch { /* 取消 */ }
}

async function removeChecked() {
  const checked = cartItems.value.filter(i => i.checked)
  if (!checked.length) return
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${checked.length} 件商品吗？`, '提示', { type: 'warning' })
    // 并行删除
    await Promise.all(checked.map(item => deleteCartItem(item.cart.id).catch(() => {})))
    cartItems.value = cartItems.value.filter(i => !i.checked)
    updateSelectAllState()
    cartStore.setList(cartItems.value.map(i => i.cart))
    ElMessage.success('已删除')
  } catch { /* 取消 */ }
}

function goCheckout() {
  router.push({ name: 'checkout' })
}

</script>

<style scoped>
.cart-page { max-width: 1200px; margin: 0 auto; }
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }
.cart-main { background: #1b1b1e; border-radius: 8px; overflow: hidden; }
.cart-header { padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,.08); font-size: 13px; color: #8b96ab; }
.cart-header-hint b { color: #ff5000; }
.cart-list { padding: 0 16px; }
.cart-item { display: flex; align-items: center; gap: 12px; padding: 16px 0; border-bottom: 1px solid rgba(255,255,255,.08); }
.item-check { flex-shrink: 0; }
.item-image { width: 80px; height: 80px; border-radius: 4px; overflow: hidden; cursor: pointer; background: #232327; flex-shrink: 0; }
.item-image img { width: 100%; height: 100%; object-fit: cover; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 14px; color: #c3cbda; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-name:hover { color: #ff5000; }
.item-sku { font-size: 12px; color: #5c6a82; }
.item-price { width: 100px; text-align: center; font-size: 14px; font-weight: 600; color: #ff5000; }
.item-qty { width: 120px; display: flex; justify-content: center; }
.item-subtotal { width: 100px; text-align: center; font-size: 14px; font-weight: 600; color: #ff5000; }
.item-del { flex-shrink: 0; }

.cart-footer { display: flex; justify-content: space-between; align-items: center; padding: 16px; background: #232327; border-top: 1px solid rgba(255,255,255,.08); }
.footer-check { display: flex; align-items: center; gap: 16px; }
.del-selected { font-size: 13px; color: #5c6a82; cursor: pointer; }
.del-selected:hover { color: #ff5000; }
.footer-right { display: flex; align-items: center; gap: 12px; }
.total-label { font-size: 14px; color: #8b96ab; }
.total-label b { color: #ff5000; }
.total-price { font-size: 24px; font-weight: 700; color: #ff5000; font-family: var(--font-tech); }
.checkout-btn { background: #ff5000; border-color: #ff5000; padding: 12px 40px; font-size: 16px; }

/* 响应式：窄屏购物车行换行、隐藏次要列 */
@media (max-width: 768px) {
  .cart-item { flex-wrap: wrap; }
  .item-price, .item-subtotal { width: auto; }
  .item-subtotal { display: none; }
  .item-info { flex: 1 1 100%; order: 2; }
}
</style>
