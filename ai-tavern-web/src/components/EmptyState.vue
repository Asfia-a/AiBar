<template>
  <div
    class="flex flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-border/70 px-6 py-12 text-center"
  >
    <p class="text-sm text-muted-foreground">{{ text }}</p>
    <p v-if="hint" class="text-xs text-muted-foreground/70">{{ hint }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无内容' },
  hint: { type: String, default: '' },
})
</script>
