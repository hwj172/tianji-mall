<template>
  <div class="cart-page">
    <h2 class="page-title">购物车</h2>

    <!-- 购物车列表 -->
    <div class="cart-main" v-if="cartItems.length">
      <div class="cart-header">
        <el-checkbox v-model="selectAll" @change="onSelectAll" :indeterminate="isIndeterminate">全选</el-checkbox>
      </div>

      <div class="cart-list">
        <div class="cart-item" v-for="item in cartItems" :key="item.cart.id" :class="{ invalid: item.invalid }">
          <el-checkbox v-model="item.checked" @change="onItemCheck(item)" :disabled="item.invalid" class="item-check" />
          <div class="item-image" @click="!item.invalid && $router.push(`/product/${item.product.id}`)">
            <img :src="item.image" :alt="item.product.name || '商品已失效'" @error="onImgError" />
          </div>
          <div class="item-info">
            <router-link v-if="!item.invalid" :to="`/product/${item.product.id}`" class="item-name">{{ item.product.name }}</router-link>
            <span v-else class="item-name invalid-name">商品已失效</span>
            <span class="item-sku" v-if="item.specs">{{ item.specs }}</span>
          </div>
          <div class="item-price">¥{{ item.invalid ? '—' : item.price }}</div>
          <div class="item-qty">
            <el-input-number v-model="item.cart.quantity" :min="1" :max="item.product.stock" size="small" @change="onQtyChange(item)" :disabled="item.invalid" />
          </div>
          <div class="item-subtotal">¥{{ item.invalid ? '—' : (item.price * item.cart.quantity).toFixed(2) }}</div>
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
          <span class="total-price">¥{{ totalPrice }}</span>
          <el-button type="primary" size="large" :disabled="!checkedCount" @click="goCheckout" class="checkout-btn">
            去结算{{ checkedCount ? ` (${checkedCount})` : '' }}
          </el-button>
        </div>
      </div>
    </div>

    <el-empty v-else description="购物车空空如也">
      <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCartList, updateCartItem, deleteCartItem, checkCartItem, getProductBatch } from '@/api'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const cartStore = useCartStore()

// 合并后的数据：[{ cart: CartItem, product: Product, image, price, specs, checked }]
const cartItems = ref([])

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
  try {
    const res = await getCartList()
    const items = res.data || []
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
      return { cart: ci, product, image, price, specs: ci.skuSpecs || '', checked: ci.checked === 1, invalid }
    })
    updateSelectAllState()
  } catch (e) {
    console.error('加载购物车失败', e)
  }
}

function getFirstImage(images) {
  if (!images) return ''
  try {
    const arr = typeof images === 'string' ? JSON.parse(images) : images
    return arr[0] || ''
  } catch { return '' }
}

function updateSelectAllState() {
  const all = cartItems.value.every(i => i.checked)
  const some = cartItems.value.some(i => i.checked)
  selectAll.value = all
  isIndeterminate.value = some && !all
}

async function onSelectAll(val) {
  cartItems.value.forEach(i => { i.checked = val })
  isIndeterminate.value = false
  // 批量更新后端
  for (const item of cartItems.value) {
    try { await checkCartItem({ cartItemId: item.cart.id, checked: val ? 1 : 0 }) }
    catch { /* best-effort */ }
  }
  cartStore.refreshCount()
}

async function onItemCheck(item) {
  updateSelectAllState()
  try {
    await checkCartItem({ cartItemId: item.cart.id, checked: item.checked ? 1 : 0 })
    cartStore.refreshCount()
  } catch { /* 回滚由用户手动处理 */ }
}

async function onQtyChange(item) {
  try {
    await updateCartItem(item.cart.id, { quantity: item.cart.quantity })
    cartStore.refreshCount()
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
    cartStore.refreshCount()
    ElMessage.success('已删除')
  } catch { /* 取消 */ }
}

async function removeChecked() {
  const checked = cartItems.value.filter(i => i.checked)
  if (!checked.length) return
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${checked.length} 件商品吗？`, '提示', { type: 'warning' })
    for (const item of checked) {
      try { await deleteCartItem(item.cart.id) }
      catch { /* continue */ }
    }
    cartItems.value = cartItems.value.filter(i => !i.checked)
    updateSelectAllState()
    cartStore.refreshCount()
    ElMessage.success('已删除')
  } catch { /* 取消 */ }
}

function goCheckout() {
  router.push({ name: 'checkout' })
}

function onImgError(e) {
  e.target.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 80"><rect fill="%23f5f5f5" width="80" height="80"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="10">无图</text></svg>'
}
</script>

<style scoped>
.cart-page { max-width: 1200px; margin: 0 auto; }
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }
.cart-main { background: #fff; border-radius: 8px; overflow: hidden; }
.cart-header { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.cart-list { padding: 0 16px; }
.cart-item { display: flex; align-items: center; gap: 12px; padding: 16px 0; border-bottom: 1px solid #f5f5f5; }
.item-check { flex-shrink: 0; }
.item-image { width: 80px; height: 80px; border-radius: 4px; overflow: hidden; cursor: pointer; background: #fafafa; flex-shrink: 0; }
.item-image img { width: 100%; height: 100%; object-fit: cover; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 14px; color: #333; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-name:hover { color: #ff5000; }
.item-sku { font-size: 12px; color: #999; }
.item-price { width: 100px; text-align: center; font-size: 14px; font-weight: 600; color: #ff5000; }
.item-qty { width: 120px; display: flex; justify-content: center; }
.item-subtotal { width: 100px; text-align: center; font-size: 14px; font-weight: 600; color: #ff5000; }
.item-del { flex-shrink: 0; }

.cart-footer { display: flex; justify-content: space-between; align-items: center; padding: 16px; background: #fafafa; border-top: 1px solid #f0f0f0; }
.footer-check { display: flex; align-items: center; gap: 16px; }
.del-selected { font-size: 13px; color: #999; cursor: pointer; }
.del-selected:hover { color: #ff5000; }
.footer-right { display: flex; align-items: center; gap: 12px; }
.total-label { font-size: 14px; color: #666; }
.total-label b { color: #ff5000; }
.total-price { font-size: 22px; font-weight: 700; color: #ff5000; }
.checkout-btn { background: #ff5000; border-color: #ff5000; padding: 12px 40px; font-size: 16px; }
</style>
