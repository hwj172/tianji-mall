<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>分类管理</h2>
      <el-button type="primary" @click="openCategoryDialog()">新增分类</el-button>
    </div>

    <el-table :data="treeData" stripe row-key="id" default-expand-all v-loading="loading">
      <el-table-column prop="name" label="分类名称" />
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" text @click="openCategoryDialog(row)">编辑</el-button>
          <el-button size="small" text @click="openCategoryDialog(null, row.id)">添加子分类</el-button>
          <el-button size="small" text type="danger" @click="handleDeleteCategory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分类 Dialog -->
    <el-dialog v-model="catVisible" :title="catEditingId ? '编辑分类' : '新增分类'" width="400px">
      <el-form :model="catForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="catForm.name" placeholder="分类名称" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catForm.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveCategory" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminCategories, createCategory, updateCategory, deleteCategory } from '@/api'

const treeData = ref([])
const loading = ref(false)
const saving = ref(false)

const catVisible = ref(false)
const catEditingId = ref(null)
const catParentId = ref(null)
const catForm = reactive({ name: '', sort: 0 })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminCategories()
    treeData.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openCategoryDialog(row, parentId) {
  if (row) {
    catEditingId.value = row.id
    catParentId.value = null
    catForm.name = row.name
    catForm.sort = row.sort || 0
  } else {
    catEditingId.value = null
    catParentId.value = parentId || null
    catForm.name = ''
    catForm.sort = 0
  }
  catVisible.value = true
}

async function handleSaveCategory() {
  if (!catForm.name) { ElMessage.warning('请输入分类名称'); return }
  saving.value = true
  try {
    if (catEditingId.value) {
      await updateCategory(catEditingId.value, { name: catForm.name, sort: catForm.sort })
      ElMessage.success('已更新')
    } else {
      await createCategory({ name: catForm.name, parentId: catParentId.value || 0, sort: catForm.sort })
      ElMessage.success('已创建')
    }
    catVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}

async function handleDeleteCategory(row) {
  try { await ElMessageBox.confirm(`确定删除分类「${row.name}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* handle by interceptor */ }
}
</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
</style>
