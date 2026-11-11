<template>
  <div class="space-y-10">
    <section class="flex flex-col items-center py-10 text-center sm:py-14">
      <p class="text-[11px] uppercase tracking-[0.32em] text-muted-foreground">Enter a World</p>
      <h1 class="mt-3 font-serif text-3xl font-semibold tracking-tight sm:text-4xl">世界广场</h1>
      <p class="mt-3 max-w-xl text-sm text-muted-foreground">
        选择要进入的世界。每扇门后是一场可探索的作品，不是一篇待读文档。
      </p>
      <form class="mt-6 flex w-full max-w-md items-center gap-2" @submit.prevent="search">
        <div class="relative flex-1">
          <Search class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="keyword" placeholder="搜索世界名或简介" class="h-11 rounded-full pl-9" />
        </div>
        <Button type="submit" class="h-11 rounded-full px-5">搜索</Button>
      </form>
      <div class="mt-4 flex flex-wrap justify-center gap-2">
        <Button variant="outline" size="sm" @click="router.push('/world-tree')">
          <TreePine class="size-4" />
          去创作世界
        </Button>
        <Button
          v-if="auth.isLoggedIn"
          variant="secondary"
          size="sm"
          @click="router.push('/my-worlds')"
        >
          我的世界树
        </Button>
      </div>
    </section>

    <section class="space-y-4">
      <div>
        <h2 class="text-lg font-medium">热门入口</h2>
        <p class="text-xs text-muted-foreground">按进入次数排序</p>
      </div>
      <div v-if="loadingHot" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton v-for="i in 3" :key="i" class="min-h-[220px] rounded-2xl" />
      </div>
      <div v-else-if="hotList.length" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <WorldCard v-for="(w, i) in hotList" :key="w.id" :world="w" :index="i" />
      </div>
      <EmptyState v-else text="暂无热门世界" hint="发布第一棵世界树，它就会出现在这里" />
    </section>

    <section class="space-y-4">
      <div class="flex items-end justify-between gap-3">
        <div>
          <h2 class="text-lg font-medium">全部入口</h2>
          <p class="text-xs text-muted-foreground">已开放探索的世界</p>
        </div>
        <span v-if="total" class="text-xs tabular-nums text-muted-foreground">共 {{ total }}</span>
      </div>
      <div v-if="loadingList && !list.length" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton v-for="i in 6" :key="i" class="min-h-[220px] rounded-2xl" />
      </div>
      <div v-else-if="list.length" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <WorldCard v-for="(w, i) in list" :key="w.id" :world="w" :index="i + 3" />
      </div>
      <EmptyState v-else text="还没有开放的世界" hint="在世界树页保存并发布即可出现在广场" />

      <div v-if="hasMore" class="flex justify-center pt-2">
        <Button variant="outline" :disabled="loadingList" @click="loadMore">
          {{ loadingList ? '加载中…' : '加载更多' }}
        </Button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, TreePine } from 'lucide-vue-next'
import { getHotWorlds, getWorlds } from '../api/worldTree'
import { useAuthStore } from '../stores/auth'
import WorldCard from '../components/WorldCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'

const router = useRouter()
const auth = useAuthStore()

const keyword = ref('')
const hotList = ref([])
const list = ref([])
const page = ref(1)
const size = 12
const total = ref(0)
const loadingHot = ref(false)
const loadingList = ref(false)

const hasMore = computed(() => list.value.length < total.value)

async function loadHot() {
  loadingHot.value = true
  try {
    const res = await getHotWorlds({ limit: 6 })
    hotList.value = Array.isArray(res) ? res : res?.list ?? []
  } catch {
    hotList.value = []
  } finally {
    loadingHot.value = false
  }
}

async function loadList({ reset = false } = {}) {
  if (reset) {
    page.value = 1
    list.value = []
  }
  loadingList.value = true
  try {
    const res = await getWorlds({
      page: page.value,
      size,
      keyword: keyword.value.trim() || undefined,
    })
    const rows = res?.list ?? []
    total.value = res?.total ?? rows.length
    list.value = reset ? rows : [...list.value, ...rows]
  } catch {
    if (reset) list.value = []
  } finally {
    loadingList.value = false
  }
}

function search() {
  loadList({ reset: true })
}

function loadMore() {
  if (!hasMore.value || loadingList.value) return
  page.value += 1
  loadList()
}

onMounted(() => {
  loadHot()
  loadList({ reset: true })
})
</script>
