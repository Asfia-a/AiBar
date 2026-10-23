<template>
  <div class="flex flex-col">
    <button
      v-for="s in sessions"
      :key="s.id"
      type="button"
      class="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-all duration-300"
      :class="
        String(s.id) === String(currentId)
          ? 'bg-secondary text-foreground'
          : 'text-muted-foreground hover:translate-x-0.5 hover:bg-accent/60 hover:text-foreground'
      "
      @click="emit('select', s.id)"
    >
      <Avatar class="size-8 shrink-0">
        <AvatarImage :src="s.characterAvatar || s.character_avatar || ''" :alt="titleOf(s)" />
        <AvatarFallback class="text-xs">{{ titleOf(s)[0] }}</AvatarFallback>
      </Avatar>
      <span class="min-w-0 flex-1">
        <span class="block truncate text-sm">{{ titleOf(s) }}</span>
        <span class="mt-0.5 block truncate text-xs text-muted-foreground">
          {{ s.lastMessage || '尚无消息' }}
        </span>
      </span>
    </button>

    <p v-if="!sessions.length" class="px-3 py-10 text-center text-xs text-muted-foreground">
      还没有会话<br />从角色广场挑一个角色开始
    </p>
  </div>
</template>

<script setup>
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'

defineProps({
  sessions: { type: Array, default: () => [] },
  currentId: { type: [String, Number], default: null },
})
const emit = defineEmits(['select'])

function titleOf(s) {
  return s.title || s.characterName || s.character_name || '未命名会话'
}
</script>
