<template>
  <div class="space-y-8">
    <!-- 顶部：搜索 -->
    <section
      class="relative flex flex-col items-center overflow-hidden py-14 text-center sm:py-20"
      @pointermove="onHeroMove"
      @pointerleave="onHeroLeave"
    >
      <!-- 背景光斑：缓慢漂浮 + 跟随鼠标做轻微视差，给静止的英雄区一点呼吸感 -->
      <div
        class="pointer-events-none absolute inset-0 transition-transform duration-300 ease-out"
        :style="{ transform: `translate3d(${parallax.x * 16}px, ${parallax.y * 12}px, 0)` }"
      >
        <div class="orb -left-20 top-0 size-72 bg-[radial-gradient(circle_at_center,rgba(228,196,137,0.22),transparent_65%)]" />
        <div class="orb -right-10 top-8 size-64 bg-[radial-gradient(circle_at_center,rgba(120,110,255,0.18),transparent_65%)] [animation-delay:-5s]" />
      </div>

      <div v-reveal class="relative z-10 flex w-full flex-col items-center">
        <p class="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">AI Character Tavern</p>
        <h1 class="mt-3 text-3xl font-semibold tracking-tight sm:text-5xl">
          挑一个角色，
          <span class="bg-gradient-to-r from-[#f6e7cc] via-[#e4c489] to-[#c9a86a] bg-clip-text text-transparent">
            开始今晚的故事
          </span>
        </h1>
        <p class="mt-4 max-w-xl text-sm text-muted-foreground">
          流式对话、角色卡创作、叙事改写与社区发布——从一句开场白开始。
        </p>

        <form class="mt-7 flex w-full max-w-md items-center gap-2" @submit.prevent="goSearch">
          <div class="group relative flex-1">
            <Search class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-foreground" />
            <Input
              v-model="keyword"
              placeholder="搜索角色名或简介"
              class="h-11 rounded-full border-border/70 bg-card/60 pl-9 backdrop-blur transition-shadow focus-visible:shadow-[0_0_0_4px_rgba(228,196,137,0.12)]"
            />
          </div>
          <Button type="submit" class="h-11 rounded-full px-6 transition-transform active:scale-95">搜索</Button>
        </form>

        <div v-if="tags.length" class="mt-5 flex flex-wrap justify-center gap-1.5">
          <button
            v-for="tag in tags.slice(0, 8)"
            :key="tag.id || tag.name || tag"
            type="button"
            class="rounded-full border border-border/70 px-2.5 py-1 text-xs text-muted-foreground transition-all duration-300 hover:-translate-y-0.5 hover:border-ring/40 hover:text-foreground"
            @click="openTag(tag)"
          >
            #{{ tag.name ?? tag }}
          </button>
        </div>
      </div>
    </section>

    <!-- 热门角色 -->
    <section v-reveal class="space-y-4">
      <div class="flex items-end justify-between">
        <div>
          <h2 class="text-lg font-medium">热门角色</h2>
          <p class="text-xs text-muted-foreground">按对话量与评分排出的本周热门</p>
        </div>
        <router-link to="/search" class="text-xs text-muted-foreground hover:text-foreground">
          查看全部
        </router-link>
      </div>

      <div v-if="loadingHot" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        <Skeleton v-for="i in 5" :key="i" class="aspect-[3/4] rounded-xl" />
      </div>
      <div
        v-else-if="hotList.length"
        class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5"
      >
        <CharacterCard v-for="c in hotList" :key="c.id" :character="c" />
      </div>
      <EmptyState v-else text="暂无热门角色" />
    </section>

    <!-- 主体：分类 + 列表 / 侧栏：排行 + 标签 -->
    <div class="grid grid-cols-1 gap-8 xl:grid-cols-[1fr_280px]">
      <section v-reveal="80" class="space-y-4">
        <div class="flex flex-wrap gap-1.5">
          <Button
            v-for="cat in categories"
            :key="cat.id || cat.name"
            :variant="activeCategory === (cat.id ?? cat) ? 'secondary' : 'ghost'"
            size="sm"
            class="rounded-full"
            @click="selectCategory(cat)"
          >
            {{ cat.name ?? cat }}
          </Button>
        </div>

        <div v-if="loadingList && !characterList.length" class="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <Skeleton v-for="i in 6" :key="i" class="aspect-[3/4] rounded-xl" />
        </div>
        <div v-else-if="characterList.length" class="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <CharacterCard v-for="c in characterList" :key="c.id" :character="c" />
        </div>
        <EmptyState v-else text="这个分类下还没有角色" />

        <div v-if="total > characterList.length" class="pt-2 text-center">
          <Button variant="outline" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </Button>
        </div>
      </section>

      <aside v-reveal="160" class="space-y-8">
        <section class="space-y-3">
          <h2 class="text-sm font-medium">排行榜</h2>
          <div v-if="loadingRank" class="space-y-2">
            <Skeleton v-for="i in 5" :key="i" class="h-14 rounded-lg" />
          </div>
          <div v-else-if="rankingList.length" class="space-y-1">
            <router-link
              v-for="(c, idx) in rankingList"
              :key="c.id"
              :to="`/character/${c.id}`"
              class="flex items-center gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-accent/60"
            >
              <span
                class="w-4 text-center text-xs font-medium tabular-nums"
                :class="idx < 3 ? 'text-foreground' : 'text-muted-foreground'"
              >
                {{ idx + 1 }}
              </span>
              <Avatar class="size-8 shrink-0">
                <AvatarImage :src="c.avatar" :alt="c.name" />
                <AvatarFallback class="text-xs">{{ c.name?.[0] }}</AvatarFallback>
              </Avatar>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm">{{ c.name }}</span>
                <span class="block text-[11px] text-muted-foreground">{{ c.chatCount ?? 0 }} 次对话</span>
              </span>
            </router-link>
          </div>
          <p v-else class="text-xs text-muted-foreground">暂无排行数据</p>
        </section>

        <section class="space-y-3">
          <h2 class="text-sm font-medium">标签探索</h2>
          <div v-if="tags.length" class="flex flex-wrap gap-1.5">
            <router-link
              v-for="tag in tags"
              :key="tag.id || tag.name || tag"
              :to="{ name: 'Search', query: { tag: tag.name ?? tag } }"
              class="rounded-full border border-border/70 px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:border-border hover:text-foreground"
            >
              #{{ tag.name ?? tag }}
            </router-link>
          </div>
          <p v-else class="text-xs text-muted-foreground">暂无标签</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import CharacterCard from '../components/CharacterCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { getHotCharacters, getCharacters, getRanking, getCategories, getTags } from '../api/character'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'

