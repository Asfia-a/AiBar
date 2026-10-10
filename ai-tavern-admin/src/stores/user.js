import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi } from '../api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('admin_user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => {
    const role = userInfo.value?.role
    return role === 1 || role === '1' || role === 'admin'
  })

  function setAuth(newToken, user) {
    token.value = newToken
    userInfo.value = user
    localStorage.setItem('admin_token', newToken)
    localStorage.setItem('admin_user', JSON.stringify(user))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_user')
  }

  async function login(form) {
    const data = await loginApi(form)
    const newToken = data.token || data.accessToken
    const user = data.user || { username: form.username, role: data.role }
    if (!(user.role === 1 || user.role === '1' || user.role === 'admin')) {
      throw new Error('仅管理员可登录')
    }
    setAuth(newToken, user)
    return user
  }

  return { token, userInfo, isLoggedIn, isAdmin, login, logout, setAuth }
})
