<template>
  <div class="admin-dashboard" v-loading="loading">
    <h2 class="page-title">数据看板</h2>

    <!-- 概览卡片 -->
    <div class="overview-cards">
      <div class="ov-card">
        <div class="ov-label">总 GMV</div>
        <div ref="gmvEl" class="ov-value ov-gmv">¥0</div>
      </div>
      <div class="ov-card">
        <div class="ov-label">总订单数</div>
        <div ref="ordersEl" class="ov-value">0</div>
      </div>
      <div class="ov-card">
        <div class="ov-label">总用户数</div>
        <div ref="usersEl" class="ov-value">0</div>
      </div>
    </div>

    <div class="charts-row">
      <!-- GMV 趋势（今日/本周/本月） -->
      <div class="chart-box">
        <h3>GMV 趋势</h3>
        <BaseChart :option="trendOption" height="300px" />
      </div>

      <!-- 订单状态分布 -->
      <div class="chart-box">
        <h3>订单状态分布</h3>
        <BaseChart v-if="(dashboard.orderStatusDist || []).length" :option="statusOption" height="300px" />
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>
    </div>

    <div class="charts-row">
      <!-- 分类销售额 -->
      <div class="chart-box wide" v-if="(dashboard.categorySales || []).length">
        <h3>分类销售额</h3>
        <BaseChart :option="categoryOption" :height="categoryHeight" />
      </div>

      <!-- 热销商品 Top 10 -->
      <div class="chart-box">
        <h3>热销商品 Top 10</h3>
        <el-table :data="dashboard.topProducts || []" size="small" max-height="360">
          <el-table-column type="index" width="50" label="#" />
          <el-table-column prop="name" label="商品" show-overflow-tooltip />
          <el-table-column prop="sales" label="销量" width="80" />
          <el-table-column label="销售额" width="110">
            <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { getAdminDashboard } from '@/api'
import BaseChart from '@/components/charts/BaseChart.vue'
import { countUp } from '@/composables/useCountUp'

const dashboard = ref({})
const loading = ref(false)
const gmvEl = ref(null)
const ordersEl = ref(null)
const usersEl = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getAdminDashboard()
    if (res.data) dashboard.value = res.data
  } catch (e) {
    console.error('加载看板数据失败', e)
  } finally {
    loading.value = false
  }
  // 概览卡数字滚动
  await nextTick()
  countUp(gmvEl.value, dashboard.value.totalGmv, { prefix: '¥' })
  countUp(ordersEl.value, dashboard.value.totalOrders)
  countUp(usersEl.value, dashboard.value.totalUsers)
})

function fmt(v) {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'number') return v.toLocaleString()
  return v
}

// ECharts 深色主题公共色
const textColor = '#8b96ab'
const axisLine = 'rgba(255,255,255,.08)'

// GMV 趋势（今日/本周/本月）
const trendOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 60, right: 20, top: 20, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['今日', '本周', '本月'],
    axisLabel: { color: textColor },
    axisLine: { lineStyle: { color: axisLine } }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: textColor },
    splitLine: { lineStyle: { color: axisLine } }
  },
  series: [{
    name: 'GMV',
    type: 'bar',
    barWidth: 32,
    data: [
      dashboard.value.today?.gmv ?? 0,
      dashboard.value.thisWeek?.gmv ?? 0,
      dashboard.value.thisMonth?.gmv ?? 0
    ],
    itemStyle: {
      borderRadius: [6, 6, 0, 0],
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: '#ff7a3d' },
          { offset: 1, color: '#ff5000' }
        ]
      }
    }
  }]
}))

// 订单状态分布环形图
const statusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, textStyle: { color: textColor }, itemWidth: 12, itemHeight: 12 },
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    center: ['50%', '44%'],
    itemStyle: { borderRadius: 6, borderColor: '#121826', borderWidth: 2 },
    label: { show: false },
    data: (dashboard.value.orderStatusDist || []).map(d => ({ name: d.label, value: d.count })),
    color: ['#ff5000', '#ff7a3d', '#22d3ee', '#34d399', '#64748b']
  }]
}))

// 分类销售额横向柱状图
const categoryOption = computed(() => {
  const sales = dashboard.value.categorySales || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 60, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      axisLabel: { color: textColor },
      splitLine: { lineStyle: { color: axisLine } }
    },
    yAxis: {
      type: 'category',
      data: sales.map(c => c.categoryName),
      axisLabel: { color: '#c3cbda' },
      axisLine: { lineStyle: { color: axisLine } }
    },
    series: [{
      type: 'bar',
      barWidth: 12,
      data: sales.map(c => c.amount),
      itemStyle: { color: '#22d3ee', borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right', color: textColor, formatter: '¥{c}' }
    }]
  }
})

const categoryHeight = computed(() => {
  const n = (dashboard.value.categorySales || []).length
  return Math.max(180, n * 36) + 'px'
})
</script>

<style scoped>
.admin-dashboard h2 { margin-bottom: 20px; }

.overview-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.ov-card { background: #121826; border-radius: 8px; padding: 24px; text-align: center; border: 1px solid rgba(255,255,255,.07); }
.ov-label { font-size: 14px; color: #5c6a82; margin-bottom: 8px; }
.ov-value { font-size: 28px; font-weight: 700; color: #c3cbda; font-family: var(--font-tech); }
.ov-gmv { color: #ff5000; text-shadow: 0 0 14px rgba(255, 80, 0, .35); }

.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.chart-box { background: #121826; border-radius: 8px; padding: 20px; border: 1px solid rgba(255,255,255,.07); }
.chart-box h3 { font-size: 15px; margin-bottom: 14px; color: #e6eaf2; }
.chart-box.wide { grid-column: 1 / -1; }
</style>
