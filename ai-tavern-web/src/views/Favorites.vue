<template>
  <div class="space-y-4">
    <h1 class="font-display text-3xl text-tavern-200">我的收藏</h1>
    <div v-loading="loading" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
      <CharacterCard v-for="c in list" :key="c.id" :character="c" />
    </div>
    <el-empty v-if="!loading && !list.length" description="还没有收藏" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getFavorites } from '../api/character'
import CharacterCard from '../components/CharacterCard.vue'

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
