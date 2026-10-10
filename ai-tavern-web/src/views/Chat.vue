<template>
  <div class="chat-page flex h-[calc(100vh-7.5rem)] gap-4">
    <!-- 会话侧栏 -->
    <aside class="chat-aside hidden md:flex w-[280px] flex-col overflow-hidden shrink-0">
      <div class="chat-aside__head px-4 py-3.5">
        <h2 class="font-display text-xl text-tavern-100">历史会话</h2>
        <p class="text-[11px] text-tavern-muted mt-0.5">{{ sessions.length }} 段故事</p>
      </div>
      <div class="flex-1 overflow-y-auto p-2.5 space-y-1.5">
        <button
          v-for="s in sessions"
          :key="s.id"
          type="button"
          class="chat-session"
          :class="{ active: String(s.id) === String(sessionId) }"
          @click="openSession(s.id)"
        >
          <div class="chat-session__avatar">
            <img
              v-if="s.characterAvatar || s.character_avatar"
              :src="s.characterAvatar || s.character_avatar"
              alt=""
            />
            <span v-else>{{ (s.characterName || s.title || '?')[0] }}</span>
          </div>
          <div class="min-w-0 flex-1 text-left">
            <div class="truncate text-sm text-tavern-100 font-medium">
              {{ s.title || s.characterName || '未命名会话' }}
            </div>
            <div class="truncate text-[11px] text-tavern-muted mt-0.5">
              {{ s.lastMessage || '尚无消息' }}
            </div>
          </div>
        </button>
        <div v-if="!sessions.length" class="text-center text-xs text-tavern-muted py-10 px-4">
          从角色页点「开始对话」即可开启第一段故事
        </div>
      </div>
    </aside>

    <!-- 主对话区 -->
    <section class="chat-stage flex-1 flex flex-col overflow-hidden relative min-w-0">
      <div
        v-if="bgUrl"
        class="chat-bg pointer-events-none absolute inset-0 z-0 overflow-hidden"
        aria-hidden="true"
      >
        <img :src="bgUrl" alt="" class="chat-bg__fill" />
        <div class="absolute inset-0 flex items-center justify-center">
          <img :src="bgUrl" alt="" class="chat-bg__hero" />
        </div>
        <div class="chat-bg__veil" />
        <div class="chat-bg__vignette" />
      </div>
      <div v-else class="chat-stage__fallback absolute inset-0 z-0" aria-hidden="true" />

      <header class="chat-header relative z-10">
        <div class="flex items-center gap-3 min-w-0">
          <div class="chat-header__avatar shrink-0">
            <img v-if="bgUrl" :src="bgUrl" alt="" />
            <span v-else>{{ (current?.characterName || '聊')[0] }}</span>
          </div>
          <div class="min-w-0">
            <h2 class="font-display text-2xl text-tavern-50 truncate leading-tight drop-shadow">
              {{ current?.characterName || '选择或开始一段对话' }}
            </h2>
            <p class="text-[11px] text-tavern-200/75 mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5">
              <span>
                积分
                <em class="not-italic text-tavern-300 font-semibold">{{ formatNum(pointsBalance) }}</em>
              </span>
              <span class="opacity-50">·</span>
              <span class="opacity-80">约 {{ tokensPerPoint }} Token / 积分</span>
              <button type="button" class="chat-link" @click="rechargeOpen = true">充值</button>
            </p>
          </div>
        </div>
        <div class="flex items-center gap-1.5 shrink-0">
          <el-button
            v-if="characterId"
            class="!hidden sm:!inline-flex"
            :type="favorited ? 'warning' : 'default'"
            plain
            size="small"
            :loading="favLoading"
            @click="toggleFav"
          >
            {{ favorited ? '已收藏' : '收藏' }}
          </el-button>
          <el-button plain type="warning" size="small" @click="rechargeOpen = true">模拟充值</el-button>
          <el-button
            v-if="sessionId"
            text
            type="danger"
            size="small"
            @click="removeSession"
          >删除</el-button>
        </div>
      </header>

      <div ref="listRef" class="chat-stream relative z-10 flex-1 overflow-y-auto px-3 sm:px-5 py-4">
        <div v-if="!messages.length && !streaming" class="chat-empty">
          <div class="chat-empty__orb" />
          <p class="font-display text-2xl text-tavern-100">烛火已备好</p>
          <p class="text-sm text-tavern-muted mt-2 max-w-xs mx-auto leading-relaxed">
            {{ current?.characterName
              ? `向「${current.characterName}」说第一句话吧`
              : '从角色详情进入，或从左侧打开历史会话' }}
          </p>
        </div>

        <div class="mx-auto max-w-3xl space-y-5">
          <div
            v-for="(m, idx) in messages"
            :key="m.id || m._tmp"
            class="chat-row"
            :class="m.role === 'user' ? 'is-user' : 'is-ai'"
            :style="{ '--i': Math.min(idx, 12) }"
          >
            <div v-if="m.role !== 'user'" class="chat-row__face">
              <img v-if="bgUrl" :src="bgUrl" alt="" />
              <span v-else>AI</span>
            </div>
            <div class="chat-bubble" :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
              <div class="chat-bubble__body whitespace-pre-wrap">
                {{ m.content }}<span v-if="m._streaming" class="chat-cursor">▍</span>
              </div>
              <div
                v-if="m.role === 'assistant' && m.id && !streaming"
                class="chat-bubble__actions"
              >
                <button type="button" class="chat-mini-btn" @click="regenerate(m.id)">重新生成</button>
              </div>
            </div>
            <div v-if="m.role === 'user'" class="chat-row__face is-self">我</div>
          </div>
        </div>
      </div>

      <footer class="chat-composer relative z-10">
        <!-- 上下文占用条：把后端的窗口裁剪显式画出来 -->
        <div v-if="ctxMeta" class="px-1 pb-1.5">
          <div class="flex h-1.5 w-full overflow-hidden rounded bg-tavern-800/60">
            <div class="bg-amber-600/80" :style="{ width: `${ctxSysPct}%` }" title="角色人设提示"></div>
            <div class="bg-amber-500/70" :style="{ width: `${ctxDiaPct}%` }" title="历史对话"></div>
            <div class="bg-tavern-600/50" :style="{ width: `${ctxOutPct}%` }" title="输出预留"></div>
          </div>
          <p class="mt-1 text-[10px] text-tavern-muted">
            上下文 {{ ctxMeta.usedRounds }}/{{ ctxMeta.window }} 轮
            <span v-if="ctxMeta.droppedRounds > 0" class="text-amber-400/90">
              · 已丢弃 {{ ctxMeta.droppedRounds }} 条更早的消息
            </span>
            <span class="opacity-60"> · 约 {{ ctxMeta.estimatedTokens }} tokens 输入</span>
          </p>
        </div>

        <div class="chat-composer__shell">
          <el-input
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="说点什么… Enter 发送，Shift+Enter 换行"
            resize="none"
            @keydown.enter.exact.prevent="send"
          />
          <div class="chat-composer__bar">
            <span class="text-[11px] text-tavern-muted hidden sm:inline">
              {{ streaming ? '对方正在回应…' : '酒馆灯光下，慢慢说' }}
            </span>
            <el-button
              type="warning"
              class="chat-send"
              :loading="streaming"
              :disabled="!input.trim()"
              @click="send"
            >
              发送
            </el-button>
          </div>
        </div>
      </footer>
    </section>

    <MockRechargeDialog v-model="rechargeOpen" prefer="basic" @success="onRecharged" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSession, deleteSession, getMessages, getSessions, streamChat, streamRegenerate } from '../api/chat'
