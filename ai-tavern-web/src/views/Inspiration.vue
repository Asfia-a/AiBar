<template>
  <div class="insp-page space-y-8">
    <header class="insp-hero relative overflow-hidden rounded-2xl px-6 py-8 sm:px-10 sm:py-10">
      <div class="insp-orb insp-orb-a" aria-hidden="true" />
      <div class="insp-orb insp-orb-b" aria-hidden="true" />
      <div class="relative z-[1] flex flex-wrap items-end justify-between gap-4">
        <div class="max-w-xl space-y-2">
          <p class="text-[11px] tracking-[0.28em] uppercase text-tavern-400/90">Spark · Capture · Polish</p>
          <h1 class="font-display text-4xl sm:text-5xl text-tavern-100 leading-tight">灵感记录</h1>
          <p class="text-sm text-tavern-muted leading-relaxed">
            把碎片念头钉在灯下。点开即可编辑，也可按你的文风配置交给 AI 润色。
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <el-button @click="prefsOpen = true">润色配置</el-button>
          <el-button type="warning" @click="openCreate">写一条灵感</el-button>
        </div>
      </div>
    </header>

    <div class="flex flex-wrap items-center gap-2">
      <button
        v-for="t in tagFilters"
        :key="t"
        type="button"
        class="insp-chip"
        :class="{ active: filterTag === t }"
        @click="filterTag = t"
      >
        {{ t }}
      </button>
      <span class="ml-auto text-xs text-tavern-muted">{{ filtered.length }} 条</span>
    </div>

    <div v-if="filtered.length" class="insp-masonry">
      <article
        v-for="(n, idx) in filtered"
        :key="n.id"
        class="insp-note"
        :style="{ '--accent': tagAccent(n.tag), '--delay': `${Math.min(idx, 8) * 45}ms` }"
        @click="openEdit(n)"
      >
        <div class="insp-note-glow" aria-hidden="true" />
        <div class="relative z-[1] space-y-3">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <span class="insp-tag">{{ n.tag }}</span>
              <h3 class="font-display text-xl text-tavern-50 mt-2 leading-snug">{{ n.title }}</h3>
            </div>
            <div class="flex gap-1 shrink-0" @click.stop>
              <el-button text type="warning" size="small" :loading="polishingId === n.id" @click="quickPolish(n)">
                润色
              </el-button>
              <el-button text type="danger" size="small" @click="remove(n.id)">删</el-button>
            </div>
          </div>
          <p class="text-sm text-tavern-200/80 whitespace-pre-wrap leading-relaxed">{{ n.body }}</p>
          <div class="flex items-center justify-between text-[11px] text-tavern-muted pt-1">
            <span>{{ formatTime(n.updatedAt) }}</span>
            <span v-if="n.polishedAt" class="text-tavern-400/80">已润色</span>
          </div>
        </div>
      </article>
    </div>
    <div v-else class="insp-empty">
      <p class="font-display text-2xl text-tavern-200">灯还空着</p>
      <p class="text-sm text-tavern-muted mt-2">记下第一句对白、一条法则，或一点说不清的氛围。</p>
      <el-button class="mt-5" type="warning" @click="openCreate">写第一条</el-button>
    </div>

    <!-- 编辑 / 润色 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editingId ? '编辑灵感' : '新灵感'"
      width="720px"
      top="6vh"
      destroy-on-close
      class="insp-dialog"
    >
      <el-form label-position="top" class="space-y-1">
        <div class="grid grid-cols-1 sm:grid-cols-[1fr_140px] gap-3">
          <el-form-item label="标题">
            <el-input v-model="draft.title" maxlength="60" show-word-limit />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="draft.tag" class="w-full">
              <el-option v-for="t in tags" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="内容">
          <el-input v-model="draft.body" type="textarea" :rows="8" placeholder="对白、设定、氛围、未命名的念头…" />
        </el-form-item>
      </el-form>

      <div v-if="polishPreview" class="insp-compare mt-2">
        <div class="flex items-center justify-between gap-2 mb-2">
          <h4 class="font-display text-tavern-200">润色预览</h4>
          <div class="flex gap-1">
            <el-button size="small" @click="applyPolish">采用润色</el-button>
            <el-button size="small" plain @click="polishPreview = ''">丢弃</el-button>
          </div>
        </div>
        <p class="text-sm text-tavern-100 whitespace-pre-wrap leading-relaxed">{{ polishPreview }}</p>
      </div>

      <template #footer>
        <div class="flex flex-wrap justify-between gap-2 w-full">
          <el-button :loading="polishing" type="warning" plain @click="polishDraft">AI 润色</el-button>
          <div class="flex gap-2">
            <el-button @click="dialogOpen = false">取消</el-button>
            <el-button type="warning" @click="save">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 润色配置 -->
    <el-drawer v-model="prefsOpen" title="AI 润色配置" size="400px" destroy-on-close>
      <p class="text-xs text-tavern-muted mb-4">配置会保存在本机，润色时自动带上。</p>
      <el-form label-position="top">
        <el-form-item label="文风">
          <el-radio-group v-model="prefs.style">
            <el-radio-button value="literary">文艺</el-radio-button>
            <el-radio-button value="colloquial">口语</el-radio-button>
            <el-radio-button value="script">剧本</el-radio-button>
            <el-radio-button value="poetic">诗意</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="润色强度">
          <el-radio-group v-model="prefs.intensity">
            <el-radio-button value="light">轻度</el-radio-button>
            <el-radio-button value="medium">中度</el-radio-button>
            <el-radio-button value="strong">深度</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="篇幅">
          <el-radio-group v-model="prefs.length">
            <el-radio-button value="shorten">压缩</el-radio-button>
            <el-radio-button value="keep">保持</el-radio-button>
            <el-radio-button value="expand">扩写</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="输出语言">
          <el-select v-model="prefs.language" clearable placeholder="跟随原文" class="w-full">
            <el-option label="跟随原文" value="" />
            <el-option label="中文" value="中文" />
            <el-option label="English" value="English" />
            <el-option label="日本語" value="日本語" />
          </el-select>
        </el-form-item>
        <el-form-item label="保留原意">
          <el-switch v-model="prefs.keepMeaning" active-text="是" inactive-text="允许创意延伸" />
        </el-form-item>
        <el-form-item label="额外要求">
          <el-input
            v-model="prefs.customRules"
            type="textarea"
            :rows="5"
            placeholder="例如：多用五感描写；保留「银色足迹」设定；对白用短句；不要网文套路…"
          />
        </el-form-item>
      </el-form>
      <div class="flex gap-2 mt-4">
        <el-button type="warning" @click="savePrefs">保存配置</el-button>
        <el-button plain @click="resetPrefs">恢复默认</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { workshopPolishInspiration } from '../api/workshop'

