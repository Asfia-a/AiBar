<template>
  <div class="flex h-[calc(100dvh-3.5rem)] w-full">
    <!-- 会话侧栏 -->
    <aside class="hidden w-64 shrink-0 flex-col border-r border-border/60 md:flex">
      <div class="flex h-14 items-center justify-between px-4">
        <h2 class="text-sm font-medium">历史会话</h2>
        <span class="text-xs text-muted-foreground">{{ sessions.length }}</span>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        <SessionList :sessions="sessions" :current-id="sessionId" @select="openSession" />
      </div>

      <div class="border-t border-border/60 p-2">
        <Button variant="outline" size="sm" class="w-full" @click="router.push('/')">
          <Plus class="size-4" />
          开始新对话
        </Button>
      </div>
    </aside>

    <!-- 主对话区 -->
    <section class="flex min-w-0 flex-1 flex-col">
      <!-- 会话头 -->
      <header class="flex h-14 shrink-0 items-center gap-3 border-b border-border/60 px-4">
        <Button
          variant="ghost"
          size="icon-sm"
          class="md:hidden"
          title="历史会话"
          @click="mobileSessionsOpen = true"
        >
          <PanelLeft class="size-4" />
        </Button>

        <Avatar class="size-8 shrink-0">
          <AvatarImage :src="bgUrl" :alt="characterName" />
          <AvatarFallback class="text-xs">{{ characterName[0] }}</AvatarFallback>
        </Avatar>

        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium">{{ characterName }}</p>
          <p class="truncate text-xs text-muted-foreground">
            积分 <span class="text-foreground">{{ formatNum(pointsBalance) }}</span>
            · 约 {{ tokensPerPoint }} Token / 积分
          </p>
        </div>

        <Button
          v-if="characterId"
          variant="ghost"
          size="icon-sm"
          :title="favorited ? '取消收藏' : '收藏角色'"
          @click="toggleFav"
        >
          <Heart class="size-4" :class="favorited ? 'fill-foreground text-foreground' : ''" />
        </Button>

        <Button variant="ghost" size="sm" class="hidden sm:inline-flex" @click="rechargeOpen = true">
          <Coins class="size-4" />
          充值
        </Button>

        <!-- 作者注释生效时给个显式标记，否则用户会忘记自己设过 -->
        <Button
          v-if="sessionId && hasAuthorNote"
          variant="ghost"
          size="icon-sm"
          title="作者注释生效中"
          @click="authorNoteOpen = true"
        >
          <StickyNote class="size-4" />
        </Button>

        <DropdownMenu v-if="sessionId">
          <DropdownMenuTrigger as-child>
            <Button variant="ghost" size="icon-sm">
              <MoreHorizontal class="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem class="cursor-pointer" @select="authorNoteOpen = true">
              <StickyNote class="size-4 opacity-70" />
              作者注释
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="removeSession">
              <Trash2 class="size-4 opacity-70" />
              删除这段会话
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </header>

      <!-- 消息区 -->
      <div
        ref="listRef"
        class="min-h-0 flex-1 overflow-y-auto"
        @scroll="onScroll"
      >
        <div v-if="!messages.length && !streaming" class="flex h-full flex-col items-center justify-center px-6 text-center">
          <span class="grid size-12 place-items-center rounded-2xl bg-secondary">
            <Sparkles class="size-5 text-muted-foreground" />
          </span>
          <p class="mt-4 text-lg font-medium">
            {{ sessionId ? `向「${characterName}」说第一句话吧` : '开始一段新故事' }}
          </p>
          <p class="mt-1.5 max-w-sm text-sm text-muted-foreground">
            {{
              sessionId
                ? '你的每一句话都会被记住，也会按 Token 计入积分。'
                : '从角色广场挑一个角色进入对话，或从左侧打开历史会话。'
            }}
          </p>
          <Button v-if="!sessionId" variant="outline" class="mt-5" @click="router.push('/')">
            去角色广场
          </Button>
        </div>

        <div v-else class="mx-auto w-full max-w-3xl space-y-6 px-4 py-6">
          <div
            v-for="m in messages"
            :key="m.id || m._tmp"
            class="group flex gap-3"
            :class="m.role === 'user' ? 'flex-row-reverse' : ''"
          >
            <!-- 头像：助手用角色头像，用户用自己 -->
            <Avatar class="mt-0.5 size-7 shrink-0">
              <template v-if="m.role === 'user'">
                <AvatarImage :src="authStore.user?.avatar" :alt="userName" />
                <AvatarFallback class="text-[10px]">{{ userName[0] }}</AvatarFallback>
              </template>
              <template v-else>
                <AvatarImage :src="bgUrl" :alt="characterName" />
                <AvatarFallback class="text-[10px]">{{ characterName[0] }}</AvatarFallback>
              </template>
            </Avatar>

            <div class="flex min-w-0 max-w-[85%] flex-col" :class="m.role === 'user' ? 'items-end' : 'items-start'">
              <div
                class="rounded-2xl px-4 py-2.5 text-sm leading-relaxed break-words"
                :class="
                  m.role === 'user'
                    ? 'whitespace-pre-wrap rounded-tr-md bg-secondary text-secondary-foreground'
                    : 'rounded-tl-md border border-border/60 bg-card'
                "
              >
                <span v-if="m.role === 'user'">{{ m.content }}</span>
                <!-- 模型输出带 markdown，解析后渲染；原始 HTML 已在 renderMarkdown 中丢弃 -->
                <div v-else class="chat-md" v-html="renderMarkdown(m.content)" />
                <span v-if="m._streaming" class="ml-0.5 inline-block animate-pulse">▍</span>
              </div>

              <!-- 悬停时出现的消息操作 -->
              <div
                class="mt-1 flex flex-wrap items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100"
                :class="m.role === 'user' ? 'flex-row-reverse' : ''"
              >
                <Button variant="ghost" size="icon-xs" title="复制" @click="copyMessage(m)">
                  <Copy class="size-3" />
                </Button>

                <!-- 多版本：左右切，右箭头到头了就用「再生成一版」 -->
                <template v-if="m.role === 'assistant' && m.id && !streaming">
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    title="上一版"
                    :disabled="(m.swipeIndex || 0) <= 0"
                    @click="switchSwipe(m, (m.swipeIndex || 0) - 1)"
                  >
                    <ChevronLeft class="size-3" />
                  </Button>
                  <span
                    class="min-w-7 text-center text-[10px] tabular-nums text-muted-foreground"
                    :title="`共 ${m.swipeCount || 1} 版`"
                  >
                    {{ (m.swipeIndex || 0) + 1 }}/{{ m.swipeCount || 1 }}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    title="下一版"
                    :disabled="(m.swipeIndex || 0) >= (m.swipeCount || 1) - 1"
                    @click="switchSwipe(m, (m.swipeIndex || 0) + 1)"
                  >
                    <ChevronRight class="size-3" />
                  </Button>
                  <Button variant="ghost" size="icon-xs" title="再生成一版" @click="newSwipe(m)">
                    <RotateCcw class="size-3" />
                  </Button>
                </template>

                <Button
                  v-if="m.id && !streaming"
                  variant="ghost"
                  size="icon-xs"
                  title="编辑"
                  @click="openEdit(m)"
                >
                  <Pencil class="size-3" />
                </Button>
                <Button
                  v-if="m.id && !streaming"
                  variant="ghost"
                  size="icon-xs"
                  title="从这里分支"
                  @click="branch(m)"
                >
                  <GitBranch class="size-3" />
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="shrink-0 border-t border-border/60 bg-background/60 px-4 py-3 backdrop-blur">
        <div class="mx-auto w-full max-w-3xl">
          <!-- 上下文占用条：把后端的窗口裁剪显式画出来 -->
          <div v-if="ctxMeta" class="mb-2">
            <div class="flex h-1 w-full overflow-hidden rounded-full bg-muted">
              <div class="bg-foreground/80" :style="{ width: `${ctxSysPct}%` }" title="角色人设提示" />
              <div class="bg-foreground/45" :style="{ width: `${ctxDiaPct}%` }" title="历史对话" />
              <div class="bg-foreground/15" :style="{ width: `${ctxOutPct}%` }" title="输出预留" />
            </div>
            <p class="mt-1 text-[10px] text-muted-foreground">
              上下文 {{ ctxMeta.usedRounds }}/{{ ctxMeta.window }} 轮
              <span v-if="ctxMeta.droppedRounds > 0" class="text-foreground/70">
                · 已丢弃 {{ ctxMeta.droppedRounds }} 条更早的消息
              </span>
              <span class="opacity-70"> · 约 {{ ctxMeta.estimatedTokens }} tokens 输入</span>
            </p>
          </div>

          <div class="rounded-2xl border border-border/80 bg-card p-2 focus-within:border-ring/60">
            <textarea
              ref="inputRef"
              v-model="input"
              rows="1"
              placeholder="说点什么… Enter 发送，Shift + Enter 换行"
              class="max-h-40 w-full resize-none bg-transparent px-2 py-1.5 text-sm outline-none placeholder:text-muted-foreground"
              @input="autoResize"
              @keydown.enter.exact.prevent="send"
            />
            <div class="flex items-center justify-between gap-2 px-1 pt-1">
              <span class="text-[11px] text-muted-foreground">
                {{ streaming ? '对方正在回应…' : ctxMeta ? `上下文约 ${ctxMeta.estimatedTokens} tokens` : '温柔一点，角色会记得' }}
              </span>
              <Button
                v-if="streaming"
                variant="secondary"
                size="sm"
                class="rounded-full"
                @click="stopGenerating"
              >
                <Square class="size-3.5" />
                停止
              </Button>
              <Button
                v-else
                size="icon-sm"
                class="rounded-full"
                :disabled="!input.trim()"
                title="发送"
                @click="send"
              >
                <ArrowUp class="size-4" />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 移动端会话抽屉 -->
    <Sheet v-model:open="mobileSessionsOpen">
      <SheetContent side="left" class="w-72 p-0">
        <SheetHeader class="border-b border-border/60 px-4 py-3">
          <SheetTitle class="text-sm">历史会话</SheetTitle>
          <SheetDescription class="sr-only">选择会话</SheetDescription>
        </SheetHeader>
        <div class="flex h-[calc(100dvh-3.5rem)] flex-col">
          <div class="min-h-0 flex-1 overflow-y-auto px-2 py-2">
            <SessionList :sessions="sessions" :current-id="sessionId" @select="openSessionFromDrawer" />
          </div>
          <div class="border-t border-border/60 p-2">
            <Button variant="outline" size="sm" class="w-full" @click="router.push('/')">
              <Plus class="size-4" />
              开始新对话
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>

    <MockRechargeDialog v-model="rechargeOpen" prefer="basic" @success="onRecharged" />

    <AuthorNotePanel v-model:open="authorNoteOpen" :session-id="sessionId" @saved="refreshSessions" />

    <!-- 编辑消息：改文本不重新生成，也不额外扣积分 -->
    <Dialog v-model:open="editOpen">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>编辑消息</DialogTitle>
          <DialogDescription>
            保存后立即生效，不会重新问模型，也不额外扣积分。AI 那边不会自动知道这次修改。
          </DialogDescription>
        </DialogHeader>
        <Textarea
          v-model="editText"
          :rows="8"
          class="mt-2 resize-y text-[13px] leading-relaxed"
          placeholder="改写这段内容…"
        />
        <DialogFooter class="mt-4 flex-row justify-end gap-2">
          <Button variant="outline" @click="editOpen = false">取消</Button>
          <Button :disabled="editSaving || !editText.trim()" @click="submitEdit">
            <Loader2 v-if="editSaving" class="size-4 animate-spin" />
            <Check v-else class="size-4" />
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  ArrowUp,
  Check,
  ChevronLeft,
  ChevronRight,
  Coins,
  Copy,
  GitBranch,
  Heart,
  Loader2,
  MoreHorizontal,
  PanelLeft,
  Pencil,
  Plus,
  RotateCcw,
  Sparkles,
  Square,
  StickyNote,
  Trash2,
} from 'lucide-vue-next'
import {
  branchSession,
  createSession,
  deleteSession,
  editMessage,
  getMessages,
  getSessions,
  selectSwipe,
  streamChat,
  streamSwipe,
} from '../api/chat'
import { favoriteCharacter, getCharacterDetail } from '../api/character'
import { getTokenBalance } from '../api/user'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import MockRechargeDialog from '../components/MockRechargeDialog.vue'
import AuthorNotePanel from '../components/chat/AuthorNotePanel.vue'
import SessionList from '../components/chat/SessionList.vue'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessions = ref([])
const messages = ref([])
const input = ref('')
const streaming = ref(false)
/** 后端 meta 事件带来的上下文裁剪统计，用于输入框上方的占用条 */
const ctxMeta = ref(null)
const listRef = ref(null)
const inputRef = ref(null)
const favorited = ref(false)
const favLoading = ref(false)
const pointsBalance = ref(0)
const tokensPerPoint = ref(1000)
const rechargeOpen = ref(false)
const mobileSessionsOpen = ref(false)
const authorNoteOpen = ref(false)
const editOpen = ref(false)
const editTarget = ref(null)
const editText = ref('')
const editSaving = ref(false)
/** 用户是否贴在底部：贴底才自动跟随流式输出，否则不打断向上翻阅 */
const stickToBottom = ref(true)
let suppressLoad = false
let abortCtrl = null

