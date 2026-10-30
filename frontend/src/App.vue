<template>
  <router-view />
  <ToastContainer />
  <GlobalLoading />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import ToastContainer from '@/components/ToastContainer.vue'
import GlobalLoading from '@/components/GlobalLoading.vue'

const userStore = useUserStore()

userStore.loadFromStorage()

onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') {
    document.documentElement.classList.add('dark')
  }
})
</script>
