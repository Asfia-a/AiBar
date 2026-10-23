<template>
  <div class="mx-auto max-w-2xl space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-semibold tracking-tight">通知</h1>
        <p class="text-sm text-muted-foreground">
          {{ unreadCount ? `${unreadCount} 条未读` : '没有未读通知' }}
        </p>
      </div>
      <Button variant="ghost" size="sm" :disabled="!unreadCount || loading" @click="readAll">
        <CheckCheck class="size-4" />
        全部已读
      </Button>
    </header>

    <div v-if="loading" class="space-y-2">
      <Skeleton v-for="i in 5" :key="i" class="h-[72px] rounded-xl" />
    </div>

    <div
      v-else-if="list.length"
      class="divide-y divide-border/60 overflow-hidden rounded-xl border border-border/60 bg-card"
    >
      <button
        v-for="n in list"
        :key="n.id"
        type="button"
        class="flex w-full items-start gap-3 px-4 py-3.5 text-left transition-colors hover:bg-accent/50"
        :class="isUnread(n) ? 'bg-secondary/40' : ''"
        @click="readOne(n)"
      >
        <span
          class="mt-1.5 size-1.5 shrink-0 rounded-full"
          :class="isUnread(n) ? 'bg-foreground' : 'bg-transparent'"
        />
        <span class="min-w-0 flex-1 space-y-1">
          <span class="flex items-baseline justify-between gap-3">
            <span
              class="min-w-0 truncate text-sm"
              :class="isUnread(n) ? 'font-medium' : 'text-muted-foreground'"
            >
              {{ n.title }}
            </span>
            <span class="shrink-0 text-[11px] tabular-nums text-muted-foreground">
              {{ formatTime(n.createdAt) }}
            </span>
          </span>
          <span class="line-clamp-2 block text-xs leading-relaxed text-muted-foreground">
            {{ n.content }}
          </span>
        </span>
      </button>
    </div>

    <EmptyState v-else text="暂无通知" hint="有新消息时会出现在这里" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { CheckCheck } from 'lucide-vue-next'
import { getNotifications, markRead, markAllRead } from '../api/notification'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import EmptyState from '../components/EmptyState.vue'

const list = ref([])
const loading = ref(false)

const unreadCount = computed(() => list.value.filter(isUnread).length)

function isUnread(n) {
  return !n.isRead
}

/** 后端时间可能是 ISO 或 "yyyy-MM-dd HH:mm:ss"，都按本地时间处理 */
function formatTime(value) {
  if (!value) return ''
  const raw = String(value)
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return raw
  const diff = Date.now() - date.getTime()
  if (diff >= 0 && diff < 60 * 1000) return '刚刚'
  if (diff >= 0 && diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`
  if (diff >= 0 && diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const page = await getNotifications({ page: 1, size: 50 })
    list.value = page.list || []
  } finally {
    loading.value = false
  }
}

async function readOne(n) {
  if (!n.isRead) {
    await markRead(n.id)
    n.isRead = 1
  }
}

async function readAll() {
  await markAllRead()
  toast.success('已全部标记为已读')
  await load()
}

onMounted(load)
</script>
