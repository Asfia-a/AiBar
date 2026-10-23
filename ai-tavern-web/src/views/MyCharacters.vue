<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-semibold tracking-tight">我的角色</h1>
        <p class="text-sm text-muted-foreground">
          你创建的角色 · 随时可以调整人设，已有对话不会被破坏
        </p>
      </div>
      <Button @click="router.push('/create')">
        <Plus class="size-4" />
        创建角色
      </Button>
    </header>

    <div class="flex flex-wrap items-center gap-1.5">
      <Button
        v-for="s in filterOptions"
        :key="s.label"
        :variant="statusFilter === s.value ? 'secondary' : 'ghost'"
        size="sm"
        class="rounded-full"
        @click="statusFilter = s.value"
      >
        {{ s.label }}
        <span class="text-xs tabular-nums opacity-60">{{ countOf(s.value) }}</span>
      </Button>
    </div>

    <div v-if="loading" class="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4">
      <div v-for="i in 8" :key="i" class="space-y-2">
        <Skeleton class="aspect-[3/4] rounded-xl" />
        <Skeleton class="h-4 w-2/3" />
      </div>
    </div>

    <template v-else-if="list.length">
      <section v-for="group in visibleSections" :key="group.label" class="space-y-3">
        <div class="flex items-baseline gap-2">
          <h2 class="text-sm font-medium">{{ group.label }}</h2>
          <span class="text-xs tabular-nums text-muted-foreground">{{ group.items.length }}</span>
        </div>

        <div class="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-4">
          <div v-for="c in group.items" :key="c.id" class="space-y-2">
            <CharacterCard :character="c" />
            <div class="flex items-center justify-between gap-1 px-0.5">
              <Badge
                :variant="Number(c.status) === 2 ? 'secondary' : 'outline'"
                class="font-normal"
                :class="Number(c.status) === 2 ? '' : 'text-muted-foreground'"
              >
                {{ statusLabel(c.status) }}
              </Badge>
              <div class="flex items-center gap-0.5">
                <Button variant="ghost" size="xs" @click="router.push(`/edit/${c.id}`)">
                  编辑
                </Button>
                <Button
                  variant="ghost"
                  size="xs"
                  class="text-muted-foreground hover:text-destructive"
                  :disabled="deletingId === c.id"
                  @click="askDelete(c)"
                >
                  删除
                </Button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <EmptyState
        v-if="!visibleSections.length"
        text="该状态下暂无角色"
        hint="切换筛选看看其他状态"
      />
    </template>

    <EmptyState
      v-else
      text="还没有创建角色"
      hint="写一句人设，剩下的交给创作工坊"
    >
      <Button size="sm" class="mt-3" @click="router.push('/create')">创建角色</Button>
    </EmptyState>

    <!-- 删除确认 -->
    <Dialog v-model:open="confirmOpen">
      <DialogContent class="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>删除角色</DialogTitle>
          <DialogDescription>
            确定删除「{{ pendingDelete?.name }}」？角色将从广场与列表移除；已有对话仍可继续（沿用人设快照）。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="confirmOpen = false">取消</Button>
          <Button variant="destructive" :disabled="deletingId !== null" @click="confirmDelete">
            删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Plus } from 'lucide-vue-next'
import { getCharacters, deleteCharacter } from '../api/character'
import { useAuthStore } from '../stores/auth'
import CharacterCard from '../components/CharacterCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'

const authStore = useAuthStore()
const router = useRouter()
const list = ref([])
const loading = ref(false)
const statusFilter = ref(null)
const deletingId = ref(null)
const confirmOpen = ref(false)
const pendingDelete = ref(null)

const statusDefs = [
  { value: 2, label: '已上架' },
  { value: 1, label: '审核中' },
  { value: 0, label: '草稿' },
  { value: 3, label: '已下架' },
  { value: 4, label: '已驳回' },
]

const filterOptions = [{ value: null, label: '全部' }, ...statusDefs]

function statusLabel(s) {
  return statusDefs.find((x) => x.value === Number(s))?.label || '未知'
}

const filtered = computed(() => {
  if (statusFilter.value === null || statusFilter.value === undefined) return list.value
  return list.value.filter((c) => Number(c.status) === Number(statusFilter.value))
})

const groupedSections = computed(() =>
  statusDefs
    .map((s) => ({
      ...s,
      items: list.value.filter((c) => Number(c.status) === s.value),
    }))
    .filter((g) => g.items.length > 0),
)

const visibleSections = computed(() => {
  if (statusFilter.value === null) return groupedSections.value
  if (!filtered.value.length) return []
  return [{ label: statusLabel(statusFilter.value), items: filtered.value }]
})

function countOf(value) {
  if (value === null) return list.value.length
  return list.value.filter((c) => Number(c.status) === Number(value)).length
}

function askDelete(c) {
  pendingDelete.value = c
  confirmOpen.value = true
}

async function confirmDelete() {
  const c = pendingDelete.value
  confirmOpen.value = false
  if (c) await onDelete(c)
}

async function onDelete(c) {
  deletingId.value = c.id
  try {
    await deleteCharacter(c.id)
    toast.success('已删除')
    list.value = list.value.filter((x) => x.id !== c.id)
  } catch (e) {
    toast.error(e.message || '删除失败')
  } finally {
    deletingId.value = null
    pendingDelete.value = null
  }
}

async function load() {
  if (!authStore.user?.id) {
    await authStore.fetchUser()
  }
  if (!authStore.user?.id) {
    router.push('/login')
    return
  }
  loading.value = true
  try {
    const page = await getCharacters({ creatorId: authStore.user.id, page: 1, size: 100 })
    list.value = page?.list || page?.records || page || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => authStore.user?.id, (id) => { if (id) load() })
</script>
