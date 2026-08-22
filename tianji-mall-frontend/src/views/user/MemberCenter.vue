<template>
  <div class="member-page">
    <!-- 会员卡 -->
    <div class="member-card" :style="cardStyle">
      <div class="mc-head">
        <div class="mc-level">
          <el-icon :size="30"><Medal /></el-icon>
          <span>{{ info.levelName || '青铜' }}会员</span>
        </div>
        <el-button
          class="mc-sign-btn"
          round
          :disabled="!!info.todaySigned"
          :loading="signing"
          @click="handleSignIn"
        >
          {{ info.todaySigned ? '今日已签到' : '每日签到' }}
        </el-button>
      </div>
      <div class="mc-points">
        <div class="mc-points-num">{{ info.points ?? 0 }}</div>
        <div class="mc-points-label">当前积分</div>
      </div>
      <div class="mc-progress">
        <div class="mc-progress-text">
          <span v-if="info.nextLevelName">距 {{ info.nextLevelName }}会员还差 {{ nextNeed }} 积分</span>
          <span v-else>已达最高等级</span>
        </div>
        <el-progress
          :percentage="info.progress ?? 0"
          :stroke-width="8"
          :show-text="false"
          :color="progressColor"
        />
      </div>
    </div>

    <!-- 积分记录 -->
    <div class="section">
      <div class="section-header">
        <h3>积分记录</h3>
      </div>
      <el-table :data="logs" v-loading="logLoading" empty-text="暂无积分记录">
        <el-table-column label="变动说明" prop="remark" min-width="160" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="row.changeType === 'sign_in' ? 'success' : 'primary'" size="small" effect="light">
              {{ changeTypeText(row.changeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="积分" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.points > 0 ? 'points-pos' : 'points-neg'">
              {{ row.points > 0 ? '+' + row.points : row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadLogs"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Medal } from '@element-plus/icons-vue'
import { getMemberInfo, signIn, getPointsLog } from '@/api'
import { fmtTime } from '@/utils/date'

const info = ref({})
const signing = ref(false)

const logs = ref([])
const logLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 等级配色（与后端 level 1-5 对应）
const LEVEL_COLORS = {
  1: ['#b0804a', '#8a5a2b'],   // 青铜
  2: ['#9aa7b3', '#7c8a96'],   // 白银
  3: ['#f6b93b', '#f5a623'],   // 黄金
  4: ['#4fc3c9', '#2f9ba3'],   // 铂金
  5: ['#7a6fd8', '#5a4fbf']    // 钻石
}
const LEVEL_THRESHOLDS = [0, 100, 500, 2000, 5000]

const cardStyle = computed(() => {
  const [c1, c2] = LEVEL_COLORS[info.value.level] || LEVEL_COLORS[1]
  return { background: `linear-gradient(135deg, ${c1}, ${c2})` }
})

const progressColor = computed(() => LEVEL_COLORS[info.value.level]?.[1] || '#fff')

const nextNeed = computed(() => {
  const level = info.value.level
  if (!level || level >= LEVEL_THRESHOLDS.length) return 0
  return Math.max(0, LEVEL_THRESHOLDS[level] - (info.value.totalPoints || 0))
})

const changeTypeText = (t) => ({ order_paid: '订单奖励', sign_in: '每日签到' }[t] || t || '-')

onMounted(() => {
  loadInfo()
  loadLogs()
})

async function loadInfo() {
  try {
    const res = await getMemberInfo()
    if (res.data) info.value = res.data
  } catch (e) {
    console.error('加载会员信息失败', e)
  }
}

async function handleSignIn() {
  if (signing.value) return
  signing.value = true
  try {
    const res = await signIn()
    if (res.data?.signed) {
      ElMessage.success(`签到成功，+${res.data.points} 积分`)
    } else {
      ElMessage.info('今天已经签到过了')
    }
    await loadInfo()
    await loadLogs()
  } catch { /* 由全局拦截器处理 */ }
  finally {
    signing.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await getPointsLog({ page: currentPage.value, size: pageSize.value })
    if (res.data) {
      logs.value = res.data.records || res.data || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载积分记录失败', e)
  } finally {
    logLoading.value = false
  }
}
</script>

<style scoped>
.member-page { max-width: 800px; margin: 0 auto; }

/* 会员卡 */
.member-card {
  border-radius: 14px;
  padding: 26px 28px;
  color: #fff;
  margin-bottom: 16px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, .12);
}
.mc-head { display: flex; justify-content: space-between; align-items: flex-start; }
.mc-level { display: flex; align-items: center; gap: 10px; font-size: 20px; font-weight: 700; }
.mc-sign-btn { background: rgba(255, 255, 255, .92); color: #a1a1aa; border: none; font-weight: 600; }
.mc-sign-btn:hover:not(:disabled) { background: #1b1b1e; }
.mc-sign-btn:disabled { background: rgba(255, 255, 255, .35); color: #fff; border-color: transparent; }

.mc-points { margin: 22px 0 6px; }
.mc-points-num { font-size: 44px; font-weight: 800; line-height: 1; }
.mc-points-label { font-size: 13px; opacity: .85; margin-top: 6px; }
.mc-progress { margin-top: 14px; }
.mc-progress-text { font-size: 13px; opacity: .9; margin-bottom: 8px; }

/* 通用区块 */
.section { background: rgba(27,27,30,.72); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); border-radius: 8px; padding: 20px; }
.section-header h3 { font-size: 16px; font-weight: 600; margin: 0 0 14px; }

.points-pos { color: #f56c2d; font-weight: 600; }
.points-neg { color: #909399; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
