<template>
  <div class="chat-layout">
    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
      <div class="confirm-modal">
        <h3>确认删除</h3>
        <p>确定删除该会话吗？删除后无法恢复。</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmDelete">确定删除</button>
        </div>
      </div>
    </div>

    <!-- 删除消息确认弹窗 -->
    <div v-if="showDeleteMessagesConfirm" class="modal-overlay" @click.self="showDeleteMessagesConfirm = false">
      <div class="confirm-modal">
        <h3>确认删除消息</h3>
        <p>确定删除选中的 {{ selectedMessageIndices.size }} 条消息吗？删除后无法恢复。</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showDeleteMessagesConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmDeleteMessages">确定删除</button>
        </div>
      </div>
    </div>

    <!-- 超限提示弹窗 -->
    <div v-if="showLimitModal" class="modal-overlay" @click.self="showLimitModal = false">
      <div class="confirm-modal">
        <h3>提示</h3>
        <p>{{ limitMessage }}</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showLimitModal = false">知道了</button>
          <button class="btn btn-primary" @click="newSessionFromLimit">新建会话</button>
        </div>
      </div>
    </div>

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="showToast" class="toast" :class="toastType">
        <svg v-if="toastType === 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
          <polyline points="22 4 12 14.01 9 11.01"></polyline>
        </svg>
        <span>{{ toastMessage }}</span>
      </div>
    </Transition>

    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <div class="header-icon rag">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
          </svg>
        </div>
        <h3>知识库问答</h3>
        <button class="btn btn-sm btn-primary new-chat-btn" @click="newSession">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
        </button>
      </div>
      <div v-if="loadingSessions" class="sidebar-loading"><div class="spinner"></div></div>
      <div v-else-if="sessions.length === 0" class="sidebar-empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
        </svg>
        <span>暂无历史对话</span>
      </div>
      <div v-else class="session-list">
        <div v-for="(s, idx) in sessions" :key="s" class="session-item" :class="{ active: currentSessionId === s }" @click="selectSession(s)">
          <div class="session-item-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
          <div class="session-item-text">
            <div class="session-item-title">会话 {{ sessions.length - idx }}</div>
            <div class="session-item-id">{{ s }}</div>
          </div>
          <button class="session-delete-btn" @click.stop="deleteSession(s)" title="删除会话">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
          </button>
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <div class="chat-container" ref="chatContainer">
        <div v-if="messages.length === 0 && !currentSessionId" class="chat-welcome">
          <div class="welcome-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              <line x1="8" y1="7" x2="16" y2="7"/>
              <line x1="8" y1="11" x2="16" y2="11"/>
              <line x1="8" y1="15" x2="12" y2="15"/>
            </svg>
          </div>
          <h3>知识库问答</h3>
          <p>基于上传的知识库进行智能问答</p>
          <div class="welcome-suggestions">
            <div class="suggestion-item" @click="inputText = '请帮我总结知识库中的主要内容'">
              <span>请帮我总结知识库中的主要内容</span>
            </div>
            <div class="suggestion-item" @click="inputText = '知识库中有哪些重要概念？'">
              <span>知识库中有哪些重要概念？</span>
            </div>
            <div class="suggestion-item" @click="inputText = '请解释一下这个知识点'">
              <span>请解释一下这个知识点</span>
            </div>
          </div>
        </div>

        <template v-if="currentSessionId">
          <!-- 选择模式操作栏 -->
          <div v-if="selectMode && messages.length > 0" class="message-actions-bar">
            <span class="select-hint">已选择 {{ selectedMessageIndices.size }} 条消息</span>
            <button class="action-btn cancel-btn" @click="cancelSelectMode">取消</button>
            <button 
              class="action-btn confirm-delete-btn" 
              :disabled="selectedMessageIndices.size === 0"
              @click="requestDeleteMessages"
            >
              确认删除
            </button>
          </div>

          <div 
            v-for="(msg, idx) in messages" 
            :key="idx" 
            class="message" 
            :class="[msg.role, { selected: selectedMessageIndices.has(idx), 'select-mode': selectMode }]"
          >
            <!-- 选择模式下的复选框 -->
            <div v-if="selectMode" class="message-checkbox" @click="toggleMessageSelection(idx)">
              <div class="checkbox" :class="{ checked: selectedMessageIndices.has(idx) }">
                <svg v-if="selectedMessageIndices.has(idx)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                  <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
              </div>
            </div>
            
            <div v-if="msg.role === 'assistant'" class="message-avatar ai">
              <img src="/ai-avatar.jpg" alt="AI" />
            </div>
            <div v-else class="message-avatar user">
              <img v-if="userStore.avatarUrl" :src="userStore.avatarUrl" alt="用户头像" class="user-avatar-img" />
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <div 
              class="message-bubble" 
              :class="{ selected: selectedMessageIndices.has(idx) }"
              @mouseenter="hoveredMessageIdx = idx"
              @mouseleave="hoveredMessageIdx = -1"
            >
              <div class="message-content" v-html="formatContent(msg.content)"></div>
              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
              <!-- 用户消息操作按钮 -->
              <div 
                v-if="!selectMode && hoveredMessageIdx === idx && msg.role === 'user'" 
                class="message-actions"
              >
                <button class="msg-action-btn" @click="copyMessage(msg.content)" title="复制">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                  </svg>
                </button>
                <button class="msg-action-btn delete" @click="enterSelectModeFromMessage(idx)" title="删除">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                  </svg>
                </button>
              </div>
              <!-- AI消息操作按钮 -->
              <div 
                v-if="!selectMode && hoveredMessageIdx === idx && msg.role === 'assistant'" 
                class="message-actions"
              >
                <button class="msg-action-btn" @click="copyMessage(msg.content)" title="复制">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                  </svg>
                </button>
                <button class="msg-action-btn" @click="regenerateMessage(idx)" title="重新生成">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 4v6h6"></path>
                    <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </template>

        <div v-if="streaming" class="message assistant">
          <div class="message-avatar ai">
            <img src="/ai-avatar.jpg" alt="AI" />
          </div>
          <div class="message-bubble">
            <div class="message-content">
              <span class="typing-indicator">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-bar">
        <div class="input-wrapper">
          <textarea
            ref="textareaRef"
            v-model="inputText"
            class="chat-input"
            :placeholder="currentSessionId ? '基于知识库提问...' : '输入问题开始新对话...'"
            rows="1"
            @keydown="handleKeydown"
            @input="autoResize"
          ></textarea>
          <button
            class="send-btn"
            @click="sendMessage"
            :disabled="!inputText.trim() || streaming"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGetHistoryList, apiGetHistoryBySessionId, apiDeleteHistory, apiGetUsage, apiDeleteMessages } from '@/api'

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
const showDeleteConfirm = ref(false)
const pendingDeleteSessionId = ref<string | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// 使用次数相关
const showLimitModal = ref(false)
const limitMessage = ref('')
const remainingUsage = ref<number>(-1)
const hasOwnApiKey = ref(false)

