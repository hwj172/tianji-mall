<template>
  <div class="checkout-page" v-loading="loading">
    <h2 class="page-title">确认订单</h2>

    <div class="checkout-main" v-if="items.length">
      <!-- 收货地址 -->
      <div class="section">
        <h3 class="section-title">收货地址</h3>
        <div class="address-list">
          <div
            v-for="addr in addresses" :key="addr.id"
            class="addr-card"
            :class="{ active: selectedAddressId === addr.id }"
            @click="selectedAddressId = addr.id"
          >
            <div class="addr-radio"><span class="radio-dot" v-if="selectedAddressId === addr.id" /></div>
            <div class="addr-body">
              <div class="addr-contact">
                <b>{{ addr.receiverName }}</b>
                <span>{{ addr.phone }}</span>
                <el-tag v-if="addr.isDefault" size="small" type="danger">默认</el-tag>
              </div>
              <p class="addr-text">{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}</p>
            </div>
          </div>
        </div>
        <el-button text type="primary" @click="openAddAddress" class="add-addr-btn">
          + 添加新地址
        </el-button>
      </div>

      <!-- 商品明细 -->
      <div class="section">
        <h3 class="section-title">商品明细</h3>
        <div class="item-list">
          <OrderItemRow v-for="item in items" :key="buyParams ? item.product.id : item.cart.id" :item="item" />
        </div>
      </div>

      <!-- 满减活动（自动应用，无需选择） -->
      <div class="section" v-if="availablePromotions.length">
        <h3 class="section-title">满减活动</h3>
        <div class="promotion-options">
          <div
            v-for="p in applicablePromotions" :key="p.id"
            class="promotion-option active"
          >
            <div class="po-name">{{ p.name }}</div>
            <div class="po-value">满 ¥{{ fmtPrice(p.threshold) }} 减 ¥{{ fmtPrice(p.discount) }}</div>
          </div>
          <div
            v-for="p in notMetPromotions" :key="'nm-' + p.id"
            class="promotion-option"
          >
            <div class="po-name">{{ p.name }}</div>
            <div class="po-value">满 ¥{{ fmtPrice(p.threshold) }} 减 ¥{{ fmtPrice(p.discount) }}</div>
            <div class="po-hint">还差 ¥{{ fmtPrice(p.threshold - Number(totalPrice)) }}</div>
          </div>
        </div>
      </div>

      <!-- 优惠券 -->
      <div class="section" v-if="availableCoupons.length">
        <h3 class="section-title">优惠券</h3>
        <div class="coupon-options">
          <div
            v-for="c in availableCoupons" :key="c.userCouponId"
            class="coupon-option" :class="{ active: selectedCouponId === c.userCouponId }"
            @click="selectedCouponId = selectedCouponId === c.userCouponId ? null : c.userCouponId"
          >
            <div class="co-amount">
              <template v-if="c.discountType === 'FIXED'">
                <span class="co-currency">¥</span><span class="co-num">{{ c.discountValue }}</span>
              </template>
              <template v-else>
                <span class="co-num">{{ formatDiscount(c.discountValue).replace('折', '') }}</span><span class="co-currency">折</span>
              </template>
            </div>
            <div class="co-body">
              <div class="co-name">{{ c.name }}</div>
              <div class="co-cond">{{ c.minOrderAmount > 0 ? `满 ¥${fmtPrice(c.minOrderAmount)} 可用` : '无门槛' }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部结算 -->
      <div class="checkout-footer">
        <div class="footer-summary">
          <span>共 <b>{{ totalCount }}</b> 件，合计：</span>
          <span v-if="totalDiscount > 0" class="footer-original">¥{{ fmtPrice(totalPrice) }}</span>
          <span class="footer-total">¥{{ fmtPrice(payPrice) }}</span>
          <span v-if="totalDiscount > 0" class="footer-coupon">已优惠 ¥{{ fmtPrice(totalDiscount) }}</span>
        </div>
        <el-button type="primary" size="large" @click="submitOrder" :loading="submitting" class="submit-btn">
          提交订单
        </el-button>
      </div>
    </div>

    <EmptyState v-else-if="!loading && !items.length" description="没有待结算的商品">
      <el-button type="primary" @click="$router.push('/cart')">返回购物车</el-button>
    </EmptyState>

    <!-- 添加地址 Dialog -->
    <el-dialog v-model="showAddAddress" title="添加收货地址" width="500px">
      <el-form :model="newAddr" label-width="80px">
        <el-form-item label="收货人"><el-input v-model="newAddr.receiverName" placeholder="请输入收货人姓名" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="newAddr.phone" placeholder="请输入手机号" /></el-form-item>
        <el-form-item label="所在地区">
          <el-cascader
            v-model="regionPath"
            :options="regionTree"
            :props="{ value: 'name', label: 'name', children: 'children' }"
            placeholder="请选择省/市/区"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="详细地址"><el-input v-model="newAddr.detail" placeholder="街道/门牌号" /></el-form-item>
        <el-form-item><el-checkbox v-model="newAddr.isDefault">设为默认地址</el-checkbox></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveAddress" :loading="savingAddr">保存</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
// 地区树懒加载缓存（模块级）：session 内首次拉取后复用，仅在打开「添加地址」弹窗时触发
let regionTreePromise = null
</script>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCartList, getProductBatch, getProductDetail, getAddressList, addAddress, createOrder, getRegionTree, getMyCoupons, getCurrentPromotions } from '@/api'
import { useCartStore } from '@/stores/cart'
import { fmtPrice } from '@/utils/format'
import { getFirstImage } from '@/utils/image'
import { formatDiscount } from '@/utils/discount'
import EmptyState from '@/components/common/EmptyState.vue'
import OrderItemRow from '@/components/common/OrderItemRow.vue'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const addresses = ref([])
const selectedAddressId = ref(null)
const items = ref([])
const submitting = ref(false)
const loading = ref(false)

