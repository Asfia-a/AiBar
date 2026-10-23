<template>
  <Dialog :open="modelValue" @update:open="(v) => emit('update:modelValue', v)">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>模拟充值</DialogTitle>
        <DialogDescription>
          对话按积分扣费（约 {{ rate }} API Token = 1 积分）。演示假支付，不会真实扣款。
        </DialogDescription>
      </DialogHeader>

      <div class="grid gap-2">
        <button
          v-for="p in packages"
          :key="p.id"
          type="button"
          class="flex items-center justify-between gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors"
          :class="
            selected?.id === p.id
              ? 'border-foreground/40 bg-secondary'
              : 'border-border/70 hover:border-border'
          "
          @click="selected = p"
        >
          <span class="min-w-0">
            <span class="block text-sm font-medium">{{ p.name }}</span>
            <span class="mt-0.5 block truncate text-xs text-muted-foreground">{{ p.desc }}</span>
          </span>
          <span class="shrink-0 text-right">
            <span class="block text-sm font-medium">¥{{ p.amount }}</span>
            <span class="block text-xs text-muted-foreground">{{ p.points }} 积分</span>
          </span>
        </button>
      </div>

      <div class="space-y-2">
        <p class="text-xs text-muted-foreground">支付方式（模拟）</p>
        <div class="flex gap-1.5">
          <Button
            v-for="c in channels"
            :key="c.value"
            :variant="channel === c.value ? 'secondary' : 'outline'"
            size="sm"
            class="flex-1"
            :disabled="paying"
            @click="channel = c.value"
          >
            {{ c.label }}
          </Button>
        </div>
      </div>

      <div v-if="paying" class="space-y-2 rounded-lg border border-border/70 p-3">
        <p class="text-xs text-muted-foreground">正在通过「{{ channelName }}」模拟支付…</p>
        <div class="h-1.5 w-full overflow-hidden rounded-full bg-muted">
          <div
            class="h-full rounded-full bg-foreground transition-all duration-200"
            :style="{ width: `${progress}%` }"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" :disabled="paying" @click="emit('update:modelValue', false)">
          取消
        </Button>
        <Button :disabled="!selected || paying" @click="pay">
          确认支付 ¥{{ selected?.amount ?? 0 }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { recharge } from '../api/user'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

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
  { id: 'whale', name: '通宵包', amount: 98, points: 2000, desc: '长篇沉浸专属' },
]
const channels = [
  { value: 'wechat', label: '微信' },
  { value: 'alipay', label: '支付宝' },
  { value: 'card', label: '银行卡' },
]

const selected = ref(packages.find((p) => p.id === props.prefer) || packages[1])
const channel = ref('wechat')
const paying = ref(false)
const progress = ref(0)

const channelName = computed(
  () => channels.find((c) => c.value === channel.value)?.label || '模拟支付',
)

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      selected.value = packages.find((p) => p.id === props.prefer) || packages[1]
      paying.value = false
      progress.value = 0
    }
  },
)

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
    toast.success(`充值成功，到账 ${res.points ?? selected.value.points} 积分`)
    emit('success', res)
    emit('update:modelValue', false)
  } catch (e) {
    toast.error(e.message || '充值失败')
  } finally {
    paying.value = false
  }
}
</script>