// 选择模式相关
const selectMode = ref(false)
const selectedMessageIndices = ref<Set<number>>(new Set())
const showDeleteMessagesConfirm = ref(false)
const hoveredMessageIdx = ref(-1)

// Toast 提示
const showToast = ref(false)
const toastMessage = ref('')
const toastType = ref<'success' | 'error'>('success')

function showToastMessage(message: string, type: 'success' | 'error' = 'success') {
  toastMessage.value = message
  toastType.value = type
  showToast.value = true
  setTimeout(() => {
    showToast.value = false
  }, 2000)
}

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  })
}

function autoResize() {
  const textarea = textareaRef.value
  if (!textarea) return
  textarea.style.height = 'auto'
  const lineHeight = 21
  const maxHeight = lineHeight * 4
  const newHeight = Math.min(textarea.scrollHeight, maxHeight)
  textarea.style.height = newHeight + 'px'
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    if (e.shiftKey || e.altKey) {
      e.preventDefault()
      const textarea = textareaRef.value
      if (!textarea) return
      const start = textarea.selectionStart
      const end = textarea.selectionEnd
      inputText.value = inputText.value.substring(0, start) + '\n' + inputText.value.substring(end)
      nextTick(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 1
        autoResize()
      })
    } else {
      e.preventDefault()
      sendMessage()
    }
  }
}

watch(inputText, () => {
  nextTick(() => {
    autoResize()
  })
})

function formatContent(text: string): string {
  if (!text) return ''
  let formatted = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
  
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
  cancelSelectMode()
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const res = await apiGetHistoryBySessionId(sessionId, userId, 'rag')
    if (res.code === 200 && res.data) {
      const list = Array.isArray(res.data) ? res.data : []
      messages.value = list.map((m) => ({
        role: m.role === 'user' ? 'user' : 'assistant',
        content: m.content || '',
        timestamp: m.timestamp || Date.now()
      }))
      scrollToBottom()
    }
  } catch (e) {
    console.error('加载历史消息失败', e)
  }
}

