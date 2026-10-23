<template>
  <div class="space-y-8">
    <header class="space-y-2">
      <h1 class="text-2xl font-semibold tracking-tight">创意工坊</h1>
      <p class="max-w-2xl text-sm text-muted-foreground">
        AI 帮你写角色卡、生成立绘。草稿会暂存在本机并写入下方「制作记录」，随时可以取回继续做。
      </p>
    </header>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_400px]">
      <!-- 左：生成表单 -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">AI 创建角色卡</CardTitle>
          <CardAction>
            <Badge variant="secondary" class="font-normal">对话 + 生图</Badge>
          </CardAction>
        </CardHeader>
        <CardContent class="space-y-5">
          <div class="space-y-2">
            <Label for="idea">角色想法</Label>
            <Textarea
              id="idea"
              v-model="idea"
              :rows="4"
              placeholder="例如：雾港酒馆的沉默调酒师，会读唇语，说话极短，对真名过敏…"
            />
          </div>

          <div class="space-y-2">
            <Label for="world">附加世界观 / 灵感（可选）</Label>
            <Textarea
              id="world"
              v-model="worldContext"
              :rows="3"
              placeholder="可从世界树复制，或点下方按钮一键填入"
            />
            <div class="flex flex-wrap gap-2">
              <Button variant="outline" size="sm" @click="fillFromWorldTree">填入世界树</Button>
              <Button variant="outline" size="sm" @click="fillFromInspiration">填入最新灵感</Button>
            </div>
          </div>

          <label class="flex cursor-pointer items-center gap-2 text-sm">
            <Switch v-model="withAvatar" />
            同时生成立绘并上传
          </label>

          <div class="flex flex-wrap gap-2 border-t border-border/60 pt-4">
            <Button :disabled="generating" @click="generate()">
              <Sparkles class="size-4" />
              {{ generating ? '生成中…' : '生成草稿' }}
            </Button>
            <Button variant="secondary" :disabled="!draft || saving" @click="useDirect">
              保存为我的角色
            </Button>
            <Button variant="outline" :disabled="!draft" @click="exportSt">导出 ST JSON</Button>
            <Button variant="outline" :disabled="!draft" @click="openEdit">去编辑页微调</Button>
            <Button
              variant="ghost"
              :disabled="!draft && !idea"
              class="text-muted-foreground"
              @click="clearWorkspace({ clearIdea: true })"
            >
              清空工作区
            </Button>
          </div>
        </CardContent>
      </Card>

      <!-- 右：草稿预览 -->
      <div class="lg:sticky lg:top-20 lg:self-start">
        <Card class="overflow-hidden">
          <CardHeader>
            <CardTitle class="text-base">草稿预览</CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="!draft" class="flex flex-col items-center justify-center gap-2 py-12 text-center">
              <span class="grid size-10 place-items-center rounded-xl bg-secondary">
                <FileText class="size-4 text-muted-foreground" />
              </span>
              <p class="text-sm text-muted-foreground">生成后会出现在这里</p>
            </div>

            <div v-else class="space-y-4">
              <div class="flex gap-3">
                <div class="size-20 shrink-0 overflow-hidden rounded-xl border border-border/60 bg-secondary">
                  <img v-if="draft.avatar" :src="draft.avatar" alt="" class="size-full object-cover" />
                  <span v-else class="grid size-full place-items-center text-lg text-muted-foreground">
                    {{ draft.name?.[0] || '?' }}
                  </span>
                </div>
                <div class="min-w-0 flex-1 space-y-1">
                  <h3 class="truncate text-lg font-medium">{{ draft.name }}</h3>
                  <p class="line-clamp-3 text-xs text-muted-foreground">{{ draft.description }}</p>
                </div>
              </div>

              <div class="flex flex-wrap gap-1">
                <Badge v-if="draft.category" variant="secondary" class="font-normal">{{ draft.category }}</Badge>
                <Badge v-for="t in draft.tags || []" :key="t" variant="outline" class="font-normal">{{ t }}</Badge>
              </div>

              <div class="space-y-1.5 rounded-lg bg-secondary/50 p-3">
                <p class="text-xs font-medium text-muted-foreground">人设</p>
                <p class="max-h-32 overflow-y-auto whitespace-pre-wrap text-xs leading-relaxed">
                  {{ draft.personality }}
                </p>
              </div>

              <div class="space-y-1.5 rounded-lg bg-secondary/50 p-3">
                <p class="text-xs font-medium text-muted-foreground">开场白</p>
                <p class="text-xs italic leading-relaxed">{{ draft.greeting }}</p>
              </div>

              <div class="space-y-2 border-t border-border/60 pt-3">
                <Label class="text-xs">立绘提示词</Label>
                <Input v-model="avatarPrompt" placeholder="可改后重生" />
                <Button
                  variant="outline"
                  size="sm"
                  class="w-full"
                  :disabled="avatarLoading"
                  @click="regenAvatar"
                >
                  <ImagePlus class="size-4" />
                  {{ avatarLoading ? '生成中…' : '重生立绘' }}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- 制作记录 -->
    <section class="space-y-3">
      <div class="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 class="text-lg font-medium">制作记录</h2>
          <p class="text-xs text-muted-foreground">
            本机保存最近 {{ MAX_HISTORY }} 条，可随时取回继续做。
          </p>
        </div>
        <Button v-if="history.length" variant="ghost" size="sm" class="text-muted-foreground" @click="askClearHistory">
          <Trash2 class="size-4" />
          清空记录
        </Button>
      </div>

      <div
        v-if="!history.length"
        class="flex flex-col items-center gap-2 rounded-xl border border-dashed border-border/70 py-12 text-center"
      >
        <p class="text-sm text-muted-foreground">还没有制作记录</p>
        <p class="text-xs text-muted-foreground">生成角色卡后会自动出现在这里</p>
      </div>

      <div v-else class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <Card v-for="item in history" :key="item.id" class="gap-3 py-4">
          <CardContent class="space-y-3">
            <div class="flex gap-3">
              <div class="size-12 shrink-0 overflow-hidden rounded-lg border border-border/60 bg-secondary">
                <img
                  v-if="item.draft?.avatar"
                  :src="item.draft.avatar"
                  alt=""
                  class="size-full object-cover"
                />
                <span v-else class="grid size-full place-items-center text-sm text-muted-foreground">
                  {{ item.draft?.name?.[0] || '?' }}
                </span>
              </div>
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-1.5">
                  <span class="truncate text-sm font-medium">{{ item.draft?.name || '未命名' }}</span>
                  <Badge variant="outline" class="shrink-0 font-normal">{{ statusLabel(item.status) }}</Badge>
                </div>
                <p class="mt-0.5 line-clamp-2 text-xs text-muted-foreground">
                  {{ item.idea || item.draft?.description }}
                </p>
                <p class="mt-1 text-[11px] text-muted-foreground/70">
                  {{ formatTime(item.updatedAt || item.createdAt) }}
                </p>
              </div>
            </div>

            <div class="flex flex-wrap gap-1.5">
              <Button variant="outline" size="xs" @click="restoreRecord(item)">取回</Button>
              <Button
                variant="secondary"
                size="xs"
                :disabled="savingId === item.id"
                @click="saveRecord(item)"
              >
                保存为角色
              </Button>
              <Button variant="ghost" size="xs" @click="exportRecord(item)">导出</Button>
              <Button
                v-if="item.characterId"
                variant="ghost"
                size="xs"
                @click="router.push(`/character/${item.characterId}`)"
              >
                查看
              </Button>
              <Button variant="ghost" size="xs" class="text-muted-foreground" @click="removeRecord(item.id)">
                删除
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </section>

    <!-- 实验区 -->
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle class="text-base">角色语音</CardTitle>
          <CardAction>
            <Badge variant="outline" class="font-normal">实验</Badge>
          </CardAction>
        </CardHeader>
        <CardContent class="space-y-4">
          <p class="text-xs text-muted-foreground">选音色与语速，用浏览器语音合成试听。</p>

          <div class="space-y-2">
            <Label class="text-xs">音色</Label>
            <div class="flex flex-wrap gap-1.5">
              <Button
                v-for="t in timbres"
                :key="t.value"
                :variant="voice.timbre === t.value ? 'secondary' : 'outline'"
                size="xs"
                @click="voice.timbre = t.value"
              >
                {{ t.label }}
              </Button>
            </div>
          </div>

          <div class="space-y-2">
            <Label class="text-xs">语速 {{ voice.rate.toFixed(1) }}x</Label>
            <input
              v-model.number="voice.rate"
              type="range"
              min="0.7"
              max="1.4"
              step="0.1"
              class="w-full accent-foreground"
            />
          </div>

          <div class="space-y-2">
            <Label class="text-xs">试听文案</Label>
            <Textarea v-model="voice.sample" :rows="2" />
          </div>

          <Button variant="outline" size="sm" @click="previewVoice">
            <Volume2 class="size-4" />
            试听
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle class="text-base">对话框风格</CardTitle>
          <CardAction>
            <Badge variant="outline" class="font-normal">实验</Badge>
          </CardAction>
        </CardHeader>
        <CardContent class="space-y-4">
          <p class="text-xs text-muted-foreground">预设气泡皮肤，之后可做成自定义模板。</p>

          <div class="flex flex-wrap gap-1.5">
            <Button
              v-for="s in styles"
              :key="s.id"
              :variant="styleId === s.id ? 'secondary' : 'outline'"
              size="xs"
              @click="styleId = s.id"
            >
              {{ s.name }}
            </Button>
          </div>

          <div class="min-h-[150px] space-y-3 rounded-xl border border-border/60 bg-secondary/30 p-4">
            <p class="text-[11px] uppercase tracking-wider text-muted-foreground">
              预览 · {{ activeStyle.name }}
            </p>
            <div class="flex justify-end">
              <div :class="activeStyle.userClass">今晚月色真美。</div>
            </div>
            <div class="flex items-end justify-start gap-2">
              <div class="size-8 shrink-0 rounded-full bg-muted" />
              <div :class="activeStyle.aiClass" v-html="activeStyle.sampleHtml" />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- 确认弹窗 -->
    <Dialog v-model:open="confirmOpen">
      <DialogContent class="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ confirmTitle }}</DialogTitle>
          <DialogDescription>{{ confirmText }}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="confirmOpen = false">取消</Button>
          <Button variant="destructive" @click="runConfirm">确定</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { FileText, ImagePlus, Sparkles, Trash2, Volume2 } from 'lucide-vue-next'
