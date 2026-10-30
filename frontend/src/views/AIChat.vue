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

    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <h3>AI 对话</h3>
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
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>暂无历史对话</span>
      </div>
      <div v-else class="session-list">
        <div v-for="(s, idx) in sessions" :key="s" class="session-item" :class="{ active: currentSessionId === s }" @click="selectSession(s)">
          <div class="session-item-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
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
          <div class="welcome-avatar">
            <img src="/ai-avatar.jpg" alt="AI" />
          </div>
          <h3>你好，我是小磊</h3>
          <p>选择一个历史会话或直接开始新对话</p>
          <div class="welcome-suggestions">
            <div class="suggestion-item" @click="inputText = '帮我解释一下什么是机器学习？'">
              <span>帮我解释一下什么是机器学习？</span>
            </div>
            <div class="suggestion-item" @click="inputText = '请给我讲一个有趣的故事'">
              <span>请给我讲一个有趣的故事</span>
            </div>
            <div class="suggestion-item" @click="inputText = '如何提高学习效率？'">
              <span>如何提高学习效率？</span>
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
              <div v-if="msg.thinking" class="thinking-block">
                <div class="thinking-header" @click="msg.thinkingExpanded = !msg.thinkingExpanded">
                  <svg class="thinking-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 18h6M10 22h4M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2z"></path>
                  </svg>
                  <span>深度思考</span>
                  <svg class="chevron" :class="{ collapsed: !msg.thinkingExpanded }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </div>
                <div v-show="msg.thinkingExpanded" class="thinking-content">{{ msg.thinking }}</div>
              </div>
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
            :placeholder="currentSessionId ? '输入你的问题...' : '输入问题开始新对话...'"
            rows="1"
            @keydown="handleKeydown"
            @input="autoResize"
          ></textarea>
          <button
            v-if="streaming"
            class="send-btn stop-btn"
            @click="stopGenerating"
            title="停止生成"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="6" y="6" width="12" height="12" rx="2"/>
            </svg>
          </button>
          <button
            v-else
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
import { useUserStore } from '@/stores/user'
import { useChatSession } from '@/composables/useChatSession'
import { marked } from 'marked'

// 与 RAGChat 的差异点：本页用 marked 渲染 Markdown
function formatContent(text: string): string {
  if (!text) return ''
  // 防XSS：先将HTML特殊字符转义再交给marked解析，
  // 阻止AI回复（可能被提问注入污染）中的<script>/<img onerror>等原生HTML经v-html执行
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return marked.parse(escaped) as string
}

const userStore = useUserStore()

const {
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
  autoResize,
  handleKeydown,
  formatTime,
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
} = useChatSession({
  type: 'chat',
  sessionIdPrefix: 'chat_',
  endpoint: '/api/ai/query',
  emptyReply: '抱歉，暂时无法回答。',
  quotaMessage: '今日免费对话次数已用完，请切换使用您自己的模型或明天再试。'
})
</script>

<style scoped>
.chat-layout { display: flex; height: calc(100vh - 120px); gap: 0; }
.chat-sidebar { width: 280px; flex-shrink: 0; border-right: 2px solid var(--ink); display: flex; flex-direction: column; background: var(--card-bg); }

