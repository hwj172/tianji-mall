// 图片工具：首图解析 + 加载失败占位
// 兼容字符串 JSON / 数组 / 空值，null 安全
export function getFirstImage(images) {
  if (!images) return ''
  try {
    const arr = typeof images === 'string' ? JSON.parse(images) : images
    if (!Array.isArray(arr)) return ''
    return arr[0] || ''
  } catch { return '' }
}

// 图片加载失败换 SVG 占位 data-URI（size 控制占位图边长）
export function imageOnError(e, size = 200) {
  const n = Math.max(10, Math.round(size / 25))
  e.target.src = `data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}"><rect fill="%23f5f5f5" width="${size}" height="${size}"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23ccc" font-size="${n}">暂无图片</text></svg>`
}
