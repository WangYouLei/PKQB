<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">&#128203; 历史记录</h2>
      <span class="page-hint">查看历史对话记录</span>
    </div>

    <div class="card" style="padding:32px">
      <div class="form-group" style="max-width:300px;margin-bottom:24px">
        <label class="form-label">对话类型</label>
        <select v-model="historyType" class="form-input" @change="loadHistory">
          <option value="chat">AI 对话</option>
          <option value="rag">知识库问答</option>
        </select>
      </div>

      <div v-if="loading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="sessions.length === 0" class="empty-state">
        <div class="empty-state-icon">&#128196;</div>
        <div class="empty-state-text">暂无历史记录</div>
        <div class="empty-state-hint">开始对话后这里会显示历史记录</div>
      </div>
      <div v-else class="history-list">
        <div v-for="(session, idx) in sessions" :key="idx" class="history-item">
          <div class="history-item-icon">{{ historyType === 'rag' ? '&#128218;' : '&#128172;' }}</div>
          <div class="history-item-content">
            <div class="history-item-title">{{ session.title }}</div>
            <div class="history-item-time">{{ session.time }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGetHistory } from '@/api'

const userStore = useUserStore()
const historyType = ref<'chat' | 'rag'>('chat')
const sessions = ref<Array<{ title: string; time: string }>>([])
const loading = ref(false)

async function loadHistory() {
  loading.value = true
  try {
    const userId = String(userStore.userId || localStorage.getItem('userId') || '')
    const res = await apiGetHistory(userId, historyType.value)
    if (res.code === 200 && res.data) {
      const list = Array.isArray(res.data) ? res.data : []
      sessions.value = list.map((item: any, idx: number) => ({
        title: `会话 ${list.length - idx}`,
        time: typeof item === 'string' ? item : `会话ID: ${item}`
      }))
    }
  } catch (e) {
    console.error('获取历史记录失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.page-hint { font-size: 14px; color: var(--text-muted); }
.loading-center { display: flex; justify-content: center; padding: 64px 0; }
.empty-state { text-align: center; padding: 60px 0; color: var(--text-muted); }
.empty-state-icon { font-size: 48px; margin-bottom: 12px; }
.empty-state-text { font-size: 16px; margin-bottom: 4px; }
.empty-state-hint { font-size: 13px; }

.history-list { display: flex; flex-direction: column; gap: 12px; }
.history-item { display: flex; align-items: center; gap: 16px; padding: 16px; border: 1px solid var(--border-color); border-radius: 12px; cursor: pointer; transition: all 0.2s; }
.history-item:hover { border-color: var(--accent); background: var(--accent-light); }
.history-item-icon { font-size: 28px; }
.history-item-title { font-size: 15px; font-weight: 500; color: var(--text-primary); }
.history-item-time { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
</style>
