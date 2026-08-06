<template>
  <div class="home-banner" v-if="banners.length">
    <el-carousel :interval="4000" arrow="hover" height="300px">
      <el-carousel-item v-for="banner in banners" :key="banner.id">
        <a :href="banner.linkUrl || '#'" class="banner-link">
          <img :src="banner.imageUrl" :alt="banner.title" @error="onImageError" />
        </a>
      </el-carousel-item>
    </el-carousel>
  </div>
  <div class="home-banner placeholder" v-else>
    <div class="placeholder-content">
      <h3>天机商城</h3>
      <p>仿淘宝智能电商平台</p>
    </div>
  </div>
</template>

<script setup>
defineProps({ banners: { type: Array, default: () => [] } })

function onImageError(e) {
  e.target.style.display = 'none'
}
</script>

<style scoped>
.home-banner { border-radius: 8px; overflow: hidden; }
.banner-link { display: block; height: 300px; }
.banner-link img { width: 100%; height: 100%; object-fit: cover; }
.placeholder {
  height: 300px;
  background: linear-gradient(135deg, #ff6b35, #ff5000);
  display: flex; align-items: center; justify-content: center;
}
.placeholder-content { text-align: center; color: #fff; }
.placeholder-content h3 { font-size: 32px; margin-bottom: 8px; }
.placeholder-content p { font-size: 14px; opacity: .9; }
</style>
