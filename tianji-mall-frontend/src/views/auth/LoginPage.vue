<template>
  <div class="auth-page">
    <div class="auth-brand">
      <span class="ab-logo">天机商城</span>
      <span class="ab-slogan">好货不贵 · 天机甄选</span>
    </div>
    <el-card class="auth-card" shadow="always">
      <h2>登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password placeholder="请输入密码" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="loading" style="width:100%">登录</el-button></el-form-item>
      </el-form>
      <p class="auth-switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)

const formRef = ref(null)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  try { await formRef.value.validate() } catch { return }
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
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
