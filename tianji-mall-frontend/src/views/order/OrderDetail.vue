<template>
  <div class="order-detail-page" v-if="order">
    <!-- 状态横幅 -->
    <div class="status-bar" :class="'status-' + order.status">
      <div class="status-icon">{{ statusInfo.icon }}</div>
      <div class="status-text">
        <h3>{{ statusInfo.title }}</h3>
        <p>{{ statusInfo.desc }}</p>
      </div>
      <div class="status-actions" v-if="order.status === 1">
        <el-button @click="handleCancel" :loading="cancelling">取消订单</el-button>
        <el-button type="primary" @click="goPay" :loading="paying">去支付</el-button>
      </div>
      <div class="status-actions" v-if="order.status === 3">
        <el-button type="primary" @click="confirmReceive" :loading="receiving">确认收货</el-button>
      </div>
      <div class="status-actions" v-if="order.status >= 2 && order.status <= 4">
        <el-button @click="openRefundDialog">申请退款</el-button>
      </div>
    </div>

    <!-- 物流信息 -->
    <div class="section" v-if="logistics.length">
      <h3 class="section-title">📦 物流信息</h3>
      <el-timeline>
        <el-timeline-item
          v-for="t in logistics" :key="t.id"
          :timestamp="formatTime(t.trackTime)"
          placement="top"
        >
          <p>{{ t.description }}</p>
          <span class="logistics-location">{{ t.location }}</span>
        </el-timeline-item>
      </el-timeline>
      <p class="logistics-summary" v-if="order.logisticsCompany">
        {{ order.logisticsCompany }} · {{ order.trackingNumber }}
      </p>
    </div>

    <!-- 收货地址 -->
    <div class="section" v-if="detail.address">
      <h3 class="section-title">📍 收货地址</h3>
      <div class="addr-info">
        <b>{{ detail.address.receiverName }}</b>
        <span class="addr-phone">{{ detail.address.phone }}</span>
        <p>{{ detail.address.province }} {{ detail.address.city }} {{ detail.address.district }} {{ detail.address.detail }}</p>
      </div>
    </div>

    <!-- 商品列表 -->
    <div class="section">
      <h3 class="section-title">🛍 商品信息</h3>
      <div class="item-list">
        <div class="od-item" v-for="(item, idx) in detail.items" :key="idx">
          <img :src="getItemImage(item.productId)" class="od-item-img" @error="onImgError" style="width:72px;height:72px;object-fit:cover" />
          <div class="od-item-info">
            <router-link :to="`/product/${item.productId}`" class="od-item-name">{{ item.productName }}</router-link>
            <span class="od-item-spec" v-if="item.skuSpecs">{{ item.skuSpecs }}</span>
          </div>
          <div class="od-item-price">¥{{ item.price }}</div>
          <div class="od-item-qty">×{{ item.quantity }}</div>
          <div class="od-item-subtotal">¥{{ (item.price * item.quantity).toFixed(2) }}</div>
        </div>
      </div>
    </div>

    <!-- 订单概要 -->
    <div class="section order-summary">
      <h3 class="section-title">📋 订单信息</h3>
      <div class="summary-grid">
        <div class="summary-row"><span>订单编号</span><b>{{ order.orderNo }}</b></div>
        <div class="summary-row"><span>创建时间</span><span>{{ formatTime(order.createTime) }}</span></div>
        <div class="summary-row"><span>订单状态</span><el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag></div>
        <div class="summary-row"><span>支付方式</span><span>{{ order.payType === 1 ? '支付宝' : '—' }}</span></div>
        <div class="summary-row total-row"><span>订单总额</span><b class="total-amount">¥{{ order.totalAmount }}</b></div>
      </div>
    </div>
  </div>

  <el-empty v-else-if="!loading" description="订单不存在" />

  <!-- 申请退款 Dialog -->
  <el-dialog v-model="refundVisible" title="申请退款" width="420px">
    <el-form label-width="80px">
      <el-form-item label="退款类型">
        <el-radio-group v-model="refundType">
          <el-radio value="REFUND_ONLY">仅退款</el-radio>
          <el-radio value="RETURN_REFUND">退货退款</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="退款原因">
        <el-input v-model="refundReason" type="textarea" :rows="3" placeholder="请填写退款原因" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="refundVisible = false">取消</el-button>
      <el-button type="danger" @click="handleRefund" :loading="refunding">提交退款申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, getProductBatch, getOrderLogistics, createPay, cancelOrder, receiveOrder, requestRefund } from '@/api'
import { submitPayForm } from '@/utils/pay'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const detail = reactive({ address: null, items: [] })
const logistics = ref([])
const loading = ref(false)
const paying = ref(false)
const cancelling = ref(false)
const receiving = ref(false)
const refunding = ref(false)
const refundVisible = ref(false)
const refundType = ref('REFUND_ONLY')
const refundReason = ref('')
const productImages = ref({})

const statusMap = {
  1: { title: '等待付款', desc: '请尽快完成支付，超时订单将自动取消', tagType: 'warning', icon: '🕐' },
  2: { title: '已付款', desc: '商家正在备货中，请耐心等待', tagType: 'primary', icon: '📦' },
  3: { title: '已发货', desc: '商品正在派送中', tagType: 'primary', icon: '🚚' },
  4: { title: '交易完成', desc: '感谢您的购买，欢迎再次光临', tagType: 'success', icon: '✅' },
  5: { title: '已取消', desc: '该订单已取消', tagType: 'info', icon: '❌' }
}

