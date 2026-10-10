<template>
  <router-link
    :to="`/character/${character.id}`"
    class="tavern-card group block overflow-hidden transition-all hover:border-tavern-500/60 hover:shadow-lg hover:shadow-tavern-900/50"
  >
    <div class="aspect-[3/4] overflow-hidden bg-tavern-900">
      <img
        v-if="character.avatar"
        :src="character.avatar"
        :alt="character.name"
        class="h-full w-full object-cover transition-transform group-hover:scale-105"
      />
      <div v-else class="flex h-full items-center justify-center text-5xl bg-tavern-800">
        {{ character.name?.[0] || '?' }}
      </div>
    </div>
    <div class="p-3">
      <h3 class="font-display text-lg font-semibold text-tavern-100 truncate">{{ character.name }}</h3>
      <p class="mt-1 text-xs text-tavern-muted line-clamp-2">{{ character.description || character.summary }}</p>
      <div class="mt-2 flex items-center justify-between text-xs text-tavern-muted">
        <span v-if="character.rating">⭐ {{ character.rating?.toFixed?.(1) ?? character.rating }}</span>
        <span v-if="character.chatCount">{{ character.chatCount }} 对话</span>
      </div>
      <div v-if="character.tags?.length" class="mt-2 flex flex-wrap gap-1">
        <span
          v-for="tag in character.tags.slice(0, 3)"
          :key="tag.id || tag.name || tag"
          class="rounded-full bg-tavern-700/50 px-2 py-0.5 text-[10px] text-tavern-300"
        >
          {{ tag.name || tag }}
        </span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
defineProps({
  character: { type: Object, required: true },
})
</script>
