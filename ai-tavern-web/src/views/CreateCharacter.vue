<template>
  <div class="max-w-3xl mx-auto tavern-card p-6 space-y-5">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <h1 class="font-display text-3xl text-tavern-200">{{ isEdit ? '编辑角色' : '创作角色' }}</h1>
      <div v-if="!isEdit" class="flex flex-wrap gap-2">
        <el-upload
          :show-file-list="false"
          accept=".png,.json,image/png,application/json"
          :before-upload="handleImport"
        >
          <el-button type="warning" plain :loading="importing">导入 SillyTavern 角色卡</el-button>
        </el-upload>
      </div>
    </div>

    <p v-if="!isEdit" class="text-sm text-tavern-muted -mt-2">
      支持 SillyTavern / TavernAI 的 PNG 角色卡（内嵌 chara）与 JSON（V1/V2/V3），导入后可继续编辑再保存。
    </p>
    <p v-else class="text-sm text-tavern-muted -mt-2">
      你是该角色的创作者，可随时调整人设。已有对话会沿用创建时的人设快照，不会被本次修改破坏；新开的对话才会用新人设。
    </p>

    <div v-if="importPreview || (form.avatar && form.avatar.startsWith('http'))" class="flex items-center gap-3 rounded-lg border border-tavern-700/50 bg-tavern-900/40 p-3">
      <img :src="importPreview || form.avatar" alt="avatar" class="h-16 w-16 rounded-lg object-cover" />
      <div class="text-sm text-tavern-muted">
        {{ form.avatar && !form.avatar.startsWith('blob:') ? '角色图像已上传 OSS 并应用到头像' : '已读取角色图像，正在上传 / 保存时将上传 OSS' }}
      </div>
    </div>

    <el-form label-position="top" :model="form">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="50" />
      </el-form-item>
      <el-form-item label="头像">
        <div class="flex flex-wrap items-center gap-3 w-full">
          <el-avatar v-if="form.avatar" :size="56" :src="form.avatar" shape="square" />
          <el-input v-model="form.avatar" class="flex-1" placeholder="OSS 公网地址，或下方上传" />
          <el-upload
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp,image/gif,.png,.jpg,.jpeg,.webp,.gif"
            :before-upload="handleAvatarUpload"
          >
            <el-button :loading="uploading">上传到 OSS</el-button>
          </el-upload>
        </div>
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="性格人设" required>
        <el-input v-model="form.personality" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="背景故事">
        <el-input v-model="form.backgroundStory" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="示例对话 (JSON)">
        <el-input v-model="form.exampleDialogue" type="textarea" :rows="3" placeholder='[{"role":"user","content":"..."},{"role":"assistant","content":"..."}]' />
      </el-form-item>
      <el-form-item label="开场白">
        <el-input v-model="form.greeting" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="form.categoryId" clearable class="w-full">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select
          v-model="form.tagIds"
          multiple
          clearable
          filterable
          allow-create
          default-first-option
          class="w-full"
          placeholder="选择或输入新标签"
        >
          <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <div class="grid grid-cols-3 gap-3">
        <el-form-item label="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="top_p">
          <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="max_tokens">
          <el-input-number v-model="form.maxTokens" :min="64" :max="4096" :step="32" />
        </el-form-item>
      </div>
      <div class="flex gap-3">
        <el-button type="primary" :loading="saving" @click="save(false)">保存草稿</el-button>
        <el-button type="warning" :loading="saving" @click="save(true)">保存并提交审核</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createCharacter, updateCharacter, publishCharacter,
  getCharacterDetail, getCategories, getTags,
} from '../api/character'
import { uploadFile } from '../api/upload'
import { parseSillyTavernFile } from '../utils/sillyTavernCard'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const saving = ref(false)
const importing = ref(false)
const uploading = ref(false)
const categories = ref([])
const tags = ref([])
const importPreview = ref('')
const pendingAvatarFile = ref(null)

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
  const tagIds = (form.tagIds || []).filter((id) => typeof id === 'number' || (typeof id === 'string' && /^\d+$/.test(id)))
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
    ElMessage.success('头像已上传到 OSS')
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
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
      form.categoryId = matchCategoryId('游戏动漫') || matchCategoryId('治愈陪伴') || categories.value[0]?.id || null
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
        ElMessage.warning(e.message || '头像上传 OSS 失败，保存时将重试')
      } finally {
        uploading.value = false
      }
    } else if (result.remoteAvatarUrl) {
      form.avatar = result.remoteAvatarUrl
    }

    const unmatched = importedTags.filter(
      (n) => !tags.value.some((t) => t.name.toLowerCase() === String(n).toLowerCase()),
    )
    ElMessage.success(
      unmatched.length
        ? `已导入「${form.name}」，${importedTags.length} 个标签将在保存时自动创建：${unmatched.slice(0, 5).join('、')}`
        : `已导入「${form.name}」${form.avatar && !form.avatar.startsWith('blob:') ? '，头像已上传 OSS' : ''}`,
    )
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
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
      ElMessage.warning('头像上传失败，请手动填写头像 URL 后保存')
    }
  }
}

async function save(publish) {
  if (!form.name || !form.personality) {
    ElMessage.warning('请填写名称与人设')
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
      ElMessage.success('已提交审核')
    } else {
      ElMessage.success('已保存')
    }
    router.push(`/character/${card.id}`)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
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
        ElMessage.info('已填入世界树设定摘要，可继续完善人设')
      }
    } catch {
      /* ignore */
    }
  } else if (route.query.from === 'workshop') {
    try {
      const spawn = JSON.parse(sessionStorage.getItem('tavern_workshop_spawn') || 'null')
      if (spawn?.name) {
        const spawnTags = Array.isArray(spawn.tags) ? spawn.tags.map((n) => String(n).trim()).filter(Boolean) : []
        Object.assign(form, {
          name: spawn.name || '',
          avatar: spawn.avatar || '',
          description: spawn.description || '',
          personality: spawn.personality || '',
          backgroundStory: spawn.backgroundStory || '',
          exampleDialogue: spawn.exampleDialogue || '',
          greeting: spawn.greeting || '',
          categoryId: spawn.categoryId || matchCategoryId(spawn.category) || matchCategoryId('游戏动漫') || categories.value[0]?.id || null,
          categoryName: spawn.category || '',
          tagIds: matchTagIds(spawnTags),
          tagNames: spawnTags,
        })
        ElMessage.info('已载入工坊 AI 草稿（含标签/分类），可继续微调后保存')
      }
    } catch {
      /* ignore */
    }
  }
})

onBeforeUnmount(() => {
  if (importPreview.value) URL.revokeObjectURL(importPreview.value)
})
</script>
