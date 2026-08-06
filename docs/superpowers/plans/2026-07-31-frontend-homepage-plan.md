# 首页实现计划

> **面向 AI 代理的工作者：** 使用 superpowers:subagent-driven-development（推荐）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 实现仿淘宝首页，调用 `GET /api/home`，展示轮播 Banner、分类导航、热销商品、推荐模块

**架构：** HomePage.vue 调用 API 获取数据，传递给子组件渲染。ProductCard 作为全局复用组件。DefaultLayout 更新为橙色主题。

**技术栈：** Vue3 + Element Plus + Axios + Pinia

**依赖：** 后端网关 8080 运行，`/api/home` 公开端点可用

---

## 文件变更

| 操作 | 文件 | 职责 |
|------|------|------|
| 创建 | `src/api/index.js` | 首页 API 方法 |
| 创建 | `src/components/common/ProductCard.vue` | 商品卡片（全局复用） |
| 创建 | `src/components/home/HomeBanner.vue` | 轮播 Banner |
| 重写 | `src/views/home/HomePage.vue` | 首页主容器 |
| 修改 | `src/layouts/DefaultLayout.vue` | 更新头部和导航样式 |

---

### 任务 1：API 层 — 首页接口

**文件：** 创建 `tianji-mall-frontend/src/api/index.js`

- [ ] **步骤 1：创建 API 模块，封装首页请求**

```js
import request from './request'

export function getHomeData() {
  return request.get('/home')
}
```

- [ ] **步骤 2：验证编译**

```bash
cd tianji-mall-frontend && npx vite build
```
预期：无编译错误

---

### 任务 2：ProductCard — 商品卡片组件

**文件：** 创建 `tianji-mall-frontend/src/components/common/ProductCard.vue`

> 此组件全局复用——首页、商品列表、推荐、订单详情等都要用到。Props 接收 `id, name, price, sales, images`。

- [ ] **步骤 1：创建 ProductCard.vue**

```vue
<template>
  <div class="product-card" @click="$router.push(`/product/${product.id}`)">
    <div class="product-image">
      <img :src="firstImage" :alt="product.name" @error="onImageError" />
    </div>
    <div class="product-info">
      <h4 class="product-name">{{ product.name }}</h4>
      <div class="product-price">
        <span class="price-current">¥{{ product.price }}</span>
        <span class="price-original" v-if="showOriginalPrice">¥{{ product.originalPrice }}</span>
      </div>
      <span class="product-sales" v-if="product.sales != null">已售 {{ formatSales(product.sales) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  product: { type: Object, required: true },
  showOriginalPrice: { type: Boolean, default: false }
})

const firstImage = computed(() => {
  if (!props.product.images) return ''
  try {
    const imgs = typeof props.product.images === 'string'
      ? JSON.parse(props.product.images)
      : props.product.images
    return imgs[0] || ''
  } catch { return '' }
})

function onImageError(e) {
  e.target.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect fill="%23f5f5f5" width="200" height="200"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="14">暂无图片</text></svg>'
}

function formatSales(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return n.toString()
}
</script>

<style scoped>
.product-card {
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid #f0f0f0;
}
.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
}
.product-image {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  background: #fafafa;
}
.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-info {
  padding: 10px 12px;
}
.product-name {
  font-size: 13px;
  font-weight: 400;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}
.product-price { display: flex; align-items: baseline; gap: 6px; }
.price-current { color: #ff5000; font-size: 18px; font-weight: 700; }
.price-original { color: #999; font-size: 12px; text-decoration: line-through; }
.product-sales { font-size: 11px; color: #999; margin-top: 4px; display: block; }
</style>
```

- [ ] **步骤 2：验证编译**

```bash
cd tianji-mall-frontend && npx vite build
```
预期：BUILD SUCCESS

---

### 任务 3：HomeBanner — 轮播 Banner

**文件：** 创建 `tianji-mall-frontend/src/components/home/HomeBanner.vue`

> Props: `banners[]`（Banner 数组），使用 `el-carousel`。

- [ ] **步驟 1：创建 HomeBanner.vue**

```vue
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
```

- [ ] **步骤 2：验证编译**

```bash
cd tianji-mall-frontend && npx vite build
```
预期：BUILD SUCCESS

---

