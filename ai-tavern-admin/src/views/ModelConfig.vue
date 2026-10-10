<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getModelConfig, updateModelConfig } from '../api/admin'

const loading = ref(false)
const saving = ref(false)

const modelOptions = [
  { label: 'DeepSeek V4 Flash（推荐）', value: 'deepseek-v4-flash' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'DeepSeek Reasoner', value: 'deepseek-reasoner' },
  { label: '自定义…', value: '__custom__' },
]

const form = reactive({
  provider: 'deepseek',
  modelName: 'deepseek-v4-flash',
  customModelName: '',
  baseUrl: 'https://claudex.org/v1',
  apiKey: '',
  maxTokens: 500,
  temperature: 0.7,
  topP: 0.9,
  status: 1,
})

const selectedModel = ref('deepseek-v4-flash')

function syncModelSelect(name) {
  const known = modelOptions.some((o) => o.value === name)
  if (known) {
    selectedModel.value = name
    form.modelName = name
    form.customModelName = ''
  } else {
    selectedModel.value = '__custom__'
    form.customModelName = name || ''
    form.modelName = name || ''
  }
}

function onModelChange(val) {
  if (val === '__custom__') {
    form.modelName = form.customModelName || ''
  } else {
    form.modelName = val
    form.customModelName = ''
  }
}

async function loadConfig() {
  loading.value = true
  try {
    const data = await getModelConfig()
    Object.assign(form, {
      provider: data?.provider || 'deepseek',
      modelName: data?.modelName || 'deepseek-v4-flash',
      baseUrl: data?.baseUrl || data?.apiBase || 'https://claudex.org/v1',
      apiKey: data?.apiKey || '',
      maxTokens: data?.maxTokens ?? 500,
      temperature: Number(data?.temperature ?? 0.7),
      topP: Number(data?.topP ?? 0.9),
      status: data?.status ?? 1,
    })
    syncModelSelect(form.modelName)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const modelName =
    selectedModel.value === '__custom__'
      ? (form.customModelName || '').trim()
      : selectedModel.value
  if (!modelName) {
    ElMessage.warning('请选择或填写模型名称')
    return
  }
  if (!form.baseUrl?.trim()) {
    ElMessage.warning('请填写 API Base')
    return
  }
  saving.value = true
  try {
    await updateModelConfig({
      provider: form.provider,
      modelName,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey,
      maxTokens: form.maxTokens,
      temperature: form.temperature,
      topP: form.topP,
      status: form.status,
    })
    ElMessage.success('配置已保存，立即生效')
    await loadConfig()
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <el-card v-loading="loading" shadow="hover">
    <template #header>
      <div style="font-weight: 600">AI 模型配置</div>
    </template>
    <el-form :model="form" label-width="120px" style="max-width: 680px">
      <el-form-item label="选择模型" required>
        <el-select
          v-model="selectedModel"
          style="width: 100%"
          placeholder="请选择模型"
          @change="onModelChange"
        >
          <el-option
            v-for="opt in modelOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedModel === '__custom__'" label="自定义模型" required>
        <el-input v-model="form.customModelName" placeholder="例如 deepseek-v4-flash" />
      </el-form-item>
      <el-form-item label="Provider">
        <el-input v-model="form.provider" placeholder="deepseek" />
      </el-form-item>
      <el-form-item label="API Base" required>
        <el-input v-model="form.baseUrl" placeholder="https://claudex.org/v1" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
        <div style="color: #909399; font-size: 12px; margin-top: 4px">
          含 **** 的掩码保存时不会覆盖原密钥；留空则使用 application.yml 默认值
        </div>
      </el-form-item>
      <el-form-item label="Max Tokens">
        <el-input-number v-model="form.maxTokens" :min="64" :max="128000" :step="64" />
      </el-form-item>
      <el-form-item label="Temperature">
        <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
      </el-form-item>
      <el-form-item label="Top P">
        <el-slider v-model="form.topP" :min="0" :max="1" :step="0.05" show-input />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        <el-button @click="loadConfig">重新加载</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
