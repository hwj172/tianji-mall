<template>
  <div class="seckill-page">
    <div class="sk-banner">
      <div class="sk-banner-left">
        <h2><el-icon class="sk-banner-icon"><Timer /></el-icon> 限时秒杀</h2>
        <span class="sk-sub">超低价限时抢购 · 手慢无</span>
      </div>
      <span class="sk-now" v-if="products.length">当前 {{ fmtClock(now) }}</span>
    </div>

    <ProductGrid
      :products="products"
      :loading="loading"
      :cols="5"
      :total="total"
      :page-size="pageSize"
      empty-text="暂无秒杀活动"
    >
      <template #card="{ product }">
        <div class="sk-card">
          <ProductCard :product="enrich(product)" showOriginalPrice />
          <div class="sk-state" :class="'sk-' + seckillState(product)" v-if="seckillState(product) !== 'unknown'">
            <template v-if="seckillState(product) === 'active'">
              <span class="sk-tag sk-tag-active">抢购中</span>
              <span class="sk-cd">距结束 {{ remainingText(product) }}</span>
            </template>
            <span v-else-if="seckillState(product) === 'pending'" class="sk-tag sk-tag-gray">未开始</span>
            <span v-else class="sk-tag sk-tag-gray">已结束</span>
          </div>
        </div>
      </template>
      <template #pagination>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
          background
        />
      </template>
    </ProductGrid>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSeckillList } from '@/api'
import ProductCard from '@/components/common/ProductCard.vue'
import ProductGrid from '@/components/common/ProductGrid.vue'
import { Timer } from '@element-plus/icons-vue'

const products = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 每秒 tick，驱动活动状态与倒计时刷新
const now = ref(Date.now())
let timer = null

onMounted(() => {
  loadData()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function loadData() {
  loading.value = true
  try {
    const res = await getSeckillList({ page: currentPage.value, size: pageSize.value })
    if (res.data) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

// 秒杀价覆盖原价显示（空值才回退，0 元秒杀也保留 0）
function enrich(p) {
  return { ...p, originalPrice: p.price, price: p.seckillPrice ?? p.price }
}

// 解析时间（Jackson LocalDateTime 可能序列化为数组 [y,m,d,h,mi,s]）
function toTime(v) {
  if (v === null || v === undefined || v === '') return NaN
  if (Array.isArray(v)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0] = v
    return new Date(y, mo - 1, d, h, mi, s).getTime()
  }
  const t = new Date(v).getTime()
  return Number.isFinite(t) ? t : NaN
}

function seckillState(p) {
  const start = toTime(p.seckillStartTime)
  const end = toTime(p.seckillEndTime)
  if (Number.isNaN(start) || Number.isNaN(end)) return 'unknown'
  if (now.value < start) return 'pending'
  if (now.value > end) return 'ended'
  return 'active'
}

function remainingText(p) {
  const end = toTime(p.seckillEndTime)
  let diff = Math.max(0, Math.floor((end - now.value) / 1000))
  const pad = n => String(n).padStart(2, '0')
  const h = Math.floor(diff / 3600)
  const m = Math.floor((diff % 3600) / 60)
  const s = diff % 60
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

function fmtClock(t) {
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<style scoped>
.seckill-page { max-width: 1200px; margin: 0 auto; }
.sk-banner {
  display: flex; justify-content: space-between; align-items: center;
  background: #ff5000;
  border-radius: 12px; padding: 20px 24px; margin-bottom: 16px;
  box-shadow: 0 4px 24px rgba(255, 80, 0, .28);
  color: #fff;
}
.sk-banner-left { display: flex; align-items: center; gap: 14px; }
.sk-banner-left h2 { font-size: 24px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.sk-banner-icon { font-size: 26px; }
.sk-sub { font-size: 13px; opacity: .85; }
.sk-now { font-size: 13px; opacity: .9; font-family: var(--font-tech); }

.sk-card { position: relative; }
.sk-state { position: absolute; top: 6px; left: 6px; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; z-index: 1; pointer-events: none; }
.sk-tag { font-size: 11px; font-weight: 600; color: #fff; padding: 3px 8px; border-radius: 4px; line-height: 1.2; }
.sk-tag-active { background: #ff5000; }
.sk-tag-gray { background: rgba(0,0,0,.55); }
.sk-cd { font-size: 11px; color: #fff; background: rgba(255,80,0,.9); padding: 3px 8px; border-radius: 4px; line-height: 1.2; font-family: var(--font-tech); letter-spacing: .5px; }
</style>
