import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// ===== 全局顶部 loading bar（轻量自实现，不引 nprogress）=====
let pending = 0
let hideTimer = null

function getLoadingBar() {
  let bar = document.getElementById('global-loading-bar')
  if (!bar) {
    bar = document.createElement('div')
    bar.id = 'global-loading-bar'
    bar.style.cssText =
      'position:fixed;top:0;left:0;height:2px;width:0;background:#ff5000;z-index:9999;transition:width .2s,opacity .3s'
    document.body.appendChild(bar)
  }
  return bar
}

// 请求开始：计数 +1，进度条恢复到 30%
function startLoading() {
  pending++
  if (hideTimer) { clearTimeout(hideTimer); hideTimer = null }
  const bar = getLoadingBar()
  bar.style.opacity = '1'
  bar.style.width = '30%'
}

// 请求结束：计数 -1，全部完成后 100% → 300ms 淡出
function endLoading() {
  pending = Math.max(0, pending - 1)
  if (pending > 0) return
  const bar = getLoadingBar()
  bar.style.width = '100%'
  hideTimer = setTimeout(() => {
    bar.style.opacity = '0'
  }, 300)
}

// 请求拦截器 — 自动带 JWT
request.interceptors.request.use(config => {
  startLoading()
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器 — 统一错误处理
request.interceptors.response.use(
  response => {
    endLoading()
    const res = response.data
    if (res.code !== 200 && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    endLoading()
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
