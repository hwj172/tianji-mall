<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>Banner 管理</h2>
      <el-button type="primary" @click="openDialog()">新增 Banner</el-button>
    </div>

    <el-table :data="banners" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="预览" width="140">
        <template #default="{ row }">
          <img v-if="row.imageUrl" :src="row.imageUrl" loading="lazy" style="width:120px;height:60px;object-fit:cover;border-radius:4px;display:block" @error="onImgError" />
          <span v-else style="color:#5c6a82">无图</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="linkUrl" label="跳转链接" show-overflow-tooltip>
        <template #default="{ row }">{{ row.linkUrl || '—' }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Banner' : '新增 Banner'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="32" placeholder="如：新品首发" /></el-form-item>
        <el-form-item label="图片">
          <div class="img-upload-row">
            <img v-if="form.imageUrl" :src="form.imageUrl" style="width:120px;height:60px;object-fit:cover;border-radius:4px" @error="onImgError" />
            <el-button size="small" @click="imgInput.click()">{{ form.imageUrl ? '更换' : '上传' }}</el-button>
          </div>
          <input ref="imgInput" type="file" accept="image/*" style="display:none" @change="handleImgChange" />
        </el-form-item>
        <el-form-item label="跳转链接"><el-input v-model="form.linkUrl" placeholder="如：/product/12 或 https://..." /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
import { getAdminBanners, createAdminBanner, updateAdminBanner, deleteAdminBanner, uploadImage } from '@/api'
import { imageOnError } from '@/utils/image'

const banners = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const imgInput = ref(null)
const form = reactive({ title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1 })

onMounted(loadData)

function onImgError(e) { imageOnError(e, 120) }

async function loadData() {
  loading.value = true
  try {
    const res = await getAdminBanners()
    banners.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      title: row.title, imageUrl: row.imageUrl || '', linkUrl: row.linkUrl || '',
      sort: row.sort ?? 0, status: row.status ?? 1
    })
  } else {
    editingId.value = null
    Object.assign(form, { title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1 })
  }
  dialogVisible.value = true
}

async function handleImgChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  e.target.value = ''
  const fd = new FormData()
  fd.append('files', file)
  try {
    const res = await uploadImage(fd)
    const url = Array.isArray(res.data) ? res.data[0] : res.data
    if (url) form.imageUrl = url
  } catch { /* interceptor */ }
}

async function handleSave() {
  if (!form.title.trim()) { ElMessage.warning('请输入标题'); return }
  if (!form.imageUrl) { ElMessage.warning('请上传图片'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateAdminBanner(editingId.value, form)
      ElMessage.success('已更新')
    } else {
      await createAdminBanner(form)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    loadData()
  } catch { /* interceptor */ }
  finally { saving.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除 Banner「${row.title}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteAdminBanner(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch { /* interceptor */ }
}
</script>

<style scoped>
.admin-page { max-width: 1000px; margin: 0 auto; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ap-header h2 { font-size: 18px; font-weight: 600; }
.img-upload-row { display: flex; align-items: center; gap: 12px; }
</style>
