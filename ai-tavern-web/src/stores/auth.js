import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi } from '../api/auth'
import { getProfile } from '../api/user'
import { getToken, setToken } from '../utils/request'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(credentials) {
    const data = await loginApi(credentials)
    const t = data.token || data.accessToken || data
    if (typeof t === 'string') {
      token.value = t
      setToken(t)
    } else if (data.token) {
      token.value = data.token
      setToken(data.token)
    }
    await fetchUser()
    return data
  }

  async function register(form) {
    const data = await registerApi(form)
    if (data.token) {
      token.value = data.token
      setToken(data.token)
      await fetchUser()
    }
    return data
  }

  async function fetchUser() {
    if (!token.value) return null
    try {
      user.value = await getProfile()
    } catch {
      logout()
    }
    return user.value
  }

  function logout() {
    token.value = null
    user.value = null
    setToken(null)
  }

  return { token, user, isLoggedIn, login, register, fetchUser, logout }
})
