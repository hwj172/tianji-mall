<template>
  <div v-if="loading && !product" v-loading="true" class="detail-loading-wrap"></div>
  <div class="product-detail-page" v-if="product">
    <div class="detail-main">
      <!-- 左：图片 -->
      <div class="detail-gallery">
        <div class="main-image" @click="openViewer" title="点击查看大图">
          <img :src="currentImage" :alt="product.name" @error="imageOnError($event, 400)" />
        </div>
        <div class="thumb-list" v-if="imageList.length > 1">
          <img
            v-for="(img, i) in imageList" :key="i"
            :src="img"
            :class="{ active: i === activeImageIdx }"
            @mouseenter="activeImageIdx = i"
            @error="onThumbError"
          />
        </div>
      </div>

      <!-- 中：商品信息 -->
      <div class="detail-info">
        <h1 class="product-title">{{ product.name }}</h1>
        <p class="product-subtitle" v-if="product.description && !isRichHtml">
          {{ product.description?.slice(0, 120) }}
        </p>

        <div class="price-box">
          <div class="price-row">
            <span class="price-label">价格</span>
            <span class="price-current">¥{{ formatPrice(currentPrice) }}</span>
            <span class="price-original" v-if="isSeckill">¥{{ formatPrice(product.price) }}</span>
          </div>
          <div class="price-tags" v-if="isSeckill">
            <el-tag type="danger" size="small">限时秒杀</el-tag>
            <span class="seckill-countdown" v-if="countdown">距结束 {{ countdown }}</span>
          </div>
        </div>

        <!-- SKU 选择器 -->
        <div class="sku-section" v-if="specTree?.length">
          <div v-for="spec in specTree" :key="spec.name" class="sku-row">
            <span class="sku-label">{{ spec.name }}</span>
            <div class="sku-values">
              <span
                v-for="val in spec.values" :key="val"
                class="sku-tag"
                :class="{ active: selectedSpecs[spec.name] === val, disabled: isSpecDisabled(spec.name, val) }"
                @click="selectSpec(spec.name, val)"
              >{{ val }}</span>
            </div>
          </div>
        </div>

        <div class="sku-hint" v-if="specTree?.length && !currentSkuInfo">请选择规格</div>

        <div class="stock-info" v-if="!specTree?.length || currentSkuInfo">
          <span>库存：{{ currentStock }} 件</span>
          <span class="sales-info">销量：{{ product.sales || 0 }}</span>
        </div>

        <div class="qty-row">
          <span class="sku-label">数量</span>
          <el-input-number v-model="quantity" :min="1" :max="currentStock" size="small" />
        </div>

        <div class="action-row">
          <el-button size="large" @click="addToCart" :disabled="!canBuy">加入购物车</el-button>
          <el-button size="large" type="primary" @click="buyNow" :disabled="!canBuy">立即购买</el-button>
          <el-button size="large" @click="toggleFav" :type="isFavorite ? 'warning' : 'default'">
            <el-icon><StarFilled v-if="isFavorite" /><Star v-else /></el-icon>
            {{ isFavorite ? '已收藏' : '收藏' }}
          </el-button>
        </div>

        <div class="shop-card" v-if="detail.shop">
          <div class="shop-inner">
            <img :src="detail.shop.logo" class="shop-logo" v-if="detail.shop.logo" />
            <span class="shop-name">{{ detail.shop.name }}</span>
            <el-button size="small" text type="primary" @click="$router.push(`/shop/${detail.shop.id}`)">进店逛逛 →</el-button>
          </div>
        </div>
      </div>

      <!-- 右：评价摘要 -->
      <div class="detail-sidebar">
        <div class="review-summary">
          <h4>用户评价</h4>
          <div class="review-score">{{ detail.reviewStats?.avgRating || 0 }}</div>
          <el-rate :model-value="Math.round((detail.reviewStats?.avgRating || 0) * 2) / 2" disabled allow-half />
          <p>{{ detail.reviewStats?.count || 0 }} 条评价</p>
          <p>好评率 {{ ((detail.reviewStats?.goodRate || 0) * 100).toFixed(1) }}%</p>
        </div>
      </div>
    </div>

    <!-- 全屏图片灯箱（点击主图打开） -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="imageList"
      :initial-index="activeImageIdx"
      hide-on-click-modal
      teleported
      @close="viewerVisible = false"
    />

    <!-- 详情 + 参数 -->
    <div class="detail-bottom">
      <el-tabs>
        <el-tab-pane label="商品详情">
          <div class="description-content" v-if="product.description">
            <div v-if="isRichHtml" v-html="product.description" />
            <p v-else style="white-space: pre-wrap">{{ product.description }}</p>
          </div>
          <el-empty v-else description="暂无详情" />
        </el-tab-pane>
        <el-tab-pane label="规格参数" v-if="detail.attributes?.length">
          <table class="attr-table">
            <tr v-for="attr in detail.attributes" :key="attr.id">
              <td class="attr-name">{{ attr.name }}</td>
              <td>{{ attr.value }}</td>
            </tr>
          </table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 商品评价 -->
    <div class="review-section">
      <div class="review-header">
        <h3>商品评价</h3>
        <div class="review-stats-summary" v-if="detail.reviewStats?.count">
          <span>共 {{ detail.reviewStats.count }} 条评价</span>
          <span class="review-good-rate">好评率 {{ ((detail.reviewStats.goodRate || 0) * 100).toFixed(1) }}%</span>
        </div>
      </div>

      <!-- 评价分组筛选 -->
      <div class="review-filter" v-if="detail.reviewStats?.count">
        <span
          v-for="f in reviewFilters"
          :key="f.value"
          :class="['rf-item', { active: reviewFilter === f.value }]"
          @click="switchReviewFilter(f.value)"
        >{{ f.label }}</span>
      </div>

      <div v-loading="reviewLoading" class="review-list-container">
        <div v-if="reviews.length === 0 && !reviewLoading" class="review-empty">
          <el-empty description="暂无评价" :image-size="80" />
        </div>
        <div v-for="r in reviews" :key="r.id" class="review-item">
          <div class="review-item-header">
            <span class="review-avatar">👤</span>
            <span class="review-username">{{ r.username }}</span>
            <el-rate :model-value="r.rating" disabled size="small" allow-half />
            <span class="review-time">{{ fmtTime(r.createTime, { dateOnly: true }) }}</span>
          </div>
          <div class="review-content" v-if="r.content">{{ r.content }}</div>
          <div class="review-images" v-if="reviewImages(r).length">
            <img v-for="(img, i) in reviewImages(r)" :key="i" :src="img" class="review-img" loading="lazy" decoding="async" />
          </div>
          <!-- 商家回复 -->
          <div class="review-reply" v-if="r.reply">
            <span class="rr-label">商家回复：</span>
            <span class="rr-content">{{ r.reply }}</span>
            <span class="rr-time" v-if="r.replyTime">{{ fmtTime(r.replyTime, { dateOnly: true }) }}</span>
          </div>
        </div>
        <div class="review-load-more" v-if="reviewTotal > reviews.length">
          <el-button text type="primary" @click="loadMoreReviews" :loading="reviewLoading">加载更多</el-button>
        </div>
      </div>
    </div>
  </div>

  <el-empty v-else-if="!loading" description="商品不存在" />
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { getProductDetail, getProductReviews, toggleFavorite } from '@/api'
import { addToCart as apiAddToCart } from '@/api'
import { useCartStore } from '@/stores/cart'
import { fmtTime } from '@/utils/date'
import { imageOnError } from '@/utils/image'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const product = ref(null)
const detail = reactive({ shop: null, reviewStats: {}, attributes: [] })
const specTree = ref(null)
const skuMatrix = ref(null)
const selectedSpecs = reactive({})
const quantity = ref(1)
const activeImageIdx = ref(0)
const viewerVisible = ref(false)
const loading = ref(false)
const isFavorite = ref(false)
const reviews = ref([])
const reviewLoading = ref(false)
const reviewPage = ref(1)
const reviewTotal = ref(0)
const reviewFilter = ref('all')
const reviewFilters = [
  { value: 'all', label: '全部' },
  { value: 'good', label: '好评' },
  { value: 'middle', label: '中评' },
  { value: 'bad', label: '差评' }
]