.sidebar-header { display: flex; align-items: center; gap: 12px; padding: 20px; border-bottom: 1px solid var(--border-glass); }
.header-icon { width: 36px; height: 36px; background: var(--accent); border: 2px solid var(--ink); border-radius: 6px; display: flex; align-items: center; justify-content: center; box-shadow: 3px 3px 0 var(--shadow-ink); }
.header-icon svg { width: 18px; height: 18px; color: #141414; }
.sidebar-header h3 { flex: 1; font-size: 16px; font-weight: 700; color: var(--text-primary); margin: 0; }
.new-chat-btn { width: 36px; height: 36px; padding: 0; border-radius: 6px; }
.new-chat-btn svg { width: 18px; height: 18px; }

.sidebar-loading { display: flex; justify-content: center; padding: 32px; }
.sidebar-empty { padding: 40px 20px; text-align: center; color: var(--text-muted); font-size: 13px; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.sidebar-empty svg { width: 40px; height: 40px; opacity: 0.3; }

.session-list { flex: 1; overflow-y: auto; padding: 12px; }
.session-item { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 2px solid transparent; border-radius: 6px; cursor: pointer; transition: all 0.2s; margin-bottom: 4px; }
.session-item:hover { background: var(--bg-tertiary); }
.session-item.active { background: var(--accent-light); border-color: var(--ink); }
.session-item-icon { width: 32px; height: 32px; background: var(--bg-glass); border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.session-item-icon svg { width: 16px; height: 16px; color: var(--text-muted); }
.session-item.active .session-item-icon { background: var(--accent); }
.session-item.active .session-item-icon svg { color: #141414; }
.session-item-text { flex: 1; overflow: hidden; }
.session-item-title { font-size: 13px; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item-id { font-size: 11px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-delete-btn { width: 28px; height: 28px; border: none; background: transparent; border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center; opacity: 0; transition: all 0.2s; flex-shrink: 0; }
.session-delete-btn svg { width: 14px; height: 14px; color: var(--text-muted); }
.session-delete-btn:hover { background: var(--error-light); }
.session-delete-btn:hover svg { color: var(--error); }
.session-item:hover .session-delete-btn { opacity: 1; }

.chat-main { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.chat-container { flex: 1; overflow-y: auto; padding: 24px; min-height: 0; }

.chat-welcome { display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); height: 100%; padding: 40px; }
.welcome-avatar { width: 100px; height: 100px; border-radius: 50%; padding: 4px; background: var(--accent); border: 2px solid var(--ink); margin-bottom: 20px; }
.welcome-avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.chat-welcome h3 { font-size: 22px; color: var(--text-primary); margin: 0 0 8px; font-weight: 700; }
.chat-welcome p { margin: 0 0 24px; }

.welcome-suggestions { display: flex; flex-direction: column; gap: 10px; width: 100%; max-width: 400px; }
.suggestion-item { padding: 14px 18px; background: var(--bg-secondary); border: 2px solid var(--ink); border-radius: var(--card-radius); box-shadow: 3px 3px 0 var(--shadow-ink); cursor: pointer; transition: all 0.2s ease; font-size: 14px; color: var(--text-secondary); }
.suggestion-item:hover { background: var(--accent-light); transform: translate(-2px, -2px); box-shadow: 5px 5px 0 var(--shadow-ink); }

/* 消息操作栏 */
.message-actions-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 8px 12px; background: var(--bg-secondary); border-radius: var(--card-radius); border: 2px solid var(--ink); box-shadow: 3px 3px 0 var(--shadow-ink); }
.action-btn { display: flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 6px; border: 2px solid var(--ink); font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.action-btn svg { width: 16px; height: 16px; }
.cancel-btn { background: var(--bg-secondary); color: var(--text-secondary); }
.cancel-btn:hover { background: var(--bg-tertiary); }
.confirm-delete-btn { background: var(--error); color: #fff; }
.confirm-delete-btn:hover:not(:disabled) { background: #dc2626; }
.confirm-delete-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.select-hint { flex: 1; font-size: 13px; color: var(--text-muted); }

/* 消息选择复选框 */
.message-checkbox { display: flex; align-items: center; padding-right: 8px; cursor: pointer; }
.checkbox { width: 20px; height: 20px; border: 2px solid var(--ink); border-radius: 4px; background: var(--bg-secondary); display: flex; align-items: center; justify-content: center; transition: all 0.2s; }
.checkbox.checked { background: var(--accent); border-color: var(--ink); }
.checkbox svg { width: 14px; height: 14px; color: #141414; }

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
.msg-action-btn:hover { background: var(--bg-tertiary); opacity: 1; }
.msg-action-btn:hover svg { color: var(--text-primary); }
.msg-action-btn.delete:hover { background: var(--error-light); }
.msg-action-btn.delete:hover svg { color: var(--error); }
.message.user .msg-action-btn svg { color: rgba(20,20,20,0.7); }
.message.user .msg-action-btn:hover { background: rgba(20,20,20,0.12); }
.message.user .msg-action-btn:hover svg { color: #141414; }

.message { display: flex; gap: 14px; margin-bottom: 24px; max-width: 85%; position: relative; }
.message.user { margin-left: auto; flex-direction: row-reverse; }
.message-avatar { width: 38px; height: 38px; border-radius: 6px; border: 2px solid var(--ink); flex-shrink: 0; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.message-avatar.ai { padding: 2px; background: var(--accent); }
.message-avatar.ai img { width: 100%; height: 100%; border-radius: 4px; object-fit: cover; }
.message-avatar.user { background: var(--secondary); }
.message-avatar.user svg { width: 20px; height: 20px; color: #fff; }
.message-avatar.user img.user-avatar-img { width: 100%; height: 100%; object-fit: cover; border-radius: 4px; }
.message-bubble { padding: 14px 18px; border-radius: 8px; max-width: 100%; transition: all 0.2s; }
.message-bubble.selected { box-shadow: 0 0 0 2px var(--accent), 3px 3px 0 var(--shadow-ink); }
.message.assistant .message-bubble { background: var(--bg-secondary); border: 2px solid var(--ink); border-radius: 8px 8px 8px 2px; box-shadow: 3px 3px 0 var(--shadow-ink); }
.message.user .message-bubble { background: var(--accent); color: #141414; border: 2px solid var(--ink); border-radius: 8px 8px 2px 8px; box-shadow: 3px 3px 0 var(--shadow-ink); }
.thinking-block { margin-bottom: 10px; border: 2px solid var(--ink); border-radius: 6px; background: var(--bg-tertiary); overflow: hidden; }
.thinking-header { display: flex; align-items: center; gap: 6px; padding: 7px 12px; cursor: pointer; font-size: 12px; color: var(--text-secondary); user-select: none; }
.thinking-header:hover { background: var(--bg-glass-hover); }
.thinking-icon { width: 14px; height: 14px; flex-shrink: 0; }
.chevron { margin-left: auto; width: 14px; height: 14px; transition: transform 0.2s; }
.chevron.collapsed { transform: rotate(-90deg); }
.thinking-content { padding: 0 12px 10px; font-size: 12px; line-height: 1.7; color: var(--text-secondary); white-space: pre-wrap; word-break: break-word; max-height: 300px; overflow-y: auto; }

.message-content { font-size: 14px; line-height: 1.7; word-break: break-word; }
.message-content p { margin: 0 0 12px 0; }
.message-content p:last-child { margin-bottom: 0; }
.message-content h1, .message-content h2, .message-content h3 { margin: 16px 0 8px 0; font-weight: 700; color: var(--text-primary); }
.message-content h1 { font-size: 20px; }
.message-content h2 { font-size: 18px; }
.message-content h3 { font-size: 16px; }
.message-content ul, .message-content ol { margin: 8px 0; padding-left: 20px; }
.message-content li { margin: 4px 0; }
.message-content code { background: var(--bg-tertiary); padding: 2px 6px; border-radius: 4px; font-size: 13px; font-family: monospace; }
.message-content pre { background: var(--bg-tertiary); padding: 12px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
.message-content pre code { background: none; padding: 0; }
.message-content blockquote { border-left: 3px solid var(--accent); padding-left: 12px; margin: 8px 0; color: var(--text-muted); }
.message-content strong { font-weight: 600; }
.message-content em { font-style: italic; }
.message-content a { color: var(--accent); text-decoration: none; }
.message-content a:hover { text-decoration: underline; }
.message-content hr { border: none; border-top: 1px solid var(--border-glass); margin: 16px 0; }
.message-content table { border-collapse: collapse; width: 100%; margin: 8px 0; }
.message-content th, .message-content td { border: 1px solid var(--border-glass); padding: 8px; text-align: left; }
.message-content th { background: var(--bg-tertiary); }
.message-time { font-size: 11px; color: var(--text-muted); margin-top: 6px; }
.message.user .message-time { color: rgba(20,20,20,0.6); }
.message.user .message-content code { background: rgba(20,20,20,0.12); }
.message.user .message-content pre { background: rgba(20,20,20,0.08); }
.message.user .message-content a { color: #141414; }

.message.select-mode { cursor: pointer; }

.typing-indicator { display: flex; gap: 4px; padding: 4px 0; }
.typing-indicator .dot { width: 8px; height: 8px; background: var(--accent); border-radius: 50%; animation: bounce 1.4s infinite ease-in-out both; }
.typing-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator .dot:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; } 40% { transform: scale(1); opacity: 1; } }

.chat-input-bar { padding: 20px 24px; border-top: 1px solid var(--border-glass); }
.input-wrapper { display: flex; gap: 12px; align-items: flex-end; background: var(--input-bg); border: 2px solid var(--ink); border-radius: 6px; padding: 8px 8px 8px 16px; transition: all 0.2s ease; box-shadow: 3px 3px 0 var(--shadow-ink); }
.input-wrapper:focus-within { border-color: var(--accent); box-shadow: 3px 3px 0 var(--shadow-ink); }
.chat-input { flex: 1; resize: none; border: none; background: transparent; font-size: 14px; color: var(--text-primary); font-family: inherit; outline: none; line-height: 21px; min-height: 21px; overflow-y: auto; }
.chat-input::placeholder { color: var(--text-muted); }
.send-btn { width: 40px; height: 40px; border-radius: 6px; border: 2px solid var(--ink); background: var(--accent); color: #141414; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s ease; flex-shrink: 0; box-shadow: 3px 3px 0 var(--shadow-ink); }
.send-btn svg { width: 18px; height: 18px; }
.send-btn:hover:not(:disabled) { transform: translate(-2px, -2px); box-shadow: 5px 5px 0 var(--shadow-ink); }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.stop-btn { background: var(--error); color: #fff; }

.confirm-modal { background: var(--card-bg); border: 2px solid var(--ink); border-radius: var(--card-radius); padding: 24px; min-width: 320px; text-align: center; box-shadow: 8px 8px 0 var(--shadow-ink); }
.confirm-modal h3 { margin: 0 0 12px; font-size: 18px; font-weight: 700; color: var(--text-primary); }
.confirm-modal p { margin: 0 0 20px; color: var(--text-secondary); font-size: 14px; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }

@media (max-width: 768px) {
  .chat-sidebar { width: 240px; }
  .welcome-suggestions { max-width: 300px; }
}
</style>
