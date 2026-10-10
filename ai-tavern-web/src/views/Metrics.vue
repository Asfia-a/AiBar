<template>
  <div class="space-y-6">
    <header class="vm-head">
      <div class="relative z-[1] max-w-2xl space-y-2">
        <p class="vm-eyebrow">Runtime · Metrics</p>
        <h1 class="font-display text-4xl text-tavern-100">运行机制可视化</h1>
        <p class="text-sm text-tavern-muted leading-relaxed">
          系统内部平时看不见的部分都记在这儿：上下文被裁了多少、Token 花在哪、缓存命中多少、MCP 调用多久。
        </p>
      </div>
      <div class="relative z-[1] flex items-center gap-2">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button text @click="doReset">重置统计</el-button>
      </div>
    </header>

    <el-empty v-if="!loading && !overview.hasData && !cacheRows.length" description="还没有运行数据，先去聊几句">
      <el-button type="primary" @click="$router.push('/chat')">去对话</el-button>
    </el-empty>

    <template v-else>
      <!-- 概览数字 -->
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div class="tavern-card p-4">
          <p class="text-xs text-tavern-muted">上下文平均占用</p>
          <p class="mt-1 font-display text-2xl text-tavern-100">
            {{ (overview.avgUsage * 100 || 0).toFixed(1) }}<span class="text-sm">%</span>
          </p>
          <p class="mt-1 text-xs opacity-60">窗口上限 {{ overview.maxWindow || 0 }} 轮</p>
        </div>
        <div class="tavern-card p-4">
          <p class="text-xs text-tavern-muted">采样次数</p>
          <p class="mt-1 font-display text-2xl text-tavern-100">{{ overview.sampleCount || 0 }}</p>
          <p class="mt-1 text-xs opacity-60">最近一次对话的窗口裁剪情况</p>
        </div>
        <div class="tavern-card p-4">
          <p class="text-xs text-tavern-muted">今日 Token</p>
          <p class="mt-1 font-display text-2xl text-tavern-100">{{ formatNum(token.todayTokens) }}</p>
          <p class="mt-1 text-xs opacity-60">对话消耗，不含叙事生成</p>
        </div>
        <div class="tavern-card p-4">
          <p class="text-xs text-tavern-muted">MCP 调用</p>
          <p class="mt-1 font-display text-2xl text-tavern-100">{{ mcpRows.length }}</p>
          <p class="mt-1 text-xs opacity-60">
            平均 {{ mcpRows.length ? Math.round(mcpAvg) : 0 }} ms
          </p>
        </div>
      </div>

      <!-- 上下文占用条 + 曲线 -->
      <section class="tavern-card p-5 space-y-4">
        <div class="flex items-baseline justify-between gap-3 flex-wrap">
          <h2 class="text-sm font-semibold text-tavern-200">上下文占用</h2>
          <span v-if="current" class="text-xs text-tavern-muted">
            本次用了 {{ current.usedRounds }} / {{ current.window }} 轮
            <span v-if="current.droppedRounds > 0" class="text-amber-400">
              （丢弃 {{ current.droppedRounds }} 条更早的消息）
            </span>
          </span>
        </div>

        <!-- 占用条：system 提示 + 历史对话 + 输出预留 三段 -->
        <div v-if="current" class="space-y-2">
          <div class="flex h-6 w-full overflow-hidden rounded border border-tavern-700/50">
            <div
              class="flex items-center justify-center text-[10px] text-tavern-900"
              :style="{ width: `${sysPct}%` }"
              title="system 提示"
            >
              system {{ current.systemChars }}
            </div>
            <div
              class="flex items-center justify-center text-[10px] text-tavern-900"
              :style="{ width: `${diaPct}%` }"
              title="历史对话"
            >
              对话 {{ current.dialogueChars }}
            </div>
            <div
              v-if="outPct > 0"
              class="flex items-center justify-center text-[10px] opacity-70"
              :style="{ width: `${outPct}%` }"
              title="输出预留"
            >
              输出
            </div>
            <div class="flex-1 bg-transparent"></div>
          </div>
          <p class="text-[11px] text-tavern-muted">
            估算输入 {{ current.estimatedTokens }} tokens / 输出上限 {{ current.maxTokens }} tokens
            —— 输入加输出超出模型窗口时，尾部会被截断，所以裁剪发生在这里。
          </p>
        </div>

        <div ref="contextRef" class="h-56 w-full"></div>
      </section>

      <!-- Token 明细 -->
      <div class="grid gap-4 lg:grid-cols-2">
        <section class="tavern-card p-5 space-y-3">
          <h2 class="text-sm font-semibold text-tavern-200">Token 明细</h2>
          <el-table :data="token.byType || []" size="small" empty-text="暂无消耗记录">
            <el-table-column prop="type" label="用途" width="90">
              <template #default="{ row }">{{ typeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column prop="calls" label="调用次数" width="90" />
            <el-table-column label="总 Token">
              <template #default="{ row }">{{ formatNum(row.tokens) }}</template>
            </el-table-column>
            <el-table-column label="平均">
              <template #default="{ row }">{{ Math.round(row.avg_tokens || 0) }}</template>
            </el-table-column>
          </el-table>

          <p v-if="(token.topSessions || []).length" class="pt-1 text-xs font-medium text-tavern-muted">
            最费 Token 的会话
          </p>
          <el-table v-if="(token.topSessions || []).length" :data="token.topSessions" size="small">
            <el-table-column prop="session_id" label="会话 ID" width="100" />
            <el-table-column prop="calls" label="轮数" width="70" />
            <el-table-column label="Token">
              <template #default="{ row }">{{ formatNum(row.tokens) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 缓存命中 -->
        <section class="tavern-card p-5 space-y-3">
          <div class="flex items-baseline justify-between gap-3">
            <h2 class="text-sm font-semibold text-tavern-200">缓存命中对比</h2>
            <span class="text-xs text-tavern-muted">读缓存 vs 查库</span>
          </div>
          <el-table :data="cacheRows" size="small" empty-text="暂无缓存记录">
            <el-table-column prop="name" label="业务" width="120">
              <template #default="{ row }">{{ cacheLabel(row.name) }}</template>
            </el-table-column>
            <el-table-column prop="hit" label="命中" width="70" />
            <el-table-column prop="miss" label="回源" width="70" />
            <el-table-column label="命中率">
              <template #default="{ row }">
                <span :class="row.hitRate >= 50 ? 'text-emerald-400' : 'text-amber-400'">
                  {{ row.hitRate }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column label="命中耗时">
              <template #default="{ row }">{{ row.avgHitCost }} ms</template>
            </el-table-column>
            <el-table-column label="回源耗时">
              <template #default="{ row }">{{ row.avgMissCost }} ms</template>
            </el-table-column>
          </el-table>
          <div ref="cacheRef" class="h-48 w-full"></div>
        </section>
      </div>

      <!-- MCP 时间线 -->
      <section class="tavern-card p-5 space-y-3">
        <h2 class="text-sm font-semibold text-tavern-200">MCP 调用时间线</h2>
        <el-table :data="mcpRows" size="small" empty-text="暂无 MCP 调用记录" max-height="320">
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.at) }}</template>
          </el-table-column>
          <el-table-column prop="tool" label="工具" />
          <el-table-column label="耗时">
            <template #default="{ row }">{{ row.cost }} ms</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="plain">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getCacheComparison,
  getContextOverview,
  getContextSamples,
  getMcpTimeline,
  getTokenDetail,
  resetMetrics,
} from '../api/metrics'

