<template>
  <div class="mx-auto max-w-5xl space-y-6">
    <!-- 首屏骨架 -->
    <div v-if="loading && !character" class="space-y-6">
      <div class="grid gap-6 rounded-xl border border-border/60 bg-card p-5 sm:grid-cols-[190px_1fr] sm:p-6">
        <Skeleton class="mx-auto aspect-[3/4] w-full max-w-[190px] rounded-xl sm:mx-0" />
        <div class="space-y-4">
          <Skeleton class="h-7 w-1/3" />
          <Skeleton class="h-4 w-2/3" />
          <Skeleton class="h-16 w-full" />
          <div class="flex gap-2">
            <Skeleton class="h-6 w-16 rounded-full" />
            <Skeleton class="h-6 w-16 rounded-full" />
            <Skeleton class="h-6 w-16 rounded-full" />
          </div>
          <Skeleton class="h-9 w-48 rounded-md" />
        </div>
      </div>
      <Skeleton class="h-48 rounded-xl" />
      <Skeleton class="h-64 rounded-xl" />
    </div>

    <template v-else-if="character">
      <!-- 角色主信息 -->
      <section class="grid gap-6 rounded-xl border border-border/60 bg-card p-5 sm:grid-cols-[190px_1fr] sm:p-6">
        <div class="mx-auto w-full max-w-[190px] sm:mx-0">
          <div class="aspect-[3/4] overflow-hidden rounded-xl border border-border/60 bg-muted">
            <img
              v-if="character.avatar"
              :src="character.avatar"
              :alt="character.name"
              class="size-full object-cover"
            />
            <div v-else class="grid size-full place-items-center text-4xl text-muted-foreground">
              {{ character.name?.[0] || '?' }}
            </div>
          </div>
        </div>

        <div class="min-w-0 space-y-4">
          <div class="space-y-2">
            <div class="flex flex-wrap items-start justify-between gap-2">
              <h1 class="text-2xl font-semibold tracking-tight">{{ character.name }}</h1>
              <Badge v-if="isOwner" variant="outline" class="font-normal text-muted-foreground">
                我的角色
              </Badge>
            </div>
            <p class="text-sm text-muted-foreground">
              创作者 {{ character.creatorName || '匿名' }} · {{ character.categoryName || '未分类' }}
            </p>
          </div>

          <p v-if="character.description" class="text-sm leading-relaxed text-muted-foreground">
            {{ character.description }}
          </p>

          <div v-if="character.tags?.length" class="flex flex-wrap gap-1.5">
            <Badge
              v-for="t in character.tags"
              :key="t.id || t.name"
              variant="outline"
              class="font-normal text-muted-foreground"
            >
              {{ t.name }}
            </Badge>
          </div>

          <dl class="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
            <div class="flex items-center gap-1.5">
              <MessageSquare class="size-3.5" />
              <span class="tabular-nums text-foreground">{{ character.chatCount || 0 }}</span>
              对话
            </div>
            <div class="flex items-center gap-1.5">
              <Bookmark class="size-3.5" />
              <span class="tabular-nums text-foreground">{{ character.favoriteCount || 0 }}</span>
              收藏
            </div>
            <div class="flex items-center gap-1.5">
              <Star class="size-3.5" />
              <span class="tabular-nums text-foreground">{{ ratingText }}</span>
              ({{ character.ratingCount || 0 }})
            </div>
          </dl>

          <div class="flex flex-wrap items-center gap-2">
            <Button @click="startChat">
              <MessageSquare class="size-4" />
              开始对话
            </Button>
            <Button variant="outline" @click="toggleFav">
              <Bookmark class="size-4" :class="character.favorited ? 'fill-current' : ''" />
              {{ character.favorited ? '已收藏' : '收藏' }}
            </Button>
            <Button v-if="isOwner" variant="outline" @click="goEdit">
              <Pencil class="size-4" />
              编辑调整
            </Button>
            <Button v-if="isOwner" variant="outline" @click="goWorldBook">
              <BookOpen class="size-4" />
              世界书
            </Button>
            <Button v-if="!isOwner" variant="outline" @click="onReplica">
              <Copy class="size-4" />
              复刻
            </Button>
          </div>

          <div class="flex items-center gap-1.5">
            <span class="mr-1 text-xs text-muted-foreground">我的评分</span>
            <button
              v-for="i in 5"
              :key="i"
              type="button"
              class="rounded-sm p-0.5 text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
              :aria-label="`${i} 星`"
              @click="onRate(i)"
            >
              <Star class="size-4" :class="i <= myScore ? 'fill-current text-foreground' : ''" />
            </button>
            <span v-if="myScore" class="ml-1 text-xs text-muted-foreground">已评 {{ myScore }} 星</span>
          </div>
        </div>
      </section>

      <!-- 人设与背景 -->
      <Card>
        <CardHeader>
          <CardTitle>人设与背景</CardTitle>
        </CardHeader>
        <CardContent class="space-y-4 text-sm leading-relaxed text-muted-foreground">
          <p v-if="character.personality" class="whitespace-pre-wrap">{{ character.personality }}</p>
          <p v-if="character.backgroundStory" class="whitespace-pre-wrap">
            {{ character.backgroundStory }}
          </p>
          <p v-if="!character.personality && !character.backgroundStory" class="text-muted-foreground">
            作者还没有补充人设资料。
          </p>
          <blockquote
            v-if="character.greeting"
            class="rounded-lg border-l-2 border-border bg-muted/40 px-4 py-3 text-sm italic text-muted-foreground"
          >
            <span class="not-italic text-xs text-muted-foreground">开场白</span>
            <p class="mt-1 whitespace-pre-wrap">{{ character.greeting }}</p>
          </blockquote>
        </CardContent>
      </Card>

      <!-- 评论 -->
      <Card>
        <CardHeader>
          <CardTitle>评论</CardTitle>
          <CardDescription>{{ comments.length }} 条</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <form
            v-if="authStore.isLoggedIn"
            class="flex flex-col gap-2 sm:flex-row sm:items-start"
            @submit.prevent="submitComment"
          >
            <Textarea
              v-model="commentText"
              :rows="2"
              placeholder="写下你的感受..."
              class="flex-1 resize-none"
            />
            <Button type="submit" :disabled="!commentText.trim()">发送</Button>
          </form>

          <div v-if="comments.length" class="divide-y divide-border/60">
            <article
              v-for="c in comments"
              :key="c.id"
              class="flex gap-3 py-3.5 first:pt-0 last:pb-0"
            >
              <Avatar class="size-7 shrink-0">
                <AvatarImage v-if="c.avatar" :src="c.avatar" :alt="c.nickname" />
                <AvatarFallback class="text-[10px]">{{ c.nickname?.[0] || '?' }}</AvatarFallback>
              </Avatar>
              <div class="min-w-0 flex-1">
                <p class="text-sm">{{ c.nickname || '匿名旅人' }}</p>
                <p class="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-muted-foreground">
                  {{ c.content }}
                </p>
              </div>
            </article>
          </div>
          <EmptyState v-else text="还没有评论" hint="说点什么，让作者知道你的感受" />
        </CardContent>
      </Card>
    </template>

    <EmptyState v-else text="角色不存在或已下架" hint="也许作者暂时收回了它">
      <Button variant="outline" size="sm" class="mt-3" @click="router.push('/')">返回广场</Button>
    </EmptyState>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Bookmark, BookOpen, Copy, MessageSquare, Pencil, Star } from 'lucide-vue-next'
