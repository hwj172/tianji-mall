<template>
  <div class="history-page">
    <div class="page-header">
      <h2>浏览足迹</h2>
      <el-button type="danger" text @click="handleClear" :disabled="!products.length">清空足迹</el-button>
    </div>

    <ProductGrid
      :products="products"
      :loading="loading"
      :cols="5"
      :total="total"
      :page-size="pageSize"
      empty-text="暂无浏览记录"
    >
      <template #pagination>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </template>
    </ProductGrid>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getBrowsingHistory, clearBrowsingHistory } from '@/api'
import ProductGrid from '@/components/common/ProductGrid.vue'
import { useProductBatch } from '@/composables/useProductBatch'

const products = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const { resolveProducts } = useProductBatch()

onMounted(() => loadData())

// 后端返回 Page<BrowsingHistory>（records 仅 productId），批量查询商品回填详情后渲染
async function loadData() {
  loading.value = true
  try {
    const res = await getBrowsingHistory({ page: currentPage.value, size: pageSize.value })
    const hist = res.data?.records || []
    const items = await resolveProducts(hist)
    products.value = items.map(i => i.product).filter(Boolean)
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确定要清空所有浏览记录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  try {
    await clearBrowsingHistory()
    currentPage.value = 1
    await loadData()
  } catch { /* ignore */ }
}
</script>

<style scoped>
.history-page { max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { font-size: 22px; }
</style>
