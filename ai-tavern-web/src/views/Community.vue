<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div class="space-y-2">
        <h1 class="text-2xl font-semibold tracking-tight">社区广场</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          别人的改写都会聚在这里。点赞收藏都留痕，评论一分钟限三条。
        </p>
      </div>
      <div class="flex items-center gap-1.5">
        <Button
          v-for="s in sorts"
          :key="s.value"
          :variant="sort === s.value ? 'secondary' : 'ghost'"
          size="sm"
          class="rounded-full"
          @click="changeSort(s.value)"
        >
          {{ s.label }}
        </Button>
        <span class="ml-1 text-xs text-muted-foreground">共 {{ total }} 篇</span>
      </div>
    </header>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Skeleton v-for="i in 6" :key="i" class="h-44 rounded-xl" />
    </div>

    <div v-else-if="works.length" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Card
        v-for="w in works"
        :key="w.id"
        class="cursor-pointer gap-3 py-4 transition-colors hover:border-border"
        @click="open(w)"
      >
        <CardContent class="flex h-full flex-col gap-3">
          <div class="flex items-start justify-between gap-2">
            <h2 class="line-clamp-2 text-base font-medium leading-snug">{{ w.title }}</h2>
            <Badge v-if="w.formType" variant="outline" class="shrink-0 font-normal">
              {{ formLabel(w.formType) }}
            </Badge>
          </div>

          <p v-if="w.summary" class="line-clamp-3 text-sm leading-relaxed text-muted-foreground">
            {{ w.summary }}
          </p>

          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <Avatar class="size-5">
              <AvatarImage :src="w.characterAvatar" :alt="w.characterName" />
              <AvatarFallback class="text-[9px]">{{ (w.characterName || '?')[0] }}</AvatarFallback>
            </Avatar>
            <span class="truncate">{{ w.characterName || '未知角色' }}</span>
            <span class="opacity-50">·</span>
            <span class="truncate">{{ w.nickname || '匿名旅人' }}</span>
          </div>

          <div class="mt-auto flex items-center gap-1 border-t border-border/60 pt-2.5 text-xs text-muted-foreground">
            <button
              type="button"
              class="flex items-center gap-1 rounded-md px-1.5 py-0.5 transition-colors hover:bg-accent hover:text-foreground"
              :class="w.liked ? 'text-foreground' : ''"
              @click.stop="onLike(w)"
            >
              <Heart class="size-3.5" :class="w.liked ? 'fill-current' : ''" />
              {{ w.likeCount || 0 }}
            </button>
            <button
              type="button"
              class="flex items-center gap-1 rounded-md px-1.5 py-0.5 transition-colors hover:bg-accent hover:text-foreground"
              :class="w.collected ? 'text-foreground' : ''"
              @click.stop="onCollect(w)"
            >
              <Star class="size-3.5" :class="w.collected ? 'fill-current' : ''" />
              {{ w.collectCount || 0 }}
            </button>
            <span class="flex items-center gap-1 px-1.5 py-0.5">
              <MessageCircle class="size-3.5" />
              {{ w.commentCount || 0 }}
            </span>
            <span class="ml-auto text-[11px] opacity-70">{{ w.wordCount || 0 }} 字</span>
          </div>
        </CardContent>
      </Card>
    </div>

    <EmptyState v-else text="还没有人发布作品" hint="把你的对话改写一篇，发布到这里">
      <Button class="mt-2" size="sm" @click="$router.push('/narrative')">去写一篇</Button>
    </EmptyState>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="flex items-center justify-center gap-3">
      <Button variant="outline" size="sm" :disabled="page <= 1" @click="goPage(page - 1)">
        <ChevronLeft class="size-4" />
        上一页
      </Button>
      <span class="text-xs text-muted-foreground">{{ page }} / {{ totalPages }}</span>
      <Button variant="outline" size="sm" :disabled="page >= totalPages" @click="goPage(page + 1)">
        下一页
        <ChevronRight class="size-4" />
      </Button>
    </div>

    <!-- 阅读器 -->
    <Sheet v-model:open="readerOpen">
      <SheetContent side="right" class="flex w-full flex-col gap-0 p-0 sm:max-w-2xl">
        <div v-if="current" class="flex h-full flex-col">
          <SheetHeader class="border-b border-border/60 px-6 py-4">
            <div class="flex items-start justify-between gap-3">
              <SheetTitle class="text-lg leading-snug">{{ current.title }}</SheetTitle>
              <Badge v-if="current.formType" variant="outline" class="shrink-0 font-normal">
                {{ formLabel(current.formType) }}
              </Badge>
            </div>
            <SheetDescription class="text-xs">
              {{ current.characterName || '未知角色' }} · {{ current.nickname || '匿名旅人' }} ·
              {{ current.wordCount || 0 }} 字
            </SheetDescription>
          </SheetHeader>

          <div class="min-h-0 flex-1 space-y-4 overflow-y-auto px-6 py-4">
            <div class="flex items-center gap-1.5">
              <Button
                :variant="current.liked ? 'secondary' : 'outline'"
                size="sm"
                @click="onLike(current)"
              >
                <Heart class="size-4" :class="current.liked ? 'fill-current' : ''" />
                {{ current.likeCount || 0 }}
              </Button>
              <Button
                :variant="current.collected ? 'secondary' : 'outline'"
                size="sm"
                @click="onCollect(current)"
              >
                <Star class="size-4" :class="current.collected ? 'fill-current' : ''" />
                {{ current.collectCount || 0 }}
              </Button>
            </div>

            <div class="chat-md text-sm leading-loose" v-html="renderMarkdown(current.content)" />

            <section class="space-y-3 border-t border-border/60 pt-4">
              <h3 class="text-sm font-medium">评论（{{ comments.length }}）</h3>

              <div class="flex items-end gap-2">
                <Textarea
                  v-model="draft"
                  :rows="2"
                  maxlength="500"
                  placeholder="说点什么…"
                  class="flex-1"
                />
                <Button :disabled="submitting" @click="submitComment">
                  {{ submitting ? '发送中…' : '发送' }}
                </Button>
              </div>

              <p v-if="!comments.length" class="text-xs text-muted-foreground">还没有评论。</p>

              <ul v-else class="space-y-3">
                <li v-for="c in comments" :key="c.id" class="flex gap-2">
                  <Avatar class="size-7 shrink-0">
                    <AvatarImage :src="c.avatar" :alt="c.nickname" />
                    <AvatarFallback class="text-[10px]">{{ (c.nickname || '?')[0] }}</AvatarFallback>
                  </Avatar>
                  <div class="min-w-0 flex-1">
                    <p class="text-xs text-muted-foreground">
                      {{ c.nickname || '匿名旅人' }}
                      <span class="ml-1 opacity-60">{{ formatTime(c.createdAt) }}</span>
                    </p>
                    <p class="mt-0.5 whitespace-pre-wrap break-words text-sm leading-relaxed">
                      {{ c.content }}
                    </p>
                  </div>
                  <Button
                    v-if="c.userId === myId"
                    variant="ghost"
                    size="xs"
                    class="shrink-0 text-muted-foreground"
                    @click="removeComment(c)"
                  >
                    删除
                  </Button>
                </li>
              </ul>
            </section>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { ChevronLeft, ChevronRight, Heart, MessageCircle, Star } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import {
  addWorkComment,
  deleteWorkComment,
  getCommunityWorks,
  getWorkComments,
  toggleWorkCollect,
  toggleWorkLike,
} from '../api/community'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import EmptyState from '../components/EmptyState.vue'

