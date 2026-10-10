<template>
  <div v-loading="loading" class="max-w-4xl mx-auto space-y-6">
    <template v-if="character">
      <section class="tavern-card p-6 flex flex-col sm:flex-row gap-6">
        <el-avatar :size="120" :src="character.avatar" shape="square" class="!rounded-xl shrink-0">
          {{ character.name?.[0] }}
        </el-avatar>
        <div class="flex-1 space-y-3">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h1 class="font-display text-3xl text-tavern-200">{{ character.name }}</h1>
              <p class="text-sm text-tavern-muted mt-1">
                创作者 {{ character.creatorName || '匿名' }} · {{ character.categoryName || '未分类' }}
                <el-tag v-if="isOwner" size="small" type="warning" class="ml-2" effect="plain">我的角色</el-tag>
              </p>
            </div>
            <div class="flex gap-2">
              <el-button v-if="isOwner" type="primary" plain @click="goEdit">编辑调整</el-button>
              <el-button type="warning" @click="startChat">开始对话</el-button>
              <el-button @click="toggleFav">{{ character.favorited ? '已收藏' : '收藏' }}</el-button>
              <el-button v-if="!isOwner" @click="onReplica">复刻</el-button>
            </div>
          </div>
          <p class="text-tavern-muted leading-relaxed">{{ character.description }}</p>
          <div class="flex flex-wrap gap-2">
            <el-tag v-for="t in character.tags || []" :key="t.id" :color="t.color" effect="dark" round>
              {{ t.name }}
            </el-tag>
          </div>
          <div class="flex flex-wrap gap-4 text-sm text-tavern-muted">
            <span>对话 {{ character.chatCount || 0 }}</span>
            <span>收藏 {{ character.favoriteCount || 0 }}</span>
            <span>评分 {{ Number(character.rating || 0).toFixed(1) }} ({{ character.ratingCount || 0 }})</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm text-tavern-muted">我的评分</span>
            <el-rate v-model="myScore" @change="onRate" />
          </div>
        </div>
      </section>

      <section class="tavern-card p-6 space-y-3">
        <h2 class="font-display text-xl text-tavern-200">人设与背景</h2>
        <p class="whitespace-pre-wrap text-tavern-muted">{{ character.personality }}</p>
        <p class="whitespace-pre-wrap text-tavern-muted">{{ character.backgroundStory }}</p>
        <p v-if="character.greeting" class="text-tavern-300 italic">开场白：{{ character.greeting }}</p>
      </section>

      <section class="tavern-card p-6 space-y-4">
        <h2 class="font-display text-xl text-tavern-200">评论</h2>
        <div v-if="authStore.isLoggedIn" class="flex gap-2">
          <el-input v-model="commentText" type="textarea" :rows="2" placeholder="写下你的感受..." />
          <el-button type="primary" @click="submitComment">发送</el-button>
        </div>
        <div v-for="c in comments" :key="c.id" class="border-b border-tavern-700/40 pb-3">
          <div class="flex items-center gap-2 mb-1">
            <el-avatar :size="28" :src="c.avatar">{{ c.nickname?.[0] }}</el-avatar>
            <span class="text-sm text-tavern-200">{{ c.nickname }}</span>
          </div>
          <p class="text-tavern-muted text-sm">{{ c.content }}</p>
        </div>
        <el-empty v-if="!comments.length" description="暂无评论" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getCharacterDetail, favoriteCharacter, rateCharacter,
  getComments, addComment, cloneCharacter,
} from '../api/character'
import { createSession, getSessions } from '../api/chat'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const character = ref(null)
const myScore = ref(0)
const comments = ref([])
const commentText = ref('')

const isOwner = computed(() => {
  if (!character.value || !authStore.user) return false
  if (character.value.ownedByMe) return true
  return Number(character.value.creatorId) === Number(authStore.user.id)
})

async function load() {
  loading.value = true
  try {
    character.value = await getCharacterDetail(route.params.id)
    myScore.value = character.value.myScore || 0
    const page = await getComments(route.params.id, { page: 1, size: 50 })
    comments.value = page.list || page || []
  } finally {
    loading.value = false
  }
}

function goEdit() {
  router.push(`/edit/${route.params.id}`)
}

async function startChat() {
  if (!authStore.isLoggedIn) return router.push('/login')
  const characterId = Number(route.params.id)
  const sessions = (await getSessions()) || []
  const existing = sessions.find((s) => Number(s.characterId ?? s.character_id) === characterId)
  if (existing) {
    router.push(`/chat/${existing.id}`)
    return
  }
  const session = await createSession({ characterId })
  router.push(`/chat/${session.id}`)
}

async function toggleFav() {
  if (!authStore.isLoggedIn) return router.push('/login')
  const res = await favoriteCharacter(route.params.id)
  character.value.favorited = res.favorited
  ElMessage.success(res.favorited ? '已收藏' : '已取消收藏')
}

async function onRate(score) {
  if (!authStore.isLoggedIn) return router.push('/login')
  const res = await rateCharacter(route.params.id, { score })
  character.value.rating = res.rating
  character.value.ratingCount = res.ratingCount
  ElMessage.success('评分成功')
}

async function onReplica() {
  if (!authStore.isLoggedIn) return router.push('/login')
  const copy = await cloneCharacter(route.params.id)
  ElMessage.success('已复刻为草稿')
  router.push(`/edit/${copy.id}`)
}

async function submitComment() {
  if (!commentText.value.trim()) return
  await addComment(route.params.id, { content: commentText.value })
  commentText.value = ''
  await load()
}

onMounted(load)
</script>
