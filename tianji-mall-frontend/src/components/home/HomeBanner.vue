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
import { imageOnError } from '@/utils/image'

defineProps({ banners: { type: Array, default: () => [] } })

function onImageError(e) {
  imageOnError(e, 300)
}
</script>

<style scoped>
.home-banner { border-radius: 8px; overflow: hidden; }
.banner-link { position: relative; display: block; height: 300px; }
.banner-link::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: 0; height: 60px;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, .25));
  pointer-events: none;
}
.banner-link img { width: 100%; height: 100%; object-fit: cover; }
.placeholder {
  height: 300px;
  background: linear-gradient(135deg, #ff6b35, #ff5000);
  display: flex; align-items: center; justify-content: center;
}
.placeholder-content { text-align: center; color: #fff; }
.placeholder-content h3 { font-size: 32px; margin-bottom: 8px; }
.placeholder-content p { font-size: 14px; opacity: .9; }

/* 轮播指示器：普通态小圆点，active 态拉长圆角条 */
.home-banner :deep(.el-carousel__indicators--horizontal) { bottom: 14px; }
.home-banner :deep(.el-carousel__indicator) { padding: 0 3px; }
.home-banner :deep(.el-carousel__indicator .el-carousel__button) {
  width: 7px; height: 7px; border-radius: 50%; background: #121826; opacity: .6;
  transition: width .2s;
}
.home-banner :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 18px; border-radius: 4px; opacity: 1;
}
</style>
