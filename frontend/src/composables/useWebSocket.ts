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
const es = ref<EventSource | null>(null)
let intentionalClose = false

const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

function connect() {
  const token = localStorage.getItem('token')
  if (!token) return

  // 如果已有连接，先断开再重建
  if (es.value) {
    intentionalClose = true
    es.value.close()
    es.value = null
  }

  intentionalClose = false

  // 鉴权依赖登录时种下的 HttpOnly token Cookie（同源请求 EventSource 会自动携带，
  // 后端 JwtInterceptor 支持从 Cookie 取 token），不再把 token 拼进 URL，
  // 避免其明文进入服务器访问日志、代理日志
  const sseUrl = '/api/notification/subscribe'
  const source = new EventSource(sseUrl)

  source.addEventListener('notification', (event) => {
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
      console.error('[SSE] 解析消息失败', e)
    }
  })

  source.onopen = () => {
    loadNotifications()
  }

  source.onerror = () => {
    // EventSource 原生自动重连（CONNECTING 状态）
    // 但服务器拒绝连接（如 Cookie 过期返回 401）时会进入 CLOSED，
    // 此时延时重建连接（用户重新登录后会种下新的 Cookie）
    if (source.readyState === EventSource.CLOSED) {
      es.value = null
      if (!intentionalClose) {
        setTimeout(() => {
          if (!intentionalClose) connect()
        }, 3000)
      }
    }
  }

  es.value = source
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

function disconnect() {
  intentionalClose = true
  if (es.value) {
    es.value.close()
    es.value = null
  }
  notifications.value = []
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
