<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>满减活动管理</h2>
      <el-button type="primary" @click="openDialog()">新增活动</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="满减活动按「优惠券前金额」自动应用，与优惠券叠加；同一订单有多个满足条件的活动时取减免最大的一项。"
      style="margin-bottom: 12px"
    />

    <el-table :data="promotions" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="活动名称" show-overflow-tooltip />
      <el-table-column label="门槛" width="110">
        <template #default="{ row }">满 ¥{{ fmtPrice(row.threshold) }}</template>
      </el-table-column>
      <el-table-column label="减免" width="110">
        <template #default="{ row }">减 ¥{{ fmtPrice(row.discount) }}</template>
      </el-table-column>
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
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑活动' : '新增活动'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="活动名称"><el-input v-model="form.name" placeholder="如：全场满300减50" /></el-form-item>
        <el-form-item label="满">
          <el-input-number v-model="form.threshold" :min="0" :precision="2" />
          <span class="form-hint">元</span>
        </el-form-item>
        <el-form-item label="减">
          <el-input-number v-model="form.discount" :min="0" :precision="2" />
          <span class="form-hint">元</span>
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
import { getAdminPromotions, createPromotion, updatePromotion, deletePromotion } from '@/api'
import { fmtTime } from '@/utils/date'
import { fmtPrice } from '@/utils/format'

const promotions = ref([])
const loading = ref(false)
const saving = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({
  name: '', threshold: 300, discount: 50, startTime: null, endTime: null
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminPromotions({ page: 1, size: 100 })
    promotions.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      name: row.name, threshold: row.threshold, discount: row.discount,
      startTime: row.startTime, endTime: row.endTime
    })
  } else {
    editingId.value = null
    Object.assign(form, {
      name: '', threshold: 300, discount: 50, startTime: null, endTime: null
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name) { ElMessage.warning('请输入活动名称'); return }
  if (!form.threshold || form.threshold <= 0) { ElMessage.warning('请输入有效的门槛金额'); return }
  if (!form.discount || form.discount <= 0) { ElMessage.warning('请输入有效的减免金额'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updatePromotion(editingId.value, form)
      ElMessage.success('已更新')
    } else {
      await createPromotion(form)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}

async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定停用活动「${row.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await deletePromotion(row.id)
    ElMessage.success('已停用')
    await loadData()
  } catch { /* handle by interceptor */ }
}

</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.form-hint { margin-left: 8px; font-size: 12px; color: #52525b; }
</style>
