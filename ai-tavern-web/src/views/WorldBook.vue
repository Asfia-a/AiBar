<template>
  <div class="mx-auto max-w-6xl space-y-5">
    <!-- 页头 -->
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0 space-y-1.5">
        <button
          type="button"
          class="inline-flex items-center gap-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
          @click="router.push(`/character/${characterId}`)"
        >
          <ArrowLeft class="size-3.5" />
          返回角色
        </button>
        <h1 class="text-xl font-semibold tracking-tight">
          世界书
          <span class="text-muted-foreground">· {{ characterName }}</span>
        </h1>
        <p class="max-w-2xl text-sm leading-relaxed text-muted-foreground">
          设定不必全塞进提示词：只有对话里出现触发词，对应条目才会被拼进上下文。没命中的条目一个 token 都不花。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <input
          ref="importInput"
          type="file"
          accept="application/json,.json"
          class="hidden"
          @change="onImportPicked"
        />
        <Button variant="outline" :disabled="importing" @click="importInput?.click()">
          <Loader2 v-if="importing" class="size-4 animate-spin" />
          <Upload v-else class="size-4" />
          导入
        </Button>
        <Button variant="outline" @click="exportBook">
          <Download class="size-4" />
          导出
        </Button>
        <Button variant="outline" @click="startNew('global')">
          <Globe class="size-4" />
          全局条目
        </Button>
        <Button @click="startNew('character')">
          <Plus class="size-4" />
          新建条目
        </Button>
      </div>
    </div>

    <div class="grid items-start gap-5 lg:grid-cols-[19rem_1fr]">
      <!-- 条目列表 -->
      <div class="space-y-3 lg:sticky lg:top-4">
        <div class="relative">
          <Search class="absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="keyword" placeholder="搜备注名或触发词" class="h-9 pl-8" />
        </div>

        <div class="rounded-xl border border-border/60 bg-card">
          <div class="max-h-[calc(100dvh-14rem)] overflow-y-auto p-1.5">
            <template v-if="loading">
              <div class="space-y-1.5 p-1">
                <Skeleton v-for="i in 4" :key="i" class="h-11 rounded-lg" />
              </div>
            </template>

            <template v-else>
              <p class="px-2 pt-1.5 pb-1 text-[11px] tracking-wide text-muted-foreground">
                角色条目 · {{ filteredCharacter.length }}
                <span class="opacity-70">（拖动可排序）</span>
              </p>
              <div
                v-for="e in filteredCharacter"
                :key="e.id"
                class="flex w-full cursor-grab items-start gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-secondary/70"
                :class="[
                  form?.id === e.id ? 'bg-secondary' : '',
                  draggedId === e.id ? 'opacity-40' : '',
                ]"
                draggable="true"
                @click="select(e, 'character')"
                @dragstart="onDragStart(e, $event)"
                @dragover.prevent
                @drop.prevent="onDropTo(e.id)"
                @dragend="draggedId = null"
              >
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-sm">{{ e.title }}</span>
                  <span class="mt-0.5 block truncate text-[11px] text-muted-foreground">
                    {{ e.constantOn === 1 ? '常量 · 每轮都插' : e.keys || '无触发词' }}
                  </span>
                </span>
                <span class="mt-0.5 shrink-0 text-[10px] text-muted-foreground">
                  {{ posLabel(e.position) }}
                </span>
                <span class="mt-0.5 shrink-0" @click.stop>
                  <Switch
                    :model-value="e.enabled === 1"
                    @update:model-value="(v) => toggleEnabled(e, v)"
                  />
                </span>
              </div>
              <p
                v-if="!filteredCharacter.length"
                class="px-2 py-4 text-center text-xs text-muted-foreground"
              >
                还没有角色条目
              </p>

              <p class="mt-2 border-t border-border/60 px-2 pt-2.5 pb-1 text-[11px] tracking-wide text-muted-foreground">
                我的全局条目 · {{ filteredGlobal.length }}
                <span class="opacity-70">（对所有角色生效）</span>
              </p>
              <div
                v-for="e in filteredGlobal"
                :key="`g-${e.id}`"
                class="flex w-full cursor-pointer items-start gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-secondary/70"
                :class="form?.id === e.id ? 'bg-secondary' : ''"
                @click="select(e, 'global')"
              >
                <Globe class="mt-0.5 size-3 shrink-0 text-muted-foreground" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-sm">{{ e.title }}</span>
                  <span class="mt-0.5 block truncate text-[11px] text-muted-foreground">
                    {{ e.constantOn === 1 ? '常量 · 每轮都插' : e.keys || '无触发词' }}
                  </span>
                </span>
                <span class="mt-0.5 shrink-0" @click.stop>
                  <Switch
                    :model-value="e.enabled === 1"
                    @update:model-value="(v) => toggleEnabled(e, v)"
                  />
                </span>
              </div>
              <p v-if="!filteredGlobal.length" class="px-2 py-4 text-center text-xs text-muted-foreground">
                还没有全局条目
              </p>
            </template>
          </div>
        </div>
      </div>

      <div class="space-y-5">
        <!-- 编辑器 -->
        <Card v-if="form">
          <CardHeader class="pb-0">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <CardTitle class="text-base">
                  {{ form.id ? '编辑条目' : form.scope === 'global' ? '新建全局条目' : '新建角色条目' }}
                </CardTitle>
                <CardDescription class="mt-1">
                  {{
                    form.scope === 'global'
                      ? '全局条目对所有角色生效，只影响你自己的对话。'
                      : '只在这个角色被聊到相关话题时插入。'
                  }}
                </CardDescription>
              </div>
              <Badge variant="outline" class="font-normal text-muted-foreground">
                {{ form.constant ? '常量' : '关键词触发' }}
              </Badge>
            </div>
          </CardHeader>

          <CardContent class="space-y-4 pt-5">
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5 sm:col-span-2">
                <Label class="text-xs text-muted-foreground">备注名（只给你看，不进提示词）</Label>
                <Input v-model="form.title" placeholder="例：钟楼的规矩" class="h-9" />
              </div>

              <div class="space-y-1.5 sm:col-span-2">
                <Label class="text-xs text-muted-foreground">
                  触发词
                  <span class="opacity-70">英文逗号分隔；/正则/i 也认</span>
                </Label>
                <Input
                  v-model="form.keys"
                  :disabled="form.constant"
                  placeholder="钟楼, 敲钟, /第[一二三四五六七八九十]+下/"
                  class="h-9 font-mono text-[13px]"
                />
                <p v-if="form.constant" class="text-[11px] text-muted-foreground">
                  常量条目不需要触发词，每轮都会插入。
                </p>
              </div>

              <div class="space-y-1.5 sm:col-span-2">
                <Label class="text-xs text-muted-foreground">命中后插入的内容</Label>
                <Textarea
                  v-model="form.content"
                  :rows="7"
                  placeholder="写设定本身，不要写「请记住」这类指令。例：钟楼每晚敲十三下，第十三下之后不要出门。"
                  class="resize-y text-[13px] leading-relaxed"
                />
                <p class="text-[11px] text-muted-foreground">
                  约 {{ estTokens }} tokens（按字符折算，与后端预算同一套估算）
                </p>
              </div>

              <div class="space-y-1.5 sm:col-span-2">
                <Label class="text-xs text-muted-foreground">插入位置</Label>
                <div class="grid gap-1.5 sm:grid-cols-3">
                  <button
                    v-for="p in POSITIONS"
                    :key="p.value"
                    type="button"
                    class="rounded-lg border px-2.5 py-2 text-left transition-colors"
                    :class="
                      form.position === p.value
                        ? 'border-foreground/30 bg-secondary'
                        : 'border-border/60 hover:bg-secondary/50'
                    "
                    @click="form.position = p.value"
                  >
                    <span class="block text-xs font-medium">{{ p.label }}</span>
                    <span class="mt-0.5 block text-[10px] leading-snug text-muted-foreground">
                      {{ p.desc }}
                    </span>
                  </button>
                </div>
              </div>

              <div v-if="form.position === 'in_chat'" class="space-y-1.5">
                <Label class="text-xs text-muted-foreground">插入深度（倒数第几条之前）</Label>
                <Input v-model.number="form.depth" type="number" min="0" max="50" class="h-9" />
              </div>

              <div class="space-y-1.5">
                <Label class="text-xs text-muted-foreground">顺序（越大越靠后）</Label>
                <Input v-model.number="form.orderNo" type="number" min="0" max="10000" class="h-9" />
              </div>

              <div class="space-y-1.5">
                <Label class="text-xs text-muted-foreground">触发概率 %</Label>
                <Input v-model.number="form.probability" type="number" min="0" max="100" class="h-9" />
                <p class="text-[11px] text-muted-foreground">100 = 命中必插，低了当随机彩蛋</p>
              </div>

              <div class="flex flex-col justify-center gap-2 rounded-lg border border-border/60 px-3 py-3 sm:col-span-2">
                <div class="flex flex-wrap items-center gap-x-10 gap-y-2">
                  <label class="flex cursor-pointer items-center gap-2">
                    <Switch v-model="form.constant" />
                    <span class="text-xs">常量（无条件插入）</span>
                  </label>
                  <label class="flex cursor-pointer items-center gap-2">
                    <Switch v-model="form.enabled" />
                    <span class="text-xs">启用</span>
                  </label>
                </div>
                <p class="text-[11px] leading-relaxed text-muted-foreground">
                  常量条目每轮都插；关掉「启用」只是不参与激活，配置会留着。
                </p>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-2 border-t border-border/60 pt-4">
              <Button :disabled="saving" @click="save">
                <Loader2 v-if="saving" class="size-4 animate-spin" />
                <Check v-else class="size-4" />
                保存
              </Button>
              <Button variant="outline" @click="startNew(form.scope)">放弃改动</Button>
              <Button
                v-if="form.id"
                variant="ghost"
                class="ml-auto text-muted-foreground hover:text-destructive"
                @click="remove"
              >
                <Trash2 class="size-4" />
                {{ confirmingDelete ? '再点一次确认删除' : '删除' }}
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card v-else>
          <CardContent class="flex flex-col items-center justify-center gap-2 py-14 text-center">
            <span class="grid size-10 place-items-center rounded-xl bg-secondary">
              <BookOpen class="size-4 text-muted-foreground" />
            </span>
            <p class="text-sm font-medium">左侧选一条，或者新建一条</p>
            <p class="max-w-sm text-xs leading-relaxed text-muted-foreground">
              世界书是「按需加载的设定」：触发词命中才插入，命中几条插几条。
            </p>
          </CardContent>
        </Card>

        <!-- 命中预演 -->
        <Card>
          <CardHeader class="pb-0">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <CardTitle class="text-base">命中预演</CardTitle>
                <CardDescription class="mt-1">
                  拿一段话跑一遍激活逻辑，看这一轮会插入哪几条 —— 不调模型，不花积分。
                </CardDescription>
              </div>
              <Button variant="ghost" size="sm" @click="fillSample">
                <Wand2 class="size-4" />
                填个示例
              </Button>
            </div>
          </CardHeader>
          <CardContent class="space-y-3 pt-4">
            <Textarea
              v-model="sample"
              :rows="3"
              placeholder="例：我推开钟楼的门，雨声被关在身后，第十三下钟声还没落下…"
              class="resize-none text-[13px]"
            />
            <div class="flex items-center gap-2">
              <Button size="sm" variant="secondary" :disabled="previewing" @click="runPreview">
                <Loader2 v-if="previewing" class="size-3.5 animate-spin" />
                <FlaskConical v-else class="size-3.5" />
                预演
              </Button>
              <span v-if="preview" class="text-xs text-muted-foreground">
                命中 <span class="text-foreground">{{ preview.hitCount }}</span> /
                {{ preview.candidateCount }} 条启用条目 · 约
                <span class="text-foreground">{{ preview.usedTokens }}</span> tokens（预算
                {{ preview.budgetTokens }}）
              </span>
            </div>

            <div v-if="preview" class="space-y-2">
              <div
                v-for="h in preview.hits"
                :key="h.id"
                class="rounded-lg border border-border/60 bg-secondary/30 px-3 py-2"
              >
                <div class="flex items-center justify-between gap-2">
                  <p class="truncate text-xs font-medium">{{ h.title }}</p>
                  <span class="shrink-0 text-[10px] text-muted-foreground">
                    {{ posLabel(h.position) }} · {{ h.tokens }} tokens
                  </span>
                </div>
                <p class="mt-1 line-clamp-2 text-[11px] leading-relaxed text-muted-foreground">
                  {{ h.content }}
                </p>
              </div>
              <p v-if="!preview.hits.length" class="text-xs text-muted-foreground">
                这段话没有触发任何条目。把触发词原样写进句子里再试，或者检查条目是不是被关掉了。
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  ArrowLeft,
  BookOpen,
  Check,
  Download,
  FlaskConical,
  Globe,
  Loader2,
  Plus,
  Search,
  Trash2,
  Upload,
  Wand2,
} from 'lucide-vue-next'
import { getCharacterDetail } from '../api/character'
import {
  createGlobalEntry,
  createWorldEntry,
  deleteWorldEntry,
  exportWorldBook,
  getWorldBook,
  importWorldBook,
  previewWorldBook,
  reorderWorldEntries,
  toggleWorldEntry,
  updateWorldEntry,
} from '../api/worldbook'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'

