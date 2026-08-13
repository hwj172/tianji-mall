<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

onMounted(() => {
  // 刷新页面后 token 仍存在 → 拉取用户信息（角色/用户名），否则 isAdmin/isSeller 丢失
  if (userStore.token) {
    userStore.fetchUserInfo()
  }
})
</script>

<style>
/* 路由过渡动效统一定义在 global.css（此处不再重复，避免时长不一致） */
</style>
