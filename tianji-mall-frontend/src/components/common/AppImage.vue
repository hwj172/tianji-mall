<template>
  <img
    :src="src"
    :alt="alt"
    loading="lazy"
    decoding="async"
    @error="handleError"
  />
</template>

<script setup>
import { imageOnError } from '@/utils/image'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  // 仅用于加载失败占位图尺寸；实际显示尺寸由容器样式控制
  size: { type: Number, default: 200 }
})

function handleError(e) {
  imageOnError(e, props.size)
}
</script>
