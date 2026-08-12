<template>
  <div class="admin-page">
    <div class="ap-header">
      <h2>拼团活动管理</h2>
      <el-button type="primary" @click="openDialog()">新增活动</el-button>
    </div>

    <el-table :data="activities" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="商品" show-overflow-tooltip>
        <template #default="{ row }">{{ productName(row.productId) }}（#{{ row.productId }}）</template>
      </el-table-column>
      <el-table-column label="阶梯" width="200">
        <template #default="{ row }">
          <span v-for="(t, i) in parseTiers(row.tiers)" :key="i" class="tier-tag">{{ t.count }}人 {{ formatDiscount(t.discount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="有效期" width="200">
        <template #default="{ row }">{{ fmtTime(row.startTime) }} ~ {{ fmtTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="expireHours" label="成团时限(h)" width="110" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑活动' : '新增活动'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="拼团商品">
          <el-select v-model="form.productId" filterable placeholder="选择商品" style="width:100%">
            <el-option v-for="p in productOptions" :key="p.id" :label="`${p.name}（#${p.id}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="拼团阶梯">
          <div class="tier-list">
            <div v-for="(t, i) in form.tiers" :key="i" class="tier-row">
              <el-input-number v-model="t.count" :min="2" :precision="0" size="small" style="width:110px" />
              <span class="tier-x">人</span>
              <el-input-number v-model="t.discount" :min="0.1" :max="1" :step="0.05" :precision="2" size="small" style="width:110px" />
              <span class="tier-x">折</span>
              <el-button size="small" text type="danger" @click="form.tiers.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" text type="primary" @click="addTier">+ 添加阶梯</el-button>
          </div>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="成团时限">
          <el-input-number v-model="form.expireHours" :min="1" :precision="0" />
          <span class="tier-x">小时</span>
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
import { ElMessage } from 'element-plus'
import { getGroupBuyList, createGroupBuy, updateGroupBuy, getAdminProducts } from '@/api'
import { fmtTime } from '@/utils/date'
import { formatDiscount } from '@/utils/discount'

const activities = ref([])
const productOptions = ref([])
const productMap = ref({})
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ productId: null, tiers: [], startTime: null, endTime: null, expireHours: 24 })

onMounted(async () => {
  loadProducts()
  loadData()
})

async function loadProducts() {
  try {
    const res = await getAdminProducts({ page: 1, size: 200 })
    const list = res.data?.records || []
    productOptions.value = list
    productMap.value = Object.fromEntries(list.map(p => [p.id, p]))
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getGroupBuyList()
    activities.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function productName(id) { return productMap.value[id]?.name || '' }

function parseTiers(json) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

function addTier() {
  form.tiers.push({ count: 2, discount: 0.9 })
}

function openDialog(row) {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      productId: row.productId,
      tiers: parseTiers(row.tiers),
      startTime: row.startTime || null,
      endTime: row.endTime || null,
      expireHours: row.expireHours ?? 24
    })
  } else {
    editingId.value = null
    Object.assign(form, { productId: null, tiers: [{ count: 2, discount: 0.9 }], startTime: null, endTime: null, expireHours: 24 })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.productId) { ElMessage.warning('请选择拼团商品'); return }
  if (!form.tiers.length) { ElMessage.warning('请至少添加一个拼团阶梯'); return }
  if (!form.startTime || !form.endTime) { ElMessage.warning('请选择完整的活动时间'); return }
  saving.value = true
  try {
    const payload = {
      productId: form.productId,
      tiers: form.tiers,
      startTime: form.startTime,
      endTime: form.endTime,
      expireHours: form.expireHours
    }
    if (editingId.value) {
      await updateGroupBuy(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createGroupBuy(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    loadData()
  } catch { /* interceptor */ }
  finally { saving.value = false }
}
</script>

<style scoped>
.admin-page { max-width: 1000px; margin: 0 auto; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ap-header h2 { font-size: 18px; font-weight: 600; }
.tier-tag { display: inline-block; margin-right: 6px; background: #fff3e8; color: #ff5000; border-radius: 4px; padding: 2px 8px; font-size: 12px; }
.tier-row { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.tier-x { color: #999; font-size: 12px; margin: 0 4px; }
</style>