const sessionId = computed(() => route.params.sessionId)
const current = computed(() => sessions.value.find((s) => isCurrent(s.id)))

const characterId = computed(() => {
  const id = current.value?.characterId ?? current.value?.character_id ?? route.query.characterId
  return id != null && id !== '' ? Number(id) : null
})

const characterName = computed(
  () => current.value?.characterName || current.value?.title || '酒馆',
)

const userName = computed(() => authStore.user?.nickname || authStore.user?.username || '我')

/** 会话列表里已经带了 author_note / an_frequency，直接用，不额外请求 */
const hasAuthorNote = computed(() => {
  const s = current.value
  if (!s) return false
  const note = s.authorNote ?? s.author_note
  const freq = s.anFrequency ?? s.an_frequency
  return !!note && String(note).trim() !== '' && (freq == null || Number(freq) > 0)
})

const bgUrl = computed(() => {
  const url = current.value?.characterAvatar || current.value?.character_avatar || ''
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : ''
})

/**
 * 占用条三段宽度。分母是「输入估算 + 输出上限」，即模型这次能吃下的总量。
 * 超出部分会触发裁剪，所以这个比例就是上下文压力的直接体现。
 */
const ctxBudget = computed(() => {
  const m = ctxMeta.value
  if (!m) return 1
  return (m.estimatedTokens || 0) + (m.maxTokens || 500) || 1
})
const ctxSysPct = computed(() =>
  ctxMeta.value ? Math.min(Math.round(((ctxMeta.value.systemChars || 0) / 1.5 / ctxBudget.value) * 100), 100) : 0,
)
const ctxDiaPct = computed(() =>
  ctxMeta.value
    ? Math.min(
        Math.round((((ctxMeta.value.dialogueChars || 0) / 1.5) / ctxBudget.value) * 100),
        100 - ctxSysPct.value,
      )
    : 0,
)
const ctxOutPct = computed(() =>
  ctxMeta.value
    ? Math.min(
        Math.round(((ctxMeta.value.maxTokens || 0) / ctxBudget.value) * 100),
        100 - ctxSysPct.value - ctxDiaPct.value,
      )
    : 0,
)