const POSITIONS = [
  { value: 'before_char', label: '人设前', desc: '当世界观底色，先于角色设定' },
  { value: 'after_char', label: '人设后', desc: '默认，跟着角色设定走' },
  { value: 'in_chat', label: '对话内', desc: '插进历史中间，影响最强' },
]

const route = useRoute()
const router = useRouter()

const characterId = computed(() => Number(route.params.id))
const characterName = ref('')
const loading = ref(true)
const saving = ref(false)
const previewing = ref(false)
const keyword = ref('')
const entries = ref([])
const globalEntries = ref([])
const form = ref(null)
const sample = ref('')
const preview = ref(null)
const confirmingDelete = ref(false)
let confirmTimer = null

const importInput = ref(null)
const importing = ref(false)
/** 正在拖拽的条目 id；null 表示没有拖拽在进行 */
const draggedId = ref(null)

const estTokens = computed(() => Math.ceil((form.value?.content?.length || 0) / 1.5))

const filteredCharacter = computed(() => filterList(entries.value))
const filteredGlobal = computed(() => filterList(globalEntries.value))

function filterList(list) {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list
  return list.filter(
    (e) =>
      (e.title || '').toLowerCase().includes(k) || (e.keys || '').toLowerCase().includes(k),
  )
}

function posLabel(position) {
  return POSITIONS.find((p) => p.value === position)?.label || '人设后'
}

