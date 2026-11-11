<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button
        :variant="variant"
        :size="size"
        class="h-8 max-w-[16rem] shrink-0 gap-1.5 px-2.5"
        :disabled="disabled"
        :title="title"
      >
        <Cpu class="size-3.5 shrink-0 opacity-70" />
        <span class="truncate">{{ currentLabel }}</span>
        <ChevronsUpDown class="size-3 shrink-0 opacity-50" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent :align="align" class="w-72 p-1">
      <DropdownMenuLabel class="text-xs text-muted-foreground">
        {{ label }} · {{ models.length }} 个
      </DropdownMenuLabel>
      <div class="max-h-72 overflow-y-auto py-1">
        <template v-for="(group, gi) in groups" :key="group.key">
          <DropdownMenuSeparator v-if="gi > 0" />
          <DropdownMenuLabel class="text-[10px] tracking-wide text-muted-foreground uppercase">
            {{ group.label }}
          </DropdownMenuLabel>
          <DropdownMenuRadioGroup :model-value="modelValue" @update:model-value="onPick">
            <DropdownMenuRadioItem
              v-for="m in group.models"
              :key="m.id"
              :value="m.id"
              class="cursor-pointer items-start py-2"
            >
              <div class="flex min-w-0 flex-col gap-0.5">
                <span class="truncate">{{ m.label }}</span>
                <span class="truncate font-mono text-[10px] font-normal text-muted-foreground">
                  {{ m.id }}
                </span>
              </div>
            </DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </template>
      </div>
    </DropdownMenuContent>
  </DropdownMenu>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronsUpDown, Cpu } from 'lucide-vue-next'
import { getChatModels } from '../api/chat'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

const STORAGE_KEY = 'tavern-chat-model'

const FALLBACK = [
  { id: 'deepseek-v4.1-flash', label: 'DeepSeek V4.1 Flash' },
  { id: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { id: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
  { id: 'deepseek-v3.2', label: 'DeepSeek V3.2' },
  { id: 'gemini-3-flash', label: 'Gemini 3 Flash' },
  { id: 'gemini-3.8-flash', label: 'Gemini 3.8 Flash' },
  { id: 'gemini-4-argon', label: 'Gemini 4 Argon' },
  { id: 'claude-opus-4-6', label: 'Claude Opus 4.6' },
  { id: 'glm-5.3', label: 'GLM 5.3' },
  { id: 'glm-5.3-flash', label: 'GLM 5.3 Flash' },
  { id: 'kimi-k3', label: 'Kimi K3' },
]

const GROUP_DEFS = [
  { key: 'deepseek', label: 'DeepSeek', match: (id) => id.startsWith('deepseek') },
  { key: 'gemini', label: 'Gemini', match: (id) => id.startsWith('gemini') },
  { key: 'claude', label: 'Claude', match: (id) => id.startsWith('claude') },
  { key: 'glm', label: '智谱 GLM', match: (id) => id.startsWith('glm') },
  { key: 'kimi', label: 'Kimi', match: (id) => id.startsWith('kimi') },
]

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  label: { type: String, default: '选择模型' },
  title: { type: String, default: '切换 AI 模型（密钥在服务端）' },
  variant: { type: String, default: 'outline' },
  size: { type: String, default: 'sm' },
  align: { type: String, default: 'start' },
  persist: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue'])

const models = ref([...FALLBACK])
const defaultModel = ref('deepseek-v4.1-flash')

const currentLabel = computed(() => {
  const hit = models.value.find((m) => m.id === props.modelValue)
  return hit?.label || props.modelValue || '选择模型'
})

const groups = computed(() => {
  const list = models.value || []
  const out = GROUP_DEFS.map((g) => ({
    key: g.key,
    label: g.label,
    models: list.filter((m) => g.match(String(m.id || ''))),
  })).filter((g) => g.models.length)
  const used = new Set(out.flatMap((g) => g.models.map((m) => m.id)))
  const other = list.filter((m) => !used.has(m.id))
  if (other.length) out.push({ key: 'other', label: '其它', models: other })
  return out
})

function onPick(id) {
  if (!id || id === props.modelValue) return
  emit('update:modelValue', id)
  if (props.persist) {
    localStorage.setItem(STORAGE_KEY, id)
  }
}

function preferInitial() {
  if (props.modelValue && models.value.some((m) => m.id === props.modelValue)) return
  const saved = props.persist ? localStorage.getItem(STORAGE_KEY) : null
  const next =
    (saved && models.value.some((m) => m.id === saved) && saved) ||
    defaultModel.value ||
    models.value[0]?.id ||
    ''
  if (next) emit('update:modelValue', next)
}

async function load() {
  try {
    const data = await getChatModels()
    if (Array.isArray(data?.models) && data.models.length) {
      models.value = data.models
    }
    if (data?.defaultModel) defaultModel.value = data.defaultModel
  } catch {
    /* 用兜底列表 */
  }
  preferInitial()
}

watch(
  () => props.modelValue,
  (v) => {
    if (v && props.persist) localStorage.setItem(STORAGE_KEY, v)
  },
)

onMounted(load)
</script>
