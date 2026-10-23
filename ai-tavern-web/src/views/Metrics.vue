<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div class="space-y-1.5">
        <h1 class="text-2xl font-semibold tracking-tight">运行机制可视化</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          把上下文裁剪、Token 去向、缓存命中与 MCP 调用这些平时看不见的部分摊开来看。
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline" :disabled="loading" @click="loadAll">
          <LoaderCircle v-if="loading" class="size-4 animate-spin" />
          <RefreshCw v-else class="size-4" />
          刷新
        </Button>
        <Button variant="ghost" :disabled="loading" @click="doReset">
          <Trash2 class="size-4" />
          重置
        </Button>
      </div>
    </header>

    <div v-if="loading" class="space-y-6">
      <div class="grid gap-3 sm:grid-cols-3">
        <Skeleton v-for="i in 3" :key="i" class="h-28 rounded-xl" />
      </div>
      <Skeleton class="h-80 rounded-xl" />
      <Skeleton class="h-96 rounded-xl" />
    </div>

    <EmptyState
      v-else-if="!hasAnyData"
      text="还没有运行数据"
      hint="先去聊几句，这里会自动记录上下文、Token 与 MCP 调用"
    >
      <Button class="mt-2 text-background" size="sm" @click="$router.push('/chat')">
        <MessageSquare class="size-4" />
        去对话
        <ArrowRight class="size-4" />
      </Button>
    </EmptyState>

    <template v-else>
      <!-- 摘要 -->
      <section class="grid gap-3 sm:grid-cols-3">
        <Card class="gap-0 py-4">
          <CardContent class="space-y-2">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs text-muted-foreground">上下文占用</span>
              <Gauge class="size-4 text-muted-foreground" />
            </div>
            <p class="text-2xl font-semibold tabular-nums">
              {{ contextUsagePct.toFixed(1) }}<span class="text-sm font-normal text-muted-foreground">%</span>
            </p>
            <p class="text-xs text-muted-foreground">
              历史均值 {{ avgUsagePct.toFixed(1) }}% · 窗口 {{ overview.maxWindow || 0 }} 轮
            </p>
          </CardContent>
        </Card>

        <Card class="gap-0 py-4">
          <CardContent class="space-y-2">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs text-muted-foreground">本次积分</span>
              <Coins class="size-4 text-muted-foreground" />
            </div>
            <p class="text-2xl font-semibold tabular-nums">
              {{ formatNum(estimatedPoints) }}
            </p>
            <p class="text-xs text-muted-foreground">按 1,000 Token ≈ 1 积分估算</p>
          </CardContent>
        </Card>

        <Card class="gap-0 py-4">
          <CardContent class="space-y-2">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs text-muted-foreground">消息留存</span>
              <MessageSquare class="size-4 text-muted-foreground" />
            </div>
            <p class="text-2xl font-semibold tabular-nums">{{ retentionLabel }}</p>
            <p class="text-xs text-muted-foreground">
              <template v-if="current">
                保留 {{ current.usedRounds || 0 }} 轮 · 裁剪 {{ current.droppedRounds || 0 }} 轮
              </template>
              <template v-else>暂无窗口采样</template>
            </p>
          </CardContent>
        </Card>
      </section>

      <!-- 上下文窗口 -->
      <Card>
        <CardHeader>
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="space-y-1">
              <CardTitle class="text-base">上下文窗口</CardTitle>
              <CardDescription>system 提示、历史对话与输出预留的占用构成。</CardDescription>
            </div>
            <div v-if="current" class="text-right text-xs text-muted-foreground">
              <p>本次 {{ current.usedRounds }} / {{ current.window }} 轮</p>
              <p v-if="current.droppedRounds > 0" class="text-foreground">
                已裁剪 {{ current.droppedRounds }} 条早期消息
              </p>
            </div>
          </div>
        </CardHeader>
        <CardContent class="space-y-4">
          <div v-if="current" class="space-y-3">
            <div class="flex h-7 w-full overflow-hidden rounded-lg bg-muted/60">
              <div
                v-if="sysPct > 0"
                class="flex items-center justify-center overflow-hidden bg-foreground px-1 text-[10px] font-medium text-background"
                :style="{ width: `${sysPct}%` }"
                title="system 提示"
              >
                <span class="truncate">system</span>
              </div>
              <div
                v-if="diaPct > 0"
                class="flex items-center justify-center overflow-hidden bg-foreground/55 px-1 text-[10px] font-medium text-background"
                :style="{ width: `${diaPct}%` }"
                title="历史对话"
              >
                <span class="truncate">对话</span>
              </div>
              <div
                v-if="outPct > 0"
                class="flex items-center justify-center overflow-hidden bg-foreground/20 px-1 text-[10px] font-medium text-foreground"
                :style="{ width: `${outPct}%` }"
                title="输出预留"
              >
                <span class="truncate">输出</span>
              </div>
            </div>

            <div class="flex flex-wrap gap-x-4 gap-y-1.5 text-[11px] text-muted-foreground">
              <span class="flex items-center gap-1.5">
                <span class="size-2 rounded-[2px] bg-foreground" />
                system {{ current.systemChars }} 字符
              </span>
              <span class="flex items-center gap-1.5">
                <span class="size-2 rounded-[2px] bg-foreground/55" />
                对话 {{ current.dialogueChars }} 字符
              </span>
              <span v-if="outPct > 0" class="flex items-center gap-1.5">
                <span class="size-2 rounded-[2px] bg-foreground/20" />
                输出预留
              </span>
            </div>

            <p class="text-[11px] leading-relaxed text-muted-foreground">
              估算输入 {{ formatNum(current.estimatedTokens) }} tokens
              <template v-if="current.maxTokens">/ 输出上限 {{ formatNum(current.maxTokens) }} tokens</template>
              ；超出窗口时会从最早的消息开始裁剪。
            </p>
          </div>

          <div class="flex items-center justify-between gap-2 border-t border-border/60 pt-4">
            <p class="text-xs font-medium">窗口占用趋势</p>
            <p class="text-xs text-muted-foreground">最近 {{ contextRows.length }} 次采样</p>
          </div>
          <div v-if="contextRows.length" ref="contextRef" class="h-[220px] w-full" />
          <EmptyState
            v-else
            text="暂无上下文采样"
            hint="对话产生后会在这里画出窗口占用曲线"
          />
        </CardContent>
      </Card>

      <!-- 图表与明细 Tab -->
      <Card class="gap-0 overflow-hidden py-0">
        <Tabs v-model="activeTab" class="gap-0">
          <div class="border-b border-border/60 p-3 sm:p-4">
            <TabsList class="w-full sm:w-fit">
              <TabsTrigger value="cache" class="flex-1 sm:flex-none sm:px-4">缓存对比</TabsTrigger>
              <TabsTrigger value="token" class="flex-1 sm:flex-none sm:px-4">Token 明细</TabsTrigger>
              <TabsTrigger value="mcp" class="flex-1 sm:flex-none sm:px-4">MCP 时间线</TabsTrigger>
            </TabsList>
          </div>

          <!-- 缓存对比 -->
          <TabsContent value="cache" class="mt-0 space-y-4 p-4 sm:p-5">
            <div class="flex flex-wrap items-end justify-between gap-3">
              <div class="space-y-1">
                <h2 class="text-sm font-medium">缓存命中对比</h2>
                <p class="text-xs text-muted-foreground">按业务对比读缓存与回源次数。</p>
              </div>
              <span class="text-xs text-muted-foreground">{{ cacheRows.length }} 个业务</span>
            </div>

            <div v-if="cacheRows.length" ref="cacheRef" class="h-[240px] w-full" />
            <EmptyState
              v-else
              text="暂无缓存记录"
              hint="访问热门角色或标签列表后会留下命中与回源数据"
            />

            <div v-if="cacheRows.length" class="overflow-x-auto">
              <table class="w-full min-w-[640px] text-sm">
                <thead>
                  <tr
                    class="border-b border-border/60 text-left text-xs font-normal text-muted-foreground"
                  >
                    <th class="pb-2 pr-4 font-normal">业务</th>
                    <th class="pb-2 pr-4 text-right font-normal">命中</th>
                    <th class="pb-2 pr-4 text-right font-normal">回源</th>
                    <th class="pb-2 pr-4 text-right font-normal">命中率</th>
                    <th class="pb-2 pr-4 text-right font-normal">命中耗时</th>
                    <th class="pb-2 text-right font-normal">回源耗时</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in cacheRows"
                    :key="row.name"
                    class="border-b border-border/50 last:border-0"
                  >
                    <td class="py-3 pr-4">{{ cacheLabel(row.name) }}</td>
                    <td class="py-3 pr-4 text-right tabular-nums">{{ row.hit }}</td>
                    <td class="py-3 pr-4 text-right tabular-nums">{{ row.miss }}</td>
                    <td class="py-3 pr-4 text-right">
                      <Badge
                        :variant="row.hitRate >= 50 ? 'secondary' : 'outline'"
                        class="font-normal tabular-nums"
                      >
                        {{ row.hitRate }}%
                      </Badge>
                    </td>
                    <td class="py-3 pr-4 text-right tabular-nums text-muted-foreground">
                      {{ row.avgHitCost }} ms
                    </td>
                    <td class="py-3 text-right tabular-nums text-muted-foreground">
                      {{ row.avgMissCost }} ms
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </TabsContent>

          <!-- Token 明细 -->
          <TabsContent value="token" class="mt-0 space-y-5 p-4 sm:p-5">
            <div class="flex flex-wrap items-end justify-between gap-3">
              <div class="space-y-1">
                <h2 class="text-sm font-medium">Token 明细</h2>
                <p class="text-xs text-muted-foreground">按用途汇总调用次数与消耗。</p>
              </div>
              <span class="text-xs text-muted-foreground">
                今日 {{ formatNum(token.todayTokens) }} Token
              </span>
            </div>

            <div v-if="(token.byType || []).length" class="overflow-x-auto">
              <table class="w-full min-w-[520px] text-sm">
                <thead>
                  <tr
                    class="border-b border-border/60 text-left text-xs font-normal text-muted-foreground"
                  >
                    <th class="pb-2 pr-4 font-normal">用途</th>
                    <th class="pb-2 pr-4 text-right font-normal">调用次数</th>
                    <th class="pb-2 pr-4 text-right font-normal">总 Token</th>
                    <th class="pb-2 text-right font-normal">平均</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in token.byType"
                    :key="row.type"
                    class="border-b border-border/50 last:border-0"
                  >
                    <td class="py-3 pr-4">{{ typeLabel(row.type) }}</td>
                    <td class="py-3 pr-4 text-right tabular-nums">{{ row.calls }}</td>
                    <td class="py-3 pr-4 text-right tabular-nums">
                      {{ formatNum(row.tokens) }}
                    </td>
                    <td class="py-3 text-right tabular-nums text-muted-foreground">
                      {{ Math.round(row.avg_tokens || 0) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <EmptyState v-else text="暂无消耗记录" hint="对话和叙事生成会按用途记录 Token" />

            <div v-if="(token.topSessions || []).length" class="space-y-3">
              <div class="flex items-end justify-between gap-3">
                <h3 class="text-sm font-medium">最费 Token 的会话</h3>
                <span class="text-xs text-muted-foreground">按总消耗排序</span>
              </div>
              <div class="divide-y divide-border/60 rounded-xl border border-border/70">
                <div
                  v-for="(row, index) in token.topSessions"
                  :key="row.session_id"
                  class="flex items-center justify-between gap-3 px-3 py-3"
                >
                  <div class="flex min-w-0 items-center gap-2.5">
                    <span class="w-4 shrink-0 text-center text-xs text-muted-foreground">
                      {{ index + 1 }}
                    </span>
                    <span class="truncate font-mono text-xs">{{ row.session_id }}</span>
                  </div>
                  <span class="shrink-0 text-xs tabular-nums text-muted-foreground">
                    {{ row.calls }} 轮 · {{ formatNum(row.tokens) }} Token
                  </span>
                </div>
              </div>
            </div>
          </TabsContent>

          <!-- MCP 时间线 -->
          <TabsContent value="mcp" class="mt-0 space-y-4 p-4 sm:p-5">
            <div class="flex flex-wrap items-end justify-between gap-3">
              <div class="space-y-1">
                <h2 class="text-sm font-medium">MCP 调用时间线</h2>
                <p class="text-xs text-muted-foreground">最近 50 次工具调用，含耗时与结果。</p>
              </div>
              <span class="text-xs text-muted-foreground">
                {{ mcpRows.length }} 次 · 平均 {{ Math.round(mcpAvg) }} ms
              </span>
            </div>

            <EmptyState
              v-if="!mcpRows.length"
              text="暂无 MCP 调用记录"
              hint="调用外部工具后会在这里留下时间线"
            />

            <div v-else class="max-h-[420px] overflow-y-auto pr-1">
              <ol class="relative ml-1 border-l border-border/60">
                <li
                  v-for="(row, index) in mcpRecent"
                  :key="`${row.at}-${row.tool}-${index}`"
                  class="relative py-3 pl-5"
                >
                  <span
                    class="absolute -left-[4.5px] top-4 size-2 rounded-full"
                    :class="row.success ? 'bg-foreground' : 'bg-muted-foreground'"
                  />
                  <div class="flex flex-wrap items-center justify-between gap-2">
                    <div class="flex min-w-0 items-center gap-2">
                      <span class="truncate text-sm font-medium">{{ row.tool }}</span>
                      <Badge
                        :variant="row.success ? 'secondary' : 'outline'"
                        class="font-normal"
                      >
                        {{ row.success ? '成功' : '失败' }}
                      </Badge>
                    </div>
                    <span class="text-xs tabular-nums text-muted-foreground">
                      {{ row.cost }} ms
                    </span>
                  </div>
                  <p class="mt-1 text-[11px] text-muted-foreground">{{ formatTime(row.at) }}</p>
                </li>
              </ol>
            </div>
          </TabsContent>
        </Tabs>
      </Card>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
// 按需引入 echarts：只注册用到的图表与组件，避免整包进打包体积
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, MarkLineComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent, CanvasRenderer])
import {
  ArrowRight,
  Coins,
  Gauge,
  LoaderCircle,
  MessageSquare,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next'
import {
  getCacheComparison,
  getContextOverview,
  getContextSamples,
  getMcpTimeline,
  getTokenDetail,
  resetMetrics,
} from '../api/metrics'
import EmptyState from '../components/EmptyState.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

const loading = ref(false)
const overview = ref({ hasData: false })
const contextRows = ref([])
const cacheRows = ref([])
const mcpRows = ref([])
const token = ref({})
const activeTab = ref('cache')

const contextRef = ref(null)
const cacheRef = ref(null)
let contextChart = null
let cacheChart = null

const current = computed(() => overview.value.current || null)

const avgUsagePct = computed(() => Number(overview.value.avgUsage || 0) * 100)

const contextUsagePct = computed(() => {
  const c = current.value
  if (!c) return avgUsagePct.value
  const usage = Number(c.usage)
  if (Number.isFinite(usage)) return usage * 100
  const window = Number(c.window)
  if (window) return (Number(c.usedRounds || 0) / window) * 100
  return 0
})

const retentionPct = computed(() => {
  const c = current.value
  if (!c) return null
  const used = Number(c.usedRounds || 0)
  const dropped = Number(c.droppedRounds || 0)
  const total = used + dropped
  return total ? Math.round((used / total) * 100) : 100
})

const retentionLabel = computed(() =>
  retentionPct.value == null ? '—' : `${retentionPct.value}%`,
)

const estimatedPoints = computed(() => {
  const c = current.value
  if (!c) return 0
  const tokens = Number(c.estimatedTokens || 0) + Number(c.maxTokens || 0)
  return Math.ceil(tokens / 1000)
})

const hasAnyData = computed(() =>
  Boolean(overview.value.hasData || cacheRows.value.length),
)

const mcpRecent = computed(() => [...mcpRows.value].reverse())

/** 占用条三段宽度。输入部分按 system+对话占输入预算的比例切分，右侧留输出空间 */
const sysPct = computed(() => {
  const c = current.value
  if (!c) return 0
  const inputBudget = (Number(c.estimatedTokens) || 0) + (Number(c.maxTokens) || 0) || 1
  return Math.min(
    Math.round(((Number(c.systemChars) || 0) / 1.5 / inputBudget) * 100),
    100,
  )
})

const diaPct = computed(() => {
  const c = current.value
  if (!c) return 0
  const inputBudget = (Number(c.estimatedTokens) || 0) + (Number(c.maxTokens) || 0) || 1
  return Math.min(
    Math.round(((Number(c.dialogueChars) || 0) / 1.5 / inputBudget) * 100),
    100 - sysPct.value,
  )
})

const outPct = computed(() => {
  const c = current.value
  if (!c) return 0
  const inputBudget = (Number(c.estimatedTokens) || 0) + (Number(c.maxTokens) || 0) || 1
  return Math.min(
    Math.round(((Number(c.maxTokens) || 0) / inputBudget) * 100),
    100 - sysPct.value - diaPct.value,
  )
})

const mcpAvg = computed(() => {
  if (!mcpRows.value.length) return 0
  return mcpRows.value.reduce((sum, row) => sum + (row.cost || 0), 0) / mcpRows.value.length
})

const CHART_TEXT = '#a3a3a3'
const CHART_STRONG = '#e5e5e5'
const CHART_DIM = '#525252'
const CHART_AXIS = 'rgba(255,255,255,0.15)'
const CHART_GRID = 'rgba(255,255,255,0.08)'

function tooltipStyle() {
  return {
    backgroundColor: '#171717',
    borderColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1,
    padding: [8, 10],
    textStyle: { color: '#e5e5e5', fontSize: 11 },
    extraCssText: 'border-radius:8px;',
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [ov, ctx, cache, mcp, tk] = await Promise.all([
      getContextOverview(),
      getContextSamples(60),
      getCacheComparison(),
      getMcpTimeline(50),
      getTokenDetail(10),
    ])
    overview.value = ov || { hasData: false }
    contextRows.value = ctx || []
    cacheRows.value = cache || []
    mcpRows.value = mcp || []
    token.value = tk || {}
  } catch (e) {
    toast.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }

  await nextTick()
  renderContext()
  renderCache()
}

function renderContext() {
  if (!contextRef.value || !contextRows.value.length) return
  if (contextChart) {
    contextChart.dispose()
    contextChart = null
  }
  contextChart = echarts.init(contextRef.value)
  contextChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 44, right: 16, top: 24, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      ...tooltipStyle(),
      valueFormatter: (value) => `${Math.round(Number(value) * 100)}%`,
    },
    xAxis: {
      type: 'category',
      data: contextRows.value.map((row) => formatTime(row.at).slice(5)),
      axisLabel: { color: CHART_TEXT, fontSize: 10, hideOverlap: true },
      axisLine: { lineStyle: { color: CHART_AXIS } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      name: '占用率',
      min: 0,
      max: 1,
      nameTextStyle: { color: CHART_TEXT, fontSize: 10 },
      axisLabel: {
        color: CHART_TEXT,
        fontSize: 10,
        formatter: (value) => `${Math.round(value * 100)}%`,
      },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: CHART_GRID } },
    },
    series: [
      {
        name: '窗口占用率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: contextRows.value.map((row) => row.usage),
        lineStyle: { color: CHART_STRONG, width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(229,229,229,0.18)' },
            { offset: 1, color: 'rgba(229,229,229,0.02)' },
          ]),
        },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: 1 }],
          label: {
            formatter: '触顶裁剪',
            color: CHART_TEXT,
            fontSize: 10,
          },
          lineStyle: { color: 'rgba(163,163,163,0.45)', type: 'dashed' },
        },
      },
    ],
  })
}