// 新增地址
const showAddAddress = ref(false)
const savingAddr = ref(false)
const newAddr = reactive({
  receiverName: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false
})

const regionTree = ref([])
const regionPath = ref([])

const buyParams = computed(() => route.query.mode === 'buy' ? {
  productId: Number(route.query.productId),
  skuId: route.query.skuId ? Number(route.query.skuId) : null,
  qty: Number(route.query.qty) || 1
} : null)

const totalCount = computed(() => items.value.reduce((s, i) => s + i.cart.quantity, 0))
const totalPrice = computed(() => {
  return items.value.reduce((s, i) => s + i.price * i.cart.quantity, 0).toFixed(2)
})

// ====== 优惠券 ======
const coupons = ref([])
const selectedCouponId = ref(null)

// 未使用 + 满足最低消费门槛的券
const availableCoupons = computed(() => {
  const total = Number(totalPrice.value)
  return coupons.value.filter(c =>
    c.status === 'UNUSED' && (!c.minOrderAmount || c.minOrderAmount <= total)
  )
})

const couponDiscount = computed(() => {
  const c = availableCoupons.value.find(x => x.userCouponId === selectedCouponId.value)
  if (!c) return 0
  const total = Number(totalPrice.value)
  if (c.discountType === 'FIXED') return Math.min(Number(c.discountValue), total)
  // PERCENT: 0.8 = 8折，优惠 = 金额 × (1 - 0.8)；兼容旧数据 discountValue=5（5折）→ 归一化 0.5
  let rate = Number(c.discountValue)
  if (rate > 1) rate = rate / 10
  return Math.round(total * (1 - rate) * 100) / 100
})

// ====== 满减活动 ======
const promotions = ref([])

// API 只返回当前有效活动，前端按门槛过滤
const applicablePromotions = computed(() => {
  const total = Number(totalPrice.value)
  return promotions.value.filter(p => Number(p.threshold) <= total)
})
const notMetPromotions = computed(() => {
  const total = Number(totalPrice.value)
  return promotions.value.filter(p => Number(p.threshold) > total)
})
const availablePromotions = computed(() => promotions.value)

// 满减自动应用：取满足门槛的最大减免，不超过订单金额（与后端 calculateDiscount 一致）
const promotionDiscount = computed(() => {
  const total = Number(totalPrice.value)
  let best = 0
  for (const p of applicablePromotions.value) {
    best = Math.max(best, Math.min(Number(p.discount), total))
  }
  return best
})

// 满减 + 优惠券叠加后总优惠封顶到订单总额，避免合计超总额导致负数应付
const totalDiscount = computed(() => {
  const total = Number(totalPrice.value)
  return Math.min(promotionDiscount.value + couponDiscount.value, total)
})
const payPrice = computed(() => (Number(totalPrice.value) - totalDiscount.value).toFixed(2))

async function loadPromotions() {
  try {
    const res = await getCurrentPromotions()
    promotions.value = res.data || []
  } catch { /* ignore */ }
}

async function loadCoupons() {
  try {
    const res = await getMyCoupons()
    coupons.value = res.data || []
  } catch { /* ignore */ }
}

