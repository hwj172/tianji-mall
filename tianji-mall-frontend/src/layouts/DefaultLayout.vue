<template>
  <div class="layout">
    <!-- 顶部栏 -->
    <header class="top-bar">
      <div class="top-bar-inner">
        <router-link to="/" class="logo">天机商城</router-link>
        <div class="search-bar">
          <el-autocomplete
            v-model="keyword"
            :fetch-suggestions="querySearch"
            placeholder="搜索商品"
            size="large"
            clearable
            :trigger-on-focus="true"
            class="search-input"
            @select="onSelectSuggestion"
            @keyup.enter="search"
          >
            <template #default="{ item }">
              <div class="suggest-item">
                <el-icon class="suggest-icon"><Clock v-if="item.tag === 'history'" /><Search v-else /></el-icon>
                <span class="suggest-text">{{ item.value }}</span>
                <span class="suggest-tag">{{ item.tag === 'history' ? '历史' : '搜索' }}</span>
              </div>
            </template>
            <template #append><el-button @click="search" class="search-btn">搜索</el-button></template>
          </el-autocomplete>
        </div>
        <div class="header-actions">
          <router-link to="/" title="返回首页"><el-button text><el-icon><HomeFilled /></el-icon></el-button></router-link>
          <template v-if="userStore.isLoggedIn">
            <router-link v-if="userStore.isSeller" to="/seller"><el-button text><el-icon><Shop /></el-icon> 商家中心</el-button></router-link>
            <router-link to="/cart"><el-badge :value="cartStore.count" :hidden="!cartStore.count"><el-button text class="cart-icon"><el-icon><ShoppingCart /></el-icon> 购物车</el-button></el-badge></router-link>
            <router-link to="/notification/list"><el-badge :value="unreadCount" :hidden="!unreadCount"><el-button text><el-icon><Bell /></el-icon></el-button></el-badge></router-link>
            <router-link to="/chat"><el-button text><el-icon><ChatDotRound /></el-icon> AI导购</el-button></router-link>
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
import { getProductSuggest, getUnreadCount } from '@/api'
import { Clock, Search } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const keyword = ref('')
const unreadCount = ref(0)

const HISTORY_KEY = 'search_history'

onMounted(() => {
  cartStore.refreshCount()
  if (userStore.isLoggedIn) refreshUnread()
})

async function refreshUnread() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data != null ? Number(res.data) : 0
  } catch { /* ignore */ }
}

function getHistory() {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]') } catch { return [] }
}

function saveHistory(kw) {
  const h = getHistory().filter(w => w !== kw)
  h.unshift(kw)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(h.slice(0, 10)))
}

// el-autocomplete 联想：输入时返回「联想词 + 历史」，空输入时只显示历史
async function querySearch(query, cb) {
  const history = getHistory().map(w => ({ value: w, tag: 'history' }))
  if (!query || !query.trim()) {
    cb(history)
    return
  }
  try {
    const res = await getProductSuggest(query.trim())
    const suggest = (res.data || []).map(w => ({ value: w, tag: 'suggest' }))
    // 去重（历史词优先），联想在前、历史在后
    const merged = [
      ...suggest.filter(s => !history.some(h => h.value === s.value)),
      ...history.filter(h => !suggest.some(s => s.value === h.value))
    ]
    cb(merged)
  } catch {
    cb(history)
  }
}

function onSelectSuggestion(item) {
  keyword.value = item.value
  search()
}

function search() {
  const kw = keyword.value.trim()
  if (kw) {
    saveHistory(kw)
    router.push({ name: 'productList', query: { keyword: kw } })
  }
}
</script>

<style scoped>
.layout { min-height: 100vh; background: transparent; }
.top-bar { background: rgba(14, 20, 36, .85); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); border-bottom: 1px solid rgba(255, 80, 0, .55); box-shadow: 0 1px 0 rgba(255, 80, 0, .18), 0 6px 28px rgba(255, 80, 0, .14); position: sticky; top: 0; z-index: 100; }
.top-bar-inner { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; height: 64px; gap: 20px; }
.logo { font-size: 24px; font-weight: 700; color: #ff5000; white-space: nowrap; text-shadow: 0 0 14px rgba(255, 80, 0, .55); }
.search-bar { flex: 1; max-width: 540px; }
.search-input { width: 100%; }
.search-input :deep(.el-input__wrapper) { border-radius: 20px 0 0 20px; border: 2px solid #ff5000; box-shadow: 0 0 12px rgba(255, 80, 0, .18); }
.search-input :deep(.el-input__wrapper.is-focus) { border-color: #ff7a3d; box-shadow: 0 0 0 2px rgba(255, 122, 61, .2) inset, 0 0 18px rgba(255, 80, 0, .28); }
.search-btn { background: linear-gradient(135deg, #ff7a3d, #ff5000); border-color: transparent; border-radius: 0 20px 20px 0; }
.suggest-item { display: flex; align-items: center; gap: 8px; width: 100%; }
.suggest-icon { font-size: 12px; }
.suggest-text { flex: 1; font-size: 13px; color: #c3cbda; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-tag { font-size: 11px; color: #5c6a82; background: rgba(255,255,255,.08); border-radius: 3px; padding: 1px 6px; }
.header-actions { display: flex; align-items: center; gap: 12px; white-space: nowrap; }
.user-name { cursor: pointer; color: #8b96ab; }
.main { max-width: 1200px; margin: 12px auto; min-height: calc(100vh - 200px); }
.footer { text-align: center; color: #5c6a82; padding: 24px; font-size: 12px; }
</style>