### 任务 4：HomePage — 首页主容器

**文件：** 重写 `tianji-mall-frontend/src/views/home/HomePage.vue`

> 调用 `getHomeData()`，组装 Banner + 分类 + 热销 + 推荐区块。

- [ ] **步骤 1：重写 HomePage.vue**

```vue
<template>
  <div class="home-page">
    <!-- 主体：分类侧栏 + Banner + 快捷入口 -->
    <div class="home-hero">
      <div class="category-sidebar">
        <div class="category-title">全部商品分类</div>
        <div
          v-for="cat in categories.slice(0, 10)"
          :key="cat.id"
          class="category-item"
          @mouseenter="activeCategory = cat.id"
          @mouseleave="activeCategory = null"
          @click="$router.push({ name: 'productList', query: { categoryId: cat.id } })"
        >
          <span>{{ cat.name }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
      <HomeBanner :banners="homeData.banners" class="hero-banner" />
      <div class="hero-sidebar">
        <div class="hero-user" v-if="!userStore.isLoggedIn">
          <div class="user-avatar">👤</div>
          <div class="user-hi">Hi, 欢迎光临</div>
          <el-button type="primary" size="small" round @click="$router.push('/login')">登录 / 注册</el-button>
          <div class="user-tags"><span>新人福利</span><span>领券中心</span></div>
        </div>
        <div class="hero-user" v-else>
          <div class="user-avatar">👤</div>
          <div class="user-hi">Hi, {{ userStore.userInfo?.username }}</div>
          <div class="user-tags"><span>我的订单</span><span>领券中心</span></div>
        </div>
        <div class="hero-notice">
          <h4>公告</h4>
          <p>🆕 秒杀专区已上线</p>
          <p>🎉 阶梯拼团新玩法</p>
          <p>🤖 AI 导购帮你挑</p>
        </div>
      </div>
    </div>

    <!-- 热销排行 -->
    <section class="home-section" v-if="homeData.hotProducts?.length">
      <div class="section-header">
        <h3>🔥 热销排行榜</h3>
        <span class="section-more" @click="$router.push('/product/list?sort=sales')">查看更多 →</span>
      </div>
      <div class="product-grid cols-4">
        <ProductCard v-for="p in homeData.hotProducts" :key="p.id" :product="p" />
      </div>
    </section>

    <!-- 猜你喜欢 -->
    <section class="home-section" v-if="homeData.recommend?.guessYouLike?.length">
      <div class="section-header">
        <h3>💝 猜你喜欢</h3>
      </div>
      <div class="product-grid cols-5">
        <ProductCard v-for="p in homeData.recommend.guessYouLike" :key="p.id" :product="p" />
      </div>
    </section>

    <!-- 热销商品推荐 -->
    <section class="home-section" v-if="homeData.recommend?.hotSales?.length">
      <div class="section-header">
        <h3>🏆 热销推荐</h3>
      </div>
      <div class="product-grid cols-5">
        <ProductCard v-for="p in homeData.recommend.hotSales" :key="p.id" :product="p" />
      </div>
    </section>

    <!-- 买了还买 -->
    <section class="home-section" v-if="homeData.recommend?.buyAfterBuy?.length">
      <div class="section-header">
        <h3>🛒 买了还买</h3>
      </div>
      <div class="product-grid cols-5">
        <ProductCard v-for="p in homeData.recommend.buyAfterBuy" :key="p.id" :product="p" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getHomeData } from '@/api'
import HomeBanner from '@/components/home/HomeBanner.vue'
import ProductCard from '@/components/common/ProductCard.vue'

const userStore = useUserStore()

const homeData = reactive({
  banners: [],
  categories: [],
  hotProducts: [],
  recommend: { guessYouLike: [], hotSales: [], buyAfterBuy: [] }
})
const categories = ref([])
const activeCategory = ref(null)

onMounted(async () => {
  try {
    const res = await getHomeData()
    if (res.data) {
      Object.assign(homeData, {
        banners: res.data.banners || [],
        hotProducts: res.data.hotProducts || [],
        recommend: res.data.recommend || { guessYouLike: [], hotSales: [], buyAfterBuy: [] }
      })
      categories.value = res.data.categories || []
    }
  } catch (e) {
    console.error('首页数据加载失败', e)
  }
})
</script>

<style scoped>
.home-page { max-width: 1200px; margin: 0 auto; }
.home-hero { display: flex; gap: 12px; margin-bottom: 20px; }
.category-sidebar { width: 200px; background: #fff; border-radius: 8px; padding: 4px 0; flex-shrink: 0; }
.category-title { padding: 10px 16px; font-weight: 600; font-size: 14px; color: #ff5000; }
.category-item { padding: 7px 16px; display: flex; justify-content: space-between; align-items: center; font-size: 13px; cursor: pointer; }
.category-item:hover { color: #ff5000; background: #fff5f0; }
.hero-banner { flex: 1; }
.hero-sidebar { width: 200px; display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }
.hero-user { background: #fff; border-radius: 8px; padding: 16px; text-align: center; }
.user-avatar { font-size: 36px; margin-bottom: 8px; }
.user-hi { font-size: 13px; color: #666; margin-bottom: 10px; }
.user-tags { display: flex; gap: 8px; margin-top: 10px; font-size: 12px; justify-content: center; }
.user-tags span { background: #fff5f0; color: #ff5000; padding: 2px 8px; border-radius: 4px; cursor: pointer; }
.hero-notice { background: #fff; border-radius: 8px; padding: 12px; flex: 1; }
.hero-notice h4 { font-size: 13px; margin-bottom: 8px; color: #333; }
.hero-notice p { font-size: 12px; color: #666; padding: 3px 0; }
.home-section { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { font-size: 18px; }
.section-more { font-size: 13px; color: #999; cursor: pointer; }
.section-more:hover { color: #ff5000; }
.product-grid { display: grid; gap: 16px; }
.product-grid.cols-4 { grid-template-columns: repeat(4, 1fr); }
.product-grid.cols-5 { grid-template-columns: repeat(5, 1fr); }
</style>
```

