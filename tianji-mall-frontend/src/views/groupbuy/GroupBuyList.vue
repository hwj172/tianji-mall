<template>
  <div class="groupbuy-page" v-loading="loading">
    <div class="gb-banner">
      <h2><el-icon class="gb-banner-icon"><Connection /></el-icon> 阶梯拼团</h2>
      <span class="gb-sub">拉人一起拼 · 人越多越便宜</span>
    </div>

    <div class="gb-list" v-if="activities.length">
      <div class="gb-card" v-for="a in activities" :key="a.id" @click="$router.push(`/groupbuy/${a.id}`)">
        <img class="gb-img" :src="getFirstImage(a.product?.images)" alt="" @error="onImgError" />
        <div class="gb-info">
          <div class="gb-name">{{ a.product?.name || `商品 #${a.productId}` }}</div>
          <div class="gb-tiers">
            <div class="tier-item" v-for="(tier, idx) in parseTiers(a.tiers)" :key="idx">
              <span class="tier-count">{{ tier.count }}人</span>
              <span class="tier-discount">{{ formatDiscount(tier.discount) }}</span>
            </div>
          </div>
        </div>
        <div class="gb-meta">
          <span>{{ fmtTime(a.startTime, { dateOnly: true }) }} 起</span>
          <el-button size="small" type="primary" @click.stop="$router.push(`/groupbuy/${a.id}`)">去参团</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无拼团活动" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getGroupBuyList } from '@/api'
import { fmtTime } from '@/utils/date'
import { formatDiscount } from '@/utils/discount'
import { getFirstImage, imageOnError } from '@/utils/image'
import { useProductBatch } from '@/composables/useProductBatch'
import { Connection } from '@element-plus/icons-vue'

const activities = ref([])
const loading = ref(false)
const { resolveProducts } = useProductBatch()

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await getGroupBuyList()
    const list = res.data || []
    // 批量回填商品名/图（后端活动只返回 productId）
    activities.value = await resolveProducts(list)
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function parseTiers(tiers) {
  if (!tiers) return []
  try { return typeof tiers === 'string' ? JSON.parse(tiers) : tiers } catch { return [] }
}

function onImgError(e) { imageOnError(e, 80) }
</script>

<style scoped>
.groupbuy-page { max-width: 900px; margin: 0 auto; }
.gb-banner {
  display: flex; align-items: center; gap: 14px;
  background: linear-gradient(135deg, #22d3ee, #0891b2);
  border-radius: 12px; padding: 20px 24px; margin-bottom: 16px;
  box-shadow: 0 4px 24px rgba(34, 211, 238, .25);
  color: #fff;
}
.gb-banner h2 { font-size: 24px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.gb-banner-icon { font-size: 26px; }
.gb-sub { font-size: 13px; opacity: .85; }

.gb-card { display: flex; align-items: center; gap: 16px; background: #1b1b1e; border: 1px solid rgba(255,255,255,.07); border-radius: 8px; padding: 16px; margin-bottom: 12px; cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .15s; }
.gb-card:hover { border-color: rgba(34, 211, 238, .5); box-shadow: 0 0 20px rgba(34, 211, 238, .12); transform: translateY(-1px); }
.gb-img { width: 80px; height: 80px; object-fit: cover; border-radius: 6px; flex-shrink: 0; background: #232327; }
.gb-info { flex: 1; min-width: 0; }
.gb-name { font-size: 15px; font-weight: 600; margin-bottom: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gb-tiers { display: flex; gap: 8px; }
.tier-item { background: rgba(34, 211, 238, .1); border: 1px solid rgba(34, 211, 238, .35); border-radius: 6px; padding: 4px 12px; display: flex; align-items: baseline; gap: 6px; }
.tier-count { font-size: 12px; color: #8b96ab; }
.tier-discount { font-size: 15px; font-weight: 700; color: #22d3ee; font-family: var(--font-tech); }
.gb-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; font-size: 12px; color: #5c6a82; flex-shrink: 0; }
</style>
