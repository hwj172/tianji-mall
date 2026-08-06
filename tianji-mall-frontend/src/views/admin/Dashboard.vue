<template>
  <div class="admin-dashboard">
    <h2 class="page-title">数据看板</h2>

    <!-- 概览卡片 -->
    <div class="overview-cards">
      <div class="ov-card">
        <div class="ov-label">总 GMV</div>
        <div class="ov-value">¥{{ fmt(dashboard.totalGmv) }}</div>
      </div>
      <div class="ov-card">
        <div class="ov-label">总订单数</div>
        <div class="ov-value">{{ fmt(dashboard.totalOrders) }}</div>
      </div>
      <div class="ov-card">
        <div class="ov-label">总用户数</div>
        <div class="ov-value">{{ fmt(dashboard.totalUsers) }}</div>
      </div>
    </div>

    <!-- 时间段统计 -->
    <div class="stats-row">
      <div class="stat-box">
        <h4>今日</h4>
        <p>GMV：¥{{ fmt(dashboard.today?.gmv) }}</p>
        <p>订单：{{ fmt(dashboard.today?.orders) }}</p>
      </div>
      <div class="stat-box">
        <h4>本周</h4>
        <p>GMV：¥{{ fmt(dashboard.thisWeek?.gmv) }}</p>
        <p>订单：{{ fmt(dashboard.thisWeek?.orders) }}</p>
      </div>
      <div class="stat-box">
        <h4>本月</h4>
        <p>GMV：¥{{ fmt(dashboard.thisMonth?.gmv) }}</p>
        <p>订单：{{ fmt(dashboard.thisMonth?.orders) }}</p>
      </div>
    </div>

    <div class="charts-row">
      <!-- Top 10 热销商品 -->
      <div class="chart-box">
        <h3>🔥 热销商品 Top 10</h3>
        <el-table :data="dashboard.topProducts || []" size="small" max-height="400">
          <el-table-column type="index" width="50" label="#" />
          <el-table-column prop="name" label="商品" show-overflow-tooltip />
          <el-table-column prop="sales" label="销量" width="80" />
          <el-table-column label="销售额" width="100">
            <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 订单状态分布 -->
      <div class="chart-box">
        <h3>📊 订单状态分布</h3>
        <div class="status-dist" v-if="(dashboard.orderStatusDist || []).length">
          <div class="sd-item" v-for="d in dashboard.orderStatusDist" :key="d.status">
            <span class="sd-label">{{ d.label }}</span>
            <span class="sd-count">{{ d.count }}</span>
            <el-progress
              :percentage="statusPercent(d.count)"
              :stroke-width="8"
              :show-text="false"
              :color="statusColor(d.status)"
            />
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>

    <!-- 分类销售额 -->
    <div class="chart-box wide" v-if="(dashboard.categorySales || []).length">
      <h3>📂 分类销售额</h3>
      <div class="category-sales">
        <div class="cs-item" v-for="c in dashboard.categorySales" :key="c.categoryId">
          <span class="cs-name">{{ c.categoryName }}</span>
          <span class="cs-amount">¥{{ fmt(c.amount) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAdminDashboard } from '@/api'

const dashboard = ref({})

onMounted(async () => {
  try {
    const res = await getAdminDashboard()
    if (res.data) dashboard.value = res.data
  } catch (e) {
    console.error('加载看板数据失败', e)
  }
})

function fmt(v) {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'number') return v.toLocaleString()
  return v
}

function statusPercent(count) {
  const total = (dashboard.value.orderStatusDist || []).reduce((s, d) => s + (d.count || 0), 0)
  return total ? Math.round((count / total) * 100) : 0
}

function statusColor(s) {
  const m = { 1: '#e6a23c', 2: '#409eff', 3: '#909399', 4: '#67c23a', 5: '#f56c6c' }
  return m[s] || '#409eff'
}
</script>

<style scoped>
.admin-dashboard h2 { margin-bottom: 20px; }

.overview-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.ov-card { background: #fff; border-radius: 8px; padding: 24px; text-align: center; }
.ov-label { font-size: 14px; color: #999; margin-bottom: 8px; }
.ov-value { font-size: 28px; font-weight: 700; color: #333; }

.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-box { background: #fff; border-radius: 8px; padding: 20px; }
.stat-box h4 { font-size: 15px; margin-bottom: 10px; color: #666; }
.stat-box p { font-size: 14px; color: #333; margin-bottom: 4px; }

.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-box { background: #fff; border-radius: 8px; padding: 20px; }
.chart-box h3 { font-size: 15px; margin-bottom: 14px; }
.chart-box.wide { grid-column: 1 / -1; }

.status-dist { display: flex; flex-direction: column; gap: 14px; }
.sd-item { display: flex; align-items: center; gap: 10px; }
.sd-label { width: 70px; font-size: 13px; color: #666; }
.sd-count { width: 40px; font-size: 14px; font-weight: 600; text-align: right; }
.sd-item :deep(.el-progress) { flex: 1; }

.category-sales { display: flex; flex-wrap: wrap; gap: 12px; }
.cs-item { background: #f5f7fa; border-radius: 6px; padding: 10px 16px; display: flex; gap: 12px; align-items: center; }
.cs-name { font-size: 14px; color: #333; }
.cs-amount { font-size: 15px; font-weight: 600; color: #ff5000; }
</style>
