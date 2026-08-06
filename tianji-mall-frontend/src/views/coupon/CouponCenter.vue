<template>
  <div class="coupon-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane name="center">
        <template #label>
          领券中心
          <el-badge v-if="unclaimedCount > 0" :value="unclaimedCount" style="margin-left: 6px" />
        </template>
        <div class="coupon-list" v-if="coupons.length">
          <div class="coupon-card" v-for="c in coupons" :key="c.id" :class="{ claimed: c.claimed }">
            <div class="cc-left">
              <div class="cc-value">
                <template v-if="c.discountType === 'FIXED'">
                  <span class="cc-symbol">¥</span>{{ c.discountValue }}
                </template>
                <template v-else>
                  {{ c.discountValue * 10 }}<span class="cc-symbol">折</span>
                </template>
              </div>
              <div class="cc-type">{{ c.discountType === 'FIXED' ? '满减券' : '折扣券' }}</div>
            </div>
            <div class="cc-right">
              <div class="cc-name">{{ c.name }}</div>
              <div class="cc-cond" v-if="c.minOrderAmount > 0">满 ¥{{ c.minOrderAmount }} 可用</div>
              <div class="cc-time">{{ fmtTime(c.endTime) }} 前可用</div>
            </div>
            <div class="cc-action">
              <el-tag v-if="c.expiringSoon" size="small" type="warning">即将过期</el-tag>
              <el-button v-if="!c.claimed" type="danger" size="small" @click="handleClaim(c)" :loading="c.claiming">立即领取</el-button>
              <el-tag v-else size="small" type="info">已领取</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无可领优惠券" />
      </el-tab-pane>

      <el-tab-pane label="我的优惠券" name="my">
        <div class="coupon-list" v-if="myCoupons.length">
          <div class="coupon-card" v-for="item in myCoupons" :key="item.userCouponId" :class="{ used: item.status === 'USED' || item.status === 'EXPIRED' }">
            <div class="cc-left">
              <div class="cc-value">
                <template v-if="item.discountType === 'FIXED'">
                  <span class="cc-symbol">¥</span>{{ item.discountValue }}
                </template>
                <template v-else>
                  {{ (item.discountValue || 0) * 10 }}<span class="cc-symbol">折</span>
                </template>
              </div>
              <div class="cc-type">{{ statusMap[item.status] || item.status }}</div>
            </div>
            <div class="cc-right">
              <div class="cc-name">{{ item.name }}</div>
              <div class="cc-cond" v-if="item.minOrderAmount > 0">满 ¥{{ item.minOrderAmount }} 可用</div>
              <div class="cc-time">{{ fmtTime(item.endTime) }} 到期</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无优惠券" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCouponCenter, claimCoupon, getMyCoupons, getCouponCount } from '@/api'

const activeTab = ref('center')
const coupons = ref([])
const myCoupons = ref([])
const unclaimedCount = ref(0)
const statusMap = { UNUSED: '未使用', USED: '已使用', EXPIRED: '已过期' }

onMounted(async () => {
  await loadCenter()
  await loadMy()
  loadUnclaimedCount()
})

async function loadUnclaimedCount() {
  try {
    const res = await getCouponCount()
    if (res.data?.unclaimed != null) unclaimedCount.value = res.data.unclaimed
  } catch { /* ignore */ }
}

async function loadCenter() {
  try {
    const res = await getCouponCenter()
    if (res.data) {
      const list = res.data.coupons || res.data || []
      coupons.value = list.map(c => ({ ...c, claiming: false }))
    }
  } catch { /* ignore */ }
}

async function loadMy() {
  try {
    const res = await getMyCoupons()
    myCoupons.value = res.data || []
  } catch { /* ignore */ }
}

async function handleClaim(c) {
  c.claiming = true
  try {
    await claimCoupon(c.id)
    ElMessage.success('领取成功')
    c.claimed = true
    loadUnclaimedCount()  // 角标递减
  } catch { /* handle by interceptor */ }
  finally { c.claiming = false }
}

function fmtTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) t = t[0] + 'T' + t[1]
  return new Date(t).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.coupon-page { max-width: 900px; margin: 0 auto; }

.coupon-list { display: flex; flex-direction: column; gap: 12px; }
.coupon-card { display: flex; align-items: stretch; background: #fff; border-radius: 8px; overflow: hidden; border: 1px solid #f0f0f0; }
.coupon-card.claimed, .coupon-card.used { opacity: .6; }

.cc-left { width: 120px; background: linear-gradient(135deg, #ff6b35, #ff5000); color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 16px 12px; }
.cc-value { font-size: 28px; font-weight: 700; }
.cc-symbol { font-size: 16px; }
.cc-type { font-size: 12px; margin-top: 4px; opacity: .85; }

.cc-right { flex: 1; padding: 16px 20px; display: flex; flex-direction: column; justify-content: center; gap: 4px; }
.cc-name { font-size: 16px; font-weight: 600; }
.cc-cond { font-size: 13px; color: #666; }
.cc-time { font-size: 12px; color: #999; }

.cc-action { display: flex; align-items: center; padding: 0 20px; }
</style>
