<template>
  <TooltipProvider :delay-duration="200">
    <div class="flex min-h-screen flex-col bg-background">
      <!-- 顶栏 -->
      <header
        class="sticky top-0 z-50 border-b border-border/60 bg-background/80 backdrop-blur-xl"
      >
        <!-- 阅读进度：细金线随滚动伸长，长页面时给一点方向感 -->
        <div class="absolute inset-x-0 top-0 h-px overflow-hidden">
          <div
            class="h-px origin-left bg-gradient-to-r from-transparent via-ring to-ring/0 transition-transform duration-150 ease-out"
            :style="{ transform: `scaleX(${scrollProgress})` }"
          />
        </div>
        <div class="mx-auto flex h-14 w-full max-w-7xl items-center gap-3 px-4 sm:px-6">
          <!-- 品牌 -->
          <router-link to="/" class="flex shrink-0 items-center gap-2">
            <span
              class="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground"
            >
              <Sparkles class="size-4" />
            </span>
            <span class="hidden text-sm font-semibold tracking-tight sm:inline">
              AI 角色酒馆
            </span>
          </router-link>

          <!-- 主导航 -->
          <nav class="ml-2 hidden items-center gap-1 md:flex">
            <router-link
              v-for="item in primaryNav"
              :key="item.to"
              :to="item.to"
              class="rounded-full px-3 py-1.5 text-sm transition-colors"
              :class="
                isActive(item)
                  ? 'bg-secondary text-foreground'
                  : 'text-muted-foreground hover:text-foreground'
              "
            >
              {{ item.label }}
            </router-link>

            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <button
                  type="button"
                  class="flex items-center gap-1 rounded-full px-3 py-1.5 text-sm transition-colors"
                  :class="
                    isCreateActive
                      ? 'bg-secondary text-foreground'
                      : 'text-muted-foreground hover:text-foreground'
                  "
                >
                  创作
                  <ChevronDown class="size-3.5 opacity-60" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" class="w-48">
                <DropdownMenuItem
                  v-for="item in createNav"
                  :key="item.to"
                  class="cursor-pointer"
                  @select="router.push(item.to)"
                >
                  <component :is="item.icon" class="size-4 opacity-70" />
                  {{ item.label }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </nav>

          <div class="flex-1" />

          <!-- 主题切换：暗 / 亮跟随整套设计令牌，偏好存 localStorage -->
          <Tooltip>
            <TooltipTrigger as-child>
              <Button variant="ghost" size="icon-sm" @click="toggleTheme">
                <Sun v-if="theme === 'dark'" class="size-4" />
                <Moon v-else class="size-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{{ theme === 'dark' ? '切换到亮色' : '切换到暗色' }}</TooltipContent>
          </Tooltip>

          <!-- 右侧操作 -->
          <Tooltip>
            <TooltipTrigger as-child>
              <Button variant="ghost" size="icon-sm" @click="router.push('/search')">
                <Search class="size-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>搜索角色</TooltipContent>
          </Tooltip>

          <template v-if="authStore.isLoggedIn">
            <router-link
              to="/profile"
              class="hidden items-center gap-1.5 rounded-full border border-border/80 px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:border-border hover:text-foreground sm:flex"
            >
              <Coins class="size-3.5" />
              <span class="font-medium text-foreground">{{ formatNum(authStore.user?.points) }}</span>
            </router-link>

            <Tooltip>
              <TooltipTrigger as-child>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  class="relative"
                  @click="router.push('/notifications')"
                >
                  <Bell class="size-4" />
                  <span
                    class="absolute right-1.5 top-1.5 size-1.5 rounded-full bg-foreground"
                  />
                </Button>
              </TooltipTrigger>
              <TooltipContent>通知</TooltipContent>
            </Tooltip>

            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <button type="button" class="ml-1 rounded-full outline-none">
                  <Avatar class="size-8">
                    <AvatarImage :src="authStore.user?.avatar" :alt="displayName" />
                    <AvatarFallback class="text-xs">{{ displayName[0] }}</AvatarFallback>
                  </Avatar>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" class="w-52">
                <DropdownMenuLabel class="font-normal">
                  <p class="text-sm font-medium">{{ displayName }}</p>
                  <p class="text-xs text-muted-foreground">
                    {{ authStore.user?.username || '旅人' }}
                  </p>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  v-for="item in accountNav"
                  :key="item.to"
                  class="cursor-pointer"
                  @select="router.push(item.to)"
                >
                  <component :is="item.icon" class="size-4 opacity-70" />
                  {{ item.label }}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem class="cursor-pointer" @select="handleLogout">
                  <LogOut class="size-4 opacity-70" />
                  退出登录
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </template>

          <Button v-else size="sm" class="rounded-full" @click="router.push('/login')">
            登录
          </Button>

          <!-- 移动端菜单 -->
          <Sheet v-model:open="mobileOpen">
            <SheetTrigger as-child>
              <Button variant="ghost" size="icon-sm" class="md:hidden">
                <Menu class="size-4" />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" class="w-72">
              <SheetHeader>
                <SheetTitle>导航</SheetTitle>
                <SheetDescription class="sr-only">站点导航</SheetDescription>
              </SheetHeader>
              <nav class="mt-4 flex flex-col gap-1">
                <router-link
                  v-for="item in allMobileNav"
                  :key="item.to"
                  :to="item.to"
                  class="flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors"
                  :class="
                    isActive(item)
                      ? 'bg-secondary text-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                  "
                  @click="mobileOpen = false"
                >
                  <component :is="item.icon" class="size-4 opacity-70" />
                  {{ item.label }}
                </router-link>
              </nav>
            </SheetContent>
          </Sheet>
        </div>
      </header>

      <!-- fullBleed 的页面（如对话页）自己占满一屏，不要外层留白和页脚 -->
      <main
        class="w-full flex-1"
        :class="isFullBleed ? 'min-h-0' : 'mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8'"
      >
        <!-- 路由切换时轻微淡入上浮，避免整页硬切 -->
        <router-view v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </router-view>
      </main>

      <footer v-if="!isFullBleed" class="border-t border-border/60 py-6">
        <p class="text-center text-xs text-muted-foreground">
          AI 角色酒馆 · 用一句话开始一段故事
        </p>
      </footer>
    </div>
  </TooltipProvider>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  Bookmark,
  CalendarCheck,
  ChevronDown,
  Coins,
  Gauge,
  Hammer,
  LayoutGrid,
  LogOut,
  Menu,
  MessageSquare,
  Moon,
  PenLine,
  Search,
  Sparkles,
  Sun,
  TreePine,
  UserRound,
  Users,
} from 'lucide-vue-next'
import { theme, toggleTheme } from '../theme'
import { useAuthStore } from '../stores/auth'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const mobileOpen = ref(false)

