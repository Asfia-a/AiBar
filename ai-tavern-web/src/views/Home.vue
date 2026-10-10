<template>
  <div class="space-y-8">
    <section class="text-center py-8">
      <h1 class="font-display text-4xl sm:text-5xl font-bold text-tavern-200 mb-3">
        欢迎来到 AI角色酒馆
      </h1>
      <p class="text-tavern-muted max-w-xl mx-auto">
        推开门，暖光与低语迎接你。挑选一位角色，开启今夜的故事。
      </p>
      <div class="mt-6 max-w-md mx-auto">
        <el-input
          v-model="keyword"
          placeholder="搜索角色..."
          size="large"
          clearable
          @keyup.enter="goSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button @click="goSearch">搜索</el-button>
          </template>
        </el-input>
      </div>
    </section>

    <section>
      <div class="flex items-center justify-between mb-4">
        <h2 class="font-display text-2xl text-tavern-200">🔥 热门角色</h2>
      </div>
      <div v-loading="loadingHot" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
        <CharacterCard v-for="c in hotList" :key="c.id" :character="c" />
      </div>
      <el-empty v-if="!loadingHot && !hotList.length" description="暂无热门角色" />
    </section>

    <section>
      <div class="flex flex-wrap gap-2 mb-4">
        <button
          v-for="cat in categories"
          :key="cat.id || cat"
          class="rounded-full px-4 py-1.5 text-sm transition-colors"
          :class="activeCategory === (cat.id ?? cat) ? 'bg-tavern-400 text-tavern-950' : 'bg-tavern-800 text-tavern-muted hover:text-tavern-200'"
          @click="selectCategory(cat)"
        >
          {{ cat.name ?? cat }}
        </button>
      </div>
      <div v-loading="loadingList" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
        <CharacterCard v-for="c in characterList" :key="c.id" :character="c" />
      </div>
      <div v-if="total > characterList.length" class="mt-6 text-center">
        <el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </section>

    <section>
      <h2 class="font-display text-2xl text-tavern-200 mb-4">🏷️ 标签探索</h2>
      <div class="flex flex-wrap gap-2">
        <router-link
          v-for="tag in tags"
          :key="tag.id || tag"
          :to="{ name: 'Search', query: { tag: tag.name ?? tag } }"
          class="rounded-full border border-tavern-600 px-3 py-1 text-sm text-tavern-300 hover:border-tavern-400 hover:bg-tavern-800/60 transition-colors"
        >
          #{{ tag.name ?? tag }}
        </router-link>
      </div>
    </section>

    <section>
      <h2 class="font-display text-2xl text-tavern-200 mb-4">📊 排行榜</h2>
      <div v-loading="loadingRank" class="space-y-2">
        <router-link
          v-for="(c, idx) in rankingList"
          :key="c.id"
          :to="`/character/${c.id}`"
          class="tavern-card flex items-center gap-4 p-3 hover:border-tavern-500/60 transition-colors"
        >
          <span class="font-display text-2xl w-8 text-center" :class="idx < 3 ? 'text-tavern-400' : 'text-tavern-muted'">
            {{ idx + 1 }}
          </span>
          <el-avatar :size="48" :src="c.avatar">{{ c.name?.[0] }}</el-avatar>
          <div class="flex-1 min-w-0">
            <div class="font-medium text-tavern-100 truncate">{{ c.name }}</div>
            <div class="text-xs text-tavern-muted">{{ c.chatCount ?? 0 }} 次对话</div>
          </div>
          <span class="text-tavern-400">⭐ {{ c.rating?.toFixed?.(1) ?? '-' }}</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import CharacterCard from '../components/CharacterCard.vue'
import { getHotCharacters, getCharacters, getRanking, getCategories, getTags } from '../api/character'

const router = useRouter()
const keyword = ref('')
const hotList = ref([])
const characterList = ref([])
const rankingList = ref([])
const categories = ref([{ id: '', name: '全部' }])
const tags = ref([])
const activeCategory = ref('')
const page = ref(1)
const total = ref(0)
const loadingHot = ref(false)
const loadingList = ref(false)
const loadingRank = ref(false)
const loadingMore = ref(false)

function goSearch() {
  router.push({ name: 'Search', query: { q: keyword.value } })
}

function selectCategory(cat) {
  activeCategory.value = cat.id ?? cat
  page.value = 1
  fetchCharacters()
}

async function fetchHot() {
  loadingHot.value = true
  try {
    const res = await getHotCharacters({ limit: 10 })
    hotList.value = res?.list ?? res ?? []
  } catch { hotList.value = [] }
  finally { loadingHot.value = false }
}

async function fetchCharacters() {
  loadingList.value = true
  try {
    const res = await getCharacters({ page: page.value, size: 20, categoryId: activeCategory.value || undefined })
    const list = res?.list ?? res ?? []
    characterList.value = page.value === 1 ? list : [...characterList.value, ...list]
    total.value = res?.total ?? list.length
  } catch { if (page.value === 1) characterList.value = [] }
  finally { loadingList.value = false }
}

async function loadMore() {
  loadingMore.value = true
  page.value++
  await fetchCharacters()
  loadingMore.value = false
}

async function fetchRanking() {
  loadingRank.value = true
  try {
    const res = await getRanking({ limit: 10 })
    rankingList.value = res?.list ?? res ?? []
  } catch { rankingList.value = [] }
  finally { loadingRank.value = false }
}

async function fetchMeta() {
  try {
    const cats = await getCategories()
    if (Array.isArray(cats) && cats.length) {
      categories.value = [{ id: '', name: '全部' }, ...cats]
    }
  } catch { /* 分类失败不影响标签 */ }
  try {
    const tagList = await getTags()
    tags.value = tagList?.list ?? tagList ?? []
  } catch { /* ignore */ }
}

onMounted(() => {
  fetchHot()
  fetchCharacters()
  fetchRanking()
  fetchMeta()
})
</script>