// 懒加载地区树：打开弹窗时才拉取，模块级 Promise 缓存复用（失败重置下次可重试）
function loadRegionTree() {
  if (!regionTreePromise) {
    regionTreePromise = getRegionTree()
      .then(res => { regionTree.value = res.data || [] })
      .catch(err => {
        regionTreePromise = null
        return null
      })
  }
  return regionTreePromise
}

function openAddAddress() {
  showAddAddress.value = true
  loadRegionTree()
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([loadAddresses(), loadCoupons(), loadPromotions()])
    if (buyParams.value) await loadBuyItem()
    else await loadItems()
  } finally {
    loading.value = false
  }
})

async function loadBuyItem() {
  try {
    const { productId, skuId, qty } = buyParams.value
    const res = await getProductDetail(productId)
    const p = res.data.product
    if (!p) { items.value = []; return }
    let price = p.price
    let specs = ''
    // 秒杀窗口内以秒杀价展示（与服务端 createOrder 扣款口径一致）
    const now = Date.now()
    if (p.seckillPrice != null && now >= parseTs(p.seckillStartTime) && now <= parseTs(p.seckillEndTime)) {
      price = p.seckillPrice
    }
    if (skuId && res.data.skuMatrix) {
      // skuMatrix value 结构为 {skuId, price, stock}（buildSpecSelectorData）
      const found = Object.entries(res.data.skuMatrix).find(([, s]) => s.skuId === skuId)
      if (found) {
        const [key, sku] = found
        price = sku.price != null ? sku.price : price
        specs = sku.specs || key
      }
    }
    items.value = [{
      cart: { id: 0, quantity: qty },
      product: p,
      image: getFirstImage(p.images),
      price,
      specs
    }]
  } catch (e) {
    console.error('加载直购商品失败', e)
  }
}

async function loadAddresses() {
  try {
    const res = await getAddressList()
    addresses.value = res.data || []
    const dft = addresses.value.find(a => a.isDefault)
    selectedAddressId.value = dft?.id || addresses.value[0]?.id || null
  } catch { /* handled by interceptor */ }
}

// 兼容 LocalDateTime 数组与 ISO 字符串，返回时间戳（秒杀窗口判断用）
function parseTs(t) {
  if (!t) return 0
  if (Array.isArray(t)) {
    const [y, m, d, h = 0, mi = 0, s = 0] = t
    return new Date(y, (m || 1) - 1, d || 1, h, mi, s).getTime()
  }
  return new Date(t).getTime()
}

async function loadItems() {
  try {
    const res = await getCartList()
    const cartItems = (res.data || []).filter(ci => ci.checked === 1)
    if (!cartItems.length) return

    const productIds = [...new Set(cartItems.map(i => i.productId))]
    const pRes = await getProductBatch(productIds)
    const productMap = {}
    if (pRes.data) pRes.data.forEach(p => { productMap[p.id] = p })

    const validItems = cartItems.filter(ci => productMap[ci.productId])
    if (validItems.length < cartItems.length) {
      ElMessage.warning(`有 ${cartItems.length - validItems.length} 件商品已失效，已移除结算`)
    }
    items.value = validItems.map(ci => {
      const product = productMap[ci.productId]
      return {
        cart: ci,
        product,
        image: getFirstImage(product.images),
        price: ci.price ?? product.price ?? 0,
        specs: ci.skuSpecs || ''
      }
    })
  } catch (e) {
    console.error('加载结算商品失败', e)
  }
}

async function saveAddress() {
  const [province, city, district] = regionPath.value || []
  if (!newAddr.receiverName || !newAddr.phone || !province || !newAddr.detail) {
    ElMessage.warning('请填写完整地址信息')
    return
  }
  savingAddr.value = true
  try {
    await addAddress({
      receiverName: newAddr.receiverName,
      phone: newAddr.phone,
      province: province || '',
      city: city || '',
      district: district || '',
      detail: newAddr.detail,
      isDefault: newAddr.isDefault ? 1 : 0
    })
    showAddAddress.value = false
    ElMessage.success('地址已添加')
    await loadAddresses()
    Object.assign(newAddr, { receiverName: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })
    regionPath.value = []
  } catch { /* handled by interceptor */ }
  finally { savingAddr.value = false }
}

async function submitOrder() {
  if (!selectedAddressId.value) { ElMessage.warning('请选择收货地址'); return }
  if (!items.value.length) { ElMessage.warning('没有待结算的商品'); return }

  submitting.value = true
  try {
    const payload = buyParams.value
      ? { addressId: selectedAddressId.value, directItems: [{ productId: buyParams.value.productId, skuId: buyParams.value.skuId, quantity: buyParams.value.qty }], couponId: selectedCouponId.value || undefined }
      : { addressId: selectedAddressId.value, cartItemIds: items.value.map(i => i.cart.id), couponId: selectedCouponId.value || undefined }
    const res = await createOrder(payload)
    ElMessage.success('下单成功')
    // 下单消耗了购物车商品，本地缓存已过期，强制重新拉取保证角标准确
    cartStore.refreshCount(true)
    router.push({ name: 'orderDetail', params: { id: res.data.id } })
  } catch { /* handled by interceptor */ }
  finally { submitting.value = false }
}