const STORAGE_KEY = 'tavern_inspiration_notes'
const PREFS_KEY = 'tavern_inspiration_polish_prefs'
const tags = ['人设', '对白', '剧情', '世界观', '其他']
const tagFilters = ['全部', ...tags]

const TAG_ACCENT = {
  人设: '#d4a853',
  对白: '#7eb8da',
  剧情: '#e08a6a',
  世界观: '#8fbf7f',
  其他: '#a89070',
}

const defaultPrefs = () => ({
  style: 'literary',
  intensity: 'medium',
  length: 'keep',
  language: '',
  keepMeaning: true,
  customRules: '',
})

const notes = ref(loadNotes())
const filterTag = ref('全部')
const dialogOpen = ref(false)
const prefsOpen = ref(false)
const editingId = ref(null)
const draft = reactive({ title: '', body: '', tag: '人设' })
const prefs = reactive(loadPrefs())
const polishing = ref(false)
const polishingId = ref(null)
const polishPreview = ref('')

const filtered = computed(() => {
  const list = [...notes.value].sort((a, b) => b.updatedAt - a.updatedAt)
  if (filterTag.value === '全部') return list
  return list.filter((n) => n.tag === filterTag.value)
})

function tagAccent(tag) {
  return TAG_ACCENT[tag] || TAG_ACCENT['其他']
}

