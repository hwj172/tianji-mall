<template>
  <div class="checkout-item">
    <div class="item-img" @click="$router.push(`/product/${item.product.id}`)">
      <AppImage :src="item.image" :alt="item.product.name" :size="80" />
    </div>
    <div class="item-info">
      <router-link :to="`/product/${item.product.id}`" class="item-name">{{ item.product.name }}</router-link>
      <span class="item-spec" v-if="item.specs">{{ item.specs }}</span>
    </div>
    <div class="item-price">¥{{ fmtPrice(item.price) }}</div>
    <div class="item-qty">×{{ item.cart.quantity }}</div>
    <div class="item-subtotal">¥{{ fmtPrice(item.price * item.cart.quantity) }}</div>
  </div>
</template>

<script setup>
import { fmtPrice } from '@/utils/format'
import AppImage from '@/components/common/AppImage.vue'

defineProps({
  item: { type: Object, required: true }
})
</script>

<style scoped>
.checkout-item { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid rgba(255,255,255,.08); }
.item-img { width: 72px; height: 72px; border-radius: 4px; overflow: hidden; cursor: pointer; background: #1a2233; flex-shrink: 0; }
.item-img img { width: 100%; height: 100%; object-fit: cover; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 14px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-name:hover { color: #ff5000; }
.item-spec { font-size: 12px; color: #5c6a82; }
.item-price, .item-qty, .item-subtotal { width: 90px; text-align: center; font-size: 14px; }
.item-price { color: #c3cbda; }
.item-subtotal { color: #ff5000; font-weight: 600; }
</style>
