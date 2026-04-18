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
import { ref } from 'vue'
import { apiAddDocumentsFile } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const selectedFile = ref<File|null>(null)
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement|null>(null)
const uploading = ref(false)
const uploadResult = ref<{ success: boolean; message: string } | null>(null)

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
    const res = await apiAddDocumentsFile(selectedFile.value, userStore.userId!)
    uploadResult.value = { success: res.code === 200, message: res.message || (res.code === 200 ? '上传成功' : '上传失败') }
    if (res.code === 200) selectedFile.value = null
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
