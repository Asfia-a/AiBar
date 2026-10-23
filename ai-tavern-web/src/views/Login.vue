<template>
  <div class="grid min-h-screen lg:grid-cols-2">
    <!-- 左侧品牌区 -->
    <div class="relative hidden overflow-hidden border-r border-border/60 lg:block">
      <div
        class="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,var(--color-secondary),transparent_60%)]"
      />
      <!-- 缓慢漂浮的光斑，让静态的登录页有点呼吸 -->
      <div class="orb -left-24 top-1/4 size-80 bg-[radial-gradient(circle_at_center,rgba(228,196,137,0.20),transparent_65%)]" />
      <div class="orb -right-16 bottom-10 size-72 bg-[radial-gradient(circle_at_center,rgba(120,110,255,0.16),transparent_65%)] [animation-delay:-6s]" />
      <div
        class="absolute inset-0 opacity-[0.04] [background-image:linear-gradient(to_right,currentColor_1px,transparent_1px),linear-gradient(to_bottom,currentColor_1px,transparent_1px)] [background-size:56px_56px]"
      />
      <div class="relative flex h-full flex-col justify-between p-12">
        <div v-reveal class="flex items-center gap-2">
          <span class="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
            <Sparkles class="size-4" />
          </span>
          <span class="text-sm font-semibold tracking-tight">AI 角色酒馆</span>
        </div>

        <div class="max-w-md">
          <h1 class="text-4xl font-semibold leading-tight tracking-tight">
            在灯下坐下，<br />和角色说第一句话。
          </h1>
          <p class="mt-4 text-sm leading-relaxed text-muted-foreground">
            流式对话、角色卡创作、叙事改写与社区发布——从一次对话到一部作品。
          </p>
          <ul class="mt-8 space-y-3 text-sm text-muted-foreground">
            <li v-for="item in features" :key="item" class="flex items-start gap-2">
              <Check class="mt-0.5 size-4 shrink-0" />
              <span>{{ item }}</span>
            </li>
          </ul>
        </div>

        <p class="text-xs text-muted-foreground">毕业设计作品 · 董佳武</p>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="flex items-center justify-center px-4 py-10">
      <div class="w-full max-w-sm">
        <div class="mb-8 lg:hidden">
          <span class="grid size-9 place-items-center rounded-lg bg-primary text-primary-foreground">
            <Sparkles class="size-4" />
          </span>
        </div>

        <h2 class="text-2xl font-semibold tracking-tight">
          {{ isRegister ? '创建账号' : '欢迎回来' }}
        </h2>
        <p class="mt-1 text-sm text-muted-foreground">
          {{ isRegister ? '注册后即可与角色开始对话' : '登录后继续你的故事' }}
        </p>

        <form class="mt-8 space-y-4" @submit.prevent="handleSubmit">
          <div class="space-y-2">
            <Label for="username">用户名</Label>
            <Input
              id="username"
              v-model.trim="form.username"
              autocomplete="username"
              placeholder="请输入用户名"
            />
          </div>

          <div v-if="isRegister" class="space-y-2">
            <Label for="nickname">昵称</Label>
            <Input id="nickname" v-model.trim="form.nickname" placeholder="酒馆中的称呼" />
          </div>

          <div class="space-y-2">
            <Label for="password">密码</Label>
            <Input
              id="password"
              v-model="form.password"
              type="password"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              placeholder="至少 6 位"
            />
          </div>

          <div v-if="isRegister" class="space-y-2">
            <Label for="confirmPassword">确认密码</Label>
            <Input
              id="confirmPassword"
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="请再次输入密码"
            />
          </div>

          <p v-if="errorMessage" class="text-sm text-destructive">{{ errorMessage }}</p>

          <Button type="submit" class="w-full" :disabled="loading">
            {{ loading ? '请稍候…' : isRegister ? '注册' : '登录' }}
          </Button>
        </form>

        <p class="mt-6 text-center text-sm text-muted-foreground">
          {{ isRegister ? '已有账号？' : '还没有账号？' }}
          <button
            type="button"
            class="font-medium text-foreground underline-offset-4 hover:underline"
            @click="toggleMode"
          >
            {{ isRegister ? '去登录' : '去注册' }}
          </button>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, Sparkles } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isRegister = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const features = [
  '手写 SSE 流式对话，逐字出现',
  '角色卡 JSON 契约约束，导入导出 SillyTavern',
  '对话一键改写为小说 / 剧本',
  'MCP 能力开放，任何 AI 客户端可调用',
]

function toggleMode() {
  isRegister.value = !isRegister.value
  errorMessage.value = ''
  form.password = ''
  form.confirmPassword = ''
}

async function handleSubmit() {
  errorMessage.value = ''

  if (!form.username) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (form.password.length < 6) {
    errorMessage.value = '密码至少 6 位'
    return
  }
  if (isRegister.value && form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    if (isRegister.value) {
      await authStore.register({
        username: form.username,
        nickname: form.nickname || form.username,
        password: form.password,
      })
    } else {
      await authStore.login({ username: form.username, password: form.password })
    }
    router.push(route.query.redirect || '/')
  } catch (err) {
    // 后端统一返回 Result，拦截器已弹出错误提示；这里兜底展示
    errorMessage.value = err?.response?.data?.message || err?.message || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>
