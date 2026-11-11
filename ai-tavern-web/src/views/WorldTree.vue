<template>
  <div class="space-y-4">
    <!-- 顶栏：世界名 + 三透镜 + 精简动作 -->
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div class="min-w-0 flex-1 space-y-1">
        <div class="flex flex-wrap items-center gap-2">
          <Input
            v-model="worldName"
            class="h-9 max-w-md border-transparent bg-transparent px-0 text-xl font-semibold tracking-tight shadow-none focus-visible:border-border focus-visible:bg-background focus-visible:px-3"
            placeholder="世界名称"
            @change="persist"
          />
          <Badge v-if="cloudId" variant="outline" class="font-normal">
            {{ cloudStatusLabel }} · #{{ cloudId }}
          </Badge>
        </div>
        <p class="text-xs text-muted-foreground">
          <template v-if="tree.length">
            {{ filledCount }} / {{ totalNodes }} 已充实 · 本机已自动保存
          </template>
          <template v-else>写 / 骨架 / 一览同页切换，少卡片多正文</template>
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <Tabs v-model="viewMode">
          <TabsList class="h-9">
            <TabsTrigger value="write">写</TabsTrigger>
            <TabsTrigger value="skeleton">骨架</TabsTrigger>
            <TabsTrigger value="browse">一览</TabsTrigger>
          </TabsList>
        </Tabs>

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button variant="outline" size="sm">
              更多
              <ChevronDown class="size-3.5 opacity-60" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-48">
            <DropdownMenuItem class="cursor-pointer" @select="router.push('/worlds')">
              世界广场
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="router.push('/my-worlds')">
              我的世界树
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="loadShowMeYourWays">
              打开示我周行
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="addRoot">
              新建根节点
            </DropdownMenuItem>
            <DropdownMenuItem
              class="cursor-pointer"
              :disabled="!selected"
              @select="spawnCharacterHint"
            >
              以此创建角色
            </DropdownMenuItem>
            <DropdownMenuItem class="cursor-pointer" @select="aiOpen = true">
              AI 生成 / 扩展
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <Button
          variant="outline"
          size="sm"
          :disabled="savingCloud || !tree.length"
          @click="saveToCloud(false)"
        >
          <LoaderCircle v-if="savingCloud" class="size-4 animate-spin" />
          <CloudUpload v-else class="size-4" />
          {{ cloudId ? '保存' : '入库' }}
        </Button>
        <Button size="sm" :disabled="savingCloud || !tree.length" @click="saveToCloud(true)">
          发布
        </Button>
      </div>
    </header>

    <Input
      v-model="worldDescription"
      class="h-9 text-sm"
      placeholder="一两句简介（发布到广场时展示）"
      @change="persist"
    />

    <div
      v-if="generating || expanding"
      class="flex items-center gap-2 rounded-xl border border-border/60 bg-secondary/40 px-3 py-2 text-xs text-muted-foreground"
    >
      <LoaderCircle class="size-3.5 animate-spin" />
      {{ generating ? '正在生成世界树…' : `正在扩展「${expandingLabel || '节点'}」…` }}
    </div>

    <!-- 空树 -->
    <Card v-if="!tree.length && !generating" class="border-dashed">
      <CardContent class="flex flex-col items-center gap-4 py-16 text-center">
        <span class="grid size-14 place-items-center rounded-2xl border border-border/70 bg-secondary">
          <Sprout class="size-6 text-muted-foreground" />
        </span>
        <div class="max-w-sm space-y-1">
          <p class="text-sm font-medium">世界树还是空的</p>
          <p class="text-sm text-muted-foreground">
            打开「示我周行」，或用 AI 从想法生成，也可以先建一个根节点。
          </p>
        </div>
        <div class="flex flex-wrap justify-center gap-2">
          <Button @click="loadShowMeYourWays">
            <LibraryBig class="size-4" />
            打开示我周行
          </Button>
          <Button variant="outline" @click="aiOpen = true">
            <Sparkles class="size-4" />
            AI 生成
          </Button>
          <Button variant="outline" @click="addRoot">
            <Plus class="size-4" />
            新建根节点
          </Button>
        </div>
      </CardContent>
    </Card>

    <!-- 写：左大纲 + 中编辑 -->
    <div
      v-else-if="viewMode === 'write'"
      class="grid min-h-[640px] grid-cols-1 overflow-hidden rounded-xl border border-border/70 bg-card lg:grid-cols-[260px_minmax(0,1fr)]"
    >
      <aside class="flex max-h-[720px] flex-col border-b border-border/60 lg:border-b-0 lg:border-r">
        <div class="space-y-2 border-b border-border/60 p-3">
          <Input v-model="outlineQuery" class="h-8" placeholder="搜索节点…" />
          <div class="flex gap-1">
            <Button size="xs" variant="outline" class="flex-1" @click="addRoot">
              <Plus class="size-3.5" />
              根
            </Button>
            <Button
              size="xs"
              variant="outline"
              class="flex-1"
              :disabled="!selected"
              @click="addChild(selected)"
            >
              <Plus class="size-3.5" />
              子节点
            </Button>
          </div>
        </div>
        <nav class="flex-1 overflow-y-auto p-2" aria-label="写作大纲">
          <button
            v-for="row in filteredOutlineRows"
            :key="`w-${row.node.id}`"
            type="button"
            class="flex w-full items-center gap-1.5 rounded-lg px-2 py-1.5 text-left text-sm transition-colors"
            :style="{ paddingLeft: `${8 + row.depth * 12}px` }"
            :class="
              selected?.id === row.node.id
                ? 'bg-secondary font-medium text-foreground'
                : 'text-muted-foreground hover:bg-secondary/50 hover:text-foreground'
            "
            @click="onSelect(row.node)"
          >
            <span
              class="size-1.5 shrink-0 rounded-full"
              :class="pipClass(row.node)"
              :title="pipTitle(row.node)"
            />
            <span class="min-w-0 flex-1 truncate">{{ row.node.label || '未命名' }}</span>
            <span class="shrink-0 text-[10px] tabular-nums text-muted-foreground/80">
              {{ wordCount(row.node) || '' }}
            </span>
          </button>
        </nav>
        <p class="border-t border-border/60 px-3 py-2 text-[11px] text-muted-foreground">
          Tab 加子节点请用下方按钮 · 点节点开始写
        </p>
      </aside>

      <main class="flex max-h-[720px] flex-col">
        <template v-if="selected">
          <div class="space-y-3 border-b border-border/60 p-4 sm:p-5">
            <Input
              v-model="selected.label"
              class="h-auto border-transparent bg-transparent px-0 text-2xl font-semibold tracking-tight shadow-none focus-visible:border-border focus-visible:bg-background focus-visible:px-3"
              placeholder="节点标题"
              @change="persist"
            />
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
            <Textarea
              v-model="selected.summary"
              :rows="2"
              class="min-h-0 resize-none text-sm text-muted-foreground"
              placeholder="一行摘要（列表 / 卡片用）"
              @change="persist"
            />
          </div>
          <div class="flex-1 overflow-y-auto p-4 sm:p-5">
            <Label for="node-content-write" class="sr-only">正文</Label>
            <Textarea
              id="node-content-write"
              v-model="selected.content"
              :rows="18"
              class="min-h-[420px] resize-y border-0 bg-transparent p-0 text-[15px] leading-7 shadow-none focus-visible:ring-0"
              placeholder="在这里写设定正文…"
              @change="persist"
            />
          </div>
          <div
            class="flex flex-wrap items-center gap-2 border-t border-border/60 bg-secondary/20 px-4 py-2.5"
          >
            <Button size="sm" variant="outline" @click="addChild(selected)">
              <Plus class="size-4" />
              子节点
            </Button>
            <Button
              size="sm"
              variant="outline"
              :disabled="generating || expanding"
              @click="expandSelected"
            >
              <LoaderCircle v-if="expanding" class="size-4 animate-spin" />
              <WandSparkles v-else class="size-4" />
              AI 扩展
            </Button>
            <Button
              size="sm"
              variant="ghost"
              class="text-muted-foreground hover:text-destructive"
              @click="removeNode(selected)"
            >
              <Trash2 class="size-4" />
              删除
            </Button>
            <span class="ml-auto text-[11px] text-muted-foreground">
              {{ wordCount(selected) }} 字
            </span>
          </div>
        </template>
        <div v-else class="flex flex-1 flex-col items-center justify-center gap-3 p-10 text-center">
          <MousePointerClick class="size-5 text-muted-foreground" />
          <p class="text-sm text-muted-foreground">从左侧大纲点一个节点开始写</p>
        </div>
      </main>
    </div>

    <!-- 骨架：高密度大纲 + 空缺过滤 -->
    <Card v-else-if="viewMode === 'skeleton'" class="gap-0 overflow-hidden py-0">
      <CardHeader class="border-b border-border/60 pt-5">
        <CardTitle class="text-base">骨架</CardTitle>
        <CardDescription>扫结构、补空缺；点行可选中，再切回「写」改长文</CardDescription>
        <CardAction>
          <div class="flex flex-wrap items-center gap-2">
            <Button
              size="xs"
              :variant="emptyOnly ? 'secondary' : 'outline'"
              @click="emptyOnly = !emptyOnly"
            >
              只看空缺
            </Button>
            <Button variant="ghost" size="xs" class="text-muted-foreground" @click="toggleCollapseAll">
              {{ hasCollapsed ? '全部展开' : '全部收起' }}
            </Button>
          </div>
        </CardAction>
      </CardHeader>
      <CardContent class="max-h-[680px] space-y-1 overflow-y-auto p-3">
        <Input v-model="outlineQuery" class="mb-2 h-8" placeholder="筛选标题…" />
        <div
          v-for="row in skeletonRows"
          :key="`s-${row.node.id}`"
          class="group rounded-xl border px-2 py-1.5 transition-colors"
          :class="
            selected?.id === row.node.id
              ? 'border-border bg-secondary'
              : 'border-transparent hover:border-border/60 hover:bg-secondary/40'
          "
          :style="{ paddingLeft: `${8 + row.depth * 16}px` }"
        >
          <div class="flex items-center gap-2">
            <Button
              v-if="row.node.children?.length"
              variant="ghost"
              size="icon-xs"
              class="shrink-0 text-muted-foreground"
              @click="toggleCollapsed(row.node.id)"
            >
              <ChevronRight
                class="transition-transform"
                :class="{ 'rotate-90': !isCollapsed(row.node.id) }"
              />
            </Button>
            <span v-else class="size-6 shrink-0" />
            <span class="size-2 shrink-0 rounded-full" :class="pipClass(row.node)" />
            <button
              type="button"
              class="min-w-0 flex-1 truncate text-left text-sm font-medium"
              @click="selectAndMaybeWrite(row.node)"
            >
              {{ row.node.label || '未命名' }}
            </button>
            <Badge variant="outline" class="shrink-0 font-normal">
              {{ typeLabel(row.node.type) }}
            </Badge>
            <span class="w-14 shrink-0 text-right text-[11px] tabular-nums text-muted-foreground">
              {{ wordCount(row.node) ? `${wordCount(row.node)} 字` : '空' }}
            </span>
            <div class="flex shrink-0 opacity-100 sm:opacity-0 sm:group-hover:opacity-100">
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
            </div>
          </div>
        </div>
        <EmptyState v-if="!skeletonRows.length" text="没有匹配的节点" class="py-12" />
      </CardContent>
    </Card>

    <!-- 一览：读正文 -->
    <Card v-else-if="viewMode === 'browse'" class="gap-0 overflow-hidden py-0">
      <CardHeader class="border-b border-border/60 pt-5">
        <CardTitle class="text-base">一览</CardTitle>
        <CardDescription>像读设定集一样浏览；点标题可选中并回「写」</CardDescription>
      </CardHeader>
      <div class="grid max-h-[720px] grid-cols-1 md:grid-cols-[240px_minmax(0,1fr)]">
        <nav
          class="max-h-[720px] overflow-y-auto border-b border-border/60 p-3 md:border-b-0 md:border-r"
          aria-label="一览目录"
        >
          <ul class="space-y-0.5">
            <li v-for="row in overviewSections" :key="`toc-${row.node.id}`">
              <button
                type="button"
                class="w-full rounded-lg px-2 py-1.5 text-left text-sm transition-colors"
                :style="{ paddingLeft: `${8 + row.depth * 12}px` }"
                :class="
                  selected?.id === row.node.id
                    ? 'bg-secondary font-medium text-foreground'
                    : 'text-muted-foreground hover:bg-secondary/50 hover:text-foreground'
                "
                @click="scrollToOverviewNode(row.node.id)"
              >
                <span class="line-clamp-2">{{ row.node.label || '未命名' }}</span>
              </button>
            </li>
          </ul>
        </nav>
        <div ref="overviewScrollRef" class="max-h-[720px] overflow-y-auto px-5 py-6 sm:px-8">
          <article
            v-for="row in overviewSections"
            :id="`wt-node-${row.node.id}`"
            :key="`doc-${row.node.id}`"
            class="scroll-mt-4 border-b border-border/40 py-6 last:border-b-0"
          >
            <button type="button" class="group w-full text-left" @click="selectAndMaybeWrite(row.node)">
              <component
                :is="overviewHeadingTag(row.depth)"
                class="font-semibold tracking-tight group-hover:underline group-hover:underline-offset-4"
                :class="row.depth === 0 ? 'text-2xl' : row.depth === 1 ? 'text-xl' : 'text-lg'"
              >
                {{ row.node.label || '未命名' }}
              </component>
              <Badge variant="outline" class="mt-2 font-normal">
                {{ typeLabel(row.node.type) }}
              </Badge>
            </button>
            <div
              v-if="row.node.content || row.node.summary"
              class="chat-md mt-4 text-sm leading-7 text-muted-foreground"
              v-html="renderOverviewBody(row.node)"
            />
            <p v-else class="mt-4 text-sm text-muted-foreground">（暂无正文）</p>
          </article>
        </div>
      </div>
    </Card>

    <!-- AI 抽屉 -->
    <Dialog v-model:open="aiOpen">
      <DialogContent class="rounded-xl sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>AI 生成世界树</DialogTitle>
          <DialogDescription>
            写一段世界观想法，生成多层结构；也可对当前选中节点扩展子树。
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4">
          <Textarea
            id="world-idea"
            v-model="aiIdea"
            :rows="4"
            placeholder="例如：永夜港口，真名会留下银色足迹…"
          />
          <div class="flex flex-wrap gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="outline" size="sm">
                  <Languages class="size-4" />
                  {{ languageLabel }}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
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
                <Button variant="outline" size="sm">
                  <Layers class="size-4" />
                  {{ depthLabel }}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                <DropdownMenuItem
                  v-for="opt in depthOptions"
                  :key="opt.value"
                  class="cursor-pointer"
                  @select="aiDepth = opt.value"
                >
                  {{ opt.label }}
                  <span class="ml-auto text-xs text-muted-foreground">{{ opt.hint }}</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <Tabs v-model="aiMode" class="w-fit">
              <TabsList class="h-8">
                <TabsTrigger value="replace">替换</TabsTrigger>
                <TabsTrigger value="append">追加</TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        </div>
        <DialogFooter class="gap-2 sm:gap-2">
          <Button
            variant="outline"
            :disabled="!selected || generating || expanding"
            @click="expandSelected"
          >
            <WandSparkles class="size-4" />
            扩展选中
          </Button>
          <Button :disabled="generating || expanding" @click="generateWorld">
            <LoaderCircle v-if="generating" class="size-4 animate-spin" />
            <Sparkles v-else class="size-4" />
            生成世界树
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import {
  Check,
  ChevronDown,
  ChevronRight,
  CloudUpload,
  Languages,
  Layers,
  LibraryBig,
  LoaderCircle,
  MousePointerClick,
  Plus,
  Sparkles,
  Sprout,
  Trash2,
  WandSparkles,
} from 'lucide-vue-next'
import { workshopGenerateWorldTree, workshopExpandWorldNode } from '../api/workshop'
import {
  createWorld,
  getWorldDetail,
  publishWorld,
  updateWorld,
} from '../api/worldTree'
import {
  createShowMeYourWaysWorldTree,
  isStaleShowMeYourWaysTree,
  SHOW_ME_YOUR_WAYS_META,
} from '../data/showMeYourWaysWorldTree'
import { renderMarkdown } from '../utils/markdown'
import EmptyState from '../components/EmptyState.vue'
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
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'

