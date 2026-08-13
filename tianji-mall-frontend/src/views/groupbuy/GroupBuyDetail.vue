<template>
  <div class="groupbuy-detail-page" v-loading="loading">
    <template v-if="activity">
      <!-- 活动信息 -->
      <div class="gb-header">
        <img :src="productImage" class="gb-img" loading="lazy" decoding="async" @error="imageOnError($event, 120)" />
        <div class="gb-info">
          <h2>{{ productName }}</h2>
          <div class="gb-price">¥{{ fmtPrice(productPrice) }}</div>
          <div class="gb-time">{{ fmtTime(activity.startTime) }} ~ {{ fmtTime(activity.endTime) }} · {{ activity.expireHours }}h 内成团</div>
        </div>
        <el-button type="primary" @click="openStartDialog" class="gb-start-btn">我要开团</el-button>
      </div>

      <!-- 阶梯价格 -->
      <div class="gb-section">
        <h3>阶梯价格</h3>
        <div class="tier-list">
          <div
            v-for="t in tiers" :key="t.count"
            class="tier-card" :class="{ selected: startTargetCount === t.count }"
            @click="startTargetCount = t.count"
          >
            <div class="tier-count">{{ t.count }}人团</div>
            <div class="tier-discount">{{ formatDiscount(t.discount) }}</div>
          </div>
        </div>
      </div>

      <!-- 进行中的团 -->
      <div class="gb-section">
        <h3>进行中的团</h3>
        <div v-if="openGroups.length" class="group-list">
          <div v-for="g in openGroups" :key="g.id" class="group-row">
            <span class="gr-count">{{ g.currentCount }}/{{ g.targetTier }} 人</span>
            <span class="gr-time">{{ fmtTime(g.expireTime) }} 截止</span>
            <el-button size="small" type="warning" @click="openJoinDialog(g)">加入</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无进行中的团，来开第一团吧" :image-size="60" />
      </div>
    </template>
    <el-empty v-else-if="!loading" description="拼团活动不存在" />

    <!-- 开团 Dialog -->
    <el-dialog v-model="startVisible" title="开团" width="440px">
      <el-form label-width="80px">
        <el-form-item label="目标人数">
          <el-select v-model="startTargetCount">
            <el-option v-for="t in tiers" :key="t.count" :label="`${t.count}人团（${formatDiscount(t.discount)}）`" :value="t.count" />
          </el-select>
        </el-form-item>
        <el-form-item label="收货地址">
          <el-select v-model="startAddressId" placeholder="选择收货地址">
            <el-option v-for="a in addresses" :key="a.id" :label="`${a.receiverName} · ${a.province}${a.city}${a.district}`" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" @click="handleStart" :loading="submitting">确认开团</el-button>
      </template>
    </el-dialog>

    <!-- 参团 Dialog -->
    <el-dialog v-model="joinVisible" title="加入拼团" width="440px">
      <el-form label-width="80px">
        <el-form-item label="收货地址">
          <el-select v-model="joinAddressId" placeholder="选择收货地址">
            <el-option v-for="a in addresses" :key="a.id" :label="`${a.receiverName} · ${a.province}${a.city}${a.district}`" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="warning" @click="handleJoin" :loading="submitting">确认加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGroupBuyDetail, startGroupBuy, joinGroupBuy, getAddressList, getProductDetail } from '@/api'
import { fmtPrice } from '@/utils/format'
import { fmtTime } from '@/utils/date'
import { getFirstImage, imageOnError } from '@/utils/image'
import { formatDiscount } from '@/utils/discount'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const activity = ref(null)
const tiers = ref([])
const openGroups = ref([])
const productName = ref('')
const productPrice = ref('')
const productImage = ref('')

const addresses = ref([])
const startVisible = ref(false)
const joinVisible = ref(false)
const startTargetCount = ref(null)
const startAddressId = ref(null)
const joinAddressId = ref(null)
const joinTarget = ref(null)
const submitting = ref(false)

