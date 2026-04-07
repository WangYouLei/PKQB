<template>
  <div class="wallpaper-bg auth-page">
    <div class="auth-container">
      <div class="auth-card glass-panel">
        <div class="auth-header">
          <h1 class="auth-logo">PKQB</h1>
          <p class="auth-subtitle">注册新账号</p>
        </div>
        <form @submit.prevent="handleRegister" class="auth-form">
          <div class="form-group">
            <label class="form-label">用户名</label>
            <input v-model="form.username" class="form-input" type="text" placeholder="请输入用户名" required />
          </div>
          <div class="form-group">
            <label class="form-label">密码</label>
            <input v-model="form.password" class="form-input" type="password" placeholder="请输入密码" required />
          </div>
          <div class="form-group">
            <label class="form-label">学号</label>
            <input v-model="form.studentNo" class="form-input" type="text" placeholder="请输入学号" required />
          </div>
          <div class="form-group">
            <label class="form-label">班级名称</label>
            <input v-model="form.className" class="form-input" type="text" placeholder="请输入班级名称" required />
          </div>
          <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            <span v-else>注 册</span>
          </button>
        </form>
        <div class="auth-footer">
          <span>已有账号？</span>
          <router-link to="/login" class="auth-link">返回登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { apiRegister } from '@/api'
import type { RegisterRequest } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const form = ref<RegisterRequest>({ username: '', password: '', studentNo: '', classId: null, className: '' })
const loading = ref(false)

async function handleRegister() {
  loading.value = true
  try {
    const res = await apiRegister(form.value)
    if (res.code === 200) {
      alert('注册成功，请登录')
      router.push('/login')
    } else {
      alert(res.message || '注册失败')
    }
  } catch (e: unknown) {
    const msg = (e as { message?: string })?.message || '注册失败，请重试'
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
