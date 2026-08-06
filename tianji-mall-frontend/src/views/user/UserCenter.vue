<template>
  <div class="user-center-page">
    <!-- 用户信息卡片 -->
    <div class="user-card">
      <div class="uc-avatar">
        <el-avatar :size="72" :src="user.avatar" />
      </div>
      <div class="uc-info">
        <h3>{{ user.username || '未登录' }}</h3>
        <span class="uc-role">{{ roleText }}</span>
      </div>
      <div class="uc-actions">
        <el-button size="small" @click="openEditDialog">编辑资料</el-button>
      </div>
    </div>

    <!-- 订单统计 -->
    <div class="section">
      <div class="section-header">
        <h3>我的订单</h3>
        <router-link to="/order/list" class="view-all">查看全部 →</router-link>
      </div>
      <div class="order-stats">
        <div class="stat-item" v-for="s in orderStatList" :key="s.key" @click="$router.push(`/order/list?status=${s.status}`)">
          <div class="stat-icon">{{ s.icon }}</div>
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-count" v-if="s.count > 0">{{ s.count }}</div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="section">
      <h3 class="section-title">快捷入口</h3>
      <div class="quick-links">
        <div class="ql-item" @click="$router.push('/cart')">
          <el-icon :size="22"><ShoppingCart /></el-icon>
          <span>购物车</span>
          <el-badge v-if="centerData.cartCount" :value="centerData.cartCount" class="ql-badge" />
        </div>
        <div class="ql-item" @click="$router.push('/coupon/center')">
          <el-icon :size="22"><Discount /></el-icon>
          <span>优惠券</span>
          <el-badge v-if="centerData.couponCount" :value="centerData.couponCount" class="ql-badge" />
        </div>
        <div class="ql-item" @click="$router.push('/user/address')">
          <el-icon :size="22"><Location /></el-icon>
          <span>收货地址</span>
        </div>
        <div class="ql-item" @click="$router.push('/review/pending')">
          <el-icon :size="22"><ChatDotSquare /></el-icon>
          <span>待评价</span>
        </div>
        <div class="ql-item" @click="$router.push('/review/my')">
          <el-icon :size="22"><Star /></el-icon>
          <span>我的评价</span>
        </div>
        <div class="ql-item" @click="$router.push('/favorite/list')">
          <span class="ql-emoji">❤️</span>
          <span>我的收藏</span>
          <el-badge v-if="centerData.favoriteCount" :value="centerData.favoriteCount" class="ql-badge" />
        </div>
        <div class="ql-item" @click="$router.push('/user/history')">
          <span class="ql-emoji">👣</span>
          <span>浏览足迹</span>
        </div>
        <div class="ql-item" @click="$router.push('/refund/list')">
          <span class="ql-emoji">🔙</span>
          <span>退款/售后</span>
        </div>
        <div class="ql-item" @click="$router.push('/notification/list')">
          <span class="ql-emoji">🔔</span>
          <span>消息通知</span>
          <el-badge v-if="unreadCount" :value="unreadCount" class="ql-badge" />
        </div>
        <div class="ql-item" @click="handleLogout">
          <el-icon :size="22"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </div>
      </div>
    </div>

    <!-- 编辑资料 Dialog -->
    <el-dialog v-model="editVisible" title="编辑资料" width="460px">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="头像">
          <div class="avatar-upload" @click="avatarInput?.click()">
            <el-avatar :size="64" :src="editForm.avatar" />
            <div class="avatar-mask">更换头像</div>
          </div>
          <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="handleAvatarChange" />
        </el-form-item>
        <el-form-item label="用户名"><el-input v-model="editForm.username" maxlength="64" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="editForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editForm.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile" :loading="savingProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserCenter, getUnreadCount, updateProfile, uploadAvatar } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const user = ref({})
const unreadCount = ref(0)
const centerData = reactive({
  orderStats: {},
  couponCount: 0,
  favoriteCount: 0,
  followShopCount: 0,
  cartCount: 0,
  historyCount: 0
})

const roleMap = { user: '普通用户', seller: '商家', admin: '管理员' }
const roleText = computed(() => roleMap[user.value.role] || '普通用户')