const STORAGE_KEY = 'tavern_world_tree'
const IDEA_KEY = 'tavern_world_tree_idea'
const VERSION_KEY = 'tavern_world_tree_smyw_version'
const FILL_OK = 200

const route = useRoute()
const router = useRouter()

const nodeTypes = [
  { value: 'world', label: '世界' },
  { value: 'calendar', label: '历法' },
  { value: 'chapter', label: '篇章' },
  { value: 'race', label: '种族' },
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

const tree = ref(loadTree())
const selected = ref(tree.value[0] || null)
const viewMode = ref(tree.value.length ? 'write' : 'write')
const aiIdea = ref(localStorage.getItem(IDEA_KEY) || '')
const aiLanguage = ref('')
const aiDepth = ref(3)
const aiMode = ref('replace')
const aiOpen = ref(false)
const generating = ref(false)
const expanding = ref(false)
const expandingLabel = ref('')
const overviewScrollRef = ref(null)
const cloudId = ref(null)
const cloudStatus = ref(null)
const worldName = ref('')
const worldDescription = ref('')
const savingCloud = ref(false)
const outlineQuery = ref('')
const emptyOnly = ref(false)
const collapsedIds = ref(new Set())
let pendingConfirm = null

const confirmState = reactive({
  open: false,
  title: '',
  description: '',
  confirmText: '确认',
  destructive: false,
})

const cloudStatusLabel = computed(() => {
  return { 0: '草稿', 2: '已发布', 3: '已下架' }[Number(cloudStatus.value)] || '未入库'
})

const languageLabel = computed(
  () => languageOptions.find((o) => o.value === aiLanguage.value)?.label || '跟随想法',
)
const depthLabel = computed(
  () => depthOptions.find((o) => o.value === aiDepth.value)?.label || '标准',
)
const totalNodes = computed(() => countNodes(tree.value))
const filledCount = computed(() => {
  let n = 0
  const walk = (list) => {
    for (const x of list || []) {
      if (wordCount(x) >= FILL_OK) n += 1
      walk(x.children)
    }
  }
  walk(tree.value)
  return n
})

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

const allRows = computed(() => {
  const rows = []
  const walk = (list, depth) => {
    for (const node of list || []) {
      rows.push({ node, depth })
      walk(node.children || [], depth + 1)
    }
  }
  walk(tree.value, 0)
  return rows
})

const filteredOutlineRows = computed(() => {
  const q = outlineQuery.value.trim().toLowerCase()
  if (!q) return allRows.value
  return allRows.value.filter((r) => String(r.node.label || '').toLowerCase().includes(q))
})

const skeletonRows = computed(() => {
  const rows = []
  const q = outlineQuery.value.trim().toLowerCase()
  const walk = (list, depth) => {
    for (const node of list || []) {
      const matchQ = !q || String(node.label || '').toLowerCase().includes(q)
      const matchEmpty = !emptyOnly.value || wordCount(node) === 0
      if (matchQ && matchEmpty) rows.push({ node, depth })
      if (node.children?.length && !collapsedIds.value.has(node.id)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(tree.value, 0)
  return rows
})

const overviewSections = computed(() => allRows.value)

function overviewHeadingTag(depth) {
  if (depth <= 0) return 'h2'
  if (depth === 1) return 'h3'
  return 'h4'
}

function scrollToOverviewNode(id) {
  selectById(id)
  requestAnimationFrame(() => {
    document.getElementById(`wt-node-${id}`)?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    })
  })
}

function typeLabel(t) {
  return nodeTypes.find((x) => x.value === t)?.label || t
}

function wordCount(node) {
  return String(node?.content || '')
    .replace(/\s+/g, '')
    .trim().length
}

function pipClass(node) {
  const n = wordCount(node)
  if (n >= FILL_OK) return 'bg-emerald-600/80'
  if (n > 0) return 'bg-amber-500/80'
  return 'bg-muted-foreground/35'
}

function pipTitle(node) {
  const n = wordCount(node)
  if (n >= FILL_OK) return '已充实'
  if (n > 0) return '偏短'
  return '空缺'
}

function uid() {
  return `w-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function loadTree() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (!Array.isArray(raw) || !raw.length) return []
    const savedVersion = localStorage.getItem(VERSION_KEY)
    if (isStaleShowMeYourWaysTree(raw, savedVersion)) {
      const fresh = createShowMeYourWaysWorldTree()
      localStorage.setItem(STORAGE_KEY, JSON.stringify(fresh))
      localStorage.setItem(VERSION_KEY, String(SHOW_ME_YOUR_WAYS_META.dataVersion))
      return fresh
    }
    return raw
  } catch {
    /* ignore */
  }
  return []
}

function renderOverviewBody(node) {
  return renderMarkdown(node?.content || node?.summary || '')
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tree.value))
  localStorage.setItem(IDEA_KEY, aiIdea.value || '')
  if (String(tree.value[0]?.label || '').includes('示我周行')) {
    localStorage.setItem(VERSION_KEY, String(SHOW_ME_YOUR_WAYS_META.dataVersion))
  }
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

function selectAndMaybeWrite(node) {
  selected.value = node
  // 骨架 / 一览点标题 → 回「写」改正文，少一次手动切透镜
  viewMode.value = 'write'
}

function defaultChildType(parentType) {
  switch (parentType) {
    case 'world':
      return 'chapter'
    case 'chapter':
      return 'lore'
    case 'era':
      return 'region'
    default:
      return 'lore'
  }
}

function addRoot() {
  const node = {
    id: uid(),
    label: '未命名世界',
    type: 'world',
    summary: '',
    content: '',
    children: [],
  }
  tree.value.push(node)
  selected.value = node
  viewMode.value = 'write'
  persist()
}

function addChild(parent) {
  if (!parent) return
  if (!parent.children) parent.children = []
  const node = {
    id: uid(),
    label: '新节点',
    type: defaultChildType(parent.type),
    summary: '',
    content: '',
    children: [],
  }
  parent.children.push(node)
  selected.value = node
  ensureExpanded(parent.id)
  viewMode.value = 'write'
  persist()
}

async function loadShowMeYourWays() {
  if (tree.value.length) {
    const confirmed = await askConfirm({
      title: '打开示我周行',
      description: '将切换到世界「示我周行」的完整设定集，当前编辑中的节点会被替换。',
      confirmText: '打开并替换',
      destructive: true,
    })
    if (!confirmed) return
  }
  tree.value = createShowMeYourWaysWorldTree()
  selected.value = tree.value[0] || null
  viewMode.value = 'write'
  collapsedIds.value = new Set()
  worldName.value = SHOW_ME_YOUR_WAYS_META.title || '示我周行 Show Me Your Ways'
  worldDescription.value =
    SHOW_ME_YOUR_WAYS_META.description || '战后信使视角下的原创世界观设定集'
  localStorage.setItem(VERSION_KEY, String(SHOW_ME_YOUR_WAYS_META.dataVersion))
  persist()
  toast.success(
    `已打开「示我周行」（${countNodes(tree.value)} 个节点 · 设定 ${SHOW_ME_YOUR_WAYS_META.definitionCount} · 事件 ${SHOW_ME_YOUR_WAYS_META.eventCount}）`,
  )
}

function buildCloudPayload() {
  const name = worldName.value.trim() || tree.value[0]?.label || '未命名世界'
  return {
    name,
    description: worldDescription.value.trim() || '',
    cover: null,
    tree: tree.value,
  }
}

async function saveToCloud(publish) {
  if (!tree.value.length) {
    toast.warning('先生成或载入一棵世界树')
    return
  }
  savingCloud.value = true
  try {
    const payload = buildCloudPayload()
    let saved
    if (cloudId.value) {
      saved = await updateWorld(cloudId.value, payload)
      if (publish && Number(saved.status) !== 2) {
        await publishWorld(cloudId.value)
        saved = await getWorldDetail(cloudId.value)
      }
    } else {
      saved = await createWorld(payload, { publish })
    }
    cloudId.value = saved.id
    cloudStatus.value = saved.status
    worldName.value = saved.name || payload.name
    worldDescription.value = saved.description || ''
    if (Array.isArray(saved.tree) && saved.tree.length) {
      tree.value = ensureIds(saved.tree)
      selected.value = tree.value[0] || null
    }
    persist()
    await router.replace({ path: '/world-tree', query: { id: String(saved.id) } })
    toast.success(publish || Number(saved.status) === 2 ? '已保存并发布到世界广场' : '已保存为草稿')
  } catch {
    /* interceptor */
  } finally {
    savingCloud.value = false
  }
}

async function loadCloudWorld(id) {
  if (!id) return
  try {
    const saved = await getWorldDetail(id)
    cloudId.value = saved.id
    cloudStatus.value = saved.status
    worldName.value = saved.name || ''
    worldDescription.value = saved.description || ''
    tree.value = ensureIds(saved.tree || [])
    selected.value = tree.value[0] || null
    viewMode.value = 'write'
    collapsedIds.value = new Set()
    persist()
  } catch {
    toast.error('加载云端世界树失败')
  }
}

onMounted(() => {
  const id = route.query.id
  if (id) loadCloudWorld(id)
  else if (tree.value[0]?.label && !worldName.value) {
    worldName.value = tree.value[0].label
  }
})

watch(
  () => route.query.id,
  (id, prev) => {
    if (id && id !== prev) loadCloudWorld(id)
  },
)

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
  if (selected.value?.id === node.id) selected.value = tree.value[0] || null
  persist()
}

function ensureIds(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes.map((n) => ({
    id: n.id || uid(),
    label: n.label || '未命名',
    type: n.type || 'lore',
    summary: n.summary || '',
    content: n.content || '',
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
    viewMode.value = 'write'
    aiOpen.value = false
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
  const target = selected.value
  expanding.value = true
  expandingLabel.value = target.label
  try {
    const res = await workshopExpandWorldNode({
      node: {
        label: target.label,
        type: target.type,
        summary: target.summary,
        content: target.content,
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

async function expandNode(node) {
  if (!node || generating.value || expanding.value) return
  selected.value = node
  await expandSelected()
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

function briefText(n) {
  if (n.summary) return n.summary
  if (n.content) {
    const one = String(n.content).replace(/\s+/g, ' ').trim()
    return one.length > 80 ? `${one.slice(0, 80)}…` : one
  }
  return ''
}

function collectContext(node) {
  const lines = []
  const walk = (n, depth) => {
    const brief = briefText(n)
    lines.push(`${'  '.repeat(depth)}[${typeLabel(n.type)}] ${n.label}${brief ? ` — ${brief}` : ''}`)
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
