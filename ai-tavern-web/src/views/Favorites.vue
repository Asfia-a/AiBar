<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-semibold tracking-tight">我的收藏</h1>
        <p class="text-sm text-muted-foreground">收藏过的角色都留在这里，随时可以回到那段对话</p>
      </div>
      <span v-if="list.length" class="text-xs tabular-nums text-muted-foreground">
        共 {{ list.length }} 个
      </span>
    </header>

    <div
      v-if="loading"
      class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5"
    >
      <Skeleton v-for="i in 10" :key="i" class="aspect-[3/4] rounded-xl" />
    </div>

    <div
      v-else-if="list.length"
      class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5"
    >
      <CharacterCard v-for="c in list" :key="c.id" :character="c" />
    </div>

    <EmptyState
      v-else
      text="还没有收藏"
      hint="在角色详情页点一下收藏，角色就会出现在这里"
    >
      <Button size="sm" class="mt-3" @click="router.push('/')">去逛逛</Button>
    </EmptyState>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFavorites } from '../api/character'
import CharacterCard from '../components/CharacterCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

const router = useRouter()
const list = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    list.value = (await getFavorites()) || []
  } finally {
    loading.value = false
  }
})
</script>
