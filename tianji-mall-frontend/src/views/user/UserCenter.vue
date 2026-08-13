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
        <div class="uc-data" v-if="dataRowText">{{ dataRowText }}</div>
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
          <el-icon :size="28" class="stat-icon"><component :is="s.icon" /></el-icon>
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-count" v-if="s.count > 0">{{ s.count }}</div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="section">
      <h3 class="section-title">快捷入口</h3>
      <div class="ql-group" v-for="g in quickGroups" :key="g.title">
        <div class="ql-group-title">{{ g.title }}</div>
        <div class="quick-links" :class="gridClass(g.items.length)">
          <div
            v-for="item in g.items"
            :key="item.label"
            class="ql-item"
            :class="{ 'ql-danger': item.danger }"
            @click="item.action"
          >
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <el-badge v-if="item.badge" :value="item.badge" class="ql-badge" />
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑资料 Dialog -->
    <el-dialog v-model="editVisible" title="编辑资料" width="460px">
      <el-form ref="formRef" :model="editForm" :rules="editRules" label-width="70px">
        <el-form-item label="头像">
          <div class="avatar-upload" @click="onAvatarClick">
            <el-avatar :size="64" :src="editForm.avatar" />
            <div class="avatar-mask">{{ uploading ? '上传中…' : '更换头像' }}</div>
          </div>
          <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp" style="display:none" @change="handleAvatarChange" />
        </el-form-item>
        <el-form-item label="用户名" prop="username"><el-input v-model="editForm.username" maxlength="64" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="editForm.phone" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="editForm.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile" :loading="savingProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 注册开店 Dialog -->
    <el-dialog v-model="registerShopVisible" title="注册开店" width="460px">
      <el-form ref="shopFormRef" :model="shopForm" :rules="shopRules" label-width="70px">
        <el-form-item label="店铺名" prop="name"><el-input v-model="shopForm.name" maxlength="32" placeholder="给店铺起个名字" /></el-form-item>
        <el-form-item label="Logo" prop="logo">
          <div class="shop-logo-upload">
            <el-avatar v-if="shopForm.logo" :src="shopForm.logo" :size="48" shape="square" />
            <el-button size="small" @click="shopLogoInput.click()">{{ shopForm.logo ? '更换' : '上传' }}</el-button>
          </div>
          <input ref="shopLogoInput" type="file" accept="image/*" style="display:none" @change="handleShopLogoChange" />
        </el-form-item>
        <el-form-item label="简介" prop="description"><el-input v-model="shopForm.description" type="textarea" :rows="2" placeholder="店铺简介（可选）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerShopVisible = false">取消</el-button>
        <el-button type="primary" :loading="registeringShop" @click="submitRegisterShop">开店</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 Dialog -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="460px">
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="80px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6-32 位新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPassword" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CreditCard, Box, Van, Stamp,
  ShoppingCart, Ticket, MapLocation, Document, Star, Clock, Service, Bell, User, Close, Shop, Medal, Lock
} from '@element-plus/icons-vue'
import { getUserCenter, getUnreadCount, updateProfile, uploadAvatar, uploadImage, registerShop, updatePassword } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

// 快捷入口网格列数：2/4 固定列，3 的倍数（6/9）用 3 列避免末行大块留白
function gridClass(n) {
  if (n === 2) return 'cols-2'
  if (n === 4) return 'cols-4'
  if (n % 3 === 0) return 'cols-3'
  return 'cols-4'
}

const user = ref({})
const unreadCount = ref(0)
const presentKeys = ref([])
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
  { key: 'pendingPayment', label: '待付款', icon: CreditCard, count: centerData.orderStats.pendingPayment || 0, status: 1 },
  { key: 'pendingShip', label: '待发货', icon: Box, count: centerData.orderStats.pendingShip || 0, status: 2 },
  { key: 'pendingReceive', label: '待收货', icon: Van, count: centerData.orderStats.pendingReceive || 0, status: 3 },
  { key: 'pendingReview', label: '待评价', icon: Stamp, count: centerData.orderStats.pendingReview || 0, status: 4 }
])

const dataRowText = computed(() => {
  const parts = []
  const defs = [
    { key: 'followShopCount', label: '关注店铺' },
    { key: 'favoriteCount', label: '收藏' },
    { key: 'historyCount', label: '浏览足迹' }
  ]
  defs.forEach(d => {
    if (presentKeys.value.includes(d.key)) parts.push(`${d.label} ${centerData[d.key]}`)
  })
  return parts.join(' · ')
})

