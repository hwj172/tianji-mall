<template>
  <div class="history-page" v-loading="loading">
    <div class="page-header">
      <h2>浏览足迹</h2>
      <el-button type="danger" text @click="handleClear" :disabled="!products.length">清空足迹</el-button>
    </div>

    <div class="product-grid" v-if="products.length">
      <ProductCard v-for="p in products" :key="p.id" :product="p" />
    </div>
    <el-empty v-else-if="!loading" description="暂无浏览记录" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getBrowsingHistory, clearBrowsingHistory } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'
import { useProductBatch } from '@/composables/useProductBatch'

const products = ref([])
const loading = ref(false)
const { resolveProducts } = useProductBatch()

onMounted(() => loadData())

// 后端返回 List<BrowsingHistory>（仅 productId），批量查询商品回填详情后渲染
async function loadData() {
  loading.value = true
  try {
    const res = await getBrowsingHistory()
    const hist = res.data || []
    const items = await resolveProducts(hist)
    products.value = items.map(i => i.product).filter(Boolean)
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
    products.value = []
  } catch { /* ignore */ }
}
</script>

<style scoped>
.history-page { max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { font-size: 22px; }

.product-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
</style>
