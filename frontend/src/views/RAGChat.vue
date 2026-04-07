<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <h3>&#128218; 知识库问答</h3>
        <button class="btn btn-sm btn-primary" @click="newSession">+ 新对话</button>
      </div>
      <div v-if="loadingSessions" class="sidebar-loading"><div class="spinner"></div></div>
      <div v-else-if="sessions.length === 0" class="sidebar-empty">暂无历史对话</div>
      <div v-else class="session-list">
        <div v-for="(s, idx) in sessions" :key="s" class="session-item" :class="{ active: currentSessionId === s }" @click="selectSession(s)">
          <div class="session-item-icon">&#128218;</div>
          <div class="session-item-text">
            <div class="session-item-title">会话 {{ sessions.length - idx }}</div>
            <div class="session-item-id">{{ s }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧聊天区域 -->
    <main class="chat-main">
      <div class="chat-container" ref="chatContainer">
        <div v-if="messages.length === 0 && !currentSessionId" class="chat-welcome">
          <img class="chat-welcome-avatar" src="/ai-avatar.jpg" alt="AI" />
          <h3>知识库问答</h3>
          <p>选择一个历史会话或直接开始新对话</p>
        </div>

        <template v-if="currentSessionId">
          <div v-for="(msg, idx) in messages" :key="idx" class="message" :class="msg.role">
            <img v-if="msg.role === 'assistant'" class="message-avatar-img" src="/ai-avatar.jpg" alt="AI" />
            <div v-else class="message-avatar">&#128100;</div>
            <div class="message-bubble">
              <div class="message-content" v-html="formatContent(msg.content)"></div>
              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
            </div>
          </div>
        </template>

        <div v-if="streaming" class="message assistant">
          <img class="message-avatar-img" src="/ai-avatar.jpg" alt="AI" />
          <div class="message-bubble">
            <div class="message-content"><span class="typing-indicator">检索知识库中...</span></div>
          </div>
        </div>
      </div>

      <div class="chat-input-bar">
        <textarea
          v-model="inputText"
          class="chat-input"
          :placeholder="currentSessionId ? '基于知识库提问...' : '输入问题开始新对话...'"
          rows="1"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>
        <button
          class="btn btn-primary btn-send"
          @click="sendMessage"
          :disabled="!inputText.trim() || streaming"
        >
          {{ streaming ? '检索中' : '发送' }}
        </button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGetHistoryList, apiGetHistoryBySessionId } from '@/api'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}

const userStore = useUserStore()
const sessions = ref<string[]>([])
const currentSessionId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const loadingSessions = ref(false)
const chatContainer = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  })
}

function formatContent(text: string): string {
  if (!text) return ''
  // 先处理 HTML 特殊字符（防止 XSS）
  let formatted = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
  
  // 处理换行符：统一转换为 <br>
  // 先统一 \r\n 和 \r 为 \n，再将 \n 转换为 <br>
  formatted = formatted
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\n/g, '<br>')
  
  return formatted
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const res = await apiGetHistoryList(userId, 'rag')
    if (res.code === 200 && res.data) {
      sessions.value = Array.isArray(res.data) ? (res.data as string[]) : []
    }
  } catch (e) {
    console.error('加载会话列表失败', e)
  } finally {
    loadingSessions.value = false
  }
}

async function selectSession(sessionId: string) {
  currentSessionId.value = sessionId
  messages.value = []
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const res = await apiGetHistoryBySessionId(sessionId, userId, 'rag')
    if (res.code === 200 && res.data) {
      const list = Array.isArray(res.data) ? res.data : []
      messages.value = list.map((m: any) => ({
        role: m.role === 'user' ? 'user' : 'assistant',
        content: m.content || '',
        timestamp: Date.now()
      }))
      scrollToBottom()
    }
  } catch (e) {
    console.error('加载历史消息失败', e)
  }
}

