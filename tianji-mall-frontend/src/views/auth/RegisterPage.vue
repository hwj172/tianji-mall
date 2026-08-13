<template>
  <div class="auth-page">
    <div class="auth-brand">
      <span class="ab-logo">天机商城</span>
      <span class="ab-slogan">加入天机 · 开启专属好物</span>
    </div>
    <el-card class="auth-card" shadow="always">
      <h2>注册</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password placeholder="请输入密码" /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPwd"><el-input v-model="form.confirmPwd" type="password" show-password placeholder="请再次输入密码" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="loading" style="width:100%">注册</el-button></el-form-item>
      </el-form>
      <p class="auth-switch">已有账号？<router-link to="/login">立即登录</router-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const formRef = ref(null)
const form = reactive({ username: '', password: '', confirmPwd: '' })
const validateConfirmPwd = (_rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 20, message: '用户名长度 3-20 位', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirmPwd: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirmPwd, trigger: 'blur' }]
}

async function handleRegister() {
  try { await formRef.value.validate() } catch { return }
  loading.value = true
  try {
    await userStore.register({ username: form.username, password: form.password })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch { /* 错误已在 interceptor 处理 */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.auth-page { display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 100vh; background: radial-gradient(900px 500px at 50% -12%, rgba(255, 80, 0, .2), transparent 60%), #0a0e17; padding: 24px; }
.auth-brand { text-align: center; margin-bottom: 24px; }
.ab-logo { display: block; font-size: 42px; font-weight: 800; color: #ff5000; letter-spacing: 3px; text-shadow: 0 0 18px rgba(255, 80, 0, .5); }
.ab-slogan { display: block; margin-top: 6px; font-size: 13px; color: #8b96ab; letter-spacing: 5px; }
.auth-card { width: 400px; border-radius: 12px; overflow: hidden; border-top: 4px solid #ff5000; }
.auth-card h2 { text-align: center; margin-bottom: 24px; color: #ff5000; }
.auth-switch { text-align: center; margin-top: 16px; }
.auth-switch a { color: #ff5000; }
.auth-card :deep(.el-button--primary) { background: #ff5000; border-color: #ff5000; }
</style>
