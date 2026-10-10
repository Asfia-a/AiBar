<template>
  <div class="min-h-screen flex flex-col">
    <header class="sticky top-0 z-50 border-b border-tavern-700/60 bg-tavern-900/90 backdrop-blur-md">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
        <router-link to="/" class="flex items-center gap-2 group">
          <span class="text-2xl">🍺</span>
          <span class="font-display text-2xl font-bold text-tavern-300 group-hover:text-tavern-200 transition-colors">
            AI角色酒馆
          </span>
        </router-link>

        <nav class="hidden md:flex items-center gap-1">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="rounded-lg px-3 py-2 text-sm transition-colors"
            :class="isNavActive(item)
              ? '!text-tavern-300 bg-tavern-800/80'
              : 'text-tavern-muted hover:text-tavern-200 hover:bg-tavern-800/60'"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="flex items-center gap-2">
          <router-link to="/search" class="p-2 text-tavern-muted hover:text-tavern-300 transition-colors">
            <el-icon :size="20"><Search /></el-icon>
          </router-link>
          <template v-if="authStore.isLoggedIn">
            <button
              type="button"
              class="hidden sm:flex items-center gap-1 rounded-lg border border-tavern-700/60 bg-tavern-800/50 px-2.5 py-1 text-xs text-tavern-200 hover:border-tavern-500 transition-colors"
              title="点击充值"
              @click="router.push('/profile')"
            >
              <span class="text-tavern-muted">积分</span>
              <span class="font-medium text-tavern-300">{{ formatNum(authStore.user?.points) }}</span>
            </button>
            <router-link to="/notifications" class="p-2 text-tavern-muted hover:text-tavern-300 transition-colors relative">
              <el-icon :size="20"><Bell /></el-icon>
            </router-link>
            <el-dropdown trigger="click" @command="handleCommand">
              <button class="flex items-center gap-2 rounded-lg px-2 py-1 hover:bg-tavern-800/60 transition-colors">
                <el-avatar :size="32" :src="authStore.user?.avatar">
                  {{ authStore.user?.nickname?.[0] || 'U' }}
                </el-avatar>
                <span class="hidden sm:inline text-sm text-tavern-200">{{ authStore.user?.nickname || '旅人' }}</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人资料 / 充值</el-dropdown-item>
                  <el-dropdown-item command="mine">我的角色</el-dropdown-item>
                  <el-dropdown-item command="workshop">创意工坊</el-dropdown-item>
                  <el-dropdown-item command="inspiration">灵感记录</el-dropdown-item>
                  <el-dropdown-item command="narrative">叙事工坊</el-dropdown-item>
                  <el-dropdown-item command="community">社区广场</el-dropdown-item>
                  <el-dropdown-item command="metrics">运行机制可视化</el-dropdown-item>
                  <el-dropdown-item command="world-tree">世界树</el-dropdown-item>
                  <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                  <el-dropdown-item command="checkin">每日签到</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <router-link v-else to="/login" class="tavern-btn-primary text-sm">
            登录
          </router-link>
        </div>
      </div>
    </header>

    <main class="flex-1 mx-auto w-full max-w-7xl px-4 py-6 sm:px-6">
      <router-view />
    </main>

    <footer class="border-t border-tavern-800 py-6 text-center text-sm text-tavern-muted">
      <p>AI角色酒馆 — 在温暖灯光下，与角色共度良宵</p>
    </footer>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { to: '/', label: '角色广场', name: 'Home' },
  { to: '/chat', label: '对话', name: 'Chat' },
  { to: '/community', label: '社区', name: 'Community' },
  { to: '/mine', label: '我的角色', name: 'MyCharacters' },
  { to: '/inspiration', label: '灵感记录', name: 'Inspiration' },
  { to: '/world-tree', label: '世界树', name: 'WorldTree' },
  { to: '/create', label: '创建', name: 'CreateCharacter' },
]

/**
 * 导航高亮不能用 router-link 的 active-class：
 * `/` 是所有子路由的父级，vue-router 按 matched 记录判定，
 * 进到任何页面都会把「角色广场」标成激活。改为比对路由名。
 */
function isNavActive(item) {
  return route.name === item.name
}

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

function handleCommand(cmd) {
  if (cmd === 'logout') {
    authStore.logout()
    router.push('/login')
  } else {
    router.push(`/${cmd}`)
  }
}

onMounted(() => {
  if (authStore.isLoggedIn && !authStore.user) {
    authStore.fetchUser()
  }
})
</script>
