<template>
  <div class="space-y-4">
    <h1 class="font-display text-3xl text-tavern-200">搜索</h1>
    <el-input v-model="keyword" size="large" placeholder="搜索角色名或简介" clearable @keyup.enter="search">
      <template #append>
        <el-button @click="search">搜索</el-button>
      </template>
    </el-input>
    <div v-loading="loading" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
      <CharacterCard v-for="c in list" :key="c.id" :character="c" />
    </div>
    <el-empty v-if="!loading && searched && !list.length" description="没有找到相关角色" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { searchCharacters, getCharacters } from '../api/character'
import CharacterCard from '../components/CharacterCard.vue'

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
