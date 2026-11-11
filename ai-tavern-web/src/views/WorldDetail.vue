<template>
  <div class="space-y-4" @keydown="onKey" tabindex="0" ref="shellRef">
    <div v-if="loading" class="space-y-4">
      <Skeleton class="h-8 w-64" />
      <Skeleton class="h-[560px] w-full rounded-2xl" />
    </div>

    <template v-else-if="world">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div class="min-w-0 space-y-1">
          <nav class="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
            <button type="button" class="hover:text-foreground" @click="router.push('/worlds')">
              广场
            </button>
            <span>/</span>
            <button
              type="button"
              class="hover:text-foreground"
              :class="mode === 'hub' ? 'text-foreground' : ''"
              @click="toHub"
            >
              {{ world.name }}
            </button>
            <template v-if="activeZone && mode !== 'hub' && mode !== 'encounter'">
              <span>/</span>
              <span class="text-foreground">{{ activeZone.label }}</span>
            </template>
            <template v-if="mode === 'encounter'">
              <span>/</span>
              <span class="text-foreground">漫游</span>
            </template>
          </nav>
          <div class="flex items-center gap-3">
            <h1 class="truncate font-serif text-xl font-semibold tracking-tight sm:text-2xl">
              {{ headerTitle }}
            </h1>
            <div
              class="relative grid size-9 place-items-center rounded-full border border-border/70 text-[10px] tabular-nums"
              :title="`已探索 ${discovered.size} / ${totalCount}`"
            >
              <svg class="absolute inset-0 size-9 -rotate-90" viewBox="0 0 36 36" aria-hidden="true">
                <circle cx="18" cy="18" r="15" fill="none" class="stroke-border" stroke-width="3" />
                <circle
                  cx="18"
                  cy="18"
                  r="15"
                  fill="none"
                  class="stroke-amber-600 transition-all duration-500"
                  stroke-width="3"
                  stroke-linecap="round"
                  :stroke-dasharray="`${explorePct * 0.94} 100`"
                />
              </svg>
              <span class="relative">{{ Math.round(explorePct) }}%</span>
            </div>
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <Button v-if="world.ownedByMe" variant="outline" size="sm" @click="goEdit">编辑</Button>
          <Button
            size="sm"
            class="rounded-full"
            :disabled="roamLeft <= 0 || transitioning"
            @click="startRoam"
          >
            漫游{{ roamLeft > 0 ? ` · ${roamLeft}` : '' }}
          </Button>
        </div>
      </header>

      <p class="text-[11px] text-muted-foreground">
        悬停预览 · 单击进入 · Esc 返回 · 数字键 1–{{ Math.min(zones.length, 9) }} 直达区域
      </p>

      <div class="relative min-h-[580px]">
        <Transition name="wd-fade" mode="out-in">
          <!-- HUB -->
          <section
            v-if="mode === 'hub'"
            key="hub"
            class="relative min-h-[580px] overflow-hidden rounded-2xl border border-border/60 bg-[#12151a] text-[#e8e4dc]"
          >
            <div
              class="pointer-events-none absolute inset-0 transition-opacity duration-500"
              :style="hubGlowStyle"
            />
            <!-- 可点区域：更大、有脉动 -->
            <div class="absolute inset-0 z-20 p-2 sm:p-4">
              <button
                v-for="(z, i) in zones"
                :key="z.id"
                type="button"
                class="group absolute w-[148px] rounded-2xl border p-3.5 text-left shadow-lg backdrop-blur-md transition-all duration-200 sm:w-[168px]"
                :class="
                  hoverZone?.id === z.id
                    ? 'z-30 scale-105 border-amber-200/60 bg-black/70'
                    : 'border-white/15 bg-black/50 hover:border-amber-200/40 hover:bg-black/65'
                "
                :style="zoneStyle(i, zones.length)"
                @mouseenter="hoverZone = z"
                @mouseleave="hoverZone = null"
                @focus="hoverZone = z"
                @blur="hoverZone = null"
                @click="enterZone(z)"
              >
                <span
                  class="absolute -inset-1 -z-10 rounded-2xl opacity-0 blur-md transition group-hover:opacity-100"
                  style="background: radial-gradient(circle, rgba(212,165,116,0.35), transparent 70%)"
                />
                <span
                  class="mb-2 inline-flex size-2 animate-pulse rounded-full bg-amber-300/90"
                  aria-hidden="true"
                />
                <p class="text-[10px] tracking-[0.2em] text-sky-200/85">
                  {{ String(i + 1).padStart(2, '0') }} · ZONE
                </p>
                <p class="mt-1 font-serif text-[15px] font-medium leading-snug">
                  {{ z.label || '未命名' }}
                </p>
                <p class="mt-1.5 line-clamp-2 text-[11px] leading-snug text-white/55">
                  {{ zoneHint(z) }}
                </p>
                <p
                  class="mt-2 text-[10px] text-amber-200/80 opacity-0 transition group-hover:opacity-100"
                >
                  点击进入 →
                </p>
              </button>
            </div>

            <div
              class="relative z-10 mx-auto flex min-h-[580px] max-w-lg flex-col items-center justify-center px-6 py-20 text-center pointer-events-none"
            >
              <p class="text-[10px] tracking-[0.32em] text-amber-200/90">
                {{ hoverZone ? 'ZONE PREVIEW' : 'POSTAL BRIEFING' }}
              </p>
              <Transition name="wd-fade" mode="out-in">
                <div :key="hoverZone?.id || 'brief'" class="mt-3">
                  <h2 class="font-serif text-2xl font-semibold leading-snug sm:text-3xl">
                    {{ hoverZone ? hoverZone.label : hubHeadline }}
                  </h2>
                  <p class="mx-auto mt-4 max-w-sm text-sm leading-7 text-white/60">
                    {{ hoverZone ? nodeBrief(hoverZone, 120) : hubIntro }}
                  </p>
                </div>
              </Transition>
              <div class="pointer-events-auto mt-10 flex flex-wrap justify-center gap-2">
                <Button
                  class="rounded-full bg-amber-200 text-stone-900 hover:bg-amber-100 active:scale-95"
                  :disabled="roamLeft <= 0"
                  @click="startRoam"
                >
                  开始漫游
                </Button>
                <Button
                  variant="outline"
                  class="rounded-full border-white/15 bg-white/5 text-white hover:bg-white/10 active:scale-95"
                  @click="router.push('/worlds')"
                >
                  回广场
                </Button>
              </div>
            </div>
          </section>

          <!-- REGION -->
          <section
            v-else-if="mode === 'region' && activeZone"
            key="region"
            class="overflow-hidden rounded-2xl border border-border/60"
          >
            <div
              class="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 bg-secondary/40 px-4 py-2.5"
            >
              <div class="flex items-center gap-2 text-xs">
                <Button size="xs" variant="ghost" @click="toHub">← 大厅</Button>
                <span class="text-muted-foreground">/</span>
                <span class="font-medium">{{ activeZone.label }}</span>
              </div>
              <div class="flex items-center gap-2 text-xs text-muted-foreground">
                <div class="h-1.5 w-24 overflow-hidden rounded-full bg-border">
                  <div
                    class="h-full rounded-full bg-amber-600 transition-all duration-500"
                    :style="{ width: `${zonePct}%` }"
                  />
                </div>
                <span class="tabular-nums">{{ zoneDiscovered }}/{{ zoneSpots.length }}</span>
              </div>
            </div>
            <div class="grid min-h-[520px] grid-cols-1 lg:grid-cols-[minmax(0,1fr)_340px]">
              <div class="space-y-3 bg-gradient-to-b from-secondary/25 to-background p-4">
                <p class="text-xs text-muted-foreground">单击预览 · 再点「深入」或双击展开全文</p>
                <div class="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                  <button
                    v-for="(spot, idx) in zoneSpots"
                    :key="spot.id"
                    type="button"
                    class="group rounded-xl border p-3.5 text-left transition-all duration-200 active:scale-[0.98]"
                    :class="
                      activeSpot?.id === spot.id
                        ? 'border-amber-700/40 bg-amber-500/10 shadow-[0_0_0_1px_rgba(180,120,60,0.25)]'
                        : 'border-border/60 hover:-translate-y-0.5 hover:border-border hover:bg-secondary/50 hover:shadow-md'
                    "
                    @click="selectSpot(spot)"
                    @dblclick="selectSpot(spot, true)"
                  >
                    <div class="flex items-start justify-between gap-2">
                      <p class="font-serif text-sm font-medium leading-snug">
                        {{ spot.label || '未命名' }}
                      </p>
                      <span
                        class="mt-0.5 size-2 shrink-0 rounded-full"
                        :class="
                          discovered.has(String(spot.id)) ? 'bg-emerald-500' : 'bg-muted-foreground/30'
                        "
                      />
                    </div>
                    <p class="mt-1.5 line-clamp-2 text-xs leading-relaxed text-muted-foreground">
                      {{ nodeBrief(spot) }}
                    </p>
                    <p
                      class="mt-2 text-[10px] text-amber-800/80 opacity-0 transition group-hover:opacity-100 dark:text-amber-200/70"
                    >
                      {{ idx + 1 }} ·
                      {{ discovered.has(String(spot.id)) ? '再次探访' : '尚未深入' }}
                    </p>
                  </button>
                </div>
              </div>

              <aside
                class="relative flex flex-col border-t border-border/60 bg-card lg:border-t-0 lg:border-l"
              >
                <Transition name="wd-slide" mode="out-in">
                  <div v-if="activeSpot" :key="activeSpot.id" class="flex h-full flex-col p-4">
                    <h3 class="font-serif text-lg font-semibold">{{ activeSpot.label }}</h3>
                    <Badge variant="outline" class="mt-2 w-fit font-normal">
                      {{ activeSpot.type || 'lore' }}
                    </Badge>
                    <div
                      v-if="!deepOpen"
                      class="mt-3 flex-1 text-sm leading-7 text-muted-foreground"
                    >
                      {{ nodeBrief(activeSpot, 200) }}
                      <p class="mt-4 text-xs text-muted-foreground/80">
                        点「深入」展开完整现场。
                      </p>
                    </div>
                    <div
                      v-else
                      class="chat-md mt-3 max-h-[380px] flex-1 overflow-y-auto text-sm leading-7 text-muted-foreground"
                      v-html="renderBody(activeSpot)"
                    />
                    <div class="mt-4 flex flex-wrap gap-2 border-t border-border/60 pt-3">
                      <Button size="sm" class="active:scale-95" @click="toggleDeep">
                        {{ deepOpen ? '收起现场' : '深入' }}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        class="active:scale-95"
                        @click="spawnFromSpot(activeSpot)"
                      >
                        与他开聊
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        class="active:scale-95"
                        @click="markSpot(activeSpot)"
                      >
                        {{ discovered.has(String(activeSpot.id)) ? '已在足迹' : '标记足迹' }}
                      </Button>
                    </div>
                  </div>
                  <div
                    v-else
                    key="empty"
                    class="flex flex-1 flex-col items-center justify-center gap-2 p-8 text-center text-sm text-muted-foreground"
                  >
                    <span class="text-2xl opacity-40">◇</span>
                    <p>点左侧点位，这里会亮起场景</p>
                  </div>
                </Transition>
              </aside>
            </div>
          </section>

          <!-- TIMELINE -->
          <section
            v-else-if="mode === 'timeline' && activeZone"
            key="timeline"
            class="overflow-hidden rounded-2xl border border-border/60 bg-[#12151a] text-[#e8e4dc]"
          >
            <div
              class="flex flex-wrap items-center justify-between gap-2 border-b border-white/10 px-4 py-2.5 text-xs text-white/55"
            >
              <div class="flex items-center gap-2">
                <Button
                  size="xs"
                  variant="ghost"
                  class="text-white/70 hover:bg-white/10 hover:text-white"
                  @click="toHub"
                >
                  ← 大厅
                </Button>
                <span>{{ activeZone.label }} · 拖拽或滚轮浏览轨道</span>
              </div>
              <div class="flex gap-1">
                <Button
                  size="xs"
                  variant="outline"
                  class="border-white/15 bg-white/5 text-white hover:bg-white/10"
                  @click="shiftTimeline(-1)"
                >
                  ‹
                </Button>
                <Button
                  size="xs"
                  variant="outline"
                  class="border-white/15 bg-white/5 text-white hover:bg-white/10"
                  @click="shiftTimeline(1)"
                >
                  ›
                </Button>
              </div>
            </div>
            <div
              ref="railRef"
              class="cursor-grab overflow-x-auto px-4 pt-10 pb-4 active:cursor-grabbing"
              @pointerdown="onRailDown"
              @pointermove="onRailMove"
              @pointerup="onRailUp"
              @pointerleave="onRailUp"
              @wheel.prevent="onRailWheel"
            >
              <div class="relative flex min-w-max gap-0 px-4">
                <div
                  class="pointer-events-none absolute left-8 right-8 top-[7px] h-0.5 bg-gradient-to-r from-transparent via-amber-200/55 to-transparent"
                />
                <button
                  v-for="(stop, i) in timelineList"
                  :key="stop.id"
                  type="button"
                  :data-stop-id="stop.id"
                  class="relative w-28 shrink-0 px-1 text-center transition-transform duration-200"
                  :class="activeSpot?.id === stop.id ? 'scale-105' : 'hover:scale-105'"
                  @click="selectSpot(stop)"
                >
                  <span
                    class="relative z-10 mx-auto mb-3 block size-3.5 rounded-full border-2 border-amber-200 transition-all"
                    :class="
                      activeSpot?.id === stop.id
                        ? 'bg-amber-200 shadow-[0_0_18px_rgba(212,165,116,0.65)]'
                        : discovered.has(String(stop.id))
                          ? 'bg-amber-200/40'
                          : 'bg-[#12151a]'
                    "
                  />
                  <span class="block text-[10px] text-white/40">{{ i + 1 }}</span>
                  <span class="mt-1 block font-serif text-xs leading-snug">{{ stop.label }}</span>
                </button>
              </div>
            </div>
            <Transition name="wd-fade" mode="out-in">
              <div
                v-if="activeSpot"
                :key="activeSpot.id"
                class="mx-4 mb-5 rounded-xl border border-white/10 bg-white/5 p-4"
              >
                <h3 class="font-serif text-lg font-semibold">{{ activeSpot.label }}</h3>
                <div
                  v-if="deepOpen"
                  class="chat-md mt-3 max-h-64 overflow-y-auto text-sm leading-7 text-white/65"
                  v-html="renderBody(activeSpot)"
                />
                <p v-else class="mt-3 text-sm leading-7 text-white/60">
                  {{ nodeBrief(activeSpot, 220) }}
                </p>
                <div class="mt-4 flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    class="rounded-full bg-amber-200 text-stone-900 hover:bg-amber-100 active:scale-95"
                    @click="toggleDeep"
                  >
                    {{ deepOpen ? '收起现场' : '查看现场' }}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    class="rounded-full border-white/15 bg-white/5 text-white hover:bg-white/10 active:scale-95"
                    @click="spawnFromSpot(activeSpot)"
                  >
                    关联开聊
                  </Button>
                </div>
              </div>
            </Transition>
          </section>

          <!-- ENCOUNTER -->
          <section
            v-else-if="mode === 'encounter' && encounter"
            key="encounter"
            class="flex min-h-[560px] flex-col overflow-hidden rounded-2xl border border-border/60 bg-[#12151a] text-[#e8e4dc]"
          >
            <div
              class="flex items-center justify-between border-b border-white/10 px-4 py-2.5 text-xs text-white/50"
            >
              <button type="button" class="hover:text-white" @click="toHub">← 大厅</button>
              <span>今日还可漫游 {{ roamLeft }}</span>
            </div>
            <div class="flex flex-1 items-center justify-center p-6">
              <div
                class="wd-encounter-card w-full max-w-md rounded-2xl border border-white/10 bg-gradient-to-b from-[#232830] to-[#1a1e24] p-6 shadow-[0_28px_70px_rgba(0,0,0,0.45)]"
              >
                <p class="text-[10px] tracking-[0.28em] text-sky-200/80">ENCOUNTER</p>
                <h2 class="mt-2 font-serif text-2xl font-semibold">
                  {{ encounter.node.label }}
                </h2>
                <p class="mt-3 text-sm leading-7 text-white/60">
                  {{ nodeBrief(encounter.node, 180) }}
                </p>
                <div class="mt-5 grid gap-2">
                  <button
                    type="button"
                    class="rounded-xl border border-amber-200/30 bg-amber-200/95 px-3 py-3 text-left text-sm font-medium text-stone-900 transition hover:bg-amber-100 active:scale-[0.98]"
                    @click="stayEncounter"
                  >
                    停留，听完这段
                  </button>
                  <button
                    v-if="encounter.parent"
                    type="button"
                    class="rounded-xl border border-white/12 bg-white/5 px-3 py-3 text-left text-sm text-white transition hover:border-amber-200/30 hover:bg-white/10 active:scale-[0.98]"
                    @click="enterZone(encounter.parent)"
                  >
                    进入「{{ encounter.parent.label }}」区域
                  </button>
                  <button
                    type="button"
                    class="rounded-xl border border-white/12 bg-white/5 px-3 py-3 text-left text-sm text-white transition hover:bg-white/10 active:scale-[0.98] disabled:opacity-40"
                    :disabled="roamLeft <= 0"
                    @click="startRoam"
                  >
                    再漫游一次
                  </button>
                  <button
                    type="button"
                    class="rounded-xl px-3 py-2.5 text-left text-sm text-white/65 transition hover:bg-white/5 hover:text-white"
                    @click="toHub"
                  >
                    回大厅
                  </button>
                </div>
              </div>
            </div>
          </section>
        </Transition>
      </div>
    </template>

    <EmptyState v-else text="世界不存在或未开放" hint="回广场看看其他入口">
      <Button size="sm" class="mt-3" @click="router.push('/worlds')">世界广场</Button>
    </EmptyState>
  </div>