const statusInfo = computed(() => statusMap[order.value?.status] || statusMap[1])
const statusText = computed(() => statusMap[order.value?.status]?.title || '未知')
const statusTagType = computed(() => {
  const m = { 1: 'warning', 2: '', 3: '', 4: 'success', 5: 'info' }
  return m[order.value?.status] || ''
})

onMounted(() => loadDetail())

async function loadDetail() {
  loading.value = true
  try {
    const res = await getOrderDetail(route.params.id)
    if (res.data) {
      order.value = res.data.order
      detail.address = res.data.address
      detail.items = res.data.items || []

      // 加载商品图片
      const productIds = [...new Set(detail.items.map(i => i.productId))]
      if (productIds.length) {
        try {
          const pRes = await getProductBatch(productIds)
          if (pRes.data) {
            pRes.data.forEach(p => {
              productImages.value[p.id] = getFirstImage(p.images)
            })
          }
        } catch { /* best-effort */ }
      }

      // 已发货 → 加载物流
      if (order.value.status >= 3) {
        try {
          const lRes = await getOrderLogistics(order.value.id)
          logistics.value = lRes.data || []
        } catch { /* best-effort */ }
      }
    }
  } catch (e) {
    console.error('加载订单详情失败', e)
  } finally {
    loading.value = false
  }
}

function getItemImage(productId) {
  return productImages.value[productId] || ''
}

function getFirstImage(images) {
  if (!images) return ''
  try {
    const arr = typeof images === 'string' ? JSON.parse(images) : images
    return arr[0] || ''
  } catch { return '' }
}

async function goPay() {
  paying.value = true
  try {
    const res = await createPay({ orderId: order.value.id, returnUrl: window.location.origin + '/order/list' })
    if (res.data?.payForm) {
      submitPayForm(res.data.payForm)
    } else {
      ElMessage.warning('未获取到支付表单，请重试')
    }
  } catch { /* handle by interceptor */ }
  finally { paying.value = false }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', { type: 'warning' })
  } catch { return }

  cancelling.value = true
  try {
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    await loadDetail()
  } catch { /* handle by interceptor */ }
  finally { cancelling.value = false }
}

function openRefundDialog() {
  refundType.value = 'REFUND_ONLY'
  refundReason.value = ''
  refundVisible.value = true
}

async function handleRefund() {
  if (!refundReason.value.trim()) { ElMessage.warning('请填写退款原因'); return }
  refunding.value = true
  try {
    const items = (detail.items || []).map(i => ({
      productId: i.productId,
      skuId: i.skuId,
      quantity: i.quantity
    }))
    await requestRefund(order.value.id, {
      reason: refundReason.value,
      refundType: refundType.value,
      items
    })
    ElMessage.success('退款申请已提交')
    refundVisible.value = false
    await loadDetail()
  } catch { /* handle by interceptor */ }
  finally { refunding.value = false }
}

async function confirmReceive() {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '提示', { type: 'warning' })
  } catch { return }

  receiving.value = true
  try {
    await receiveOrder(order.value.id)
    ElMessage.success('已确认收货')
    await loadDetail()
  } catch { /* handle by interceptor */ }
  finally { receiving.value = false }
}

function formatTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) t = t[0] + 'T' + t[1]
  return new Date(t).toLocaleString('zh-CN')
}

function onImgError(e) {
  e.target.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 72 72"><rect fill="%23f5f5f5" width="72" height="72"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="10">无图</text></svg>'
}
</script>

<style scoped>
.order-detail-page { max-width: 900px; margin: 0 auto; }

/* 状态横幅 */
.status-bar { display: flex; align-items: center; gap: 16px; background: #fff; border-radius: 8px; padding: 24px; margin-bottom: 12px; border-left: 4px solid #eee; }
.status-1 { border-color: #e6a23c; }
.status-2, .status-3 { border-color: #409eff; }
.status-4 { border-color: #67c23a; }
.status-5 { border-color: #999; }
.status-icon { font-size: 40px; }
.status-text { flex: 1; }
.status-text h3 { font-size: 18px; margin-bottom: 4px; }
.status-text p { font-size: 13px; color: #999; }
.status-actions { display: flex; gap: 10px; }

/* 区块 */
.section { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.section-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.order-summary { border-top: none; }

.addr-info b { font-size: 15px; margin-right: 12px; }
.addr-phone { color: #666; font-size: 14px; }
.addr-info p { font-size: 13px; color: #666; margin-top: 4px; }

.logistics-location { font-size: 12px; color: #999; }
.logistics-summary { font-size: 13px; color: #666; margin-top: 8px; }

/* 商品明细 */
.od-item { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid #f5f5f5; }
.od-item:last-child { border-bottom: none; }
.od-item-img { border-radius: 4px; overflow: hidden; background: #fafafa; flex-shrink: 0; }
.od-item-info { flex: 1; min-width: 0; }
.od-item-name { font-size: 14px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.od-item-name:hover { color: #ff5000; }
.od-item-spec { font-size: 12px; color: #999; }
.od-item-price, .od-item-qty, .od-item-subtotal { width: 90px; text-align: center; font-size: 14px; }
.od-item-subtotal { color: #ff5000; font-weight: 600; }

/* 订单概要 */
.summary-grid { display: flex; flex-direction: column; gap: 10px; }
.summary-row { display: flex; justify-content: space-between; font-size: 14px; color: #666; }
.summary-row b { color: #333; }
.total-row { border-top: 1px solid #f0f0f0; padding-top: 10px; font-size: 15px; }
.total-amount { color: #ff5000; font-size: 20px; }
</style>
