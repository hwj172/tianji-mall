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
/* 全局路由过渡动效（作用于各 layout 内部 router-view 的 transition，layout 自身不被整体过渡） */
.fade-slide-enter-active, .fade-slide-leave-active { transition: opacity .2s, transform .2s; }
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; }
</style>
