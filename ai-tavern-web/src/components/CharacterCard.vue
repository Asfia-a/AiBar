<template>
  <router-link
    :to="`/character/${character.id}`"
    class="group block overflow-hidden rounded-xl border border-border/60 bg-card transition-all hover:border-border hover:shadow-lg hover:shadow-black/20"
  >
    <div class="relative aspect-[3/4] overflow-hidden bg-muted">
      <img
        v-if="character.avatar"
        :src="character.avatar"
        :alt="character.name"
        class="size-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
        loading="lazy"
      />
      <div v-else class="grid size-full place-items-center text-4xl text-muted-foreground">
        {{ character.name?.[0] || '?' }}
      </div>

      <!-- 分类/标签压在图上角，避免占掉卡片下半部分的阅读空间 -->
      <div v-if="character.tags?.length" class="absolute left-2 top-2 flex max-w-[85%] flex-wrap gap-1">
        <span
          v-for="tag in character.tags.slice(0, 2)"
          :key="tag.id || tag.name || tag"
          class="rounded-full bg-black/45 px-1.5 py-0.5 text-[10px] text-white/85 backdrop-blur-sm"
        >
          {{ tag.name || tag }}
        </span>
      </div>

      <div
        class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/45 to-transparent p-3 pt-8"
      >
        <h3 class="truncate text-sm font-medium text-white">{{ character.name }}</h3>
        <p class="mt-0.5 line-clamp-2 text-[11px] leading-snug text-white/70">
          {{ character.description || character.summary }}
        </p>
      </div>
    </div>

    <div class="flex items-center justify-between gap-2 px-3 py-2 text-[11px] text-muted-foreground">
      <span v-if="character.rating" class="flex items-center gap-1">
        <Star class="size-3" />
        {{ character.rating?.toFixed?.(1) ?? character.rating }}
      </span>
      <span v-else />
      <span v-if="character.chatCount">{{ character.chatCount }} 次对话</span>
    </div>
  </router-link>
</template>

<script setup>
import { Star } from 'lucide-vue-next'

defineProps({
  character: { type: Object, required: true },
})
</script>