/** 阅读进度（0~1）：页面滚到哪，顶部那条金线就伸到哪 */
const scrollProgress = ref(0)
let ticking = false

function updateProgress() {
  const el = document.documentElement
  const max = el.scrollHeight - el.clientHeight
  scrollProgress.value = max > 0 ? Math.min(1, Math.max(0, el.scrollTop / max)) : 0
  ticking = false
}

function onWindowScroll() {
  if (ticking) return
  ticking = true
  requestAnimationFrame(updateProgress)
}

const primaryNav = [
  { to: '/', label: '角色广场', name: 'Home' },
  { to: '/worlds', label: '世界广场', name: 'WorldPlaza' },
  { to: '/chat', label: '对话', name: 'Chat' },
  { to: '/community', label: '社区', name: 'Community' },
]

const createNav = [
  { to: '/create', label: '创建角色', name: 'CreateCharacter', icon: PenLine },
  { to: '/inspiration', label: '灵感记录', name: 'Inspiration', icon: Sparkles },
  { to: '/narrative', label: '叙事工坊', name: 'Narrative', icon: Bookmark },
  { to: '/world-tree', label: '世界树', name: 'WorldTree', icon: TreePine },
  { to: '/workshop', label: '创意工坊', name: 'Workshop', icon: Hammer },
  { to: '/metrics', label: '运行机制', name: 'Metrics', icon: Gauge },
]

const accountNav = [
  { to: '/profile', label: '个人资料', name: 'Profile', icon: UserRound },
  { to: '/mine', label: '我的角色', name: 'MyCharacters', icon: LayoutGrid },
  { to: '/my-worlds', label: '我的世界树', name: 'MyWorldTrees', icon: TreePine },
  { to: '/favorites', label: '我的收藏', name: 'Favorites', icon: Bookmark },
  { to: '/checkin', label: '每日签到', name: 'CheckIn', icon: CalendarCheck },
]

// 移动端把桌面端「主航 + 创作 + 账号」三组拍平成一个抽屉，顺序与桌面一致
const allMobileNav = [
  { to: '/', label: '角色广场', name: 'Home', icon: LayoutGrid },
  { to: '/worlds', label: '世界广场', name: 'WorldPlaza', icon: TreePine },
  { to: '/chat', label: '对话', name: 'Chat', icon: MessageSquare },
  { to: '/community', label: '社区', name: 'Community', icon: Users },
  ...createNav,
  ...accountNav,
  { to: '/notifications', label: '通知', name: 'Notifications', icon: Bell },
  { to: '/search', label: '搜索', name: 'Search', icon: Search },
]

const isFullBleed = computed(() => route.meta.fullBleed === true)

const isCreateActive = computed(() => createNav.some((item) => route.name === item.name))

const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '旅人')

/**
 * 高亮按路由名比对：`/` 是父级，用 router-link 的 active-class 会把
 * 所有页面都判定成「角色广场」激活。
 */
function isActive(item) {
  return item.name === route.name
}

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

onMounted(() => {
  if (authStore.isLoggedIn && !authStore.user) {
    authStore.fetchUser()
  }
  updateProgress()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  window.addEventListener('resize', onWindowScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onWindowScroll)
  window.removeEventListener('resize', onWindowScroll)
})
</script>
