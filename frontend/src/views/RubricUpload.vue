<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">📁 上传题目</h2>
      <span class="page-hint">上传题目文件，AI 自动解析题型、答案和解析</span>
    </div>

    <div class="card" style="padding:32px">
      <!-- 文件上传 -->
      <div class="upload-zone" @click="triggerFileInput" @dragover.prevent="dragOver=true" @dragleave="dragOver=false" @drop.prevent="handleDrop" :class="{'drag-over':dragOver}">
        <div class="upload-zone-icon">📄</div>
        <div class="upload-zone-text">点击或拖拽题目文件到此处</div>
        <div class="upload-zone-hint">支持 PDF、DOC、DOCX、TXT 等格式</div>
      </div>
      <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.txt,.md" style="display:none" @change="handleFileSelect" />
      <div v-if="selectedFile" style="margin-top:16px;padding:12px 16px;background:var(--accent-light);border-radius:12px;display:flex;justify-content:space-between;align-items:center">
        <span style="color:var(--accent);font-weight:500">{{ selectedFile.name }}</span>
        <button class="btn btn-sm btn-ghost" @click="selectedFile=null">移除</button>
      </div>
      <button class="btn btn-primary" style="margin-top:16px" @click="confirmParse" :disabled="!selectedFile || parsing">
        <span v-if="parsing" class="spinner"></span><span v-else>AI 解析题目</span>
      </button>

      <!-- 结果提示 -->
      <div v-if="parseResult" class="upload-result" :class="parseResult.success ? 'success' : 'error'">
        <div class="result-icon">{{ parseResult.success ? '✅' : '❌' }}</div>
        <div class="result-text">{{ parseResult.message }}</div>
        <button class="btn btn-sm btn-ghost" @click="parseResult=null">关闭</button>
      </div>
    </div>

    <!-- 解析结果展示 -->
    <div v-if="questions.length > 0" class="card" style="padding:32px;margin-top:24px">
      <div class="result-header">
        <h3 style="font-size:18px;">解析结果（共 {{ questions.length }} 题）</h3>
        <div class="result-actions">
          <button class="btn btn-secondary" @click="toggleEditMode">
            {{ editMode ? '预览' : '编辑' }}
          </button>
          <button class="btn btn-primary" @click="showUploadForm">上传题目</button>
        </div>
      </div>
      
      <div v-for="(q, idx) in questions" :key="idx" class="question-card">
        <div class="question-header">
          <span class="question-number">第 {{ idx + 1 }} 题</span>
          <!-- 查看模式：显示题型 -->
          <template v-if="!editMode">
            <span class="question-type" :class="'type-' + q.questionType">
              {{ typeLabel(q.questionType) }}
            </span>
          </template>
          <!-- 编辑模式：选择题型 -->
          <template v-else>
            <select v-model="q.questionType" class="type-select">
              <option value="single_choice">单选题</option>
              <option value="multiple_choice">多选题</option>
              <option value="true_false">判断题</option>
              <option value="short_answer">简答题</option>
              <option value="calculation">计算题</option>
            </select>
          </template>
        </div>
        
        <!-- 题目内容 -->
        <div v-if="!editMode" class="question-text">{{ q.question }}</div>
        <div v-else class="form-group">
          <label class="form-label">题目内容</label>
          <textarea v-model="q.question" class="form-input" rows="3"></textarea>
        </div>
        
        <!-- 选项 -->
        <div v-if="q.options && q.options.length" class="question-options">
          <template v-if="!editMode">
            <div v-for="(opt, oi) in q.options" :key="oi" class="option-item">
              <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
              <span class="option-text">{{ opt }}</span>
            </div>
          </template>
          <template v-else>
            <div v-for="(opt, oi) in q.options" :key="oi" class="option-edit-item">
              <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
              <input v-model="q.options![oi]" class="option-input" />
            </div>
          </template>
        </div>
        
        <!-- 计算题步骤 -->
        <div v-if="!editMode && q.questionType === 'calculation' && q.calculationSteps && q.calculationSteps.length" class="calculation-steps">
          <strong>计算步骤：</strong>
          <div v-for="(step, si) in q.calculationSteps" :key="si" class="step-item">
            {{ si + 1 }}. {{ step }}
          </div>
        </div>
        <div v-else-if="editMode && q.questionType === 'calculation'" class="form-group">
          <label class="form-label">计算步骤（每行一步）</label>
          <textarea v-model="q.calculationStepsText" class="form-input" rows="3" placeholder="步骤1&#10;步骤2&#10;步骤3"></textarea>
        </div>
        
        <!-- 答案 -->
        <div v-if="!editMode" class="question-answer">
          <strong>答案：</strong>{{ q.answer }}
        </div>
        <div v-else class="form-group">
          <label class="form-label">答案</label>
          <input v-model="q.answer" class="form-input" placeholder="如：A 或 A,B,C" />
        </div>
        
        <!-- 解析 -->
        <div v-if="!editMode && q.explanation" class="question-explanation">
          <strong>解析：</strong>{{ q.explanation }}
        </div>
        <div v-else-if="editMode" class="form-group">
          <label class="form-label">解析</label>
          <textarea v-model="q.explanation" class="form-input" rows="2"></textarea>
        </div>
      </div>
    </div>

    <!-- 上传题目弹窗 -->
    <div v-if="showUploadModal" class="modal-overlay" @click.self="showUploadModal = false">
      <div class="modal-content">
        <h3 style="font-size:18px;margin-bottom:20px">上传题目</h3>
        <div class="form-group">
          <label class="form-label">试卷标题 *</label>
          <input v-model="rubricTitle" class="form-input" placeholder="请输入试卷标题" />
        </div>
        <div class="form-group">
          <label class="form-label">班级名称 *</label>
          <input v-model="rubricClassName" class="form-input" placeholder="请输入班级名称" />
        </div>
        <div class="form-group" style="display:flex;align-items:center;justify-content:space-between">
          <label class="form-label" style="margin-bottom:0">是否公开（班级可见）</label>
          <label class="toggle">
            <input v-model="rubricIsPublic" type="checkbox" />
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div style="display:flex;gap:12px;margin-top:24px">
          <button class="btn btn-secondary" @click="showUploadModal = false">取消</button>
          <button class="btn btn-primary" @click="handleUploadRubric" :disabled="uploadLoading || !rubricTitle.trim() || !rubricClassName.trim()">
            <span v-if="uploadLoading" class="spinner"></span><span v-else>确定上传</span>
          </button>
        </div>
      </div>
    </div>

    <!-- AI解析确认弹窗 -->
    <div v-if="showParseConfirm" class="modal-overlay" @click.self="showParseConfirm = false">
      <div class="modal-content">
        <h3 style="font-size:18px;margin-bottom:16px">⚠️ 确认解析</h3>
        <p style="color:var(--text-muted);margin-bottom:24px">
          AI解析可能需要几分钟时间，请耐心等待。解析过程中请勿关闭页面。
        </p>
        <div style="display:flex;gap:12px">
          <button class="btn btn-secondary" @click="showParseConfirm = false">取消</button>
          <button class="btn btn-primary" @click="showParseConfirm = false; handleFileParse()">开始解析</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { apiAddRubricFile } from '@/api'
