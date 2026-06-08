import { ref, computed } from 'vue'
import { apiGetNotifications, apiMarkNotificationRead, apiMarkAllNotificationsRead, apiDeleteNotification, apiClearNotifications } from '@/api'
import type { NotificationItem } from '@/types'

interface WsNotification {
  id: number
  type: string
  title: string
  message: string
  timestamp: string
  read: boolean
}

const notifications = ref<NotificationItem[]>([])
const ws = ref<WebSocket | null>(null)
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
let intentionalClose = false

const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

function connect() {
  const token = localStorage.getItem('token')
  if (!token) return

  // 如果已有连接（含正在关闭的旧连接），先断开再重建
  if (ws.value) {
    intentionalClose = true
    ws.value.close()
    ws.value = null
  }

  // 清除待执行的重连定时器
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  intentionalClose = false
  reconnectAttempts = 0

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/ws/notification?token=${encodeURIComponent(token)}`

  const socket = new WebSocket(wsUrl)

  socket.onopen = () => {
    loadNotifications()
  }

  socket.onmessage = (event) => {
    try {
      const data: WsNotification = JSON.parse(event.data)
      const item: NotificationItem = {
        id: data.id,
        userId: 0,
        type: data.type,
        title: data.title,
        message: data.message,
        isRead: data.read,
        createTime: data.timestamp
      }
      notifications.value.unshift(item)
      if (notifications.value.length > 100) {
        notifications.value = notifications.value.slice(0, 100)
      }
    } catch (e) {
      console.error('[WebSocket] 解析消息失败', e)
    }
  }

  socket.onclose = () => {
    ws.value = null
    if (!intentionalClose) {
      scheduleReconnect()
    }
  }

  socket.onerror = () => {
    socket.close()
  }

  ws.value = socket
}

async function loadNotifications() {
  try {
    const res = await apiGetNotifications()
    if (res?.data) {
      notifications.value = res.data
    }
  } catch (e) {
    console.error('[通知] 加载历史通知失败', e)
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return
  reconnectAttempts++
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts - 1), 30000)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

function disconnect() {
  intentionalClose = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws.value) {
    ws.value.close()
    ws.value = null
  }
  notifications.value = []
  reconnectAttempts = 0
}

async function markAsRead(id: number) {
  const item = notifications.value.find(n => n.id === id)
  if (item) {
    item.isRead = true
  }
  try {
    await apiMarkNotificationRead(id)
  } catch (e) {
    console.error('[通知] 标记已读失败', e)
  }
}

async function markAllRead() {
  notifications.value.forEach(n => n.isRead = true)
  try {
    await apiMarkAllNotificationsRead()
  } catch (e) {
    console.error('[通知] 全部已读失败', e)
  }
}

async function deleteNotification(id: number) {
  notifications.value = notifications.value.filter(n => n.id !== id)
  try {
    await apiDeleteNotification(id)
  } catch (e) {
    console.error('[通知] 删除通知失败', e)
  }
}

async function clearNotifications() {
  notifications.value = []
  try {
    await apiClearNotifications()
  } catch (e) {
    console.error('[通知] 清空通知失败', e)
  }
}

export function useWebSocket() {
  return {
    notifications,
    unreadCount,
    connect,
    disconnect,
    markAsRead,
    markAllRead,
    deleteNotification,
    clearNotifications
  }
}