- [ ] **步骤 2：验证编译**

```bash
cd tianji-mall-frontend && npx vite build
```
预期：BUILD SUCCESS

---

### 任务 5：DefaultLayout — 更新头部样式

**文件：** 修改 `tianji-mall-frontend/src/layouts/DefaultLayout.vue`

- [ ] **步骤 1：更新 header 为橙色主题，添加二级导航栏**

将 `<el-header>` 模板部分替换为淘宝风格：

```vue
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
            <router-link to="/cart"><el-badge :value="cartCount" :hidden="!cartCount"><el-button text>🛒 购物车</el-button></el-badge></router-link>
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
```

脚本部分新增 `showSubNav`：

```js
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const cartCount = ref(0)

// 只在首页显示二级导航
const showSubNav = computed(() => route.path === '/')

function search() {
  if (keyword.value.trim()) {
    router.push({ name: 'productList', query: { keyword: keyword.value.trim() } })
  }
}
```

- [ ] **步骤 2：更新样式**

```css
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
.main { max-width: 1200px; margin: 12px auto; }
.footer { text-align: center; color: #999; padding: 24px; font-size: 12px; }
</style>
```

- [ ] **步骤 3：验证构建**

```bash
cd tianji-mall-frontend && npx vite build
```
预期：BUILD SUCCESS

---

### 任务 6：联调验证

- [ ] **步骤 1：启动后端服务（确保网关运行）**

确认 `http://192.168.150.11:8080/api/home` 可访问

- [ ] **步骤 2：启动前端开发服务器**

```bash
cd tianji-mall-frontend && npm run dev
```

- [ ] **步骤 3：浏览器打开 `http://localhost:5173`**

检查项：
- 顶部橙色 Logo + 搜索框
- Banner 轮播或占位渐变
- 左侧分类列表
- 热销商品 4 列卡片
- 推荐区块
- 未登录状态正常展示

---

## 自检

**1. 规格覆盖度：**
- ✅ Banner 轮播 → 任务 3
- ✅ 分类导航 → 任务 4
- ✅ 热销榜 → 任务 4
- ✅ 推荐区块 → 任务 4
- ✅ 搜索框跳转 → 任务 5
- ✅ 未登录正常 → 任务 4 (userStore.isLoggedIn)

**2. 占位符扫描：** 无 TODO、待定

**3. 类型一致性：** ProductCard props 与 HomePage 使用的字段一致（id, name, price, sales, images）
