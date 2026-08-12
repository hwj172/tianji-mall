<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>秒杀管理</h2>
      <el-input v-model="query.keyword" placeholder="搜索商品名称" clearable style="width:220px" @keyup.enter="query.page = 1; loadData()" @clear="query.page = 1; loadData()" />
    </div>

    <el-table :data="products" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="商品名称" show-overflow-tooltip />
      <el-table-column label="原价" width="100">
        <template #default="{ row }">¥{{ fmtPrice(row.price) }}</template>
      </el-table-column>
      <el-table-column label="秒杀信息" width="240">
        <template #default="{ row }">
          <template v-if="row.seckillPrice != null">
            <div>秒杀价 ¥{{ fmtPrice(row.seckillPrice) }}（库存 {{ row.seckillStock ?? 0 }}）</div>
            <div class="sk-time">{{ fmtTime(row.seckillStartTime, { dateOnly: false }) }} ~ {{ fmtTime(row.seckillEndTime, { dateOnly: false }) }}</div>
          </template>
          <span v-else style="color:#999">未设置</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openDialog(row)">设置/修改</el-button>
          <el-button v-if="row.seckillPrice != null" size="small" text type="danger" @click="handleClear(row)">清除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="设置秒杀" width="480px">
      <el-alert type="info" :closable="false" title="秒杀仅对无 SKU 商品生效；设置后商品在秒杀窗口内以下单价出售，并与拼团互斥。" style="margin-bottom:12px" />
      <el-form :model="form" label-width="90px">
        <el-form-item label="商品"><el-input :model-value="form.name" disabled /></el-form-item>
        <el-form-item label="秒杀价">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="秒杀库存">
          <el-input-number v-model="form.stock" :min="1" :precision="0" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminProducts, setProductSeckill, clearProductSeckill } from '@/api'
import { fmtPrice } from '@/utils/format'
import { fmtTime } from '@/utils/date'

const products = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const query = reactive({ page: 1, size: 20, keyword: '' })
const dialogVisible = ref(false)
const currentProduct = ref(null)
const form = reactive({ name: '', price: 9.9, stock: 100, startTime: null, endTime: null })

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword
    const res = await getAdminProducts(params)
    products.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openDialog(row) {
  currentProduct.value = row
  Object.assign(form, {
    name: row.name,
    price: row.seckillPrice != null ? row.seckillPrice : row.price,
    stock: row.seckillStock != null ? row.seckillStock : 100,
    startTime: row.seckillStartTime || null,
    endTime: row.seckillEndTime || null
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.startTime || !form.endTime) { ElMessage.warning('请选择完整的秒杀时间段'); return }
  if (new Date(form.endTime) <= new Date(form.startTime)) { ElMessage.warning('结束时间需晚于开始时间'); return }
  saving.value = true
  try {
    await setProductSeckill(currentProduct.value.id, {
      price: form.price, stock: form.stock, startTime: form.startTime, endTime: form.endTime
    })
    ElMessage.success('秒杀设置成功')
    dialogVisible.value = false
    loadData()
  } catch { /* interceptor */ }
  finally { saving.value = false }
}

async function handleClear(row) {
  try {
    await ElMessageBox.confirm(`确定清除「${row.name}」的秒杀设置？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await clearProductSeckill(row.id)
    ElMessage.success('已清除')
    loadData()
  } catch { /* interceptor */ }
}
</script>

<style scoped>
.admin-page { max-width: 1000px; margin: 0 auto; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ap-header h2 { font-size: 18px; font-weight: 600; }
.sk-time { font-size: 12px; color: #999; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
