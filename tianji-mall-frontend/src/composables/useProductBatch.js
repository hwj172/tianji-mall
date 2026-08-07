import { getProductBatch } from '@/api'

// 商品批量回填：收集 items 中的 productId → 批量查询 → Map → 按 items 原顺序回填 product。
// items 需含 productId 字段，返回 [{ ...item, product }]（商品不存在/已下架时 product 为 undefined）。
export function useProductBatch() {
  async function resolveProducts(items = []) {
    const list = Array.isArray(items) ? items : []
    if (!list.length) return []
    const ids = [...new Set(list.map(i => i.productId))]
    const res = await getProductBatch(ids)
    const map = new Map((res.data || []).map(p => [p.id, p]))
    return list.map(item => ({ ...item, product: map.get(item.productId) }))
  }
  return { resolveProducts }
}