function loadNotes() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (Array.isArray(raw) && raw.length) return raw
  } catch {
    /* ignore */
  }
  const now = Date.now()
  return [
    {
      id: 'seed-1',
      title: '雨夜酒馆的门铃',
      tag: '对白',
      body: '门一开，铜铃轻响。她没有回头，只把酒杯往空位推了推。\n「……坐。」',
      updatedAt: now - 86400000,
    },
    {
      id: 'seed-2',
      title: '世界法则碎片',
      tag: '世界观',
      body: '凡说出真名者，会在月光下留下银色足迹，三日不散。',
      updatedAt: now - 3600000,
    },
  ]
}

function loadPrefs() {
  try {
    return { ...defaultPrefs(), ...(JSON.parse(localStorage.getItem(PREFS_KEY) || '{}') || {}) }
  } catch {
    return defaultPrefs()
  }
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(notes.value))
}

function savePrefs() {
  localStorage.setItem(PREFS_KEY, JSON.stringify({ ...prefs }))
  prefsOpen.value = false
  ElMessage.success('润色配置已保存')
}

function resetPrefs() {
  Object.assign(prefs, defaultPrefs())
  localStorage.setItem(PREFS_KEY, JSON.stringify({ ...prefs }))
  ElMessage.success('已恢复默认配置')
}

function formatTime(ts) {
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return ''
  }
}

function openCreate() {
  editingId.value = null
  draft.title = ''
  draft.body = ''
  draft.tag = '人设'
  polishPreview.value = ''
  dialogOpen.value = true
}

function openEdit(n) {
  editingId.value = n.id
  draft.title = n.title
  draft.body = n.body
  draft.tag = n.tag
  polishPreview.value = ''
  dialogOpen.value = true
}

function polishPayload(text, title, tag) {
  return {
    text,
    title,
    tag,
    style: prefs.style,
    intensity: prefs.intensity,
    length: prefs.length,
    language: prefs.language || undefined,
    keepMeaning: prefs.keepMeaning,
    customRules: prefs.customRules || undefined,
  }
}

async function polishDraft() {
  if (!draft.body.trim()) {
    ElMessage.warning('先写一点内容再润色')
    return
  }
  polishing.value = true
  try {
    const res = await workshopPolishInspiration(polishPayload(draft.body, draft.title, draft.tag))
    polishPreview.value = res.polished || ''
    if (res.title && !draft.title.trim()) draft.title = res.title
    ElMessage.success('润色完成，可预览后采用')
  } catch {
    /* */
  } finally {
    polishing.value = false
  }
}

function applyPolish() {
  if (!polishPreview.value) return
  draft.body = polishPreview.value
  polishPreview.value = ''
  ElMessage.success('已采用润色结果，记得保存')
}

async function quickPolish(n) {
  polishingId.value = n.id
  try {
    const res = await workshopPolishInspiration(polishPayload(n.body, n.title, n.tag))
    const i = notes.value.findIndex((x) => x.id === n.id)
    if (i >= 0 && res.polished) {
      notes.value[i] = {
        ...notes.value[i],
        body: res.polished,
        title: res.title || notes.value[i].title,
        updatedAt: Date.now(),
        polishedAt: Date.now(),
      }
      persist()
      ElMessage.success(`「${n.title}」已润色`)
    }
  } catch {
    /* */
  } finally {
    polishingId.value = null
  }
}

