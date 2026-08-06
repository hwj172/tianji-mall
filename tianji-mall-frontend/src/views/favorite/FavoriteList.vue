<template>
  <div class="favorite-page" v-loading="loading">
    <div class="page-header">
      <h2>我的收藏</h2>
    </div>

    <div class="product-grid" v-if="products.length">
      <ProductCard v-for="p in products" :key="p.id" :product="p" />
    </div>
    <el-empty v-else-if="!loading" description="暂无收藏" />

    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getFavorites } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'

const products = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getFavorites({ page: currentPage.value, size: pageSize.value })
    if (res.data) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.favorite-page { max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 22px; }

.product-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