const imageList = computed(() => {
  if (!product.value?.images) return []
  try {
    return typeof product.value.images === 'string'
      ? JSON.parse(product.value.images)
      : product.value.images
  } catch { return [] }
})

const currentImage = computed(() => imageList.value[activeImageIdx.value] || imageList.value[0] || '')

const isRichHtml = computed(() => {
  const desc = product.value?.description
  return desc && /<\/?[a-z][\s\S]*>/i.test(desc)
})

const isSeckill = computed(() => {
  const p = product.value
  if (!p?.seckillPrice) return false
  const now = Date.now()
  const start = p.seckillStartTime ? new Date(p.seckillStartTime).getTime() : 0
  const end = p.seckillEndTime ? new Date(p.seckillEndTime).getTime() : 0
  return now >= start && now <= end && p.seckillStock > 0
})

const currentPrice = computed(() => {
  if (isSeckill.value) return product.value.seckillPrice
  if (currentSkuInfo.value?.price != null) return currentSkuInfo.value.price
  return product.value?.price
})

const currentSkuInfo = computed(() => {
  if (!specTree.value?.length || !skuMatrix.value) return null
  const key = specTree.value.map(s => selectedSpecs[s.name] || '').join(';')
  return skuMatrix.value[key] || null
})

const currentStock = computed(() => {
  if (currentSkuInfo.value) return currentSkuInfo.value.stock
  if (isSeckill.value) return product.value.seckillStock
  return product.value?.stock || 0
})