import { favoriteCharacter, getCharacterDetail } from '../api/character'
import { getTokenBalance } from '../api/user'
import { useAuthStore } from '../stores/auth'
import MockRechargeDialog from '../components/MockRechargeDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sessions = ref([])
const messages = ref([])
const input = ref('')
const streaming = ref(false)
/** 后端 meta 事件带来的上下文裁剪统计，用于输入框上方的占用条 */
const ctxMeta = ref(null)
const listRef = ref(null)
const favorited = ref(false)
const favLoading = ref(false)
const pointsBalance = ref(0)
const tokensPerPoint = ref(1000)
const rechargeOpen = ref(false)
let suppressLoad = false
let abortCtrl = null

const sessionId = computed(() => route.params.sessionId)
const current = computed(() => sessions.value.find((s) => String(s.id) === String(sessionId.value)))

/**
 * 占用条三段宽度。分母是「输入估算 + 输出上限」，即模型这次能吃下的总量。
 * 超出部分会触发裁剪，所以这个比例就是上下文压力的直接体现。
 */
const ctxBudget = computed(() => {
  const m = ctxMeta.value
  if (!m) return 1
  return (m.estimatedTokens || 0) + (m.maxTokens || 500) || 1
})
const ctxSysPct = computed(() =>
  ctxMeta.value ? Math.min(Math.round(((ctxMeta.value.systemChars || 0) / 1.5 / ctxBudget.value) * 100), 100) : 0,
)
const ctxDiaPct = computed(() =>
  ctxMeta.value
    ? Math.min(
        Math.round((((ctxMeta.value.dialogueChars || 0) / 1.5) / ctxBudget.value) * 100),
        100 - ctxSysPct.value,
      )
    : 0,
)
const ctxOutPct = computed(() =>
  ctxMeta.value
    ? Math.min(
        Math.round(((ctxMeta.value.maxTokens || 0) / ctxBudget.value) * 100),
        100 - ctxSysPct.value - ctxDiaPct.value,
      )
    : 0,
)

