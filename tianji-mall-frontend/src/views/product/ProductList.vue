<template>
  <div class="product-list-page">
    <!-- 搜索热词 -->
    <div class="hot-keywords" v-if="hotKeywords.length && !route.query.keyword">
      <span class="hot-label">热门搜索：</span>
      <span v-for="kw in hotKeywords" :key="kw" class="hot-tag" @click="search(kw)">{{ kw }}</span>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-sorts">
        <span
          v-for="opt in sortOptions" :key="opt.value"
          class="sort-item"
          :class="{ active: isSortActive(opt) }"
          @click="changeSort(opt.value)"
        >
          {{ opt.label }}
          <template v-if="opt.value === 'price' && isSortActive(opt)"><span class="sort-arrow">{{ priceArrow }}</span></template>
        </span>
      </div>
      <div class="filter-price">
        <el-input v-model="priceFrom" placeholder="¥ 最低价" size="small" class="price-input" @keyup.enter="applyPrice" />
        <span class="price-sep">—</span>
        <el-input v-model="priceTo" placeholder="¥ 最高价" size="small" class="price-input" @keyup.enter="applyPrice" />
        <el-button size="small" @click="applyPrice" type="primary">确定</el-button>
      </div>
    </div>

    <!-- 商品网格 -->
    <ProductGridSkeleton v-if="loading" :cols="4" />
    <div class="product-grid cols-4" v-else-if="products.length">
      <ProductCard v-for="p in products" :key="p.id" :product="p" :show-original-price="true" />
    </div>
    <EmptyState v-if="!loading && !products.length" description="暂无商品">
      <div class="empty-actions">
        <el-button v-if="hasActiveFilters" type="primary" plain @click="clearFilters">清除筛选</el-button>
        <el-button @click="router.push('/')">返回首页</el-button>
      </div>
    </EmptyState>

    <!-- 分页 -->
    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductList, getHotKeywords } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'
import ProductGridSkeleton from '@/components/common/ProductGridSkeleton.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const sortOptions = [
  { label: '综合', value: '' },
  { label: '价格', value: 'price' },
  { label: '销量', value: 'sales' },
  { label: '新品', value: 'created' }
]

const products = ref([])
const hotKeywords = ref([])

// 热门词模块级缓存（10 分钟 TTL）：避免每次进入列表页都重新拉取
let hotKeywordsCache = null
let hotKeywordsCacheTime = 0
const HOT_KEYWORDS_TTL = 10 * 60 * 1000
const loading = ref(false)
const currentPage = ref(1)
const total = ref(0)
const pageSize = 20

// URL query 是筛选参数的单一数据源，以下 ref 仅作 query 的镜像（供 UI 展示/输入框回显）
const currentSort = ref('')
const priceFrom = ref('')
const priceTo = ref('')

// 价格 tab 激活时显示当前方向箭头（price_asc↑ / price_desc↓），非激活无箭头
const isSortActive = (opt) => {
  if (opt.value === 'price') {
    return currentSort.value === 'price_asc' || currentSort.value === 'price_desc'
  }
  return currentSort.value === opt.value
}
const priceArrow = computed(() => (currentSort.value === 'price_asc' ? '↑' : '↓'))

// 有激活的筛选条件时显示「清除筛选」
const hasActiveFilters = computed(() =>
  Boolean(route.query.keyword || route.query.minPrice || route.query.maxPrice || route.query.sort)
)

// 从 URL query 同步 ref（currentSort 保持从 query 初始化的逻辑）
function syncFromQuery() {
  currentSort.value = route.query.sort ? String(route.query.sort) : ''
  priceFrom.value = route.query.minPrice ? String(route.query.minPrice) : ''
  priceTo.value = route.query.maxPrice ? String(route.query.maxPrice) : ''
  currentPage.value = route.query.page ? parseInt(route.query.page, 10) : 1
}
syncFromQuery()

onMounted(() => {
  loadProducts()
  loadHotKeywords()
})

// 筛选/排序/分页统一写回 URL（router.replace，不新增历史记录），由 watch 驱动加载
watch(() => route.query, () => {
  syncFromQuery()
  loadProducts().then(() => {
    // 仅交互触发的加载（非首屏）滚动回顶
    window.scrollTo({ top: 0, behavior: 'smooth' })
  })
})

async function loadProducts() {
  loading.value = true
  try {
    const res = await getProductList(buildParams())
    if (res.data) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载商品列表失败', e)
  } finally {
    loading.value = false
  }
}

async function loadHotKeywords() {
  // URL 带 keyword 时热词条已隐藏，跳过拉取
  if (route.query.keyword) return
  const now = Date.now()
  if (hotKeywordsCache && now - hotKeywordsCacheTime < HOT_KEYWORDS_TTL) {
    hotKeywords.value = hotKeywordsCache
    return
  }
  try {
    const res = await getHotKeywords()
    if (res.data) {
      hotKeywordsCache = res.data.slice(0, 10)
      hotKeywordsCacheTime = Date.now()
      hotKeywords.value = hotKeywordsCache
    }
  } catch { /* hot keywords are optional */ }
}

// 只从 route.query 读取筛选参数（单一数据源）
function buildParams() {
  const params = {
    page: currentPage.value,
    size: pageSize
  }
  if (route.query.keyword) params.keyword = route.query.keyword
  if (route.query.categoryId) params.categoryId = route.query.categoryId
  if (route.query.minPrice) params.minPrice = route.query.minPrice
  if (route.query.maxPrice) params.maxPrice = route.query.maxPrice
  if (route.query.sort) params.sortBy = route.query.sort
  return params
}

// 写回 URL：空值删 key，不新增历史记录
function updateQuery(patch) {
  const query = { ...route.query }
  for (const [key, value] of Object.entries(patch)) {
    if (value === '' || value == null) {
      delete query[key]
    } else {
      query[key] = value
    }
  }
  router.replace({ query })
}

function changeSort(value) {
  // 价格 tab：在升序/降序间翻转
  if (value === 'price') {
    value = currentSort.value === 'price_asc' ? 'price_desc' : 'price_asc'
  }
  updateQuery({ sort: value, page: 1 })
}

function applyPrice() {
  updateQuery({ minPrice: priceFrom.value.trim(), maxPrice: priceTo.value.trim(), page: 1 })
}

function onPageChange(page) {
  updateQuery({ page })
}

function clearFilters() {
  updateQuery({ sort: '', minPrice: '', maxPrice: '', keyword: '', page: 1 })
}

function search(keyword) {
  router.push({ name: 'productList', query: { keyword } })
}
</script>

<style scoped>
.product-list-page { max-width: 1200px; margin: 0 auto; }
.hot-keywords { padding: 10px 0; font-size: 13px; }
.hot-label { color: #ff5000; }
.hot-tag { color: #666; margin: 0 8px; cursor: pointer; }
.hot-tag:hover { color: #ff5000; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; background: #fff; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px; }
.filter-sorts { display: flex; gap: 4px; }
.sort-item { padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; color: #666; }
.sort-item:hover, .sort-item.active { background: #fff5f0; color: #ff5000; font-weight: 600; }
.sort-arrow { margin-left: 2px; }
.filter-price { display: flex; align-items: center; gap: 6px; }
.price-input { width: 90px; }
.price-sep { color: #999; font-size: 12px; }
.product-grid { display: grid; gap: 16px; }
.product-grid.cols-4 { grid-template-columns: repeat(4, 1fr); }
.pagination-wrap { display: flex; justify-content: center; margin-top: 24px; padding-bottom: 40px; }
.empty-actions { margin-top: 12px; }
</style>