function newSession() {
  if (sessions.value.length >= 20) {
    limitMessage.value = '会话数量已达上限(20个)，请删除一个会话后再创建新会话。'
    showLimitModal.value = true
    return
  }
  currentSessionId.value = null
  messages.value = []
  cancelSelectMode()
}

function newSessionFromLimit() {
  showLimitModal.value = false
  newSession()
}

async function checkUsage() {
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const res = await apiGetUsage(userId, 'rag')
    if (res.code === 200 && res.data) {
      hasOwnApiKey.value = res.data.hasOwnApiKey
      remainingUsage.value = res.data.remaining
    }
  } catch (e) {
    console.error('获取使用次数失败', e)
  }
}

async function deleteSession(sessionId: string) {
  pendingDeleteSessionId.value = sessionId
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (!pendingDeleteSessionId.value) return
  const sessionId = pendingDeleteSessionId.value
  showDeleteConfirm.value = false
  
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const res = await apiDeleteHistory(sessionId, userId, 'rag')
    if (res.code === 200) {
      sessions.value = sessions.value.filter(s => s !== sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
        messages.value = []
        cancelSelectMode()
      }
    }
  } catch (e) {
    console.error('删除会话失败', e)
  } finally {
    pendingDeleteSessionId.value = null
  }
}

// 选择模式相关方法
function enterSelectModeFromMessage(userMsgIdx: number) {
  selectMode.value = true
  selectedMessageIndices.value = new Set()
  toggleMessageSelection(userMsgIdx)
}

function cancelSelectMode() {
  selectMode.value = false
  selectedMessageIndices.value = new Set()
}

function toggleMessageSelection(idx: number) {
  const newSet = new Set(selectedMessageIndices.value)
  const msg = messages.value[idx]
  
  if (msg.role === 'user') {
    if (newSet.has(idx)) {
      newSet.delete(idx)
      if (idx + 1 < messages.value.length && messages.value[idx + 1].role === 'assistant') {
        newSet.delete(idx + 1)
      }
    } else {
      newSet.add(idx)
      if (idx + 1 < messages.value.length && messages.value[idx + 1].role === 'assistant') {
        newSet.add(idx + 1)
      }
    }
  } else {
    if (newSet.has(idx)) {
      newSet.delete(idx)
      if (idx - 1 >= 0 && messages.value[idx - 1].role === 'user') {
        newSet.delete(idx - 1)
      }
    } else {
      newSet.add(idx)
      if (idx - 1 >= 0 && messages.value[idx - 1].role === 'user') {
        newSet.add(idx - 1)
      }
    }
  }
  
  selectedMessageIndices.value = newSet
}

function requestDeleteMessages() {
  if (selectedMessageIndices.value.size === 0) return
  showDeleteMessagesConfirm.value = true
}

function copyMessage(content: string) {
  navigator.clipboard.writeText(content).then(() => {
    showToastMessage('复制成功')
  }).catch(err => {
    console.error('复制失败', err)
    showToastMessage('复制失败', 'error')
  })
}

async function regenerateMessage(aiMsgIdx: number) {
  if (streaming.value) return
  
  const userMsgIdx = aiMsgIdx - 1
  if (userMsgIdx < 0 || messages.value[userMsgIdx].role !== 'user') {
    showToastMessage('无法重新生成', 'error')
    return
  }
  
  const userMsg = messages.value[userMsgIdx]
  
  messages.value.splice(aiMsgIdx, 1)
  
  const text = userMsg.content
  streaming.value = true
  scrollToBottom()

  const assistantMsg: ChatMessage = { role: 'assistant', content: '', timestamp: Date.now() }
  messages.value.push(assistantMsg)

  try {
    const token = localStorage.getItem('token') || ''
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')

    const response = await fetch(`/api/ai/rag-query?userId=${userId}`, {
      method: 'POST',
      headers: {
        'token': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: text,
        sessionId: currentSessionId.value
      })
    })

    if (!response.ok) {
      throw new Error('请求失败')
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('无法读取响应流')

    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.substring(5).trim()
          if (jsonStr === '[DONE]') continue
          if (!jsonStr) continue
          try {
            const content = JSON.parse(jsonStr)
            assistantMsg.content += content
          } catch (e) {
            assistantMsg.content += jsonStr
          }
          messages.value = [...messages.value]
          scrollToBottom()
        }
      }
    }

    if (!assistantMsg.content.trim()) {
      assistantMsg.content = '根据现有知识库信息，无法回答该问题。'
    }
    
    showToastMessage('重新生成成功')
  } catch (e: unknown) {
    const index = messages.value.indexOf(assistantMsg)
    if (index !== -1) {
      messages.value[index] = { ...assistantMsg, content: `请求出错：${(e as Error).message}` }
    }
    showToastMessage('重新生成失败', 'error')
  } finally {
    streaming.value = false
    scrollToBottom()
  }
}

