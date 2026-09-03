<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-logo">PKQB <span>管理端</span></div>
      <nav class="sidebar-nav">
        <router-link to="/users" class="nav-link" active-class="active">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
          账号管理
        </router-link>
        <router-link to="/classes" class="nav-link" active-class="active">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 10v6M2 10l10-5 10 5-10 5z" />
            <path d="M6 12v5c3 3 9 3 12 0v-5" />
          </svg>
          班级管理
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <span class="sidebar-user">{{ auth.username }}</span>
        <button class="btn btn-sm" @click="handleLogout">退出</button>
      </div>
    </aside>
    <main class="main-content"><router-view /></main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>
