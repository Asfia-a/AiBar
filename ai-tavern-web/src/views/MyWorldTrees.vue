<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-2xl font-semibold tracking-tight">我的世界树</h1>
        <p class="text-sm text-muted-foreground">草稿与已发布的设定集，编辑后可上架到世界广场</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <Button variant="outline" @click="router.push('/worlds')">世界广场</Button>
        <Button @click="router.push('/world-tree')">
          <Plus class="size-4" />
          新建 / 编辑
        </Button>
      </div>
    </header>

    <div class="flex flex-wrap gap-1.5">
      <Button
        v-for="s in filters"
        :key="s.label"
        :variant="statusFilter === s.value ? 'secondary' : 'ghost'"
        size="sm"
        class="rounded-full"
        @click="statusFilter = s.value"
      >
        {{ s.label }}
      </Button>
    </div>

    <div v-if="loading" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Skeleton v-for="i in 6" :key="i" class="aspect-[16/10] rounded-xl" />
    </div>

    <template v-else-if="filtered.length">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div v-for="w in filtered" :key="w.id" class="space-y-2">
          <WorldCard :world="w" />
          <div class="flex items-center justify-between gap-1 px-0.5">
            <Badge
              :variant="Number(w.status) === 2 ? 'secondary' : 'outline'"
              class="font-normal"
            >
              {{ statusLabel(w.status) }}
            </Badge>
            <div class="flex flex-wrap items-center gap-0.5">
              <Button variant="ghost" size="xs" @click="router.push(`/world-tree?id=${w.id}`)">
                编辑
              </Button>
              <Button
                v-if="Number(w.status) !== 2"
                variant="ghost"
                size="xs"
                :disabled="busyId === w.id"
                @click="doPublish(w)"
              >
                发布
              </Button>
              <Button
                v-else
                variant="ghost"
                size="xs"
                :disabled="busyId === w.id"
                @click="doOffline(w)"
              >
                下架
              </Button>
              <Button
                variant="ghost"
                size="xs"
                class="text-muted-foreground hover:text-destructive"
                :disabled="busyId === w.id"
                @click="askDelete(w)"
              >
                删除
              </Button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <EmptyState v-else text="还没有世界树" hint="在编辑页搭好结构后保存到云端">
      <Button size="sm" class="mt-3" @click="router.push('/world-tree')">去创建</Button>
    </EmptyState>

    <Dialog v-model:open="confirmOpen">
      <DialogContent class="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>删除世界树</DialogTitle>
          <DialogDescription>
            确定删除「{{ pending?.name }}」？广场上也会一并消失。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="confirmOpen = false">取消</Button>
          <Button variant="destructive" :disabled="busyId !== null" @click="confirmDelete">
            删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { Plus } from 'lucide-vue-next'
import { deleteWorld, getWorlds, offlineWorld, publishWorld } from '../api/worldTree'
import { useAuthStore } from '../stores/auth'
import WorldCard from '../components/WorldCard.vue'
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

const router = useRouter()
const auth = useAuthStore()

const filters = [
  { label: '全部', value: null },
  { label: '已发布', value: 2 },
  { label: '草稿', value: 0 },
  { label: '已下架', value: 3 },
]

const list = ref([])
const loading = ref(false)
const statusFilter = ref(null)
const busyId = ref(null)
const confirmOpen = ref(false)
const pending = ref(null)

const filtered = computed(() => {
  if (statusFilter.value === null) return list.value
  return list.value.filter((w) => Number(w.status) === statusFilter.value)
})

function statusLabel(s) {
  return { 0: '草稿', 2: '已发布', 3: '已下架' }[Number(s)] || '未知'
}

async function load() {
  if (!auth.user?.id) {
    await auth.fetchUser()
  }
  const creatorId = auth.user?.id
  if (!creatorId) return
  loading.value = true
  try {
    const res = await getWorlds({ creatorId, page: 1, size: 100 })
    list.value = res?.list ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function doPublish(w) {
  busyId.value = w.id
  try {
    await publishWorld(w.id)
    toast.success('已发布到世界广场')
    await load()
  } catch {
    /* */
  } finally {
    busyId.value = null
  }
}

async function doOffline(w) {
  busyId.value = w.id
  try {
    await offlineWorld(w.id)
    toast.success('已下架')
    await load()
  } catch {
    /* */
  } finally {
    busyId.value = null
  }
}

function askDelete(w) {
  pending.value = w
  confirmOpen.value = true
}

async function confirmDelete() {
  if (!pending.value) return
  busyId.value = pending.value.id
  try {
    await deleteWorld(pending.value.id)
    toast.success('已删除')
    confirmOpen.value = false
    pending.value = null
    await load()
  } catch {
    /* */
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>
