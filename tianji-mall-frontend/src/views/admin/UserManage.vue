<template>
  <div class="admin-page">
    <h2>用户管理</h2>

    <div class="ap-tabs" style="margin-bottom: 12px;">
      <span class="tab-item" :class="{ active: viewMode === 'all' }" @click="switchView('all')">全部用户</span>
      <span class="tab-item" :class="{ active: viewMode === 'pending' }" @click="switchView('pending')">待审核资料</span>
    </div>

    <div class="ap-filter" v-if="viewMode === 'all'">
      <el-input v-model="keyword" placeholder="搜索用户名/手机号" clearable @input="search" style="width:220px" />
      <el-select v-model="filterRole" placeholder="角色" clearable @change="loadData">
        <el-option label="普通用户" value="user" />
        <el-option label="商家" value="seller" />
        <el-option label="管理员" value="admin" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="状态" clearable @change="loadData">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
    </div>

    <el-table v-if="viewMode === 'all'" :data="users" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : row.role === 'seller' ? 'warning' : ''" size="small">
            {{ roleMap[row.role] || row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="handleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-dropdown @command="(role) => handleRole(row, role)">
            <el-button size="small" text>改角色 <el-icon><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="user">普通用户</el-dropdown-item>
                <el-dropdown-item command="seller">商家</el-dropdown-item>
                <el-dropdown-item command="admin">管理员</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-if="viewMode === 'pending'" :data="pendingProfiles" stripe v-loading="pendingLoading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="当前用户名" />
      <el-table-column label="待审核用户名" width="140">
        <template #default="{ row }"><b style="color:#ff5000">{{ row.pendingUsername || '—' }}</b></template>
      </el-table-column>
      <el-table-column label="待审核头像" width="90">
        <template #default="{ row }">
          <el-avatar v-if="row.pendingAvatar" :src="row.pendingAvatar" :size="36" />
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" text type="success" @click="handleApprove(row)">通过</el-button>
          <el-button size="small" text type="danger" @click="handleReject(row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="viewMode === 'pending' && !pendingLoading && !pendingProfiles.length" description="暂无待审核资料" />

    <div class="pagination-wrap" v-if="viewMode === 'all'">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadData"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, updateUserStatus, updateUserRole, getPendingProfiles, auditProfile } from '@/api'
import { fmtTime } from '@/utils/date'

const users = ref([])
const total = ref(0)
const loading = ref(false)
const viewMode = ref('all')
const pendingProfiles = ref([])
const pendingLoading = ref(false)
const keyword = ref('')
const filterRole = ref(null)
const filterStatus = ref(null)

const query = reactive({ page: 1, size: 20 })
const roleMap = { user: '普通用户', seller: '商家', admin: '管理员' }

let searchTimer = null

onMounted(() => loadData())

function search() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadData(), 400)
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (keyword.value) params.keyword = keyword.value
    if (filterRole.value) params.role = filterRole.value
    if (filterStatus.value !== null) params.status = filterStatus.value
    const res = await getAdminUsers(params)
    // Feign 返回的 Map 嵌套在 data 里
    const d = res.data
    if (d && d.records) {
      users.value = d.records
      total.value = d.total || 0
    } else if (Array.isArray(d)) {
      users.value = d
      total.value = d.length
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 1 ? '启用' : '禁用'
  try { await ElMessageBox.confirm(`确定${action}用户「${row.username}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    row.status = newStatus
  } catch { /* handle by interceptor */ }
}

async function handleRole(row, role) {
  if (role === row.role) return
  try { await ElMessageBox.confirm(`确定将用户「${row.username}」的角色改为「${roleMap[role]}」？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await updateUserRole(row.id, role)
    ElMessage.success(`角色已更新为「${roleMap[role]}」`)
    row.role = role
  } catch { /* handle by interceptor */ }
}

function switchView(v) {
  viewMode.value = v
  if (v === 'pending') loadPending()
  else loadData()
}

async function loadPending() {
  pendingLoading.value = true
  try {
    const res = await getPendingProfiles()
    pendingProfiles.value = res.data || []
  } catch { /* ignore */ }
  finally { pendingLoading.value = false }
}

async function handleApprove(row) {
  try {
    await auditProfile(row.id, true)
    ElMessage.success(`已通过「${row.username}」的资料审核`)
    loadPending()
  } catch { /* interceptor */ }
}

async function handleReject(row) {
  try {
    await ElMessageBox.confirm(`确定拒绝用户「${row.username}」的资料修改？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await auditProfile(row.id, false)
    ElMessage.success('已拒绝')
    loadPending()
  } catch { /* interceptor */ }
}

</script>

<style scoped>
.admin-page h2 { margin-bottom: 16px; }
.ap-filter { display: flex; gap: 12px; margin-bottom: 12px; }
.ap-tabs { display: flex; gap: 4px; margin-bottom: 12px; }
.ap-tabs .tab-item { padding: 6px 16px; font-size: 13px; cursor: pointer; border-radius: 4px; color: #666; }
.ap-tabs .tab-item:hover, .ap-tabs .tab-item.active { background: #fff7f0; color: #ff5000; font-weight: 600; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>
