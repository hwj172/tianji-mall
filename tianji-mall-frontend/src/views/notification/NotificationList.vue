<template>
  <div class="notification-page" v-loading="loading">
    <div class="np-header">
      <h2 class="page-title">消息通知</h2>
      <el-button type="primary" size="small" @click="handleMarkAllRead" :disabled="!hasUnread">全部已读</el-button>
    </div>

    <!-- 分类筛选 -->
    <div class="np-tabs">
      <span
        v-for="tab in tabs" :key="tab.value"
        :class="['np-tab', { active: currentType === tab.value }]"
        @click="switchTab(tab.value)"
      >{{ tab.label }}</span>
    </div>

    <!-- 通知列表 -->
    <div class="notification-list" v-if="notifications.length">
      <div
        class="notification-card"
        v-for="n in notifications" :key="n.id"
        :class="{ 'is-read': n.isRead }"
        @click="handleClick(n)"
      >
        <div class="nc-icon"><el-icon><component :is="typeIcon(n.type)" /></el-icon></div>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getNotifications, markRead, markAllRead, getUnreadCount } from '@/api'
import { fmtTime } from '@/utils/date'
import { ShoppingCart, Box, CircleCheck, Bell } from '@element-plus/icons-vue'

const router = useRouter()

const notifications = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const currentType = ref('all')

const tabs = [
  { label: '全部', value: 'all' },
  { label: '订单通知', value: 'order' },
  { label: '系统公告', value: 'system' }
]

// 图标 key 与后端 type 保持一致（ORDER_CREATED / ORDER_SHIPPED / ORDER_COMPLETED / SYSTEM_ANNOUNCEMENT）
const typeIcons = {
  ORDER_CREATED: ShoppingCart, ORDER_SHIPPED: Box, ORDER_COMPLETED: CircleCheck, SYSTEM_ANNOUNCEMENT: Bell
}

function typeIcon(t) { return typeIcons[t] || Bell }

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
    const res = await getNotifications({ page: currentPage.value, size: pageSize.value, type: currentType.value })
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

function switchTab(value) {
  if (currentType.value === value) return
  currentType.value = value
  currentPage.value = 1
  loadData()
}

async function handleClick(n) {
  // 未读先标已读
  if (!n.isRead) {
    try {
      await markRead(n.id)
      n.isRead = true
    } catch { /* ignore */ }
  }
  // 关联订单则跳转到订单详情
  if (n.relatedOrderId) {
    router.push({ name: 'orderDetail', params: { id: n.relatedOrderId } })
  }
}

async function handleMarkAllRead() {
  try {
    await markAllRead()
    notifications.value.forEach(n => { n.isRead = true })
    globalUnread.value = 0
    ElMessage.success('全部已读')
  } catch { /* ignore */ }
}

</script>

<style scoped>
.notification-page { max-width: 800px; margin: 0 auto; }
.np-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-title { font-size: 20px; font-weight: 600; margin: 0; }
.np-tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.np-tab { font-size: 13px; color: #8b96ab; padding: 5px 16px; border-radius: 16px; border: 1px solid rgba(255,255,255,.08); cursor: pointer; transition: all .2s; }
.np-tab:hover { color: #ff5000; border-color: #ff5000; }
.np-tab.active { color: #fff; background: #ff5000; border-color: #ff5000; }

.notification-list { display: flex; flex-direction: column; gap: 10px; }
.notification-card { background: #121826; border-radius: 8px; padding: 16px 20px; display: flex; gap: 14px; cursor: pointer; transition: box-shadow .2s; }
.notification-card:not(.is-read):hover { box-shadow: 0 2px 12px rgba(0,0,0,.06); background: #1a2233; }
.notification-card.is-read { background: #f7f7f7; opacity: .7; cursor: default; }

.nc-icon { font-size: 28px; flex-shrink: 0; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; }

.nc-body { flex: 1; min-width: 0; }
.nc-title { font-size: 15px; font-weight: 600; color: #c3cbda; margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }
.nc-content { font-size: 13px; color: #8b96ab; line-height: 1.5; margin-bottom: 6px; }
.nc-time { font-size: 12px; color: #5c6a82; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
