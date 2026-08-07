<template>
  <div class="seller-page">
    <h2>订单管理</h2>

    <el-table :data="orders" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="orderNo" label="订单号" width="180" show-overflow-tooltip />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ fmtPrice(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="orderStatusTag(row.status)" size="small">{{ orderStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 2" size="small" text type="primary" @click="openShipDialog(row)">发货</el-button>
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

    <!-- 发货 Dialog -->
    <el-dialog v-model="shipVisible" title="发货" width="400px">
      <el-form :model="shipForm" label-width="80px">
        <el-form-item label="快递公司"><el-input v-model="shipForm.trackingCompany" placeholder="如：顺丰速运" /></el-form-item>
        <el-form-item label="快递单号"><el-input v-model="shipForm.trackingNumber" placeholder="快递单号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="handleShip" :loading="shipping">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSellerOrders, sellerShipOrder } from '@/api'
import { fmtPrice } from '@/utils/format'
import { fmtTime } from '@/utils/date'
import { orderStatusText, orderStatusTag } from '@/utils/order'

const orders = ref([])
const total = ref(0)
const loading = ref(false)
const shipping = ref(false)

const query = reactive({ page: 1, size: 20 })

const shipVisible = ref(false)
const shipTarget = ref(null)
const shipForm = reactive({ trackingCompany: '', trackingNumber: '' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getSellerOrders({ page: query.page, size: query.size })
    if (res.data) {
      orders.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openShipDialog(order) {
  shipTarget.value = order
  shipForm.trackingCompany = ''
  shipForm.trackingNumber = ''
  shipVisible.value = true
}

async function handleShip() {
  if (!shipForm.trackingCompany || !shipForm.trackingNumber) {
    ElMessage.warning('请填写快递信息')
    return
  }
  shipping.value = true
  try {
    await sellerShipOrder(shipTarget.value.id, shipForm)
    ElMessage.success('已发货')
    shipVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { shipping.value = false }
}

</script>

<style scoped>
.seller-page h2 { margin-bottom: 16px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>
