<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>商品管理</h2>
      <el-button type="primary" @click="openDialog()">新增商品</el-button>
    </div>

    <!-- 筛选 -->
    <div class="ap-filter">
      <el-select v-model="filterCategoryId" placeholder="选择分类" clearable @change="loadData">
        <el-option v-for="c in flatCategories" :key="c.id" :label="c.prefix + c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="商品状态" clearable @change="loadData" style="margin-left: 8px">
        <el-option label="待审核" :value="2" />
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
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
          <el-tag :type="row.status === 1 ? 'success' : (row.status === 2 ? 'warning' : 'info')" size="small">{{ row.status === 1 ? '上架' : (row.status === 2 ? '待审核' : '下架') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
          <template v-if="row.status === 2">
            <el-button size="small" text type="success" @click="handleApprove(row)">通过</el-button>
            <el-button size="small" text type="danger" @click="handleReject(row)">拒绝</el-button>
          </template>
          <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑 Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑商品' : '新增商品'" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="商品名称"><el-input v-model="form.name" placeholder="请输入商品名称" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" placeholder="选择分类">
            <el-option v-for="c in flatCategories" :key="c.id" :label="c.prefix + c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品图片">
          <div class="img-list">
            <div v-for="(img, i) in imageList" :key="i" class="img-item">
              <img :src="img" alt="商品图" />
              <el-icon class="img-del" @click="removeImage(i)"><Close /></el-icon>
            </div>
            <div class="img-add" @click="imgInput.click()">
              <el-icon><Plus /></el-icon>
              <span>上传</span>
            </div>
          </div>
          <input ref="imgInput" type="file" accept="image/*" style="display:none" @change="handleImgUpload" />
        </el-form-item>
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
import { getAdminProducts, createAdminProduct, updateAdminProduct, deleteAdminProduct, getAdminCategories, uploadImage, approveAdminProduct, rejectAdminProduct } from '@/api'

const products = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const flatCategories = ref([])

const query = reactive({ page: 1, size: 10 })
const filterCategoryId = ref(null)
const filterStatus = ref(null)

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', categoryId: null, price: 0, stock: 0, status: 1, images: '', description: '' })
const imageList = ref([])
const imgInput = ref(null)

// images 字段存 JSON 数组字符串；imageList 为图片 URL 数组（UI 用），互相同步
function syncImages() {
  form.images = JSON.stringify(imageList.value)
}

function parseImages(str) {
  if (!str) return []
  try { const arr = JSON.parse(str); return Array.isArray(arr) ? arr : [] } catch { return [] }
}

function removeImage(i) {
  imageList.value.splice(i, 1)
  syncImages()
}

async function handleImgUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  e.target.value = ''
  const fd = new FormData()
  fd.append('files', file)
  try {
    const res = await uploadImage(fd)
    const url = Array.isArray(res.data) ? res.data[0] : res.data
    if (url) { imageList.value.push(url); syncImages() }
  } catch { /* interceptor 统一处理 */ }
}

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

function flattenTree(tree, prefix = '') {
  const result = []
  for (const node of tree) {
    result.push({ id: node.id, name: node.name, prefix })
    if (node.children) result.push(...flattenTree(node.children, prefix + '├ '))
  }
  return result
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (filterCategoryId.value) params.categoryId = filterCategoryId.value
    if (filterStatus.value != null) params.status = filterStatus.value
    const res = await getAdminProducts(params)
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
    form.categoryId = row.categoryId
    form.price = row.price || 0
    form.stock = row.stock || 0
    form.status = row.status
    imageList.value = parseImages(row.images)
    form.description = row.description || ''
  } else {
    editingId.value = null
    Object.assign(form, { name: '', categoryId: null, price: 0, stock: 0, status: 1, description: '' })
    imageList.value = []
  }
  syncImages()
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name) { ElMessage.warning('请输入商品名称'); return }
  saving.value = true
  try {
    const data = {
      name: form.name,
      categoryId: form.categoryId,
      price: form.price,
      stock: form.stock,
      status: form.status,
      images: form.images,
      description: form.description
    }
    if (editingId.value) {
      await updateAdminProduct(editingId.value, data)
      ElMessage.success('已更新')
    } else {
      await createAdminProduct(data)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}

async function handleApprove(row) {
  try {
    await approveAdminProduct(row.id)
    ElMessage.success(`「${row.name}」已通过审核并上架`)
    await loadData()
  } catch { /* handle by interceptor */ }
}

async function handleReject(row) {
  try {
    await ElMessageBox.confirm(`确定拒绝「${row.name}」的上架申请？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await rejectAdminProduct(row.id)
    ElMessage.success('已拒绝，商品下架')
    await loadData()
  } catch { /* handle by interceptor */ }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除商品「${row.name}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteAdminProduct(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* handle by interceptor */ }
}
</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.ap-filter { margin-bottom: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
.img-list { display: flex; flex-wrap: wrap; gap: 8px; }
.img-item { position: relative; width: 64px; height: 64px; border-radius: 4px; overflow: hidden; border: 1px solid #f0f0f0; }
.img-item img { width: 100%; height: 100%; object-fit: cover; }
.img-del { position: absolute; top: 0; right: 0; background: rgba(0,0,0,.5); color: #fff; font-size: 14px; padding: 2px; cursor: pointer; }
.img-add { width: 64px; height: 64px; border: 1px dashed #ccc; border-radius: 4px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #999; cursor: pointer; font-size: 12px; }
.img-add:hover { border-color: #ff5000; color: #ff5000; }
</style>
