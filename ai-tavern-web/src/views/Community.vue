<template>
  <div class="space-y-6">
    <header class="cm-head">
      <div class="relative z-[1] max-w-2xl space-y-2">
        <p class="cm-eyebrow">Community</p>
        <h1 class="font-display text-4xl text-tavern-100">社区广场</h1>
        <p class="text-sm text-tavern-muted leading-relaxed">
          别人的改写都会聚在这里。点赞收藏都留痕，评论别刷太快——一分钟限三条。
        </p>
      </div>
    </header>

    <div class="flex items-center justify-between gap-3 flex-wrap">
      <el-radio-group v-model="sort" @change="reload">
        <el-radio-button value="latest">最新</el-radio-button>
        <el-radio-button value="hot">最热</el-radio-button>
      </el-radio-group>
      <span class="text-xs text-tavern-muted">共 {{ total }} 篇</span>
    </div>

    <el-empty v-if="!loading && !works.length" description="还没有人发布作品">
      <el-button type="primary" @click="$router.push('/narrative')">去写一篇</el-button>
    </el-empty>

    <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <article
        v-for="w in works"
        :key="w.id"
        class="tavern-card cm-card flex flex-col gap-3 p-4 cursor-pointer"
        @click="open(w)"
      >
        <div class="flex items-start justify-between gap-2">
          <h2 class="font-display text-lg text-tavern-100 leading-snug line-clamp-2">{{ w.title }}</h2>
          <el-tag v-if="w.formType" size="small" effect="plain">{{ formLabel(w.formType) }}</el-tag>
        </div>

        <p v-if="w.summary" class="text-sm text-tavern-muted leading-relaxed line-clamp-3">{{ w.summary }}</p>

        <div class="flex items-center gap-2 text-xs text-tavern-muted">
          <img
            v-if="w.characterAvatar"
            :src="w.characterAvatar"
            :alt="w.characterName"
            class="h-5 w-5 rounded-full object-cover"
          />
          <span class="truncate">{{ w.characterName || '未知角色' }}</span>
          <span class="opacity-60">·</span>
          <span class="truncate">{{ w.nickname || '匿名旅人' }}</span>
        </div>

        <div class="mt-auto flex items-center gap-1 pt-2 border-t border-tavern-700/40">
          <button class="cm-act" :class="{ 'is-on': w.liked }" @click.stop="onLike(w)">
            <span>{{ w.liked ? '♥' : '♡' }}</span>
            <span>{{ w.likeCount || 0 }}</span>
          </button>
          <button class="cm-act" :class="{ 'is-on': w.collected }" @click.stop="onCollect(w)">
            <span>{{ w.collected ? '★' : '☆' }}</span>
            <span>{{ w.collectCount || 0 }}</span>
          </button>
          <span class="cm-act cursor-default">
            <span>💬</span>
            <span>{{ w.commentCount || 0 }}</span>
          </span>
          <span class="ml-auto text-xs opacity-60">{{ w.wordCount || 0 }} 字</span>
        </div>
      </article>
    </div>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      class="justify-center"
      @current-change="reload"
    />

    <!-- 作品详情 -->
    <el-drawer v-model="readerOpen" :size="drawerSize" direction="rtl" :with-header="false">
      <div v-if="current" class="flex h-full flex-col gap-4">
        <div class="space-y-2">
          <div class="flex items-start justify-between gap-3">
            <h2 class="font-display text-2xl text-tavern-100 leading-snug">{{ current.title }}</h2>
            <el-tag v-if="current.formType" size="small" effect="plain">{{ formLabel(current.formType) }}</el-tag>
          </div>
          <p class="text-xs text-tavern-muted">
            {{ current.characterName || '未知角色' }} · {{ current.nickname || '匿名旅人' }} ·
            {{ current.wordCount || 0 }} 字
          </p>
        </div>

        <div class="flex items-center gap-2 text-sm">
          <button class="cm-act" :class="{ 'is-on': current.liked }" @click="onLike(current)">
            <span>{{ current.liked ? '♥' : '♡' }}</span>
            <span>{{ current.likeCount || 0 }}</span>
          </button>
          <button class="cm-act" :class="{ 'is-on': current.collected }" @click="onCollect(current)">
            <span>{{ current.collected ? '★' : '☆' }}</span>
            <span>{{ current.collectCount || 0 }}</span>
          </button>
        </div>

        <div class="cm-body flex-1 overflow-y-auto whitespace-pre-wrap leading-loose text-tavern-200">
          {{ current.content }}
        </div>

        <!-- 评论 -->
        <section class="space-y-3 border-t border-tavern-700/40 pt-4">
          <h3 class="text-sm font-semibold text-tavern-200">评论（{{ comments.length }}）</h3>

          <div class="flex gap-2">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="说点什么…"
            />
            <el-button type="primary" :loading="submitting" @click="submitComment">发送</el-button>
          </div>

          <p v-if="!comments.length" class="text-xs text-tavern-muted">还没有评论。</p>

          <ul v-else class="space-y-3">
            <li v-for="c in comments" :key="c.id" class="flex gap-2">
              <img
                v-if="c.avatar"
                :src="c.avatar"
                :alt="c.nickname"
                class="h-7 w-7 shrink-0 rounded-full object-cover"
              />
              <div class="min-w-0 flex-1">
                <p class="text-xs text-tavern-muted">
                  {{ c.nickname || '匿名旅人' }}
                  <span class="opacity-60">{{ formatTime(c.createdAt) }}</span>
                </p>
                <p class="mt-0.5 text-sm leading-relaxed text-tavern-100 break-words">{{ c.content }}</p>
              </div>
              <el-button
                v-if="c.userId === myId"
                text
                size="small"
                class="shrink-0"
                @click="removeComment(c)"
              >
                删除
              </el-button>
            </li>
          </ul>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  addWorkComment,
  deleteWorkComment,
  getCommunityWorks,
  getWorkComments,
  toggleWorkCollect,
  toggleWorkLike,
} from '../api/community'