import { useUserStore } from '@/stores/user'
import type { QuestionItem } from '@/types'

const userStore = useUserStore()

const selectedFile = ref<File|null>(null)
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement|null>(null)
const parsing = ref(false)
const parseResult = ref<{ success: boolean; message: string } | null>(null)
const questions = ref<QuestionItem[]>([])
const editMode = ref(false)

// 上传题目表单相关
const showUploadModal = ref(false)
const showParseConfirm = ref(false)  // AI解析确认弹窗
const uploadLoading = ref(false)
const rubricTitle = ref('')
const rubricClassName = ref('')
const rubricIsPublic = ref(false)

function triggerFileInput() { fileInput.value?.click() }
function handleFileSelect(e: Event) {
  const t = e.target as HTMLInputElement
  if (t.files && t.files[0]) selectedFile.value = t.files[0]
}
function handleDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) selectedFile.value = e.dataTransfer.files[0]
}

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    single_choice: '单选题',
    multiple_choice: '多选题',
    true_false: '判断题',
    short_answer: '简答题',
    calculation: '计算题'
  }
  return map[type] || type
}

function toggleEditMode() {
  editMode.value = !editMode.value
}

// 上传题目 - 显示弹窗
function showUploadForm() {
  rubricTitle.value = ''
  rubricClassName.value = userStore.className || ''
  rubricIsPublic.value = false
  showUploadModal.value = true
}

