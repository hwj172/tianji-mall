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
          <template v-if="userStore.isLoggedIn">
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
    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <footer class="footer">© 2026 天机商城 · 仿淘宝智能电商平台</footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const keyword = ref('')

onMounted(() => cartStore.refreshCount())

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
.main { max-width: 1200px; margin: 12px auto; min-height: calc(100vh - 200px); }
.footer { text-align: center; color: #999; padding: 24px; font-size: 12px; }
</style>
