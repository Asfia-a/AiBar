<template>
  <div class="mx-auto max-w-xl space-y-6">
    <header class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">每日签到</h1>
      <p class="text-sm text-muted-foreground">
        每天签到领积分，连续天数越多奖励越高；对话按积分扣费
      </p>
    </header>

    <!-- 连续签到 -->
    <Card class="py-10">
      <CardContent class="flex flex-col items-center gap-3 text-center">
        <p class="text-[11px] uppercase tracking-[0.2em] text-muted-foreground">连续签到</p>
        <div class="flex items-baseline gap-2">
          <span class="text-6xl font-semibold tabular-nums tracking-tight">
            {{ consecutiveDays }}
          </span>
          <span class="text-sm text-muted-foreground">天</span>
        </div>

        <p v-if="result" class="text-sm text-muted-foreground">
          本次获得 <span class="tabular-nums text-foreground">{{ result.points }}</span> 积分 · 连续
          {{ result.consecutive }} 天
        </p>
        <p v-else class="text-xs text-muted-foreground">
          {{ checkedInToday ? '今天已经签到，明天再来' : '今天还没有签到' }}
        </p>

        <Button
          size="lg"
          class="mt-2 min-w-40 rounded-full"
          :disabled="checkedInToday || loading"
          @click="doCheckIn"
        >
          <Loader2 v-if="loading" class="size-4 animate-spin" />
          <CalendarCheck v-else class="size-4" />
          {{ loading ? '签到中' : checkedInToday ? '今日已签到' : '立即签到' }}
        </Button>
      </CardContent>
    </Card>

    <!-- 奖励说明 -->
    <Card>
      <CardHeader>
        <CardTitle>签到奖励</CardTitle>
        <CardDescription>积分是对话的唯一消耗</CardDescription>
      </CardHeader>
      <CardContent>
        <ul class="space-y-2.5 text-sm leading-relaxed text-muted-foreground">
          <li class="flex gap-2.5">
            <span class="mt-[7px] size-1 shrink-0 rounded-full bg-muted-foreground" />
            每日签到可获得一笔积分奖励，当天只能签到一次
          </li>
          <li class="flex gap-2.5">
            <span class="mt-[7px] size-1 shrink-0 rounded-full bg-muted-foreground" />
            连续签到天数越多，单次奖励越高
          </li>
          <li class="flex gap-2.5">
            <span class="mt-[7px] size-1 shrink-0 rounded-full bg-muted-foreground" />
            中断后连续天数从第 1 天重新计算
          </li>
        </ul>
      </CardContent>
    </Card>

    <!-- 签到记录 -->
    <Card>
      <CardHeader>
        <CardTitle>签到记录</CardTitle>
        <CardDescription v-if="records.length">{{ records.length }} 条</CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="recordsLoading" class="space-y-2">
          <Skeleton v-for="i in 4" :key="i" class="h-8 rounded-md" />
        </div>
        <div v-else-if="records.length" class="divide-y divide-border/60">
          <div
            v-for="r in records"
            :key="r.id"
            class="flex items-center justify-between gap-3 py-2.5 text-sm first:pt-0 last:pb-0"
          >
            <span class="tabular-nums text-muted-foreground">{{ formatDate(r.checkDate) }}</span>
            <span class="flex items-center gap-3">
              <span class="text-xs text-muted-foreground">连续 {{ r.consecutive ?? 0 }} 天</span>
              <span class="tabular-nums">+{{ r.points ?? 0 }} 积分</span>
            </span>
          </div>
        </div>
        <p v-else class="py-4 text-center text-sm text-muted-foreground">还没有签到记录</p>
      </CardContent>
    </Card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import { CalendarCheck, Loader2 } from 'lucide-vue-next'
import { checkIn, getCheckInRecords } from '../api/checkin'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

const loading = ref(false)
const result = ref(null)
const records = ref([])
const recordsLoading = ref(false)

function dateOf(value) {
  if (!value) return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  const raw = String(value).trim()
  // 只把 "yyyy-MM-dd HH:mm:ss" 里的空格换成 T，避免破坏 Date.toString() 之类的中文/英文长格式
  const normalized = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(raw) ? raw.replace(' ', 'T') : raw
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function dateKey(value) {
  const date = dateOf(value)
  if (!date) return String(value || '').slice(0, 10)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatDate(value) {
  return dateOf(value) ? dateKey(value) : String(value || '')
}

const latestRecord = computed(() => {
  return records.value.reduce((latest, r) => {
    if (!latest) return r
    const a = dateOf(r.checkDate)?.getTime() || 0
    const b = dateOf(latest.checkDate)?.getTime() || 0
    return a >= b ? r : latest
  }, null)
})

/** 优先用本次签到结果，否则取最近一条记录 */
const consecutiveDays = computed(() => {
  const value = result.value?.consecutive ?? latestRecord.value?.consecutive ?? 0
  return Number(value) || 0
})

const checkedInToday = computed(() => {
  if (result.value) return true
  const today = dateKey(new Date())
  return records.value.some((r) => dateKey(r.checkDate) === today)
})

async function doCheckIn() {
  loading.value = true
  try {
    result.value = await checkIn()
    toast.success('签到成功')
    records.value = (await getCheckInRecords()) || []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  recordsLoading.value = true
  try {
    records.value = (await getCheckInRecords()) || []
  } finally {
    recordsLoading.value = false
  }
})
</script>