const auth = useAuthStore()
const myId = computed(() => auth.user?.id ?? null)
const sort = ref('latest')
const page = ref(1)
const pageSize = 12
const total = ref(0)
const works = ref([])
const loading = ref(false)

const readerOpen = ref(false)
const current = ref(null)
const comments = ref([])
const draft = ref('')
const submitting = ref(false)

const drawerSize = computed(() => (window.innerWidth < 768 ? '100%' : '58%'))

async function load() {
  loading.value = true
  try {
    const { data } = await getCommunityWorks(sort.value, page.value, pageSize)
    works.value = data?.list || []
    total.value = data?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function open(w) {
  current.value = w
  readerOpen.value = true
  comments.value = []
  try {
    const { data } = await getWorkComments(w.id)
    comments.value = data?.list || []
  } catch {
    /* 评论加载失败不挡住正文阅读 */
  }
}

/** 点赞/收藏返回的是切换后的状态，就地回写避免整页刷新 */
function applyToggle(w, data) {
  if (data?.liked !== undefined) w.liked = data.liked
  if (data?.likeCount !== undefined) w.likeCount = data.likeCount
  if (data?.collected !== undefined) w.collected = data.collected
  if (data?.collectCount !== undefined) w.collectCount = data.collectCount
}

async function onLike(w) {
  try {
    const { data } = await toggleWorkLike(w.id)
    applyToggle(w, data)
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function onCollect(w) {
  try {
    const { data } = await toggleWorkCollect(w.id)
    applyToggle(w, data)
    ElMessage.success(data?.collected ? '已收藏' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function submitComment() {
  const text = draft.value.trim()
  if (!text) {
    ElMessage.warning('评论不能为空')
    return
  }
  submitting.value = true
  try {
    const { data } = await addWorkComment(current.value.id, text)
    draft.value = ''
    comments.value.unshift(data)
    current.value.commentCount = (current.value.commentCount || 0) + 1
  } catch (e) {
    // 频控和敏感词拦截都在后端，这里直接把原因透给用户
    ElMessage.error(e.message || '发表失败')
  } finally {
    submitting.value = false
  }
}

async function removeComment(c) {
  try {
    await deleteWorkComment(c.id)
    comments.value = comments.value.filter((x) => x.id !== c.id)
    if (current.value?.commentCount) current.value.commentCount -= 1
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

function formLabel(t) {
  return { novel: '小说', script: '剧本', outline: '大纲' }[t] || t
}

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<style scoped>
.cm-head {
  position: relative;
  overflow: hidden;
  border-radius: 1rem;
  border: 1px solid rgba(61, 42, 26, 0.5);
  background:
    radial-gradient(120% 140% at 10% 0%, rgba(201, 149, 44, 0.16), transparent 60%),
    rgba(45, 31, 20, 0.6);
  padding: 2rem 2.5rem;
}

.cm-eyebrow {
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(212, 168, 83, 0.9);
}

.cm-card {
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.cm-card:hover {
  border-color: rgba(201, 149, 44, 0.55);
  transform: translateY(-2px);
}

.cm-act {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.45rem;
  border-radius: 0.375rem;
  font-size: 12px;
  line-height: 1.4;
  color: #a99578;
  transition: color 0.15s ease, background 0.15s ease;
}

.cm-act:hover {
  color: #d4a853;
  background: rgba(201, 149, 44, 0.1);
}

.cm-act.is-on {
  color: #d4a853;
}

.cm-body {
  font-family: Georgia, 'Songti SC', serif;
  font-size: 15px;
}
</style>
