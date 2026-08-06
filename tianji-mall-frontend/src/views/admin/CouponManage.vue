<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>优惠券管理</h2>
      <el-button type="primary" @click="openDialog()">新增优惠券</el-button>
    </div>

    <el-table :data="coupons" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" show-overflow-tooltip />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ row.discountType === 'FIXED' ? '满减' : '折扣' }}</template>
      </el-table-column>
      <el-table-column label="面值" width="100">
        <template #default="{ row }">
          {{ row.discountType === 'FIXED' ? `¥${row.discountValue}` : `${row.discountValue * 10}折` }}
        </template>
      </el-table-column>
      <el-table-column label="最低消费" width="100">
        <template #default="{ row }">¥{{ row.minOrderAmount || '—' }}</template>
      </el-table-column>
      <el-table-column prop="totalQuantity" label="总量" width="70" />
      <el-table-column prop="usedQuantity" label="已用" width="70" />
      <el-table-column label="有效期" width="200">
        <template #default="{ row }">{{ fmtTime(row.startTime) }} ~ {{ fmtTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑优惠券' : '新增优惠券'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="优惠券名称" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.discountType">
            <el-radio value="FIXED">满减</el-radio>
            <el-radio value="PERCENT">折扣</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面值">
          <el-input-number v-model="form.discountValue" :min="0" :precision="form.discountType === 'PERCENT' ? 2 : 0" :step="form.discountType === 'PERCENT' ? 0.1 : 1" />
          <span class="form-hint">{{ form.discountType === 'FIXED' ? '元' : '（0-1之间，0.8=8折）' }}</span>
        </el-form-item>
        <el-form-item label="最低消费"><el-input-number v-model="form.minOrderAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="发行量"><el-input-number v-model="form.totalQuantity" :min="1" /></el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="form.applicableCategoryId" placeholder="全部分类" clearable>
            <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
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
import { getAdminCoupons, createCoupon, updateCoupon, deleteCoupon, getAdminCategories } from '@/api'

const coupons = ref([])
const loading = ref(false)
const saving = ref(false)
const flatCategories = ref([])

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({
  name: '', discountType: 'FIXED', discountValue: 10, minOrderAmount: 0,
  totalQuantity: 100, startTime: null, endTime: null, applicableCategoryId: null
})

onMounted(async () => {
  await loadCategories()
  await loadData()
})

async function loadCategories() {
  try {
    const res = await getAdminCategories()
    flatCategories.value = flattenTree(res.data || [])
  } catch { /* ignore */ }
}

function flattenTree(tree) {
  const result = []
  for (const node of tree) {
    result.push({ id: node.id, name: node.name })
    if (node.children) result.push(...flattenTree(node.children))
  }
  return result
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminCoupons({ page: 1, size: 100 })
    coupons.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      name: row.name, discountType: row.discountType, discountValue: row.discountValue,
      minOrderAmount: row.minOrderAmount || 0, totalQuantity: row.totalQuantity,
      startTime: row.startTime, endTime: row.endTime, applicableCategoryId: row.applicableCategoryId
    })
  } else {
    editingId.value = null
    Object.assign(form, {
      name: '', discountType: 'FIXED', discountValue: 10, minOrderAmount: 0,
      totalQuantity: 100, startTime: null, endTime: null, applicableCategoryId: null
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name) { ElMessage.warning('请输入优惠券名称'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateCoupon(editingId.value, form)
      ElMessage.success('已更新')
    } else {
      await createCoupon(form)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}

async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定停用优惠券「${row.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await deleteCoupon(row.id)
    ElMessage.success('已停用')
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
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.form-hint { margin-left: 8px; font-size: 12px; color: #999; }
</style>
