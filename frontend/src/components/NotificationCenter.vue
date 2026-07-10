<template>
  <div class="notification-center" :class="{ 'sidebar-mode': sidebar }">
    <a v-if="sidebar" class="sidebar-notification-link" @click.stop="togglePanel" :title="isCollapsed ? '通知' : ''">
      <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      <span class="link-text">通知</span>
      <span class="sidebar-badge" v-if="unreadCount > 0">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </a>
    <button v-else class="bell-btn" @click.stop="togglePanel" title="通知">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      <span class="badge" v-if="unreadCount > 0">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>

    <Teleport to="body">
      <div class="notification-overlay" v-if="showPanel" @click="showPanel = false"></div>
      <div class="notification-drawer" :class="{ open: showPanel }" @click.stop>
        <div class="drawer-header">
          <span class="drawer-title">通知中心</span>
          <div class="drawer-header-actions">
            <button class="read-all-btn" @click="handleMarkAllRead" v-if="unreadCount > 0">全部已读</button>
            <button class="clear-btn" @click="handleClear" v-if="notifications.length > 0">清空</button>
            <button class="close-btn" @click="showPanel = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>
        <div class="drawer-body">
          <div v-if="notifications.length === 0" class="empty-tip">
            <div class="empty-icon">🔔</div>
            <div>暂无通知</div>
            <div class="empty-hint">上传题目或知识库后，AI 处理完成会在这里通知你</div>
          </div>
          <div
            v-for="n in notifications"
            :key="n.id"
            class="notification-item"
            :class="{ unread: !n.isRead }"
            @click="handleClick(n)"
          >
            <div class="notification-item-header">
              <div class="notification-title">{{ n.title }}</div>
              <button class="item-delete-btn" @click.stop="handleDelete(n.id)" title="删除">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            <div class="notification-message">{{ n.message }}</div>
            <div class="notification-time">{{ formatTime(n.createTime) }}</div>
          </div>
        </div>
      </div>

      <!-- 全屏通知详情 -->
      <div class="notification-fullscreen" v-if="selectedNotification" @click.self.stop="selectedNotification = null">
        <div class="fullscreen-card">
          <div class="fullscreen-header">
            <h2 class="fullscreen-title">{{ selectedNotification.title }}</h2>
            <button class="fullscreen-delete-btn" @click="handleDeleteFullscreen" title="删除">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
            <button class="fullscreen-close-btn" @click="selectedNotification = null" title="关闭">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="fullscreen-message">{{ selectedNotification.message }}</div>
          <div class="fullscreen-time">{{ formatTime(selectedNotification.createTime) }}</div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import type { NotificationItem } from '@/types'

defineProps<{
  sidebar?: boolean
  isCollapsed?: boolean
}>()

const { notifications, unreadCount, markAsRead, markAllRead, deleteNotification, clearNotifications } = useWebSocket()
const showPanel = ref(false)
const selectedNotification = ref<NotificationItem | null>(null)

function togglePanel(e: Event) {
  e.stopPropagation()
  showPanel.value = !showPanel.value
}

function handleClick(n: NotificationItem) {
  if (!n.isRead) {
    markAsRead(n.id)
  }
  selectedNotification.value = n
}

function handleMarkAllRead() {
  markAllRead()
}

function handleDelete(id: number) {
  deleteNotification(id)
}

function handleDeleteFullscreen() {
  if (selectedNotification.value) {
    deleteNotification(selectedNotification.value.id)
    selectedNotification.value = null
  }
}

function handleClear() {
  clearNotifications()
}

function formatTime(timestamp: string): string {
  const now = Date.now()
  const then = new Date(timestamp).getTime()
  const diff = now - then
  if (diff < 0) return '刚刚'
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

</script>

<style scoped>
.notification-center {
  position: relative;
}

.bell-btn {
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
  position: relative;
}

.bell-btn svg {
  width: 18px;
  height: 18px;
}

.bell-btn:hover {
  background: var(--accent-light);
  color: var(--accent);
}

.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--error);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.sidebar-notification-link {
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
  cursor: pointer;
  overflow: hidden;
}

.sidebar-notification-link:hover {
  background: rgba(255,255,255,0.04);
  color: var(--text-primary);
  transform: translateX(4px);
}

:root.light .sidebar-notification-link:hover {
  background: rgba(0,0,0,0.04);
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

.sidebar-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--error);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.notification-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 9998;
  animation: overlayIn 0.2s ease;
}

@keyframes overlayIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.notification-drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 380px;
  height: 100vh;
  background: var(--card-bg);
  border-left: 1px solid var(--card-border);
  box-shadow: -8px 0 32px rgba(0,0,0,0.15);
  backdrop-filter: blur(20px);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.notification-drawer.open {
  transform: translateX(0);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-glass);
  flex-shrink: 0;
}

.drawer-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.drawer-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.read-all-btn {
  font-size: 13px;
  color: var(--accent);
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.read-all-btn:hover {
  background: var(--accent-light);
}

.clear-btn {
  font-size: 13px;
  color: var(--text-muted);
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.clear-btn:hover {
  color: var(--error);
  background: var(--error-light);
}

.close-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  margin-left: 4px;
}

.close-btn svg {
  width: 16px;
  height: 16px;
}

.close-btn:hover {
  background: var(--error-light);
  border-color: var(--error);
  color: var(--error);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.empty-tip {
  text-align: center;
  padding: 60px 24px;
  color: var(--text-muted);
  font-size: 14px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-hint {
  font-size: 13px;
  margin-top: 12px;
  color: var(--text-muted);
  opacity: 0.7;
  line-height: 1.6;
}

.notification-item {
  padding: 16px 24px;
  transition: background 0.2s ease;
  border-left: 3px solid transparent;
}

.notification-item:hover {
  background: var(--bg-glass-hover);
}

.notification-item.unread {
  border-left-color: var(--accent);
  background: var(--accent-light);
}

.notification-item.unread:hover {
  background: var(--bg-glass-hover);
}

.notification-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.notification-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
  flex: 1;
}

.item-delete-btn {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.item-delete-btn svg {
  width: 14px;
  height: 14px;
}

.notification-item:hover .item-delete-btn {
  opacity: 1;
}

.item-delete-btn:hover {
  background: var(--error-light);
  color: var(--error);
}

.notification-message {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
  cursor: pointer;
}

.notification-time {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 8px;
}

@media (max-width: 640px) {
  .notification-drawer {
    width: 100%;
  }
}

.notification-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: overlayIn 0.2s ease;
}

.fullscreen-card {
  background: var(--card-bg);
  border-radius: 16px;
  padding: 32px;
  max-width: 600px;
  width: 90%;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  animation: fullscreenIn 0.25s ease;
}

@keyframes fullscreenIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.fullscreen-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.fullscreen-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  flex: 1;
  line-height: 1.4;
}

.fullscreen-delete-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.fullscreen-delete-btn svg {
  width: 18px;
  height: 18px;
}

.fullscreen-delete-btn:hover {
  background: var(--error-light);
  border-color: var(--error);
  color: var(--error);
}

.fullscreen-close-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
  margin-left: 16px;
}

.fullscreen-close-btn svg {
  width: 18px;
  height: 18px;
}

.fullscreen-close-btn:hover {
  background: var(--error-light);
  border-color: var(--error);
  color: var(--error);
}

.fullscreen-message {
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.8;
  word-break: break-word;
  white-space: pre-wrap;
}

.fullscreen-time {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-glass);
}
</style>