function isCurrent(id) {
  return String(id) === String(sessionId.value)
}

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

async function refreshTokenBalance() {
  try {
    const b = await getTokenBalance()
    pointsBalance.value = Number(b.points || 0)
    tokensPerPoint.value = Number(b.tokensPerPoint || 1000)
    if (authStore.user) {
      authStore.user.points = pointsBalance.value
      authStore.user.tokenBalance = b.tokenBalance
    }
  } catch {
    /* 余额拉取失败不阻塞对话 */
  }
}

async function onRecharged(res) {
  if (res?.pointsBalance != null) pointsBalance.value = Number(res.pointsBalance)
  else if (res?.balance != null) pointsBalance.value = Number(res.balance)
  else await refreshTokenBalance()
  await authStore.fetchUser()
}

async function refreshSessions() {
  sessions.value = (await getSessions()) || []
}

async function loadFavoriteState() {
  if (!characterId.value) {
    favorited.value = false
    return
  }
  try {
    const c = await getCharacterDetail(characterId.value)
    favorited.value = !!c?.favorited
  } catch {
    favorited.value = false
  }
}

async function toggleFav() {
  if (!characterId.value || favLoading.value) return
  favLoading.value = true
  try {
    const res = await favoriteCharacter(characterId.value)
    favorited.value = !!res?.favorited
    toast.success(favorited.value ? '已收藏' : '已取消收藏')
  } catch (e) {
    toast.error(e.message || '操作失败')
  } finally {
    favLoading.value = false
  }
}