const quickGroups = computed(() => [
  {
    title: '购物',
    items: [
      { label: '购物车', icon: ShoppingCart, action: () => router.push('/cart'), badge: centerData.cartCount },
      { label: '优惠券', icon: Ticket, action: () => router.push('/coupon/center'), badge: centerData.couponCount },
      { label: '我的收藏', icon: Star, action: () => router.push('/favorite/list'), badge: centerData.favoriteCount },
      { label: '浏览足迹', icon: Clock, action: () => router.push('/user/history'), badge: centerData.historyCount }
    ]
  },
  {
    title: '服务',
    items: [
      { label: '我的会员', icon: Medal, action: () => router.push('/member') },
      { label: '收货地址', icon: MapLocation, action: () => router.push('/user/address') },
      { label: '我的评价', icon: Document, action: () => router.push('/review/my') },
      { label: '退款售后', icon: Service, action: () => router.push('/refund/list') },
      { label: '关注店铺', icon: Shop, action: () => router.push('/user/following-shops'), badge: centerData.followShopCount },
      { label: '消息通知', icon: Bell, action: () => router.push('/notification/list'), badge: unreadCount.value }
    ]
  },
  {
    title: '账户',
    items: [
      ...(userStore.userInfo?.role === 'user' ? [{ label: '注册开店', icon: Shop, action: openRegisterShop }] : []),
      { label: '编辑资料', icon: User, action: openEditDialog },
      { label: '修改密码', icon: Lock, action: openPasswordDialog },
      { label: '退出登录', icon: Close, action: handleLogout, danger: true }
    ]
  }
])

onMounted(async () => {
  try {
    const res = await getUserCenter()
    if (res.data) {
      if (res.data.user) user.value = res.data.user
      Object.assign(centerData, res.data)
      presentKeys.value = Object.keys(res.data).filter(k => centerData[k] != null)
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

// ========== 注册开店 ==========

const registerShopVisible = ref(false)
const registeringShop = ref(false)
const shopFormRef = ref(null)
const shopForm = ref({ name: '', logo: '', description: '' })
const shopLogoInput = ref(null)
const shopRules = {
  name: [{ required: true, message: '请输入店铺名', trigger: 'blur' }]
}

// 开店 Logo 文件上传 → 回填 shopForm.logo（唤起文件夹，不再手动输 URL）
async function handleShopLogoChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  e.target.value = ''
  const fd = new FormData()
  fd.append('files', file)
  try {
    const res = await uploadImage(fd)
    const url = Array.isArray(res.data) ? res.data[0] : res.data
    if (url) shopForm.value.logo = url
  } catch { /* interceptor 统一处理 */ }
}

function openRegisterShop() {
  shopForm.value = { name: '', logo: '', description: '' }
  registerShopVisible.value = true
}

async function submitRegisterShop() {
  if (shopFormRef.value) {
    try { await shopFormRef.value.validate() } catch { return }
  }
  registeringShop.value = true
  try {
    await registerShop({
      name: shopForm.value.name,
      logo: shopForm.value.logo || null,
      description: shopForm.value.description || null
    })
    ElMessage.success('开店成功！请重新登录以激活商家权限')
    registerShopVisible.value = false
    // 角色提升在 DB 生效，但 JWT 的 role claim 仍为旧值（网关据此鉴权）
    // 必须重新登录签发新 token，商家中心才能通过网关角色校验
    userStore.logout()
  } catch { /* handle by interceptor */ }
  finally { registeringShop.value = false }
}

// ========== 编辑资料 ==========

const editVisible = ref(false)
const savingProfile = ref(false)
const uploading = ref(false)
const avatarInput = ref(null)
const formRef = ref(null)
const editForm = ref({ username: '', phone: '', email: '', avatar: '' })

const editRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [{
    validator: (rule, value, callback) => {
      if (!value) return callback()
      return /^1[3-9]\d{9}$/.test(value) ? callback() : callback(new Error('请输入正确的手机号'))
    },
    trigger: 'blur'
  }],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }]
}

function openEditDialog() {
  editForm.value = {
    username: user.value.username || '',
    phone: user.value.phone || '',
    email: user.value.email || '',
    avatar: user.value.avatar || ''
  }
  editVisible.value = true
}

function onAvatarClick() {
  if (uploading.value) return
  avatarInput.value?.click()
}

async function handleAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.warning('仅支持 JPG / PNG / WebP 格式的图片')
    e.target.value = ''
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    e.target.value = ''
    return
  }
  uploading.value = true
  try {
    const res = await uploadAvatar(file)
    if (res.data) {
      // 头像修改需 admin 审核，先预览待审核头像，生效前仍显示原头像
      editForm.value.avatar = res.data
    }
    ElMessage.success('头像已提交审核，待管理员通过后生效')
  } catch { /* handle by interceptor */ }
  finally { uploading.value = false }
  e.target.value = ''
}

