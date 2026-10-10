<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">PKQB <span style="color: var(--accent)">管理端</span></h1>
      <p class="login-subtitle">管理员登录</p>
      <div v-if="error" class="login-error">{{ error }}</div>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input v-model.trim="form.username" class="form-input" placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="form-input" placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <button class="btn btn-primary" style="width: 100%" :disabled="loading" type="submit">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    toast.success('登录成功')
    router.push('/')
  } catch (e: any) {
    error.value = e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
