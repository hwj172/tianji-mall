<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>SKU / 属性管理</h2>
      <el-select
        v-model="productId"
        filterable
        placeholder="选择商品以管理其 SKU 与属性"
        style="width: 320px"
        @change="loadAll"
      >
        <el-option v-for="p in productOptions" :key="p.id" :label="`${p.name}（#${p.id}）`" :value="p.id" />
      </el-select>
    </div>

    <template v-if="productId">
      <div class="section">
        <div class="section-header">
          <h3>SKU（规格）</h3>
          <el-button size="small" type="primary" @click="openSkuDialog()">新增 SKU</el-button>
        </div>
        <el-table :data="skus" stripe v-loading="skuLoading" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="skuCode" label="编码" width="120" />
          <el-table-column prop="specs" label="规格" show-overflow-tooltip />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">¥{{ fmtPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="stock" label="库存" width="70" />
          <el-table-column prop="sales" label="销量" width="70" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openSkuDialog(row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteSku(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="section">
        <div class="section-header">
          <h3>属性</h3>
          <el-button size="small" type="primary" @click="openAttrDialog()">新增属性</el-button>
        </div>
        <el-table :data="attributes" stripe v-loading="attrLoading" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="属性名" width="140" />
          <el-table-column prop="value" label="属性值" show-overflow-tooltip />
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openAttrDialog(row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteAttr(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
    <el-empty v-else description="请先选择商品" />

    <!-- SKU Dialog -->
    <el-dialog v-model="skuDialogVisible" :title="editingSkuId ? '编辑 SKU' : '新增 SKU'" width="440px">
      <el-form :model="skuForm" label-width="70px">
        <el-form-item label="编码"><el-input v-model="skuForm.skuCode" placeholder="SKU 编码，如 BLACK-128G" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="skuForm.specs" placeholder="如 颜色:黑色;版本:128G" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="skuForm.price" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="skuForm.stock" :min="0" :precision="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSku" @click="handleSaveSku">保存</el-button>
      </template>
    </el-dialog>

    <!-- 属性 Dialog -->
    <el-dialog v-model="attrDialogVisible" :title="editingAttrId ? '编辑属性' : '新增属性'" width="440px">
      <el-form :model="attrForm" label-width="70px">
        <el-form-item label="属性名"><el-input v-model="attrForm.name" placeholder="如 屏幕尺寸" /></el-form-item>
        <el-form-item label="属性值"><el-input v-model="attrForm.value" placeholder="如 6.1 英寸" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="attrForm.sort" :min="0" :precision="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="attrDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAttr" @click="handleSaveAttr">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminProducts, getProductSkus, createProductSku, updateProductSku, deleteProductSku,
  getProductAttributes, createProductAttribute, updateProductAttribute, deleteProductAttribute
} from '@/api'
import { fmtPrice } from '@/utils/format'

const productOptions = ref([])
const productId = ref(null)
const skus = ref([])
const attributes = ref([])
const skuLoading = ref(false)
const attrLoading = ref(false)

const skuDialogVisible = ref(false)
const editingSkuId = ref(null)
const savingSku = ref(false)
const skuForm = reactive({ skuCode: '', specs: '', price: 0.01, stock: 0 })

const attrDialogVisible = ref(false)
const editingAttrId = ref(null)
const savingAttr = ref(false)
const attrForm = reactive({ name: '', value: '', sort: 0 })

onMounted(async () => {
  try {
    const res = await getAdminProducts({ page: 1, size: 200 })
    productOptions.value = res.data?.records || []
  } catch { /* ignore */ }
})

async function loadAll() {
  await Promise.all([loadSkus(), loadAttributes()])
}

async function loadSkus() {
  if (!productId.value) return
  skuLoading.value = true
  try {
    const res = await getProductSkus(productId.value)
    skus.value = res.data || []
  } catch { /* ignore */ }
  finally { skuLoading.value = false }
}

async function loadAttributes() {
  if (!productId.value) return
  attrLoading.value = true
  try {
    const res = await getProductAttributes(productId.value)
    attributes.value = res.data || []
  } catch { /* ignore */ }
  finally { attrLoading.value = false }
}

// ===== SKU =====
function openSkuDialog(row) {
  if (row) {
    editingSkuId.value = row.id
    Object.assign(skuForm, { skuCode: row.skuCode || '', specs: row.specs || '', price: row.price ?? 0.01, stock: row.stock ?? 0 })
  } else {
    editingSkuId.value = null
    Object.assign(skuForm, { skuCode: '', specs: '', price: 0.01, stock: 0 })
  }
  skuDialogVisible.value = true
}

async function handleSaveSku() {
  if (!skuForm.specs.trim() && !skuForm.skuCode.trim()) { ElMessage.warning('请填写编码或规格'); return }
  savingSku.value = true
  try {
    if (editingSkuId.value) {
      await updateProductSku(productId.value, editingSkuId.value, { ...skuForm })
      ElMessage.success('已更新')
    } else {
      await createProductSku(productId.value, { ...skuForm })
      ElMessage.success('已创建')
    }
    skuDialogVisible.value = false
    loadSkus()
  } catch { /* interceptor */ }
  finally { savingSku.value = false }
}

async function handleDeleteSku(row) {
  try {
    await ElMessageBox.confirm(`确定删除 SKU「${row.skuCode || row.specs}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteProductSku(productId.value, row.id)
    ElMessage.success('已删除')
    loadSkus()
  } catch { /* interceptor */ }
}

// ===== 属性 =====
function openAttrDialog(row) {
  if (row) {
    editingAttrId.value = row.id
    Object.assign(attrForm, { name: row.name || '', value: row.value || '', sort: row.sort ?? 0 })
  } else {
    editingAttrId.value = null
    Object.assign(attrForm, { name: '', value: '', sort: 0 })
  }
  attrDialogVisible.value = true
}

async function handleSaveAttr() {
  if (!attrForm.name.trim()) { ElMessage.warning('请输入属性名'); return }
  savingAttr.value = true
  try {
    if (editingAttrId.value) {
      await updateProductAttribute(productId.value, editingAttrId.value, { ...attrForm })
      ElMessage.success('已更新')
    } else {
      await createProductAttribute(productId.value, { ...attrForm })
      ElMessage.success('已创建')
    }
    attrDialogVisible.value = false
    loadAttributes()
  } catch { /* interceptor */ }
  finally { savingAttr.value = false }
}

async function handleDeleteAttr(row) {
  try {
    await ElMessageBox.confirm(`确定删除属性「${row.name}」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteProductAttribute(productId.value, row.id)
    ElMessage.success('已删除')
    loadAttributes()
  } catch { /* interceptor */ }
}
</script>

<style scoped>
.admin-page { max-width: 1000px; margin: 0 auto; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ap-header h2 { font-size: 18px; font-weight: 600; }
.section { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.section-header h3 { font-size: 15px; font-weight: 600; }
</style>