</script>

<style scoped>
.checkout-page { max-width: 1200px; margin: 0 auto; }
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }

.section { background: #1b1b1e; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.section-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid rgba(255,255,255,.08); }

/* 地址 */
.address-list { display: grid; gap: 10px; }
.addr-card { display: flex; gap: 12px; border: 2px solid rgba(255,255,255,.08); border-radius: 8px; padding: 14px; cursor: pointer; transition: border-color .2s; }
.addr-card:hover, .addr-card.active { border-color: #ff5000; }
.addr-radio { width: 20px; height: 20px; border: 2px solid rgba(255,255,255,.08); border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2px; }
.address-list .addr-card.active .addr-radio { border-color: #ff5000; }
.radio-dot { width: 10px; height: 10px; background: #ff5000; border-radius: 50%; }
.addr-contact { display: flex; align-items: center; gap: 8px; font-size: 14px; margin-bottom: 4px; }
.addr-text { font-size: 13px; color: #71717a; }
.add-addr-btn { margin-top: 10px; }

/* 商品明细：行内结构与样式已收敛到公共组件 OrderItemRow */
.item-list { display: flex; flex-direction: column; gap: 12px; }

/* 底部 */
.checkout-footer { background: #1b1b1e; border-radius: 8px; padding: 16px 20px; display: flex; justify-content: flex-end; align-items: center; gap: 16px; }
.footer-summary { font-size: 14px; color: #71717a; }
.footer-summary b { color: #ff5000; }
.footer-total { font-size: 24px; font-weight: 700; color: #ff5000; }
.submit-btn { background: #ff5000; border-color: #ff5000; padding: 12px 48px; font-size: 16px; }
.footer-original { color: #52525b; text-decoration: line-through; font-size: 13px; }
.footer-coupon { color: #ff5000; font-size: 13px; }

/* 优惠券票样式：左面额大数字 + 虚线分隔 + 右券信息 */
.coupon-options { display: flex; flex-wrap: wrap; gap: 12px; }
.coupon-option { border: 1px solid rgba(255,255,255,.12); border-radius: 8px; cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .15s; display: flex; align-items: stretch; min-width: 180px; overflow: hidden; background: rgba(255,255,255,.02); }
.coupon-option:hover { border-color: #ff5000; transform: translateY(-1px); }
.coupon-option.active { border-color: #ff5000; box-shadow: 0 0 16px rgba(255, 80, 0, .25); }
.co-amount { width: 78px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; gap: 1px; background: rgba(255, 80, 0, .12); border-right: 1px dashed rgba(255, 80, 0, .45); color: #ff5000; font-family: var(--font-tech); }
.co-currency { font-size: 13px; }
.co-num { font-size: 22px; font-weight: 700; }
.co-body { padding: 10px 12px; display: flex; flex-direction: column; justify-content: center; gap: 4px; min-width: 0; }
.co-name { font-size: 13px; font-weight: 600; color: #a1a1aa; }
.co-cond { font-size: 12px; color: #52525b; }

/* 满减活动（自动应用） */
.promotion-options { display: flex; flex-wrap: wrap; gap: 12px; }
.promotion-option { border: 1px solid rgba(255,255,255,.08); border-radius: 8px; padding: 10px 14px; display: flex; flex-direction: column; gap: 4px; min-width: 160px; }
.promotion-option.active { border-color: #ff5000; background: rgba(255, 80, 0, .12); }
.po-name { font-size: 14px; font-weight: 600; }
.po-value { color: #ff5000; font-weight: 700; font-size: 14px; }
.po-hint { font-size: 12px; color: #52525b; }

.region-row { display: flex; gap: 8px; }
.region-input { flex: 1; }

/* 响应式：移动端结算栏堆叠、券票全宽、容器留白收缩 */
@media (max-width: 768px) {
  .checkout-page { padding: 0 12px; }
  .section { padding: 16px; }
  .checkout-footer { flex-direction: column; align-items: stretch; gap: 12px; padding: 14px; }
  .footer-summary { text-align: center; }
  .submit-btn { width: 100%; }
  .coupon-option, .promotion-option { min-width: 100%; }
}
</style>
