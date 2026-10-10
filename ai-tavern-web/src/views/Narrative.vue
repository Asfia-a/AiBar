<template>
  <div class="space-y-6">
    <header class="nr-head">
      <div class="relative z-[1] max-w-2xl space-y-2">
        <p class="nr-eyebrow">Chat · Rewrite</p>
        <h1 class="font-display text-4xl text-tavern-100">叙事工坊</h1>
        <p class="text-sm text-tavern-muted leading-relaxed">
          挑一场聊过的对话，交给 AI 改写成小说、剧本或大纲。生成后可以改稿、导出，也能发到广场让别人看。
        </p>
      </div>
    </header>

    <el-tabs v-model="tab" class="nr-tabs">
      <!-- 生成 -->
      <el-tab-pane label="生成作品" name="generate">
        <div class="grid gap-5 lg:grid-cols-[320px_minmax(0,1fr)]">
          <aside class="space-y-4">
            <section class="tavern-card p-4 space-y-4">
              <h2 class="text-sm font-semibold text-tavern-200">1 · 选一场对话</h2>
              <el-select
                v-model="form.sessionId"
                placeholder="选择会话"
                class="w-full"
                :loading="loadingSessions"
              >
                <el-option
                  v-for="s in sessions"
                  :key="s.id"
                  :value="s.id"
                  :label="sessionLabel(s)"
                >
                  <div class="flex items-center justify-between gap-2">
                    <span class="truncate">{{ s.title || '未命名会话' }}</span>
                    <span class="text-xs text-tavern-muted shrink-0">{{ s.messageCount || 0 }} 条</span>
                  </div>
                </el-option>
              </el-select>
              <p v-if="!loadingSessions && !sessions.length" class="text-xs text-tavern-muted">
                还没有足够的对话。先去和角色聊几句，至少 4 条消息才能改写。
              </p>
            </section>

            <section class="tavern-card p-4 space-y-4">
              <h2 class="text-sm font-semibold text-tavern-200">2 · 写成什么样</h2>
              <el-form label-position="top" size="default">
                <el-form-item label="作品形式">
                  <el-radio-group v-model="form.formType">
                    <el-radio-button value="novel">小说</el-radio-button>
                    <el-radio-button value="script">剧本</el-radio-button>
                    <el-radio-button value="outline">大纲</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="文风">
                  <el-select v-model="form.style" class="w-full">
                    <el-option label="文学叙事" value="literary" />
                    <el-option label="口语自然" value="colloquial" />
                    <el-option label="戏剧对白" value="script" />
                    <el-option label="偏诗意" value="poetic" />
                  </el-select>
                </el-form-item>
                <el-form-item label="篇幅">
                  <el-radio-group v-model="form.length">
                    <el-radio-button value="short">短</el-radio-button>
                    <el-radio-button value="medium">中</el-radio-button>
                    <el-radio-button value="long">长</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="额外要求（可选）">
                  <el-input
                    v-model="form.extraInstruction"
                    type="textarea"
                    :rows="3"
                    maxlength="200"
                    show-word-limit
                    placeholder="例如：结局写成开放式；突出雨夜场景"
                  />
                </el-form-item>
              </el-form>
              <el-button
                type="warning"
                class="w-full"
                :loading="generating"
                :disabled="!canGenerate"
                @click="startGenerate"
              >
                {{ generating ? '正在改写…' : '开始生成' }}
              </el-button>
              <el-button v-if="generating" class="w-full" @click="abortGenerate">停止</el-button>
            </section>
          </aside>

          <section class="tavern-card min-h-[420px] p-5 flex flex-col">
            <div class="flex items-center justify-between gap-3 mb-4">
              <h2 class="text-sm font-semibold text-tavern-200">3 · 改写结果</h2>
              <div class="flex items-center gap-2 text-xs text-tavern-muted">
                <span v-if="meta.messageCount">{{ meta.messageCount }} 条对话素材</span>
                <span v-if="content.length">{{ content.length }} 字</span>
                <span v-if="generating" class="nr-cursor">▍</span>
              </div>
            </div>

            <div
              v-if="!content && !generating"
              class="flex-1 flex items-center justify-center text-sm text-tavern-muted"
            >
              左边选好参数，点「开始生成」，内容会一个字一个字出现。
            </div>

            <div v-else class="flex-1">
              <textarea
                v-model="content"
                :readonly="generating"
                class="w-full h-full min-h-[340px] resize-none rounded-lg border border-tavern-700 bg-tavern-900/60 p-4 text-sm leading-7 text-tavern-100 outline-none focus:border-tavern-400"
                placeholder="生成结果会显示在这里，可以直接修改"
              />
            </div>

            <div v-if="content && !generating" class="mt-4 flex flex-wrap items-center gap-2">
              <span v-if="savedWorkId" class="text-xs text-tavern-muted">
                已存为草稿，改完到「我的作品」里再保存
              </span>
              <el-button @click="download('txt')">导出 TXT</el-button>
              <el-button @click="download('md')">导出 Markdown</el-button>
              <el-button @click="tab = 'mine'">去我的作品</el-button>
              <el-button text @click="resetResult">清空</el-button>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <!-- 我的作品 -->
      <el-tab-pane label="我的作品" name="mine">
        <div class="flex items-center justify-between mb-4">
          <span class="text-sm text-tavern-muted">共 {{ works.length }} 篇</span>
          <el-button text :loading="loadingWorks" @click="loadWorks">刷新</el-button>
        </div>
        <div v-if="works.length" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <article v-for="w in works" :key="w.id" class="tavern-card p-4 flex flex-col gap-3">
            <div class="flex items-start justify-between gap-2">
              <h3 class="font-display text-xl text-tavern-100 leading-snug line-clamp-2">{{ w.title }}</h3>
              <el-tag size="small" :type="statusTagType(w)" effect="dark">{{ statusLabel(w) }}</el-tag>
            </div>
            <p class="text-sm text-tavern-200/75 line-clamp-3 leading-relaxed flex-1">{{ w.summary }}</p>
            <div class="flex items-center gap-3 text-[11px] text-tavern-muted">
              <span>{{ formLabel(w.formType) }}</span>
              <span>{{ w.wordCount }} 字</span>
              <span>{{ w.sourceMessageCount }} 条素材</span>
              <span>{{ formatTime(w.updatedAt) }}</span>
            </div>
            <div class="flex flex-wrap gap-1.5 pt-1">
              <el-button size="small" @click="openWork(w)">查看</el-button>
              <el-button size="small" @click="download('txt', w)">TXT</el-button>
              <el-button size="small" @click="download('md', w)">MD</el-button>
              <el-button v-if="w.status !== 1" size="small" type="warning" @click="publish(w)">发布</el-button>
              <el-button v-else size="small" @click="unpublish(w)">撤回</el-button>
              <el-button size="small" type="danger" plain @click="removeWork(w)">删除</el-button>
            </div>
          </article>
        </div>
        <el-empty v-else description="还没有作品，先去生成一篇" />
      </el-tab-pane>

      <!-- 社区广场 -->
      <el-tab-pane label="社区广场" name="published">
        <div class="flex items-center justify-between mb-4">
          <span class="text-sm text-tavern-muted">已发布 {{ published.length }} 篇</span>
          <el-button text :loading="loadingPublished" @click="loadPublished">刷新</el-button>
        </div>
        <div v-if="published.length" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <article
            v-for="w in published"
            :key="w.id"
            class="tavern-card p-4 flex flex-col gap-3 cursor-pointer hover:border-tavern-400 transition-colors"
            @click="openWork(w)"
          >
            <div class="flex items-start justify-between gap-2">
              <h3 class="font-display text-xl text-tavern-100 leading-snug line-clamp-2">{{ w.title }}</h3>
              <el-avatar :size="28" :src="w.avatar">{{ (w.nickname || '?')[0] }}</el-avatar>
            </div>
            <p class="text-sm text-tavern-200/75 line-clamp-3 leading-relaxed flex-1">{{ w.summary }}</p>
            <div class="flex items-center gap-3 text-[11px] text-tavern-muted">
              <span>{{ w.nickname || '匿名旅人' }}</span>
              <span v-if="w.characterName">与 {{ w.characterName }}</span>
              <span>{{ w.wordCount }} 字</span>
            </div>
          </article>
        </div>
        <el-empty v-else description="广场还没有公开的作品" />
      </el-tab-pane>
    </el-tabs>

    <!-- 阅读 / 编辑弹窗 -->
    <el-dialog v-model="readerOpen" :title="current?.title" width="min(900px, 92vw)" top="6vh">
      <div v-if="current" class="space-y-4">
        <div class="flex items-center gap-3 text-xs text-tavern-muted">
          <el-tag size="small" effect="dark">{{ formLabel(current.formType) }}</el-tag>
          <span>{{ current.wordCount }} 字</span>
          <span>{{ current.sourceMessageCount }} 条素材</span>
          <span v-if="current.sessionId">来源会话 #{{ current.sessionId }}</span>
        </div>
        <el-input
          v-model="readerContent"
          type="textarea"
          :rows="20"
          class="nr-reader"
        />
      </div>
      <template #footer>
        <el-button @click="readerOpen = false">关闭</el-button>
        <el-button @click="download('txt', current)">导出 TXT</el-button>
        <el-button type="warning" :loading="saving" @click="saveEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  deleteNarrativeWork,
  fetchNarrativeExport,
  getNarrativeSessions,
  getNarrativeWorks,
  getPublishedWorks,
  publishNarrativeWork,
  streamNarrative,
  unpublishNarrativeWork,
  updateNarrativeWork,
} from '../api/narrative'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const tab = ref('generate')
const sessions = ref([])
const works = ref([])
const published = ref([])
const loadingSessions = ref(false)
const loadingWorks = ref(false)
const loadingPublished = ref(false)
const generating = ref(false)
const saving = ref(false)
const content = ref('')
const meta = reactive({ messageCount: 0, dialogueChars: 0 })