import {
  getCharacterDetail, favoriteCharacter, rateCharacter,
  getComments, addComment, cloneCharacter,
} from '../api/character'
import { createSession, getSessions } from '../api/chat'
import { useAuthStore } from '../stores/auth'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const character = ref(null)
const myScore = ref(0)
const comments = ref([])
const commentText = ref('')

const isOwner = computed(() => {
  if (!character.value || !authStore.user) return false
  if (character.value.ownedByMe) return true
  return Number(character.value.creatorId) === Number(authStore.user.id)
})

const ratingText = computed(() => Number(character.value?.rating || 0).toFixed(1))

async function load() {
  loading.value = true
  try {
    character.value = await getCharacterDetail(route.params.id)
    myScore.value = character.value.myScore || 0
    const page = await getComments(route.params.id, { page: 1, size: 50 })
    comments.value = page.list || page || []
  } finally {
    loading.value = false
  }
}

function goEdit() {
  router.push(`/edit/${route.params.id}`)
}

function goWorldBook() {
  router.push(`/character/${route.params.id}/worldbook`)
}

async function startChat() {
  if (!authStore.isLoggedIn) return router.push('/login')
  const characterId = Number(route.params.id)
  const sessions = (await getSessions()) || []
  const existing = sessions.find((s) => Number(s.characterId ?? s.character_id) === characterId)
  if (existing) {
    router.push(`/chat/${existing.id}`)
    return
  }
  const session = await createSession({ characterId })
  router.push(`/chat/${session.id}`)
}

async function toggleFav() {
  if (!authStore.isLoggedIn) return router.push('/login')
  const res = await favoriteCharacter(route.params.id)
  character.value.favorited = res.favorited
  toast.success(res.favorited ? '已收藏' : '已取消收藏')
}

async function onRate(score) {
  if (!authStore.isLoggedIn) return router.push('/login')
  if (score === myScore.value) return
  myScore.value = score
  const res = await rateCharacter(route.params.id, { score })
  character.value.rating = res.rating
  character.value.ratingCount = res.ratingCount
  toast.success('评分成功')
}

async function onReplica() {
  if (!authStore.isLoggedIn) return router.push('/login')
  const copy = await cloneCharacter(route.params.id)
  toast.success('已复刻为草稿')
  router.push(`/edit/${copy.id}`)
}

async function submitComment() {
  if (!commentText.value.trim()) return
  await addComment(route.params.id, { content: commentText.value })
  commentText.value = ''
  await load()
}

onMounted(load)
</script>
