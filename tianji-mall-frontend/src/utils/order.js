// 订单状态：1=待付款 2=已付款 3=已发货 4=已完成 5=已取消
export const ORDER_STATUS_MAP = { 1: '待付款', 2: '已付款', 3: '已发货', 4: '已完成', 5: '已取消' }

export function orderStatusText(s) {
  return ORDER_STATUS_MAP[s] || '未知'
}

// el-tag type：1=warning 4=success 5=info，其余用默认
const STATUS_TAG_MAP = { 1: 'warning', 2: '', 3: '', 4: 'success', 5: 'info' }
export function orderStatusTag(s) {
  return STATUS_TAG_MAP[s] || ''
}
