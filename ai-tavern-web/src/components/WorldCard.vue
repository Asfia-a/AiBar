<template>
  <router-link
    :to="`/worlds/${world.id}`"
    class="group relative flex min-h-[220px] flex-col overflow-hidden rounded-2xl border border-border/50 bg-card transition-all duration-300 hover:-translate-y-1 hover:border-amber-500/40 hover:shadow-[0_28px_60px_-28px_rgba(0,0,0,0.55)] active:translate-y-0 active:scale-[0.99]"
  >
    <div
      class="absolute inset-0 bg-gradient-to-br opacity-90 transition-opacity duration-500 group-hover:opacity-100"
      :class="tone"
    />
    <div
      class="absolute inset-0 opacity-40"
      style="
        background:
          radial-gradient(circle at 28% 18%, rgba(255, 255, 255, 0.16), transparent 42%),
          radial-gradient(circle at 80% 70%, rgba(0, 0, 0, 0.35), transparent 50%);
      "
    />
    <img
      v-if="world.cover"
      :src="world.cover"
      :alt="world.name"
      class="absolute inset-0 size-full object-cover opacity-35 mix-blend-luminosity transition-transform duration-700 group-hover:scale-[1.04]"
      loading="lazy"
    />

    <div class="relative z-10 mt-auto flex flex-1 flex-col justify-end p-4 pt-16">
      <p
        class="mb-2 inline-flex items-center gap-1 text-[10px] font-medium uppercase tracking-[0.28em] text-amber-200/90 transition group-hover:tracking-[0.36em]"
      >
        Enter
        <span class="transition-transform group-hover:translate-x-0.5">→</span>
      </p>
      <h3 class="font-serif text-lg font-semibold tracking-tight text-white">
        {{ world.name }}
      </h3>
      <p class="mt-1.5 line-clamp-2 text-xs leading-relaxed text-white/70">
        {{ world.description || '一封尚未拆开的世界。' }}
      </p>
      <div class="mt-3 flex items-center justify-between gap-2 text-[11px] text-white/55">
        <span class="truncate">{{ world.creatorName || '匿名' }}</span>
        <span class="shrink-0 tabular-nums">
          {{ scale }}
          <template v-if="world.viewCount"> · {{ world.viewCount }} 进入</template>
        </span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import { gateTone, worldScaleLabel } from '../utils/worldExplore'

const props = defineProps({
  world: { type: Object, required: true },
  index: { type: Number, default: 0 },
})

const tone = computed(() => gateTone(props.index || props.world?.id || 0))
const scale = computed(() => worldScaleLabel(props.world?.nodeCount))
</script>