const loading = ref(false)
const overview = ref({ hasData: false })
const contextRows = ref([])
const cacheRows = ref([])
const mcpRows = ref([])
const token = ref({})

const contextRef = ref(null)
const cacheRef = ref(null)
let contextChart = null
let cacheChart = null

const current = computed(() => overview.value.current || null)

/** 占用条三段宽度。输入部分按 system+对话占输入预算的比例切分，右侧留输出空间 */
const sysPct = computed(() => {
  const c = current.value
  if (!c) return 0
  const inputBudget = (c.estimatedTokens + c.maxTokens) || 1
  return Math.min(Math.round((c.systemChars / 1.5 / inputBudget) * 100), 100)
})
const diaPct = computed(() => {
  const c = current.value
  if (!c) return 0
  const inputBudget = (c.estimatedTokens + c.maxTokens) || 1
  return Math.min(Math.round((c.dialogueChars / 1.5 / inputBudget) * 100), 100 - sysPct.value)
})
const outPct = computed(() => {
  const c = current.value
  if (!c) return 0
  const inputBudget = (c.estimatedTokens + c.maxTokens) || 1
  return Math.min(Math.round((c.maxTokens / inputBudget) * 100), 100 - sysPct.value - diaPct.value)
})

const mcpAvg = computed(() => {
  if (!mcpRows.value.length) return 0
  return mcpRows.value.reduce((s, r) => s + (r.cost || 0), 0) / mcpRows.value.length
})

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
    await nextTick()
    renderContext()
    renderCache()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function renderContext() {
  if (!contextRef.value || !contextRows.value.length) return
  contextChart = echarts.init(contextRef.value)
  contextChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 40, right: 16, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: contextRows.value.map((r) => formatTime(r.at).slice(5)),
      axisLabel: { color: '#a99578', fontSize: 10 },
      axisLine: { lineStyle: { color: 'rgba(212,168,83,0.25)' } },
    },
    yAxis: {
      type: 'value',
      name: '占用率',
      max: 1,
      axisLabel: {
        color: '#a99578',
        fontSize: 10,
        formatter: (v) => `${Math.round(v * 100)}%`,
      },
      splitLine: { lineStyle: { color: 'rgba(212,168,83,0.12)' } },
    },
    series: [
      {
        name: '窗口占用率',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: contextRows.value.map((r) => r.usage),
        lineStyle: { color: '#d4a853', width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(212,168,83,0.28)' },
            { offset: 1, color: 'rgba(212,168,83,0.02)' },
          ]),
        },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: 1, label: { formatter: '触顶裁剪', color: '#c08457', fontSize: 10 } }],
          lineStyle: { color: 'rgba(192,132,87,0.5)', type: 'dashed' },
        },
      },
    ],
  })
}

