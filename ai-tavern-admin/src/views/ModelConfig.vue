<script setup>
import { onMounted, ref } from 'vue'
import { getModelConfig } from '../api/admin'

const loading = ref(false)
const summary = ref({
  provider: 'deepseek',
  modelName: '',
  baseUrl: '',
  apiKey: '',
})

async function loadConfig() {
  loading.value = true
  try {
    const data = await getModelConfig()
    summary.value = {
      provider: data?.provider || 'deepseek',
      modelName: data?.modelName || '',
      baseUrl: data?.baseUrl || '',
      apiKey: data?.apiKey || '（未配置）',
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <el-card v-loading="loading" shadow="hover">
    <template #header>
      <div style="font-weight: 600">AI 模型配置</div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="配置已迁出管理端"
      description="API Key / Base URL 请在后端 application.yml 或环境变量（AI_API_KEY、AI_BASE_URL）中设置；用户在聊天页自行选择 DeepSeek 模型。此处仅作只读摘要。"
      style="margin-bottom: 20px"
    />

    <el-descriptions :column="1" border>
      <el-descriptions-item label="Provider">{{ summary.provider }}</el-descriptions-item>
      <el-descriptions-item label="默认模型">{{ summary.modelName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="API Base">{{ summary.baseUrl || '—' }}</el-descriptions-item>
      <el-descriptions-item label="API Key">{{ summary.apiKey || '—' }}</el-descriptions-item>
    </el-descriptions>

    <div style="margin-top: 16px">
      <el-button @click="loadConfig">刷新摘要</el-button>
    </div>
  </el-card>
</template>
