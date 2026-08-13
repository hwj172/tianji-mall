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
        <template #default="{ row }">¥{{ fmtPrice(row.price) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column prop="sales" label="销量" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : (row.status === 2 ? 'warning' : 'info')" size="small">{{ row.status === 1 ? '上架' : (row.status === 2 ? '审核中' : '下架') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
          <el-button v-if="row.status === 2" size="small" text disabled>审核中</el-button>
          <el-button v-else-if="row.status === 0" size="small" text type="success" @click="handleRestore(row)">上架</el-button>
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
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" placeholder="选择商品分类（必填）" clearable style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
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
import { getSellerProducts, createSellerProduct, updateSellerProduct, deleteSellerProduct, uploadImage, getHomeData } from '@/api'
import { fmtPrice } from '@/utils/format'

const products = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)

const query = reactive({ page: 1, size: 20, status: 1 })

const statusTabs = [
  { value: 2, label: '待审核' },
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
const form = reactive({ name: '', categoryId: null, price: 0, stock: 0, status: 1, images: '', description: '' })
const imageList = ref([])
const imgInput = ref(null)
const categoryOptions = ref([])

// 分类下拉：公开首页接口返回分类树，扁平化为平铺选项
function flattenCategories(tree, list = []) {
  tree.forEach(c => {
    list.push({ id: c.id, name: c.name })
    if (c.children?.length) flattenCategories(c.children, list)
  })
  return list
}

onMounted(async () => {
  try {
    const res = await getHomeData()
    categoryOptions.value = flattenCategories(res.data?.categories || [])
  } catch { /* 分类加载失败不影响主流程 */ }
})

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
    form.categoryId = row.categoryId ?? null
    form.price = row.price || 0
    form.stock = row.stock || 0
    // 编辑保留原上下架状态（updateProduct 用 NOT_NULL 策略，传原 status 避免误上架/误下架）
    form.status = row.status ?? 1
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
    // Server expects Product entity directly（编辑保留原上下架状态；新建默认上架）
    const data = {
      name: form.name,
      categoryId: form.categoryId,
      price: form.price,
      stock: form.stock,
      images: form.images,
      description: form.description,
      status: editingId.value ? form.status : 1
    }
    if (editingId.value) {
      await updateSellerProduct(editingId.value, data)
      ElMessage.success('已更新，待管理员审核')
    } else {
      await createSellerProduct(data)
      ElMessage.success('已提交，待管理员审核上架')
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
    // updateById 只更新非 null 字段，仅传 status 即可提交上架（后端置待审核）
    await updateSellerProduct(row.id, { status: 1 })
    ElMessage.success('已提交上架审核')
    await loadData()
  } catch { /* handle by interceptor */ }
}
</script>

<style scoped>
.seller-page h2 { margin-bottom: 16px; }
.sp-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.sp-tabs { display: flex; gap: 4px; margin-bottom: 12px; }
.sp-tabs .tab-item { padding: 6px 16px; font-size: 13px; cursor: pointer; border-radius: 4px; color: #8b96ab; }
.sp-tabs .tab-item:hover, .sp-tabs .tab-item.active { background: rgba(255, 80, 0, .12); color: #ff5000; font-weight: 600; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
.img-list { display: flex; flex-wrap: wrap; gap: 8px; }
.img-item { position: relative; width: 64px; height: 64px; border-radius: 4px; overflow: hidden; border: 1px solid rgba(255,255,255,.08); }
.img-item img { width: 100%; height: 100%; object-fit: cover; }
.img-del { position: absolute; top: 0; right: 0; background: rgba(0,0,0,.5); color: #fff; font-size: 14px; padding: 2px; cursor: pointer; }
.img-add { width: 64px; height: 64px; border: 1px dashed #ccc; border-radius: 4px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #5c6a82; cursor: pointer; font-size: 12px; }
.img-add:hover { border-color: #ff5000; color: #ff5000; }
</style>
