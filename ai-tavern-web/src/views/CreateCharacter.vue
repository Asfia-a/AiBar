<template>
  <div class="mx-auto max-w-4xl space-y-6 pb-20">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div class="space-y-1.5">
        <h1 class="text-2xl font-semibold tracking-tight">
          {{ isEdit ? '编辑角色' : '创建角色' }}
        </h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          {{
            isEdit
              ? '修改会对新对话生效；已有对话继续沿用人设快照，不会被本次编辑破坏。'
              : '填写角色的人设与开场，保存草稿后可以随时回来继续打磨。'
          }}
        </p>
      </div>

      <div v-if="!isEdit" class="shrink-0">
        <Button
          variant="outline"
          :disabled="importing || uploading || pageLoading"
          @click="importInput?.click()"
        >
          <LoaderCircle v-if="importing" class="size-4 animate-spin" />
          <FileUp v-else class="size-4" />
          {{ importing ? '导入中…' : '导入角色卡' }}
        </Button>
      </div>
    </header>

    <input
      ref="importInput"
      type="file"
      class="hidden"
      accept=".png,.json,image/png,application/json"
      @change="onImportPicked"
    />
    <input
      ref="avatarInput"
      type="file"
      class="hidden"
      accept="image/png,image/jpeg,image/webp,image/gif"
      @change="onAvatarPicked"
    />

    <div v-if="pageLoading" class="space-y-6">
      <Skeleton class="h-80 rounded-xl" />
      <Skeleton class="h-72 rounded-xl" />
      <Skeleton class="h-48 rounded-xl" />
    </div>

    <template v-else>
      <!-- 基础信息 -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">基础信息</CardTitle>
          <CardDescription>角色在广场和详情页里展示的信息。</CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <div class="space-y-2">
            <Label for="character-name">
              名称
              <span class="text-muted-foreground">*</span>
            </Label>
            <Input
              id="character-name"
              v-model="form.name"
              maxlength="50"
              placeholder="给角色起一个名字"
            />
          </div>

          <div class="space-y-3">
            <Label>头像</Label>
            <div class="flex flex-col gap-4 sm:flex-row">
              <button
                type="button"
                class="group relative flex size-32 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-dashed border-border bg-muted/30 transition-colors hover:border-foreground/30"
                :disabled="uploading"
                @click="avatarInput?.click()"
              >
                <img
                  v-if="avatarPreview"
                  :src="avatarPreview"
                  alt="角色头像预览"
                  class="size-full object-cover"
                />
                <span v-else class="flex flex-col items-center gap-2 text-muted-foreground">
                  <ImagePlus class="size-6" />
                  <span class="text-xs">上传头像</span>
                </span>
                <span
                  v-if="uploading"
                  class="absolute inset-0 grid place-items-center bg-background/70 backdrop-blur-sm"
                >
                  <LoaderCircle class="size-5 animate-spin" />
                </span>
              </button>

              <div class="min-w-0 flex-1 space-y-3">
                <div class="space-y-2">
                  <Label for="avatar-url">头像地址</Label>
                  <Input
                    id="avatar-url"
                    v-model="form.avatar"
                    placeholder="https://…，或点击左侧上传"
                  />
                </div>
                <div class="flex flex-wrap items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="uploading"
                    @click="avatarInput?.click()"
                  >
                    <LoaderCircle v-if="uploading" class="size-4 animate-spin" />
                    <Upload v-else class="size-4" />
                    {{ uploading ? '上传中…' : '上传到 OSS' }}
                  </Button>
                  <Button
                    v-if="form.avatar || importPreview"
                    variant="ghost"
                    size="sm"
                    @click="clearAvatar"
                  >
                    清除
                  </Button>
                </div>
                <p class="text-xs leading-relaxed text-muted-foreground">{{ avatarHint }}</p>
              </div>
            </div>
          </div>

          <div class="space-y-2">
            <Label for="character-description">简介</Label>
            <Textarea
              id="character-description"
              v-model="form.description"
              :rows="3"
              placeholder="一两句话介绍这个角色"
            />
          </div>

          <div class="space-y-2">
            <Label>分类</Label>
            <div v-if="categories.length" class="flex flex-wrap gap-1.5">
              <Button
                v-for="c in categories"
                :key="c.id"
                size="sm"
                class="rounded-full"
                :variant="form.categoryId === c.id ? 'secondary' : 'outline'"
                @click="selectCategory(c)"
              >
                {{ c.name }}
              </Button>
            </div>
            <p v-else class="text-xs text-muted-foreground">暂无分类可选。</p>
            <p class="text-xs text-muted-foreground">再次点击已选分类即可清除。</p>
          </div>

          <div class="space-y-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <Label>标签</Label>
              <span v-if="selectedTagChips.length" class="text-xs text-muted-foreground">
                已选 {{ selectedTagChips.length }} 个
              </span>
            </div>

            <div v-if="tags.length" class="flex flex-wrap gap-1.5">
              <Button
                v-for="t in tags"
                :key="t.id"
                size="sm"
                class="rounded-full"
                :variant="isTagSelected(t) ? 'secondary' : 'outline'"
                @click="toggleTag(t)"
              >
                {{ t.name }}
              </Button>
            </div>
            <p v-else class="text-xs text-muted-foreground">
              暂无可用标签，可以直接输入创建。
            </p>

            <div class="flex items-center gap-2">
              <Input
                v-model="customTag"
                placeholder="输入新标签后回车"
                @keydown.enter.prevent="addCustomTag"
              />
              <Button variant="outline" @click="addCustomTag">添加</Button>
            </div>

            <div v-if="selectedTagChips.length" class="flex flex-wrap items-center gap-1.5">
              <Badge
                v-for="chip in selectedTagChips"
                :key="chip.key"
                variant="secondary"
                class="gap-1 pr-1 font-normal"
              >
                {{ chip.name }}
                <button
                  type="button"
                  class="rounded-full p-0.5 text-muted-foreground transition-colors hover:bg-background/60 hover:text-foreground"
                  :aria-label="`移除标签 ${chip.name}`"
                  @click="removeTag(chip)"
                >
                  <X class="size-3" />
                </button>
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- 人设 -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">人设</CardTitle>
          <CardDescription>决定角色如何说话、行动，以及如何理解自己的过去。</CardDescription>
        </CardHeader>
        <CardContent class="space-y-5">
          <div class="space-y-2">
            <Label for="character-personality">
              性格人设
              <span class="text-muted-foreground">*</span>
            </Label>
            <Textarea
              id="character-personality"
              v-model="form.personality"
              :rows="6"
              placeholder="描述性格、说话方式、习惯与边界，越具体越稳定"
            />
          </div>

          <div class="space-y-2">
            <Label for="character-background">背景故事</Label>
            <Textarea
              id="character-background"
              v-model="form.backgroundStory"
              :rows="4"
              placeholder="角色的来历、处境与正在发生的事"
            />
          </div>

          <div class="space-y-2">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <Label for="character-examples">示例对话</Label>
              <Badge variant="outline" class="font-normal">JSON</Badge>
            </div>
            <Textarea
              id="character-examples"
              v-model="form.exampleDialogue"
              :rows="4"
              class="font-mono text-xs leading-relaxed"
              placeholder='[{"role":"user","content":"..."},{"role":"assistant","content":"..."}]'
            />
            <p class="text-xs text-muted-foreground">可选；用几轮示例固定角色的语气。</p>
          </div>
        </CardContent>
      </Card>

      <!-- 开场白 -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">开场白</CardTitle>
          <CardDescription>新对话开始时，角色说出的第一段话。</CardDescription>
        </CardHeader>
        <CardContent>
          <Textarea
            v-model="form.greeting"
            :rows="3"
            placeholder="例如：雨停之前，你都可以坐在这里。"
          />
        </CardContent>
      </Card>

      <!-- 模型参数 -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">模型参数</CardTitle>
          <CardDescription>控制回复的随机性与长度，保存时会随角色一起提交。</CardDescription>
        </CardHeader>
        <CardContent class="grid gap-6 sm:grid-cols-3">
          <div class="space-y-3">
            <div class="flex items-baseline justify-between gap-2">
              <Label for="temperature">temperature</Label>
              <span class="text-sm tabular-nums">{{ Number(form.temperature).toFixed(2) }}</span>
            </div>
            <input
              id="temperature"
              v-model.number="form.temperature"
              type="range"
              min="0"
              max="2"
              step="0.1"
              class="param-range w-full"
            />
            <div class="flex justify-between text-[11px] text-muted-foreground">
              <span>0 克制</span>
              <span>2 发散</span>
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex items-baseline justify-between gap-2">
              <Label for="top-p">top_p</Label>
              <span class="text-sm tabular-nums">{{ Number(form.topP).toFixed(2) }}</span>
            </div>
            <input
              id="top-p"
              v-model.number="form.topP"
              type="range"
              min="0"
              max="1"
              step="0.05"
              class="param-range w-full"
            />
            <div class="flex justify-between text-[11px] text-muted-foreground">
              <span>0 聚焦</span>
              <span>1 放开</span>
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex items-baseline justify-between gap-2">
              <Label for="max-tokens">max_tokens</Label>
              <span class="text-sm tabular-nums">{{ formatNum(form.maxTokens) }}</span>
            </div>
            <input
              id="max-tokens"
              v-model.number="form.maxTokens"
              type="range"
              min="64"
              max="4096"
              step="32"
              class="param-range w-full"
            />
            <div class="flex justify-between text-[11px] text-muted-foreground">
              <span>64</span>
              <span>4096</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- 操作栏 -->
      <div class="sticky bottom-4 z-20">
        <div
          class="flex flex-col-reverse gap-3 rounded-xl border border-border/80 bg-card/95 p-3 shadow-lg backdrop-blur sm:flex-row sm:items-center sm:justify-between"
        >
          <p class="text-xs text-muted-foreground">
            {{ isEdit ? '保存后立即生效，已有对话不受影响。' : '先保存草稿，确认后再提交审核。' }}
          </p>
          <div class="flex flex-wrap items-center justify-end gap-2">
            <Button variant="ghost" :disabled="saving" @click="goCancel">
              <X class="size-4" />
              取消
            </Button>
            <Button variant="outline" :disabled="saving" @click="save(false)">
              <LoaderCircle v-if="saving" class="size-4 animate-spin" />
              <Save v-else class="size-4" />
              保存草稿
            </Button>
            <Button class="text-background" :disabled="saving" @click="save(true)">
              <LoaderCircle v-if="saving" class="size-4 animate-spin" />
              <Send v-else class="size-4" />
              保存并提交审核
            </Button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { FileUp, ImagePlus, LoaderCircle, Save, Send, Upload, X } from 'lucide-vue-next'
