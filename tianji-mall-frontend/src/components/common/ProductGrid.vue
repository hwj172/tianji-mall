<template>
  <div class="product-grid-wrap">
    <ProductGridSkeleton v-if="loading" :cols="cols" />
    <div class="product-grid" v-else-if="products.length" :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)` }">
      <slot name="card" v-for="p in products" :key="p.id" :product="p">
        <ProductCard :product="p" />
      </slot>
    </div>
    <EmptyState v-else-if="!loading" :description="emptyText" />
    <div class="pagination-wrap" v-if="total > pageSize">
      <slot name="pagination" />
    </div>
  </div>
</template>

<script setup>
import ProductCard from '@/components/common/ProductCard.vue'
import ProductGridSkeleton from '@/components/common/ProductGridSkeleton.vue'
import EmptyState from '@/components/common/EmptyState.vue'

defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  cols: { type: Number, default: 4 },
  emptyText: { type: String, default: '暂无数据' },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 0 }
})
</script>

<style scoped>
.product-grid { display: grid; gap: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
