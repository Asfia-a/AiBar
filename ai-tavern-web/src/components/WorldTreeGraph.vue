<template>
  <div
    ref="viewportRef"
    class="wt-viewport relative w-full h-full min-h-[520px] overflow-hidden rounded-xl select-none"
    @wheel.prevent="onWheel"
    @pointerdown="onPanStart"
    @pointermove="onPanMove"
    @pointerup="onPanEnd"
    @pointerleave="onPanEnd"
    @dblclick.self="emit('add-root')"
  >
    <div class="absolute inset-0 wt-grid pointer-events-none" aria-hidden="true" />

    <div class="absolute top-3 left-3 z-10 flex flex-wrap gap-1">
      <el-button size="small" @click.stop="zoomBy(1.15)">放大</el-button>
      <el-button size="small" @click.stop="zoomBy(1 / 1.15)">缩小</el-button>
      <el-button size="small" @click.stop="fitView">适应画布</el-button>
      <el-button size="small" type="warning" plain @click.stop="emit('add-root')">+ 世界根</el-button>
    </div>
    <p class="absolute bottom-3 left-3 z-10 text-[11px] text-tavern-muted pointer-events-none">
      滚轮缩放 · 拖动画布 · 点选节点 · 双击空白新建世界根
    </p>

    <div
      class="absolute origin-top-left will-change-transform"
      :style="{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})` }"
    >
      <svg
        class="absolute top-0 left-0 overflow-visible pointer-events-none"
        :width="Math.max(bounds.w, 1)"
        :height="Math.max(bounds.h, 1)"
      >
        <path
          v-for="(e, i) in edges"
          :key="i"
          :d="e.d"
          fill="none"
          stroke="rgba(212, 168, 98, 0.45)"
          stroke-width="2"
        />
      </svg>

      <div
        v-for="n in layoutNodes"
        :key="n.id"
        class="absolute wt-node"
        :class="{ 'is-selected': n.id === selectedId }"
        :style="{
          left: `${n.x}px`,
          top: `${n.y}px`,
          width: `${NODE_W}px`,
          minHeight: `${NODE_H}px`,
          borderColor: typeColor(n.type),
        }"
        @pointerdown.stop
        @click.stop="emit('select', n.id)"
      >
        <div class="flex items-start justify-between gap-2">
          <span
            class="text-[10px] rounded px-1.5 py-0.5 shrink-0"
            :style="{ background: typeColor(n.type) + '33', color: typeColor(n.type) }"
          >
            {{ typeLabel(n.type) }}
          </span>
          <div class="flex gap-0.5 shrink-0" @click.stop>
            <button type="button" class="wt-icon-btn" title="添加子节点" @click="emit('add-child', n.id)">+</button>
            <button type="button" class="wt-icon-btn danger" title="删除" @click="emit('remove', n.id)">×</button>
          </div>
        </div>
        <div class="mt-1.5 text-sm font-medium text-tavern-100 leading-snug line-clamp-2">
          {{ n.label || '未命名' }}
        </div>
        <p v-if="n.summary" class="mt-1 text-[11px] text-tavern-muted line-clamp-2 leading-relaxed">
          {{ n.summary }}
        </p>
      </div>
    </div>

    <div
      v-if="!layoutNodes.length"
      class="absolute inset-0 flex items-center justify-center text-sm text-tavern-muted pointer-events-none"
    >
      还没有节点 — 用上方 AI 生成，或点「+ 世界根」
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'

