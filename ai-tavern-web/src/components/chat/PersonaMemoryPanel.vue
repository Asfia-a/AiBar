<template>
  <Sheet v-model:open="open">
    <SheetContent side="right" class="flex w-[min(94vw,28rem)] flex-col gap-0 p-0">
      <SheetHeader class="shrink-0 border-b border-border/60 px-5 py-4">
        <SheetTitle class="text-sm">我与记忆</SheetTitle>
        <SheetDescription class="mt-1 text-xs leading-relaxed text-muted-foreground">
          告诉角色「你是谁」，并让被窗口裁掉的早期剧情以摘要形式保留下来。都只对当前会话生效。
        </SheetDescription>
      </SheetHeader>

      <div class="min-h-0 flex-1 space-y-6 overflow-y-auto px-5 py-4">
        <!-- 用户人设 -->
        <section class="space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="text-xs font-medium tracking-wide text-muted-foreground uppercase">你的人设</h3>
            <span
              v-if="personaActive"
              class="rounded-full bg-secondary px-2 py-0.5 text-[10px] text-foreground"
            >生效中</span>
          </div>

          <div class="space-y-1.5">
            <Label class="text-xs text-muted-foreground">称呼</Label>
            <Input
              v-model="personaName"
              maxlength="32"
              placeholder="例：沈渡"
              class="h-9"
            />
            <p class="text-[11px] text-muted-foreground">
              会替换对话里的「你」／<code v-pre>{{user}}</code>，角色会按这个名字称呼你
            </p>
          </div>

          <div class="space-y-1.5">
            <Label class="text-xs text-muted-foreground">身份与经历</Label>
            <Textarea
              v-model="personaDesc"
              :rows="5"
              placeholder="例：二十出头的游侠，左手缺一指，说话简短，不轻易信任人。"
              class="resize-none text-[13px] leading-relaxed"
            />
            <p class="text-[11px] text-muted-foreground">
              {{ personaDesc.trim().length }} / 1500 · 留空等于不注入人设块
            </p>
          </div>

          <Button size="sm" :disabled="personaSaving || !sessionId" @click="savePersonaForm">
            <Loader2 v-if="personaSaving" class="size-4 animate-spin" />
            <Check v-else class="size-4" />
            保存人设
          </Button>
        </section>

        <div class="h-px bg-border/60" />

        <!-- 剧情摘要 -->
        <section class="space-y-3">
          <div class="flex items-center justify-between gap-2">
            <h3 class="text-xs font-medium tracking-wide text-muted-foreground uppercase">剧情摘要</h3>
            <label class="flex cursor-pointer items-center gap-2 text-xs text-muted-foreground">
              <input
                v-model="memoryEnabled"
                type="checkbox"
                class="size-3.5 accent-foreground"
                @change="toggleMemory"
              />
              自动压缩早期剧情
            </label>
          </div>

          <p class="text-[11px] leading-relaxed text-muted-foreground">
            对话变长后，最早的几轮会滚出上下文。开启后系统会把它们压成摘要再塞回提示词，角色不容易「失忆」。
          </p>

          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <Label class="text-xs text-muted-foreground">当前摘要</Label>
              <span class="text-[11px] text-muted-foreground">
                {{ summary.trim().length }} / 1200
              </span>
            </div>
            <Textarea
              v-model="summary"
              :rows="8"
              :disabled="!memoryEnabled"
              placeholder="尚无摘要。聊得足够长之后会自动出现，也可以点下方「立即刷新」。"
              class="resize-none text-[13px] leading-relaxed"
            />
          </div>

          <div class="flex flex-wrap gap-2">
            <Button
              size="sm"
              :disabled="memorySaving || !sessionId"
              @click="saveMemoryForm"
            >
              <Loader2 v-if="memorySaving" class="size-4 animate-spin" />
              <Check v-else class="size-4" />
              保存摘要
            </Button>
            <Button
              size="sm"
              variant="outline"
              :disabled="memoryRefreshing || !sessionId"
              @click="doRefresh"
            >
              <Loader2 v-if="memoryRefreshing" class="size-4 animate-spin" />
              <RefreshCw v-else class="size-4" />
              立即刷新
            </Button>
            <Button
              size="sm"
              variant="ghost"
              :disabled="memorySaving || !summary.trim()"
              @click="clearSummary"
            >
              清空
            </Button>
          </div>

          <p v-if="untilId" class="text-[11px] text-muted-foreground">
            已覆盖至消息 #{{ untilId }}
          </p>
        </section>
      </div>
    </SheetContent>
  </Sheet>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Check, Loader2, RefreshCw } from 'lucide-vue-next'
