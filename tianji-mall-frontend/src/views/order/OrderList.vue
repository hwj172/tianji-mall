<template>
  <div class="order-list-page">
    <h2 class="page-title">我的订单</h2>

    <!-- 状态 Tab -->
    <div class="status-tabs">
      <span
        v-for="tab in tabs" :key="tab.key"
        class="tab-item" :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >{{ tab.label }}</span>
    </div>

    <!-- 订单列表 -->
    <div v-if="filteredOrders.length" class="order-list">
      <div class="order-card" v-for="order in filteredOrders" :key="order.id" @click="$router.push(`/order/${order.id}`)">
        <div class="oc-header">
          <span class="oc-no">订单号：{{ order.orderNo }}</span>
          <el-tag :type="statusTag(order.status)" size="small">{{ statusText(order.status) }}</el-tag>
          <span class="oc-time">{{ formatTime(order.createTime) }}</span>
        </div>
        <div class="oc-body">
          <div class="oc-info">
            <span class="oc-amount">¥{{ order.totalAmount }}</span>
            <span class="oc-pay">{{ order.payType === 1 ? '支付宝' : '—' }}</span>
          </div>
          <div class="oc-actions" @click.stop>
            <el-button size="small" @click="$router.push(`/order/${order.id}`)">查看详情</el-button>
            <el-button v-if="order.status === 1" size="small" type="primary" @click="handlePay(order)">去支付</el-button>
            <el-button v-if="order.status === 1" size="small" @click="handleCancel(order)">取消</el-button>
            <el-button v-if="order.status === 3" size="small" type="success" @click="handleReceive(order)">确认收货</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="暂无订单" />

    <!-- 分页 -->
    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadOrders"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, cancelOrder, createPay, receiveOrder } from '@/api'
import { submitPayForm } from '@/utils/pay'

const router = useRouter()

const orders = ref([])
const loading = ref(false)
const activeTab = ref('all')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const tabs = [
  { key: 'all', label: '全部' },
  { key: '1', label: '待付款' },
  { key: '2', label: '待发货' },
  { key: '3', label: '待收货' },
  { key: '4', label: '待评价' },
  { key: '5', label: '已取消' }
]

const statusMap = { 1: '待付款', 2: '已付款', 3: '已发货', 4: '已完成', 5: '已取消' }

function statusText(s) { return statusMap[s] || '未知' }
function statusTag(s) {
  const m = { 1: 'warning', 2: '', 3: '', 4: 'success', 5: 'info' }
  return m[s] || ''
}

const filteredOrders = computed(() => {
  if (activeTab.value === 'all') return orders.value
  return orders.value.filter(o => o.status === Number(activeTab.value))
})

onMounted(() => loadOrders())

async function loadOrders() {
  loading.value = true
  try {
    const res = await getOrderList({ page: currentPage.value, size: pageSize.value })
    const data = res.data || {}
    orders.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    console.error('加载订单列表失败', e)
  } finally {
    loading.value = false
  }
}

async function handleCancel(order) {
  try { await ElMessageBox.confirm('确定取消该订单？', '提示', { type: 'warning' }) } catch { return }
  try {
    await cancelOrder(order.id)
    ElMessage.success('已取消')
    await loadOrders()
  } catch { /* handle by interceptor */ }
}

async function handlePay(order) {
  try {
    const res = await createPay({ orderId: order.id })
    if (res.data?.payForm) {
      submitPayForm(res.data.payForm)
    } else {
      ElMessage.warning('未获取到支付表单，请重试')
    }
  } catch { /* handle by interceptor */ }
}

async function handleReceive(order) {
  try { await ElMessageBox.confirm('确认已收到商品？', '提示', { type: 'warning' }) } catch { return }
  try {
    await receiveOrder(order.id)
    ElMessage.success('已确认收货')
    await loadOrders()
  } catch { /* handle by interceptor */ }
}

function formatTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) t = t[0] + 'T' + t[1]
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.order-list-page { max-width: 1100px; margin: 0 auto; }
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }

.status-tabs { display: flex; gap: 0; background: #fff; border-radius: 8px; padding: 0 16px; margin-bottom: 12px; }
.tab-item { padding: 14px 20px; font-size: 14px; cursor: pointer; border-bottom: 2px solid transparent; color: #666; transition: all .2s; }
.tab-item:hover { color: #ff5000; }
.tab-item.active { color: #ff5000; border-color: #ff5000; font-weight: 600; }

.order-card { background: #fff; border-radius: 8px; padding: 16px 20px; margin-bottom: 12px; cursor: pointer; transition: box-shadow .2s; }
.order-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.06); }
.oc-header { display: flex; align-items: center; gap: 12px; padding-bottom: 12px; border-bottom: 1px solid #f5f5f5; }
.oc-no { font-size: 13px; color: #666; flex: 1; }
.oc-time { font-size: 12px; color: #999; }

.oc-body { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; }
.oc-amount { font-size: 20px; font-weight: 700; color: #ff5000; margin-right: 10px; }
.oc-pay { font-size: 12px; color: #999; }
.oc-actions { display: flex; gap: 8px; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
