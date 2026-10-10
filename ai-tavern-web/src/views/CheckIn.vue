<template>
  <div class="max-w-lg mx-auto tavern-card p-8 text-center space-y-6">
    <h1 class="font-display text-3xl text-tavern-200">每日签到</h1>
    <p class="text-tavern-muted">连续签到可获得更多积分（对话按积分扣费）</p>
    <el-button type="warning" size="large" :loading="loading" @click="doCheckIn">立即签到</el-button>
    <div v-if="result" class="text-tavern-300">
      获得 {{ result.points }} 积分，连续 {{ result.consecutive }} 天
    </div>
    <el-timeline class="text-left">
      <el-timeline-item v-for="r in records" :key="r.id" :timestamp="r.checkDate">
        +{{ r.points }} 积分 · 连续 {{ r.consecutive }} 天
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkIn, getCheckInRecords } from '../api/checkin'

const loading = ref(false)
const result = ref(null)
const records = ref([])

async function doCheckIn() {
  loading.value = true
  try {
    result.value = await checkIn()
    ElMessage.success('签到成功')
    records.value = (await getCheckInRecords()) || []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  records.value = (await getCheckInRecords()) || []
})
</script>