async function saveProfile() {
  if (formRef.value) {
    try {
      await formRef.value.validate()
    } catch { return }
  }
  savingProfile.value = true
  try {
    // 姓名修改需 admin 审核（防违规昵称），手机/邮箱直接生效
    const nameChanged = editForm.value.username !== user.value.username
    await updateProfile({
      username: editForm.value.username,
      phone: editForm.value.phone,
      email: editForm.value.email
    })
    user.value.phone = editForm.value.phone
    user.value.email = editForm.value.email
    if (nameChanged) {
      ElMessage.success('保存成功，姓名修改待管理员审核')
    } else {
      user.value.username = editForm.value.username
      ElMessage.success('保存成功')
    }
    editVisible.value = false
  } catch { /* handle by interceptor */ }
  finally { savingProfile.value = false }
}

// ========== 修改密码 ==========

const passwordVisible = ref(false)
const savingPassword = ref(false)
const passwordFormRef = ref(null)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 位之间', trigger: 'blur' }
  ],
  confirmPassword: [{
    validator: (rule, value, callback) => {
      if (!value) return callback(new Error('请再次输入新密码'))
      if (value !== passwordForm.value.newPassword) return callback(new Error('两次输入的密码不一致'))
      callback()
    },
    trigger: 'blur'
  }]
}

function openPasswordDialog() {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordVisible.value = true
}

async function submitPassword() {
  if (passwordFormRef.value) {
    try {
      await passwordFormRef.value.validate()
    } catch { return }
  }
  savingPassword.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    passwordVisible.value = false
  } catch { /* handle by interceptor */ }
  finally { savingPassword.value = false }
}
</script>

<style scoped>
.user-center-page { max-width: 900px; margin: 0 auto; }

/* 用户卡片 */
.user-card { background: linear-gradient(135deg, #ff6b35, #ff5000); border-radius: 12px; padding: 28px; display: flex; align-items: center; gap: 20px; color: #fff; margin-bottom: 16px; }
.uc-avatar :deep(.el-avatar) { border: 3px solid rgba(255,255,255,.4); }
.uc-info h3 { font-size: 22px; margin-bottom: 4px; }
.uc-role { font-size: 13px; opacity: .85; }
.uc-data { font-size: 13px; opacity: .9; margin-top: 6px; }
.uc-actions { margin-left: auto; }
.avatar-upload { position: relative; cursor: pointer; border-radius: 50%; overflow: hidden; flex-shrink: 0; }
.avatar-mask { position: absolute; inset: 0; background: rgba(0,0,0,.45); color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity .2s; }
.avatar-upload:hover .avatar-mask { opacity: 1; }
.shop-logo-upload { display: flex; align-items: center; gap: 12px; }

/* 通用区块 */
.section { background: #121826; border-radius: 8px; padding: 20px; margin-bottom: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-header h3 { font-size: 16px; font-weight: 600; }
.section-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.view-all { font-size: 13px; color: #5c6a82; }

/* 订单统计 */
.order-stats { display: flex; justify-content: space-around; }
.stat-item { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; position: relative; padding: 10px 20px; border-radius: 8px; transition: background .2s; }
.stat-item:hover { background: rgba(255, 80, 0, .12); }
.stat-icon { color: #ff5000; }
.stat-label { font-size: 13px; color: #8b96ab; }
.stat-count { position: absolute; top: 2px; right: 8px; background: #ff5000; color: #fff; font-size: 11px; min-width: 18px; height: 18px; line-height: 18px; text-align: center; border-radius: 9px; padding: 0 5px; }

/* 快捷入口 */
.ql-group-title { font-size: 13px; color: #5c6a82; margin: 14px 0 8px; }
.ql-group:first-child .ql-group-title { margin-top: 0; }
.quick-links { display: grid; gap: 12px; }
.quick-links.cols-2 { grid-template-columns: repeat(2, 1fr); }
.quick-links.cols-3 { grid-template-columns: repeat(3, 1fr); }
.quick-links.cols-4 { grid-template-columns: repeat(4, 1fr); }
.quick-links.cols-5 { grid-template-columns: repeat(5, 1fr); }
.ql-item { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 16px; border-radius: 8px; cursor: pointer; transition: all .2s; position: relative; color: #c3cbda; }
.ql-item:hover { background: rgba(255, 80, 0, .12); color: #ff5000; }
.ql-item span { font-size: 13px; }
.ql-item.ql-danger { color: #f56c6c; }
.ql-item.ql-danger:hover { color: #f56c6c; background: rgba(245, 108, 108, .12); }
.ql-badge { position: absolute; top: 6px; right: 10px; }

/* 响应式：移动端用户卡竖排、快捷入口网格收缩 */
@media (max-width: 768px) {
  .user-center-page { padding: 0 12px; }
  .user-card { flex-direction: column; text-align: center; }
  .uc-actions { margin: 0 auto; }
  .quick-links.cols-4, .quick-links.cols-5 { grid-template-columns: repeat(3, 1fr); }
}
</style>
