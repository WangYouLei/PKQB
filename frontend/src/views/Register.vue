<template>
  <div class="wallpaper-bg auth-page">
    <div class="auth-bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>
    <div class="auth-container">
      <div class="auth-card glass-panel">
        <div class="auth-header">
          <div class="auth-logo-wrapper">
            <div class="auth-logo-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
              </svg>
            </div>
            <h1 class="auth-logo">PKQB</h1>
          </div>
          <p class="auth-subtitle">创建新账号</p>
          <p class="auth-desc">加入智能学习平台</p>
        </div>
        <form @submit.prevent="handleRegister" class="auth-form">
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">用户名</label>
              <div class="input-wrapper">
                <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="8" r="4"/>
                  <path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/>
                </svg>
                <input v-model="form.username" class="form-input with-icon" type="text" placeholder="请输入用户名" required />
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">密码</label>
              <div class="input-wrapper">
                <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
                <input v-model="form.password" class="form-input with-icon" type="password" placeholder="请输入密码" required />
              </div>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">学号</label>
              <div class="input-wrapper">
                <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 10v6M2 10l10-5 10 5-10 5z"/>
                  <path d="M6 12v5c3 3 9 3 12 0v-5"/>
                </svg>
                <input v-model="form.studentNo" class="form-input with-icon" type="text" placeholder="请输入学号" required />
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">班级名称</label>
              <div class="input-wrapper">
                <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                </svg>
                <input v-model="form.className" class="form-input with-icon" type="text" placeholder="请输入班级名称" required />
              </div>
            </div>
          </div>
          <button type="submit" class="btn btn-primary btn-block auth-btn" :disabled="loading">
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
import { apiRegister } from '@/api'
import type { RegisterRequest } from '@/types'

const router = useRouter()
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
.auth-page { 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  padding: 24px; 
  position: relative;
  overflow: hidden;
}

.auth-bg-shapes {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.shape {
  position: absolute;
  border: 2px solid var(--ink);
  opacity: 0.9;
  animation: float 8s ease-in-out infinite;
}

.shape-1 {
  width: 120px;
  height: 120px;
  background: var(--accent);
  top: -30px;
  right: -30px;
  transform: rotate(-12deg);
  animation-delay: -1s;
}

.shape-2 {
  width: 90px;
  height: 90px;
  background: var(--secondary);
  bottom: 40px;
  left: -20px;
  transform: rotate(8deg);
  animation-delay: -3s;
}

.shape-3 {
  width: 60px;
  height: 60px;
  background: var(--tertiary);
  top: 30%;
  left: 18%;
  transform: rotate(-20deg);
  animation-delay: -5s;
}

@keyframes float {
  0%, 100% { translate: 0 0; }
  33% { translate: 20px -16px; }
  66% { translate: -14px 12px; }
}

.auth-container { 
  width: 100%; 
  max-width: 500px; 
  position: relative;
  z-index: 1;
}

.auth-card {
  padding: 40px 36px; 
  position: relative;
}

.auth-header { 
  text-align: center; 
  margin-bottom: 32px; 
}

.auth-logo-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 8px;
}

.auth-logo-icon {
  width: 44px;
  height: 44px;
  background: var(--accent);
  border: 2px solid var(--ink);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 3px 3px 0 var(--shadow-ink);
}

.auth-logo-icon svg {
  width: 24px;
  height: 24px;
  color: #141414;
}

.auth-logo { 
  font-size: 32px; 
  font-weight: 800; 
  color: var(--text-primary);
  letter-spacing: 2px; 
}

.auth-subtitle { 
  color: var(--text-primary); 
  font-size: 16px; 
  margin-top: 8px;
  font-weight: 500;
}

.auth-desc {
  color: var(--text-muted);
  font-size: 13px;
  margin-top: 4px;
}

.auth-form { 
  display: flex; 
  flex-direction: column; 
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.input-wrapper {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  transition: color 0.3s ease;
}

.form-input.with-icon {
  padding-left: 48px;
}

.input-wrapper:focus-within .input-icon {
  color: var(--accent);
}

.auth-btn {
  margin-top: 8px;
  height: 50px;
  font-size: 15px;
  letter-spacing: 0.1em;
}

.auth-footer { 
  text-align: center; 
  margin-top: 24px; 
  font-size: 14px; 
  color: var(--text-secondary); 
}

.auth-link { 
  color: #D6336C; 
  text-decoration: none; 
  margin-left: 4px; 
  font-weight: 700;
  transition: all 0.12s ease;
}

:root.dark .auth-link {
  color: #FF7FAB;
}

.auth-link:hover { 
  text-decoration: underline;
  text-underline-offset: 4px;
}

@media (max-width: 520px) {
  .auth-card {
    padding: 32px 24px;
  }
  
  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
  
  .auth-logo {
    font-size: 28px;
  }
}
</style>
