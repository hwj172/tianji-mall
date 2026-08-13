import { onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import gsap from 'gsap'

/**
 * 滚动入场 + 交错动画：容器内匹配选择器的元素依次 fade-up 入场。
 * 监听容器子元素数量变化，数据异步渲染后自动重新触发。
 * @param {import('vue').Ref<HTMLElement|null>} containerRef 容器 ref
 * @param {string} itemSelector 子元素选择器（如 '.product-card'）
 * @param {object} opts { stagger }
 */
export function useReveal(containerRef, itemSelector, { stagger = 0.06 } = {}) {
  let observer = null

  function reveal() {
    const container = containerRef.value
    if (!container) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    const items = container.querySelectorAll(itemSelector)
    if (!items.length) return
    // 已入场过的元素跳过，只对新出现的元素做入场
    const fresh = Array.from(items).filter(el => !el.dataset.revealed)
    if (!fresh.length) return

    gsap.fromTo(fresh,
      { opacity: 0, y: 18 },
      { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out', stagger, onComplete: () => {
        fresh.forEach(el => { el.dataset.revealed = '1' })
      } })
  }

  function observe() {
    const container = containerRef.value
    if (!container) return
    if (observer) observer.disconnect()
    observer = new MutationObserver(() => nextTick(reveal))
    observer.observe(container, { childList: true, subtree: true })
  }

  onMounted(() => {
    nextTick(() => { reveal(); observe() })
  })

  watch(containerRef, () => {
    nextTick(() => { reveal(); observe() })
  })

  onBeforeUnmount(() => {
    if (observer) observer.disconnect()
  })
}
