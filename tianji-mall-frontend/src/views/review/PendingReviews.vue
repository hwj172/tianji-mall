<template>
  <div class="reviews-page">
    <h2>待评价商品</h2>

    <div class="review-list" v-if="items.length">
      <div class="review-card" v-for="item in items" :key="item.orderId + '_' + item.productId">
        <div class="rc-img" @click="$router.push(`/product/${item.productId}`)">
          <el-image :src="item.productImage" style="width:80px;height:80px" @error="onImgError" />
        </div>
        <div class="rc-info">
          <router-link :to="`/product/${item.productId}`" class="rc-name">{{ item.productName }}</router-link>
          <span class="rc-spec" v-if="item.skuSpecs">{{ item.skuSpecs }}</span>
          <span class="rc-price">¥{{ item.price }}</span>
        </div>
        <div class="rc-action">
          <el-button type="primary" size="small" @click="openReview(item)">评价</el-button>
        </div>
      </div>
      <div class="load-more" v-if="hasMore">
        <el-button text type="primary" @click="loadMore" :loading="loadingMore">加载更多</el-button>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无待评价商品" />

    <!-- 评价 Dialog -->
    <el-dialog v-model="dialogVisible" title="发表评价" width="460px">
      <el-form :model="reviewForm" label-width="60px">
        <el-form-item label="评分">
          <el-rate v-model="reviewForm.rating" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="reviewForm.content" type="textarea" :rows="4" placeholder="分享你的使用体验..." />
        </el-form-item>
        <el-form-item label="晒图">
          <el-upload
            action="#" :http-request="handleUpload" list-type="picture-card"
            :limit="4" multiple accept="image/*"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPendingReviews, createReview, uploadImage } from '@/api'

const items = ref([])
const loading = ref(false)
const submitting = ref(false)
const loadingMore = ref(false)
const reviewPage = ref(1)
const pageSize = 20
const hasMore = ref(true)
const dialogVisible = ref(false)
const reviewTarget = ref(null)
const reviewForm = reactive({ rating: 5, content: '' })
const uploadedImages = ref([])

// el-upload 自定义上传：调 /api/upload/image，成功 URL 收集到 uploadedImages
async function handleUpload(options) {
  try {
    const fd = new FormData()
    fd.append('files', options.file)
    const res = await uploadImage(fd)
    const urls = res.data || []
    if (urls.length) uploadedImages.value.push(urls[0])
    options.onSuccess && options.onSuccess(res)
  } catch (e) {
    options.onError && options.onError(e)
    ElMessage.error('图片上传失败')
  }
}

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  reviewPage.value = 1
  hasMore.value = true
  try {
    const res = await getPendingReviews({ page: 1, size: pageSize })
    items.value = res.data || []
    hasMore.value = items.value.length >= pageSize
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadMore() {
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    reviewPage.value++
    const res = await getPendingReviews({ page: reviewPage.value, size: pageSize })
    const list = res.data || []
    items.value = [...items.value, ...list]
    hasMore.value = list.length >= pageSize
  } catch { reviewPage.value-- }
  finally { loadingMore.value = false }
}

function openReview(item) {
  reviewTarget.value = item
  reviewForm.rating = 5
  reviewForm.content = ''
  uploadedImages.value = []
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!reviewForm.rating) { ElMessage.warning('请评分'); return }
  submitting.value = true
  try {
    await createReview({
      orderId: reviewTarget.value.orderId,
      productId: reviewTarget.value.productId,
      rating: reviewForm.rating,
      content: reviewForm.content,
      images: uploadedImages.value.length ? JSON.stringify(uploadedImages.value) : undefined
    })
    ElMessage.success('评价成功')
    dialogVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { submitting.value = false }
}

function onImgError(e) {
  e.target.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 80"><rect fill="%23f5f5f5" width="80" height="80"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="10">无图</text></svg>'
}
</script>

<style scoped>
.reviews-page { max-width: 800px; margin: 0 auto; }
.reviews-page h2 { font-size: 20px; font-weight: 600; margin-bottom: 16px; }

.review-card { background: #fff; border-radius: 8px; padding: 16px; display: flex; align-items: center; gap: 14px; margin-bottom: 10px; }
.rc-img { cursor: pointer; border-radius: 4px; overflow: hidden; background: #fafafa; flex-shrink: 0; }
.rc-info { flex: 1; min-width: 0; }
.rc-name { font-size: 14px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rc-name:hover { color: #ff5000; }
.rc-spec { font-size: 12px; color: #999; display: block; }
.rc-price { font-size: 14px; font-weight: 600; color: #ff5000; }
.rc-action { margin-left: auto; }
</style>