// 提交题目到后端
async function handleUploadRubric() {
  if (!rubricTitle.value.trim() || !rubricClassName.value.trim()) {
    alert('请填写完整的试卷信息')
    return
  }
  uploadLoading.value = true
  try {
    // 准备题目数据
    const rubrics = questions.value.map(q => ({
      question: q.question,
      questionType: q.questionType,
      options: q.options || [],
      answer: q.answer,
      explanation: q.explanation || '',
      calculationSteps: q.calculationSteps && q.calculationSteps.length > 0 ? q.calculationSteps : []
    }))
    
    const payload = {
      title: rubricTitle.value,
      className: rubricClassName.value,
      createId: userStore.userId,
      createStudentNo: userStore.studentNo,
      isPublic: rubricIsPublic.value,
      rubrics: rubrics
    }
    
    const res = await fetch('/api/rubric/add-rubric', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'token': userStore.token
      },
      body: JSON.stringify(payload)
    })
    const result = await res.json()
    
    if (result.code === 200) {
      showUploadModal.value = false
      alert('上传成功！')
      resetForm()
    } else {
      alert(result.message || '上传失败')
    }
  } catch (e: unknown) {
    alert((e as Error).message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

function resetForm() {
  selectedFile.value = null
  questions.value = []
  parseResult.value = null
  rubricTitle.value = ''
  rubricClassName.value = ''
  rubricIsPublic.value = false
  editMode.value = false
}

async function handleFileParse() {
  if (!selectedFile.value) return
  parsing.value = true
  editMode.value = false
  try {
    const res = await apiAddRubricFile(selectedFile.value, userStore.userId!)
    if (res.code === 200 && res.data) {
      // 后端直接返回数组对象，不需要再 JSON.parse
      const questionsArr = Array.isArray(res.data) ? res.data : []
      questions.value = questionsArr.map((q: any) => ({
        ...q,
        // 处理 calculationStepsText
        calculationStepsText: q.calculationSteps?.join('\n') || ''
      }))
      parseResult.value = { success: true, message: `成功解析 ${questionsArr.length} 道题目` }
    } else {
      parseResult.value = { success: false, message: res.message || '解析失败' }
    }
  } catch (e: unknown) {
    parseResult.value = { success: false, message: (e as Error).message || '解析失败' }
  } finally { parsing.value = false }
}

function confirmParse() {
  showParseConfirm.value = true
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content:space-between; margin-bottom: 24px; }
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

.result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.result-actions { display: flex; gap: 12px; }

.question-card { border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; margin-bottom: 16px; }
.question-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.question-number { font-weight: 600; color: var(--text-primary); }
.question-type { font-size: 12px; padding: 2px 10px; border-radius: 20px; font-weight: 500; }
.type-single_choice { background: rgba(59,130,246,0.1); color: #3b82f6; }
.type-multiple_choice { background: rgba(168,85,247,0.1); color: #a855f7; }
.type-true_false { background: rgba(34,197,94,0.1); color: #22c55e; }
.type-short_answer { background: rgba(249,115,22,0.1); color: #f97316; }
.type-calculation { background: rgba(236,72,153,0.1); color: #ec4899; }
.calculation-steps { padding: 8px 12px; background: rgba(236,72,153,0.05); border-radius: 8px; margin-bottom: 8px; font-size: 14px; color: #db2777; }
.calculation-steps strong { display: block; margin-bottom: 8px; }
.step-item { padding: 4px 0; }
.question-text { font-size: 15px; line-height: 1.7; color: var(--text-primary); margin-bottom: 12px; }
.question-options { margin-bottom: 12px; }
.option-item { display: flex; gap: 8px; padding: 6px 0; font-size: 14px; color: var(--text-primary); }
.option-label { font-weight: 600; flex-shrink: 0; }
.question-answer { padding: 8px 12px; background: rgba(34,197,94,0.05); border-radius: 8px; margin-bottom: 8px; font-size: 14px; color: #16a34a; }
.question-explanation { padding: 8px 12px; background: rgba(59,130,246,0.05); border-radius: 8px; font-size: 14px; color: #2563eb; line-height: 1.6; }

/* 编辑模式样式 */
.type-select { padding: 4px 8px; border-radius: 20px; border: 1px solid var(--border-color); background: var(--card-bg); color: var(--text-primary); font-size: 12px; cursor: pointer; }
.form-group { margin-bottom: 12px; }
.form-label { display: block; font-size: 13px; font-weight: 500; color: var(--text-muted); margin-bottom: 6px; }
.form-input { width: 100%; padding: 8px 12px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--card-bg); color: var(--text-primary); font-size: 14px; font-family: inherit; resize: vertical; }
.form-input:focus { outline: none; border-color: var(--accent); }
.option-edit-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.option-input { flex: 1; padding: 6px 10px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--card-bg); color: var(--text-primary); font-size: 14px; }
.option-input:focus { outline: none; border-color: var(--accent); }

/* 模态框样式 */
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: var(--card-bg); border-radius: 16px; padding: 32px; width: 90%; max-width: 420px; border: 1px solid var(--border-color); }
.toggle { position: relative; display: inline-block; width: 48px; height: 26px; cursor: pointer; }
.toggle input { opacity: 0; width: 0; height: 0; }
.toggle-slider { position: absolute; inset: 0; background: var(--border-color); border-radius: 26px; transition: 0.3s; }
.toggle-slider::before { content: ''; position: absolute; width: 20px; height: 20px; left: 3px; bottom: 3px; background: #fff; border-radius: 50%; transition: 0.3s; }
.toggle input:checked + .toggle-slider { background: var(--accent); }
.toggle input:checked + .toggle-slider::before { transform: translateX(22px); }
</style>