const characterId = computed(() => {
  const id = current.value?.characterId ?? current.value?.character_id ?? route.query.characterId
  return id != null && id !== '' ? Number(id) : null
})
const bgUrl = computed(() => {
  const url = current.value?.characterAvatar || current.value?.character_avatar || ''
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : ''
})

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

async function refreshTokenBalance() {
  try {
    const b = await getTokenBalance()
    pointsBalance.value = Number(b.points || 0)
    tokensPerPoint.value = Number(b.tokensPerPoint || 1000)
    if (authStore.user) {
      authStore.user.points = pointsBalance.value
      authStore.user.tokenBalance = b.tokenBalance
    }
  } catch {
    /* ignore */
  }
}

async function onRecharged(res) {
  if (res?.pointsBalance != null) pointsBalance.value = Number(res.pointsBalance)
  else if (res?.balance != null) pointsBalance.value = Number(res.balance)
  else await refreshTokenBalance()
  await authStore.fetchUser()
}

async function refreshSessions() {
  sessions.value = (await getSessions()) || []
}

async function loadFavoriteState() {
  if (!characterId.value) {
    favorited.value = false
    return
  }
  try {
    const c = await getCharacterDetail(characterId.value)
    favorited.value = !!c?.favorited
  } catch {
    favorited.value = false
  }
}