import {
  createCharacter,
  updateCharacter,
  publishCharacter,
  getCharacterDetail,
  getCategories,
  getTags,
} from '../api/character'
import { uploadFile } from '../api/upload'
import { parseSillyTavernFile } from '../utils/sillyTavernCard'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const saving = ref(false)
const importing = ref(false)
const uploading = ref(false)
const pageLoading = ref(true)
const categories = ref([])
const tags = ref([])
const importPreview = ref('')
const pendingAvatarFile = ref(null)
const customTag = ref('')
const importInput = ref(null)
const avatarInput = ref(null)

const form = reactive({
  name: '',
  avatar: '',
  description: '',
  personality: '',
  backgroundStory: '',
  exampleDialogue: '',
  greeting: '',
  categoryId: null,
  categoryName: '',
  tagIds: [],
  tagNames: [],
  temperature: 0.7,
  topP: 0.9,
  maxTokens: 500,
})

const avatarPreview = computed(() => importPreview.value || form.avatar || '')

const avatarHint = computed(() => {
  if (uploading.value) return '正在上传头像到 OSS…'
  if (importPreview.value) return '已读取角色卡图像，正在上传或将在保存时上传到 OSS。'
  if (pendingAvatarFile.value) return '头像待上传，保存时会自动传到 OSS。'
  if (form.avatar?.startsWith('http')) return '头像已上传，保存在 OSS。'
  if (form.avatar?.startsWith('blob:')) return '当前为本地预览，保存时会尝试上传。'
  return '建议使用 1:1 图片，支持 PNG / JPG / WebP / GIF。'
})

