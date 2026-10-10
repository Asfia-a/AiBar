<template>
  <div class="space-y-8">
    <header class="space-y-2">
      <h1 class="font-display text-3xl text-tavern-200">创意工坊</h1>
      <p class="text-tavern-muted text-sm max-w-2xl">
        AI 帮你写角色卡、生成立绘。草稿会暂存在本机并写入下方「制作记录」；工作区需自行点「清空工作区」关闭。
      </p>
    </header>

    <!-- AI 角色卡 -->
    <section class="tavern-card p-5 space-y-4">
      <div class="flex items-center justify-between gap-2 flex-wrap">
        <h2 class="font-display text-xl text-tavern-200">AI 创建角色卡</h2>
        <el-tag size="small" type="warning" effect="plain">对话 + 生图</el-tag>
      </div>
      <el-form label-position="top">
        <el-form-item label="角色想法">
          <el-input
            v-model="idea"
            type="textarea"
            :rows="4"
            placeholder="例如：雾港酒馆的沉默调酒师，会读唇语，说话极短，对真名过敏…"
          />
        </el-form-item>
        <el-form-item label="附加世界观 / 灵感（可选）">
          <el-input
            v-model="worldContext"
            type="textarea"
            :rows="3"
            placeholder="可从世界树复制，或点下方「填入世界树」"
          />
          <div class="mt-2 flex flex-wrap gap-2">
            <el-button size="small" @click="fillFromWorldTree">填入世界树</el-button>
            <el-button size="small" @click="fillFromInspiration">填入最新灵感</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="withAvatar">同时生成立绘并上传 OSS</el-checkbox>
        </el-form-item>
        <div class="flex flex-wrap gap-2">
          <el-button type="warning" :loading="generating" @click="generate()">生成草稿</el-button>
          <el-button type="primary" :loading="saving" :disabled="!draft" @click="useDirect">直接保存为我的角色</el-button>
          <el-button :disabled="!draft" @click="exportSt">导出 ST JSON</el-button>
          <el-button :disabled="!draft" @click="openEdit">去编辑页微调</el-button>
          <el-button :disabled="!draft && !idea" plain @click="clearWorkspace({ clearIdea: true })">清空工作区</el-button>
        </div>
      </el-form>

      <div v-if="draft" class="rounded-xl border border-tavern-700/50 bg-tavern-950/40 p-4 space-y-3">
        <div class="flex gap-4 items-start">
          <el-avatar v-if="draft.avatar" :size="88" :src="draft.avatar" shape="square" class="!rounded-lg" />
          <div class="min-w-0 flex-1 space-y-1">
            <h3 class="font-display text-2xl text-tavern-100">{{ draft.name }}</h3>
            <p class="text-sm text-tavern-muted">{{ draft.description }}</p>
            <div class="flex flex-wrap gap-1 items-center">
              <el-tag v-if="draft.category" size="small" type="warning" effect="plain">{{ draft.category }}</el-tag>
              <el-tag v-for="t in draft.tags || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
            </div>
          </div>
        </div>
        <details class="text-sm text-tavern-muted">
          <summary class="cursor-pointer text-tavern-300">人设与开场白</summary>
          <p class="mt-2 whitespace-pre-wrap">{{ draft.personality }}</p>
          <p class="mt-2 italic text-tavern-200">开场：{{ draft.greeting }}</p>
        </details>
        <div class="flex flex-wrap gap-2 items-end">
          <el-input v-model="avatarPrompt" class="flex-1 min-w-[200px]" placeholder="立绘提示词（可改后重生）" />
          <el-button :loading="avatarLoading" @click="regenAvatar">重生立绘</el-button>
        </div>
      </div>
    </section>

    <!-- 制作记录 -->
    <section class="tavern-card p-5 space-y-4">
      <div class="flex items-center justify-between gap-2 flex-wrap">
        <div>
          <h2 class="font-display text-xl text-tavern-200">制作记录</h2>
          <p class="text-xs text-tavern-muted mt-1">本机保存最近 {{ MAX_HISTORY }} 条生成结果，可随时取回继续做。</p>
        </div>
        <el-button v-if="history.length" size="small" type="danger" plain @click="clearHistory">清空记录</el-button>
      </div>

      <div v-if="!history.length" class="text-sm text-tavern-muted py-6 text-center border border-dashed border-tavern-700/50 rounded-xl">
        还没有制作记录。生成角色卡后会自动出现在这里。
      </div>

      <ul v-else class="space-y-3">
        <li
          v-for="item in history"
          :key="item.id"
          class="rounded-xl border border-tavern-700/40 bg-tavern-950/30 p-3 flex gap-3 items-start"
        >
          <el-avatar v-if="item.draft?.avatar" :size="56" :src="item.draft.avatar" shape="square" class="!rounded-md shrink-0" />
          <div v-else class="h-14 w-14 rounded-md bg-tavern-800 flex items-center justify-center text-tavern-muted shrink-0 text-lg">
            {{ item.draft?.name?.[0] || '?' }}
          </div>
          <div class="min-w-0 flex-1 space-y-1">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-medium text-tavern-100 truncate">{{ item.draft?.name || '未命名' }}</span>
              <el-tag size="small" :type="statusTagType(item.status)" effect="plain">{{ statusLabel(item.status) }}</el-tag>
              <span class="text-[11px] text-tavern-muted">{{ formatTime(item.updatedAt || item.createdAt) }}</span>
            </div>
            <p class="text-xs text-tavern-muted line-clamp-2">{{ item.idea || item.draft?.description }}</p>
            <div class="flex flex-wrap gap-1 pt-1">
              <el-button size="small" @click="restoreRecord(item)">取回工作区</el-button>
              <el-button size="small" type="primary" plain :loading="savingId === item.id" @click="saveRecord(item)">保存为我的角色</el-button>
              <el-button size="small" plain @click="exportRecord(item)">导出 JSON</el-button>
              <el-button
                v-if="item.characterId"
                size="small"
                link
                type="warning"
                @click="router.push(`/character/${item.characterId}`)"
              >查看角色</el-button>
              <el-button size="small" link type="danger" @click="removeRecord(item.id)">删除</el-button>
            </div>
          </div>
        </li>
      </ul>
    </section>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <section class="tavern-card p-5 space-y-4">
        <div class="flex items-center justify-between gap-2">
          <h2 class="font-display text-xl text-tavern-200">角色语音</h2>
          <el-tag size="small" type="info" effect="plain">占位</el-tag>
        </div>
        <p class="text-xs text-tavern-muted">为角色选择音色、语速与情绪，对话时可自动朗读回复。</p>
        <el-form label-position="top" class="space-y-1">
          <el-form-item label="音色">
            <el-radio-group v-model="voice.timbre">
              <el-radio-button value="warm">温柔</el-radio-button>
              <el-radio-button value="cool">清冷</el-radio-button>
              <el-radio-button value="bright">明亮</el-radio-button>
              <el-radio-button value="deep">低沉</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="`语速 ${voice.rate.toFixed(1)}x`">
            <el-slider v-model="voice.rate" :min="0.7" :max="1.4" :step="0.1" />
          </el-form-item>
          <el-form-item label="试听文案">
            <el-input v-model="voice.sample" type="textarea" :rows="2" />
          </el-form-item>
          <el-button type="warning" plain @click="previewVoice">试听（浏览器语音）</el-button>
        </el-form>
      </section>

      <section class="tavern-card p-5 space-y-4">
        <div class="flex items-center justify-between gap-2">
          <h2 class="font-display text-xl text-tavern-200">对话框 HTML 风格</h2>
          <el-tag size="small" type="info" effect="plain">占位</el-tag>
        </div>
        <p class="text-xs text-tavern-muted">预设气泡皮肤，之后可写成自定义 HTML/CSS 模板。</p>
        <el-radio-group v-model="styleId" class="flex flex-wrap gap-2">
          <el-radio-button v-for="s in styles" :key="s.id" :value="s.id">{{ s.name }}</el-radio-button>
        </el-radio-group>
        <div class="rounded-xl border border-tavern-700/50 bg-tavern-950/40 p-4 min-h-[160px] space-y-3">
          <p class="text-[11px] uppercase tracking-wider text-tavern-muted">预览 · {{ activeStyle.name }}</p>
          <div class="flex justify-end">
            <div :class="activeStyle.userClass">今晚月色真美。</div>
          </div>
          <div class="flex justify-start gap-2 items-end">
            <div class="h-8 w-8 rounded-full bg-tavern-700 shrink-0" />
            <div :class="activeStyle.aiClass" v-html="activeStyle.sampleHtml" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  workshopGenerateCharacter,
  workshopGenerateAvatar,
} from '../api/workshop'
import { createCharacter } from '../api/character'
import { downloadStCardJson } from '../utils/sillyTavernCard'