</template>

<style scoped>
.wd-fade-enter-active,
.wd-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.wd-fade-enter-from,
.wd-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
.wd-slide-enter-active,
.wd-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.wd-slide-enter-from {
  opacity: 0;
  transform: translateX(12px);
}
.wd-slide-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
.wd-encounter-card {
  animation: wd-pop 0.35s ease;
}
@keyframes wd-pop {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { getWorldDetail } from '../api/worldTree'
import { renderMarkdown } from '../utils/markdown'
import {
  consumeRoam,
  countNodes,
  flattenNodes,
  isTimelineZone,
  loadDiscovered,
  markDiscovered,
  nodeBrief,
  nodeBody,
  pickEncounter,
  roamRemaining,
  timelineStops,
  worldRoot,
  worldZones,
} from '../utils/worldExplore'
import EmptyState from '../components/EmptyState.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

const ROAM_DAILY = 5

const route = useRoute()
const router = useRouter()

const shellRef = ref(null)
const railRef = ref(null)
const loading = ref(true)
const world = ref(null)
const mode = ref('hub')
const activeZone = ref(null)
const activeSpot = ref(null)
const hoverZone = ref(null)
const deepOpen = ref(false)
const discovered = ref(new Set())
const roamLeft = ref(ROAM_DAILY)
const encounter = ref(null)
const transitioning = ref(false)

const railDrag = ref({ on: false, x: 0, left: 0 })

const tree = computed(() => world.value?.tree || [])
const zones = computed(() => worldZones(tree.value))
const root = computed(() => worldRoot(tree.value))
const totalCount = computed(() => countNodes(tree.value))
const explorePct = computed(() => {
  const t = totalCount.value || 1
  return Math.min(100, (discovered.value.size / t) * 100)
})

const zoneSpots = computed(() => {
  const z = activeZone.value
  if (!z) return []
  const kids = z.children || []
  return kids.length ? kids : [z]
})

const zoneDiscovered = computed(() =>
  zoneSpots.value.filter((s) => discovered.value.has(String(s.id))).length,
)
const zonePct = computed(() => {
  const t = zoneSpots.value.length || 1
  return Math.min(100, (zoneDiscovered.value / t) * 100)
})

const timelineList = computed(() => timelineStops(activeZone.value))

const headerTitle = computed(() => {
  if (mode.value === 'region' && activeZone.value) return activeZone.value.label
  if (mode.value === 'timeline' && activeZone.value) return `轨道 · ${activeZone.value.label}`
  if (mode.value === 'encounter') return '漫游遭遇'
  return world.value?.name || '世界'
})

const hubHeadline = computed(() => {
  const r = root.value
  const text = String(r?.summary || r?.content || world.value?.description || '').trim()
  if (!text) return '您好，朋友。这座世界向你敞开。'
  const one = text.replace(/\s+/g, ' ')
  const m = one.match(/^(.{8,40}?[。！？!?])/)
  return m ? m[1] : one.slice(0, 40) + (one.length > 40 ? '…' : '')
})

const hubIntro = computed(() => {
  const r = root.value
  const body = nodeBrief(r || { summary: world.value?.description }, 140)
  if (body && body !== '尚未留下可辨认的痕迹。') return body
  return '把指针移到周围的区域上看一眼，再点进去。'
})

const hubGlowStyle = computed(() => {
  const warm = hoverZone.value
    ? 'rgba(212, 165, 116, 0.22)'
    : 'rgba(212, 165, 116, 0.12)'
  return {
    background: `
      radial-gradient(ellipse at 50% 40%, ${warm}, transparent 55%),
      radial-gradient(ellipse at 50% 100%, rgba(0,0,0,0.5), transparent 42%)
    `,
  }
})

function zoneHint(z) {
  const n = (z.children || []).length
  if (isTimelineZone(z)) return n ? `${n} 个历史站点` : '时间的褶皱'
  if (n) return `${n} 处可遭逢`
  return nodeBrief(z, 28)
}

function zoneStyle(i, total) {
  const presets = [
    { left: '4%', top: '12%' },
    { right: '4%', top: '10%' },
    { left: '3%', bottom: '12%' },
    { right: '4%', bottom: '14%' },
    { left: '50%', bottom: '5%', transform: 'translateX(-50%)' },
    { left: '50%', top: '5%', transform: 'translateX(-50%)' },
  ]
  if (i < presets.length) return presets[i]
  const angle = (i / Math.max(total, 1)) * Math.PI * 2 - Math.PI / 2
  const x = 50 + Math.cos(angle) * 40
  const y = 48 + Math.sin(angle) * 36
  return { left: `${x}%`, top: `${y}%`, transform: 'translate(-50%, -50%)' }
}

function renderBody(node) {
  return renderMarkdown(nodeBody(node) || '（此处安静无声）')
}

function refreshRoam() {
  roamLeft.value = roamRemaining(world.value?.id, ROAM_DAILY)
}

function discover(nodeId) {
  const before = discovered.value.size
  discovered.value = markDiscovered(world.value?.id, nodeId, discovered.value)
  if (discovered.value.size > before) {
    // 轻反馈，避免刷屏
  }
}

function toHub() {
  mode.value = 'hub'
  activeZone.value = null
  activeSpot.value = null
  encounter.value = null
  deepOpen.value = false
  hoverZone.value = null
  nextTick(() => shellRef.value?.focus?.())
}

function enterZone(z) {
  if (!z || transitioning.value) return
  transitioning.value = true
  discover(z.id)
  activeZone.value = z
  activeSpot.value = null
  deepOpen.value = false
  encounter.value = null
  hoverZone.value = null
  mode.value = isTimelineZone(z) ? 'timeline' : 'region'
  if (mode.value === 'timeline' && timelineList.value[0]) {
    selectSpot(timelineList.value[0])
  } else if (mode.value === 'region' && zoneSpots.value[0]) {
    // 自动选中第一项，减少「点了没反应」的空窗
    selectSpot(zoneSpots.value[0])
  }
  window.setTimeout(() => {
    transitioning.value = false
    shellRef.value?.focus?.()
  }, 220)
}

function selectSpot(spot, openDeep = false) {
  if (!spot) return
  activeSpot.value = spot
  deepOpen.value = !!openDeep
  discover(spot.id)
}

function toggleDeep() {
  if (!activeSpot.value) return
  deepOpen.value = !deepOpen.value
  if (deepOpen.value) discover(activeSpot.value.id)
}

function markSpot(spot) {
  if (!spot) return
  discover(spot.id)
  toast.success(`足迹：${spot.label || '未命名'}`)
}

function startRoam() {
  if (roamLeft.value <= 0) {
    toast.warning('今日漫游次数已用完')
    return
  }
  const hit = pickEncounter(tree.value, discovered.value)
  if (!hit) {
    toast.warning('这座世界暂时无路可走')
    return
  }
  roamLeft.value = consumeRoam(world.value.id, ROAM_DAILY)
  encounter.value = hit
  discover(hit.node.id)
  mode.value = 'encounter'
  activeZone.value = null
  activeSpot.value = null
  deepOpen.value = false
  nextTick(() => shellRef.value?.focus?.())
}

function stayEncounter() {
  if (!encounter.value) return
  const { node, parent } = encounter.value
  if (parent) {
    enterZone(parent)
    nextTick(() => {
      selectSpot(node, true)
    })
  } else {
    activeZone.value = node
    mode.value = isTimelineZone(node) ? 'timeline' : 'region'
    selectSpot(node, true)
  }
}

function spawnFromSpot(spot) {
  if (!spot) return
  const flat = flattenNodes(tree.value)
  const lines = []
  const walkUp = (id) => {
    const hit = flat.find((x) => x.node.id === id)
    if (!hit) return
    if (hit.parent) walkUp(hit.parent.id)
    lines.push(`[${hit.node.type || 'lore'}] ${hit.node.label}`)
  }
  walkUp(spot.id)
  sessionStorage.setItem(
    'tavern_world_spawn',
    JSON.stringify({
      worldName: world.value?.name || spot.label,
      context: `${lines.join('\n')}\n\n${nodeBrief(spot, 400)}`,
      at: Date.now(),
    }),
  )
  toast.success('已带上场景摘要，去开启对话')
  router.push({ path: '/create', query: { from: 'world-plaza' } })
}

function goEdit() {
  router.push(`/world-tree?id=${world.value.id}`)
}

function shiftTimeline(dir) {
  const list = timelineList.value
  if (!list.length) return
  const idx = Math.max(
    0,
    list.findIndex((s) => s.id === activeSpot.value?.id),
  )
  const next = list[(idx + dir + list.length) % list.length]
  selectSpot(next)
  scrollStopIntoView(next.id)
}

function scrollStopIntoView(id) {
  const el = railRef.value
  if (!el) return
  const btn = el.querySelector(`[data-stop-id="${id}"]`)
  btn?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
}

function onRailDown(e) {
  const el = railRef.value
  if (!el || e.button !== 0) return
  railDrag.value = { on: true, x: e.clientX, left: el.scrollLeft }
  el.setPointerCapture?.(e.pointerId)
}

function onRailMove(e) {
  if (!railDrag.value.on) return
  const el = railRef.value
  if (!el) return
  el.scrollLeft = railDrag.value.left - (e.clientX - railDrag.value.x)
}

function onRailUp() {
  railDrag.value.on = false
}

function onRailWheel(e) {
  const el = railRef.value
  if (!el) return
  el.scrollLeft += e.deltaY || e.deltaX
}

function onKey(e) {
  if (e.key === 'Escape') {
    if (deepOpen.value) {
      deepOpen.value = false
      return
    }
    if (mode.value !== 'hub') toHub()
    return
  }
  if (mode.value === 'hub' && /^[1-9]$/.test(e.key)) {
    const z = zones.value[Number(e.key) - 1]
    if (z) enterZone(z)
    return
  }
  if (mode.value === 'timeline') {
    if (e.key === 'ArrowLeft') shiftTimeline(-1)
    if (e.key === 'ArrowRight') shiftTimeline(1)
  }
  if (mode.value === 'region' && activeSpot.value) {
    const list = zoneSpots.value
    const idx = list.findIndex((s) => s.id === activeSpot.value.id)
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      selectSpot(list[(idx + 1) % list.length])
    }
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      selectSpot(list[(idx - 1 + list.length) % list.length])
    }
    if (e.key === 'Enter') toggleDeep()
  }
}

async function load() {
  loading.value = true
  try {
    world.value = await getWorldDetail(route.params.id)
    discovered.value = loadDiscovered(world.value?.id)
    refreshRoam()
    toHub()
  } catch {
    world.value = null
  } finally {
    loading.value = false
    nextTick(() => shellRef.value?.focus?.())
  }
}

onMounted(load)
watch(
  () => route.params.id,
  (id, prev) => {
    if (id && id !== prev) load()
  },
)
</script>
