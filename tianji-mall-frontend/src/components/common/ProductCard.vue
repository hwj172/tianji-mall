<template>
  <div class="product-card" @click="$router.push(`/product/${product.id}`)">
    <div class="product-image">
      <img :src="firstImage" :alt="product.name" @error="onImageError" />
    </div>
    <div class="product-info">
      <h4 class="product-name">{{ product.name }}</h4>
      <div class="product-price">
        <span class="price-current">¥{{ product.price }}</span>
        <span class="price-original" v-if="showOriginalPrice">¥{{ product.originalPrice }}</span>
      </div>
      <span class="product-sales" v-if="product.sales != null">已售 {{ formatSales(product.sales) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  product: { type: Object, required: true },
  showOriginalPrice: { type: Boolean, default: false }
})

const firstImage = computed(() => {
  if (!props.product.images) return ''
  try {
    const imgs = typeof props.product.images === 'string'
      ? JSON.parse(props.product.images)
      : props.product.images
    return imgs[0] || ''
  } catch { return '' }
})

function onImageError(e) {
  e.target.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect fill="%23f5f5f5" width="200" height="200"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="14">暂无图片</text></svg>'
}

function formatSales(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return n.toString()
}
</script>

<style scoped>
.product-card {
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid #f0f0f0;
}
.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
}
.product-image {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: #fafafa;
}
.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-info {
  padding: 10px 12px;
}
.product-name {
  font-size: 13px;
  font-weight: 400;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}
.product-price { display: flex; align-items: baseline; gap: 6px; }
.price-current { color: #ff5000; font-size: 18px; font-weight: 700; }
.price-original { color: #999; font-size: 12px; text-decoration: line-through; }
.product-sales { font-size: 11px; color: #999; margin-top: 4px; display: block; }
</style>