const router = useRouter()
const WORK_KEY = 'tavern_workshop_draft'
const HISTORY_KEY = 'tavern_workshop_history'
const MAX_HISTORY = 30

const idea = ref('')
const worldContext = ref('')
const withAvatar = ref(true)
const generating = ref(false)
const saving = ref(false)
const savingId = ref(null)
const avatarLoading = ref(false)
const draft = ref(null)
const avatarPrompt = ref('')
const activeRecordId = ref(null)
const history = ref([])

const voice = reactive({
  timbre: 'warm',
  rate: 1,
  sample: '嘘……再靠近一点，我才告诉你今晚的秘密。',
})

const styles = [
  {
    id: 'tavern',
    name: '酒馆暖光',
    userClass: 'max-w-[80%] rounded-2xl rounded-br-md bg-tavern-500/90 text-tavern-950 px-3 py-2 text-sm',
    aiClass: 'max-w-[80%] rounded-2xl rounded-bl-md bg-tavern-800/90 text-tavern-100 px-3 py-2 text-sm border border-tavern-600/40',
    sampleHtml: '烛火晃了晃，她抬眼看你：<em>「……你来了。」</em>',
  },
  {
    id: 'ink',
    name: '水墨笺',
    userClass: 'max-w-[80%] border border-tavern-500/40 bg-transparent text-tavern-200 px-3 py-2 text-sm italic',
    aiClass: 'max-w-[80%] border-l-2 border-tavern-400 pl-3 text-tavern-100 text-sm leading-relaxed',
    sampleHtml: '纸页翻动。<br/><span class="opacity-80">风过竹林，未闻足音。</span>',
  },
  {
    id: 'neon',
    name: '霓虹夜',
    userClass: 'max-w-[80%] rounded-md bg-amber-700/80 text-amber-50 px-3 py-2 text-sm',
    aiClass: 'max-w-[80%] rounded-md bg-tavern-900 text-cyan-100 px-3 py-2 text-sm border border-cyan-700/50',
    sampleHtml: '信号接通。<strong>「目标已锁定。」</strong>',
  },
]
const styleId = ref('tavern')
const activeStyle = computed(() => styles.find((s) => s.id === styleId.value) || styles[0])

