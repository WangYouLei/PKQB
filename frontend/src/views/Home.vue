<template>
  <div class="wallpaper-bg layout">
    <aside class="sidebar">
      <div class="sidebar-logo">PKQB</div>
      <nav class="sidebar-nav">
        <div class="sidebar-section">
          <div class="sidebar-section-title">文件管理</div>
          <router-link to="/my-files" class="sidebar-link" active-class="active"><span>&#128196;</span> 我的文件</router-link>
          <router-link to="/class-files" class="sidebar-link" active-class="active"><span>&#128101;</span> 班级共享</router-link>
        </div>
        <div class="sidebar-section">
          <div class="sidebar-section-title">AI 功能</div>
          <router-link to="/ai-chat" class="sidebar-link" active-class="active"><span>&#128172;</span> AI 对话</router-link>
          <router-link to="/rag-chat" class="sidebar-link" active-class="active"><span>&#128218;</span> 知识库问答</router-link>
          <router-link to="/kb-upload" class="sidebar-link" active-class="active"><span>&#128228;</span> 上传知识库</router-link>
          <router-link to="/rubric-upload" class="sidebar-link" active-class="active"><span>&#128221;</span> 上传题目</router-link>
        </div>
      </nav>
      <div class="sidebar-user">
        <div class="sidebar-user-name">{{ userStore.username }}</div>
        <div class="sidebar-user-info">{{ userStore.studentNo }} &middot; {{ userStore.className }}</div>
        <button class="btn btn-ghost btn-sm" style="margin-top:12px;width:100%" @click="handleLogout">退出登录</button>
      </div>
    </aside>
    <main class="main-content"><router-view /></main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
const router = useRouter()
const userStore = useUserStore()
function handleLogout() { userStore.logout(); router.push('/login') }
</script>
