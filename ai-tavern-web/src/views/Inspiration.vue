<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div class="space-y-2">
        <h1 class="text-2xl font-semibold tracking-tight">灵感记录</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          把碎片念头存在本地。点开即可编辑，也可以按你的文风交给 AI 润色。
        </p>
      </div>
      <div class="flex gap-2">
        <Button variant="outline" @click="prefsOpen = true">
          <Settings2 class="size-4" />
          润色配置
        </Button>
        <Button @click="openCreate">
          <Plus class="size-4" />
          写一条灵感
        </Button>
      </div>
    </header>

    <!-- 分类筛选 -->
    <div class="flex flex-wrap items-center gap-1.5">
      <Button
        v-for="t in tagFilters"
        :key="t"
        :variant="filterTag === t ? 'secondary' : 'ghost'"
        size="sm"
        class="rounded-full"
        @click="filterTag = t"
      >
        {{ t }}
      </Button>
      <span class="ml-auto text-xs text-muted-foreground">{{ filtered.length }} 条</span>
    </div>

    <!-- 笔记瀑布流 -->
    <div v-if="filtered.length" class="columns-1 gap-4 md:columns-2 xl:columns-3">
      <Card
        v-for="n in filtered"
        :key="n.id"
        class="mb-4 inline-block w-full break-inside-avoid cursor-pointer gap-3 py-4 transition-colors hover:border-border"
        @click="openEdit(n)"
      >
        <CardContent class="space-y-2.5">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 space-y-1.5">
              <Badge variant="outline" class="font-normal">{{ n.tag }}</Badge>
              <h3 class="text-base font-medium leading-snug">{{ n.title }}</h3>
            </div>
            <div class="flex shrink-0 gap-0.5" @click.stop>
              <Button
                variant="ghost"
                size="icon-xs"
                title="AI 润色"
                :disabled="polishingId === n.id"
                @click="quickPolish(n)"
              >
                <Wand2 class="size-3" />
              </Button>
              <Button variant="ghost" size="icon-xs" title="删除" @click="remove(n.id)">
                <Trash2 class="size-3" />
              </Button>
            </div>
          </div>

          <p class="whitespace-pre-wrap text-sm leading-relaxed text-muted-foreground">
            {{ n.body }}
          </p>

          <div class="flex items-center justify-between pt-1 text-[11px] text-muted-foreground/70">
            <span>{{ formatTime(n.updatedAt) }}</span>
            <span v-if="n.polishedAt">已润色</span>
          </div>
        </CardContent>
      </Card>
    </div>

    <div
      v-else
      class="flex flex-col items-center gap-2 rounded-xl border border-dashed border-border/70 py-16 text-center"
    >
      <span class="grid size-10 place-items-center rounded-xl bg-secondary">
        <Feather class="size-4 text-muted-foreground" />
      </span>
      <p class="text-sm font-medium">还没有灵感</p>
      <p class="text-xs text-muted-foreground">记下第一句对白、一条法则，或一点说不清的氛围。</p>
      <Button class="mt-2" size="sm" @click="openCreate">写第一条</Button>
    </div>

    <!-- 编辑 / 润色 -->
    <Dialog v-model:open="dialogOpen">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ editingId ? '编辑灵感' : '新灵感' }}</DialogTitle>
          <DialogDescription class="sr-only">记录一条灵感</DialogDescription>
        </DialogHeader>

        <div class="space-y-4">
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_auto]">
            <div class="space-y-2">
              <Label for="note-title">标题</Label>
              <Input id="note-title" v-model="draft.title" maxlength="60" placeholder="给这条念头起个名字" />
            </div>
            <div class="space-y-2">
              <Label>分类</Label>
              <div class="flex flex-wrap gap-1">
                <Button
                  v-for="t in tags"
                  :key="t"
                  :variant="draft.tag === t ? 'secondary' : 'outline'"
                  size="xs"
                  @click="draft.tag = t"
                >
                  {{ t }}
                </Button>
              </div>
            </div>
          </div>

          <div class="space-y-2">
            <Label for="note-body">内容</Label>
            <Textarea
              id="note-body"
              v-model="draft.body"
              :rows="8"
              placeholder="对白、设定、氛围、还没成形的念头…"
            />
          </div>

          <div v-if="polishPreview" class="space-y-2 rounded-lg border border-border/70 bg-secondary/40 p-3">
            <div class="flex items-center justify-between gap-2">
              <p class="text-xs font-medium text-muted-foreground">润色预览</p>
              <div class="flex gap-1.5">
                <Button size="xs" @click="applyPolish">采用</Button>
                <Button size="xs" variant="ghost" @click="polishPreview = ''">丢弃</Button>
              </div>
            </div>
            <div class="chat-md max-h-64 overflow-y-auto text-sm" v-html="renderMarkdown(polishPreview)" />
          </div>
        </div>

        <DialogFooter class="sm:justify-between">
          <Button variant="outline" :disabled="polishing" @click="polishDraft">
            <Wand2 class="size-4" />
            {{ polishing ? '润色中…' : 'AI 润色' }}
          </Button>
          <div class="flex gap-2">
            <Button variant="ghost" @click="dialogOpen = false">取消</Button>
            <Button @click="save">保存</Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 润色配置 -->
    <Sheet v-model:open="prefsOpen">
      <SheetContent side="right" class="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>AI 润色配置</SheetTitle>
          <SheetDescription>配置保存在本机，润色时会自动带上。</SheetDescription>
        </SheetHeader>

        <div class="mt-6 space-y-5">
          <div class="space-y-2">
            <Label class="text-xs">文风</Label>
            <div class="flex flex-wrap gap-1.5">
              <Button
                v-for="o in styleOptions"
                :key="o.value"
                :variant="prefs.style === o.value ? 'secondary' : 'outline'"
                size="xs"
                @click="prefs.style = o.value"
              >
                {{ o.label }}
              </Button>
            </div>
          </div>

          <div class="space-y-2">
            <Label class="text-xs">润色强度</Label>
            <div class="flex flex-wrap gap-1.5">
              <Button
                v-for="o in intensityOptions"
                :key="o.value"
                :variant="prefs.intensity === o.value ? 'secondary' : 'outline'"
                size="xs"
                @click="prefs.intensity = o.value"
              >
                {{ o.label }}
              </Button>
            </div>
          </div>

          <div class="space-y-2">
            <Label class="text-xs">篇幅</Label>
            <div class="flex flex-wrap gap-1.5">
              <Button
                v-for="o in lengthOptions"
                :key="o.value"
                :variant="prefs.length === o.value ? 'secondary' : 'outline'"
                size="xs"
                @click="prefs.length = o.value"
              >
                {{ o.label }}
              </Button>
            </div>
          </div>

          <div class="space-y-2">
            <Label class="text-xs">输出语言</Label>
            <div class="flex flex-wrap gap-1.5">
              <Button
                v-for="o in languageOptions"
                :key="o.value"
                :variant="prefs.language === o.value ? 'secondary' : 'outline'"
                size="xs"
                @click="prefs.language = o.value"
              >
                {{ o.label }}
              </Button>
            </div>
          </div>

          <label class="flex cursor-pointer items-center justify-between gap-3 text-sm">
            <span>
              保留原意
              <span class="block text-xs text-muted-foreground">关闭后允许 AI 自由延伸</span>
            </span>
            <Switch v-model="prefs.keepMeaning" />
          </label>

          <div class="space-y-2">
            <Label for="rules" class="text-xs">额外要求</Label>
            <Textarea
              id="rules"
              v-model="prefs.customRules"
              :rows="5"
              placeholder="例如：多用五感描写；保留「银色足迹」设定；对白用短句；不要网文套路…"
            />
          </div>

          <div class="flex gap-2 pt-2">
            <Button @click="savePrefs">保存配置</Button>
            <Button variant="outline" @click="resetPrefs">恢复默认</Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Feather, Plus, Settings2, Trash2, Wand2 } from 'lucide-vue-next'
