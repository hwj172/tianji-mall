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
          :class="{ active: activeCategory === cat.id }"
          @mouseenter="activeCategory = cat.id"
          @mouseleave="activeCategory = null"
          @click="$router.push({ name: 'productList', query: { categoryId: cat.id } })"
        >
          <span>{{ categoryIcon(cat.name) }} {{ cat.name }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
      <HomeBanner :banners="homeData.banners" class="hero-banner" />
      <div class="hero-sidebar">
        <div class="hero-user">
          <div class="user-avatar">👤</div>
          <div class="user-hi">{{ userStore.isLoggedIn ? `Hi, ${userStore.userInfo?.username}` : 'Hi, 欢迎光临' }}</div>
          <el-button v-if="!userStore.isLoggedIn" class="user-login-btn" size="small" round @click="$router.push('/login')">登录 / 注册</el-button>
          <div class="user-tags">
            <template v-if="userStore.isLoggedIn">
              <span @click="$router.push('/order/list')">我的订单</span>
              <span @click="$router.push('/coupon/center')">领券中心</span>
            </template>
            <template v-else>
              <span>新人福利</span>
              <span @click="$router.push('/coupon/center')">领券中心</span>
            </template>
          </div>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/seckill')"><div class="quick-icon">⚡</div><div class="quick-label">限时秒杀</div></div>
          <div class="quick-item" @click="$router.push('/groupbuy')"><div class="quick-icon">🎯</div><div class="quick-label">阶梯拼团</div></div>
          <div class="quick-item" @click="$router.push('/coupon/center')"><div class="quick-icon">🎫</div><div class="quick-label">领券中心</div></div>
          <div class="quick-item" @click="$router.push('/chat')"><div class="quick-icon">🤖</div><div class="quick-label">AI 导购</div></div>
        </div>
        <div class="hero-notice">
          <h4>📢 公告</h4>
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

    <!-- 个性化推荐（独立接口） -->
    <section class="home-section" v-if="personalRecommend.length || personalAlsoBuy.length">
      <template v-if="personalRecommend.length">
        <div class="section-header"><h3>💝 猜你喜欢</h3></div>
        <div class="product-grid cols-5">
          <ProductCard v-for="p in personalRecommend" :key="p.id" :product="p" />
        </div>
      </template>
      <template v-if="personalAlsoBuy.length">
        <div class="section-header" style="margin-top: 24px"><h3>🛒 买了还买</h3></div>
        <div class="product-grid cols-5">
          <ProductCard v-for="p in personalAlsoBuy" :key="p.id" :product="p" />
        </div>
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

const userStore = useUserStore()

const homeData = reactive({
  banners: [],
  categories: [],
  hotProducts: [],
  recommend: { guessYouLike: [], hotSales: [], buyAfterBuy: [] }
})
const categories = ref([])
const activeCategory = ref(null)
const CATEGORY_ICONS = [
  { keyword: '手机', icon: '📱' },
  { keyword: '电脑', icon: '💻' },
  { keyword: '办公', icon: '💻' },
  { keyword: '服饰', icon: '👕' },
  { keyword: '鞋', icon: '👟' },
  { keyword: '智能', icon: '⌚' },
  { keyword: '家电', icon: '🏠' },
  { keyword: '家居', icon: '🛋️' },
  { keyword: '运动', icon: '⚽' },
  { keyword: '美妆', icon: '💄' },
  { keyword: '食品', icon: '🍎' },
  { keyword: '图书', icon: '📚' },
  { keyword: '玩具', icon: '🧸' },
]

function categoryIcon(name) {
  const hit = CATEGORY_ICONS.find(i => name.includes(i.keyword))
  return hit ? hit.icon : '📦'
}
const personalRecommend = ref([])
const personalAlsoBuy = ref([])

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
      // 首页接口已返回推荐数据（RecommendResponse），直接复用，不再单独请求
      personalRecommend.value = homeData.recommend.guessYouLike || []
      personalAlsoBuy.value = homeData.recommend.buyAfterBuy || []
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
.category-title { position: relative; padding: 10px 16px; font-weight: 600; font-size: 14px; color: #ff5000; }
.category-title::after {
  content: '';
  position: absolute; left: 16px; right: 16px; bottom: 0; height: 2px;
  background: linear-gradient(90deg, #ff5000, #ff7a3d); border-radius: 1px;
}
.category-item { padding: 7px 16px; display: flex; justify-content: space-between; align-items: center; font-size: 13px; cursor: pointer; transition: background .15s; }
.category-item:hover, .category-item.active { color: #ff5000; background: #fff5f0; font-weight: 600; }
.hero-banner { flex: 1; }
.hero-sidebar { width: 220px; display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }
.hero-user {
  background: linear-gradient(135deg, #ff7a3d, #ff5000);
  border-radius: 10px; padding: 14px; text-align: center; color: #fff;
  box-shadow: 0 2px 8px rgba(255, 80, 0, .2);
}
.user-avatar { font-size: 32px; margin-bottom: 6px; }
.user-hi { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.user-login-btn { border: 1px solid #fff; background: transparent; color: #fff; }
.user-login-btn:hover { background: rgba(255, 255, 255, .15); color: #fff; }
.user-tags { display: flex; gap: 8px; margin-top: 10px; font-size: 11px; justify-content: center; }
.user-tags span { background: rgba(255, 255, 255, .18); color: #fff; padding: 2px 8px; border-radius: 4px; cursor: pointer; }
.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.quick-item {
  background: #fff; border-radius: 10px; padding: 10px 0; text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .05); cursor: pointer;
  transition: transform .15s, box-shadow .15s;
}
.quick-item:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, .1); }
.quick-icon { font-size: 20px; }
.quick-label { font-size: 11px; color: #666; margin-top: 2px; }
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
