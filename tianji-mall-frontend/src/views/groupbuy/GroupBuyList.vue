<template>
  <div class="groupbuy-page">
    <h2>👥 阶梯拼团</h2>

    <div class="gb-list" v-if="activities.length">
      <div class="gb-card" v-for="a in activities" :key="a.id">
        <div class="gb-product">
          <span class="gb-pid">商品 #{{ a.productId }}</span>
        </div>
        <div class="gb-tiers">
          <div class="tier-item" v-for="(tier, idx) in parseTiers(a.tiers)" :key="idx">
            <span class="tier-count">{{ tier.count }}人团</span>
            <span class="tier-discount">{{ (tier.discount * 100).toFixed(0) }}折</span>
          </div>
        </div>
        <div class="gb-meta">
          <span>{{ fmtTime(a.startTime) }} ~ {{ fmtTime(a.endTime) }}</span>
          <el-button size="small" type="primary" @click="$router.push(`/product/${a.productId}`)">去参团</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无拼团活动" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getGroupBuyList } from '@/api'

const activities = ref([])
const loading = ref(false)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getGroupBuyList()
    activities.value = res.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function parseTiers(tiers) {
  if (!tiers) return []
  try { return typeof tiers === 'string' ? JSON.parse(tiers) : tiers } catch { return [] }
}

function fmtTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) t = t[0] + 'T' + t[1]
  return new Date(t).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.groupbuy-page { max-width: 900px; margin: 0 auto; }
.groupbuy-page h2 { font-size: 20px; margin-bottom: 16px; }

.gb-card { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.gb-product { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: #ff5000; cursor: pointer; }

.gb-tiers { display: flex; gap: 12px; margin-bottom: 12px; }
.tier-item { background: #fff7f0; border: 1px solid #ffe0cc; border-radius: 6px; padding: 8px 16px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.tier-count { font-size: 14px; font-weight: 600; color: #333; }
.tier-discount { font-size: 13px; color: #ff5000; }

.gb-meta { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: #999; }
</style>