const form = reactive({
  sessionId: null,
  formType: 'novel',
  style: 'literary',
  length: 'medium',
  extraInstruction: '',
})

let controller = null
let savedWorkId = null

const canGenerate = computed(() => !!form.sessionId && !generating.value)

const readerOpen = ref(false)
const current = ref(null)
const readerContent = ref('')

function sessionLabel(s) {
  const who = s.characterName ? s.characterName : '未知角色'
  return `${s.title || '未命名会话'} · ${who}（${s.messageCount || 0} 条）`
}

function formLabel(t) {
  return { novel: '小说', script: '剧本', outline: '大纲' }[t] || '小说'
}

function statusLabel(w) {
  if (w.status === 1) return '已发布'
  if (w.auditStatus === 2) return '发布未通过'
  return '草稿'
}

function statusTagType(w) {
  if (w.status === 1) return 'success'
  if (w.auditStatus === 2) return 'danger'
  return 'info'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = (await getNarrativeSessions()) || []
  } catch (e) {
    ElMessage.error(e.message || '会话列表加载失败')
  } finally {
    loadingSessions.value = false
  }
}

async function startGenerate() {
  if (!canGenerate.value) return
  generating.value = true
  content.value = ''
  savedWorkId = null
  meta.messageCount = 0
  meta.dialogueChars = 0
  controller = new AbortController()

  try {
    await streamNarrative(
      { ...form },
      {
        signal: controller.signal,
        onMeta: (m) => {
          meta.messageCount = m.messageCount || 0
          meta.dialogueChars = m.dialogueChars || 0
        },
        onChunk: (chunk) => {
          content.value += chunk
        },
        onDone: async (id) => {
          savedWorkId = id
          ElMessage.success('已保存为作品草稿')
          await Promise.all([loadWorks(), authStore.fetchUser()])
        },
        onError: (err) => {
          if (err?.name === 'AbortError') return
          ElMessage.error(err.message || '生成失败')
        },
      },
    )
  } catch (err) {
    if (err?.name !== 'AbortError') {
      ElMessage.error(err.message || '生成失败')
    }
  } finally {
    generating.value = false
    controller = null
  }
}

