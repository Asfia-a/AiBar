<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getDashboardStats, getDashboardTrend, getPopularCharacters } from '../api/admin'

const loading = ref(false)
const stats = ref({
  dau: 0,
  totalUsers: 0,
  totalChats: 0,
  totalTokens: 0,
})
const popularCharacters = ref([])

const trendChartRef = ref()
const barChartRef = ref()
let trendChart = null
let barChart = null

function normalizeList(data) {
  if (Array.isArray(data)) return data
  return data?.list || data?.items || []
}

function formatCompact(value) {
  const n = Number(value) || 0
  if (Math.abs(n) >= 1e6) return `${(n / 1e6).toFixed(1)}M`
  if (Math.abs(n) >= 1e3) return `${(n / 1e3).toFixed(1)}k`
  return String(n)
}

function renderTrendChart(trendData) {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const dates = trendData.map((item) => item.date || item.day)
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => Number(v).toLocaleString(),
    },
    legend: {
      data: ['活跃用户', '对话数', 'Token消耗'],
      top: 4,
      left: 'center',
      itemGap: 16,
    },
    grid: { left: 16, right: 16, top: 48, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { hideOverlap: true, margin: 10 },
      boundaryGap: false,
    },
    // 左轴：用户/对话；右轴：Token，避免量级差导致其它曲线贴底
    yAxis: [
      {
        type: 'value',
        name: '用户/对话',
        splitNumber: 4,
        alignTicks: true,
        axisLabel: { formatter: formatCompact },
      },
      {
        type: 'value',
        name: 'Token',
        splitNumber: 4,
        alignTicks: true,
        axisLabel: { formatter: formatCompact },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '活跃用户',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        data: trendData.map((item) => item.users ?? item.dau ?? 0),
      },
      {
        name: '对话数',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        data: trendData.map((item) => item.chats ?? item.conversations ?? 0),
      },
      {
        name: 'Token消耗',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: trendData.map((item) => item.tokens ?? 0),
      },
    ],
  })
}

function renderBarChart(trendData) {
  if (!barChartRef.value) return
  if (!barChart) {
    barChart = echarts.init(barChartRef.value)
  }
  const dates = trendData.map((item) => item.date || item.day)
  barChart.setOption({
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => Number(v).toLocaleString(),
    },
    legend: {
      data: ['对话数', 'Token消耗'],
      top: 4,
      left: 'center',
      itemGap: 16,
    },
    grid: { left: 16, right: 16, top: 48, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { hideOverlap: true, margin: 10 },
    },
    yAxis: [
      {
        type: 'value',
        name: '对话',
        splitNumber: 4,
        alignTicks: true,
        axisLabel: { formatter: formatCompact },
      },
      {
        type: 'value',
        name: 'Token',
        splitNumber: 4,
        alignTicks: true,
        axisLabel: { formatter: formatCompact },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '对话数',
        type: 'bar',
        yAxisIndex: 0,
        barMaxWidth: 28,
        data: trendData.map((item) => item.chats ?? item.conversations ?? 0),
      },
      {
        name: 'Token消耗',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 28,
        data: trendData.map((item) => item.tokens ?? 0),
      },
    ],
  })
}

function handleResize() {
  trendChart?.resize()
  barChart?.resize()
}

async function loadData() {
  loading.value = true
  try {
    const [statsData, trendData, popularData] = await Promise.all([
      getDashboardStats(),
      getDashboardTrend({ days: 7 }),
      getPopularCharacters({ limit: 10 }),
    ])
    stats.value = {
      dau: statsData?.dau ?? 0,
      totalUsers: statsData?.userCount ?? statsData?.totalUsers ?? 0,
      totalChats: statsData?.sessionCount ?? statsData?.totalChats ?? 0,
      totalTokens: statsData?.totalTokens ?? 0,
    }
    // 后端返回 { users:[], sessions:[], tokens:[] }
    let trend = []
    if (trendData && !Array.isArray(trendData) && (trendData.users || trendData.sessions)) {
      const days = new Map()
      for (const u of trendData.users || []) {
        const key = u.day || u.DAY
        days.set(String(key), { day: key, users: Number(u.cnt || u.CNT || 0), chats: 0, tokens: 0 })
      }
      for (const s of trendData.sessions || []) {
        const key = String(s.day || s.DAY)
        const row = days.get(key) || { day: key, users: 0, chats: 0, tokens: 0 }
        row.chats = Number(s.cnt || s.CNT || 0)
        days.set(key, row)
      }
      for (const t of trendData.tokens || []) {
        const key = String(t.day || t.DAY)
        const row = days.get(key) || { day: key, users: 0, chats: 0, tokens: 0 }
        row.tokens = Number(t.cnt || t.CNT || 0)
        days.set(key, row)
      }
      trend = Array.from(days.values()).sort((a, b) => String(a.day).localeCompare(String(b.day)))
    } else {
      trend = normalizeList(trendData)
    }
    renderTrendChart(trend)
    renderBarChart(trend)
    popularCharacters.value = (normalizeList(popularData) || []).map((c) => ({
      ...c,
      creator: c.creatorName || c.creator,
    }))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  barChart?.dispose()
})
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">DAU</div>
          <div class="stat-value">{{ stats.dau.toLocaleString() }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">总用户</div>
          <div class="stat-value">{{ stats.totalUsers.toLocaleString() }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">总对话</div>
          <div class="stat-value">{{ stats.totalChats.toLocaleString() }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">Token 消耗</div>
          <div class="stat-value">{{ stats.totalTokens.toLocaleString() }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>趋势折线图</template>
          <div ref="trendChartRef" class="chart-box" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>趋势柱状图</template>
          <div ref="barChartRef" class="chart-box" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="popular-card">
      <template #header>热门角色</template>
      <el-table :data="popularCharacters" stripe>
        <el-table-column prop="name" label="角色名" min-width="160" />
        <el-table-column prop="creator" label="创建者" min-width="120" />
        <el-table-column
          prop="chatCount"
          label="对话次数"
          min-width="120"
          :formatter="(row) => (row.chatCount ?? row.count ?? 0).toLocaleString()"
        />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-label {
  color: #909399;
  font-size: 14px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-top: 8px;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-box {
  height: 320px;
}

.popular-card {
  margin-bottom: 16px;
}
</style>
