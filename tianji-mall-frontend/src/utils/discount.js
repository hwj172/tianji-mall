// 折扣格式化：0.9 → '9折'，空值回退 '0折'
export function formatDiscount(d) {
  const v = Number(d) || 0
  return (v * 10) + '折'
}