function blankForm(scope) {
  return {
    id: null,
    scope,
    title: '',
    keys: '',
    content: '',
    position: 'after_char',
    depth: 4,
    orderNo: 100,
    probability: 100,
    constant: false,
    enabled: true,
  }
}

function startNew(scope) {
  form.value = blankForm(scope)
  preview.value = null
  confirmingDelete.value = false
}

function select(entry, scope) {
  form.value = {
    id: entry.id,
    scope,
    title: entry.title || '',
    keys: entry.keys || '',
    content: entry.content || '',
    position: entry.position || 'after_char',
    depth: entry.depth ?? 4,
    orderNo: entry.orderNo ?? 100,
    probability: entry.probability ?? 100,
    constant: entry.constantOn === 1,
    enabled: entry.enabled !== 0,
  }
  confirmingDelete.value = false
}

async function load() {
  loading.value = true
  try {
    const res = await getWorldBook(characterId.value)
    entries.value = res?.entries || []
    globalEntries.value = res?.global || []
  } catch (e) {
    toast.error(e?.message || '世界书加载失败')
    router.push(`/character/${characterId.value}`)
  } finally {
    loading.value = false
  }
}

async function save() {
  const f = form.value
  if (!f.title.trim()) return toast.warning('先给条目起个备注名')
  if (!f.content.trim()) return toast.warning('插入内容不能为空')
  if (!f.constant && !f.keys.trim()) return toast.warning('非常量条目必须填触发词')

  const payload = {
    title: f.title.trim(),
    keys: f.constant ? '' : f.keys.trim(),
    content: f.content,
    orderNo: Number(f.orderNo) || 100,
    position: f.position,
    depth: Number(f.depth) || 0,
    probability: f.probability === '' ? 100 : Number(f.probability),
    constant: f.constant,
    enabled: f.enabled,
  }

  saving.value = true
  try {
    let saved
    if (f.id) {
      saved = await updateWorldEntry(f.id, payload)
    } else if (f.scope === 'global') {
      saved = await createGlobalEntry(payload)
    } else {
      saved = await createWorldEntry(characterId.value, payload)
    }
    await load()
    const list = f.scope === 'global' ? globalEntries.value : entries.value
    const found = list.find((e) => e.id === saved?.id)
    if (found) select(found, f.scope)
    toast.success('已保存')
  } catch (e) {
    toast.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 删除做两步确认：按钮文字先变，再点才真删 */
async function remove() {
  if (!form.value?.id) return
  if (!confirmingDelete.value) {
    confirmingDelete.value = true
    clearTimeout(confirmTimer)
    confirmTimer = setTimeout(() => (confirmingDelete.value = false), 4000)
    return
  }
  clearTimeout(confirmTimer)
  try {
    await deleteWorldEntry(form.value.id)
    toast.success('已删除')
    form.value = null
    preview.value = null
    await load()
  } catch (e) {
    toast.error(e?.message || '删除失败')
  } finally {
    confirmingDelete.value = false
  }
}

/** 列表上的一键开关：乐观更新，失败回滚（配置都还在，改的只是 enabled） */
async function toggleEnabled(entry, val) {
  const next = val ? 1 : 0
  if (entry.enabled === next) return
  const prev = entry.enabled
  entry.enabled = next
  try {
    await toggleWorldEntry(entry.id, !!val)
  } catch (e) {
    entry.enabled = prev
    toast.error(e?.message || '切换失败')
  }
}

/** 拖拽按「条目 id」而不是列表下标走：搜索过滤时下标会错位，id 不会 */
function onDragStart(entry, event) {
  draggedId.value = entry.id
  event.dataTransfer.effectAllowed = 'move'
  try {
    event.dataTransfer.setData('text/plain', String(entry.id))
  } catch {
    /* Firefox 需要 setData 才启动拖拽，失败不影响主流程 */
  }
}

function onDropTo(targetId) {
  const fromId = draggedId.value
  draggedId.value = null
  if (fromId == null || fromId === targetId) return
  const list = [...entries.value]
  const fromIdx = list.findIndex((e) => e.id === fromId)
  const toIdx = list.findIndex((e) => e.id === targetId)
  if (fromIdx < 0 || toIdx < 0) return
  const [moved] = list.splice(fromIdx, 1)
  list.splice(toIdx, 0, moved)
  entries.value = list
  persistOrder(list)
}

async function persistOrder(list) {
  try {
    await reorderWorldEntries(
      characterId.value,
      list.map((e) => e.id),
    )
  } catch (e) {
    toast.error(e?.message || '排序保存失败')
    await load()
  }
}

async function exportBook() {
  try {
    const data = await exportWorldBook(characterId.value)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `世界书-${characterName.value || characterId.value}.json`
    a.click()
    URL.revokeObjectURL(url)
    toast.success('已导出为 JSON 文件')
  } catch (e) {
    toast.error(e?.message || '导出失败')
  }
}

async function onImportPicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  importing.value = true
  try {
    const parsed = JSON.parse(await file.text())
    const items = Array.isArray(parsed) ? parsed : parsed?.entries
    if (!Array.isArray(items) || !items.length) {
      throw new Error('文件里没有条目，请选择本系统导出的世界书 JSON')
    }
    const res = await importWorldBook(characterId.value, items)
    await load()
    const imported = res?.imported ?? 0
    const skipped = res?.skipped ?? 0
    toast.success(`已导入 ${imported} 条${skipped ? `，跳过 ${skipped} 条脏数据` : ''}`)
  } catch (e) {
    toast.error(e?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function fillSample() {
  const source = entries.value.find((e) => e.keys) || globalEntries.value.find((e) => e.keys)
  const key = source?.keys?.split(',')[0]?.trim()
  sample.value = key
    ? `我推开门，${key}的影子在雨里拉得很长。`
    : '我推开门，雨声被关在身后。'
}

async function runPreview() {
  if (!sample.value.trim()) return toast.warning('先写一段用来测试的话')
  previewing.value = true
  try {
    preview.value = await previewWorldBook(characterId.value, sample.value.trim(), 1500)
  } catch (e) {
    toast.error(e?.message || '预演失败')
  } finally {
    previewing.value = false
  }
}

onMounted(async () => {
  try {
    const c = await getCharacterDetail(characterId.value)
    characterName.value = c?.name || ''
  } catch {
    /* 名字拿不到不影响管理条目 */
  }
  await load()
  // 默认打开第一条，避免右侧空着还要用户多点一下
  if (entries.value.length) select(entries.value[0], 'character')
  else if (globalEntries.value.length) select(globalEntries.value[0], 'global')
})
</script>