function newSession() {
  currentSessionId.value = null
  messages.value = []
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  if (!currentSessionId.value) {
    currentSessionId.value = `rag_${Date.now()}`
  }

  messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
  inputText.value = ''
  streaming.value = true
  scrollToBottom()

  const assistantMsg: ChatMessage = { role: 'assistant', content: '', timestamp: Date.now() }
  messages.value.push(assistantMsg)

  try {
    const token = localStorage.getItem('token') || ''
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const url = `/api/ai/rag-query?query=${encodeURIComponent(text)}&sessionId=${currentSessionId.value}&userId=${userId}`

    const response = await fetch(url, { headers: { token } })
    if (!response.ok) throw new Error('请求失败')

    const reader = response.body?.getReader()
    if (!reader) throw new Error('无法读取响应流')

    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      const lines = chunk.split('\n')
      for (const line of lines) {
        // 解析 SSE 格式: "data:xxx" 或 "data: xxx"
        if (line.startsWith('data:')) {
          // 提取 data: 后面的内容，保留原始内容（包括换行）
          const content = line.substring(5)
          if (content && content.trim() && content.trim() !== '[DONE]') {
            // 不再 trim，保留原始内容
            assistantMsg.content += content
            messages.value = [...messages.value]
            scrollToBottom()
          }
        } else if (line.trim()) {
          // 非 SSE 格式的行也处理
          assistantMsg.content += line
          messages.value = [...messages.value]
          scrollToBottom()
        }
      }
    }

    if (!assistantMsg.content.trim()) {
      assistantMsg.content = '根据现有知识库信息，无法回答该问题。'
    }

    await loadSessions()
  } catch (e: unknown) {
    assistantMsg.content = `请求出错：${(e as Error).message}`
  } finally {
    streaming.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.chat-layout { display: flex; height: calc(100vh - 120px); gap: 0; }
.chat-sidebar { width: 260px; flex-shrink: 0; border-right: 1px solid var(--border-color); display: flex; flex-direction: column; background: var(--card-bg); }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; padding: 16px; border-bottom: 1px solid var(--border-color); }
.sidebar-header h3 { font-size: 15px; font-weight: 600; color: var(--text-primary); margin: 0; }
.sidebar-loading { display: flex; justify-content: center; padding: 32px; }
.sidebar-empty { padding: 24px 16px; text-align: center; color: var(--text-muted); font-size: 13px; }
.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 10px; cursor: pointer; transition: all 0.2s; margin-bottom: 4px; }
.session-item:hover { background: rgba(255,255,255,0.06); }
.session-item.active { background: var(--accent-light); border: 1px solid var(--accent-border); }
.session-item-icon { font-size: 18px; }
.session-item-text { flex: 1; overflow: hidden; }
.session-item-title { font-size: 13px; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item-id { font-size: 11px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.chat-main { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.chat-container { flex: 1; overflow-y: auto; padding: 16px 24px; min-height: 0; }
.chat-welcome { display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); height: 100%; }
.chat-welcome-avatar { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; margin-bottom: 16px; box-shadow: 0 4px 24px rgba(0,0,0,0.3); }
.chat-welcome h3 { font-size: 20px; color: var(--text-primary); margin: 0 0 8px; }
.chat-welcome p { margin: 0; }

.chat-container { flex: 1; overflow-y: auto; padding: 16px 24px; }
.message { display: flex; gap: 12px; margin-bottom: 20px; max-width: 80%; }
.message.user { margin-left: auto; flex-direction: row-reverse; }
.message-avatar { font-size: 32px; flex-shrink: 0; }
.message-avatar-img { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.message-bubble { padding: 12px 16px; border-radius: 16px; max-width: 100%; }
.message.assistant .message-bubble { background: var(--card-bg); border: 1px solid var(--border-color); }
.message.user .message-bubble { background: var(--accent); color: #fff; }
.message-content { font-size: 14px; line-height: 1.7; word-break: break-word; }
.message.user .message-content { color: #fff; }
.message-time { font-size: 11px; color: var(--text-muted); margin-top: 4px; }
.message.user .message-time { color: rgba(255,255,255,0.7); }
.typing-indicator { color: var(--text-muted); font-style: italic; }

.chat-input-bar { display: flex; gap: 12px; padding: 16px 24px; border-top: 1px solid var(--border-color); align-items: flex-end; }
.chat-input { flex: 1; resize: none; border: 1px solid var(--border-color); border-radius: 12px; padding: 12px 16px; font-size: 14px; background: var(--card-bg); color: var(--text-primary); font-family: inherit; outline: none; }
.chat-input:focus { border-color: var(--accent); }
.btn-send { height: 44px; padding: 0 24px; flex-shrink: 0; }
</style>