const router = useRouter()
const keyword = ref('')

/** 鼠标在英雄区移动时，两层光斑做相反方向的视差位移 */
const parallax = reactive({ x: 0, y: 0 })

function onHeroMove(ev) {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  const rect = ev.currentTarget.getBoundingClientRect()
  parallax.x = ((ev.clientX - rect.left) / rect.width - 0.5) * 2
  parallax.y = ((ev.clientY - rect.top) / rect.height - 0.5) * 2
}

function onHeroLeave() {
  parallax.x = 0
  parallax.y = 0
}
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

function openTag(tag) {
  router.push({ name: 'Search', query: { tag: tag.name ?? tag } })
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
  } catch {
    hotList.value = []
  } finally {
    loadingHot.value = false
  }
}

async function fetchCharacters() {
  loadingList.value = true
  try {
    const res = await getCharacters({
      page: page.value,
      size: 20,
      categoryId: activeCategory.value || undefined,
    })
    const list = res?.list ?? res ?? []
    characterList.value = page.value === 1 ? list : [...characterList.value, ...list]
    total.value = res?.total ?? list.length
  } catch {
    if (page.value === 1) characterList.value = []
  } finally {
    loadingList.value = false
  }
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
  } catch {
    rankingList.value = []
  } finally {
    loadingRank.value = false
  }
}

async function fetchMeta() {
  try {
    const cats = await getCategories()
    if (Array.isArray(cats) && cats.length) {
      categories.value = [{ id: '', name: '全部' }, ...cats]
    }
  } catch {
    /* 分类失败不影响标签 */
  }
  try {
    const tagList = await getTags()
    tags.value = tagList?.list ?? tagList ?? []
  } catch {
    /* 标签失败不影响主列表 */
  }
}

onMounted(() => {
  fetchHot()
  fetchCharacters()
  fetchRanking()
  fetchMeta()
})
</script>
