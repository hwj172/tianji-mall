<template>
  <div class="admin-page">
    <h2>订单管理</h2>

    <div class="ap-filter">
      <el-select v-model="filterStatus" placeholder="订单状态" clearable @change="loadData">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
    </div>

    <el-table :data="orders" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="orderNo" label="订单号" width="180" show-overflow-tooltip />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ fmtPrice(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusMap[row.status] || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <!-- 发货由商家操作（SellerController），管理员只做订单状态审核 -->
          <el-button v-if="row.status === 3" size="small" text type="success" @click="handleComplete(row)">完成</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminOrders, completeOrder } from '@/api'
import { fmtPrice } from '@/utils/format'

const orders = ref([])
const total = ref(0)
const loading = ref(false)
const filterStatus = ref(null)

const query = reactive({ page: 1, size: 10 })

const statusMap = { 1: '待付款', 2: '已付款', 3: '已发货', 4: '已完成', 5: '已取消' }
const statusOptions = Object.entries(statusMap).map(([value, label]) => ({ value: Number(value), label }))

function statusTag(s) {
  const m = { 1: 'warning', 2: '', 3: '', 4: 'success', 5: 'info' }
  return m[s] || ''
}

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (filterStatus.value) params.status = filterStatus.value
    const res = await getAdminOrders(params)
    if (res.data) {
      orders.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleComplete(order) {
  try { await ElMessageBox.confirm('确认完成该订单？', '提示', { type: 'warning' }) } catch { return }
  try {
    await completeOrder(order.id)
    ElMessage.success('已完成')
    await loadData()
  } catch { /* handle by interceptor */ }
}

function fmtTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) t = t[0] + 'T' + t[1]
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
.ap-filter { margin-bottom: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>