async function confirmDeleteMessages() {
  if (selectedMessageIndices.value.size === 0) return
  
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const indices = Array.from(selectedMessageIndices.value)
    const res = await apiDeleteMessages(currentSessionId.value!, userId, 'rag', indices)
    
    if (res.code === 200) {
      messages.value = messages.value.filter((_, idx) => !selectedMessageIndices.value.has(idx))
      cancelSelectMode()
      showToastMessage('删除成功')
    }
  } catch (e) {
    console.error('删除消息失败', e)
    showToastMessage('删除失败', 'error')
  } finally {
    showDeleteMessagesConfirm.value = false
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  // 检查使用次数
  if (!hasOwnApiKey.value && remainingUsage.value === 0) {
    limitMessage.value = '今日免费问答次数已用完，请切换使用您自己的模型或明天再试。'
    showLimitModal.value = true
    return
  }

  if (!currentSessionId.value) {
    currentSessionId.value = `rag_${Date.now()}`
  }

  // 退出选择模式
  cancelSelectMode()

  messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
  inputText.value = ''
  streaming.value = true
  scrollToBottom()

  const assistantMsg: ChatMessage = { role: 'assistant', content: '', timestamp: Date.now() }
  messages.value.push(assistantMsg)

  try {
    const token = localStorage.getItem('token') || ''
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')

    const response = await fetch(`/api/ai/rag-query?userId=${userId}`, {
      method: 'POST',
      headers: {
        'token': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: text,
        sessionId: currentSessionId.value
      })
    })
    if (!response.ok) {
      const errorText = await response.text()
      if (errorText.includes('今日使用次数已达上限')) {
        throw new Error('今日免费问答次数已用完，请切换使用您自己的模型或明天再试')
      }
      if (errorText.includes('API') || errorText.includes('key') || errorText.includes('Key') || errorText.includes('model') || errorText.includes('Model') || errorText.includes('401') || errorText.includes('403') || errorText.includes('invalid')) {
        throw new Error('上传的API Key或模型名称有误，请核验')
      }
      throw new Error('请求失败')
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('无法读取响应流')

    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const jsonStr = line.substring(5).trim()
          if (jsonStr === '[DONE]') continue
          if (!jsonStr) continue
          try {
            const content = JSON.parse(jsonStr)
            assistantMsg.content += content
          } catch (e) {
            assistantMsg.content += jsonStr
          }
          messages.value = [...messages.value]
          scrollToBottom()
        }
      }
    }

    if (!assistantMsg.content.trim()) {
      assistantMsg.content = '根据现有知识库信息，无法回答该问题。'
    }

    await loadSessions()
    await checkUsage()
  } catch (e: unknown) {
    if (!assistantMsg.content.trim()) {
      assistantMsg.content = `请求出错：${(e as Error).message}`
    }
  } finally {
    streaming.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  loadSessions()
  checkUsage()
})
</script>

<style scoped>
.chat-layout { display: flex; height: calc(100vh - 120px); gap: 0; }
.chat-sidebar { width: 280px; flex-shrink: 0; border-right: 1px solid var(--border-glass); display: flex; flex-direction: column; background: var(--card-bg); }