async function toggleFav() {
  if (!characterId.value) return
  favLoading.value = true
  try {
    const res = await favoriteCharacter(characterId.value)
    favorited.value = !!res?.favorited
    ElMessage.success(favorited.value ? '已收藏' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    favLoading.value = false
  }
}

async function loadMessages() {
  if (!sessionId.value) {
    if (!streaming.value) messages.value = []
    return
  }
  if (streaming.value || suppressLoad) return
  try {
    const list = await getMessages(sessionId.value)
    if (streaming.value || suppressLoad) return
    messages.value = Array.isArray(list) ? list : []
    await scrollBottom()
  } catch (e) {
    console.warn('loadMessages failed', e)
  }
}

function openSession(id) {
  if (streaming.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  router.push(`/chat/${id}`)
}

async function ensureSession(characterId) {
  const list = sessions.value.length ? sessions.value : ((await getSessions()) || [])
  sessions.value = list
  const existing = list.find((s) => Number(s.characterId ?? s.character_id) === Number(characterId))
  if (existing) return existing
  return createSession({ characterId: Number(characterId) })
}

function appendByTmp(tmpId, chunk) {
  if (!chunk) return
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx < 0) return
  const cur = messages.value[idx]
  messages.value[idx] = {
    ...cur,
    content: (cur.content || '') + chunk,
    _streaming: true,
  }
  scrollBottom()
}

function stopStreamingFlag(tmpId) {
  const idx = messages.value.findIndex((m) => m._tmp === tmpId)
  if (idx >= 0) {
    const cur = messages.value[idx]
    messages.value[idx] = { ...cur, _streaming: false }
  }
  streaming.value = false
}

async function send() {
  if (!input.value.trim() || streaming.value) return
  if (pointsBalance.value <= 0) {
    ElMessage.warning('积分不足，请先充值或签到')
    rechargeOpen.value = true
    return
  }
  let sid = sessionId.value
  if (!sid) {
    const cid = Number(route.query.characterId)
    if (!cid) {
      ElMessage.warning('请先从角色详情进入对话')
      return
    }
    const s = await ensureSession(cid)
    sid = s.id
    suppressLoad = true
    await router.replace(`/chat/${sid}`)
    await refreshSessions()
    await nextTick()
    suppressLoad = false
  }

  const text = input.value.trim()
  input.value = ''
  const userTmp = `u-${Date.now()}`
  const assistantTmp = `a-${Date.now()}`
  messages.value.push({ role: 'user', content: text, _tmp: userTmp })
  messages.value.push({ role: 'assistant', content: '', _tmp: assistantTmp, _streaming: true })
  streaming.value = true
  abortCtrl = new AbortController()

  try {
    await streamChat(
      sid,
      { message: text },
      {
        onChunk: ({ content, meta }) => {
          if (meta) {
            // meta 里带上下文裁剪统计，用来画占用条
            ctxMeta.value = meta
          }
          appendByTmp(assistantTmp, content)
        },
        onDone: async () => {
          stopStreamingFlag(assistantTmp)
          suppressLoad = false
          await loadMessages()
          await refreshSessions()
          await refreshTokenBalance()
        },
        onError: (e) => {
          stopStreamingFlag(assistantTmp)
          const msg = e?.message || '发送失败'
          ElMessage.error(msg)
          if (String(msg).includes('余额不足') || String(msg).includes('积分不足') || String(msg).includes('Token')) {
            rechargeOpen.value = true
            refreshTokenBalance()
          }
        },
        signal: abortCtrl.signal,
      },
    )
  } catch (e) {
    if (e?.name === 'AbortError') streaming.value = false
  }
}

async function regenerate(messageId) {
  if (streaming.value) return
  streaming.value = true
  messages.value = messages.value.filter((m) => m.id !== messageId)
  const assistantTmp = `a-${Date.now()}`
  messages.value.push({ role: 'assistant', content: '', _tmp: assistantTmp, _streaming: true })
  try {
    await streamRegenerate(
      sessionId.value,
      messageId,
      {
        onChunk: ({ content, meta }) => {
          if (meta) {
            ctxMeta.value = meta
          }
          appendByTmp(assistantTmp, content)
        },
        onDone: async () => {
          stopStreamingFlag(assistantTmp)
          await loadMessages()
        },
        onError: (e) => {
          stopStreamingFlag(assistantTmp)
          ElMessage.error(e?.message || '重新生成失败')
        },
      },
    )
  } catch (e) {
    stopStreamingFlag(assistantTmp)
    if (e?.name !== 'AbortError') ElMessage.error(e?.message || '重新生成失败')
  }
}

async function removeSession() {
  if (streaming.value) {
    ElMessage.warning('请等待当前回复完成')
    return
  }
  await deleteSession(sessionId.value)
  await refreshSessions()
  router.push('/chat')
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

watch(sessionId, (id, prev) => {
  if (streaming.value || suppressLoad) return
  if (String(id) === String(prev)) return
  loadMessages()
})
watch(characterId, loadFavoriteState, { immediate: true })

onMounted(async () => {
  await refreshSessions()
  await refreshTokenBalance()
  if (route.query.characterId && !sessionId.value) {
    const s = await ensureSession(Number(route.query.characterId))
    suppressLoad = true
    await router.replace(`/chat/${s.id}`)
    await refreshSessions()
    suppressLoad = false
  }
  await loadMessages()
  await loadFavoriteState()
})
</script>

<style scoped>
.chat-aside {
  border-radius: 1rem;
  border: 1px solid rgba(212, 168, 83, 0.18);
  background: linear-gradient(180deg, rgba(45, 31, 20, 0.92), rgba(26, 18, 9, 0.94));
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.chat-aside__head {
  border-bottom: 1px solid rgba(212, 168, 83, 0.12);
  background: linear-gradient(90deg, rgba(212, 168, 83, 0.08), transparent);
}

.chat-session {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.65rem 0.7rem;
  border-radius: 0.85rem;
  border: 1px solid transparent;
  transition:
    background 0.2s,
    border-color 0.2s,
    transform 0.2s;
}
.chat-session:hover {
  background: rgba(212, 168, 83, 0.08);
  transform: translateX(2px);
}
.chat-session.active {
  background: rgba(212, 168, 83, 0.14);
  border-color: rgba(212, 168, 83, 0.35);
}
.chat-session__avatar {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 0.7rem;
  overflow: hidden;
  flex-shrink: 0;
  background: rgba(92, 64, 48, 0.6);
  display: grid;
  place-items: center;
  font-family: 'Cormorant Garamond', Georgia, serif;
  color: #d4a853;
  font-size: 1.1rem;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}
.chat-session__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.chat-stage {
  border-radius: 1.1rem;
  border: 1px solid rgba(212, 168, 83, 0.2);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  background: rgba(20, 14, 10, 0.55);
}
.chat-stage__fallback {
  background:
    radial-gradient(ellipse at 30% 0%, rgba(212, 168, 83, 0.12), transparent 50%),
    radial-gradient(ellipse at 80% 100%, rgba(92, 64, 48, 0.35), transparent 45%),
    linear-gradient(160deg, #241910, #140e0a);
}

.chat-bg__fill {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(32px) saturate(1.1);
  transform: scale(1.2);
  opacity: 0.9;
}
.chat-bg__hero {
  height: 100%;
  width: auto;
  max-width: min(100%, 52vh);
  object-fit: contain;
  object-position: center top;
  opacity: 0.92;
  filter: drop-shadow(0 20px 40px rgba(0, 0, 0, 0.45));
}
.chat-bg__veil {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 6, 5, 0.5) 0%, rgba(8, 6, 5, 0.22) 40%, rgba(8, 6, 5, 0.22) 60%, rgba(8, 6, 5, 0.5) 100%),
    linear-gradient(180deg, rgba(8, 6, 5, 0.2) 0%, rgba(8, 6, 5, 0.15) 55%, rgba(8, 6, 5, 0.62) 100%);
}
.chat-bg__vignette {
  position: absolute;
  inset: 0;
  box-shadow: inset 0 0 80px rgba(0, 0, 0, 0.45);
  pointer-events: none;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.42), rgba(0, 0, 0, 0.22));
  backdrop-filter: blur(8px);
}
.chat-header__avatar {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 0.85rem;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: rgba(45, 31, 20, 0.9);
  border: 1px solid rgba(212, 168, 83, 0.35);
  color: #d4a853;
  font-family: 'Cormorant Garamond', Georgia, serif;
  font-size: 1.25rem;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35);
}
.chat-header__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.chat-link {
  color: #e2c07a;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.chat-link:hover {
  color: #f5e6c8;
}

