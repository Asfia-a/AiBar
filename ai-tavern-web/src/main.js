import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reveal, tilt } from './directives/motion'
import './style.css'

// 主题（暗/亮）由 index.html 的内联脚本在首屏前挂好 .dark，src/theme.js 负责切换

const app = createApp(App)

// 全局动效：v-reveal 滚动进场、v-tilt 跟随鼠标的 3D 倾斜
app.directive('reveal', reveal)
app.directive('tilt', tilt)

app.use(createPinia())
app.use(router)
app.mount('#app')
