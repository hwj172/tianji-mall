import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getCartList } from '@/api'
import { useUserStore } from '@/stores/user'

export const useCartStore = defineStore('cart', () => {
  const count = ref(0)
  // 列表缓存：避免角标刷新每次全量拉取购物车。CartPage 增删改后通过 setList 同步本地结果，
  // 其他页面（加购/下单）走 force 强制重新拉取保证准确。
  let listCache = null
  let cacheUser = ''

  async function refreshCount(force = false) {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) { count.value = 0; listCache = null; cacheUser = ''; return }
    // 已缓存且用户未变化且非强制 → 本地算数量，不发请求
    if (!force && listCache && cacheUser === userStore.token) {
      count.value = listCache.reduce((s, i) => s + (i.quantity || 0), 0)
      return
    }
    try {
      const res = await getCartList()
      listCache = res.data || []
      cacheUser = userStore.token
      count.value = listCache.reduce((s, i) => s + (i.quantity || 0), 0)
    } catch {
      count.value = 0
    }
  }

  // 由购物车页在本地数据变更后同步（不触发网络请求）
  function setList(items) {
    listCache = items || []
    cacheUser = useUserStore().token || ''
    count.value = listCache.reduce((s, i) => s + (i.quantity || 0), 0)
  }

  return { count, refreshCount, setList }
})
