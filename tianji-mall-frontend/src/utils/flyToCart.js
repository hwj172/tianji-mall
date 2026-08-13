import gsap from 'gsap'

/**
 * 加购飞入购物车动画：商品图从主图位置飞向顶栏购物车图标。
 * @param {string} imgSrc 商品图片 URL
 */
export function flyToCart(imgSrc) {
  const cartEl = document.querySelector('.cart-icon')
  const startEl = document.querySelector('.main-image')
  if (!cartEl || !startEl) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

  const start = startEl.getBoundingClientRect()
  const end = cartEl.getBoundingClientRect()

  const flyer = document.createElement('img')
  flyer.src = imgSrc
  flyer.style.cssText = `position:fixed;left:${start.left + start.width / 2 - 25}px;top:${start.top + start.height / 2 - 25}px;width:50px;height:50px;object-fit:cover;border-radius:8px;z-index:9999;pointer-events:none;box-shadow:0 4px 16px rgba(0,0,0,.4);`
  document.body.appendChild(flyer)

  gsap.to(flyer, {
    left: end.left + end.width / 2 - 10,
    top: end.top + end.height / 2 - 10,
    width: 20,
    height: 20,
    opacity: 0.3,
    duration: 0.7,
    ease: 'power2.in',
    onComplete: () => flyer.remove()
  })
}