import { workshopGenerateCharacter, workshopGenerateAvatar } from '../api/workshop'
import { createCharacter } from '../api/character'
import { downloadStCardJson } from '../utils/sillyTavernCard'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'

const router = useRouter()
const WORK_KEY = 'tavern_workshop_draft'
const HISTORY_KEY = 'tavern_workshop_history'
const MAX_HISTORY = 30

const idea = ref('')
const worldContext = ref('')
const withAvatar = ref(true)
const generating = ref(false)
const saving = ref(false)
const savingId = ref(null)
const avatarLoading = ref(false)
const draft = ref(null)
const avatarPrompt = ref('')
const activeRecordId = ref(null)
const history = ref([])

/** 统一的小确认弹窗，替代 ElMessageBox */
const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmText = ref('')
let confirmAction = null

function askConfirm(title, text, action) {
  confirmTitle.value = title
  confirmText.value = text
  confirmAction = action
  confirmOpen.value = true
}

function runConfirm() {
  confirmOpen.value = false
  confirmAction?.()
  confirmAction = null
}

const timbres = [
  { value: 'warm', label: '温柔' },
  { value: 'cool', label: '清冷' },
  { value: 'bright', label: '明亮' },
  { value: 'deep', label: '低沉' },
]

const voice = reactive({
  timbre: 'warm',
  rate: 1,
  sample: '嘘……再靠近一点，我才告诉你今晚的秘密。',
})

