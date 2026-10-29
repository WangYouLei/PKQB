<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">&#128218; 上传知识库</h2>
      <span class="page-hint">上传文档到向量知识库，用于 RAG 问答</span>
    </div>

    <div class="card" style="padding:32px">
      <!-- 文件上传 -->
      <div class="upload-zone" @click="triggerFileInput" @dragover.prevent="dragOver=true" @dragleave="dragOver=false" @drop.prevent="handleDrop" :class="{'drag-over':dragOver}">
        <div class="upload-zone-icon">&#128206;</div>
        <div class="upload-zone-text">点击或拖拽文件到此处</div>
        <div class="upload-zone-hint">支持 PDF、DOC、DOCX、MD 等格式</div>
      </div>
      <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.md,.txt" style="display:none" @change="handleFileSelect" />
      <div v-if="selectedFile" style="margin-top:16px;padding:12px 16px;background:var(--accent-light);border-radius:12px;display:flex;justify-content:space-between;align-items:center">
        <span style="color:var(--accent);font-weight:500">{{ selectedFile.name }}</span>
        <button class="btn btn-sm btn-ghost" @click="selectedFile=null">移除</button>
      </div>
      <button class="btn btn-primary" style="margin-top:16px" @click="handleFileUpload" :disabled="!selectedFile || uploading">
        <span v-if="uploading" class="spinner"></span><span v-else>上传到知识库</span>
      </button>

      <!-- 结果提示 -->
      <div v-if="uploadResult" class="upload-result" :class="uploadResult.success ? 'success' : 'error'">
        <div class="result-icon">{{ uploadResult.success ? '&#9989;' : '&#10060;' }}</div>
        <div class="result-text">{{ uploadResult.message }}</div>
        <button class="btn btn-sm btn-ghost" @click="uploadResult=null">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { apiAddDocumentsFile, apiGetKbUploadTask } from '@/api'

const selectedFile = ref<File|null>(null)
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement|null>(null)
const uploading = ref(false)
const uploadResult = ref<{ success: boolean; message: string } | null>(null)

let pollTimer: number | null = null
// 连续失败计数：状态接口持续异常时终止轮询，避免无限重试与反复弹错提示
let pollFailCount = 0
const MAX_POLL_FAILURES = 5

function stopPolling() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

onUnmounted(stopPolling)

function triggerFileInput() { fileInput.value?.click() }
function handleFileSelect(e: Event) {
  const t = e.target as HTMLInputElement
  if (t.files && t.files[0]) selectedFile.value = t.files[0]
}
function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) selectedFile.value = e.dataTransfer.files[0]
}

async function handleFileUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res = await apiAddDocumentsFile(selectedFile.value)
    if (res.code !== 200) {
      uploadResult.value = { success: false, message: res.message || '上传失败' }
      return
    }
    // 后端为异步任务：提交后立即返回taskId，轮询任务状态直至完成
    // （上传含AI内容判断+全文向量化，大文件可能耗时较久，此前同步等待请求可挂2分钟以上）
    const taskId = res.data
    pollFailCount = 0
    await new Promise<void>((resolve) => {
      // 用setTimeout链代替setInterval：上一次请求返回后才调度下一次，避免单次请求超2秒时并发叠加
      const poll = async () => {
        try {
          const task = await apiGetKbUploadTask(taskId)
          pollFailCount = 0
          if (task.code !== 200) {
            // 任务不存在/已过期，终止轮询
            stopPolling()
            uploadResult.value = { success: false, message: task.message || '查询任务状态失败' }
            resolve()
            return
          }
          const st = task.data
          if (st?.status === 'SUCCESS') {
            stopPolling()
            uploadResult.value = { success: true, message: st.message || '上传成功' }
            selectedFile.value = null
            resolve()
          } else if (st?.status === 'FAILED') {
            stopPolling()
            uploadResult.value = { success: false, message: st.message || '上传失败' }
            resolve()
          } else {
            // RUNNING：2秒后再查
            pollTimer = window.setTimeout(poll, 2000)
          }
        } catch {
          // 网络异常：连续失败达上限后终止轮询并提示
          pollFailCount++
          if (pollFailCount >= MAX_POLL_FAILURES) {
            stopPolling()
            uploadResult.value = { success: false, message: '查询任务状态连续失败，请稍后重新上传' }
            resolve()
          } else {
            pollTimer = window.setTimeout(poll, 2000)
          }
        }
      }
      poll()
    })
  } catch (e: unknown) {
    uploadResult.value = { success: false, message: (e as Error).message || '上传失败' }
  } finally { uploading.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.page-hint { font-size: 14px; color: var(--text-muted); }
.upload-zone { border: 2px dashed var(--border-color); border-radius: 16px; padding: 48px 24px; text-align: center; cursor: pointer; transition: all 0.2s; }
.upload-zone:hover, .upload-zone.drag-over { border-color: var(--accent); background: var(--accent-light); }
.upload-zone-icon { font-size: 48px; margin-bottom: 12px; }
.upload-zone-text { font-size: 16px; color: var(--text-primary); margin-bottom: 4px; }
.upload-zone-hint { font-size: 13px; color: var(--text-muted); }
.upload-result { margin-top: 24px; padding: 16px 20px; border-radius: 12px; display: flex; align-items: center; gap: 12px; }
.upload-result.success { background: rgba(34,197,94,0.1); border: 1px solid rgba(34,197,94,0.3); }
.upload-result.error { background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.3); }
.result-icon { font-size: 24px; }
.result-text { flex: 1; font-size: 14px; }
</style>
