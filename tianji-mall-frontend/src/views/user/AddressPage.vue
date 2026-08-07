<template>
  <div class="address-page">
    <div class="ap-header">
      <h2>收货地址</h2>
      <el-button type="primary" @click="openDialog()">新增地址</el-button>
    </div>

    <div class="addr-list" v-if="addresses.length">
      <div class="addr-card" v-for="addr in addresses" :key="addr.id">
        <div class="ac-body">
          <div class="ac-contact">
            <b>{{ addr.receiverName }}</b>
            <span>{{ addr.phone }}</span>
            <el-tag v-if="addr.isDefault" size="small" type="danger">默认</el-tag>
          </div>
          <p class="ac-text">{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}</p>
        </div>
        <div class="ac-actions">
          <el-button size="small" text @click="openDialog(addr)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(addr)">删除</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading && !addresses.length" description="暂无收货地址" />

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑地址' : '新增地址'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="收货人"><el-input v-model="form.receiverName" placeholder="姓名" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
        <el-form-item label="所在地区">
          <el-cascader
            v-model="regionPath"
            :options="regionTree"
            :props="{ value: 'name', label: 'name', children: 'children' }"
            placeholder="请选择省/市/区"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="详细地址"><el-input v-model="form.detail" placeholder="街道/门牌号" /></el-form-item>
        <el-form-item><el-checkbox v-model="form.isDefault">设为默认地址</el-checkbox></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAddressList, addAddress, updateAddress, deleteAddress, getRegionTree } from '@/api'

const addresses = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const regionTree = ref([])
const regionPath = ref([])
const form = reactive({ receiverName: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })

onMounted(() => { loadData(); loadRegionTree() })

async function loadRegionTree() {
  try {
    const res = await getRegionTree()
    regionTree.value = res.data || []
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAddressList()
    addresses.value = res.data || []
  } catch (e) {
    console.error('加载地址失败', e)
  } finally {
    loading.value = false
  }
}

function openDialog(addr) {
  if (addr) {
    editingId.value = addr.id
    Object.assign(form, {
      receiverName: addr.receiverName, phone: addr.phone,
      province: addr.province, city: addr.city, district: addr.district,
      detail: addr.detail, isDefault: addr.isDefault === 1 || addr.isDefault === true
    })
    regionPath.value = [addr.province, addr.city, addr.district].filter(Boolean)
  } else {
    editingId.value = null
    Object.assign(form, { receiverName: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false })
    regionPath.value = []
  }
  dialogVisible.value = true
}

async function handleSave() {
  // 从级联选择器提取省市区
  const [province, city, district] = regionPath.value || []
  form.province = province || ''
  form.city = city || ''
  form.district = district || ''
  if (!form.receiverName || !form.phone || !form.province || !form.detail) {
    ElMessage.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    const data = {
      receiverName: form.receiverName, phone: form.phone,
      province: form.province, city: form.city, district: form.district,
      detail: form.detail, isDefault: form.isDefault ? 1 : 0
    }
    if (editingId.value) {
      await updateAddress(editingId.value, data)
      ElMessage.success('已更新')
    } else {
      await addAddress(data)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    await loadData()
  } catch { /* handle by interceptor */ }
  finally { saving.value = false }
}

async function handleDelete(addr) {
  try { await ElMessageBox.confirm('确定删除该地址？', '提示', { type: 'warning' }) } catch { return }
  try {
    await deleteAddress(addr.id)
    ElMessage.success('已删除')
    await loadData()
  } catch { /* handle by interceptor */ }
}
</script>

<style scoped>
.address-page { max-width: 800px; margin: 0 auto; }
.ap-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ap-header h2 { font-size: 20px; font-weight: 600; }

.addr-card { background: #fff; border-radius: 8px; padding: 16px 20px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
.ac-contact { display: flex; align-items: center; gap: 10px; font-size: 15px; margin-bottom: 6px; }
.ac-text { font-size: 13px; color: #666; }
.ac-actions { display: flex; gap: 4px; }

</style>
