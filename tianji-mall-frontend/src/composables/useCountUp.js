import gsap from 'gsap'

/**
 * 数字从 0 滚动到目标值（gsap）。
 * @param {HTMLElement} el 目标元素
 * @param {number|null|undefined} target 目标值
 * @param {object} opts { duration, prefix }
 */
export function countUp(el, target, { duration = 1.2, prefix = '' } = {}) {
  if (!el) return
  if (target === null || target === undefined || target === '') {
    el.textContent = '—'
    return
  }
  const num = Number(target)
  if (Number.isNaN(num)) {
    el.textContent = String(target)
    return
  }
  // 尊重用户减少动效偏好
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    el.textContent = prefix + num.toLocaleString()
    return
  }
  const state = { val: 0 }
  gsap.to(state, {
    val: num,
    duration,
    ease: 'power2.out',
    onUpdate: () => {
      el.textContent = prefix + Math.floor(state.val).toLocaleString()
    },
    onComplete: () => {
      el.textContent = prefix + num.toLocaleString()
    }
  })
}