const canBuy = computed(() => {
  if (specTree.value?.length) return !!(currentSkuInfo.value && currentSkuInfo.value.stock > 0)
  return currentStock.value > 0
})

onMounted(() => {
  // 详情/评价并行；收藏状态由详情接口的 favorited 字段返回，无需单独请求
  loadDetail()
  loadReviews()
})
onUnmounted(stopCountdown)

// 秒杀窗口变化时启停倒计时
watch(isSeckill, (val) => {
  if (val) startCountdown()
  else { countdown.value = ''; stopCountdown() }
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getProductDetail(route.params.id)
    if (res.data) {
      product.value = res.data.product
      detail.shop = res.data.shop || null
      detail.reviewStats = res.data.reviewStats || {}
      detail.attributes = res.data.attributes || []
      specTree.value = res.data.specTree || null
      skuMatrix.value = res.data.skuMatrix || null
      preselectFirstSku()
      // 收藏状态直接取自详情接口（未登录/未收藏均为 false）
      isFavorite.value = !!res.data.favorited
    }
  } catch (e) {
    console.error('加载商品详情失败', e)
  } finally {
    loading.value = false
  }
}

function selectSpec(name, value) {
  if (isSpecDisabled(name, value)) return
  selectedSpecs[name] = value
}

function isSpecDisabled(name, value) {
  if (!specTree.value?.length || !skuMatrix.value) return false
  const testSpecs = { ...selectedSpecs, [name]: value }
  const dimensionNames = specTree.value.map(s => s.name)
  // 多规格：只要存在一个完整组合包含当前已选规格即可选（不能要求 key 完全命中，
  // 否则首次选择时其他维度为空，所有规格都被禁用）
  const hasMatch = Object.keys(skuMatrix.value).some(key => {
    const parts = key.split(';')
    return dimensionNames.every((dim, i) => {
      const selected = testSpecs[dim]
      if (selected == null || selected === '') return true // 未选维度不限制
      return parts[i] === selected
    })
  })
  return !hasMatch
}

async function addToCart() {
  if (specTree.value?.length && !currentSkuInfo.value) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  try {
    await apiAddToCart({
      productId: product.value.id,
      skuId: currentSkuInfo.value?.skuId || null,
      quantity: quantity.value
    })
    // 加购后端做 (productId, skuId) 去重合并，本地无法精确计算，强制重新拉取保证角标准确
    cartStore.refreshCount(true)
    ElMessage.success('已加入购物车')
  } catch { /* interceptor 处理错误 */ }
}

function buyNow() {
  if (specTree.value?.length && !currentSkuInfo.value) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  router.push({
    name: 'checkout',
    query: {
      mode: 'buy',
      productId: product.value.id,
      skuId: currentSkuInfo.value?.skuId || '',
      qty: quantity.value
    }
  })
}

async function toggleFav() {
  try {
    const res = await toggleFavorite(product.value.id)
    // 以后端返回的 favorited 为准（避免本地取反与服务端状态不同步）
    isFavorite.value = res.data?.favorited ?? !isFavorite.value
    ElMessage.success(isFavorite.value ? '已收藏' : '已取消收藏')
  } catch {
    // handle by interceptor
  }
}