const selectedTagChips = computed(() => {
  const chips = []
  const seen = new Set()

  for (const raw of form.tagIds || []) {
    const tag = tags.value.find((t) => String(t.id) === String(raw))
    const name = tag?.name ?? (typeof raw === 'string' ? raw.trim() : '')
    if (!name) continue
    const key = name.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    chips.push({ key: String(raw), name, id: tag?.id ?? null })
  }

  for (const raw of form.tagNames || []) {
    const name = String(raw).trim()
    if (!name) continue
    const key = name.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    chips.push({ key: `name:${name}`, name, id: null })
  }

  return chips
})

function formatNum(n) {
  return Number(n || 0).toLocaleString()
}

function isTagSelected(tag) {
  return form.tagIds.some((id) => String(id) === String(tag.id))
}

function selectCategory(category) {
  if (form.categoryId === category.id) {
    form.categoryId = null
    form.categoryName = ''
    return
  }
  form.categoryId = category.id
  form.categoryName = category.name || ''
}

function toggleTag(tag) {
  const name = String(tag.name || '').toLowerCase()
  if (isTagSelected(tag)) {
    form.tagIds = form.tagIds.filter((id) => String(id) !== String(tag.id))
    form.tagNames = form.tagNames.filter((n) => String(n).toLowerCase() !== name)
    return
  }

  form.tagIds = [
    ...form.tagIds.filter(
      (id) => !(typeof id === 'string' && !/^\d+$/.test(id) && id.toLowerCase() === name),
    ),
    tag.id,
  ]
}