const props = defineProps({
  tree: { type: Array, default: () => [] },
  selectedId: { type: String, default: null },
  typeLabels: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select', 'add-child', 'remove', 'add-root'])

const NODE_W = 188
const NODE_H = 88
const GAP_X = 56
const GAP_Y = 28

const viewportRef = ref(null)
const scale = ref(1)
const pan = reactive({ x: 40, y: 56 })
const panning = ref(false)
const panOrigin = reactive({ x: 0, y: 0, px: 0, py: 0 })

const TYPE_COLORS = {
  world: '#E6A23C',
  era: '#B37FEB',
  region: '#409EFF',
  faction: '#F56C6C',
  lore: '#67C23A',
  figure: '#D4A862',
}

function typeColor(t) {
  return TYPE_COLORS[t] || '#909399'
}

function typeLabel(t) {
  return props.typeLabels[t] || t
}

/** 水平树布局：根在左，子节点向右展开 */
function layoutForest(roots) {
  const nodes = []
  const edges = []
  let cursorY = 0

  function measure(node) {
    const kids = node.children || []
    if (!kids.length) return NODE_H
    let h = 0
    kids.forEach((c, i) => {
      h += measure(c)
      if (i < kids.length - 1) h += GAP_Y
    })
    return Math.max(NODE_H, h)
  }

  function place(node, depth, top) {
    const kids = node.children || []
    const blockH = measure(node)
    const x = depth * (NODE_W + GAP_X)
    let y
    if (!kids.length) {
      y = top + (blockH - NODE_H) / 2
    } else {
      let childTop = top
      const placedChildren = []
      kids.forEach((c, i) => {
        const ch = measure(c)
        const placed = place(c, depth + 1, childTop)
        placedChildren.push(placed)
        childTop += ch + (i < kids.length - 1 ? GAP_Y : 0)
      })
      const mid =
        (placedChildren[0].y + NODE_H / 2 + placedChildren[placedChildren.length - 1].y + NODE_H / 2) / 2
      y = mid - NODE_H / 2
      const itemPreview = { x: depth * (NODE_W + GAP_X), y }
      for (const child of placedChildren) {
        const x1 = itemPreview.x + NODE_W
        const y1 = itemPreview.y + NODE_H / 2
        const x2 = child.x
        const y2 = child.y + NODE_H / 2
        const mx = (x1 + x2) / 2
        edges.push({ d: `M ${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}` })
      }
    }
    const item = {
      id: node.id,
      label: node.label,
      type: node.type,
      summary: node.summary,
      x,
      y,
    }
    nodes.push(item)
    return item
  }

  for (const root of roots || []) {
    const h = measure(root)
    place(root, 0, cursorY)
    cursorY += h + GAP_Y * 2
  }

  return { nodes, edges }
}

const layout = computed(() => layoutForest(props.tree))
const layoutNodes = computed(() => layout.value.nodes)
const edges = computed(() => layout.value.edges)

const bounds = computed(() => {
  const ns = layoutNodes.value
  if (!ns.length) return { w: 400, h: 300 }
  let maxX = 0
  let maxY = 0
  for (const n of ns) {
    maxX = Math.max(maxX, n.x + NODE_W + 40)
    maxY = Math.max(maxY, n.y + NODE_H + 40)
  }
  return { w: maxX, h: maxY }
})

function onWheel(e) {
  const factor = e.deltaY < 0 ? 1.08 : 1 / 1.08
  zoomAt(e.clientX, e.clientY, factor)
}

function zoomBy(factor) {
  const el = viewportRef.value
  if (!el) {
    scale.value = Math.min(2.2, Math.max(0.35, scale.value * factor))
    return
  }
  const r = el.getBoundingClientRect()
  zoomAt(r.left + r.width / 2, r.top + r.height / 2, factor)
}

function zoomAt(clientX, clientY, factor) {
  const el = viewportRef.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const mx = clientX - r.left
  const my = clientY - r.top
  const next = Math.min(2.2, Math.max(0.35, scale.value * factor))
  const wx = (mx - pan.x) / scale.value
  const wy = (my - pan.y) / scale.value
  scale.value = next
  pan.x = mx - wx * next
  pan.y = my - wy * next
}

function onPanStart(e) {
  if (e.button !== 0) return
  // 仅空白处拖拽平移
  if (e.target !== viewportRef.value && !e.target.classList?.contains('wt-grid')) return
  panning.value = true
  panOrigin.x = e.clientX
  panOrigin.y = e.clientY
  panOrigin.px = pan.x
  panOrigin.py = pan.y
  e.currentTarget?.setPointerCapture?.(e.pointerId)
}

function onPanMove(e) {
  if (!panning.value) return
  pan.x = panOrigin.px + (e.clientX - panOrigin.x)
  pan.y = panOrigin.py + (e.clientY - panOrigin.y)
}

function onPanEnd() {
  panning.value = false
}

function fitView() {
  const el = viewportRef.value
  if (!el || !layoutNodes.value.length) {
    scale.value = 1
    pan.x = 40
    pan.y = 56
    return
  }
  const pad = 48
  const vw = el.clientWidth - pad * 2
  const vh = el.clientHeight - pad * 2
  const bw = bounds.value.w
  const bh = bounds.value.h
  const s = Math.min(1.2, Math.max(0.35, Math.min(vw / bw, vh / bh)))
  scale.value = s
  pan.x = pad + (vw - bw * s) / 2
  pan.y = pad + Math.max(0, (vh - bh * s) / 2)
}

function structureKey(nodes) {
  const ids = []
  const walk = (list) => {
    for (const n of list || []) {
      ids.push(n.id)
      walk(n.children)
    }
  }
  walk(nodes)
  return ids.join('|')
}

watch(
  () => structureKey(props.tree),
  async () => {
    await nextTick()
    fitView()
  },
)
</script>

<style scoped>
.wt-viewport {
  background: linear-gradient(160deg, rgba(28, 22, 18, 0.95), rgba(18, 14, 12, 0.98));
  border: 1px solid rgba(212, 168, 98, 0.18);
  cursor: grab;
}
.wt-viewport:active {
  cursor: grabbing;
}
.wt-grid {
  background-image:
    radial-gradient(rgba(212, 168, 98, 0.08) 1px, transparent 1px);
  background-size: 22px 22px;
}
.wt-node {
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1.5px solid;
  background: rgba(32, 26, 22, 0.92);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}
.wt-node:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.45);
}
.wt-node.is-selected {
  box-shadow:
    0 0 0 2px rgba(212, 168, 98, 0.55),
    0 12px 28px rgba(0, 0, 0, 0.45);
}
.wt-icon-btn {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #e8dcc8;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}
.wt-icon-btn:hover {
  background: rgba(212, 168, 98, 0.25);
}
.wt-icon-btn.danger:hover {
  background: rgba(245, 108, 108, 0.35);
  color: #fecaca;
}
</style>