function renderCache() {
  if (!cacheRef.value || !cacheRows.value.length) return
  cacheChart = echarts.init(cacheRef.value)
  cacheChart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 46, right: 16, top: 20, bottom: 24 },
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: '#a99578', fontSize: 10 }, top: 0 },
    xAxis: {
      type: 'category',
      data: cacheRows.value.map((r) => cacheLabel(r.name)),
      axisLabel: { color: '#a99578', fontSize: 10 },
      axisLine: { lineStyle: { color: 'rgba(212,168,83,0.25)' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#a99578', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(212,168,83,0.12)' } },
    },
    series: [
      {
        name: '命中',
        type: 'bar',
        stack: 'total',
        data: cacheRows.value.map((r) => r.hit),
        itemStyle: { color: '#5a9e6f' },
      },
      {
        name: '回源',
        type: 'bar',
        stack: 'total',
        data: cacheRows.value.map((r) => r.miss),
        itemStyle: { color: '#8a6b4a' },
      },
    ],
  })
}

async function doReset() {
  try {
    await resetMetrics()
    ElMessage.success('已重置')
    loadAll()
  } catch (e) {
    ElMessage.error(e.message || '重置失败')
  }
}

function cacheLabel(name) {
  return { 'hot-characters': '热门角色', tags: '标签列表' }[name] || name
}

function typeLabel(t) {
  return { 1: '角色对话', 2: '叙事生成', 3: '其他' }[t] || `类型 ${t}`
}

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

function formatTime(s) {
  if (!s) return ''
  return new Date(s).toLocaleString('zh-CN', { hour12: false }).slice(0, 19)
}

function handleResize() {
  contextChart?.resize()
  cacheChart?.resize()
}

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

<style scoped>
.vm-head {
  position: relative;
  overflow: hidden;
  border-radius: 1rem;
  border: 1px solid rgba(61, 42, 26, 0.5);
  background:
    radial-gradient(120% 140% at 10% 0%, rgba(201, 149, 44, 0.16), transparent 60%),
    rgba(45, 31, 20, 0.6);
  padding: 2rem 2.5rem;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.vm-eyebrow {
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(212, 168, 83, 0.9);
}
</style>