function addCustomTag() {
  const name = customTag.value.trim()
  if (!name) return

  const existing = tags.value.find((t) => String(t.name).toLowerCase() === name.toLowerCase())
  if (existing) {
    if (!isTagSelected(existing)) form.tagIds = [...form.tagIds, existing.id]
    form.tagNames = form.tagNames.filter((n) => String(n).toLowerCase() !== name.toLowerCase())
  } else {
    const duplicated =
      form.tagIds.some(
        (id) => typeof id === 'string' && String(id).toLowerCase() === name.toLowerCase(),
      ) || form.tagNames.some((n) => String(n).toLowerCase() === name.toLowerCase())
    if (!duplicated) form.tagIds = [...form.tagIds, name]
  }

  customTag.value = ''
}

function removeTag(chip) {
  const name = chip.name.toLowerCase()
  form.tagIds = form.tagIds.filter((id) => {
    if (chip.id != null && String(id) === String(chip.id)) return false
    return String(id).toLowerCase() !== name
  })
  form.tagNames = form.tagNames.filter((n) => String(n).toLowerCase() !== name)
}

function clearAvatar() {
  form.avatar = ''
  pendingAvatarFile.value = null
  if (importPreview.value) {
    URL.revokeObjectURL(importPreview.value)
    importPreview.value = ''
  }
}

function matchTagIds(tagNames) {
  if (!tagNames?.length) return []
  const map = new Map(tags.value.map((t) => [String(t.name).toLowerCase(), t.id]))
  const ids = []
  for (const name of tagNames) {
    const id = map.get(String(name).toLowerCase())
    if (id != null && !ids.includes(id)) ids.push(id)
  }
  return ids
}

function matchCategoryId(name) {
  if (!name) return null
  const n = String(name).trim()
  const exact = categories.value.find((c) => c.name === n)
  if (exact) return exact.id
  const soft = categories.value.find((c) => c.name && (n.includes(c.name) || c.name.includes(n)))
  return soft?.id ?? null
}

