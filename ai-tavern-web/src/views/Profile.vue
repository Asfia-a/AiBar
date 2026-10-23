<template>
  <div class="mx-auto max-w-5xl space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div class="space-y-1.5">
        <h1 class="text-2xl font-semibold tracking-tight">个人资料</h1>
        <p class="text-sm text-muted-foreground">
          管理账号信息、头像与积分余额，所有对外展示都以这里为准。
        </p>
      </div>
      <Badge variant="secondary" class="font-normal">{{ levelName }}会员</Badge>
    </header>

    <div v-if="loading" class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <Skeleton class="h-[460px] rounded-xl" />
      <Skeleton class="h-[380px] rounded-xl" />
      <Skeleton class="h-72 rounded-xl lg:col-span-2" />
    </div>

    <template v-else>
      <div class="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <!-- 资料卡 -->
        <Card>
          <CardHeader>
            <CardTitle class="text-base">资料卡</CardTitle>
            <CardDescription>昵称和头像会出现在角色详情、评论与社区作品里。</CardDescription>
          </CardHeader>
          <CardContent class="space-y-6">
            <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
              <div class="flex shrink-0 flex-col items-center gap-2.5">
                <Avatar class="size-24 rounded-2xl border border-border/60">
                  <AvatarImage :src="form.avatar" :alt="form.nickname || username" />
                  <AvatarFallback class="rounded-2xl text-2xl">{{ initial }}</AvatarFallback>
                </Avatar>
                <input
                  ref="avatarInput"
                  type="file"
                  class="hidden"
                  accept="image/*"
                  @change="onAvatarPicked"
                />
                <Button
                  variant="outline"
                  size="sm"
                  :disabled="uploading"
                  @click="avatarInput?.click()"
                >
                  <LoaderCircle v-if="uploading" class="size-4 animate-spin" />
                  <Camera v-else class="size-4" />
                  {{ uploading ? '上传中…' : '上传头像' }}
                </Button>
              </div>

              <div class="min-w-0 flex-1 space-y-1">
                <p class="truncate text-xl font-semibold">
                  {{ form.nickname || '未设置昵称' }}
                </p>
                <p class="truncate text-sm text-muted-foreground">
                  @{{ username || '未设置用户名' }}
                </p>
                <p class="truncate text-xs text-muted-foreground">
                  {{ form.email || '未填写邮箱' }}
                </p>
              </div>
            </div>

            <Separator />

            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-2">
                <Label for="profile-nickname">昵称</Label>
                <Input
                  id="profile-nickname"
                  v-model="form.nickname"
                  maxlength="32"
                  placeholder="怎么称呼你"
                />
              </div>
              <div class="space-y-2">
                <Label for="profile-username">用户名</Label>
                <Input
                  id="profile-username"
                  :model-value="username"
                  disabled
                  placeholder="用户名不可修改"
                />
              </div>
              <div class="space-y-2">
                <Label for="profile-email">邮箱</Label>
                <Input
                  id="profile-email"
                  v-model="form.email"
                  type="email"
                  placeholder="you@example.com"
                />
              </div>
              <div class="space-y-2">
                <Label for="profile-phone">手机</Label>
                <Input
                  id="profile-phone"
                  v-model="form.phone"
                  type="tel"
                  placeholder="选填"
                />
              </div>
            </div>

            <div class="space-y-2">
              <Label for="profile-avatar-url">头像地址</Label>
              <Input
                id="profile-avatar-url"
                v-model="form.avatar"
                placeholder="也可以粘贴图片地址"
              />
              <p class="text-xs text-muted-foreground">
                上传后会先替换预览，点击「保存资料」才会同步到账号。
              </p>
            </div>

            <div class="flex justify-end border-t border-border/60 pt-4">
              <Button class="text-background" :disabled="saving" @click="save">
                <LoaderCircle v-if="saving" class="size-4 animate-spin" />
                <Save v-else class="size-4" />
                保存资料
              </Button>
            </div>
          </CardContent>
        </Card>

        <!-- 积分概览 -->
        <Card class="lg:sticky lg:top-20">
          <CardHeader>
            <CardTitle class="text-base">积分钱包</CardTitle>
            <CardDescription>
              对话按积分扣费 · 约 {{ balance.tokensPerPoint || 1000 }} Token = 1 积分
            </CardDescription>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="rounded-xl border border-border/70 bg-secondary/60 p-4">
              <p class="text-xs text-muted-foreground">当前积分</p>
              <p class="mt-1 text-3xl font-semibold tabular-nums">
                {{ formatNum(balance.points) }}
              </p>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="rounded-xl border border-border/70 p-3">
                <p class="text-xs text-muted-foreground">等价 Token</p>
                <p class="mt-1 text-lg font-medium tabular-nums">
                  {{ formatNum(balance.tokenBalance) }}
                </p>
              </div>
              <div class="rounded-xl border border-border/70 p-3">
                <p class="text-xs text-muted-foreground">会员等级</p>
                <p class="mt-1 text-lg font-medium">{{ levelName }}</p>
              </div>
            </div>

            <div class="space-y-2">
              <Button class="w-full" @click="rechargeOpen = true">
                <CreditCard class="size-4" />
                模拟充值
              </Button>
              <Button variant="outline" class="w-full" @click="goCheckIn">
                <CalendarCheck class="size-4" />
                签到领积分
              </Button>
            </div>

            <div class="space-y-2 border-t border-border/60 pt-3">
              <p class="text-xs text-muted-foreground">快捷充值</p>
              <div class="grid grid-cols-2 gap-2">
                <Button variant="outline" size="sm" @click="quickRecharge(1)">充 ¥1</Button>
                <Button variant="outline" size="sm" @click="quickRecharge(6)">充 ¥6</Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- 积分记录 -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">积分记录</CardTitle>
          <CardDescription>最近的消费、充值、签到与兑换。</CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            v-if="!logs.length"
            text="暂无积分记录"
            hint="完成一次对话或充值后会显示在这里"
          />
          <div v-else class="overflow-x-auto">
            <table class="w-full min-w-[560px] text-sm">
              <thead>
                <tr
                  class="border-b border-border/60 text-left text-xs font-normal text-muted-foreground"
                >
                  <th class="pb-2 pr-4 font-normal">类型</th>
                  <th class="pb-2 pr-4 font-normal">积分变动</th>
                  <th class="pb-2 pr-4 font-normal">积分余额</th>
                  <th class="pb-2 font-normal">时间</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, index) in logs"
                  :key="row.id || row.createdAt || index"
                  class="border-b border-border/50 last:border-0"
                >
                  <td class="py-3 pr-4">
                    <Badge variant="outline" class="font-normal">
                      {{ typeMap[row.type] || row.type }}
                    </Badge>
                  </td>
                  <td
                    class="py-3 pr-4 tabular-nums"
                    :class="row.type === 1 ? 'text-foreground' : 'text-muted-foreground'"
                  >
                    {{ row.type === 1 ? '-' : '+' }}{{ formatNum(row.tokensUsed) }}
                  </td>
                  <td class="py-3 pr-4 tabular-nums">{{ formatNum(row.balanceAfter) }}</td>
                  <td class="py-3 text-xs text-muted-foreground">{{ formatTime(row.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </template>

    <MockRechargeDialog v-model="rechargeOpen" @success="onRecharged" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { CalendarCheck, Camera, CreditCard, LoaderCircle, Save } from 'lucide-vue-next'
import { getProfile, updateProfile, getTokenBalance, getTokenLogs, recharge } from '../api/user'
import { uploadFile } from '../api/upload'
import { useAuthStore } from '../stores/auth'
import MockRechargeDialog from '../components/MockRechargeDialog.vue'
import EmptyState from '../components/EmptyState.vue'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'

const router = useRouter()
const authStore = useAuthStore()
const form = reactive({ nickname: '', avatar: '', email: '', phone: '' })
const username = ref('')
const balance = ref({})
const logs = ref([])
const saving = ref(false)
const uploading = ref(false)
const loading = ref(true)
const rechargeOpen = ref(false)
const avatarInput = ref(null)
const typeMap = { 1: '消费', 2: '充值', 3: '签到', 4: '兑换' }
const levelName = computed(
  () => ['普通', '白银', '黄金', '钻石'][balance.value.memberLevel || 0] || '普通',
)
const initial = computed(() => form.nickname?.[0] || username.value?.[0] || '旅')

function formatNum(n) {
  if (n == null || n === '') return '0'
  return Number(n).toLocaleString()
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false }).slice(0, 19)
}

async function load() {
  try {
    const u = await getProfile()
    username.value = u.username || ''
    Object.assign(form, {
      nickname: u.nickname || '',
      avatar: u.avatar || '',
      email: u.email || '',
      phone: u.phone || '',
    })
    balance.value = await getTokenBalance()
    const page = await getTokenLogs({ page: 1, size: 30 })
    logs.value = page.list || page.records || page || []
  } catch (e) {
    toast.error(e.message || '加载个人资料失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await updateProfile(form)
    await authStore.fetchUser()
    toast.success('资料已保存')
  } catch (e) {
    toast.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onAvatarUpload(file) {
  uploading.value = true
  try {
    const data = await uploadFile(file)
    const url = data?.url || data
    if (!url) throw new Error('上传失败')
    form.avatar = url
    toast.success('头像已上传，请点击保存')
  } catch (e) {
    toast.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

function onAvatarPicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (file) onAvatarUpload(file)
}

function goCheckIn() {
  router.push('/checkin')
}

async function quickRecharge(amount) {
  try {
    const res = await recharge({ amount, mock: true, channel: 'quick' })
    toast.success(`到账 ${formatNum(res.points)} 积分`)
    await onRecharged(res)
  } catch (e) {
    toast.error(e.message || '充值失败')
  }
}

async function onRecharged(res) {
  if (res?.pointsBalance != null || res?.balance != null) {
    balance.value = { ...balance.value, points: res.pointsBalance ?? res.balance }
  }
  await load()
  await authStore.fetchUser()
}

onMounted(load)
</script>
