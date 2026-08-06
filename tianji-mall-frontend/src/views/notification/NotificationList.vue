<template>
  <div class="notification-page">
    <div class="np-header">
      <h2 class="page-title">消息通知</h2>
      <el-button type="primary" size="small" @click="handleMarkAllRead" :disabled="!hasUnread">全部已读</el-button>
    </div>

    <!-- 通知列表 -->
    <div class="notification-list" v-if="notifications.length">
      <div
        class="notification-card"
        v-for="n in notifications" :key="n.id"
        :class="{ 'is-read': n.isRead }"
        @click="handleClick(n)"
      >
        <div class="nc-icon">{{ typeIcon(n.type) }}</div>
        <div class="nc-body">
          <div class="nc-title">
            {{ n.title }}
            <el-tag v-if="n.isRead" size="small" type="info">已读</el-tag>
          </div>
          <div class="nc-content">{{ n.content }}</div>
          <div class="nc-time">{{ fmtTime(n.createTime) }}</div>
        </div>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="暂无通知" />

    <!-- 分页 -->
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
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotifications, markRead, markAllRead, getUnreadCount } from '@/api'

const notifications = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const typeIcons = { CREATED: '🛒', SHIPPED: '📦', COMPLETED: '✅' }

function typeIcon(t) { return typeIcons[t] || '📌' }

// 用后端全局未读数判断（避免分页后"全部已读"按钮失真）
const globalUnread = ref(0)
const hasUnread = computed(() => globalUnread.value > 0)

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    globalUnread.value = res.data != null ? Number(res.data) : 0
  } catch { /* ignore */ }
}

onMounted(() => { loadData(); loadUnread() })

async function loadData() {
  loading.value = true
  try {
    const res = await getNotifications({ page: currentPage.value, size: pageSize.value })
    if (res.data) {
      notifications.value = res.data.records || res.data || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载通知列表失败', e)
  } finally {
    loading.value = false
  }
}

async function handleClick(n) {
  if (n.isRead) return
  try {
    await markRead(n.id)
    n.isRead = true
  } catch { /* ignore */ }
}

async function handleMarkAllRead() {
  try {
    await markAllRead()
    notifications.value.forEach(n => { n.isRead = true })
    globalUnread.value = 0
    ElMessage.success('全部已读')
  } catch { /* ignore */ }
}

function fmtTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) t = t[0] + 'T' + t[1]
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.notification-page { max-width: 800px; margin: 0 auto; }
.np-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; margin: 0; }

.notification-list { display: flex; flex-direction: column; gap: 10px; }
.notification-card { background: #fff; border-radius: 8px; padding: 16px 20px; display: flex; gap: 14px; cursor: pointer; transition: box-shadow .2s; }
.notification-card:not(.is-read):hover { box-shadow: 0 2px 12px rgba(0,0,0,.06); background: #fafafa; }
.notification-card.is-read { background: #f7f7f7; opacity: .7; cursor: default; }

.nc-icon { font-size: 28px; flex-shrink: 0; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; }

.nc-body { flex: 1; min-width: 0; }
.nc-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }
.nc-content { font-size: 13px; color: #666; line-height: 1.5; margin-bottom: 6px; }
.nc-time { font-size: 12px; color: #999; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
