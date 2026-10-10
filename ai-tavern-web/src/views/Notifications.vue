<template>
  <div class="max-w-2xl mx-auto space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="font-display text-3xl text-tavern-200">通知中心</h1>
      <el-button text @click="readAll">全部已读</el-button>
    </div>
    <div v-loading="loading" class="space-y-2">
      <div
        v-for="n in list"
        :key="n.id"
        class="tavern-card p-4 cursor-pointer"
        :class="n.isRead ? 'opacity-60' : ''"
        @click="readOne(n)"
      >
        <div class="flex justify-between gap-2">
          <h3 class="text-tavern-200">{{ n.title }}</h3>
          <span class="text-xs text-tavern-muted">{{ n.createdAt }}</span>
        </div>
        <p class="text-sm text-tavern-muted mt-1">{{ n.content }}</p>
      </div>
      <el-empty v-if="!loading && !list.length" description="暂无通知" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getNotifications, markRead, markAllRead } from '../api/notification'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await getNotifications({ page: 1, size: 50 })
    list.value = page.list || []
  } finally {
    loading.value = false
  }
}

async function readOne(n) {
  if (!n.isRead) {
    await markRead(n.id)
    n.isRead = 1
  }
}

async function readAll() {
  await markAllRead()
  await load()
}

onMounted(load)
</script>
