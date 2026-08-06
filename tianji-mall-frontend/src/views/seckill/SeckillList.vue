<template>
  <div class="seckill-page" v-loading="loading">
    <div class="sk-header">
      <h2>⚡ 限时秒杀</h2>
    </div>

    <div class="product-grid" v-if="products.length">
      <ProductCard v-for="p in products" :key="p.id" :product="enrich(p)" showOriginalPrice />
    </div>
    <el-empty v-else-if="!loading" description="暂无秒杀活动" />

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
import { getSeckillList } from '@/api'
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
    const res = await getSeckillList({ page: currentPage.value, size: pageSize.value })
    if (res.data) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

// 秒杀价覆盖原价显示
function enrich(p) {
  return { ...p, originalPrice: p.price, price: p.seckillPrice || p.price }
}
</script>

<style scoped>
.seckill-page { max-width: 1200px; margin: 0 auto; }
.sk-header { margin-bottom: 16px; }
.sk-header h2 { font-size: 22px; }

.product-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