.sidebar-header { display: flex; align-items: center; gap: 12px; padding: 20px; border-bottom: 1px solid var(--border-glass); }
.header-icon { width: 36px; height: 36px; background: var(--accent-gradient); border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.header-icon.rag { background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%); }
.header-icon svg { width: 18px; height: 18px; color: #fff; }
.sidebar-header h3 { flex: 1; font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0; }
.new-chat-btn { width: 36px; height: 36px; padding: 0; border-radius: 10px; }
.new-chat-btn svg { width: 18px; height: 18px; }

.sidebar-loading { display: flex; justify-content: center; padding: 32px; }
.sidebar-empty { padding: 40px 20px; text-align: center; color: var(--text-muted); font-size: 13px; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.sidebar-empty svg { width: 40px; height: 40px; opacity: 0.3; }

.session-list { flex: 1; overflow-y: auto; padding: 12px; }
.session-item { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 12px; cursor: pointer; transition: all 0.2s; margin-bottom: 4px; }
.session-item:hover { background: rgba(255,255,255,0.04); }
.session-item.active { background: var(--tertiary-light); border: 1px solid rgba(6,182,212,0.3); }
.session-item-icon { width: 32px; height: 32px; background: var(--bg-glass); border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.session-item-icon svg { width: 16px; height: 16px; color: var(--text-muted); }
.session-item.active .session-item-icon { background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%); }
.session-item.active .session-item-icon svg { color: #fff; }
.session-item-text { flex: 1; overflow: hidden; }
.session-item-title { font-size: 13px; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item-id { font-size: 11px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-delete-btn { width: 28px; height: 28px; border: none; background: transparent; border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center; opacity: 0; transition: all 0.2s; flex-shrink: 0; }
.session-delete-btn svg { width: 14px; height: 14px; color: var(--text-muted); }
.session-delete-btn:hover { background: rgba(239,68,68,0.1); }
.session-delete-btn:hover svg { color: #ef4444; }
.session-item:hover .session-delete-btn { opacity: 1; }

.chat-main { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.chat-container { flex: 1; overflow-y: auto; padding: 24px; min-height: 0; }

.chat-welcome { display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); height: 100%; padding: 40px; }
.welcome-icon { width: 100px; height: 100px; border-radius: 50%; background: linear-gradient(135deg, rgba(6,182,212,0.2) 0%, rgba(6,182,212,0.1) 100%); display: flex; align-items: center; justify-content: center; margin-bottom: 20px; }
.welcome-icon svg { width: 48px; height: 48px; color: var(--tertiary); }
.chat-welcome h3 { font-size: 22px; color: var(--text-primary); margin: 0 0 8px; font-weight: 600; }
.chat-welcome p { margin: 0 0 24px; }

.welcome-suggestions { display: flex; flex-direction: column; gap: 10px; width: 100%; max-width: 400px; }
.suggestion-item { padding: 14px 18px; background: var(--bg-glass); border: 1px solid var(--border-glass); border-radius: 12px; cursor: pointer; transition: all 0.3s ease; font-size: 14px; color: var(--text-secondary); }
.suggestion-item:hover { background: var(--tertiary-light); border-color: rgba(6,182,212,0.3); color: var(--tertiary); transform: translateX(4px); }

/* 消息操作栏 */
.message-actions-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 8px 12px; background: var(--bg-glass); border-radius: 12px; border: 1px solid var(--border-glass); }
.action-btn { display: flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 8px; border: none; font-size: 13px; cursor: pointer; transition: all 0.2s; }
.action-btn svg { width: 16px; height: 16px; }
.cancel-btn { background: var(--bg-glass); color: var(--text-secondary); border: 1px solid var(--border-glass); }
.cancel-btn:hover { background: rgba(255,255,255,0.1); }
.confirm-delete-btn { background: #ef4444; color: #fff; }
.confirm-delete-btn:hover:not(:disabled) { background: #dc2626; }
.confirm-delete-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.select-hint { flex: 1; font-size: 13px; color: var(--text-muted); }

/* 消息选择复选框 */
.message-checkbox { display: flex; align-items: center; padding-right: 8px; cursor: pointer; }
.checkbox { width: 20px; height: 20px; border: 2px solid var(--border-glass); border-radius: 6px; display: flex; align-items: center; justify-content: center; transition: all 0.2s; }
.checkbox.checked { background: var(--tertiary); border-color: var(--tertiary); }
.checkbox svg { width: 14px; height: 14px; color: #fff; }

/* 消息操作按钮栏 */
.message-actions { 
  display: flex; 
  gap: 4px; 
  margin-top: 8px; 
  justify-content: flex-end;
}
.msg-action-btn { 
  width: 28px; 
  height: 28px; 
  border: none; 
  background: transparent; 
  border-radius: 6px; 
  cursor: pointer; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  transition: all 0.2s; 
  opacity: 0.6;
}
.msg-action-btn svg { width: 16px; height: 16px; color: var(--text-muted); }
.msg-action-btn:hover { background: var(--bg-glass); opacity: 1; }
.msg-action-btn:hover svg { color: var(--text-primary); }
.msg-action-btn.delete:hover { background: rgba(239,68,68,0.1); }
.msg-action-btn.delete:hover svg { color: #ef4444; }
.message.user .msg-action-btn svg { color: rgba(255,255,255,0.7); }
.message.user .msg-action-btn:hover { background: rgba(255,255,255,0.2); }
.message.user .msg-action-btn:hover svg { color: #fff; }

.message { display: flex; gap: 14px; margin-bottom: 24px; max-width: 85%; position: relative; }
.message.user { margin-left: auto; flex-direction: row-reverse; }
.message-avatar { width: 38px; height: 38px; border-radius: 12px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.message-avatar.ai { padding: 2px; background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%); }
.message-avatar.ai img { width: 100%; height: 100%; border-radius: 10px; object-fit: cover; }
.message-avatar.user { background: var(--secondary); }
.message-avatar.user svg { width: 20px; height: 20px; color: #fff; }
.message-avatar.user img.user-avatar-img { width: 100%; height: 100%; object-fit: cover; border-radius: 12px; }
.message-bubble { padding: 14px 18px; border-radius: 18px; max-width: 100%; transition: all 0.2s; }
.message-bubble.selected { box-shadow: 0 0 0 2px var(--tertiary); }
.message.assistant .message-bubble { background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 18px 18px 18px 4px; }
.message.user .message-bubble { background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%); color: #fff; border-radius: 18px 18px 4px 18px; }
.message-content { font-size: 14px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.message.user .message-content { color: #fff; }
.message-content code { background: rgba(0,0,0,0.2); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.message-time { font-size: 11px; color: var(--text-muted); margin-top: 6px; }
.message.user .message-time { color: rgba(255,255,255,0.6); }

.message.select-mode { cursor: pointer; }

.typing-indicator { display: flex; gap: 4px; padding: 4px 0; }
.typing-indicator .dot { width: 8px; height: 8px; background: var(--tertiary); border-radius: 50%; animation: bounce 1.4s infinite ease-in-out both; }
.typing-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator .dot:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; } 40% { transform: scale(1); opacity: 1; } }

.chat-input-bar { padding: 20px 24px; border-top: 1px solid var(--border-glass); }
.input-wrapper { display: flex; gap: 12px; align-items: flex-end; background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 16px; padding: 8px 8px 8px 16px; transition: all 0.3s ease; }
.input-wrapper:focus-within { border-color: var(--tertiary); box-shadow: 0 0 0 3px rgba(6,182,212,0.1); }
.chat-input { flex: 1; resize: none; border: none; background: transparent; font-size: 14px; color: var(--text-primary); font-family: inherit; outline: none; line-height: 21px; min-height: 21px; overflow-y: auto; }
.chat-input::placeholder { color: var(--text-muted); }
.send-btn { width: 40px; height: 40px; border-radius: 12px; border: none; background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.3s ease; flex-shrink: 0; }
.send-btn svg { width: 18px; height: 18px; }
.send-btn:hover:not(:disabled) { transform: scale(1.05); box-shadow: 0 4px 16px rgba(6,182,212,0.4); }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; backdrop-filter: blur(4px); }
.confirm-modal { background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 16px; padding: 24px; min-width: 320px; text-align: center; }
.confirm-modal h3 { margin: 0 0 12px; font-size: 18px; color: var(--text-primary); }
.confirm-modal p { margin: 0 0 20px; color: var(--text-secondary); font-size: 14px; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }

/* Toast 提示 */
.toast {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--card-bg);
  border: 1px solid var(--border-glass);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
  z-index: 2000;
  font-size: 14px;
  color: var(--text-primary);
}
.toast svg { width: 18px; height: 18px; }
.toast.success { border-color: rgba(34,197,94,0.3); }
.toast.success svg { color: #22c55e; }
.toast.error { border-color: rgba(239,68,68,0.3); }
.toast.error svg { color: #ef4444; }

/* Toast 动画 */
.toast-enter-active { animation: toastIn 0.3s ease; }
.toast-leave-active { animation: toastOut 0.3s ease; }
@keyframes toastIn {
  from { opacity: 0; transform: translateX(-50%) translateY(-20px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
@keyframes toastOut {
  from { opacity: 1; transform: translateX(-50%) translateY(0); }
  to { opacity: 0; transform: translateX(-50%) translateY(-20px); }
}

@media (max-width: 768px) {
  .chat-sidebar { width: 240px; }
  .welcome-suggestions { max-width: 300px; }
}
</style>
