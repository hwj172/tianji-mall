<template>
  <div class="seller-page">
    <div class="sp-header">
      <h2>商品管理</h2>
      <el-button type="primary" @click="openDialog()">新增商品</el-button>
    </div>

    <div class="sp-tabs">
      <span
        v-for="t in statusTabs" :key="t.value"
        class="tab-item" :class="{ active: query.status === t.value }"
        @click="switchStatus(t.value)"
      >{{ t.label }}</span>
    </div>

    <el-table :data="products" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="商品名称" show-overflow-tooltip />
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column prop="sales" label="销量" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
          <el-button v-if="row.status === 0" size="small" text type="success" @click="handleRestore(row)">上架</el-button>
          <el-button v-else size="small" text type="danger" @click="handleDelete(row)">下架</el-button>
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

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑商品' : '新增商品'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="商品名称" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="图片URL"><el-input v-model="form.images" placeholder="JSON数组" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
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
import { getSellerProducts, createSellerProduct, updateSellerProduct, deleteSellerProduct } from '@/api'

const products = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)

const query = reactive({ page: 1, size: 20, status: 1 })

const statusTabs = [
  { value: 1, label: '上架' },
  { value: 0, label: '下架' }
]

function switchStatus(v) {
  query.status = v
  query.page = 1
  loadData()
}

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', price: 0, stock: 0, images: '', description: '' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getSellerProducts({ page: query.page, size: query.size, status: query.status })
    if (res.data) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    form.name = row.name || ''
    form.price = row.price || 0
    form.stock = row.stock || 0
    form.images = typeof row.images === 'string' ? row.images : JSON.stringify(row.images || [])
    form.description = row.description || ''
  } else {
    editingId.value = null
    Object.assign(form, { name: '', price: 0, stock: 0, images: '', description: '' })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name) { ElMessage.warning('请输入商品名称'); return }
  saving.value = true
  try {
    // Server expects Product entity directly
    const data = { name: form.name, price: form.price, stock: form.stock, images: form.images, description: form.description, status: 1 }
    if (editingId.value) {
      await updateSellerProduct(editingId.value, data)
      ElMessage.success('已更新')
    } else {
      await createSellerProduct(data)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}

async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定下架商品「${row.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await deleteSellerProduct(row.id)
    ElMessage.success('已下架')
    await loadData()
  } catch { /* handle by interceptor */ }
}

async function handleRestore(row) {
  try {
    // updateById 只更新非 null 字段，仅传 status 即可恢复上架
    await updateSellerProduct(row.id, { status: 1 })
    ElMessage.success('已上架')
    await loadData()
  } catch { /* handle by interceptor */ }
}
</script>

<style scoped>
.seller-page h2 { margin-bottom: 16px; }
.sp-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.sp-tabs { display: flex; gap: 4px; margin-bottom: 12px; }
.sp-tabs .tab-item { padding: 6px 16px; font-size: 13px; cursor: pointer; border-radius: 4px; color: #666; }
.sp-tabs .tab-item:hover, .sp-tabs .tab-item.active { background: #fff7f0; color: #ff5000; font-weight: 600; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>