.chat-stream {
  scroll-behavior: smooth;
}
.chat-empty {
  position: relative;
  text-align: center;
  padding: 4.5rem 1rem 3rem;
}
.chat-empty__orb {
  width: 72px;
  height: 72px;
  margin: 0 auto 1.25rem;
  border-radius: 999px;
  background: radial-gradient(circle at 35% 30%, #f0d9a8, #d4a853 45%, transparent 70%);
  opacity: 0.85;
  filter: blur(0.2px);
  animation: chat-glow 3.2s ease-in-out infinite;
}
@keyframes chat-glow {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.75;
  }
  50% {
    transform: scale(1.08);
    opacity: 1;
  }
}

.chat-row {
  display: flex;
  align-items: flex-end;
  gap: 0.65rem;
  animation: chat-in 0.35s ease both;
  animation-delay: calc(var(--i, 0) * 18ms);
}
.chat-row.is-user {
  justify-content: flex-end;
}
.chat-row.is-ai {
  justify-content: flex-start;
}
@keyframes chat-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.chat-row__face {
  width: 2.15rem;
  height: 2.15rem;
  border-radius: 0.65rem;
  overflow: hidden;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  color: #d4a853;
  background: rgba(26, 18, 9, 0.75);
  border: 1px solid rgba(212, 168, 83, 0.28);
}
.chat-row__face.is-self {
  background: rgba(212, 168, 83, 0.2);
  color: #f5e6c8;
  font-size: 0.75rem;
}
.chat-row__face img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.chat-bubble {
  max-width: min(78%, 36rem);
  border-radius: 1.1rem;
  padding: 0.75rem 1rem;
  font-size: 0.925rem;
  line-height: 1.65;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.28);
}
.bubble-user {
  border-radius: 1.1rem 1.1rem 0.35rem 1.1rem;
  background: linear-gradient(145deg, #e0b45c, #c9952c);
  color: #1a1209;
}
.bubble-ai {
  border-radius: 1.1rem 1.1rem 1.1rem 0.35rem;
  background: rgba(12, 9, 7, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f7ecda;
  backdrop-filter: blur(10px);
}
.chat-bubble__actions {
  margin-top: 0.45rem;
  padding-top: 0.35rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.chat-mini-btn {
  font-size: 11px;
  color: rgba(240, 217, 168, 0.85);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
}
.chat-mini-btn:hover {
  color: #f5e6c8;
  text-decoration: underline;
}

.chat-composer {
  padding: 0.75rem 0.85rem 0.95rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.28));
  backdrop-filter: blur(10px);
}
.chat-composer__shell {
  max-width: 48rem;
  margin: 0 auto;
  border-radius: 1rem;
  border: 1px solid rgba(212, 168, 83, 0.28);
  background: rgba(18, 12, 8, 0.72);
  padding: 0.55rem 0.65rem 0.55rem;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
}
.chat-composer__shell :deep(.el-textarea__inner) {
  background: transparent !important;
  box-shadow: none !important;
  color: #f5e6c8;
  padding: 0.35rem 0.25rem;
  min-height: 3rem !important;
}
.chat-composer__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.35rem;
  padding-top: 0.35rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.chat-send {
  min-width: 5.5rem;
}

.chat-cursor {
  display: inline-block;
  margin-left: 1px;
  color: #f0d9a8;
  animation: chat-blink 1s steps(1) infinite;
}
@keyframes chat-blink {
  50% {
    opacity: 0;
  }
}
</style>