function abortGenerate() {
  controller?.abort()
  generating.value = false
  ElMessage.info('已停止')
}

function resetResult() {
  content.value = ''
  savedWorkId = null
  meta.messageCount = 0
  meta.dialogueChars = 0
}

async function loadWorks() {
  loadingWorks.value = true
  try {
    works.value = (await getNarrativeWorks()) || []
  } catch (e) {
    ElMessage.error(e.message || '作品列表加载失败')
  } finally {
    loadingWorks.value = false
  }
}

async function loadPublished() {
  loadingPublished.value = true
  try {
    published.value = (await getPublishedWorks()) || []
  } catch (e) {
    ElMessage.error(e.message || '广场加载失败')
  } finally {
    loadingPublished.value = false
  }
}

function openWork(w) {
  current.value = w
  readerContent.value = w.content || ''
  readerOpen.value = true
}

async function saveEdit() {
  if (!current.value) return
  saving.value = true
  try {
    const data = await updateNarrativeWork(current.value.id, {
      title: current.value.title,
      content: readerContent.value,
    })
    current.value = data
    ElMessage.success('已保存')
    await loadWorks()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function publish(w) {
  try {
    await publishNarrativeWork(w.id)
    ElMessage.success('已发布到广场')
    await Promise.all([loadWorks(), loadPublished()])
  } catch (e) {
    ElMessage.error(e.message || '发布失败')
    await loadWorks()
  }
}

async function unpublish(w) {
  try {
    await unpublishNarrativeWork(w.id)
    ElMessage.success('已撤回')
    await Promise.all([loadWorks(), loadPublished()])
  } catch (e) {
    ElMessage.error(e.message || '撤回失败')
  }
}

async function removeWork(w) {
  try {
    await ElMessageBox.confirm(`确定删除《${w.title}》？删除后不可恢复。`, '删除作品', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteNarrativeWork(w.id)
    ElMessage.success('已删除')
    await loadWorks()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

async function download(format, w) {
  const id = w?.id ?? savedWorkId
  if (!id) {
    ElMessage.warning('还没有可导出的作品')
    return
  }
  try {
    const response = await fetchNarrativeExport(id, format)
    const blob = await response.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `${(w?.title || 'narrative').replace(/[\\/:*?"<>|]/g, '_')}.${format}`
    a.click()
    URL.revokeObjectURL(a.href)
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadWorks(), loadPublished()])
})

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<style scoped>
.nr-head {
  position: relative;
  overflow: hidden;
  border-radius: 1rem;
  border: 1px solid rgba(61, 42, 26, 0.5);
  background:
    radial-gradient(120% 140% at 10% 0%, rgba(201, 149, 44, 0.16), transparent 60%),
    rgba(45, 31, 20, 0.6);
  padding: 2rem 2.5rem;
}

.nr-eyebrow {
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(212, 168, 83, 0.9);
}

.nr-tabs :deep(.el-tabs__item) {
  color: #a99578;
}

.nr-tabs :deep(.el-tabs__item.is-active) {
  color: #d4a853;
}

.nr-cursor {
  animation: nr-blink 1s step-end infinite;
  color: #d4a853;
}

@keyframes nr-blink {
  50% {
    opacity: 0;
  }
}

.nr-reader :deep(.el-textarea__inner) {
  font-family: Georgia, 'Songti SC', serif;
  line-height: 2;
  font-size: 15px;
}
</style>