import { workshopPolishInspiration } from '../api/workshop'
import { renderMarkdown } from '../utils/markdown'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'

const STORAGE_KEY = 'tavern_inspiration_notes'
const PREFS_KEY = 'tavern_inspiration_polish_prefs'
const tags = ['人设', '对白', '剧情', '世界观', '其他']
const tagFilters = ['全部', ...tags]

const styleOptions = [
  { value: 'literary', label: '文艺' },
  { value: 'colloquial', label: '口语' },
  { value: 'script', label: '剧本' },
  { value: 'poetic', label: '诗意' },
]
const intensityOptions = [
  { value: 'light', label: '轻度' },
  { value: 'medium', label: '中度' },
  { value: 'strong', label: '深度' },
]
const lengthOptions = [
  { value: 'shorten', label: '压缩' },
  { value: 'keep', label: '保持' },
  { value: 'expand', label: '扩写' },
]
const languageOptions = [
  { value: '', label: '跟随原文' },
  { value: '中文', label: '中文' },
  { value: 'English', label: 'English' },
  { value: '日本語', label: '日本語' },
]

const defaultPrefs = () => ({
  style: 'literary',
  intensity: 'medium',
  length: 'keep',
  language: '',
  keepMeaning: true,
  customRules: '',
})

const notes = ref(loadNotes())
const filterTag = ref('全部')
const dialogOpen = ref(false)
const prefsOpen = ref(false)
const editingId = ref(null)
const draft = reactive({ title: '', body: '', tag: '人设' })
const prefs = reactive(loadPrefs())
const polishing = ref(false)
const polishingId = ref(null)
const polishPreview = ref('')