function renderCache() {
  if (!cacheRef.value || !cacheRows.value.length) return
  if (cacheChart) {
    cacheChart.dispose()
    cacheChart = null
  }
  cacheChart = echarts.init(cacheRef.value)
  cacheChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 42, right: 18, top: 36, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      ...tooltipStyle(),
      valueFormatter: (value) => `${value} 次`,
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 2,
      textStyle: { color: CHART_TEXT, fontSize: 10 },
    },
    xAxis: {
      type: 'category',
      data: cacheRows.value.map((row) => cacheLabel(row.name)),
      axisLabel: { color: CHART_TEXT, fontSize: 10 },
      axisLine: { lineStyle: { color: CHART_AXIS } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: CHART_TEXT, fontSize: 10 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: CHART_GRID } },
    },
    series: [
      {
        name: '命中',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 6,
        data: cacheRows.value.map((row) => row.hit),
        itemStyle: { color: CHART_STRONG },
        lineStyle: { color: CHART_STRONG, width: 2 },
      },
      {
        name: '回源',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 6,
        data: cacheRows.value.map((row) => row.miss),
        itemStyle: { color: CHART_DIM },
        lineStyle: { color: CHART_DIM, width: 2 },
      },
    ],
  })
}

async function doReset() {
  try {
    await resetMetrics()
    toast.success('已重置')
    await loadAll()
  } catch (e) {
    toast.error(e.message || '重置失败')
  }
}

function cacheLabel(name) {
  return { 'hot-characters': '热门角色', tags: '标签列表' }[name] || name
}

function typeLabel(type) {
  return { 1: '角色对话', 2: '叙事生成', 3: '其他' }[type] || `类型 ${type}`
}

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false }).slice(0, 19)
}

function handleResize() {
  contextChart?.resize()
  cacheChart?.resize()
}

watch(activeTab, async (tab) => {
  if (tab !== 'cache') return
  await nextTick()
  renderCache()
})

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  contextChart?.dispose()
  cacheChart?.dispose()
})
</script>
