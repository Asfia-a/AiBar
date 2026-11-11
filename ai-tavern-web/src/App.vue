<template>
  <router-view />
  <Toaster :theme="theme" position="top-center" :rich-colors="true" :close-button="true" />
</template>

<script setup>
import { onMounted } from 'vue'
import { Toaster } from 'vue-sonner'
import 'vue-sonner/style.css'
import { useAuthStore } from './stores/auth'
import { theme } from './theme'

const authStore = useAuthStore()

onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.fetchUser()
  }
})
</script>
