<template>
  <div class="favorite-page">
    <div class="page-header">
      <h2>我的收藏</h2>
    </div>

    <ProductGrid
      :products="products"
      :loading="loading"
      :cols="5"
      :total="total"
      :page-size="pageSize"
      empty-text="暂无收藏"
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
import { getFavorites } from '@/api'
import ProductGrid from '@/components/common/ProductGrid.vue'
import { useProductBatch } from '@/composables/useProductBatch'

const products = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const { resolveProducts } = useProductBatch()

onMounted(() => loadData())

// 后端返回 Page<Favorite>（records 仅 productId），批量查询商品回填详情后渲染
async function loadData() {
  loading.value = true
  try {
    const res = await getFavorites({ page: currentPage.value, size: pageSize.value })
    const favs = res.data?.records || []
    const items = await resolveProducts(favs)
    products.value = items.map(i => i.product).filter(Boolean)
    total.value = res.data?.total || 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.favorite-page { max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 22px; }
</style>