function buildSavePayload() {
  const selectedNames = (form.tagIds || [])
    .map((id) => {
      if (typeof id === 'string') return id.trim()
      return tags.value.find((t) => t.id === id)?.name
    })
    .filter(Boolean)
  const tagNames = [...new Set([...(form.tagNames || []), ...selectedNames])]
  const tagIds = (form.tagIds || [])
    .filter(
      (id) =>
        typeof id === 'number' || (typeof id === 'string' && /^\d+$/.test(id)),
    )
    .map((id) => Number(id))
  return {
    ...form,
    tagIds,
    tagNames,
    categoryId: form.categoryId || matchCategoryId(form.categoryName) || null,
    categoryName: form.categoryName || undefined,
  }
}

async function handleAvatarUpload(file) {
  uploading.value = true
  try {
    const data = await uploadFile(file)
    const url = data?.url || data
    if (!url) throw new Error('未返回地址')
    form.avatar = url
    pendingAvatarFile.value = null
    if (importPreview.value) {
      URL.revokeObjectURL(importPreview.value)
      importPreview.value = ''
    }
    toast.success('头像已上传到 OSS')
  } catch (e) {
    toast.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

async function handleImport(file) {
  importing.value = true
  try {
    const result = await parseSillyTavernFile(file)
    const importedTags = (result.tagNames || []).map((n) => String(n).trim()).filter(Boolean)
    Object.assign(form, {
      name: result.form.name,
      description: result.form.description,
      personality: result.form.personality,
      backgroundStory: result.form.backgroundStory,
      exampleDialogue: result.form.exampleDialogue,
      greeting: result.form.greeting,
      tagIds: matchTagIds(importedTags),
      tagNames: importedTags,
    })
    if (!form.categoryId) {
      form.categoryId =
        matchCategoryId('游戏动漫') ||
        matchCategoryId('治愈陪伴') ||
        categories.value[0]?.id ||
        null
      form.categoryName = categories.value.find((c) => c.id === form.categoryId)?.name || ''
    }
    if (importPreview.value) URL.revokeObjectURL(importPreview.value)
    importPreview.value = result.previewUrl || ''
    pendingAvatarFile.value = result.avatarFile

    // 导入时立即提取角色图并上传 OSS，写入头像字段
    if (result.avatarFile) {
      uploading.value = true
      try {
        const data = await uploadFile(result.avatarFile)
        const url = data?.url || data
        if (!url) throw new Error('未返回地址')
        form.avatar = url
        pendingAvatarFile.value = null
        if (importPreview.value) {
          URL.revokeObjectURL(importPreview.value)
          importPreview.value = ''
        }
      } catch (e) {
        // 上传失败时保留本地预览，保存时再重试
        if (result.previewUrl) form.avatar = result.previewUrl
        toast.warning(e.message || '头像上传 OSS 失败，保存时将重试')
      } finally {
        uploading.value = false
      }
    } else if (result.remoteAvatarUrl) {
      form.avatar = result.remoteAvatarUrl
    }

    const unmatched = importedTags.filter(
      (n) => !tags.value.some((t) => t.name.toLowerCase() === String(n).toLowerCase()),
    )
    toast.success(
      unmatched.length
        ? `已导入「${form.name}」，${importedTags.length} 个标签将在保存时自动创建：${unmatched.slice(0, 5).join('、')}`
        : `已导入「${form.name}」${form.avatar && !form.avatar.startsWith('blob:') ? '，头像已上传 OSS' : ''}`,
    )
  } catch (e) {
    toast.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
  return false
}

async function ensureAvatarUploaded() {
  if (!pendingAvatarFile.value) return
  try {
    const data = await uploadFile(pendingAvatarFile.value)
    const url = data?.url || data
    if (url) {
      form.avatar = url
      pendingAvatarFile.value = null
    }
  } catch {
    // 上传失败保留本地预览 URL 可能导致后端存 blob，改为清空让用户手填
    if (form.avatar?.startsWith('blob:')) {
      form.avatar = ''
      toast.warning('头像上传失败，请手动填写头像 URL 后保存')
    }
  }
}

async function save(publish) {
  if (!form.name || !form.personality) {
    toast.warning('请填写名称与人设')
    return
  }
  saving.value = true
  try {
    await ensureAvatarUploaded()
    const payload = buildSavePayload()
    let card
    if (isEdit.value) {
      card = await updateCharacter(route.params.id, payload)
    } else {
      card = await createCharacter(payload)
    }
    if (publish) {
      await publishCharacter(card.id)
      toast.success('已提交审核')
    } else {
      toast.success('已保存')
    }
    router.push(`/character/${card.id}`)
  } finally {
    saving.value = false
  }
}

function goCancel() {
  if (isEdit.value) {
    router.push(`/character/${route.params.id}`)
  } else {
    router.push('/mine')
  }
}

function onImportPicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) handleImport(file)
}

function onAvatarPicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) handleAvatarUpload(file)
}

