<template>
  <div class="wallpaper-bg layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header">
        <div class="sidebar-logo">
          <div class="logo-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
            </svg>
          </div>
          <span class="logo-text">PKQB</span>
        </div>
        <button class="collapse-btn" @click="toggleSidebar" :title="isCollapsed ? '展开侧边栏' : '收起侧边栏'">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline :points="isCollapsed ? '9 18 15 12 9 6' : '15 18 9 12 15 6'"/>
          </svg>
        </button>
      </div>
      
      <div class="theme-switch">
        <button 
          class="theme-btn" 
          :class="{ active: !isLightMode }" 
          @click="setTheme(false)"
          :title="isCollapsed ? '深色模式' : ''"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
          </svg>
        </button>
        <button 
          class="theme-btn" 
          :class="{ active: isLightMode }" 
          @click="setTheme(true)"
          :title="isCollapsed ? '浅色模式' : ''"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="5"/>
            <line x1="12" y1="1" x2="12" y2="3"/>
            <line x1="12" y1="21" x2="12" y2="23"/>
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
            <line x1="1" y1="12" x2="3" y2="12"/>
            <line x1="21" y1="12" x2="23" y2="12"/>
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
          </svg>
        </button>
      </div>
      
      <nav class="sidebar-nav">
        <div class="sidebar-section">
          <div class="sidebar-section-title" v-show="!isCollapsed">文件管理</div>
          <router-link to="/my-files" class="sidebar-link" active-class="active" :title="isCollapsed ? '我的文件' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10 9 9 9 8 9"/>
            </svg>
            <span class="link-text">我的文件</span>
          </router-link>
          <router-link to="/class-files" class="sidebar-link" active-class="active" :title="isCollapsed ? '班级共享' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <span class="link-text">班级共享</span>
          </router-link>
        </div>
        <div class="sidebar-section">
          <div class="sidebar-section-title" v-show="!isCollapsed">AI 功能</div>
          <router-link to="/ai-chat" class="sidebar-link" active-class="active" :title="isCollapsed ? 'AI 对话' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <span class="link-text">AI 对话</span>
          </router-link>
          <router-link to="/rag-chat" class="sidebar-link" active-class="active" :title="isCollapsed ? '知识库问答' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
            <span class="link-text">知识库问答</span>
          </router-link>
          <router-link to="/kb-upload" class="sidebar-link" active-class="active" :title="isCollapsed ? '上传知识库' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            <span class="link-text">上传知识库</span>
          </router-link>
          <router-link to="/rubric-upload" class="sidebar-link" active-class="active" :title="isCollapsed ? '上传题目' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            <span class="link-text">上传题目</span>
          </router-link>
        </div>
        <div class="sidebar-section">
          <div class="sidebar-section-title" v-show="!isCollapsed">其他功能</div>
          <a class="sidebar-link" @click="showFeedbackModal = true" :title="isCollapsed ? '意见反馈' : ''">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
            <span class="link-text">意见反馈</span>
          </a>
        </div>
      </nav>
      
      <div class="sidebar-user" v-show="!isCollapsed">
        <div class="user-avatar">
          <img v-if="userStore.avatarUrl" :src="userStore.avatarUrl" alt="用户头像" class="user-avatar-img" />
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="user-info">
          <div class="sidebar-user-name">{{ userStore.username }}</div>
          <div class="sidebar-user-info">{{ userStore.studentNo }} · {{ userStore.className }}</div>
        </div>
        <div class="user-actions">
          <router-link to="/settings" class="settings-btn" title="设置">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </router-link>
          <button class="logout-btn" @click="handleLogout" title="退出登录">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </button>
        </div>
      </div>
    </aside>
    <button 
      class="sidebar-expand-btn" 
      v-show="isCollapsed" 
      @click="toggleSidebar" 
      title="展开侧边栏"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="9 18 15 12 9 6"/>
      </svg>
    </button>
    <main class="main-content"><router-view /></main>
    
    <!-- 意见反馈模态框 -->
    <div class="feedback-modal-overlay" v-if="showFeedbackModal" @click="showFeedbackModal = false">
      <div class="feedback-modal" @click.stop>
        <button class="modal-close-btn" @click="showFeedbackModal = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
        <div class="modal-header">
          <h3>意见反馈</h3>
          <p>扫描下方二维码添加微信进行意见反馈</p>
        </div>
        <div class="modal-body">
          <div class="qrcode-container">
            <img src="/20260216200513_24_63.png" alt="微信二维码" class="qrcode-image" />
          </div>
          <p class="feedback-tip">请添加微信好友后进行反馈</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const isLightMode = ref(false)
const isCollapsed = ref(false)
const showFeedbackModal = ref(false)

onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  isLightMode.value = savedTheme === 'light'
  
  const savedCollapsed = localStorage.getItem('sidebarCollapsed')
  isCollapsed.value = savedCollapsed === 'true'
})

function setTheme(light: boolean) {
  isLightMode.value = light
  localStorage.setItem('theme', light ? 'light' : 'dark')
  if (light) {
    document.documentElement.classList.add('light')
  } else {
    document.documentElement.classList.remove('light')
  }
}

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('sidebarCollapsed', String(isCollapsed.value))
}

function handleLogout() { userStore.logout(); router.push('/login') }
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  min-width: 260px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.sidebar.collapsed {
  width: 0 !important;
  min-width: 0 !important;
  max-width: 0 !important;
  padding: 0 !important;
  margin: 0 !important;
  overflow: hidden !important;
  border: none !important;
  opacity: 0;
  pointer-events: none;
}

.sidebar-header {
  padding: 0 12px 24px;
  border-bottom: 1px solid var(--border-glass);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
}

