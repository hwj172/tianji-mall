<template>
  <div class="admin-page">
    <h2>店铺管理</h2>

    <el-table :data="shops" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="店铺名称" show-overflow-tooltip />
      <el-table-column label="Logo" width="80">
        <template #default="{ row }">
          <el-avatar v-if="row.logo" :src="row.logo" :size="40" shape="square" />
          <span v-else style="color:#ccc">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="sellerId" label="商家ID" width="80" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '营业' : '关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="handleStatus(row)">
            {{ row.status === 1 ? '关闭' : '开启' }}
          </el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminShops, updateShopStatus, deleteShop } from '@/api'

const shops = ref([])
const loading = ref(false)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminShops()
    shops.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await updateShopStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已开启' : '已关闭')
    row.status = newStatus
  } catch { /* handle by interceptor */ }
}

async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定删除店铺「${row.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await deleteShop(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* handle by interceptor */ }
}
</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
</style>
