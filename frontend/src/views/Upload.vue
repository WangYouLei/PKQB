<template>
  <div>
    <div class="page-header"><h2 class="page-title">生成文件</h2></div>

    <div v-if="step === 1" class="card" style="padding:32px">
      <h3 style="font-size:18px;margin-bottom:20px">选择输入方式</h3>
      <div class="nav-bar" style="margin-bottom:24px;max-width:300px">
        <button :class="['nav-item',{active:inputMode==='text'}]" @click="inputMode='text'">文字输入</button>
        <button :class="['nav-item',{active:inputMode==='file'}]" @click="inputMode='file'">上传文档</button>
      </div>
      <div v-if="inputMode==='text'">
        <div class="form-group">
          <label class="form-label">输入内容</label>
          <textarea v-model="textContent" class="form-input" placeholder="请输入文档内容..." rows="10"></textarea>
        </div>
      </div>
      <div v-if="inputMode==='file'">
        <div class="upload-zone" @click="triggerFileInput" @dragover.prevent="dragOver=true" @dragleave="dragOver=false" @drop.prevent="handleDrop" :class="{'drag-over':dragOver}">
          <div class="upload-zone-icon">&#128206;</div>
          <div class="upload-zone-text">点击或拖拽文件到此处</div>
          <div class="upload-zone-hint">支持 PDF、DOC、MD 格式</div>
        </div>
        <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.md" style="display:none" @change="handleFileSelect" />
        <div v-if="selectedFile" style="margin-top:16px;padding:12px 16px;background:var(--accent-light);border-radius:12px">
          <span style="color:var(--accent);font-weight:500">已选择：{{ selectedFile.name }}</span>
        </div>
      </div>
      <button class="btn btn-primary" style="margin-top:24px" @click="handleAnalyze" :disabled="!canAnalyze">分析内容</button>
    </div>

    <div v-if="step === 2" class="card" style="padding:32px">
      <h3 style="font-size:18px;margin-bottom:20px">内容分析结果</h3>
      <div class="analysis-preview"><pre class="preview-text">{{ analysisResult }}</pre></div>
      <div style="display:flex;gap:12px;margin-top:24px">
        <button class="btn btn-secondary" @click="step=1">返回修改</button>
        <button class="btn btn-primary" @click="step=3">继续生成</button>
      </div>
    </div>

    <div v-if="step === 3" class="card" style="padding:32px">
      <h3 style="font-size:18px;margin-bottom:20px">保存设置</h3>
      <div class="form-group">
        <label class="form-label">文件名</label>
        <input v-model="fileName" class="form-input" type="text" placeholder="请输入文件名" />
      </div>
      <div class="form-group" style="display:flex;align-items:center;justify-content:space-between">
        <label class="form-label" style="margin-bottom:0">是否公开（班级可见）</label>
        <label class="toggle"><input v-model="isPublic" type="checkbox" /><span class="toggle-slider"></span></label>
      </div>
      <div style="display:flex;gap:12px;margin-top:24px">
        <button class="btn btn-secondary" @click="step=2">返回预览</button>
        <button class="btn btn-primary" @click="handleSave" :disabled="saving||!fileName.trim()">
          <span v-if="saving" class="spinner"></span><span v-else>保存文件</span>
        </button>
      </div>
    </div>

    <div v-if="step === 4" class="card" style="padding:48px;text-align:center">
      <div style="font-size:64px;margin-bottom:16px">&#9989;</div>
      <h3 style="font-size:20px;margin-bottom:8px">文件保存成功！</h3>
      <p style="color:var(--text-secondary);margin-bottom:24px">{{ fileName }} 已保存并上传</p>
      <div style="display:flex;gap:12px;justify-content:center">
        <button class="btn btn-secondary" @click="resetForm">继续生成</button>
        <router-link to="/my-files" class="btn btn-primary">查看我的文件</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { apiUploadFile } from '@/api'

const step = ref(1)
const inputMode = ref<'text'|'file'>('text')
const textContent = ref('')
const selectedFile = ref<File|null>(null)
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement|null>(null)
const fileName = ref('')
const isPublic = ref(false)
const saving = ref(false)
const analysisResult = ref('')

const canAnalyze = computed(() => {
  if (inputMode.value === 'text') return textContent.value.trim().length > 0
  return selectedFile.value !== null
})

function triggerFileInput() { fileInput.value?.click() }
function handleFileSelect(e: Event) {
  const t = e.target as HTMLInputElement
  if (t.files && t.files[0]) { selectedFile.value = t.files[0]; readSelectedFile(t.files[0]) }
}
function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) { selectedFile.value = e.dataTransfer.files[0]; readSelectedFile(e.dataTransfer.files[0]) }
}
async function readSelectedFile(file: File) {
  try {
    if (file.name.endsWith('.md')) { textContent.value = await file.text() }
    else { analysisResult.value = `[文件] ${file.name}\n\n注意：PDF 和 DOC 文件需要后端解析。\n文件大小：${(file.size/1024).toFixed(1)} KB` }
  } catch { analysisResult.value = `文件：${file.name}\n大小：${(file.size/1024).toFixed(1)} KB` }
}
function handleAnalyze() {
  if (inputMode.value === 'text') { analysisResult.value = textContent.value }
  else if (selectedFile.value && !analysisResult.value) { analysisResult.value = `文件：${selectedFile.value.name}\n大小：${(selectedFile.value.size/1024).toFixed(1)} KB\n\n请确认文件内容后继续生成。` }
  step.value = 2
}
function escapeHtml(text: string): string {
  const d = document.createElement('div'); d.textContent = text; return d.innerHTML
}
async function handleSave() {
  if (!fileName.value.trim()) return
  saving.value = true
  try {
    const htmlContent = `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"><title>${fileName.value}</title><style>body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;max-width:800px;margin:40px auto;padding:0 20px;line-height:1.8;color:#333}h1{color:#ff6b35;border-bottom:2px solid #ff6b35;padding-bottom:8px}pre{background:#f5f5f5;padding:16px;border-radius:8px;overflow-x:auto;white-space:pre-wrap}</style></head><body><h1>${escapeHtml(fileName.value)}</h1><pre>${escapeHtml(analysisResult.value)}</pre></body></html>`
    const blob = new Blob([htmlContent], { type: 'text/html' })
    const file = new File([blob], `${fileName.value}.html`, { type: 'text/html' })
    const res = await apiUploadFile(file, fileName.value, isPublic.value)
    if (res.code === 200) { step.value = 4 } else { alert(res.message || '保存失败') }
  } catch (e: unknown) {
    alert((e as { message?: string })?.message || '保存失败，请重试')
  } finally { saving.value = false }
}
function resetForm() { step.value=1; textContent.value=''; selectedFile.value=null; fileName.value=''; isPublic.value=false; analysisResult.value='' }
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.analysis-preview { background: rgba(0,0,0,0.3); border-radius: 12px; padding: 20px; max-height: 400px; overflow-y: auto; }
.preview-text { white-space: pre-wrap; word-break: break-word; color: var(--text-primary); font-size: 14px; line-height: 1.8; margin: 0; font-family: inherit; }
</style>
