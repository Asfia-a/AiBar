<template>
  <div class="min-h-screen flex items-center justify-center px-4">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <span class="text-4xl">🍺</span>
        <h1 class="font-display text-3xl font-bold text-tavern-300 mt-2">AI角色酒馆</h1>
        <p class="text-tavern-muted mt-1">{{ isRegister ? '注册新账号' : '欢迎回来，旅人' }}</p>
      </div>

      <div class="tavern-card p-6">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="昵称" prop="nickname">
            <el-input v-model="form.nickname" placeholder="酒馆中的称呼" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>
          <el-form-item v-if="isRegister" label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password />
          </el-form-item>

          <el-button type="primary" class="w-full" :loading="loading" native-type="submit">
            {{ isRegister ? '注册' : '登录' }}
          </el-button>
        </el-form>

        <div class="mt-4 text-center text-sm text-tavern-muted">
          <button class="text-tavern-400 hover:text-tavern-300 transition-colors" @click="toggleMode">
            {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isRegister = ref(false)
const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const validateConfirm = (_rule, value, callback) => {
  if (isRegister.value && value !== form.password) {
    callback(new Error('两次密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '至少6位', trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
}

function toggleMode() {
  isRegister.value = !isRegister.value
  formRef.value?.resetFields()
}

async function handleSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    if (isRegister.value) {
      await authStore.register({ username: form.username, nickname: form.nickname, password: form.password })
      ElMessage.success('注册成功')
    } else {
      await authStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
    }
    router.push(route.query.redirect || '/')
  } catch { /* handled by interceptor */ }
  finally { loading.value = false }
}
</script>
