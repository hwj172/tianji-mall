import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/api/request'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'admin')
  // 商家中心仅 seller 专属（管理员走 admin 后台）
  const isSeller = computed(() => userInfo.value?.role === 'seller')

  async function login(credentials) {
    const res = await request.post('/user/login', credentials)
    token.value = res.data.token
    // LoginResponse 扁平结构：{userId, username, token, role}
    userInfo.value = {
      id: res.data.userId,
      username: res.data.username,
      role: res.data.role
    }
    localStorage.setItem('token', res.data.token)
    return res.data
  }

  async function register(data) {
    return await request.post('/user/register', data)
  }

  async function fetchUserInfo() {
    try {
      const res = await request.get('/user/info')
      userInfo.value = res.data
    } catch {
      logout()
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    router.push('/login')
  }

  return { token, userInfo, isLoggedIn, isAdmin, isSeller, login, register, fetchUserInfo, logout }
})
