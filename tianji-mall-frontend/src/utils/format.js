// 金额统一格式化：非数字/异常值回退 '0.00'
export function fmtPrice(n) {
  const v = Number(n)
  return Number.isFinite(v) ? v.toFixed(2) : '0.00'
}