const orderStatList = computed(() => [
  { key: 'pendingPayment', label: '待付款', icon: '💰', count: centerData.orderStats.pendingPayment || 0, status: 1 },
  { key: 'pendingShip', label: '待发货', icon: '📦', count: centerData.orderStats.pendingShip || 0, status: 2 },
  { key: 'pendingReceive', label: '待收货', icon: '🚚', count: centerData.orderStats.pendingReceive || 0, status: 3 },
  { key: 'pendingReview', label: '待评价', icon: '✍️', count: centerData.orderStats.pendingReview || 0, status: 4 }
])

onMounted(async () => {
  try {
    const res = await getUserCenter()
    if (res.data) {
      if (res.data.user) user.value = res.data.user
      Object.assign(centerData, res.data)
    }
  } catch (e) {
    console.error('加载用户中心失败', e)
  }
  loadUnreadCount()
})

async function loadUnreadCount() {
  try {
    const res = await getUnreadCount()
    if (res.data != null) unreadCount.value = res.data
  } catch { /* ignore */ }
}

function handleLogout() {
  ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' }).then(() => {
    userStore.logout()   // 清 Pinia 状态 + localStorage + 跳登录页
  }).catch(() => {})
}

// ========== 编辑资料 ==========

const editVisible = ref(false)
const savingProfile = ref(false)
const avatarInput = ref(null)
const editForm = ref({ username: '', phone: '', email: '', avatar: '' })

function openEditDialog() {
  editForm.value = {
    username: user.value.username || '',
    phone: user.value.phone || '',
    email: user.value.email || '',
    avatar: user.value.avatar || ''
  }
  editVisible.value = true
}

async function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadAvatar(file)
    if (res.data) {
      editForm.value.avatar = res.data
      user.value.avatar = res.data
    }
    ElMessage.success('头像已更新')
  } catch { /* handle by interceptor */ }
  e.target.value = ''
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await updateProfile({
      username: editForm.value.username,
      phone: editForm.value.phone,
      email: editForm.value.email
    })
    user.value.username = editForm.value.username
    user.value.phone = editForm.value.phone
    user.value.email = editForm.value.email
    ElMessage.success('保存成功')
    editVisible.value = false
  } catch { /* handle by interceptor */ }
  finally { savingProfile.value = false }
}
</script>

<style scoped>
.user-center-page { max-width: 900px; margin: 0 auto; }

/* 用户卡片 */
.user-card { background: linear-gradient(135deg, #ff6b35, #ff5000); border-radius: 12px; padding: 28px; display: flex; align-items: center; gap: 20px; color: #fff; margin-bottom: 16px; }
.uc-avatar :deep(.el-avatar) { border: 3px solid rgba(255,255,255,.4); }
.uc-info h3 { font-size: 22px; margin-bottom: 4px; }
.uc-role { font-size: 13px; opacity: .85; }
.uc-actions { margin-left: auto; }
.avatar-upload { position: relative; cursor: pointer; border-radius: 50%; overflow: hidden; flex-shrink: 0; }
.avatar-mask { position: absolute; inset: 0; background: rgba(0,0,0,.45); color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity .2s; }
.avatar-upload:hover .avatar-mask { opacity: 1; }

/* 通用区块 */
.section { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { font-size: 16px; font-weight: 600; }
.section-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.view-all { font-size: 13px; color: #999; }

/* 订单统计 */
.order-stats { display: flex; justify-content: space-around; }
.stat-item { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; position: relative; padding: 10px 20px; border-radius: 8px; transition: background .2s; }
.stat-item:hover { background: #fff7f0; }
.stat-icon { font-size: 28px; }
.stat-label { font-size: 13px; color: #666; }
.stat-count { position: absolute; top: 2px; right: 8px; background: #ff5000; color: #fff; font-size: 11px; min-width: 18px; height: 18px; line-height: 18px; text-align: center; border-radius: 9px; padding: 0 5px; }

/* 快捷入口 */
.quick-links { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.ql-item { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 16px; border-radius: 8px; cursor: pointer; transition: all .2s; position: relative; color: #333; }
.ql-item:hover { background: #fff7f0; color: #ff5000; }
.ql-item span { font-size: 13px; }
.ql-emoji { font-size: 22px !important; line-height: 1; }
.ql-badge { position: absolute; top: 6px; right: 10px; }
</style>
