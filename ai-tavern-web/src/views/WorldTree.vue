<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div class="space-y-1.5">
        <h1 class="text-2xl font-semibold tracking-tight">世界树</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          用 AI 搭起世界观骨架，再逐层展开时代、地域、势力与人物线索。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Tabs v-model="viewMode">
          <TabsList class="h-9">
            <TabsTrigger value="graph">
              <Network class="size-4" />
              图形
            </TabsTrigger>
            <TabsTrigger value="list">
              <ListTree class="size-4" />
              列表
            </TabsTrigger>
          </TabsList>
        </Tabs>
        <Button variant="outline" @click="addRoot">
          <Plus class="size-4" />
          新建根节点
        </Button>
        <Button :disabled="!selected" @click="spawnCharacterHint">
          <UserPlus class="size-4" />
          以此创建角色
        </Button>
      </div>
    </header>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <div class="min-w-0 space-y-6">
        <!-- AI 生成 -->
        <Card>
          <CardHeader>
            <CardTitle class="text-base">AI 生成世界树</CardTitle>
            <CardDescription>
              写一段世界观想法，AI 会一次生成多层结构；之后也可以单独扩展任意节点。
            </CardDescription>
            <CardAction>
              <Badge variant="secondary" class="font-normal">跟随想法语言</Badge>
            </CardAction>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="space-y-2">
              <Label for="world-idea">世界观想法</Label>
              <Textarea
                id="world-idea"
                v-model="aiIdea"
                :rows="3"
                placeholder="例如：永夜港口，真名会留下银色足迹，潮汐由失落的钟声驱动…"
              />
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button variant="outline" size="sm" class="text-muted-foreground">
                    <Languages class="size-4" />
                    {{ languageLabel }}
                    <ChevronDown class="size-3.5 opacity-60" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start" class="w-48">
                  <DropdownMenuLabel>输出语言</DropdownMenuLabel>
                  <DropdownMenuItem
                    v-for="opt in languageOptions"
                    :key="opt.value || 'auto'"
                    class="cursor-pointer"
                    @select="aiLanguage = opt.value"
                  >
                    <Check v-if="aiLanguage === opt.value" class="size-4" />
                    <span v-else class="size-4" />
                    {{ opt.label }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button variant="outline" size="sm" class="text-muted-foreground">
                    <Layers class="size-4" />
                    {{ depthLabel }}
                    <ChevronDown class="size-3.5 opacity-60" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start" class="w-52">
                  <DropdownMenuLabel>生成深度</DropdownMenuLabel>
                  <DropdownMenuItem
                    v-for="opt in depthOptions"
                    :key="opt.value"
                    class="cursor-pointer"
                    @select="aiDepth = opt.value"
                  >
                    <Check v-if="aiDepth === opt.value" class="size-4" />
                    <span v-else class="size-4" />
                    {{ opt.label }}
                    <span class="ml-auto text-xs text-muted-foreground">{{ opt.hint }}</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              <Tabs v-model="aiMode" class="w-fit">
                <TabsList class="h-8">
                  <TabsTrigger value="replace">替换当前树</TabsTrigger>
                  <TabsTrigger value="append">追加新世界</TabsTrigger>
                </TabsList>
              </Tabs>

              <div class="flex flex-wrap items-center gap-2 sm:ml-auto">
                <Button
                  variant="outline"
                  :disabled="!selected || generating || expanding"
                  @click="expandSelected"
                >
                  <LoaderCircle v-if="expanding" class="size-4 animate-spin" />
                  <WandSparkles v-else class="size-4" />
                  {{ expanding ? '扩展中…' : 'AI 扩展选中' }}
                </Button>
                <Button :disabled="generating || expanding" @click="generateWorld">
                  <LoaderCircle v-if="generating" class="size-4 animate-spin" />
                  <Sparkles v-else class="size-4" />
                  {{ generating ? '生成中…' : '生成世界树' }}
                </Button>
              </div>
            </div>

            <p class="text-xs text-muted-foreground">
              生成通常需要数十秒。结果会保存在本机，刷新页面不会丢失。
            </p>
          </CardContent>
        </Card>

        <!-- 树 / 图 -->
        <Card class="gap-0 overflow-hidden py-0">
          <CardHeader class="border-b border-border/60 pt-5">
            <CardTitle class="text-base">世界结构</CardTitle>
            <CardDescription>
              <template v-if="tree.length">{{ worldCount }} 个世界 · {{ totalNodes }} 个节点</template>
              <template v-else>还没有节点</template>
            </CardDescription>
            <CardAction v-if="viewMode === 'list' && tree.length">
              <Button variant="ghost" size="xs" class="text-muted-foreground" @click="toggleCollapseAll">
                {{ hasCollapsed ? '全部展开' : '全部收起' }}
              </Button>
            </CardAction>
          </CardHeader>

          <div
            v-if="generating || expanding"
            class="flex items-center gap-2 border-b border-border/60 bg-secondary/30 px-4 py-2 text-xs text-muted-foreground"
          >
            <LoaderCircle class="size-3.5 animate-spin" />
            {{ generating ? '正在生成世界树，请稍候…' : `正在扩展「${expandingLabel || '节点'}」…` }}
          </div>

          <CardContent class="p-0">
            <div v-if="generating && !tree.length" class="space-y-4 p-6">
              <Skeleton class="h-5 w-40" />
              <Skeleton class="h-20 w-full rounded-xl" />
              <div class="ml-6 space-y-3">
                <Skeleton class="h-16 w-3/4 rounded-xl" />
                <Skeleton class="h-16 w-2/3 rounded-xl" />
              </div>
              <div class="ml-12 space-y-3">
                <Skeleton class="h-14 w-2/3 rounded-xl" />
                <Skeleton class="h-14 w-1/2 rounded-xl" />
              </div>
            </div>

            <div
              v-else-if="!tree.length"
              class="flex flex-col items-center justify-center gap-4 px-6 py-20 text-center"
            >
              <span class="grid size-14 place-items-center rounded-2xl border border-border/70 bg-secondary">
                <Sprout class="size-6 text-muted-foreground" />
              </span>
              <div class="max-w-sm space-y-1">
                <p class="text-sm font-medium">世界树还是空的</p>
                <p class="text-sm text-muted-foreground">
                  写一段世界观想法，让 AI 一次生成多层结构；也可以先建一个根节点，自己慢慢填。
                </p>
              </div>
              <div class="flex flex-wrap justify-center gap-2">
                <Button @click="startGenerate">
                  <Sparkles class="size-4" />
                  AI 生成世界树
                </Button>
                <Button variant="outline" @click="addRoot">
                  <Plus class="size-4" />
                  新建根节点
                </Button>
              </div>
            </div>

            <div v-else-if="viewMode === 'graph'" class="h-[560px] sm:h-[620px]">
              <WorldTreeGraph
                :tree="tree"
                :selected-id="selected?.id"
                :type-labels="typeLabelMap"
                class="h-full"
                @select="selectById"
                @add-child="addChildById"
                @remove="removeById"
                @add-root="addRoot"
              />
            </div>

            <div v-else class="max-h-[640px] overflow-y-auto p-2 sm:p-3">
              <ul class="space-y-1">
                <li
                  v-for="row in visibleRows"
                  :key="row.node.id"
                  :style="{ paddingLeft: `${row.depth * 20}px` }"
                >
                  <div
                    class="group flex items-start gap-1 rounded-xl border px-2 py-2 transition-colors"
                    :class="
                      selected?.id === row.node.id
                        ? 'border-border bg-secondary'
                        : 'border-transparent hover:border-border/60 hover:bg-secondary/40'
                    "
                  >
                    <Button
                      v-if="row.node.children?.length"
                      variant="ghost"
                      size="icon-xs"
                      class="mt-0.5 shrink-0 text-muted-foreground"
                      :title="isCollapsed(row.node.id) ? '展开子节点' : '收起子节点'"
                      @click="toggleCollapsed(row.node.id)"
                    >
                      <ChevronRight
                        class="transition-transform"
                        :class="{ 'rotate-90': !isCollapsed(row.node.id) }"
                      />
                    </Button>
                    <span v-else class="mt-0.5 size-6 shrink-0" />

                    <button
                      type="button"
                      class="min-w-0 flex-1 rounded-md px-1 py-0.5 text-left outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                      @click="onSelect(row.node)"
                    >
                      <span class="flex min-w-0 items-center gap-2">
                        <span class="truncate text-sm font-medium">{{ row.node.label || '未命名' }}</span>
                        <Badge variant="outline" class="shrink-0 font-normal">
                          {{ typeLabel(row.node.type) }}
                        </Badge>
                      </span>
                      <span
                        v-if="row.node.summary"
                        class="mt-0.5 block truncate text-xs text-muted-foreground"
                      >
                        {{ row.node.summary }}
                      </span>
                    </button>

                    <div
                      class="flex shrink-0 items-center gap-0.5 transition-opacity sm:opacity-0 sm:group-focus-within:opacity-100 sm:group-hover:opacity-100"
                      :class="selected?.id === row.node.id ? 'sm:opacity-100' : ''"
                    >
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        title="添加子节点"
                        @click.stop="addChild(row.node)"
                      >
                        <Plus class="size-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        title="AI 扩展"
                        :disabled="generating || expanding"
                        @click.stop="expandNode(row.node)"
                      >
                        <WandSparkles class="size-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        class="text-muted-foreground hover:text-destructive"
                        title="删除节点"
                        @click.stop="removeNode(row.node)"
                      >
                        <Trash2 class="size-3.5" />
                      </Button>
                    </div>
                  </div>
                </li>
              </ul>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- 节点编辑 -->
      <aside class="lg:sticky lg:top-20 lg:self-start">
        <Card>
          <CardHeader>
            <CardTitle class="text-base">节点编辑</CardTitle>
            <CardDescription>名称、类型与摘要都会保存在本机。</CardDescription>
            <CardAction v-if="selected">
              <Badge variant="outline" class="font-normal">{{ typeLabel(selected.type) }}</Badge>
            </CardAction>
          </CardHeader>

          <CardContent v-if="selected" class="space-y-5">
            <div class="space-y-2">
              <Label for="node-label">名称</Label>
              <Input id="node-label" v-model="selected.label" @change="persist" />
            </div>

            <div class="space-y-2">
              <Label>类型</Label>
              <div class="flex flex-wrap gap-1.5">
                <Button
                  v-for="t in nodeTypes"
                  :key="t.value"
                  type="button"
                  size="xs"
                  class="rounded-full"
                  :variant="selected.type === t.value ? 'secondary' : 'outline'"
                  @click="setNodeType(t.value)"
                >
                  {{ t.label }}
                </Button>
              </div>
            </div>

            <div class="space-y-2">
              <Label for="node-summary">设定摘要</Label>
              <Textarea
                id="node-summary"
                v-model="selected.summary"
                :rows="8"
                placeholder="历史、法则、氛围、与其他节点的关系…"
                @change="persist"
              />
            </div>

            <div class="flex flex-wrap gap-2 border-t border-border/60 pt-4">
              <Button variant="outline" size="sm" @click="addChild(selected)">
                <Plus class="size-4" />
                添加子节点
              </Button>
              <Button
                variant="outline"
                size="sm"
                :disabled="generating || expanding"
                @click="expandSelected"
              >
                <LoaderCircle v-if="expanding" class="size-4 animate-spin" />
                <WandSparkles v-else class="size-4" />
                AI 扩展
              </Button>
              <Button
                variant="ghost"
                size="sm"
                class="text-muted-foreground hover:text-destructive"
                @click="removeNode(selected)"
              >
                <Trash2 class="size-4" />
                删除
              </Button>
            </div>
          </CardContent>

          <CardContent v-else class="flex flex-col items-center gap-3 py-10 text-center">
            <span class="grid size-10 place-items-center rounded-xl bg-secondary">
              <MousePointerClick class="size-4 text-muted-foreground" />
            </span>
            <div class="space-y-1">
              <p class="text-sm font-medium">未选择节点</p>
              <p class="text-xs text-muted-foreground">在图形或列表里点一个节点开始编辑</p>
            </div>
          </CardContent>
        </Card>
      </aside>
    </div>

    <!-- 确认弹窗：替换树 / 删除节点共用 -->
    <Dialog v-model:open="confirmState.open" @update:open="onConfirmOpenChange">
      <DialogContent class="rounded-xl sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ confirmState.title }}</DialogTitle>
          <DialogDescription>{{ confirmState.description }}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="settleConfirm(false)">取消</Button>
          <Button
            :variant="confirmState.destructive ? 'destructive' : 'default'"
            @click="settleConfirm(true)"
          >
            {{ confirmState.confirmText }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  Check,
  ChevronDown,
  ChevronRight,
  Languages,
  Layers,
  ListTree,
  LoaderCircle,
  MousePointerClick,
  Network,
  Plus,
  Sparkles,
  Sprout,
  Trash2,
  UserPlus,
  WandSparkles,
} from 'lucide-vue-next'
import { workshopGenerateWorldTree, workshopExpandWorldNode } from '../api/workshop'
import WorldTreeGraph from '../components/WorldTreeGraph.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'

const STORAGE_KEY = 'tavern_world_tree'
const IDEA_KEY = 'tavern_world_tree_idea'
const router = useRouter()

const nodeTypes = [
  { value: 'world', label: '世界' },
  { value: 'era', label: '时代' },
  { value: 'region', label: '地域' },
  { value: 'faction', label: '势力' },
  { value: 'lore', label: '传说/法则' },
  { value: 'figure', label: '人物线索' },
]

const languageOptions = [
  { value: '', label: '跟随想法' },
  { value: '中文', label: '中文' },
  { value: 'English', label: 'English' },
  { value: '日本語', label: '日本語' },
]

const depthOptions = [
  { value: 2, label: '浅层', hint: '约 2 层' },
  { value: 3, label: '标准', hint: '约 3 层' },
  { value: 4, label: '丰富', hint: '约 4 层' },
]

const typeLabelMap = computed(() =>
  Object.fromEntries(nodeTypes.map((t) => [t.value, t.label])),
)

const tree = ref(loadTree())
const selected = ref(null)
const viewMode = ref('graph')
const aiIdea = ref(localStorage.getItem(IDEA_KEY) || '')
const aiLanguage = ref('')
const aiDepth = ref(3)
const aiMode = ref('replace')
const generating = ref(false)
const expanding = ref(false)
const expandingLabel = ref('')

const collapsedIds = ref(new Set())
let pendingConfirm = null

const confirmState = reactive({
  open: false,
  title: '',
  description: '',
  confirmText: '确认',
  destructive: false,
})

const languageLabel = computed(
  () => languageOptions.find((o) => o.value === aiLanguage.value)?.label || '跟随想法',
)
const depthLabel = computed(
  () => depthOptions.find((o) => o.value === aiDepth.value)?.label || '标准',
)
const totalNodes = computed(() => countNodes(tree.value))
const worldCount = computed(() => tree.value.length)

const parentIds = computed(() => {
  const ids = []
  const walk = (list) => {
    for (const n of list || []) {
      if (n.children?.length) ids.push(n.id)
      walk(n.children || [])
    }
  }
  walk(tree.value)
  return ids
})

const hasCollapsed = computed(() => parentIds.value.some((id) => collapsedIds.value.has(id)))

/** 列表视图把树拍平成带缩进的行，折叠的子树不展开 */
const visibleRows = computed(() => {
  const rows = []
  const walk = (list, depth) => {
    for (const node of list || []) {
      rows.push({ node, depth })
      if (node.children?.length && !collapsedIds.value.has(node.id)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(tree.value, 0)
  return rows
})

function typeLabel(t) {
  return nodeTypes.find((x) => x.value === t)?.label || t
}

function uid() {
  return `w-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function loadTree() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (Array.isArray(raw) && raw.length) return raw
  } catch {
    /* ignore */
  }
  return []
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tree.value))
  localStorage.setItem(IDEA_KEY, aiIdea.value || '')
}

function findNodeById(list, id) {
  for (const n of list || []) {
    if (n.id === id) return n
    const hit = findNodeById(n.children || [], id)
    if (hit) return hit
  }
  return null
}

function onSelect(data) {
  selected.value = data
}

function selectById(id) {
  selected.value = findNodeById(tree.value, id)
}

function addRoot() {
  const node = {
    id: uid(),
    label: '未命名世界',
    type: 'world',
    summary: '',
    children: [],
  }
  tree.value.push(node)
  selected.value = node
  persist()
}

function addChild(parent) {
  if (!parent) return
  if (!parent.children) parent.children = []
  const node = {
    id: uid(),
    label: '新节点',
    type: parent.type === 'world' ? 'era' : parent.type === 'era' ? 'region' : 'lore',
    summary: '',
    children: [],
  }
  parent.children.push(node)
  selected.value = node
  ensureExpanded(parent.id)
  persist()
}

function addChildById(id) {
  const parent = findNodeById(tree.value, id)
  if (parent) addChild(parent)
}

async function removeNode(node) {
  if (!node) return
  const confirmed = await askConfirm({
    title: '删除节点',
    description: `删除「${node.label}」及其全部子节点？此操作无法撤销。`,
    confirmText: '删除',
    destructive: true,
  })
  if (!confirmed) return
  const strip = (list) => {
    const next = list.filter((n) => n.id !== node.id)
    next.forEach((n) => {
      if (n.children?.length) n.children = strip(n.children)
    })
    return next
  }
  tree.value = strip(tree.value)
  if (selected.value?.id === node.id) selected.value = null
  persist()
}

function removeById(id) {
  const node = findNodeById(tree.value, id)
  if (node) removeNode(node)
}

function ensureIds(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes.map((n) => ({
    id: n.id || uid(),
    label: n.label || '未命名',
    type: n.type || 'lore',
    summary: n.summary || '',
    children: ensureIds(n.children || []),
  }))
}

async function generateWorld() {
  if (!aiIdea.value.trim()) {
    toast.warning('先写一点世界观想法')
    return
  }
  if (aiMode.value === 'replace' && tree.value.length) {
    const confirmed = await askConfirm({
      title: '替换当前世界树',
      description: '将用 AI 生成的结果替换当前世界树，现有节点会被覆盖。',
      confirmText: '替换',
      destructive: true,
    })
    if (!confirmed) return
  }
  generating.value = true
  try {
    const res = await workshopGenerateWorldTree({
      idea: aiIdea.value,
      language: aiLanguage.value || undefined,
      depth: aiDepth.value,
    })
    const generated = ensureIds(res.tree || [])
    if (!generated.length) {
      toast.error('未生成有效世界树')
      return
    }
    if (aiMode.value === 'append') {
      tree.value = [...tree.value, ...generated]
    } else {
      tree.value = generated
    }
    selected.value = tree.value[0] || null
    viewMode.value = 'graph'
    collapsedIds.value = new Set()
    persist()
    toast.success(`已生成 ${countNodes(generated)} 个节点`)
  } catch {
    /* interceptor */
  } finally {
    generating.value = false
  }
}

async function expandSelected() {
  if (!selected.value) return
  // 请求期间用户可能切换选中节点，先把目标节点固定下来
  const target = selected.value
  expanding.value = true
  expandingLabel.value = target.label
  try {
    const res = await workshopExpandWorldNode({
      node: {
        label: target.label,
        type: target.type,
        summary: target.summary,
      },
      hint: aiIdea.value || '',
      language: aiLanguage.value || undefined,
    })
    const children = ensureIds(res.children || [])
    if (!children.length) {
      toast.warning('没有生成子节点')
      return
    }
    if (!target.children) target.children = []
    target.children.push(...children)
    ensureExpanded(target.id)
    persist()
    toast.success(`已为「${target.label}」扩展 ${children.length} 个子节点`)
  } catch {
    /* */
  } finally {
    expanding.value = false
    expandingLabel.value = ''
  }
}

/** 从列表 / 图形里直接对某个节点发起 AI 扩展 */
async function expandNode(node) {
  if (!node || generating.value || expanding.value) return
  selected.value = node
  await expandSelected()
}

function startGenerate() {
  if (!aiIdea.value.trim()) {
    toast.warning('先写一点世界观想法')
    document.getElementById('world-idea')?.focus()
    return
  }
  generateWorld()
}

function setNodeType(value) {
  if (!selected.value) return
  selected.value.type = value
  persist()
}

function isCollapsed(id) {
  return collapsedIds.value.has(id)
}

function toggleCollapsed(id) {
  const next = new Set(collapsedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsedIds.value = next
}

function ensureExpanded(id) {
  if (!collapsedIds.value.has(id)) return
  const next = new Set(collapsedIds.value)
  next.delete(id)
  collapsedIds.value = next
}

function toggleCollapseAll() {
  collapsedIds.value = hasCollapsed.value ? new Set() : new Set(parentIds.value)
}

function askConfirm({ title, description, confirmText = '确认', destructive = false }) {
  if (pendingConfirm) settleConfirm(false)
  return new Promise((resolve) => {
    pendingConfirm = resolve
    Object.assign(confirmState, { open: true, title, description, confirmText, destructive })
  })
}

function settleConfirm(value) {
  const resolve = pendingConfirm
  pendingConfirm = null
  confirmState.open = false
  resolve?.(value)
}

function onConfirmOpenChange(open) {
  if (!open) settleConfirm(false)
}

function countNodes(nodes) {
  let n = 0
  const walk = (list) => {
    for (const x of list || []) {
      n += 1
      walk(x.children)
    }
  }
  walk(nodes)
  return n
}

function collectContext(node) {
  const lines = []
  const walk = (n, depth) => {
    lines.push(`${'  '.repeat(depth)}[${typeLabel(n.type)}] ${n.label}${n.summary ? ` — ${n.summary}` : ''}`)
    ;(n.children || []).forEach((c) => walk(c, depth + 1))
  }
  let root = node
  while (true) {
    const parent = findParent(tree.value, root.id)
    if (!parent) break
    root = parent
  }
  walk(root, 0)
  return lines.join('\n')
}

function findParent(list, childId, parent = null) {
  for (const n of list) {
    if (n.id === childId) return parent
    const hit = findParent(n.children || [], childId, n)
    if (hit) return hit
  }
  return null
}

function spawnCharacterHint() {
  if (!selected.value) return
  const ctx = collectContext(selected.value)
  sessionStorage.setItem(
    'tavern_world_spawn',
    JSON.stringify({
      worldName: selected.value.label,
      context: ctx,
      at: Date.now(),
    }),
  )
  toast.success('已带上世界观摘要，跳转创建角色')
  router.push({ path: '/create', query: { from: 'world-tree' } })
}
</script>