async function loadReviews() {
  reviewLoading.value = true
  reviewPage.value = 1
  try {
    const res = await getProductReviews(route.params.id, { page: 1, size: 5, filter: reviewFilter.value })
    if (res.data) {
      reviews.value = res.data.records || res.data || []
      reviewTotal.value = res.data.total || 0
    }
  } catch {
    // 静默降级
  } finally {
    reviewLoading.value = false
  }
}

function switchReviewFilter(value) {
  if (reviewFilter.value === value) return
  reviewFilter.value = value
  loadReviews()
}

function reviewImages(r) {
  if (!r || !r.images) return []
  try {
    const arr = typeof r.images === 'string' ? JSON.parse(r.images) : r.images
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
}

async function loadMoreReviews() {
  reviewLoading.value = true
  try {
    reviewPage.value++
    const res = await getProductReviews(route.params.id, { page: reviewPage.value, size: 5, filter: reviewFilter.value })
    if (res.data) {
      const list = res.data.records || res.data || []
      reviews.value = [...reviews.value, ...list]
      reviewTotal.value = res.data.total || 0
    }
  } catch {
    reviewPage.value--
  } finally {
    reviewLoading.value = false
  }
}

function formatPrice(n) {
  if (n == null || n === '') return '0.00'
  const num = Number(n)
  return isNaN(num) ? '0.00' : num.toFixed(2)
}

function openViewer() {
  viewerVisible.value = true
}

// 兼容 ISO 字符串与 LocalDateTime 数组两种返回格式
function parseDate(t) {
  if (!t) return 0
  let str = t
  if (Array.isArray(t)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = t
    str = `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}T${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  const ts = new Date(str).getTime()
  return isNaN(ts) ? 0 : ts
}

const countdown = ref('')
let countdownTimer = null

function updateCountdown() {
  const remain = parseDate(product.value?.seckillEndTime) - Date.now()
  if (remain <= 0) {
    countdown.value = '00:00:00'
    stopCountdown()
    return
  }
  const h = Math.floor(remain / 3600000)
  const m = Math.floor((remain % 3600000) / 60000)
  const s = Math.floor((remain % 60000) / 1000)
  countdown.value = [h, m, s].map(n => String(n).padStart(2, '0')).join(':')
}

function startCountdown() {
  stopCountdown()
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

// 预选首个可购 SKU：遍历 skuMatrix，用可购组合的 specs 填充 selectedSpecs，
// currentSkuInfo（computed）随之联动回填价格/库存/数量上限
function preselectFirstSku() {
  if (!specTree.value?.length || !skuMatrix.value) return
  const names = specTree.value.map(s => s.name)
  for (const [key, sku] of Object.entries(skuMatrix.value)) {
    if (sku && sku.stock > 0) {
      const parts = key.split(';')
      names.forEach((name, i) => {
        selectedSpecs[name] = parts[i] || ''
      })
      return
    }
  }
}

function onThumbError(e) {
  e.target.style.display = 'none'
}
</script>

<style scoped>
.product-detail-page { max-width: 1200px; margin: 0 auto; }
.detail-main { display: flex; gap: 24px; background: #fff; border-radius: 8px; padding: 24px; margin-bottom: 16px; }

.detail-gallery { width: 400px; flex-shrink: 0; }
.main-image { width: 400px; height: 400px; overflow: hidden; border-radius: 4px; border: 1px solid #f0f0f0; background: #fafafa; cursor: zoom-in; }
.main-image img { width: 100%; height: 100%; object-fit: contain; }
.thumb-list { display: flex; gap: 8px; margin-top: 8px; }
.thumb-list img { width: 64px; height: 64px; object-fit: cover; border-radius: 4px; border: 2px solid transparent; cursor: pointer; }
.thumb-list img.active { border-color: #ff5000; }

.detail-info { flex: 1; }
.product-title { font-size: 20px; font-weight: 600; margin-bottom: 6px; }
.product-subtitle { font-size: 13px; color: #999; margin-bottom: 12px; }

.price-box { background: #fff5f0; padding: 16px; border-radius: 8px; margin-bottom: 16px; }
.price-row { display: flex; align-items: baseline; gap: 12px; }
.price-label { font-size: 13px; color: #999; }
.price-current { color: #ff5000; font-size: 28px; font-weight: 700; }
.price-original { color: #999; font-size: 14px; text-decoration: line-through; }
.price-tags { margin-top: 8px; }

.sku-section { margin-bottom: 8px; }
.sku-row { display: flex; align-items: flex-start; margin-bottom: 10px; }
.sku-label { width: 50px; font-size: 13px; color: #999; line-height: 28px; flex-shrink: 0; }
.sku-values { display: flex; flex-wrap: wrap; gap: 8px; }
.sku-tag { padding: 4px 16px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; cursor: pointer; user-select: none; }
.sku-tag:hover { border-color: #ff5000; color: #ff5000; }
.sku-tag.active { border-color: #ff5000; background: #fff5f0; color: #ff5000; font-weight: 600; }
.sku-tag.disabled { color: #ccc; border-color: #eee; cursor: not-allowed; }

.stock-info { font-size: 13px; color: #666; margin-bottom: 12px; display: flex; gap: 20px; }
.sales-info { color: #999; }

.qty-row { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }

.action-row { display: flex; gap: 12px; margin-bottom: 20px; }
.action-row .el-button--large { width: 160px; }
.action-row .el-button--primary { background: #ff5000; border-color: #ff5000; }

.shop-card { border: 1px solid #f0f0f0; border-radius: 8px; padding: 12px; }
.shop-inner { display: flex; align-items: center; gap: 8px; }
.shop-logo { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.shop-name { font-size: 14px; font-weight: 500; flex: 1; }

.detail-sidebar { width: 180px; flex-shrink: 0; }
.review-summary { text-align: center; padding: 16px; border: 1px solid #f0f0f0; border-radius: 8px; }
.review-summary h4 { font-size: 14px; margin-bottom: 8px; }
.review-score { font-size: 32px; color: #ff5000; font-weight: 700; }
.review-summary p { font-size: 12px; color: #999; margin-top: 8px; }

.detail-bottom { background: #fff; border-radius: 8px; padding: 24px; min-height: 400px; }
.description-content { max-width: 800px; }
.description-content :deep(img) { max-width: 100%; }

.attr-table { width: 100%; border-collapse: collapse; }
.attr-table td { padding: 8px 12px; border-bottom: 1px solid #f0f0f0; font-size: 13px; }
.attr-name { width: 120px; color: #999; background: #fafafa; }

/* 评价区 */
.review-section { background: #fff; border-radius: 8px; padding: 24px; margin-top: 16px; }
.review-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.review-header h3 { font-size: 16px; font-weight: 600; }
.review-stats-summary { font-size: 13px; color: #666; display: flex; gap: 16px; }
.review-good-rate { color: #ff5000; }
.review-list-container { min-height: 100px; }
.review-empty { padding: 20px 0; }
.review-item { padding: 16px 0; border-bottom: 1px solid #f5f5f5; }
.review-item:last-child { border-bottom: none; }
.review-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.review-avatar { font-size: 20px; }
.review-username { font-size: 13px; color: #666; }
.review-time { font-size: 12px; color: #999; margin-left: auto; }
.review-content { font-size: 14px; line-height: 1.6; color: #333; }
.review-images { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.review-img { width: 80px; height: 80px; object-fit: cover; border-radius: 4px; border: 1px solid #f0f0f0; }
.review-load-more { text-align: center; padding: 16px 0; }
.review-filter { display: flex; gap: 8px; margin-bottom: 12px; }
.rf-item { font-size: 13px; color: #666; padding: 4px 14px; border-radius: 14px; border: 1px solid #e0e0e0; cursor: pointer; transition: all .2s; }
.rf-item:hover { color: #ff5000; border-color: #ff5000; }
.rf-item.active { color: #fff; background: #ff5000; border-color: #ff5000; }
.review-reply { margin-top: 10px; background: #f7f8fa; border-radius: 6px; padding: 10px 12px; font-size: 13px; line-height: 1.6; }
.rr-label { color: #ff5000; font-weight: 500; }
.rr-content { color: #333; }
.rr-time { color: #999; font-size: 12px; margin-left: 8px; }

/* 批次 C：SKU 提示条 + 秒杀倒计时 + 响应式 */
.sku-hint { font-size: 13px; color: #ff5000; background: #fff5f0; border: 1px solid #ffd8c8; border-radius: 4px; padding: 8px 12px; margin-bottom: 12px; }
.seckill-countdown { color: #ff5000; font-size: 13px; margin-left: 10px; }

@media (max-width: 992px) {
  .detail-main { flex-direction: column; }
  .detail-gallery { width: 100%; flex-shrink: 0; }
  .main-image { width: 100%; height: auto; aspect-ratio: 1 / 1; }
  .action-row .el-button { flex: 1; width: auto; }
}
</style>
