<template>
  <div ref="pageRef" class="home-page">
    <!-- 主体：分类侧栏 + Banner + 快捷入口 -->
    <div class="home-hero">
      <div class="category-sidebar">
        <div class="category-title">全部商品分类</div>
        <div
          v-for="cat in categories.slice(0, 10)"
          :key="cat.id"
          class="category-item"
          :class="{ active: activeCategory === cat.id }"
          @mouseenter="activeCategory = cat.id"
          @mouseleave="activeCategory = null"
          @click="$router.push({ name: 'productList', query: { categoryId: cat.id } })"
        >
          <span class="category-label"><el-icon class="category-icon"><component :is="categoryIcon(cat.name)" /></el-icon>{{ cat.name }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
      <HomeBanner :banners="homeData.banners" class="hero-banner" />
      <div class="hero-sidebar">
        <div class="hero-user">
          <div class="user-avatar"><el-icon><User /></el-icon></div>
          <div class="user-hi">{{ userStore.isLoggedIn ? `Hi, ${userStore.userInfo?.username || '用户'}` : 'Hi, 欢迎光临' }}</div>
          <el-button v-if="!userStore.isLoggedIn" class="user-login-btn" size="small" round @click="$router.push('/login')">登录 / 注册</el-button>
          <div class="user-tags">
            <template v-if="userStore.isLoggedIn">
              <span @click="$router.push('/order/list')">我的订单</span>
              <span @click="$router.push('/coupon/center')">领券中心</span>
            </template>
            <template v-else>
              <span @click="$router.push('/coupon/center')">新人福利</span>
              <span @click="$router.push('/coupon/center')">领券中心</span>
            </template>
          </div>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/seckill')"><el-icon class="quick-icon"><Timer /></el-icon><div class="quick-label">限时秒杀</div></div>
          <div class="quick-item" @click="$router.push('/groupbuy')"><el-icon class="quick-icon"><Connection /></el-icon><div class="quick-label">阶梯拼团</div></div>
          <div class="quick-item" @click="$router.push('/coupon/center')"><el-icon class="quick-icon"><Stamp /></el-icon><div class="quick-label">领券中心</div></div>
          <div class="quick-item" @click="$router.push('/chat')"><el-icon class="quick-icon"><ChatDotRound /></el-icon><div class="quick-label">AI 导购</div></div>
        </div>
        <div class="hero-notice">
          <h4>公告</h4>
          <p>秒杀专区已上线</p>
          <p>阶梯拼团新玩法</p>
          <p>AI 导购帮你挑</p>
        </div>
      </div>
    </div>

    <!-- 热销排行 -->
    <section class="home-section" v-if="loading || homeData.hotProducts?.length">
      <div class="section-header">
        <h3>热销排行榜</h3>
        <span class="section-more" @click="$router.push('/product/list?sort=sales')">查看更多 →</span>
      </div>
      <template v-if="loading">
        <ProductGridSkeleton :cols="4" />
      </template>
      <div class="product-grid cols-4" v-else>
        <ProductCard v-for="p in homeData.hotProducts" :key="p.id" :product="p" />
      </div>
    </section>

    <!-- 个性化推荐（独立接口） -->
    <section class="home-section" v-if="loading || personalRecommend.length || personalAlsoBuy.length">
      <template v-if="loading">
        <div class="section-header"><h3>猜你喜欢</h3></div>
        <ProductGridSkeleton :cols="5" />
      </template>
      <template v-else>
        <template v-if="personalRecommend.length">
          <div class="section-header"><h3>猜你喜欢</h3></div>
          <div class="product-grid cols-5">
            <div v-for="p in personalRecommend" :key="p.id" class="rec-card">
              <div class="rec-reason" v-if="p.reason">{{ p.reason }}</div>
              <ProductCard :product="p" />
            </div>
          </div>
        </template>
        <template v-if="personalAlsoBuy.length">
          <div class="section-header" style="margin-top: 24px"><h3>买了还买</h3></div>
          <div class="product-grid cols-5">
            <div v-for="p in personalAlsoBuy" :key="p.id" class="rec-card">
              <div class="rec-reason" v-if="p.reason">{{ p.reason }}</div>
              <ProductCard :product="p" />
            </div>
          </div>
        </template>
      </template>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { getHomeData } from '@/api'
import HomeBanner from '@/components/home/HomeBanner.vue'
import ProductCard from '@/components/common/ProductCard.vue'
import ProductGridSkeleton from '@/components/common/ProductGridSkeleton.vue'
import { useReveal } from '@/composables/useReveal'
import {
  User, Iphone, Monitor, OfficeBuilding, ShoppingBag, Cpu, House,
  Trophy, Present, Food, Reading, Box, Timer, Connection, Stamp, ChatDotRound
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const pageRef = ref(null)
// 商品卡片交错入场
useReveal(pageRef, '.product-card', { stagger: 0.05 })

const loading = ref(true)

const homeData = reactive({
  banners: [],
  categories: [],
  hotProducts: [],
  recommend: { guessYouLike: [], hotSales: [], buyAfterBuy: [] }
})
const categories = ref([])
const activeCategory = ref(null)
// 分类图标：Element 图标映射（keyword 命中，未命中兜底 Box）
const CATEGORY_ICONS = [
  { keyword: '手机', icon: Iphone },
  { keyword: '电脑', icon: Monitor },
  { keyword: '办公', icon: OfficeBuilding },
  { keyword: '服饰', icon: ShoppingBag },
  { keyword: '鞋', icon: ShoppingBag },
  { keyword: '智能', icon: Cpu },
  { keyword: '家电', icon: House },
  { keyword: '家居', icon: House },
  { keyword: '运动', icon: Trophy },
  { keyword: '美妆', icon: Present },
  { keyword: '食品', icon: Food },
  { keyword: '图书', icon: Reading },
  { keyword: '玩具', icon: Present },
]

function categoryIcon(name) {
  const hit = CATEGORY_ICONS.find(i => name.includes(i.keyword))
  return hit ? hit.icon : Box
}
const personalRecommend = ref([])
const personalAlsoBuy = ref([])

// 首页数据 sessionStorage 缓存（5 分钟 TTL），避免每次回首页全量拉取
const HOME_CACHE_KEY = 'home_data_cache'
const HOME_CACHE_TTL = 5 * 60 * 1000

function applyHomeData(data) {
  Object.assign(homeData, {
    banners: data.banners || [],
    hotProducts: data.hotProducts || [],
    recommend: data.recommend || { guessYouLike: [], hotSales: [], buyAfterBuy: [] }
  })
  categories.value = data.categories || []
  // 首页接口已返回推荐数据（RecommendResponse），直接复用，不再单独请求
  personalRecommend.value = homeData.recommend.guessYouLike || []
  personalAlsoBuy.value = homeData.recommend.buyAfterBuy || []
}

// 读缓存（JSON 容错：解析失败/结构异常/过期均视为无缓存）
function readHomeCache() {
  try {
    const raw = sessionStorage.getItem(HOME_CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || !parsed.ts || !parsed.data) return null
    if (Date.now() - parsed.ts > HOME_CACHE_TTL) return null
    return parsed.data
  } catch { return null }
}

onMounted(async () => {
  const cached = readHomeCache()
  if (cached) {
    applyHomeData(cached)
    loading.value = false
    return
  }
  try {
    const res = await getHomeData()
    if (res.data) {
      applyHomeData(res.data)
      try {
        sessionStorage.setItem(HOME_CACHE_KEY, JSON.stringify({ ts: Date.now(), data: res.data }))
      } catch { /* storage 满/禁用时忽略 */ }
    }
  } catch (e) {
    console.error('首页数据加载失败', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.home-page { max-width: 1200px; margin: 0 auto; }
.home-hero { display: flex; gap: 12px; margin-bottom: 20px; }
.category-sidebar { width: 200px; background: #1b1b1e; border-radius: 8px; padding: 4px 0; flex-shrink: 0; border: 1px solid rgba(255,255,255,.07); }
.category-title { padding: 10px 16px 12px; font-weight: 600; font-size: 14px; color: #f4f4f5; border-bottom: 1px solid rgba(255,255,255,.08); margin-bottom: 4px; }
.category-label { display: inline-flex; align-items: center; gap: 8px; }
.category-icon { color: #71717a; font-size: 15px; }
.category-item { padding: 7px 16px; display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: #a1a1aa; cursor: pointer; transition: background .12s, color .12s; }
.category-item:hover { color: #ff5000; background: rgba(255, 255, 255, .04); }
.category-item.active { color: #ff5000; background: rgba(255, 80, 0, .08); }
.hero-banner { flex: 1; }
.hero-sidebar { width: 220px; display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }
.hero-user {
  background: #ff5000;
  border-radius: 10px; padding: 14px; text-align: center; color: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, .4);
}
.user-avatar { font-size: 30px; margin-bottom: 6px; }
.user-avatar .el-icon { font-size: 30px; }
.user-hi { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.user-login-btn { border: 1px solid rgba(255, 255, 255, .6); background: transparent; color: #fff; }
.user-login-btn:hover { background: rgba(255, 255, 255, .15); color: #fff; }
.user-tags { display: flex; gap: 8px; margin-top: 10px; font-size: 11px; justify-content: center; }
.user-tags span { background: rgba(0, 0, 0, .18); color: #fff; padding: 2px 8px; border-radius: 4px; cursor: pointer; }
.user-tags span:hover { background: rgba(0, 0, 0, .3); }
.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.quick-item {
  background: #1b1b1e; border-radius: 8px; padding: 12px 0; text-align: center;
  border: 1px solid rgba(255,255,255,.07); cursor: pointer;
  transition: transform .15s, border-color .15s, box-shadow .15s;
}
.quick-item:hover { transform: translateY(-2px); border-color: rgba(255, 255, 255, .16); box-shadow: 0 4px 16px rgba(0, 0, 0, .35); }
.quick-icon { font-size: 20px; color: #ff5000; margin-bottom: 4px; }
/* AI 导购用科技青标识，区分于品牌橙 */
.quick-item:last-child .quick-icon { color: var(--tianji-cyan); }
.quick-label { font-size: 11px; color: #a1a1aa; }
.hero-notice { background: #1b1b1e; border-radius: 8px; padding: 12px; flex: 1; border: 1px solid rgba(255,255,255,.07); }
.hero-notice h4 { font-size: 13px; margin-bottom: 8px; color: #f4f4f5; }
.hero-notice p { font-size: 12px; color: #71717a; padding: 3px 0; }
.home-section { background: #1b1b1e; border-radius: 8px; padding: 20px; margin-bottom: 12px; border: 1px solid rgba(255,255,255,.07); }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { font-size: 17px; color: #f4f4f5; }
.section-more { font-size: 13px; color: #71717a; cursor: pointer; }
.section-more:hover { color: #ff5000; }
.product-grid { display: grid; gap: 16px; }
.product-grid.cols-4 { grid-template-columns: repeat(4, 1fr); }
.product-grid.cols-5 { grid-template-columns: repeat(5, 1fr); }
.rec-card { position: relative; }
.rec-reason { position: absolute; top: 4px; left: 4px; z-index: 2; font-size: 11px; color: #ff5000; background: rgba(18, 24, 38, .9); border: 1px solid rgba(255, 80, 0, .35); border-radius: 10px; padding: 1px 8px; max-width: 90%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 响应式：窄屏首屏折叠为单列 */
@media (max-width: 900px) {
  .home-hero { flex-direction: column; }
  .category-sidebar, .hero-sidebar { width: 100%; }
  .product-grid.cols-5, .product-grid.cols-4 { grid-template-columns: repeat(2, 1fr); }
}
</style>
