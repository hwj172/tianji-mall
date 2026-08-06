<template>
  <div class="favorite-page" v-loading="loading">
    <div class="page-header">
      <h2>我的收藏</h2>
    </div>

    <div class="product-grid" v-if="products.length">
      <ProductCard v-for="p in products" :key="p.id" :product="p" />
    </div>
    <el-empty v-else-if="!loading" description="暂无收藏" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFavorites, getProductBatch } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'

const products = ref([])
const loading = ref(false)

onMounted(() => loadData())

// 后端返回 List<Favorite>（仅 productId），需批量查询商品回填详情后渲染
async function loadData() {
  loading.value = true
  try {
    const res = await getFavorites()
    const favs = res.data || []
    if (!favs.length) {
      products.value = []
      return
    }
    const pRes = await getProductBatch(favs.map(f => f.productId))
    const byId = new Map((pRes.data || []).map(p => [p.id, p]))
    products.value = favs.map(f => byId.get(f.productId)).filter(Boolean)
  } catch { /* ignore */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.favorite-page { max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 22px; }

.product-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
</style>