function save() {
  if (!draft.title.trim() || !draft.body.trim()) {
    ElMessage.warning('标题和内容都要写一点')
    return
  }
  const now = Date.now()
  if (editingId.value) {
    const i = notes.value.findIndex((n) => n.id === editingId.value)
    if (i >= 0) {
      notes.value[i] = {
        ...notes.value[i],
        title: draft.title.trim(),
        body: draft.body.trim(),
        tag: draft.tag,
        updatedAt: now,
      }
    }
  } else {
    notes.value.push({
      id: `n-${now}`,
      title: draft.title.trim(),
      body: draft.body.trim(),
      tag: draft.tag,
      updatedAt: now,
    })
  }
  persist()
  dialogOpen.value = false
  ElMessage.success('已保存到本地')
}

function remove(id) {
  notes.value = notes.value.filter((n) => n.id !== id)
  persist()
}

watch(
  prefs,
  () => {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ ...prefs }))
  },
  { deep: true },
)
</script>

<style scoped>
.insp-hero {
  background:
    linear-gradient(135deg, rgba(212, 168, 83, 0.14), transparent 42%),
    linear-gradient(210deg, rgba(45, 31, 20, 0.95), rgba(22, 16, 10, 0.98));
  border: 1px solid rgba(212, 168, 83, 0.22);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.28);
}
.insp-orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(40px);
  opacity: 0.45;
  animation: insp-float 9s ease-in-out infinite;
}
.insp-orb-a {
  width: 180px;
  height: 180px;
  right: 8%;
  top: -40px;
  background: rgba(212, 168, 83, 0.35);
}
.insp-orb-b {
  width: 140px;
  height: 140px;
  right: 22%;
  bottom: -50px;
  background: rgba(126, 184, 218, 0.22);
  animation-delay: -3s;
}
@keyframes insp-float {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-12px) scale(1.05);
  }
}

.insp-chip {
  border-radius: 999px;
  padding: 0.35rem 0.85rem;
  font-size: 12px;
  color: #a89070;
  background: rgba(45, 31, 20, 0.8);
  border: 1px solid rgba(212, 168, 83, 0.15);
  transition:
    color 0.2s,
    background 0.2s,
    border-color 0.2s,
    transform 0.2s;
}
.insp-chip:hover {
  color: #f5e6c8;
  transform: translateY(-1px);
}
.insp-chip.active {
  color: #1a1209;
  background: #d4a853;
  border-color: transparent;
}

.insp-masonry {
  columns: 1;
  column-gap: 1rem;
}
@media (min-width: 768px) {
  .insp-masonry {
    columns: 2;
  }
}
@media (min-width: 1200px) {
  .insp-masonry {
    columns: 3;
  }
}

.insp-note {
  break-inside: avoid;
  margin-bottom: 1rem;
  position: relative;
  overflow: hidden;
  border-radius: 1rem;
  padding: 1.1rem 1.15rem;
  cursor: pointer;
  border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
  background:
    linear-gradient(160deg, color-mix(in srgb, var(--accent) 12%, transparent), transparent 55%),
    rgba(32, 24, 16, 0.88);
  animation: insp-rise 0.55s ease both;
  animation-delay: var(--delay);
  transition:
    transform 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}
.insp-note:hover {
  transform: translateY(-4px);
  border-color: color-mix(in srgb, var(--accent) 70%, transparent);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.35);
}
.insp-note-glow {
  position: absolute;
  inset: auto -20% -40% auto;
  width: 140px;
  height: 140px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 28%, transparent);
  filter: blur(28px);
  opacity: 0.55;
  pointer-events: none;
}
@keyframes insp-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.insp-tag {
  display: inline-block;
  font-size: 10px;
  letter-spacing: 0.06em;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 18%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
}

.insp-empty {
  text-align: center;
  padding: 4rem 1rem;
  border-radius: 1rem;
  border: 1px dashed rgba(212, 168, 83, 0.28);
  background: rgba(26, 18, 9, 0.45);
}

.insp-compare {
  border-radius: 0.85rem;
  border: 1px solid rgba(212, 168, 83, 0.28);
  background: rgba(212, 168, 83, 0.08);
  padding: 0.9rem 1rem;
}
</style>
