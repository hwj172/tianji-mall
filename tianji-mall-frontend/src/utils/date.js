// 时间格式化：兼容 [y,m,d,h,mi,s] 数组与 ISO 字符串
// options.dateOnly 为 true 时仅显示日期
export function fmtTime(t, options = {}) {
  if (!t) return ''
  let d
  if (Array.isArray(t)) {
    const [y, m, day, h = 0, mi = 0, s = 0] = t
    d = new Date(y, (m || 1) - 1, day || 1, h, mi, s)
  } else {
    d = new Date(t)
  }
  if (Number.isNaN(d.getTime())) return ''
  return options.dateOnly ? d.toLocaleDateString('zh-CN') : d.toLocaleString('zh-CN')
}
