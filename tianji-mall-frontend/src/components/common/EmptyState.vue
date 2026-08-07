<template>
  <el-empty :description="description" :image="image">
    <slot />
  </el-empty>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  image: { type: String, default: '' }
})
</script>
