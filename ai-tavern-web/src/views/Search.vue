<template>
  <div class="space-y-6">
    <header class="space-y-2">
      <h1 class="text-2xl font-semibold tracking-tight">
        {{ route.query.tag ? `#${route.query.tag}` : '搜索' }}
      </h1>
      <p class="text-sm text-muted-foreground">
        {{ route.query.tag ? '按标签筛选角色' : '输入角色名或简介里的关键词' }}
      </p>
    </header>

    <form class="flex max-w-xl items-center gap-2" @submit.prevent="search">
      <div class="relative flex-1">
        <Search class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input v-model="keyword" placeholder="搜索角色" class="h-10 rounded-full pl-9" />
      </div>
      <Button type="submit" class="h-10 rounded-full px-5" :disabled="loading">
        {{ loading ? '搜索中…' : '搜索' }}
      </Button>
    </form>

    <div v-if="loading" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      <Skeleton v-for="i in 8" :key="i" class="aspect-[3/4] rounded-xl" />
    </div>

    <div v-else-if="list.length" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      <CharacterCard v-for="c in list" :key="c.id" :character="c" />
    </div>

    <EmptyState
      v-else-if="searched"
      text="没有找到相关角色"
      hint="换个关键词，或去角色广场看看热门"
    />

    <EmptyState v-else text="输入关键词开始搜索" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from 'lucide-vue-next'
import CharacterCard from '../components/CharacterCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { searchCharacters, getCharacters } from '../api/character'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'

const route = useRoute()
const keyword = ref(route.query.q || '')
const list = ref([])
const loading = ref(false)
const searched = ref(false)

async function search() {
  loading.value = true
  searched.value = true
  try {
    if (route.query.tag) {
      const page = await getCharacters({ keyword: route.query.tag, page: 1, size: 40 })
      list.value = page.list || []
    } else if (keyword.value.trim()) {
      const page = await searchCharacters({ keyword: keyword.value.trim(), page: 1, size: 40 })
      list.value = page.list || []
    } else {
      list.value = []
    }
  } finally {
    loading.value = false
  }
}

onMounted(search)
</script>