async function loadMessages() {
  if (!sessionId.value) {
    if (!streaming.value) messages.value = []
    return
  }
  if (streaming.value || suppressLoad) return
  try {
    const list = await getMessages(sessionId.value)
    if (streaming.value || suppressLoad) return
    messages.value = Array.isArray(list) ? list : []
    stickToBottom.value = true
    await scrollBottom()
  } catch (e) {
    console.warn('loadMessages failed', e)
  }
}

function openSession(id) {
  if (streaming.value) {
    toast.warning('请等待当前回复完成')
    return
  }
  router.push(`/chat/${id}`)
}

function openSessionFromDrawer(id) {
  mobileSessionsOpen.value = false
  openSession(id)
}

async function ensureSession(cid) {
  const list = sessions.value.length ? sessions.value : ((await getSessions()) || [])
  sessions.value = list
  const existing = list.find((s) => Number(s.characterId ?? s.character_id) === Number(cid))
  if (existing) return existing
  return createSession({ characterId: Number(cid) })
}

function appendByTmp(tmpId, chunk) {
  if (!chunk) return
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx < 0) return
  const cur = messages.value[idx]
  messages.value[idx] = { ...cur, content: (cur.content || '') + chunk, _streaming: true }
  if (stickToBottom.value) scrollBottom()
}

