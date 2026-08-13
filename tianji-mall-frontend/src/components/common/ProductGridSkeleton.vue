<template>
  <div class="product-grid" :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)` }">
    <div v-for="i in cols" :key="i" class="skeleton-card">
      <div class="sk-image"></div>
      <div class="sk-line sk-line-1"></div>
      <div class="sk-line sk-line-2"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  cols: { type: Number, default: 4 }
})
</script>

<style scoped>
.product-grid {
  display: grid;
  gap: 16px;
}
.skeleton-card {
  background: #1b1b1e;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, .07);
}
.sk-image {
  aspect-ratio: 1;
  background: linear-gradient(90deg, rgba(255,255,255,.05) 25%, rgba(255,255,255,.1) 37%, rgba(255,255,255,.05) 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}
.sk-line {
  height: 14px;
  border-radius: 4px;
  margin: 12px 12px 0;
  background: linear-gradient(90deg, rgba(255,255,255,.05) 25%, rgba(255,255,255,.1) 37%, rgba(255,255,255,.05) 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}
.sk-line-1 { width: 80%; }
.sk-line-2 { width: 50%; margin-top: 8px; margin-bottom: 16px; }
@keyframes shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
</style>
