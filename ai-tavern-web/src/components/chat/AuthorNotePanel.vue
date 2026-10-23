<template>
  <Sheet v-model:open="open">
    <SheetContent side="right" class="flex w-[min(94vw,28rem)] flex-col gap-0 p-0">
      <SheetHeader class="shrink-0 border-b border-border/60 px-5 py-4">
        <SheetTitle class="text-sm">作者注释</SheetTitle>
        <SheetDescription class="mt-1 text-xs leading-relaxed text-muted-foreground">
          只在当前会话生效的一句强指令。它插进历史对话里，比人设更「近」，所以对这一轮回复的影响更大。
        </SheetDescription>
      </SheetHeader>

      <div class="min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-4">
        <div class="space-y-1.5">
          <Label class="text-xs text-muted-foreground">注释内容</Label>
          <Textarea
            v-model="note"
            :rows="6"
            placeholder="例：这一段让角色的语气更克制，少用感叹号，不要主动推进剧情。"
            class="resize-none text-[13px] leading-relaxed"
          />
          <p class="text-[11px] text-muted-foreground">
            {{ note.trim().length }} / 2000 字 · 留空等于关掉这条指令
          </p>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label class="text-xs text-muted-foreground">插入深度</Label>
            <Input v-model.number="depth" type="number" min="0" max="50" class="h-9" />
            <p class="text-[11px] text-muted-foreground">0 = 历史末尾</p>
          </div>
          <div class="space-y-1.5">
            <Label class="text-xs text-muted-foreground">频率（每 N 轮）</Label>
            <Input v-model.number="frequency" type="number" min="0" max="100" class="h-9" />
            <p class="text-[11px] text-muted-foreground">0 = 不插入，1 = 每轮都插</p>
          </div>
        </div>

        <!-- 把「什么时候真的会插进去」摊开写清楚，避免设了不生效还不知道为什么 -->
        <div class="space-y-1 rounded-lg border border-border/60 bg-secondary/40 px-3 py-2.5 text-[11px] leading-relaxed text-muted-foreground">
          <p>
            插入位置：
            <span class="text-foreground">{{ depth <= 0 ? '历史末尾（离模型最近）' : `倒数第 ${depth} 条之前` }}</span>
          </p>
          <p>
            触发频率：
            <span class="text-foreground">
              {{ frequency <= 0 ? '不插入' : `每 ${frequency} 条用户消息插一次` }}
            </span>
          </p>
          <p>
            当前状态：
            <span :class="active ? 'text-foreground' : ''">
              {{ active ? '生效中' : note.trim() && frequency > 0 ? '保存后生效' : '未启用' }}
            </span>
          </p>
        </div>

        <p class="text-[11px] leading-relaxed text-muted-foreground">
          提示：作者注释改的是「这一段怎么聊」，不影响角色卡本身；换个会话就是另一套设定。
        </p>
      </div>

      <div class="flex shrink-0 items-center gap-2 border-t border-border/60 px-5 py-3">
        <Button :disabled="saving || !sessionId" @click="save">
          <Loader2 v-if="saving" class="size-4 animate-spin" />
          <Check v-else class="size-4" />
          保存
        </Button>
        <Button variant="outline" :disabled="saving" @click="reset">清空</Button>
        <span v-if="loaded && active" class="ml-auto text-[11px] text-muted-foreground">已生效</span>
      </div>
    </SheetContent>
  </Sheet>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Check, Loader2 } from 'lucide-vue-next'
import { getAuthorNote, saveAuthorNote } from '../../api/chat'
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

const note = ref('')
const depth = ref(0)
const frequency = ref(1)
const saving = ref(false)
const loaded = ref(false)

const active = computed(
  () => !!note.value.trim() && Number(frequency.value) > 0 && loaded.value,
)

async function load() {
  if (!props.sessionId) return
  try {
    const res = await getAuthorNote(props.sessionId)
    note.value = res?.authorNote || ''
    depth.value = res?.depth ?? 0
    frequency.value = res?.frequency ?? 1
    loaded.value = true
  } catch (e) {
    toast.error(e?.message || '作者注释加载失败')
  }
}

async function save() {
  if (!props.sessionId) return
  saving.value = true
  try {
    const res = await saveAuthorNote(props.sessionId, {
      note: note.value,
      depth: Number(depth.value) || 0,
      frequency: Number(frequency.value) || 0,
    })
    note.value = res?.authorNote || ''
    depth.value = res?.depth ?? 0
    frequency.value = res?.frequency ?? 1
    loaded.value = true
    emit('saved')
    toast.success(note.value.trim() ? '作者注释已保存，下一轮起生效' : '已清空作者注释')
  } catch (e) {
    toast.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function reset() {
  note.value = ''
  depth.value = 0
  frequency.value = 1
}

// 每次打开都重新拉一次，避免在别处改过之后这里还是旧值
watch(open, (v) => {
  if (v) load()
})
watch(
  () => props.sessionId,
  () => {
    loaded.value = false
    if (open.value) load()
  },
)
</script>
