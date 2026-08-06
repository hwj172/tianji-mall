<template>
  <div class="seller-dashboard">
    <h2>商家看板</h2>

    <!-- 店铺信息 -->
    <div class="shop-card" v-if="shop">
      <div class="shop-logo">
        <el-avatar v-if="shop.logo" :src="shop.logo" :size="64" shape="square" />
        <el-avatar v-else :size="64" shape="square" :icon="Shop" />
      </div>
      <div class="shop-info">
        <h3>{{ shop.name }}</h3>
        <p>{{ shop.description || '暂无店铺描述' }}</p>
        <el-tag :type="shop.status === 1 ? 'success' : 'info'" size="small">
          {{ shop.status === 1 ? '营业中' : '已关闭' }}
        </el-tag>
      </div>
      <div class="shop-actions">
        <el-button @click="openEditDialog">编辑店铺</el-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">商品数量</div>
        <div class="stat-value">{{ dashboard.productCount || 0 }}</div>
      </div>
    </div>

    <!-- 编辑 Dialog -->
    <el-dialog v-model="editVisible" title="编辑店铺信息" width="460px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="店铺名称"><el-input v-model="editForm.name" placeholder="店铺名称" /></el-form-item>
        <el-form-item label="Logo URL"><el-input v-model="editForm.logo" placeholder="Logo 图片地址" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="店铺描述" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateShop" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Shop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSellerDashboard, getSellerShop, updateSellerShop } from '@/api'

const shop = ref(null)
const dashboard = reactive({ productCount: 0 })
const saving = ref(false)

const editVisible = ref(false)
const editForm = reactive({ name: '', logo: '', description: '' })

onMounted(async () => {
  try {
    const res = await getSellerDashboard()
    if (res.data) {
      if (res.data.shop) shop.value = res.data.shop
      dashboard.productCount = res.data.productCount || 0
    }
  } catch (e) {
    console.error('加载商家看板失败', e)
  }
})

function openEditDialog() {
  editForm.name = shop.value?.name || ''
  editForm.logo = shop.value?.logo || ''
  editForm.description = shop.value?.description || ''
  editVisible.value = true
}

async function handleUpdateShop() {
  if (!editForm.name) { ElMessage.warning('请输入店铺名称'); return }
  saving.value = true
  try {
    await updateSellerShop(editForm)
    ElMessage.success('已更新')
    if (shop.value) {
      shop.value.name = editForm.name
      shop.value.logo = editForm.logo
      shop.value.description = editForm.description
    }
    editVisible.value = false
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}
</script>

<style scoped>
.seller-dashboard h2 { margin-bottom: 20px; }

.shop-card { background: #fff; border-radius: 8px; padding: 24px; display: flex; align-items: center; gap: 20px; margin-bottom: 16px; }
.shop-info { flex: 1; }
.shop-info h3 { font-size: 20px; margin-bottom: 6px; }
.shop-info p { font-size: 13px; color: #666; margin-bottom: 8px; }

.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.stat-card { background: #fff; border-radius: 8px; padding: 24px; text-align: center; }
.stat-label { font-size: 14px; color: #999; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; color: #333; }
</style>
