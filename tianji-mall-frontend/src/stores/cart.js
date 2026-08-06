import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getCartList } from '@/api'
import { useUserStore } from '@/stores/user'

export const useCartStore = defineStore('cart', () => {
  const count = ref(0)

  async function refreshCount() {
    if (!useUserStore().isLoggedIn) { count.value = 0; return }
    try {
      const res = await getCartList()
      count.value = (res.data || []).reduce((s, i) => s + (i.quantity || 0), 0)
    } catch {
      count.value = 0
    }
  }

  return { count, refreshCount }
})
