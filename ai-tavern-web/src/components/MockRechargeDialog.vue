<template>
  <el-dialog
    :model-value="modelValue"
    title="模拟充值"
    width="440px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="text-sm text-tavern-muted mb-4">
      对话按积分扣费（约 {{ rate }} API Token = 1 积分）。演示假支付，不会真实扣款。
    </p>
    <div class="grid grid-cols-1 gap-2 mb-4">
      <button
        v-for="p in packages"
        :key="p.id"
        type="button"
        class="text-left rounded-xl border px-4 py-3 transition-colors"
        :class="selected?.id === p.id
          ? 'border-tavern-400 bg-tavern-800/80 text-tavern-100'
          : 'border-tavern-700/60 bg-tavern-900/40 text-tavern-muted hover:border-tavern-500'"
        @click="selected = p"
      >
        <div class="flex items-center justify-between gap-2">
          <div>
            <div class="font-medium text-tavern-100">{{ p.name }}</div>
            <div class="text-xs mt-1">{{ p.desc }}</div>
          </div>
          <div class="text-right shrink-0">
            <div class="text-tavern-300 font-display text-lg">¥{{ p.amount }}</div>
            <div class="text-xs">{{ p.points }} 积分</div>
          </div>
        </div>
      </button>
    </div>

    <div class="mb-4">
      <div class="text-sm text-tavern-muted mb-2">支付方式（模拟）</div>
      <el-radio-group v-model="channel">
        <el-radio-button value="wechat">微信支付</el-radio-button>
        <el-radio-button value="alipay">支付宝</el-radio-button>
        <el-radio-button value="card">银行卡</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="paying" class="rounded-lg border border-tavern-700/50 bg-tavern-900/50 p-4 text-center text-sm text-tavern-200">
      正在通过「{{ channelName }}」模拟支付…
      <el-progress :percentage="progress" :stroke-width="8" class="mt-3" />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)" :disabled="paying">取消</el-button>
      <el-button type="warning" :loading="paying" :disabled="!selected" @click="pay">
        确认支付 ¥{{ selected?.amount ?? 0 }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { recharge } from '../api/user'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  prefer: { type: String, default: 'basic' },
})
const emit = defineEmits(['update:modelValue', 'success'])

const rate = 1000
const packages = [
  { id: 'trial', name: '体验包', amount: 1, points: 20, desc: '约可聊数轮，快速试用' },
  { id: 'basic', name: '畅聊包', amount: 6, points: 100, desc: '日常对话够用' },
  { id: 'pro', name: '沉浸包', amount: 30, points: 600, desc: '适合长篇扮演' },
  { id: 'whale', name: '酒馆通宵包', amount: 98, points: 2000, desc: '土豪专属' },
]

const selected = ref(packages.find((p) => p.id === props.prefer) || packages[1])
const channel = ref('wechat')
const paying = ref(false)
const progress = ref(0)

const channelName = computed(() => ({
  wechat: '微信支付',
  alipay: '支付宝',
  card: '银行卡',
}[channel.value] || '模拟支付'))

watch(() => props.modelValue, (v) => {
  if (v) {
    selected.value = packages.find((p) => p.id === props.prefer) || packages[1]
    paying.value = false
    progress.value = 0
  }
})

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

async function pay() {
  if (!selected.value || paying.value) return
  paying.value = true
  progress.value = 0
  try {
    for (const step of [18, 42, 67, 88, 100]) {
      progress.value = step
      await sleep(220)
    }
    const res = await recharge({
      amount: selected.value.amount,
      packageId: selected.value.id,
      channel: channel.value,
      mock: true,
    })
    ElMessage.success(`充值成功，到账 ${res.points ?? selected.value.points} 积分`)
    emit('success', res)
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(e.message || '充值失败')
  } finally {
    paying.value = false
  }
}
</script>
