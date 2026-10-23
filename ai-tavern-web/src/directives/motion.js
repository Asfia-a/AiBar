/**
 * 动效指令：v-reveal（滚动进场）与 v-tilt（跟随鼠标的 3D 倾斜 + 光斑）。
 *
 * 两条原则：
 * 1. 系统开了「减少动态效果」就全部跳过，不做动画；
 * 2. 触屏设备不做倾斜（没有 hover，倾斜只会卡）。
 */

const prefersReduced = () =>
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** 滚动进入视口时淡入上浮；可传数字做延迟（毫秒） */
export const reveal = {
  mounted(el, binding) {
    if (prefersReduced()) return
    el.classList.add('reveal')
    const delay = typeof binding.value === 'number' ? binding.value : 0
    if (delay) el.style.transitionDelay = `${delay}ms`

    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            el.classList.add('is-visible')
            io.unobserve(el)
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    )
    io.observe(el)
    el.__revealIO = io
  },
  unmounted(el) {
    el.__revealIO?.disconnect()
    delete el.__revealIO
  },
}

/** 鼠标在元素上移动时做轻微 3D 倾斜，并把光斑位置写进 --mx / --my */
export const tilt = {
  mounted(el, binding) {
    if (prefersReduced()) return
    if (window.matchMedia?.('(hover: none)').matches) return

    const max = typeof binding.value === 'number' ? binding.value : 6
    el.classList.add('tilt')

    const onMove = (ev) => {
      const rect = el.getBoundingClientRect()
      const px = (ev.clientX - rect.left) / rect.width
      const py = (ev.clientY - rect.top) / rect.height
      el.style.setProperty('--mx', `${px * 100}%`)
      el.style.setProperty('--my', `${py * 100}%`)
      el.style.transform = `perspective(900px) rotateX(${(0.5 - py) * max}deg) rotateY(${(px - 0.5) * max}deg) translateY(-2px)`
    }
    const onLeave = () => {
      el.style.transform = ''
    }

    el.addEventListener('pointermove', onMove)
    el.addEventListener('pointerleave', onLeave)
    el.__tiltCleanup = () => {
      el.removeEventListener('pointermove', onMove)
      el.removeEventListener('pointerleave', onLeave)
    }
  },
  unmounted(el) {
    el.__tiltCleanup?.()
    delete el.__tiltCleanup
  },
}