const filtered = computed(() => {
  const list = [...notes.value].sort((a, b) => b.updatedAt - a.updatedAt)
  if (filterTag.value === '全部') return list
  return list.filter((n) => n.tag === filterTag.value)
})

function loadNotes() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (Array.isArray(raw) && raw.length) return raw
  } catch {
    /* 本地缓存损坏时回落到示例数据 */
  }
  const now = Date.now()
  return [
    {
      id: 'seed-1',
      title: '雨夜酒馆的门铃',
      tag: '对白',
      body: '门一开，铜铃轻响。她没有回头，只把酒杯往空位推了推。\n「……坐。」',
      updatedAt: now - 86400000,
    },
    {
      id: 'seed-2',
      title: '世界法则碎片',
      tag: '世界观',
      body: '凡说出真名者，会在月光下留下银色足迹，三日不散。',
      updatedAt: now - 3600000,
    },
  ]
}

function loadPrefs() {
  try {
    return { ...defaultPrefs(), ...(JSON.parse(localStorage.getItem(PREFS_KEY) || '{}') || {}) }
  } catch {
    return defaultPrefs()
  }
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(notes.value))
}

function savePrefs() {
  localStorage.setItem(PREFS_KEY, JSON.stringify({ ...prefs }))
  prefsOpen.value = false
  toast.success('润色配置已保存')
}

function resetPrefs() {
  Object.assign(prefs, defaultPrefs())
  localStorage.setItem(PREFS_KEY, JSON.stringify({ ...prefs }))
  toast.success('已恢复默认配置')
}

function formatTime(ts) {
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ''
  }
}

function openCreate() {
  editingId.value = null
  draft.title = ''
  draft.body = ''
  draft.tag = '人设'
  polishPreview.value = ''
  dialogOpen.value = true
}

function openEdit(n) {
  editingId.value = n.id
  draft.title = n.title
  draft.body = n.body
  draft.tag = n.tag
  polishPreview.value = ''
  dialogOpen.value = true
}

function polishPayload(text, title, tag) {
  return {
    text,
    title,
    tag,
    style: prefs.style,
    intensity: prefs.intensity,
    length: prefs.length,
    language: prefs.language || undefined,
    keepMeaning: prefs.keepMeaning,
    customRules: prefs.customRules || undefined,
  }
}

async function polishDraft() {
  if (!draft.body.trim()) {
    toast.warning('先写一点内容再润色')
    return
  }
  polishing.value = true
  try {
    const res = await workshopPolishInspiration(polishPayload(draft.body, draft.title, draft.tag))
    polishPreview.value = res.polished || ''
    if (res.title && !draft.title.trim()) draft.title = res.title
    toast.success('润色完成，可预览后采用')
  } catch {
    /* 接口错误由拦截器提示 */
  } finally {
    polishing.value = false
  }
}

function applyPolish() {
  if (!polishPreview.value) return
  draft.body = polishPreview.value
  polishPreview.value = ''
  toast.success('已采用润色结果，记得保存')
}

async function quickPolish(n) {
  polishingId.value = n.id
  try {
    const res = await workshopPolishInspiration(polishPayload(n.body, n.title, n.tag))
    const i = notes.value.findIndex((x) => x.id === n.id)
    if (i >= 0 && res.polished) {
      notes.value[i] = {
        ...notes.value[i],
        body: res.polished,
        title: res.title || notes.value[i].title,
        updatedAt: Date.now(),
        polishedAt: Date.now(),
      }
      persist()
      toast.success(`「${n.title}」已润色`)
    }
  } catch {
    /* */
  } finally {
    polishingId.value = null
  }
}

function save() {
  if (!draft.title.trim() || !draft.body.trim()) {
    toast.warning('标题和内容都要写一点')
    return
  }
  const now = Date.now()
  if (editingId.value) {
    const i = notes.value.findIndex((n) => n.id === editingId.value)
    if (i >= 0) {
      notes.value[i] = {
        ...notes.value[i],
        title: draft.title.trim(),
        body: draft.body.trim(),
        tag: draft.tag,
        updatedAt: now,
      }
    }
  } else {
    notes.value.push({
      id: `n-${now}`,
      title: draft.title.trim(),
      body: draft.body.trim(),
      tag: draft.tag,
      updatedAt: now,
    })
  }
  persist()
  dialogOpen.value = false
  toast.success('已保存到本地')
}

function remove(id) {
  notes.value = notes.value.filter((n) => n.id !== id)
  persist()
  toast.success('已删除')
}

watch(
  prefs,
  () => {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ ...prefs }))
  },
  { deep: true },
)
</script>