function stopStreamingFlag(tmpId) {
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx >= 0) {
    messages.value[idx] = { ...messages.value[idx], _streaming: false }
  }
  streaming.value = false
}

/** 用户点了「停止」：中断请求，保留已经收到的部分文字 */
function stopGenerating() {
  abortCtrl?.abort()
  const last = messages.value.findLast?.((m) => m._streaming)
  if (last) last._streaming = false
  streaming.value = false
  toast.info('已停止生成')
}

async function copyMessage(m) {
  try {
    await navigator.clipboard.writeText(m.content || '')
    toast.success('已复制')
  } catch {
    toast.error('复制失败')
  }
}

async function send() {
  if (!input.value.trim() || streaming.value) return
  if (pointsBalance.value <= 0) {
    toast.warning('积分不足，请先充值或签到')
    rechargeOpen.value = true
    return
  }

  let sid = sessionId.value
  if (!sid) {
    const cid = Number(route.query.characterId)
    if (!cid) {
      toast.warning('请先从角色详情进入对话')
      return
    }
    const s = await ensureSession(cid)
    sid = s.id
    suppressLoad = true
    await router.replace(`/chat/${sid}`)
    await refreshSessions()
    await nextTick()
    suppressLoad = false
  }

  const text = input.value.trim()
  input.value = ''
  await nextTick()
  autoResize()

  const userTmp = `u-${Date.now()}`
  const assistantTmp = `a-${Date.now()}`
  messages.value.push({ role: 'user', content: text, _tmp: userTmp })
  messages.value.push({ role: 'assistant', content: '', _tmp: assistantTmp, _streaming: true })
  streaming.value = true
  stickToBottom.value = true
  abortCtrl = new AbortController()
  await scrollBottom()

  try {
    await streamChat(
      sid,
      { message: text },
      {
        onChunk: ({ content, meta }) => {
          // meta 里带上下文裁剪统计，用来画占用条
          if (meta) ctxMeta.value = meta
          appendByTmp(assistantTmp, content)
        },
        onDone: async () => {
          stopStreamingFlag(assistantTmp)
          suppressLoad = false
          await loadMessages()
          await refreshSessions()
          await refreshTokenBalance()
        },
        onError: (e) => {
          stopStreamingFlag(assistantTmp)
          const msg = e?.message || '发送失败'
          toast.error(msg)
          if (String(msg).includes('余额不足') || String(msg).includes('积分不足') || String(msg).includes('Token')) {
            rechargeOpen.value = true
            refreshTokenBalance()
          }
        },
        signal: abortCtrl.signal,
      },
    )
  } catch (e) {
    if (e?.name === 'AbortError') streaming.value = false
  }
}

