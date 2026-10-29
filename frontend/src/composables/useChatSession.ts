import { ref, nextTick, onMounted, onScopeDispose, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { authHeaders } from '@/api/request'
import {
  apiGetHistoryList,
  apiGetHistoryBySessionId,
  apiDeleteHistory,
  apiGetUsage,
  apiDeleteMessages
} from '@/api'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  thinking?: string
  thinkingExpanded?: boolean
  timestamp: number
}

export interface ChatSessionOptions {
  /** 历史记录接口的业务类型 */
  type: 'rag' | 'chat'
  /** 新建会话时 sessionId 的前缀 */
  sessionIdPrefix: string
  /** 流式问答接口地址（不含 userId 查询参数） */
  endpoint: string
  /** 模型没有返回任何正文时的兜底文案 */
  emptyReply: string
  /** 免费次数用尽时的提示文案 */
  quotaMessage: string
}

/** 流式渲染的合并刷新间隔（毫秒），避免每收到一个 token 就重渲染整表 */
const STREAM_FLUSH_INTERVAL = 60

// 处理流式chunk：正文为纯字符串，思考内容为 {thinking:true,text:...} 对象
function appendStreamChunk(msg: ChatMessage, jsonStr: string) {
  try {
    const content = JSON.parse(jsonStr)
    if (typeof content === 'string') {
      msg.content += content
      // 正文开始输出后自动收起思考区块
      if (msg.thinking && msg.thinkingExpanded) msg.thinkingExpanded = false
    } else if (content && typeof content === 'object' && content.thinking) {
      msg.thinking = (msg.thinking || '') + content.text
      if (msg.thinkingExpanded === undefined) msg.thinkingExpanded = true
    }
  } catch (e) {
    msg.content += jsonStr
  }
}

