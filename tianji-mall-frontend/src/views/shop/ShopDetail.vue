<template>
  <div class="shop-page" v-if="shop">
    <!-- 店铺头部 -->
    <div class="shop-header">
      <div class="sh-left">
        <el-avatar v-if="shop.logo" :src="shop.logo" :size="72" shape="square" />
        <el-avatar v-else :size="72" shape="square" :icon="Shop" />
        <div class="sh-info">
          <h2>{{ shop.name }}</h2>
          <p>{{ shop.description || '暂无店铺描述' }}</p>
          <div class="sh-stats">
            <span>粉丝 {{ followerCount }}</span>
          </div>
        </div>
      </div>
      <div class="sh-actions">
        <el-button
          :type="isFollowing ? 'default' : 'danger'"
          @click="handleFollow"
          :loading="following"
        >{{ isFollowing ? '已关注' : '+ 关注' }}</el-button>
      </div>
    </div>

    <!-- 店铺公告 -->
    <div class="shop-notice" v-if="shop.notice">
      <span class="notice-icon">📢</span>
      <span class="notice-text">{{ shop.notice }}</span>
    </div>

    <!-- 商品列表 -->
    <div class="shop-products">
      <div class="sp-filter">
        <h3>{{ categoryName || '全部商品' }}</h3>
        <div class="sp-sorts">
          <span
            v-for="opt in sortOptions" :key="opt.value"
            class="sp-sort"
            :class="{ active: sortBy === opt.value }"
            @click="changeSort(opt.value)"
          >{{ opt.label }}</span>
        </div>
      </div>

      <!-- 分类筛选 tab -->
      <div class="sp-categories" v-if="categories.length">
        <span
          :class="['sp-cat', { active: !categoryId }]"
          @click="changeCategory(null)"
        >全部</span>
        <span
          v-for="c in categories" :key="c.categoryId"
          :class="['sp-cat', { active: categoryId === c.categoryId }]"
          @click="changeCategory(c.categoryId)"
        >{{ c.name }}<em class="sp-cat-count">{{ c.count }}</em></span>
      </div>

      <div class="product-grid" v-if="products.length">
        <ProductCard v-for="p in products" :key="p.id" :product="p" />
      </div>
      <el-empty v-else description="暂无商品" />

      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
          background
        />
      </div>
    </div>
  </div>

  <el-empty v-else-if="!loading" description="店铺不存在或已关闭" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Shop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getShopDetail, followShop } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'

const route = useRoute()
const shop = ref(null)
const products = ref([])
const categories = ref([])
const total = ref(0)
const followerCount = ref(0)
const isFollowing = ref(false)
const loading = ref(false)
const following = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const categoryId = ref(null)
const sortBy = ref('')

const sortOptions = [
  { label: '综合', value: '' },
  { label: '销量', value: 'sales' },
  { label: '价格↑', value: 'price_asc' },
  { label: '价格↓', value: 'price_desc' }
]
const categoryName = ref('')

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (categoryId.value) params.categoryId = categoryId.value
    if (sortBy.value) params.sortBy = sortBy.value
    const res = await getShopDetail(route.params.id, params)
    if (res.data) {
      shop.value = res.data.shop
      products.value = res.data.products?.records || []
      total.value = res.data.products?.total || 0
      categories.value = res.data.categories || []
      followerCount.value = res.data.followerCount || 0
      isFollowing.value = res.data.isFollowing || false
      const current = categories.value.find(c => c.categoryId === categoryId.value)
      categoryName.value = current ? current.name : ''
    }
  } catch (e) {
    console.error('加载店铺详情失败', e)
  }
  finally { loading.value = false }
}

function changeCategory(id) {
  if (categoryId.value === id) return
  categoryId.value = id
  currentPage.value = 1
  loadData()
}

function changeSort(v) {
  if (sortBy.value === v) return
  sortBy.value = v
  currentPage.value = 1
  loadData()
}

async function handleFollow() {
  following.value = true
  try {
    const res = await followShop(shop.value.id)
    isFollowing.value = !isFollowing.value
    followerCount.value += isFollowing.value ? 1 : -1
    ElMessage.success(isFollowing.value ? '已关注' : '已取消关注')
  } catch { /* handle by interceptor */ }
  finally { following.value = false }
}
</script>

<style scoped>
.shop-page { max-width: 1200px; margin: 0 auto; }

.shop-header { background: #fff; border-radius: 8px; padding: 24px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.sh-left { display: flex; align-items: center; gap: 16px; }
.sh-info h2 { font-size: 22px; margin-bottom: 6px; }
.sh-info p { font-size: 13px; color: #666; margin-bottom: 8px; }
.sh-stats { font-size: 13px; color: #999; }

.shop-notice { background: #fff8e6; border: 1px solid #ffe9b8; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #8a6d1a; }
.notice-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.shop-products h3 { font-size: 17px; margin-bottom: 14px; }
.sp-filter { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.sp-sorts { display: flex; gap: 4px; }
.sp-sort { padding: 3px 10px; border-radius: 4px; cursor: pointer; font-size: 13px; color: #666; }
.sp-sort:hover, .sp-sort.active { background: #fff5f0; color: #ff5000; font-weight: 600; }

.sp-categories { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.sp-cat { font-size: 13px; color: #666; padding: 4px 14px; border-radius: 14px; border: 1px solid #e5e5e5; cursor: pointer; transition: all .2s; }
.sp-cat:hover { color: #ff5000; border-color: #ff5000; }
.sp-cat.active { color: #fff; background: #ff5000; border-color: #ff5000; }
.sp-cat-count { font-style: normal; font-size: 11px; margin-left: 3px; opacity: .8; }

.product-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
