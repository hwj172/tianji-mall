<template>
  <div class="product-card" @click="$router.push(`/product/${product.id}`)">
    <div class="product-image">
      <AppImage :src="firstImage" :alt="product.name" :size="200" />
      <span v-if="showOriginalPrice && discountPercent != null" class="discount-badge">-{{ discountPercent }}%</span>
    </div>
    <div class="product-info">
      <h4 class="product-name">
        <template v-for="(part, i) in nameParts" :key="i">
          <mark v-if="part.match" class="name-highlight">{{ part.text }}</mark>
          <template v-else>{{ part.text }}</template>
        </template>
      </h4>
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
import { getFirstImage } from '@/utils/image'
import AppImage from '@/components/common/AppImage.vue'

const props = defineProps({
  product: { type: Object, required: true },
  showOriginalPrice: { type: Boolean, default: false },
  keyword: { type: String, default: '' }
})

const firstImage = computed(() => getFirstImage(props.product.images))

// 搜索词高亮：把商品名按命中位置拆成 [前, 命中, 后] 三段（大小写不敏感，仅高亮首次命中）
const nameParts = computed(() => {
  const name = props.product.name || ''
  const kw = (props.keyword || '').trim()
  if (!kw || !name) return [{ text: name, match: false }]
  const idx = name.toLowerCase().indexOf(kw.toLowerCase())
  if (idx === -1) return [{ text: name, match: false }]
  return [
    { text: name.slice(0, idx), match: false },
    { text: name.slice(idx, idx + kw.length), match: true },
    { text: name.slice(idx + kw.length), match: false }
  ]
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
  background: #121826;
  transition: transform .15s, box-shadow .15s, border-color .15s;
  border: 1px solid rgba(255, 255, 255, .07);
}
.product-card:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 80, 0, .55);
  box-shadow: 0 1px 2px rgba(0, 0, 0, .45), 0 0 28px rgba(255, 80, 0, .28), 0 8px 24px rgba(0, 0, 0, .4);
}
.product-image {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: #1a2233;
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
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
  margin-bottom: 6px;
  color: #c3cbda;
}
.name-highlight {
  color: #ff5000;
  background: transparent;
  font-weight: 600;
}
.product-price { display: flex; align-items: baseline; gap: 6px; }
.price-current { color: #ff5000; font-size: 18px; font-weight: 700; }
.price-original { color: #5c6a82; font-size: 12px; text-decoration: line-through; }
.product-sales { font-size: 11px; color: #8b96ab; margin-top: auto; padding-top: 4px; display: block; }
</style>
