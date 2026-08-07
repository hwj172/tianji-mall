<template>
  <div class="reviews-page" v-loading="loading">
    <h2>我的评价</h2>

    <div class="review-list" v-if="reviews.length">
      <div class="review-card" v-for="r in reviews" :key="r.id">
        <div class="rc-header">
          <span class="rc-product" @click="$router.push(`/product/${r.productId}`)">商品 #{{ r.productId }}</span>
          <el-rate :model-value="r.rating" disabled show-score size="small" />
        </div>
        <div class="rc-content" v-if="r.content">{{ r.content }}</div>
        <div class="rc-time">{{ fmtTime(r.createTime) }}</div>
      </div>
      <div class="load-more" v-if="hasMore">
        <el-button text type="primary" @click="loadMore" :loading="loadingMore">加载更多</el-button>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无评价" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyReviews } from '@/api'
import { fmtTime } from '@/utils/date'

const reviews = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const reviewPage = ref(1)
const pageSize = 20
const hasMore = ref(true)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  reviewPage.value = 1
  hasMore.value = true
  try {
    const res = await getMyReviews({ page: 1, size: pageSize })
    reviews.value = res.data || []
    hasMore.value = reviews.value.length >= pageSize
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadMore() {
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    reviewPage.value++
    const res = await getMyReviews({ page: reviewPage.value, size: pageSize })
    const list = res.data || []
    reviews.value = [...reviews.value, ...list]
    hasMore.value = list.length >= pageSize
  } catch { reviewPage.value-- }
  finally { loadingMore.value = false }
}

</script>

<style scoped>
.reviews-page { max-width: 800px; margin: 0 auto; }
.reviews-page h2 { font-size: 20px; font-weight: 600; margin-bottom: 16px; }

.review-card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 10px; }
.rc-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rc-product { font-size: 14px; cursor: pointer; color: #333; }
.rc-product:hover { color: #ff5000; }
.rc-content { font-size: 14px; color: #333; line-height: 1.6; margin-bottom: 8px; }
.rc-time { font-size: 12px; color: #999; }
</style>
