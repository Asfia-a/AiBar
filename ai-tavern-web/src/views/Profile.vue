<template>
  <div class="max-w-3xl mx-auto space-y-6">
    <section class="tavern-card p-6">
      <div class="flex flex-col sm:flex-row gap-6 items-start">
        <div class="flex flex-col items-center gap-2 shrink-0">
          <el-avatar :size="96" :src="form.avatar" class="!rounded-2xl ring-2 ring-tavern-600/50">
            {{ form.nickname?.[0] || '旅' }}
          </el-avatar>
          <el-upload :show-file-list="false" accept="image/*" :before-upload="onAvatarUpload">
            <el-button size="small" :loading="uploading">上传头像</el-button>
          </el-upload>
        </div>
        <div class="flex-1 w-full space-y-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="font-display text-3xl text-tavern-200">个人资料</h1>
            <el-tag effect="dark" type="warning">{{ levelName }}会员</el-tag>
          </div>
          <el-form label-position="top" :model="form">
            <div class="grid sm:grid-cols-2 gap-3">
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" maxlength="32" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input :model-value="username" disabled />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="form.email" />
              </el-form-item>
              <el-form-item label="手机">
                <el-input v-model="form.phone" />
              </el-form-item>
            </div>
            <el-form-item label="头像 URL">
              <el-input v-model="form.avatar" placeholder="也可粘贴图片地址" />
            </el-form-item>
            <el-button type="warning" :loading="saving" @click="save">保存资料</el-button>
          </el-form>
        </div>
      </div>
    </section>

    <section class="tavern-card p-6 space-y-4">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 class="font-display text-xl text-tavern-200">积分钱包</h2>
          <p class="text-sm text-tavern-muted mt-1">
            对话按积分扣费 · 约 {{ balance.tokensPerPoint || 1000 }} API Token = 1 积分
          </p>
        </div>
        <el-button type="warning" @click="rechargeOpen = true">模拟充值</el-button>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
        <div class="rounded-xl border border-tavern-700/50 bg-tavern-900/40 p-4">
          <div class="text-xs text-tavern-muted">当前积分</div>
          <div class="text-2xl font-display text-tavern-300 mt-1">{{ formatNum(balance.points) }}</div>
        </div>
        <div class="rounded-xl border border-tavern-700/50 bg-tavern-900/40 p-4">
          <div class="text-xs text-tavern-muted">等价 Token</div>
          <div class="text-2xl font-display text-tavern-200 mt-1">{{ formatNum(balance.tokenBalance) }}</div>
        </div>
        <div class="rounded-xl border border-tavern-700/50 bg-tavern-900/40 p-4">
          <div class="text-xs text-tavern-muted">会员</div>
          <div class="text-2xl font-display text-tavern-200 mt-1">{{ levelName }}</div>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <el-button @click="goCheckIn">去签到领积分</el-button>
        <el-button plain type="warning" @click="quickRecharge(1)">快速充 ¥1</el-button>
        <el-button plain type="warning" @click="quickRecharge(6)">快速充 ¥6</el-button>
      </div>
    </section>

    <section class="tavern-card p-6">
      <h2 class="font-display text-xl text-tavern-200 mb-3">消费 / 充值记录</h2>
      <el-table :data="logs" size="small" class="w-full" empty-text="暂无记录">
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">{{ typeMap[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column prop="tokensUsed" label="积分" width="110">
          <template #default="{ row }">
            <span :class="row.type === 1 ? 'text-red-300' : 'text-emerald-300'">
              {{ row.type === 1 ? '-' : '+' }}{{ formatNum(row.tokensUsed) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="积分余额" width="120">
          <template #default="{ row }">{{ formatNum(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" min-width="160" />
      </el-table>
    </section>

    <MockRechargeDialog v-model="rechargeOpen" @success="onRecharged" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile, getTokenBalance, getTokenLogs, recharge } from '../api/user'
import { uploadFile } from '../api/upload'
import { useAuthStore } from '../stores/auth'
import MockRechargeDialog from '../components/MockRechargeDialog.vue'

const router = useRouter()
const authStore = useAuthStore()
const form = reactive({ nickname: '', avatar: '', email: '', phone: '' })
const username = ref('')
const balance = ref({})
const logs = ref([])
const saving = ref(false)
const uploading = ref(false)
const rechargeOpen = ref(false)
const typeMap = { 1: '消费', 2: '充值', 3: '签到', 4: '兑换' }
const levelName = computed(() => ['普通', '白银', '黄金', '钻石'][balance.value.memberLevel || 0] || '普通')

function formatNum(n) {
  if (n == null || n === '') return '0'
  return Number(n).toLocaleString()
}

async function load() {
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
}

async function save() {
  saving.value = true
  try {
    await updateProfile(form)
    await authStore.fetchUser()
    ElMessage.success('资料已保存')
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
    ElMessage.success('头像已上传，请点击保存')
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

function goCheckIn() {
  router.push('/checkin')
}

async function quickRecharge(amount) {
  try {
    const res = await recharge({ amount, mock: true, channel: 'quick' })
    ElMessage.success(`到账 ${formatNum(res.points)} 积分`)
    await onRecharged(res)
  } catch (e) {
    ElMessage.error(e.message || '充值失败')
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