const styles = [
  {
    id: 'tavern',
    name: '酒馆暖光',
    userClass: 'max-w-[80%] rounded-2xl rounded-br-md bg-primary text-primary-foreground px-3 py-2 text-sm',
    aiClass: 'max-w-[80%] rounded-2xl rounded-bl-md bg-card text-foreground px-3 py-2 text-sm border border-border/60',
    sampleHtml: '烛火晃了晃，她抬眼看你：<em>「……你来了。」</em>',
  },
  {
    id: 'ink',
    name: '水墨笺',
    userClass: 'max-w-[80%] border border-border bg-transparent text-foreground px-3 py-2 text-sm italic',
    aiClass: 'max-w-[80%] border-l-2 border-foreground/50 pl-3 text-foreground text-sm leading-relaxed',
    sampleHtml: '纸页翻动。<br /><span class="opacity-70">风过竹林，未闻足音。</span>',
  },
  {
    id: 'neon',
    name: '霓虹夜',
    userClass: 'max-w-[80%] rounded-md bg-secondary text-secondary-foreground px-3 py-2 text-sm',
    aiClass: 'max-w-[80%] rounded-md bg-card text-foreground px-3 py-2 text-sm border border-border/60',
    sampleHtml: '信号接通。<strong>「目标已锁定。」</strong>',
  },
]
const styleId = ref('tavern')
const activeStyle = computed(() => styles.find((s) => s.id === styleId.value) || styles[0])

function uid() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function loadHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    history.value = Array.isArray(raw) ? raw : []
  } catch {
    history.value = []
  }
}

function saveHistory() {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value.slice(0, MAX_HISTORY)))
}

