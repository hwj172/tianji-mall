// 折扣格式化：0.9 → '9折'，空值回退 '0折'
// 兼容旧数据：PERCENT discountValue 可能存 5（表示 5 折）而非 0.5，>1 时归一化为 0.x（与后端 applyCoupon 一致）
export function formatDiscount(d) {
  let v = Number(d) || 0
  if (v > 1) v = v / 10
  return (v * 10) + '折'
}