function uid() {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function loadHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    history.value = Array.isArray(raw) ? raw : []
  } catch {
    history.value = []
  }
}

function saveHistory() {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value.slice(0, MAX_HISTORY)))
}

function cloneDraft(d) {
  return d ? JSON.parse(JSON.stringify(d)) : null
}

/** 把当前工作区写入/更新制作记录 */
function upsertHistory(status, extra = {}) {
  if (!draft.value) return null
  const now = Date.now()
  const id = activeRecordId.value || uid()
  const record = {
    id,
    createdAt: now,
    updatedAt: now,
    idea: idea.value,
    worldContext: worldContext.value,
    draft: cloneDraft(draft.value),
    avatarPrompt: avatarPrompt.value,
    status,
    characterId: null,
    ...extra,
  }
  const idx = history.value.findIndex((h) => h.id === id)
  if (idx >= 0) {
    record.createdAt = history.value[idx].createdAt
    record.characterId = extra.characterId ?? history.value[idx].characterId ?? null
    history.value.splice(idx, 1, record)
  } else {
    history.value.unshift(record)
  }
  history.value = history.value.slice(0, MAX_HISTORY)
  saveHistory()
  activeRecordId.value = id
  return id
}

function clearWorkspace({ clearIdea = false } = {}) {
  draft.value = null
  avatarPrompt.value = ''
  activeRecordId.value = null
  if (clearIdea) {
    idea.value = ''
    worldContext.value = ''
  }
}