function cloneDraft(d) {
  return d ? JSON.parse(JSON.stringify(d)) : null
}

/** 把当前工作区写入/更新制作记录 */
function upsertHistory(status, extra = {}) {
  if (!draft.value) return null
  const now = Date.now()
  const id = activeRecordId.value || uid()
  const record = {
    id,
    createdAt: now,
    updatedAt: now,
    idea: idea.value,
    worldContext: worldContext.value,
    draft: cloneDraft(draft.value),
    avatarPrompt: avatarPrompt.value,
    status,
    characterId: null,
    ...extra,
  }
  const idx = history.value.findIndex((h) => h.id === id)
  if (idx >= 0) {
    record.createdAt = history.value[idx].createdAt
    record.characterId = extra.characterId ?? history.value[idx].characterId ?? null
    history.value.splice(idx, 1, record)
  } else {
    history.value.unshift(record)
  }
  history.value = history.value.slice(0, MAX_HISTORY)
  saveHistory()
  activeRecordId.value = id
  return id
}

function clearWorkspace({ clearIdea = false } = {}) {
  draft.value = null
  avatarPrompt.value = ''
  activeRecordId.value = null
  if (clearIdea) {
    idea.value = ''
    worldContext.value = ''
  }
}

function statusLabel(s) {
  return ({ generated: '草稿', saved: '已保存', exported: '已导出', archived: '已归档', editing: '编辑中' })[s] || s
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function previewVoice() {
  if (!window.speechSynthesis) {
    toast.warning('当前浏览器不支持语音合成')
    return
  }
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(voice.sample)
  u.rate = voice.rate
  u.pitch = voice.timbre === 'deep' ? 0.7 : voice.timbre === 'bright' ? 1.3 : voice.timbre === 'cool' ? 0.95 : 1.05
  window.speechSynthesis.speak(u)
}

function fillFromWorldTree() {
  try {
    const tree = JSON.parse(localStorage.getItem('tavern_world_tree') || '[]')
    if (!tree.length) {
      toast.warning('世界树还是空的')
      return
    }
    const lines = []
    const walk = (n, d) => {
      lines.push(`${'  '.repeat(d)}[${n.type}] ${n.label}${n.summary ? ` — ${n.summary}` : ''}`)
      ;(n.children || []).forEach((c) => walk(c, d + 1))
    }
    tree.forEach((r) => walk(r, 0))
    worldContext.value = lines.join('\n')
    toast.success('已填入世界树')
  } catch {
    toast.error('读取世界树失败')
  }
}

function fillFromInspiration() {
  try {
    const notes = JSON.parse(localStorage.getItem('tavern_inspiration_notes') || '[]')
    if (!notes.length) {
      toast.warning('还没有灵感记录')
      return
    }
    const latest = [...notes].sort((a, b) => b.updatedAt - a.updatedAt)[0]
    worldContext.value = `【灵感】${latest.title}\n${latest.body}`
    toast.success('已填入最新灵感')
  } catch {
    toast.error('读取灵感失败')
  }
}

function toCreatePayload(card) {
  return {
    name: card.name,
    avatar: card.avatar,
    description: card.description,
    personality: card.personality,
    backgroundStory: card.backgroundStory,
    exampleDialogue: card.exampleDialogue,
    greeting: card.greeting,
    categoryId: card.categoryId || null,
    categoryName: card.category || undefined,
    tagNames: Array.isArray(card.tags) ? card.tags : [],
    temperature: 0.7,
    topP: 0.9,
    maxTokens: 800,
  }
}

async function generate() {
  if (!idea.value.trim()) {
    toast.warning('先写一点角色想法')
    return
  }
  generating.value = true
  try {
    const res = await workshopGenerateCharacter({
      idea: idea.value,
      worldContext: worldContext.value,
      withAvatar: withAvatar.value,
    })
    draft.value = res
    avatarPrompt.value = res.avatarPrompt || ''
    activeRecordId.value = null
    upsertHistory('generated')
    toast.success(withAvatar.value ? '已生成，并写入制作记录' : '角色卡已生成，并写入制作记录')
  } catch {
    /* 接口错误由拦截器提示 */
  } finally {
    generating.value = false
  }
}

async function useDirect() {
  if (!draft.value) {
    toast.warning('请先生成草稿')
    return
  }
  saving.value = true
  try {
    const card = await createCharacter(toCreatePayload(draft.value))
    upsertHistory('saved', { characterId: card.id })
    clearWorkspace({ clearIdea: true })
    toast.success('已保存为我的角色，工作区已清空')
    router.push(`/edit/${card.id}`)
  } catch {
    /* */
  } finally {
    saving.value = false
  }
}

function exportSt() {
  if (!draft.value) return
  downloadStCardJson(draft.value.stCard || draft.value)
  upsertHistory('exported')
  clearWorkspace()
  toast.success('已导出，草稿已收入制作记录')
}

function openEdit() {
  if (!draft.value) return
  upsertHistory('editing')
  sessionStorage.setItem(
    'tavern_workshop_spawn',
    JSON.stringify({ ...draft.value, at: Date.now() }),
  )
  clearWorkspace({ clearIdea: true })
  router.push({ path: '/create', query: { from: 'workshop' } })
}

async function regenAvatar() {
  if (!draft.value) return
  avatarLoading.value = true
  try {
    const res = await workshopGenerateAvatar({
      prompt: avatarPrompt.value || draft.value.avatarPrompt,
      name: draft.value.name,
    })
    draft.value = { ...draft.value, avatar: res.url }
    upsertHistory('generated')
    toast.success('立绘已更新')
  } catch {
    /* */
  } finally {
    avatarLoading.value = false
  }
}

function restoreRecord(item) {
  idea.value = item.idea || ''
  worldContext.value = item.worldContext || ''
  draft.value = cloneDraft(item.draft)
  avatarPrompt.value = item.avatarPrompt || item.draft?.avatarPrompt || ''
  activeRecordId.value = item.id
  toast.success(`已取回「${item.draft?.name || '草稿'}」`)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function saveRecord(item) {
  if (!item?.draft) return
  savingId.value = item.id
  try {
    const card = await createCharacter(toCreatePayload(item.draft))
    const idx = history.value.findIndex((h) => h.id === item.id)
    if (idx >= 0) {
      history.value[idx] = {
        ...history.value[idx],
        status: 'saved',
        characterId: card.id,
        updatedAt: Date.now(),
      }
      saveHistory()
    }
    toast.success('已保存为我的角色')
    router.push(`/edit/${card.id}`)
  } catch {
    /* */
  } finally {
    savingId.value = null
  }
}

function exportRecord(item) {
  if (!item?.draft) return
  downloadStCardJson(item.draft.stCard || item.draft)
  const idx = history.value.findIndex((h) => h.id === item.id)
  if (idx >= 0) {
    history.value[idx] = { ...history.value[idx], status: 'exported', updatedAt: Date.now() }
    saveHistory()
  }
  toast.success('已下载 JSON')
}

function removeRecord(id) {
  history.value = history.value.filter((h) => h.id !== id)
  saveHistory()
  if (activeRecordId.value === id) activeRecordId.value = null
}

function askClearHistory() {
  askConfirm('清空制作记录', '将删除全部本地制作记录，不可恢复。', () => {
    history.value = []
    saveHistory()
    toast.success('已清空')
  })
}

function persistWork() {
  localStorage.setItem(
    WORK_KEY,
    JSON.stringify({
      idea: idea.value,
      worldContext: worldContext.value,
      withAvatar: withAvatar.value,
      voice: { ...voice },
      styleId: styleId.value,
      draft: draft.value,
      avatarPrompt: avatarPrompt.value,
      activeRecordId: activeRecordId.value,
    }),
  )
}

loadHistory()

try {
  const raw = JSON.parse(localStorage.getItem(WORK_KEY) || 'null')
  if (raw) {
    if (raw.idea) idea.value = raw.idea
    if (raw.worldContext) worldContext.value = raw.worldContext
    if (typeof raw.withAvatar === 'boolean') withAvatar.value = raw.withAvatar
    if (raw.voice) Object.assign(voice, raw.voice)
    if (raw.styleId) styleId.value = raw.styleId
    if (raw.draft) draft.value = raw.draft
    if (raw.avatarPrompt) avatarPrompt.value = raw.avatarPrompt
    if (raw.activeRecordId) activeRecordId.value = raw.activeRecordId
  }
  // 旧版滞留草稿：迁入制作记录并清空工作区，避免一直挂着
  if (draft.value) {
    const already = history.value.some(
      (h) =>
        h.id === activeRecordId.value ||
        (h.draft?.name === draft.value.name && h.idea === idea.value),
    )
    if (!already) upsertHistory('archived')
    clearWorkspace({ clearIdea: true })
  }
} catch {
  /* 本地缓存损坏时忽略 */
}

watch([idea, worldContext, withAvatar, voice, styleId, draft, avatarPrompt, activeRecordId], persistWork, {
  deep: true,
})
</script>
