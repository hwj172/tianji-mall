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

    <!-- 个性化推荐（独立接口） -->
    <section class="home-section" v-loading="recommendLoading" v-if="personalRecommend.length || personalAlsoBuy.length">
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
import { getHomeData, getProductRecommend } from '@/api'
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
const recommendLoading = ref(false)
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
    }
  } catch (e) {
    console.error('首页数据加载失败', e)
  }
  loadRecommend()
})

async function loadRecommend() {
  recommendLoading.value = true
  try {
    const res = await getProductRecommend({ count: 10 })
    if (res.data) {
      personalRecommend.value = res.data.recommend || []
      personalAlsoBuy.value = res.data.alsoBuy || []
    }
  } catch {
    // 静默降级
  } finally {
    recommendLoading.value = false
  }
}
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
