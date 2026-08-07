<template>
  <div class="product-card" @click="$router.push(`/product/${product.id}`)">
    <div class="product-image">
      <img :src="firstImage" :alt="product.name" loading="lazy" decoding="async" @error="onImageError" />
      <span v-if="showOriginalPrice && discountPercent != null" class="discount-badge">-{{ discountPercent }}%</span>
    </div>
    <div class="product-info">
      <h4 class="product-name">{{ product.name }}</h4>
      <div class="product-price">
        <span class="price-current">¥{{ priceDisplay }}</span>
        <span class="price-original" v-if="showOriginalPrice && originalPrice != null">¥{{ originalPriceDisplay }}</span>
      </div>
      <span class="product-sales" v-if="product.sales != null">已售 {{ formatSales(product.sales) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmtPrice } from '@/utils/format'

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

// 价格统一保留两位小数
const priceDisplay = computed(() => fmtPrice(props.product.price))

// 原价字段存在时划线显示 + 折扣角标（列表接口目前不返回该字段，防御式：有才渲染）
const originalPrice = computed(() => {
  const v = props.product.originalPrice
  return v != null ? Number(v) : null
})
const originalPriceDisplay = computed(() =>
  originalPrice.value != null ? fmtPrice(originalPrice.value) : ''
)
const discountPercent = computed(() => {
  const p = Number(props.product.price) || 0
  const o = originalPrice.value
  if (o != null && p > 0 && o > p) {
    return Math.round((1 - p / o) * 100)
  }
  return null
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
  position: relative;
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
.discount-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  background: #ff5000;
  color: #fff;
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
  padding: 4px 6px;
  border-radius: 4px;
}
.product-info {
  padding: 10px 12px;
  min-height: 100px;
  display: flex;
  flex-direction: column;
}
.product-name {
  font-size: 13px;
  font-weight: 400;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
  margin-bottom: 6px;
}
.product-price { display: flex; align-items: baseline; gap: 6px; }
.price-current { color: #ff5000; font-size: 18px; font-weight: 700; }
.price-original { color: #999; font-size: 12px; text-decoration: line-through; }
.product-sales { font-size: 11px; color: #999; margin-top: 4px; display: block; }
</style>
