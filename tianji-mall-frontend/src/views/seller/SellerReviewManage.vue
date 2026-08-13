<template>
  <div class="seller-page">
    <h2>商品评价</h2>

    <el-table :data="reviews" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="productId" label="商品ID" width="80" />
      <el-table-column label="评分" width="140">
        <template #default="{ row }">
          <el-rate :model-value="row.rating" disabled size="small" />
        </template>
      </el-table-column>
      <el-table-column label="评价内容" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.content || '—' }}</template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="110" show-overflow-tooltip />
      <el-table-column label="时间" width="100">
        <template #default="{ row }">{{ fmtTime(row.createTime, { dateOnly: true }) }}</template>
      </el-table-column>
      <el-table-column label="回复" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.reply" type="success" size="small">已回复</el-tag>
          <el-tag v-else type="info" size="small">未回复</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.reply" size="small" text type="primary" @click="openReplyDialog(row)">回复</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
        background
      />
    </div>

    <!-- 回复 Dialog -->
    <el-dialog v-model="replyVisible" title="回复评价" width="460px">
      <div class="reply-origin" v-if="currentReview">
        <el-rate :model-value="currentReview.rating" disabled size="small" />
        <p class="reply-origin-content">{{ currentReview.content }}</p>
      </div>
      <el-input
        v-model="replyForm.reply"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请输入回复内容"
      />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" @click="handleReply" :loading="saving">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSellerReviews, replyReview } from '@/api'
import { fmtTime } from '@/utils/date'

const reviews = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const query = reactive({ page: 1, size: 20 })

const replyVisible = ref(false)
const currentReview = ref(null)
const replyForm = reactive({ reply: '' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getSellerReviews({ page: query.page, size: query.size })
    if (res.data) {
      reviews.value = res.data.records || res.data || []
      total.value = res.data.total || 0
    }
  } catch { /* handle by interceptor */ }
  finally { loading.value = false }
}

function openReplyDialog(row) {
  currentReview.value = row
  replyForm.reply = ''
  replyVisible.value = true
}

async function handleReply() {
  if (!replyForm.reply.trim()) { ElMessage.warning('请输入回复内容'); return }
  saving.value = true
  try {
    await replyReview(currentReview.value.id, { reply: replyForm.reply.trim() })
    ElMessage.success('回复成功')
    replyVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}
</script>

<style scoped>
.seller-page h2 { font-size: 18px; font-weight: 600; margin-bottom: 16px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.reply-origin { background: #232327; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.reply-origin-content { font-size: 13px; color: #a1a1aa; margin-top: 8px; line-height: 1.6; }
</style>