function archiveAndClear() {
  if (!draft.value) return
  upsertHistory('archived')
  clearWorkspace({ clearIdea: true })
  ElMessage.success('已收入制作记录并清空工作区')
}

function statusLabel(s) {
  return ({ generated: '草稿', saved: '已保存', exported: '已导出', archived: '已归档', editing: '编辑中' })[s] || s
}

function statusTagType(s) {
  return ({ generated: 'info', saved: 'success', exported: 'warning', archived: '', editing: 'warning' })[s] || 'info'
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function previewVoice() {
  if (!window.speechSynthesis) {
    ElMessage.warning('当前浏览器不支持语音合成')
    return
  }
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(voice.sample)
  u.rate = voice.rate
  u.pitch = voice.timbre === 'deep' ? 0.7 : voice.timbre === 'bright' ? 1.3 : voice.timbre === 'cool' ? 0.95 : 1.05
  window.speechSynthesis.speak(u)
}

function fillFromWorldTree() {
  try {
    const tree = JSON.parse(localStorage.getItem('tavern_world_tree') || '[]')
    if (!tree.length) {
      ElMessage.warning('世界树还是空的')
      return
    }
    const lines = []
    const walk = (n, d) => {
      lines.push(`${'  '.repeat(d)}[${n.type}] ${n.label}${n.summary ? ` — ${n.summary}` : ''}`)
      ;(n.children || []).forEach((c) => walk(c, d + 1))
    }
    tree.forEach((r) => walk(r, 0))
    worldContext.value = lines.join('\n')
    ElMessage.success('已填入世界树')
  } catch {
    ElMessage.error('读取世界树失败')
  }
}

function fillFromInspiration() {
  try {
    const notes = JSON.parse(localStorage.getItem('tavern_inspiration_notes') || '[]')
    if (!notes.length) {
      ElMessage.warning('还没有灵感记录')
      return
    }
    const latest = [...notes].sort((a, b) => b.updatedAt - a.updatedAt)[0]
    worldContext.value = `【灵感】${latest.title}\n${latest.body}`
    ElMessage.success('已填入最新灵感')
  } catch {
    ElMessage.error('读取灵感失败')
  }
}

function toCreatePayload(card) {
  return {
    name: card.name,
    avatar: card.avatar,
    description: card.description,
    personality: card.personality,
    backgroundStory: card.backgroundStory,
    exampleDialogue: card.exampleDialogue,
    greeting: card.greeting,
    categoryId: card.categoryId || null,
    categoryName: card.category || undefined,
    tagNames: Array.isArray(card.tags) ? card.tags : [],
    temperature: 0.7,
    topP: 0.9,
    maxTokens: 800,
  }
}

async function generate() {
  if (!idea.value.trim()) {
    ElMessage.warning('先写一点角色想法')
    return
  }
  generating.value = true
  try {
    const res = await workshopGenerateCharacter({
      idea: idea.value,
      worldContext: worldContext.value,
      withAvatar: withAvatar.value,
    })
    draft.value = res
    avatarPrompt.value = res.avatarPrompt || ''
    activeRecordId.value = null
    upsertHistory('generated')
    ElMessage.success(withAvatar.value ? '已生成，并写入制作记录' : '角色卡已生成，并写入制作记录')
  } catch {
    /* toast by interceptor */
  } finally {
    generating.value = false
  }
}

async function useDirect() {
  if (!draft.value) {
    ElMessage.warning('请先生成草稿')
    return
  }
  saving.value = true
  try {
    const card = await createCharacter(toCreatePayload(draft.value))
    upsertHistory('saved', { characterId: card.id })
    clearWorkspace({ clearIdea: true })
    ElMessage.success('已保存为我的角色，工作区已清空')
    router.push(`/edit/${card.id}`)
  } catch {
    /* */
  } finally {
    saving.value = false
  }
}

function exportSt() {
  if (!draft.value) return
  downloadStCardJson(draft.value.stCard || draft.value)
  upsertHistory('exported')
  clearWorkspace()
  ElMessage.success('已导出，草稿已收入制作记录并清空工作区')
}

function openEdit() {
  if (!draft.value) return
  upsertHistory('editing')
  sessionStorage.setItem(
    'tavern_workshop_spawn',
    JSON.stringify({ ...draft.value, at: Date.now() }),
  )
  clearWorkspace({ clearIdea: true })
  router.push({ path: '/create', query: { from: 'workshop' } })
}

async function regenAvatar() {
  if (!draft.value) return
  avatarLoading.value = true
  try {
    const res = await workshopGenerateAvatar({
      prompt: avatarPrompt.value || draft.value.avatarPrompt,
      name: draft.value.name,
    })
    draft.value = { ...draft.value, avatar: res.url }
    upsertHistory('generated')
    ElMessage.success('立绘已更新')
  } catch {
    /* */
  } finally {
    avatarLoading.value = false
  }
}

function restoreRecord(item) {
  idea.value = item.idea || ''
  worldContext.value = item.worldContext || ''
  draft.value = cloneDraft(item.draft)
  avatarPrompt.value = item.avatarPrompt || item.draft?.avatarPrompt || ''
  activeRecordId.value = item.id
  ElMessage.success(`已取回「${item.draft?.name || '草稿'}」`)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function saveRecord(item) {
  if (!item?.draft) return
  savingId.value = item.id
  try {
    const card = await createCharacter(toCreatePayload(item.draft))
    const idx = history.value.findIndex((h) => h.id === item.id)
    if (idx >= 0) {
      history.value[idx] = {
        ...history.value[idx],
        status: 'saved',
        characterId: card.id,
        updatedAt: Date.now(),
      }
      saveHistory()
    }
    ElMessage.success('已保存为我的角色')
    router.push(`/edit/${card.id}`)
  } catch {
    /* */
  } finally {
    savingId.value = null
  }
}

function exportRecord(item) {
  if (!item?.draft) return
  downloadStCardJson(item.draft.stCard || item.draft)
  const idx = history.value.findIndex((h) => h.id === item.id)
  if (idx >= 0) {
    history.value[idx] = { ...history.value[idx], status: 'exported', updatedAt: Date.now() }
    saveHistory()
  }
  ElMessage.success('已下载 JSON')
}

function removeRecord(id) {
  history.value = history.value.filter((h) => h.id !== id)
  saveHistory()
  if (activeRecordId.value === id) activeRecordId.value = null
}

async function clearHistory() {
  try {
    await ElMessageBox.confirm('确定清空全部制作记录？不可恢复。', '清空记录', { type: 'warning' })
    history.value = []
    saveHistory()
  } catch {
    /* cancel */
  }
}

function persistWork() {
  localStorage.setItem(
    WORK_KEY,
    JSON.stringify({
      idea: idea.value,
      worldContext: worldContext.value,
      withAvatar: withAvatar.value,
      voice: { ...voice },
      styleId: styleId.value,
      draft: draft.value,
      avatarPrompt: avatarPrompt.value,
      activeRecordId: activeRecordId.value,
    }),
  )
}

loadHistory()

try {
  const raw = JSON.parse(localStorage.getItem(WORK_KEY) || 'null')
  if (raw) {
    if (raw.idea) idea.value = raw.idea
    if (raw.worldContext) worldContext.value = raw.worldContext
    if (typeof raw.withAvatar === 'boolean') withAvatar.value = raw.withAvatar
    if (raw.voice) Object.assign(voice, raw.voice)
    if (raw.styleId) styleId.value = raw.styleId
    if (raw.draft) draft.value = raw.draft
    if (raw.avatarPrompt) avatarPrompt.value = raw.avatarPrompt
    if (raw.activeRecordId) activeRecordId.value = raw.activeRecordId
  }
  // 旧版滞留草稿：迁入制作记录并清空工作区，避免一直挂着
  if (draft.value) {
    const already = history.value.some(
      (h) => h.id === activeRecordId.value
        || (h.draft?.name === draft.value.name && h.idea === idea.value),
    )
    if (!already) upsertHistory('archived')
    clearWorkspace({ clearIdea: true })
  }
} catch {
  /* */
}

watch([idea, worldContext, withAvatar, voice, styleId, draft, avatarPrompt, activeRecordId], persistWork, {
  deep: true,
})
</script>