.logo-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  background: var(--accent-gradient);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(99,102,241,0.3);
}

.logo-icon svg {
  width: 22px;
  height: 22px;
  color: #fff;
}

.logo-text {
  font-size: 24px;
  font-weight: 800;
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 1px;
  white-space: nowrap;
  transition: opacity 0.2s ease;
}

.sidebar.collapsed .logo-text {
  opacity: 0;
  width: 0;
}

.collapse-btn {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 8px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.collapse-btn svg {
  width: 16px;
  height: 16px;
}

.collapse-btn:hover {
  background: var(--accent-light);
  border-color: var(--accent-border);
  color: var(--accent);
}

.sidebar.collapsed .collapse-btn svg {
  transform: rotate(180deg);
}

.theme-switch {
  display: flex;
  gap: 8px;
  padding: 0 12px 20px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-glass);
  transition: all 0.3s ease;
}

.sidebar.collapsed .theme-switch {
  flex-direction: column;
  padding: 0 12px 16px;
}

.theme-btn {
  flex: 1;
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all .3s ease;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.theme-btn svg {
  width: 18px;
  height: 18px;
}

.theme-btn:hover {
  background: var(--bg-glass-hover);
  transform: scale(1.02);
}

.theme-btn.active {
  background: var(--accent-gradient);
  border-color: transparent;
  box-shadow: 0 4px 16px rgba(99,102,241,0.3);
  color: #fff;
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-section {
  margin-bottom: 24px;
}

.sidebar-section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 1.5px;
  padding: 0 20px;
  margin-bottom: 12px;
  white-space: nowrap;
  transition: opacity 0.2s ease;
}

.sidebar.collapsed .sidebar-section-title {
  opacity: 0;
  height: 0;
  margin: 0;
  padding: 0;
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  margin: 4px 12px;
  border-radius: 14px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all .3s ease;
  position: relative;
  overflow: hidden;
}

.sidebar.collapsed .sidebar-link {
  padding: 14px;
  margin: 4px 8px;
  justify-content: center;
}

.nav-icon {
  width: 20px;
  height: 20px;
  min-width: 20px;
  flex-shrink: 0;
}

.link-text {
  white-space: nowrap;
  transition: opacity 0.2s ease;
}

.sidebar.collapsed .link-text {
  opacity: 0;
  width: 0;
  margin-left: 0;
}

.sidebar-link:hover {
  background: rgba(255,255,255,0.04);
  color: var(--text-primary);
  transform: translateX(4px);
}

.sidebar.collapsed .sidebar-link:hover {
  transform: translateX(0) scale(1.05);
}

:root.light .sidebar-link:hover {
  background: rgba(0,0,0,0.04);
}

.sidebar-link.active {
  background: var(--accent-light);
  color: var(--accent);
}

.sidebar-link.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--accent-gradient);
  border-radius: 0 3px 3px 0;
  transition: opacity 0.2s ease;
}

.sidebar.collapsed .sidebar-link.active::before {
  opacity: 0;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-glass);
  margin-top: auto;
  background: rgba(255,255,255,0.02);
  border-radius: 16px;
  margin: 16px 12px 0;
  transition: all 0.3s ease;
}

.user-avatar {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 12px;
  background: var(--accent-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.user-avatar svg {
  width: 20px;
  height: 20px;
  color: #fff;
}

.user-avatar img.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 12px;
}

.user-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.sidebar-user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-user-info {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.settings-btn {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 10px;
  border: none;
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  text-decoration: none;
}

.settings-btn svg {
  width: 18px;
  height: 18px;
}

.settings-btn:hover {
  background: var(--accent-light);
  color: var(--accent);
}

.logout-btn {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 10px;
  border: none;
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.logout-btn svg {
  width: 18px;
  height: 18px;
}

.logout-btn:hover {
  background: var(--error-light);
  color: var(--error);
}

.sidebar-expand-btn {
  position: fixed;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  z-index: 100;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
}

.sidebar-expand-btn svg {
  width: 20px;
  height: 20px;
}

.sidebar-expand-btn:hover {
  background: var(--accent-light);
  border-color: var(--accent-border);
  color: var(--accent);
  transform: translateY(-50%) scale(1.1);
  box-shadow: 0 6px 24px rgba(99,102,241,0.3);
}

.main-content {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}

@media (max-width: 768px) {
  .sidebar {
    width: 72px;
    min-width: 72px;
  }
  
  .sidebar .logo-text,
  .sidebar .link-text,
  .sidebar .sidebar-section-title,
  .sidebar .sidebar-user {
    display: none;
    opacity: 0;
  }
  
  .sidebar .sidebar-link {
    padding: 14px;
    margin: 4px 8px;
    justify-content: center;
  }
  
  .collapse-btn {
    display: none;
  }
}

.feedback-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.feedback-modal {
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  border-radius: 20px;
  padding: 32px;
  max-width: 400px;
  width: 90%;
  position: relative;
  animation: slideUp 0.3s ease;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--border-glass);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.modal-close-btn svg {
  width: 16px;
  height: 16px;
}

.modal-close-btn:hover {
  background: var(--error-light);
  border-color: var(--error);
  color: var(--error);
}

.modal-header {
  text-align: center;
  margin-bottom: 24px;
}

.modal-header h3 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.modal-header p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.modal-body {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qrcode-container {
  background: #fff;
  padding: 16px;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  margin-bottom: 16px;
}

.qrcode-image {
  width: 220px;
  height: 220px;
  object-fit: contain;
  display: block;
}

.feedback-tip {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
  padding: 8px 16px;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(99, 102, 241, 0.2);
}
</style>