onMounted(async () => {
  try {
    categories.value = (await getCategories()) || []
    tags.value = (await getTags()) || []
    if (isEdit.value) {
      const c = await getCharacterDetail(route.params.id)
      Object.assign(form, {
        name: c.name,
        avatar: c.avatar,
        description: c.description,
        personality: c.personality,
        backgroundStory: c.backgroundStory,
        exampleDialogue: c.exampleDialogue,
        greeting: c.greeting,
        categoryId: c.categoryId,
        tagIds: (c.tags || []).map((t) => t.id),
        temperature: Number(c.temperature ?? 0.7),
        topP: Number(c.topP ?? 0.9),
        maxTokens: c.maxTokens || 500,
      })
    } else if (route.query.from === 'world-tree') {
      try {
        const spawn = JSON.parse(sessionStorage.getItem('tavern_world_spawn') || 'null')
        if (spawn?.context) {
          form.backgroundStory = `【来自世界树】\n${spawn.context}`
          if (spawn.worldName && !form.name) {
            form.description = `诞生于「${spawn.worldName}」世界观下的角色。`
          }
          toast.info('已填入世界树设定摘要，可继续完善人设')
        }
      } catch {
        /* ignore */
      }
    } else if (route.query.from === 'workshop') {
      try {
        const spawn = JSON.parse(sessionStorage.getItem('tavern_workshop_spawn') || 'null')
        if (spawn?.name) {
          const spawnTags = Array.isArray(spawn.tags)
            ? spawn.tags.map((n) => String(n).trim()).filter(Boolean)
            : []
          Object.assign(form, {
            name: spawn.name || '',
            avatar: spawn.avatar || '',
            description: spawn.description || '',
            personality: spawn.personality || '',
            backgroundStory: spawn.backgroundStory || '',
            exampleDialogue: spawn.exampleDialogue || '',
            greeting: spawn.greeting || '',
            categoryId:
              spawn.categoryId ||
              matchCategoryId(spawn.category) ||
              matchCategoryId('游戏动漫') ||
              categories.value[0]?.id ||
              null,
            categoryName: spawn.category || '',
            tagIds: matchTagIds(spawnTags),
            tagNames: spawnTags,
          })
          toast.info('已载入工坊 AI 草稿（含标签/分类），可继续微调后保存')
        }
      } catch {
        /* ignore */
      }
    }
  } catch (e) {
    toast.error(e.message || '加载角色数据失败')
  } finally {
    pageLoading.value = false
  }
})

onBeforeUnmount(() => {
  if (importPreview.value) URL.revokeObjectURL(importPreview.value)
})
</script>

<style scoped>
.param-range {
  height: 4px;
  appearance: none;
  border-radius: 9999px;
  background: var(--muted);
  outline: none;
}

.param-range::-webkit-slider-thumb {
  width: 16px;
  height: 16px;
  appearance: none;
  border: 2px solid var(--background);
  border-radius: 9999px;
  background: var(--foreground);
  box-shadow: 0 0 0 1px var(--border);
  cursor: pointer;
}

.param-range::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border: 2px solid var(--background);
  border-radius: 9999px;
  background: var(--foreground);
  box-shadow: 0 0 0 1px var(--border);
  cursor: pointer;
}

.param-range:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in oklch, var(--ring) 50%, transparent);
}
</style>
