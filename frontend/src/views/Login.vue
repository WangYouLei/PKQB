<template>
  <div class="wallpaper-bg auth-page">
    <div class="auth-container">
      <div class="auth-card glass-panel">
        <div class="auth-header">
          <h1 class="auth-logo">PKQB</h1>
          <p class="auth-subtitle">智能文档生成系统</p>
        </div>
        <form @submit.prevent="handleLogin" class="auth-form">
          <div class="form-group">
            <label class="form-label">学号</label>
            <input v-model="form.studentNo" class="form-input" type="text" placeholder="请输入学号" required />
          </div>
          <div class="form-group">
            <label class="form-label">密码</label>
            <input v-model="form.password" class="form-input" type="password" placeholder="请输入密码" required />
          </div>
          <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            <span v-else>登 录</span>
          </button>
        </form>
        <div class="auth-footer">
          <span>还没有账号？</span>
          <router-link to="/register" class="auth-link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiLogin } from '@/api'
import type { LoginRequest } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const form = ref<LoginRequest>({ studentNo: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    const res = await apiLogin(form.value)
    if (res.code === 200 && res.data) {
      userStore.setUser(res.data)
      router.push('/')
    } else {
      alert(res.message || '登录失败')
    }
  } catch (e: unknown) {
    const msg = (e as { message?: string })?.message || '登录失败，请重试'
    alert(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: flex; align-items: center; justify-content: center; padding: 24px; }
.auth-container { width: 100%; max-width: 420px; }
.auth-card { padding: 40px 32px; }
.auth-header { text-align: center; margin-bottom: 32px; }
.auth-logo { font-size: 36px; font-weight: 800; background: linear-gradient(135deg, #ff6b35, #ff8c5a); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; letter-spacing: 2px; }
.auth-subtitle { color: var(--text-secondary); font-size: 14px; margin-top: 8px; }
.auth-form { display: flex; flex-direction: column; }
.auth-footer { text-align: center; margin-top: 24px; font-size: 14px; color: var(--text-secondary); }
.auth-link { color: var(--accent); text-decoration: none; margin-left: 4px; font-weight: 500; }
.auth-link:hover { text-decoration: underline; }
</style>
