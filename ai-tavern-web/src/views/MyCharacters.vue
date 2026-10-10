<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="font-display text-3xl text-tavern-200">我的角色</h1>
        <p class="text-sm text-tavern-muted mt-1">你创建的 AI · 可随时调整人设，已有对话不会被破坏</p>
      </div>
      <el-button type="warning" @click="$router.push('/create')">创建新角色</el-button>
    </div>

    <el-radio-group v-model="statusFilter" size="small">
      <el-radio-button :value="null">全部</el-radio-button>
      <el-radio-button v-for="s in statusDefs" :key="s.value" :value="s.value">
        {{ s.label }}
      </el-radio-button>
    </el-radio-group>

    <div v-loading="loading" class="space-y-8 min-h-[120px]">
      <!-- 全部：按状态分行 -->
      <template v-if="statusFilter === null">
        <section v-for="group in groupedSections" :key="group.value" class="space-y-3">
          <div class="flex items-center gap-2 border-b border-tavern-700/50 pb-2">
            <h2 class="font-display text-lg text-tavern-200">{{ group.label }}</h2>
            <span class="text-xs text-tavern-muted">{{ group.items.length }}</span>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <article
              v-for="c in group.items"
              :key="c.id"
              class="tavern-card p-4 flex gap-3 cursor-pointer hover:border-tavern-500/50 transition-colors"
              @click="$router.push(`/character/${c.id}`)"
            >
              <el-avatar :size="72" :src="c.avatar" shape="square" class="!rounded-lg shrink-0">
                {{ c.name?.[0] }}
              </el-avatar>
              <div class="min-w-0 flex-1 space-y-2">
                <div class="flex items-start justify-between gap-2">
                  <h3 class="font-display text-lg text-tavern-100 truncate">{{ c.name }}</h3>
                  <el-tag size="small" :type="statusType(c.status)">{{ statusLabel(c.status) }}</el-tag>
                </div>
                <p class="text-xs text-tavern-muted line-clamp-2">{{ c.description || '暂无简介' }}</p>
                <div class="flex flex-wrap gap-2" @click.stop>
                  <el-button size="small" type="primary" plain @click="$router.push(`/edit/${c.id}`)">编辑</el-button>
                  <el-button size="small" type="warning" plain @click="$router.push(`/character/${c.id}`)">查看</el-button>
                  <el-button size="small" type="danger" plain :loading="deletingId === c.id" @click="onDelete(c)">删除</el-button>
                </div>
              </div>
            </article>
          </div>
        </section>
        <el-empty v-if="!loading && !list.length" description="还没有角色，去创建一个吧" />
      </template>

      <!-- 单状态筛选 -->
      <template v-else>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <article
            v-for="c in filtered"
            :key="c.id"
            class="tavern-card p-4 flex gap-3 cursor-pointer hover:border-tavern-500/50 transition-colors"
            @click="$router.push(`/character/${c.id}`)"
          >
            <el-avatar :size="72" :src="c.avatar" shape="square" class="!rounded-lg shrink-0">
              {{ c.name?.[0] }}
            </el-avatar>
            <div class="min-w-0 flex-1 space-y-2">
              <div class="flex items-start justify-between gap-2">
                <h3 class="font-display text-lg text-tavern-100 truncate">{{ c.name }}</h3>
                <el-tag size="small" :type="statusType(c.status)">{{ statusLabel(c.status) }}</el-tag>
              </div>
              <p class="text-xs text-tavern-muted line-clamp-2">{{ c.description || '暂无简介' }}</p>
              <div class="flex flex-wrap gap-2" @click.stop>
                <el-button size="small" type="primary" plain @click="$router.push(`/edit/${c.id}`)">编辑</el-button>
                <el-button size="small" type="warning" plain @click="$router.push(`/character/${c.id}`)">查看</el-button>
                <el-button size="small" type="danger" plain :loading="deletingId === c.id" @click="onDelete(c)">删除</el-button>
              </div>
            </div>
          </article>
        </div>
        <el-empty v-if="!loading && !filtered.length" description="该状态下暂无角色" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCharacters, deleteCharacter } from '../api/character'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()
const list = ref([])
const loading = ref(false)
const statusFilter = ref(null)
const deletingId = ref(null)

const statusDefs = [
  { value: 2, label: '已上架' },
  { value: 1, label: '审核中' },
  { value: 0, label: '草稿' },
  { value: 3, label: '已下架' },
  { value: 4, label: '已驳回' },
]

function statusLabel(s) {
  return statusDefs.find((x) => x.value === Number(s))?.label || '未知'
}

function statusType(s) {
  return ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' })[s] || 'info'
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

async function onDelete(c) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${c.name}」？角色将从广场与列表移除；已有对话仍可继续（沿用人设快照）。`,
      '删除角色',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  deletingId.value = c.id
  try {
    await deleteCharacter(c.id)
    ElMessage.success('已删除')
    list.value = list.value.filter((x) => x.id !== c.id)
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  } finally {
    deletingId.value = null
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
