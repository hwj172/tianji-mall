<template>
  <div class="seller-refund-page" v-loading="loading">
    <h2>退款处理</h2>
    <p class="sub-tip">买家已寄回、等待你确认收货的退货单</p>

    <el-empty v-if="!loading && !refunds.length" description="暂无待确认的退货单" />
    <div class="refund-list" v-else>
      <div class="refund-card" v-for="r in refunds" :key="r.id">
        <div class="rc-header">
          <span class="rc-id">退款ID：{{ r.id }}</span>
          <span class="rc-order">关联订单：{{ r.orderId }}</span>
          <span class="rc-type">{{ typeText(r.refundType) }}</span>
          <span class="rc-amount">¥{{ fmtPrice(r.amount) }}</span>
        </div>
        <div class="rc-meta">
          买家：用户 #{{ r.userId }} · 物流：{{ r.trackingCompany || '-' }} {{ r.trackingNumber || '-' }}
        </div>
        <div class="rc-reason" v-if="r.reason">退款原因：{{ r.reason }}</div>
        <div class="rc-actions">
          <el-button size="small" type="success" @click="confirmReceive(r)">确认收货并退款</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSellerPendingRefunds, receiveRefund } from '@/api'
import { fmtPrice } from '@/utils/format'

const refunds = ref([])
const loading = ref(false)

const typeMap = { REFUND_ONLY: '仅退款', RETURN_REFUND: '退货退款' }
function typeText(t) { return typeMap[t] || t || '' }

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getSellerPendingRefunds()
    refunds.value = res.data || []
  } catch { /* interceptor 统一处理 */ }
  finally { loading.value = false }
}

async function confirmReceive(r) {
  try { await ElMessageBox.confirm('确认已收到退货？确认后将执行退款。', '提示', { type: 'warning' }) } catch { return }
  try {
    await receiveRefund(r.id)
    ElMessage.success('已确认收货，退款处理中')
    refunds.value = refunds.value.filter(x => x.id !== r.id)
  } catch { /* interceptor 统一处理 */ }
}
</script>

<style scoped>
.seller-refund-page h2 { margin-bottom: 8px; }
.sub-tip { font-size: 13px; color: #5c6a82; margin-bottom: 16px; }
.refund-list { display: flex; flex-direction: column; gap: 12px; }
.refund-card { background: #121826; border-radius: 8px; padding: 16px 20px; }
.rc-header { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 1px solid rgba(255,255,255,.08); }
.rc-id { font-weight: 600; color: #c3cbda; }
.rc-order { color: #8b96ab; flex: 1; font-size: 13px; }
.rc-type { font-size: 12px; color: #5c6a82; }
.rc-amount { font-size: 18px; font-weight: 700; color: #ff5000; }
.rc-meta { font-size: 13px; color: #8b96ab; margin-top: 10px; }
.rc-reason { font-size: 13px; color: #5c6a82; margin-top: 6px; }
.rc-actions { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
