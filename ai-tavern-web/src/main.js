import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reveal, tilt } from './directives/motion'
import './style.css'

// 新版界面默认暗色（现代 AI 产品风）
document.documentElement.classList.add('dark')

const app = createApp(App)

// 全局动效：v-reveal 滚动进场、v-tilt 跟随鼠标的 3D 倾斜
app.directive('reveal', reveal)
app.directive('tilt', tilt)

app.use(createPinia())
app.use(router)
app.mount('#app')