const auth = useAuthStore()
const myId = computed(() => auth.user?.id ?? null)

const sorts = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '最热' },
]
const sort = ref('latest')
const page = ref(1)
const pageSize = 12
const total = ref(0)
const works = ref([])
const loading = ref(false)

const readerOpen = ref(false)
const current = ref(null)
const comments = ref([])
const draft = ref('')
const submitting = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

/**
 * 接口经 axios 拦截器已经拆过一层 Result，这里拿到的就是业务数据本身，
 * 不要再取 .data（旧代码多解构一层，导致广场永远是空的）。
 */
async function load() {
  loading.value = true
  try {
    const res = await getCommunityWorks(sort.value, page.value, pageSize)
    works.value = res?.list || []
    total.value = res?.total || 0
  } catch (e) {
    toast.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function changeSort(value) {
  if (sort.value === value) return
  sort.value = value
  page.value = 1
  load()
}

function goPage(p) {
  page.value = p
  load()
}

async function open(w) {
  current.value = w
  readerOpen.value = true
  comments.value = []
  try {
    const res = await getWorkComments(w.id)
    comments.value = res?.list || []
  } catch {
    /* 评论加载失败不挡住正文阅读 */
  }
}

/** 点赞/收藏返回的是切换后的状态，就地回写避免整页刷新 */
function applyToggle(w, res) {
  if (res?.liked !== undefined) w.liked = res.liked
  if (res?.likeCount !== undefined) w.likeCount = res.likeCount
  if (res?.collected !== undefined) w.collected = res.collected
  if (res?.collectCount !== undefined) w.collectCount = res.collectCount
}

async function onLike(w) {
  try {
    const res = await toggleWorkLike(w.id)
    applyToggle(w, res)
  } catch (e) {
    toast.error(e.message || '操作失败')
  }
}

async function onCollect(w) {
  try {
    const res = await toggleWorkCollect(w.id)
    applyToggle(w, res)
    toast.success(res?.collected ? '已收藏' : '已取消收藏')
  } catch (e) {
    toast.error(e.message || '操作失败')
  }
}

async function submitComment() {
  const text = draft.value.trim()
  if (!text) {
    toast.warning('评论不能为空')
    return
  }
  submitting.value = true
  try {
    const created = await addWorkComment(current.value.id, text)
    draft.value = ''
    comments.value.unshift(created)
    current.value.commentCount = (current.value.commentCount || 0) + 1
  } catch (e) {
    // 频控和敏感词拦截都在后端，这里直接把原因透给用户
    toast.error(e.message || '发表失败')
  } finally {
    submitting.value = false
  }
}

async function removeComment(c) {
  try {
    await deleteWorkComment(c.id)
    comments.value = comments.value.filter((x) => x.id !== c.id)
    if (current.value?.commentCount) current.value.commentCount -= 1
  } catch (e) {
    toast.error(e.message || '删除失败')
  }
}

function formLabel(t) {
  return { novel: '小说', script: '剧本', outline: '大纲' }[t] || t
}

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>
