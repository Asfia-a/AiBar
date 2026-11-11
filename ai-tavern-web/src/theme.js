import { ref } from 'vue'

const STORAGE_KEY = 'tavern-theme'

/**
 * 当前主题。
 *
 * 初始值以 <html> 上的类为准——index.html 里有一段首屏前的内联脚本，
 * 按 localStorage 决定挂不挂 .dark；这里只在应用启动时读一次初始状态，
 * 避免模块加载晚于首帧造成闪屏。
 */
export const theme = ref(
  typeof document !== 'undefined' && document.documentElement.classList.contains('dark')
    ? 'dark'
    : 'light',
)

export function setTheme(next) {
  theme.value = next === 'light' ? 'light' : 'dark'
  document.documentElement.classList.toggle('dark', theme.value === 'dark')
  try {
    localStorage.setItem(STORAGE_KEY, theme.value)
  } catch {
    /* 隐私模式写不进 localStorage 就算了，本次会话内仍然生效 */
  }
}

export function toggleTheme() {
  setTheme(theme.value === 'dark' ? 'light' : 'dark')
}
