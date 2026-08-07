import { ref } from 'vue'

// 加载更多分页：fetcher({ page, size }) 返回 { data: [...] }，list 累积已加载数据。
// 返回 list/page/loading/loadingMore/hasMore + loadData（重置并加载第一页）/loadMore（追加下一页）。
export function usePagedList(fetcher, pageSize = 20) {
  const list = ref([])
  const page = ref(1)
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(true)

  async function loadData() {
    loading.value = true
    page.value = 1
    hasMore.value = true
    try {
      const res = await fetcher({ page: 1, size: pageSize })
      list.value = res.data || []
      hasMore.value = list.value.length >= pageSize
    } catch { /* ignore */ }
    finally { loading.value = false }
  }

  async function loadMore() {
    if (loadingMore.value) return
    loadingMore.value = true
    try {
      page.value++
      const res = await fetcher({ page: page.value, size: pageSize })
      const newList = res.data || []
      list.value = [...list.value, ...newList]
      hasMore.value = newList.length >= pageSize
    } catch { page.value-- }
    finally { loadingMore.value = false }
  }

  return { list, page, loading, loadingMore, hasMore, loadData, loadMore }
}
