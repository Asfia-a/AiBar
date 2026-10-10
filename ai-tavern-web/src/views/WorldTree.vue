<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="font-display text-3xl text-tavern-200">世界树</h1>
        <p class="text-sm text-tavern-muted mt-1 max-w-2xl">
          图形化搭建世界观：点选、加子、删除；也可用自然语言一键生成。
        </p>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="graph">图形</el-radio-button>
          <el-radio-button value="list">列表</el-radio-button>
        </el-radio-group>
        <el-button @click="addRoot">新世界根节点</el-button>
        <el-button type="warning" :disabled="!selected" @click="spawnCharacterHint">
          以此世界观创建角色
        </el-button>
      </div>
    </header>

    <section class="tavern-card p-4 space-y-3">
      <div class="flex items-center justify-between gap-2 flex-wrap">
        <h2 class="font-display text-lg text-tavern-200">AI 一键生成</h2>
        <el-tag size="small" type="warning" effect="plain">跟随你的语言</el-tag>
      </div>
      <el-input
        v-model="aiIdea"
        type="textarea"
        :rows="3"
        placeholder="用任意语言描述世界观，例如：永夜港口，真名会留下银色足迹…"
      />
      <div class="flex flex-wrap gap-3 items-center">
        <el-select v-model="aiLanguage" clearable placeholder="输出语言（可空=跟随想法）" class="w-44">
          <el-option label="跟随想法语言" value="" />
          <el-option label="中文" value="中文" />
          <el-option label="English" value="English" />
          <el-option label="日本語" value="日本語" />
        </el-select>
        <el-select v-model="aiDepth" class="w-36">
          <el-option :value="2" label="浅层 (~2)" />
          <el-option :value="3" label="标准 (~3)" />
          <el-option :value="4" label="丰富 (~4)" />
        </el-select>
        <el-radio-group v-model="aiMode" size="small">
          <el-radio-button value="replace">替换当前树</el-radio-button>
          <el-radio-button value="append">追加新世界</el-radio-button>
        </el-radio-group>
        <el-button type="warning" :loading="generating" @click="generateWorld">一键生成世界树</el-button>
        <el-button :disabled="!selected" :loading="expanding" plain @click="expandSelected">
          AI 扩展选中节点
        </el-button>
      </div>
    </section>

    <div class="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_340px] gap-6">
      <section class="tavern-card p-0 overflow-hidden min-h-[520px]">
        <WorldTreeGraph
          v-if="viewMode === 'graph'"
          :tree="tree"
          :selected-id="selected?.id"
          :type-labels="typeLabelMap"
          class="h-[560px]"
          @select="selectById"
          @add-child="addChildById"
          @remove="removeById"
          @add-root="addRoot"
        />
        <div v-else class="p-4 overflow-auto min-h-[520px]">
          <el-tree
            :data="tree"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="onSelect"
          >
            <template #default="{ data }">
              <div class="flex items-center gap-2 py-0.5 pr-2 w-full min-w-0">
                <span class="text-[10px] rounded px-1.5 py-0.5 bg-tavern-800 text-tavern-300 shrink-0">
                  {{ typeLabel(data.type) }}
                </span>
                <span class="truncate text-tavern-100 text-sm">{{ data.label }}</span>
                <span class="ml-auto flex gap-1 shrink-0" @click.stop>
                  <el-button link type="primary" size="small" @click="addChild(data)">加子</el-button>
                  <el-button link type="danger" size="small" @click="removeNode(data)">删</el-button>
                </span>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="!tree.length" description="还没有世界，用上方 AI 生成或新建根节点" />
        </div>
      </section>

      <aside class="tavern-card p-4 space-y-4 h-fit sticky top-20">
        <h2 class="font-display text-lg text-tavern-200">节点编辑</h2>
        <template v-if="selected">
          <el-form label-position="top">
            <el-form-item label="名称">
              <el-input v-model="selected.label" @change="persist" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="selected.type" class="w-full" @change="persist">
                <el-option v-for="t in nodeTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="设定摘要">
              <el-input
                v-model="selected.summary"
                type="textarea"
                :rows="8"
                placeholder="历史、法则、氛围、与其他节点的关系…"
                @change="persist"
              />
            </el-form-item>
          </el-form>
          <div class="flex flex-wrap gap-2">
            <el-button size="small" @click="addChild(selected)">添加子节点</el-button>
            <el-button size="small" type="danger" plain @click="removeNode(selected)">删除节点</el-button>
          </div>
          <p class="text-xs text-tavern-muted">改完名称/设定会即时反映在图形节点上。</p>
        </template>
        <p v-else class="text-sm text-tavern-muted">在画布上点选一个节点进行编辑。</p>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workshopGenerateWorldTree, workshopExpandWorldNode } from '../api/workshop'
import WorldTreeGraph from '../components/WorldTreeGraph.vue'

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
  persist()
}

function addChildById(id) {
  const parent = findNodeById(tree.value, id)
  if (parent) addChild(parent)
}

async function removeNode(node) {
  if (!node) return
  try {
    await ElMessageBox.confirm(`删除「${node.label}」及其全部子节点？`, '删除节点', { type: 'warning' })
  } catch {
    return
  }
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
    ElMessage.warning('先写一点世界观想法')
    return
  }
  if (aiMode.value === 'replace' && tree.value.length) {
    try {
      await ElMessageBox.confirm('将用 AI 结果替换当前世界树，旧数据可被覆盖。继续？', '替换确认', {
        type: 'warning',
      })
    } catch {
      return
    }
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
      ElMessage.error('未生成有效世界树')
      return
    }
    if (aiMode.value === 'append') {
      tree.value = [...tree.value, ...generated]
    } else {
      tree.value = generated
    }
    selected.value = tree.value[0] || null
    viewMode.value = 'graph'
    persist()
    ElMessage.success(`已生成 ${countNodes(generated)} 个节点`)
  } catch {
    /* interceptor */
  } finally {
    generating.value = false
  }
}

async function expandSelected() {
  if (!selected.value) return
  expanding.value = true
  try {
    const res = await workshopExpandWorldNode({
      node: {
        label: selected.value.label,
        type: selected.value.type,
        summary: selected.value.summary,
      },
      hint: aiIdea.value || '',
      language: aiLanguage.value || undefined,
    })
    const children = ensureIds(res.children || [])
    if (!children.length) {
      ElMessage.warning('没有生成子节点')
      return
    }
    if (!selected.value.children) selected.value.children = []
    selected.value.children.push(...children)
    persist()
    ElMessage.success(`已为「${selected.value.label}」扩展 ${children.length} 个子节点`)
  } catch {
    /* */
  } finally {
    expanding.value = false
  }
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
  ElMessage.success('已带上世界观摘要，跳转创建角色')
  router.push({ path: '/create', query: { from: 'world-tree' } })
}
</script>
