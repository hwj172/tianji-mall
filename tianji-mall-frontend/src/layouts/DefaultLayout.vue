<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="top-bar">
      <div class="top-bar-inner">
        <router-link to="/" class="logo">天机商城</router-link>
        <div class="search-bar">
          <el-input v-model="keyword" placeholder="搜索商品" size="large" clearable @keyup.enter="search" class="search-input">
            <template #append><el-button @click="search" class="search-btn">搜索</el-button></template>
          </el-input>
        </div>
        <div class="header-actions">
          <router-link to="/" title="返回首页"><el-button text>🏠</el-button></router-link>
          <template v-if="userStore.isLoggedIn">
            <router-link to="/user/center"><el-button text>👤 个人中心</el-button></router-link>
            <router-link to="/cart"><el-badge :value="cartStore.count" :hidden="!cartStore.count"><el-button text>🛒 购物车</el-button></el-badge></router-link>
            <router-link to="/chat"><el-button text>🤖 AI导购</el-button></router-link>
            <el-dropdown>
              <span class="user-name">{{ userStore.userInfo?.username }}</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/user/center')">个人中心</el-dropdown-item>
                  <el-dropdown-item @click="$router.push('/order/list')">我的订单</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isSeller" @click="$router.push('/seller')">商家中心</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" @click="$router.push('/admin')">管理后台</el-dropdown-item>
                  <el-dropdown-item divided @click="userStore.logout()">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <router-link to="/login"><el-button text>登录</el-button></router-link>
            <router-link to="/register"><el-button type="primary" size="small">免费注册</el-button></router-link>
          </template>
        </div>
      </div>
    </header>
    <!-- 二级导航 -->
    <nav class="sub-nav" v-show="showSubNav">
      <div class="sub-nav-inner">
        <router-link to="/seckill">⚡ 限时秒杀</router-link>
        <router-link to="/groupbuy">🎯 阶梯拼团</router-link>
        <router-link to="/coupon/center">🎫 领券中心</router-link>
      </div>
    </nav>
    <main class="main"><router-view /></main>
    <footer class="footer">© 2026 天机商城 · 仿淘宝智能电商平台</footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const keyword = ref('')

onMounted(() => cartStore.refreshCount())

// 只在首页显示二级导航
const showSubNav = computed(() => route.path === '/')

function search() {
  if (keyword.value.trim()) {
    router.push({ name: 'productList', query: { keyword: keyword.value.trim() } })
  }
}
</script>

<style scoped>
.layout { min-height: 100vh; background: #f5f5f5; }
.top-bar { background: #fff; border-bottom: 2px solid #ff5000; position: sticky; top: 0; z-index: 100; }
.top-bar-inner { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; height: 64px; gap: 20px; }
.logo { font-size: 24px; font-weight: 700; color: #ff5000; white-space: nowrap; }
.search-bar { flex: 1; max-width: 540px; }
.search-input :deep(.el-input__wrapper) { border-radius: 20px 0 0 20px; border: 2px solid #ff5000; box-shadow: none; }
.search-btn { background: #ff5000; border-color: #ff5000; border-radius: 0 20px 20px 0; }
.header-actions { display: flex; align-items: center; gap: 12px; white-space: nowrap; }
.user-name { cursor: pointer; color: #666; }
.sub-nav { background: #fff; border-bottom: 1px solid #eee; }
.sub-nav-inner { max-width: 1200px; margin: 0 auto; display: flex; gap: 24px; padding: 8px 0; font-size: 13px; }
.sub-nav-inner a { color: #333; }
.sub-nav-inner a:hover { color: #ff5000; }
.main { max-width: 1200px; margin: 12px auto; min-height: calc(100vh - 200px); }
.footer { text-align: center; color: #999; padding: 24px; font-size: 12px; }
</style>