onMounted(async () => {
  await Promise.all([loadDetail(), loadAddresses()])
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getGroupBuyDetail(route.params.id)
    if (res.data) {
      activity.value = res.data.activity
      tiers.value = res.data.tiers || parseTiers(activity.value?.tiers)
      openGroups.value = res.data.openGroups || []
      startTargetCount.value = tiers.value[0]?.count || null
      if (activity.value?.productId) await loadProduct(activity.value.productId)
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadProduct(pid) {
  try {
    const res = await getProductDetail(pid)
    const p = res.data?.product
    if (p) {
      productName.value = p.name
      productPrice.value = p.price
      productImage.value = getFirstImage(p.images)
    }
  } catch { /* ignore */ }
}

async function loadAddresses() {
  try {
    const res = await getAddressList()
    addresses.value = res.data || []
  } catch { /* ignore */ }
}

function parseTiers(t) {
  if (!t) return []
  try { return typeof t === 'string' ? JSON.parse(t) : t } catch { return [] }
}

function openStartDialog() {
  if (!addresses.value.length) { ElMessage.warning('请先添加收货地址'); return }
  startAddressId.value = addresses.value[0]?.id || null
  startVisible.value = true
}

async function handleStart() {
  if (!startTargetCount.value) { ElMessage.warning('请选择目标人数'); return }
  if (!startAddressId.value) { ElMessage.warning('请选择收货地址'); return }
  submitting.value = true
  try {
    const res = await startGroupBuy({
      activityId: activity.value.id,
      targetCount: startTargetCount.value,
      addressId: startAddressId.value,
      directItems: [{ productId: activity.value.productId, quantity: 1 }]
    })
    ElMessage.success('开团成功！')
    startVisible.value = false
    router.push({ name: 'orderDetail', params: { id: res.data.orderId } })
  } catch { /* handle by interceptor */ }
  finally { submitting.value = false }
}

function openJoinDialog(g) {
  if (!addresses.value.length) { ElMessage.warning('请先添加收货地址'); return }
  joinTarget.value = g
  joinAddressId.value = addresses.value[0]?.id || null
  joinVisible.value = true
}

async function handleJoin() {
  if (!joinAddressId.value) { ElMessage.warning('请选择收货地址'); return }
  submitting.value = true
  try {
    const res = await joinGroupBuy(joinTarget.value.groupId, {
      addressId: joinAddressId.value,
      directItems: [{ productId: activity.value.productId, quantity: 1 }]
    })
    ElMessage.success('参团成功！')
    joinVisible.value = false
    router.push({ name: 'orderDetail', params: { id: res.data.orderId } })
  } catch { /* handle by interceptor */ }
  finally { submitting.value = false }
}

</script>

<style scoped>
.groupbuy-detail-page { max-width: 900px; margin: 0 auto; }

.gb-header { display: flex; align-items: center; gap: 20px; background: #121826; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.gb-img { width: 120px; height: 120px; border-radius: 8px; object-fit: cover; background: #1a2233; flex-shrink: 0; }
.gb-info { flex: 1; min-width: 0; }
.gb-info h2 { font-size: 20px; margin-bottom: 8px; }
.gb-price { color: #ff5000; font-size: 24px; font-weight: 700; margin-bottom: 8px; }
.gb-time { font-size: 13px; color: #5c6a82; }
.gb-start-btn { flex-shrink: 0; }

.gb-section { background: #121826; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.gb-section h3 { font-size: 16px; margin-bottom: 14px; }

.tier-list { display: flex; gap: 12px; }
.tier-card { border: 1px solid rgba(255,255,255,.08); border-radius: 8px; padding: 12px 20px; text-align: center; cursor: pointer; transition: all .2s; }
.tier-card:hover { border-color: #ff5000; }
.tier-card.selected { border-color: #ff5000; background: rgba(255, 80, 0, .12); }
.tier-count { font-size: 15px; font-weight: 600; }
.tier-discount { font-size: 13px; color: #ff5000; margin-top: 4px; }

.group-list { display: flex; flex-direction: column; gap: 10px; }
.group-row { display: flex; align-items: center; gap: 12px; background: #1a2233; border-radius: 6px; padding: 12px 16px; }
.gr-count { font-size: 14px; font-weight: 600; flex: 1; }
.gr-time { font-size: 12px; color: #5c6a82; }
</style>