export function useChatSession(options: ChatSessionOptions) {
  const userStore = useUserStore()
  const toast = useToast()

  const sessions = ref<string[]>([])
  const currentSessionId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const inputText = ref('')
  const streaming = ref(false)
  const loadingSessions = ref(false)
  const chatContainer = ref<HTMLElement | null>(null)
  const textareaRef = ref<HTMLTextAreaElement | null>(null)

  const showDeleteConfirm = ref(false)
  const pendingDeleteSessionId = ref<string | null>(null)

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

  // 会话切换的竞态守卫：只接受最后一次请求的响应
  let sessionRequestSeq = 0
  // 当前流式请求的控制器，用于主动中止
  let activeController: AbortController | null = null

  /** 创建节流刷新器：流式期间按固定间隔合并刷新，结束后强制刷新一次 */
  function createStreamFlusher() {
    let timer: ReturnType<typeof setTimeout> | null = null
    let lastFlush = 0

    function flush() {
      timer = null
      lastFlush = Date.now()
      // 重新赋值数组以触发视图更新（assistantMsg 是原始对象，原地修改不会响应）
      messages.value = [...messages.value]
      scrollToBottom()
    }

    function schedule() {
      if (timer !== null) return
      const wait = Math.max(0, STREAM_FLUSH_INTERVAL - (Date.now() - lastFlush))
      timer = setTimeout(flush, wait)
    }

    function stop() {
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
    }

    return {
      schedule,
      stop,
      flushNow() {
        stop()
        flush()
      }
    }
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

  function formatTime(ts: number): string {
    const d = new Date(ts)
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hour = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    return `${month}-${day} ${hour}:${min}`
  }

  async function loadSessions() {
    loadingSessions.value = true
    try {
      const res = await apiGetHistoryList(options.type)
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
    // 切换会话时中止尚未结束的流式请求
    stopGenerating()
    const seq = ++sessionRequestSeq
    currentSessionId.value = sessionId
    messages.value = []
    cancelSelectMode()
    try {
      const res = await apiGetHistoryBySessionId(sessionId, options.type)
      // 期间又切换了会话，丢弃过期响应
      if (seq !== sessionRequestSeq || currentSessionId.value !== sessionId) return
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
    stopGenerating()
    // 让尚未返回的历史请求失效
    sessionRequestSeq++
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
      const res = await apiGetUsage(options.type)
      if (res.code === 200 && res.data) {
        hasOwnApiKey.value = res.data.hasOwnApiKey
        remainingUsage.value = res.data.remaining
      }
    } catch (e) {
      console.error('获取使用次数失败', e)
    }
  }

  function deleteSession(sessionId: string) {
    pendingDeleteSessionId.value = sessionId
    showDeleteConfirm.value = true
  }

  async function confirmDelete() {
    if (!pendingDeleteSessionId.value) return
    const sessionId = pendingDeleteSessionId.value
    showDeleteConfirm.value = false

    try {
      const res = await apiDeleteHistory(sessionId, options.type)
      if (res.code === 200) {
        sessions.value = sessions.value.filter(s => s !== sessionId)
        if (currentSessionId.value === sessionId) {
          stopGenerating()
          sessionRequestSeq++
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
      toast.success('复制成功', 2000)
    }).catch(err => {
      console.error('复制失败', err)
      toast.error('复制失败', 2000)
    })
  }

  /** 主动中止当前流式请求（用户点击停止 / 切换会话 / 组件卸载） */
  function stopGenerating() {
    if (!activeController) return
    activeController.abort()
    activeController = null
  }

  /**
   * 发起流式问答并写入 assistantMsg
   * 返回 done / aborted / failed，aborted 表示被主动中止（不提示错误）
   */
  async function streamQuery(assistantMsg: ChatMessage, query: string): Promise<'done' | 'aborted' | 'failed'> {
    const controller = new AbortController()
    activeController = controller
    const flusher = createStreamFlusher()

    try {
      const userId = String(userStore.userId || localStorage.getItem('userId') || '')

      const response = await fetch(`${options.endpoint}?userId=${userId}`, {
        method: 'POST',
        headers: {
          ...authHeaders(),
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          query,
          sessionId: currentSessionId.value
        }),
        signal: controller.signal
      })
      if (!response.ok) {
        const errorText = await response.text()
        const quotaError = options.quotaMessage.replace(/。$/, '')
        if (errorText.includes('今日使用次数已达上限')) {
          throw new Error(quotaError)
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
            appendStreamChunk(assistantMsg, jsonStr)
            flusher.schedule()
          }
        }
      }

      flusher.stop()
      if (!assistantMsg.content.trim()) {
        assistantMsg.content = options.emptyReply
      }
      return 'done'
    } catch (e: unknown) {
      if (controller.signal.aborted) return 'aborted'
      const index = messages.value.indexOf(assistantMsg)
      if (index !== -1 && !assistantMsg.content.trim()) {
        messages.value[index] = { ...assistantMsg, content: `请求出错：${(e as Error).message}` }
      }
      return 'failed'
    } finally {
      // 流结束（正常 / 中止 / 出错）都强制刷新一次，保证内容完整
      flusher.flushNow()
      if (activeController === controller) activeController = null
      streaming.value = false
    }
  }

  async function regenerateMessage(aiMsgIdx: number) {
    if (streaming.value) return

    const userMsgIdx = aiMsgIdx - 1
    if (userMsgIdx < 0 || messages.value[userMsgIdx].role !== 'user') {
      toast.error('无法重新生成', 2000)
      return
    }

    const userMsg = messages.value[userMsgIdx]

    messages.value.splice(aiMsgIdx, 1)

    const text = userMsg.content
    streaming.value = true
    scrollToBottom()

    const assistantMsg: ChatMessage = { role: 'assistant', content: '', timestamp: Date.now() }
    messages.value.push(assistantMsg)

    const result = await streamQuery(assistantMsg, text)
    if (result === 'done') {
      toast.success('重新生成成功', 2000)
    } else if (result === 'failed') {
      toast.error('重新生成失败', 2000)
    }
  }

  async function confirmDeleteMessages() {
    if (selectedMessageIndices.value.size === 0) return

    try {
      const indices = Array.from(selectedMessageIndices.value)
      const res = await apiDeleteMessages(currentSessionId.value!, options.type, indices)

      if (res.code === 200) {
        messages.value = messages.value.filter((_, idx) => !selectedMessageIndices.value.has(idx))
        cancelSelectMode()
        toast.success('删除成功', 2000)
      }
    } catch (e) {
      console.error('删除消息失败', e)
      toast.error('删除失败', 2000)
    } finally {
      showDeleteMessagesConfirm.value = false
    }
  }

  async function sendMessage() {
    const text = inputText.value.trim()
    if (!text || streaming.value) return

    // 检查使用次数
    if (!hasOwnApiKey.value && remainingUsage.value === 0) {
      limitMessage.value = options.quotaMessage
      showLimitModal.value = true
      return
    }

    if (!currentSessionId.value) {
      currentSessionId.value = `${options.sessionIdPrefix}${Date.now()}`
    }

    // 退出选择模式
    cancelSelectMode()

    messages.value.push({ role: 'user', content: text, timestamp: Date.now() })
    inputText.value = ''
    streaming.value = true
    scrollToBottom()

    const assistantMsg: ChatMessage = { role: 'assistant', content: '', timestamp: Date.now() }
    messages.value.push(assistantMsg)

    const result = await streamQuery(assistantMsg, text)
    if (result !== 'done') return

    await loadSessions()
    await checkUsage()
  }

  onMounted(() => {
    loadSessions()
    checkUsage()
  })

  // 组件卸载时中止未完成的流式请求
  onScopeDispose(() => {
    stopGenerating()
  })

  return {
    sessions,
    currentSessionId,
    messages,
    inputText,
    streaming,
    loadingSessions,
    chatContainer,
    textareaRef,
    showDeleteConfirm,
    showLimitModal,
    limitMessage,
    selectMode,
    selectedMessageIndices,
    showDeleteMessagesConfirm,
    hoveredMessageIdx,
    scrollToBottom,
    autoResize,
    handleKeydown,
    formatTime,
    loadSessions,
    selectSession,
    newSession,
    newSessionFromLimit,
    deleteSession,
    confirmDelete,
    enterSelectModeFromMessage,
    cancelSelectMode,
    toggleMessageSelection,
    requestDeleteMessages,
    copyMessage,
    regenerateMessage,
    confirmDeleteMessages,
    sendMessage,
    stopGenerating
  }
}