/**
 * 再生成一版：不复用、不覆盖，直接在原消息上加一个版本。
 *
 * 流式期间先把气泡清空当作占位；失败时回滚成原来那一版，别让用户丢掉已看到的内容。
 */
async function newSwipe(m) {
  if (streaming.value || !m.id) return
  if (pointsBalance.value <= 0) {
    toast.warning('积分不足，请先充值或签到')
    rechargeOpen.value = true
    return
  }

  const backup = { content: m.content, index: m.swipeIndex || 0 }
  streaming.value = true
  m.content = ''
  m._streaming = true
  stickToBottom.value = true
  abortCtrl = new AbortController()
  await scrollBottom()

  const restore = () => {
    m.content = backup.content
    m.swipeIndex = backup.index
    m._streaming = false
    streaming.value = false
  }

  try {
    await streamSwipe(m.id, {
      onChunk: ({ content, meta }) => {
        if (meta) ctxMeta.value = meta
        if (content) {
          m.content += content
          if (stickToBottom.value) scrollBottom()
        }
      },
      onDone: async () => {
        m._streaming = false
        streaming.value = false
        await loadMessages()
        await refreshSessions()
        await refreshTokenBalance()
      },
      onError: (e) => {
        restore()
        toast.error(e?.message || '再生成失败')
      },
      signal: abortCtrl.signal,
    })
  } catch (e) {
    if (e?.name === 'AbortError') {
      // 用户主动停止：保留已收到的半截，重新拉一次拿真实的版本号
      m._streaming = false
      streaming.value = false
      await loadMessages()
    } else {
      restore()
      toast.error(e?.message || '再生成失败')
    }
  }
}

/** 左右切版本：纯读库，不扣积分 */
async function switchSwipe(m, index) {
  const count = m.swipeCount || 1
  if (streaming.value || !m.id || index < 0 || index >= count || index === (m.swipeIndex || 0)) {
    return
  }
  try {
    const res = await selectSwipe(m.id, index)
    m.content = res?.content ?? m.content
    m.swipeIndex = res?.swipeIndex ?? index
    m.swipeCount = res?.swipeCount ?? count
  } catch (e) {
    toast.error(e?.message || '切换版本失败')
  }
}

function openEdit(m) {
  editTarget.value = m
  editText.value = m.content || ''
  editOpen.value = true
}

async function submitEdit() {
  const target = editTarget.value
  const text = editText.value.trim()
  if (!target?.id || !text) return
  editSaving.value = true
  try {
    const res = await editMessage(target.id, text)
    target.content = res?.content ?? text
    editOpen.value = false
    toast.success('已保存')
  } catch (e) {
    toast.error(e?.message || '保存失败')
  } finally {
    editSaving.value = false
  }
}

/** 分支：复制到这条消息为止，原会话不动 */
async function branch(m) {
  if (streaming.value || !m.id) return
  try {
    const s = await branchSession(m.id)
    await refreshSessions()
    toast.success('已分支出一段新会话，原对话没动')
    router.push(`/chat/${s.id}`)
  } catch (e) {
    toast.error(e?.message || '分支失败')
  }
}

async function removeSession() {
  if (streaming.value) {
    toast.warning('请等待当前回复完成')
    return
  }
  await deleteSession(sessionId.value)
  await refreshSessions()
  router.push('/chat')
}

/** 输入框随内容长高，最多 160px */
function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

function onScroll() {
  const el = listRef.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

watch(sessionId, (id, prev) => {
  if (streaming.value || suppressLoad) return
  if (String(id) === String(prev)) return
  loadMessages()
})
watch(characterId, loadFavoriteState, { immediate: true })

onMounted(async () => {
  await refreshSessions()
  await refreshTokenBalance()
  if (route.query.characterId && !sessionId.value) {
    const s = await ensureSession(Number(route.query.characterId))
    suppressLoad = true
    await router.replace(`/chat/${s.id}`)
    await refreshSessions()
    suppressLoad = false
  }
  await loadMessages()
  await loadFavoriteState()
})
</script>
