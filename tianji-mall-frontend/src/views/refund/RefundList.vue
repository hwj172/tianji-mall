<template>
  <div class="refund-page" v-loading="loading">
    <h2 class="page-title">退款/售后</h2>

    <!-- 状态筛选 -->
    <div class="refund-tabs">
      <el-radio-group v-model="filterStatus">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="processing">处理中</el-radio-button>
        <el-radio-button value="success">已完成</el-radio-button>
        <el-radio-button value="fail">已失败</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 退款列表 -->
    <div class="refund-list" v-if="filteredRefunds.length">
      <div class="refund-card" v-for="r in filteredRefunds" :key="r.id" @click="showDetail(r.id)">
        <div class="rc-header">
          <span class="rc-id">退款ID：{{ r.id }}</span>
          <span class="rc-order">关联订单：{{ r.orderId }}</span>
          <el-tag :type="typeTag(r.refundType)" size="small">{{ typeText(r.refundType) }}</el-tag>
          <el-tag :type="statusTag(r.status)" size="small">{{ statusText(r.status) }}</el-tag>
          <span class="rc-time">{{ fmtTime(r.createdAt) }}</span>
        </div>
        <div class="rc-body">
          <div class="rc-amount">¥{{ fmtPrice(r.amount) }}</div>
          <div class="rc-actions" @click.stop>
            <!-- 退货退款 + 已寄回 + 卖家/管理员：确认收货 -->
            <el-button
              v-if="userStore.isSeller && r.refundType === 'RETURN_REFUND' && r.returnStatus === 'SHIPPED' && r.status === 'processing'"
              size="small" type="success" @click="handleReceive(r)"
            >确认收货</el-button>
          </div>
        </div>
        <!-- 退货物流信息 -->
        <div class="rc-logistics" v-if="r.refundType === 'RETURN_REFUND' && r.trackingCompany">
          <span>物流：{{ r.trackingCompany }} {{ r.trackingNumber }}</span>
          <el-tag v-if="r.returnStatus" :type="returnStatusTag(r.returnStatus)" size="small">{{ returnStatusText(r.returnStatus) }}</el-tag>
        </div>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="暂无退款记录" />

    <!-- 分页 -->
    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
        background
      />
    </div>

    <!-- 退款详情对话框 -->
    <el-dialog v-model="dialogVisible" title="退款详情" width="560px" @close="detailRefund = null">
      <template v-if="detailRefund">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="退款ID">{{ detailRefund.id }}</el-descriptions-item>
          <el-descriptions-item label="关联订单">{{ detailRefund.orderId }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">¥{{ fmtPrice(detailRefund.amount) }}</el-descriptions-item>
          <el-descriptions-item label="退款类型">
            <el-tag :type="typeTag(detailRefund.refundType)" size="small">{{ typeText(detailRefund.refundType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailRefund.status)" size="small">{{ statusText(detailRefund.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="退货状态" v-if="detailRefund.refundType === 'RETURN_REFUND'">
            <el-tag v-if="detailRefund.returnStatus" :type="returnStatusTag(detailRefund.returnStatus)" size="small">{{ returnStatusText(detailRefund.returnStatus) }}</el-tag>
            <span v-else>未退货</span>
          </el-descriptions-item>
          <el-descriptions-item label="退货快递" v-if="detailRefund.trackingCompany">
            {{ detailRefund.trackingCompany }} {{ detailRefund.trackingNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="退款原因" :span="2">{{ detailRefund.reason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ fmtTime(detailRefund.createdAt) }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailItems && detailItems.length" style="margin-top: 16px;">
          <h4 style="margin-bottom: 8px;">退款商品</h4>
          <div class="refund-item" v-for="item in detailItems" :key="item.id">
            <el-image v-if="item.product" :src="getFirstImage(item.product.images)" class="ri-img" fit="cover" />
            <div class="ri-info">
              <div class="ri-name">{{ item.product?.name || `商品 #${item.productId}` }}</div>
              <div class="ri-meta">{{ item.skuId ? `SKU #${item.skuId} · ` : '' }}x{{ item.quantity }}</div>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyRefunds, getRefundDetail, receiveRefund, getProductBatch } from '@/api'
import { useUserStore } from '@/stores/user'
import { fmtPrice } from '@/utils/format'
import { fmtTime } from '@/utils/date'
import { getFirstImage } from '@/utils/image'

const userStore = useUserStore()

const refunds = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const dialogVisible = ref(false)
const detailRefund = ref(null)
const detailItems = ref([])

// 状态 tab 筛选（本地过滤）
const filterStatus = ref('')
const filteredRefunds = computed(() => {
  if (!filterStatus.value) return refunds.value
  return refunds.value.filter(r => r.status === filterStatus.value)
})

const statusMap = { processing: '处理中', success: '已完成', fail: '失败' }
const typeMap = { REFUND_ONLY: '仅退款', RETURN_REFUND: '退货退款' }
const returnStatusMap = { SHIPPED: '已寄回', RECEIVED: '已收货' }

function statusText(s) { return statusMap[s] || s || '未知' }
function statusTag(s) {
  const m = { processing: 'warning', success: 'success', fail: 'danger' }
  return m[s] || ''
}
function typeText(t) { return typeMap[t] || t || '未知' }
function typeTag(t) {
  const m = { REFUND_ONLY: 'info', RETURN_REFUND: 'warning' }
  return m[t] || ''
}
function returnStatusText(s) { return returnStatusMap[s] || s }
function returnStatusTag(s) {
  const m = { SHIPPED: 'warning', RECEIVED: 'success' }
  return m[s] || ''
}

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getMyRefunds({ page: currentPage.value, size: pageSize.value })
    if (res.data) {
      refunds.value = res.data.records || res.data || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载退款列表失败', e)
  } finally {
    loading.value = false
  }
}

async function showDetail(id) {
  try {
    const res = await getRefundDetail(id)
    detailRefund.value = res.data?.refund || null
    detailItems.value = res.data?.items || []
    // 回填商品名称/图片
    if (detailItems.value.length) {
      const ids = [...new Set(detailItems.value.map(i => i.productId))]
      try {
        const pb = await getProductBatch(ids)
        const map = new Map((pb.data || []).map(p => [p.id, p]))
        detailItems.value = detailItems.value.map(i => ({ ...i, product: map.get(i.productId) }))
      } catch { /* 回填失败不影响详情 */ }
    }
    dialogVisible.value = true
  } catch { /* ignore */ }
}

async function handleReceive(r) {
  try { await ElMessageBox.confirm('确认已收到退货商品？', '提示', { type: 'warning' }) } catch { return }
  try {
    await receiveRefund(r.id)
    ElMessage.success('已确认收货，退款处理中')
    await loadData()
  } catch { /* handle by interceptor */ }
}

</script>

<style scoped>
.refund-page { max-width: 900px; margin: 0 auto; }
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 16px; }

.refund-tabs { margin-bottom: 16px; }
.refund-list { display: flex; flex-direction: column; gap: 12px; }
.refund-card { background: #fff; border-radius: 8px; padding: 16px 20px; cursor: pointer; transition: box-shadow .2s; }
.refund-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,.06); }

.rc-header { display: flex; align-items: center; gap: 12px; padding-bottom: 12px; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
.rc-id { color: #333; font-weight: 500; }
.rc-order { color: #666; flex: 1; }
.rc-time { font-size: 12px; color: #999; }

.rc-body { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; }
.rc-amount { font-size: 20px; font-weight: 700; color: #ff5000; }
.rc-actions { display: flex; gap: 8px; }

.rc-logistics { margin-top: 10px; padding-top: 10px; border-top: 1px solid #f5f5f5; display: flex; align-items: center; gap: 10px; font-size: 13px; color: #666; }

.refund-item { display: flex; gap: 12px; align-items: center; padding: 8px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; color: #333; }
.refund-item:last-child { border-bottom: none; }
.ri-img { width: 48px; height: 48px; border-radius: 6px; flex-shrink: 0; }
.ri-name { font-size: 13px; color: #333; }
.ri-meta { font-size: 12px; color: #999; margin-top: 2px; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