import {
  getMemory,
  getPersona,
  refreshMemory,
  saveMemory,
  savePersona,
} from '../../api/chat'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Textarea } from '@/components/ui/textarea'

const open = defineModel('open', { type: Boolean, default: false })
const props = defineProps({
  sessionId: { type: [String, Number], default: null },
})
const emit = defineEmits(['saved'])

const personaName = ref('')
const personaDesc = ref('')
const personaSaving = ref(false)
const personaLoaded = ref(false)

const summary = ref('')
const memoryEnabled = ref(true)
const untilId = ref(null)
const memorySaving = ref(false)
const memoryRefreshing = ref(false)
const memoryLoaded = ref(false)

const personaActive = computed(
  () =>
    personaLoaded.value &&
    (!!personaName.value.trim() || !!personaDesc.value.trim()),
)

async function load() {
  if (!props.sessionId) return
  try {
    const [p, m] = await Promise.all([
      getPersona(props.sessionId),
      getMemory(props.sessionId),
    ])
    personaName.value = p?.personaName || ''
    personaDesc.value = p?.personaDesc || ''
    personaLoaded.value = true
    summary.value = m?.summary || ''
    memoryEnabled.value = m?.enabled !== false
    untilId.value = m?.untilId ?? null
    memoryLoaded.value = true
  } catch (e) {
    toast.error(e?.message || '加载失败')
  }
}

async function savePersonaForm() {
  if (!props.sessionId) return
  personaSaving.value = true
  try {
    const res = await savePersona(props.sessionId, {
      personaName: personaName.value,
      personaDesc: personaDesc.value,
    })
    personaName.value = res?.personaName || ''
    personaDesc.value = res?.personaDesc || ''
    personaLoaded.value = true
    emit('saved')
    toast.success(
      personaName.value.trim() || personaDesc.value.trim()
        ? '人设已保存，下一轮起生效'
        : '已清空人设',
    )
  } catch (e) {
    toast.error(e?.message || '保存人设失败')
  } finally {
    personaSaving.value = false
  }
}

async function saveMemoryForm() {
  if (!props.sessionId) return
  memorySaving.value = true
  try {
    const res = await saveMemory(props.sessionId, {
      summary: summary.value,
      enabled: memoryEnabled.value,
    })
    applyMemory(res)
    emit('saved')
    toast.success('摘要已保存')
  } catch (e) {
    toast.error(e?.message || '保存摘要失败')
  } finally {
    memorySaving.value = false
  }
}

async function toggleMemory() {
  if (!props.sessionId || !memoryLoaded.value) return
  try {
    const res = await saveMemory(props.sessionId, {
      enabled: memoryEnabled.value,
    })
    applyMemory(res)
    emit('saved')
    toast.success(memoryEnabled.value ? '已开启自动摘要' : '已关闭自动摘要')
  } catch (e) {
    memoryEnabled.value = !memoryEnabled.value
    toast.error(e?.message || '切换失败')
  }
}

async function doRefresh() {
  if (!props.sessionId) return
  memoryRefreshing.value = true
  try {
    const res = await refreshMemory(props.sessionId)
    applyMemory(res)
    emit('saved')
    toast.success(res?.summary ? '摘要已更新' : '暂无需要压缩的早期剧情')
  } catch (e) {
    toast.error(e?.message || '刷新失败')
  } finally {
    memoryRefreshing.value = false
  }
}

async function clearSummary() {
  summary.value = ''
  await saveMemoryForm()
}

function applyMemory(res) {
  summary.value = res?.summary || ''
  memoryEnabled.value = res?.enabled !== false
  untilId.value = res?.untilId ?? null
  memoryLoaded.value = true
}

watch(open, (v) => {
  if (v) load()
})
watch(
  () => props.sessionId,
  () => {
    personaLoaded.value = false
    memoryLoaded.value = false
    if (open.value) load()
  },
)
</script>
