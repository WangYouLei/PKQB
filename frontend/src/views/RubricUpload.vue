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
      <button class="btn btn-primary" style="margin-top:16px" @click="confirmParse(2)" :disabled="!selectedFile || parsing">
        <span v-if="parsing" class="spinner"></span><span v-else>视觉模型解析</span>
      </button>
      <button class="btn btn-secondary" style="margin-top:16px;margin-left:12px" @click="confirmParse(1)" :disabled="!selectedFile || parsing">
        <span v-if="parsing" class="spinner"></span><span v-else>纯文本模型解析</span>
      </button>
      <button class="btn btn-secondary" style="margin-top:16px;margin-left:12px" @click="confirmParseLocal" :disabled="!selectedFile || parsingLocal">
        <span v-if="parsingLocal" class="spinner"></span><span v-else>本地解析题目</span>
      </button>

      <!-- AI解析实时进度 -->
      <div v-if="parsing && parseProgress" style="margin-top:16px;padding:12px 16px;background:var(--accent-light);border-radius:12px;display:flex;align-items:center;gap:10px">
        <span class="spinner"></span>
        <span style="color:var(--accent);font-weight:500">{{ parseProgress }}</span>
      </div>

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
        <template v-if="!editMode">
          <div class="question-text">{{ q.question }}</div>
          <template v-if="q.resources?.length">
            <img v-for="img in getQuestionImages(q.resources)" :key="img.url" :src="img.url" class="question-image" />
          </template>
        </template>
        <div v-else class="form-group">
          <label class="form-label">题目内容</label>
          <textarea v-model="q.question" class="form-input" rows="3"></textarea>
          <div class="image-edit-area">
            <template v-if="getQuestionImages(q.resources).length">
              <div v-for="img in getQuestionImages(q.resources)" :key="img.url" class="image-edit-item">
                <img :src="img.url" class="question-image" />
                <div class="image-edit-actions">
                  <button class="img-action-btn replace" @click="triggerImageUpload(q, 'question_image', '')" title="替换图片">替换</button>
                  <button class="img-action-btn delete" @click="removeResource(q, 'question_image')" title="删除图片">删除</button>
                </div>
              </div>
            </template>
            <button v-else class="img-action-btn add" @click="triggerImageUpload(q, 'question_image', '')" title="添加图片">+ 添加题目图片</button>
          </div>
        </div>
        
        <!-- 选项 -->
        <div v-if="q.options && q.options.length" class="question-options">
          <template v-if="!editMode">
            <div v-for="(opt, oi) in q.options" :key="oi" class="option-item">
              <span class="option-text">{{ opt }}</span>
              <img v-if="getOptionImage(q.resources, String.fromCharCode(65 + oi))" :src="getOptionImage(q.resources, String.fromCharCode(65 + oi))!" class="option-image" />
            </div>
          </template>
          <template v-else>
            <div v-for="(_, oi) in q.options" :key="oi" class="option-edit-item">
              <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
              <input v-model="q.options![oi]" class="option-input" />
              <div class="image-edit-area">
                <template v-if="getOptionImage(q.resources, String.fromCharCode(65 + oi))">
                  <img :src="getOptionImage(q.resources, String.fromCharCode(65 + oi))!" class="option-image" />
                  <div class="image-edit-actions">
                    <button class="img-action-btn replace" @click="triggerImageUpload(q, 'option_image', String.fromCharCode(65 + oi))" title="替换图片">替换</button>
                    <button class="img-action-btn delete" @click="removeResource(q, 'option_image', String.fromCharCode(65 + oi))" title="删除图片">删除</button>
                  </div>
                </template>
                <button v-else class="img-action-btn add" @click="triggerImageUpload(q, 'option_image', String.fromCharCode(65 + oi))" title="添加图片">+ 图片</button>
              </div>
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
          <img v-if="getAnswerImage(q.resources)" :src="getAnswerImage(q.resources)!" class="answer-image" />
        </div>
        <div v-else class="form-group">
          <label class="form-label">答案</label>
          <input v-model="q.answer" class="form-input" placeholder="如：A 或 A,B,C" />
        </div>
        
        <!-- 解析 -->
        <div v-if="!editMode && q.explanation" class="question-explanation">
          <strong>解析：</strong>{{ q.explanation }}
          <img v-if="getExplanationImage(q.resources)" :src="getExplanationImage(q.resources)!" class="explanation-image" />
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
        <!-- 模式切换 -->
        <div class="mode-switch">
          <button type="button" class="mode-btn" :class="{ active: uploadMode === 'new' }" @click="switchUploadMode('new')">新建试卷</button>
          <button type="button" class="mode-btn" :class="{ active: uploadMode === 'existing' }" @click="switchUploadMode('existing')">加入已有试卷</button>
        </div>

        <!-- 新建试卷模式 -->
        <template v-if="uploadMode === 'new'">
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
              <input :checked="!rubricIsPrivate" @change="rubricIsPrivate = !($event.target as HTMLInputElement).checked" type="checkbox" />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </template>

        <!-- 加入已有试卷模式 -->
        <template v-else>
          <div class="form-group">
            <label class="form-label">选择试卷 *（仅显示您创建的试卷）</label>
            <div v-if="myRubricsLoading" style="padding:8px 0;color:var(--text-muted);font-size:14px">加载中...</div>
            <select v-else-if="myRubrics.length" v-model="selectedRubricId" class="form-input">
              <option :value="null" disabled>请选择要加入的试卷</option>
              <option v-for="r in myRubrics" :key="r.id" :value="r.id">
                {{ r.title }}（{{ r.questionCount ?? 0 }}题）
              </option>
            </select>
            <p v-else class="no-rubric-tip">您还没有创建过试卷，请选择"新建试卷"</p>
          </div>
        </template>

        <div style="display:flex;gap:12px;margin-top:24px">
          <button class="btn btn-secondary" @click="showUploadModal = false">取消</button>
          <button class="btn btn-primary" @click="handleUploadRubric" :disabled="uploadLoading || !canSubmit">
            <span v-if="uploadLoading" class="spinner"></span><span v-else>确定上传</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 试卷列表弹窗 -->
    <div v-if="showExamListModal" class="modal-overlay" @click.self="showExamListModal = false">
      <div class="modal-content" style="max-width:500px">
        <h3 style="font-size:18px;margin-bottom:16px">📋 已有试卷列表</h3>
        <p style="color:var(--text-muted);margin-bottom:16px;font-size:14px">
          以下是您和班级公开的试卷，请确认是否已存在相同试卷，不要重复上传。如果不确定，请先查看一下
        </p>
        <div v-if="examListLoading" style="text-align:center;padding:20px;color:var(--text-muted)">
          加载中...
        </div>
        <div v-else-if="examList.length === 0" style="text-align:center;padding:20px;color:var(--text-muted)">
          暂无可用试卷
        </div>
        <div v-else style="max-height:300px;overflow-y:auto;margin-bottom:16px">
          <div v-for="exam in examList" :key="exam.id" class="exam-item">
            <span class="exam-title">{{ exam.title }}</span>
            <span class="exam-badge" :class="exam.isPrivate ? 'private' : 'public'">
              {{ exam.isPrivate ? '我的' : '公开' }}
            </span>
          </div>
        </div>
        <div style="display:flex;gap:12px">
          <button class="btn btn-secondary" @click="showExamListModal = false">取消</button>
          <button class="btn btn-primary" @click="proceedToParse">确认继续解析</button>
        </div>
      </div>
    </div>

    <!-- AI解析确认弹窗 -->
    <div v-if="showParseConfirm" class="modal-overlay" @click.self="showParseConfirm = false">
      <div class="modal-content">
        <h3 style="font-size:18px;margin-bottom:16px">⚠️ 确认解析</h3>
        <p style="color:var(--text-muted);margin-bottom:24px">
          将使用<strong>{{ pendingModelType === 2 ? '视觉模型' : '纯文本模型' }}</strong>解析，可能需要几分钟时间，请耐心等待。解析过程中会显示实时进度，请勿关闭页面。
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
import { ref, computed, onUnmounted } from 'vue'
import { apiAddRubricFile, apiAddRubricFileLocal, apiAppendRubricQuestions, apiGetMyRubrics, apiGetPublicRubrics, apiUploadResourceImage, apiGetRubricParseTask } from '@/api'
import { useUserStore } from '@/stores/user'
import type { QuestionItem, QuestionResource, RubricItem } from '@/types'
import { getQuestionImages, getOptionImage, getAnswerImage, getExplanationImage } from '@/composables/useQuestionResources'

const userStore = useUserStore()

const selectedFile = ref<File|null>(null)
const dragOver = ref(false)
const fileInput = ref<HTMLInputElement|null>(null)
const parsing = ref(false)
const parsingLocal = ref(false)
const parseProgress = ref('')  // AI异步解析进度文案
const parseResult = ref<{ success: boolean; message: string } | null>(null)
const questions = ref<QuestionItem[]>([])
const editMode = ref(false)

// 上传题目表单相关
const showUploadModal = ref(false)
const showParseConfirm = ref(false)  // AI解析确认弹窗
const pendingModelType = ref<number>(2)  // 待解析的模型类型
const showExamListModal = ref(false)  // 试卷列表弹窗
const uploadLoading = ref(false)
const rubricTitle = ref('')
const rubricClassName = ref('')
const rubricIsPrivate = ref(false)
const examList = ref<RubricItem[]>([])  // 试卷列表（公开+用户）
const examListLoading = ref(false)

// 上传模式：new=新建试卷，existing=加入已有试卷
const uploadMode = ref<'new' | 'existing'>('new')
const myRubrics = ref<RubricItem[]>([])
const myRubricsLoading = ref(false)
const selectedRubricId = ref<number | null>(null)

const canSubmit = computed(() =>
  uploadMode.value === 'new'
    ? rubricTitle.value.trim() !== '' && rubricClassName.value.trim() !== ''
    : selectedRubricId.value !== null
)

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

// 图片编辑相关
function removeResource(q: QuestionItem, type: string, label?: string) {
  if (!q.resources) return
  q.resources = q.resources.filter(r => !(r.type === type && (label === undefined || r.label === label)))
}

async function uploadImageToResource(q: QuestionItem, type: string, label: string, file: File) {
  const res = await apiUploadResourceImage(file)
  if (res.code === 200 && res.data?.url) {
    if (!q.resources) q.resources = []
    // 如果同 type+label 已存在，替换
    const existing = q.resources.find(r => r.type === type && r.label === label)
    if (existing) {
      existing.url = res.data.url
    } else {
      q.resources.push({ type: type as QuestionResource['type'], label, url: res.data.url })
    }
  } else {
    alert(res.message || '图片上传失败')
  }
}

function triggerImageUpload(q: QuestionItem, type: string, label: string) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    await uploadImageToResource(q, type, label, file)
  }
  input.click()
}

// 上传题目 - 显示弹窗
function showUploadForm() {
  rubricTitle.value = ''
  rubricClassName.value = userStore.className || ''
  rubricIsPrivate.value = false
  uploadMode.value = 'new'
  selectedRubricId.value = null
  showUploadModal.value = true
}

// 切换上传模式（首次切换到"加入已有试卷"时加载我的试卷列表）
async function switchUploadMode(mode: 'new' | 'existing') {
  uploadMode.value = mode
  if (mode === 'existing' && myRubrics.value.length === 0 && !myRubricsLoading.value) {
    myRubricsLoading.value = true
    try {
      const res = await apiGetMyRubrics()
      myRubrics.value = res.code === 200 && res.data ? res.data : []
    } catch {
      myRubrics.value = []
    } finally {
      myRubricsLoading.value = false
    }
  }
}

// 构造题目数据
function buildRubricsPayload() {
  return questions.value.map(q => ({
    question: q.question,
    questionType: q.questionType,
    options: q.options || [],
    answer: q.answer,
    explanation: q.explanation || '',
    calculationSteps: q.calculationSteps && q.calculationSteps.length > 0 ? q.calculationSteps : [],
    resources: q.resources || []
  }))
}

// 提交题目到后端
async function handleUploadRubric() {
  // 加入已有试卷
  if (uploadMode.value === 'existing') {
    if (!selectedRubricId.value) {
      alert('请选择要加入的试卷')
      return
    }
    uploadLoading.value = true
    try {
      const res = await apiAppendRubricQuestions(selectedRubricId.value, buildRubricsPayload())
      if (res.code === 200) {
        showUploadModal.value = false
        alert('题目已成功加入所选试卷！')
        resetForm()
      } else {
        alert(res.message || '上传失败')
      }
    } catch (e: unknown) {
      alert((e as Error).message || '上传失败')
    } finally {
      uploadLoading.value = false
    }
    return
  }

  // 新建试卷
  if (!rubricTitle.value.trim() || !rubricClassName.value.trim()) {
    alert('请填写完整的试卷信息')
    return
  }
  uploadLoading.value = true
  try {
    const payload = {
      title: rubricTitle.value,
      className: rubricClassName.value,
      createId: userStore.userId,
      createStudentNo: userStore.studentNo,
      isPrivate: rubricIsPrivate.value,
      rubrics: buildRubricsPayload()
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
  rubricIsPrivate.value = false
  editMode.value = false
}

// AI异步解析：轮询定时器与清理
let parsePollTimer: ReturnType<typeof setTimeout> | null = null
function stopPolling() {
  if (parsePollTimer !== null) {
    clearTimeout(parsePollTimer)
    parsePollTimer = null
  }
}
onUnmounted(stopPolling)

function applyParsedQuestions(questionsArr: QuestionItem[]) {
  questions.value = questionsArr.map((q) => ({
    ...q,
    // 处理 calculationStepsText
    calculationStepsText: q.calculationSteps?.join('\n') || ''
  }))
  parseResult.value = { success: true, message: `成功解析 ${questionsArr.length} 道题目` }
}

async function handleFileParse() {
  if (!selectedFile.value) return
  parsing.value = true
  editMode.value = false
  parseResult.value = null
  parseProgress.value = '正在上传文件并提交解析任务...'
  let pollFailCount = 0
  try {
    // 1. 提交异步解析任务
    const submitRes = await apiAddRubricFile(selectedFile.value, pendingModelType.value)
    if (submitRes.code !== 200 || !submitRes.data) {
      parseResult.value = { success: false, message: submitRes.message || '提交解析任务失败' }
      return
    }
    const taskId = submitRes.data

    // 2. 轮询任务状态（3秒一次；连续3次查询失败则终止）
    await new Promise<void>((resolve) => {
      const poll = async () => {
        try {
          const res = await apiGetRubricParseTask(taskId)
          pollFailCount = 0
          if (res.code === 200 && res.data) {
            const task = res.data
            if (task.status === 'RUNNING') {
              parseProgress.value = task.message || 'AI解析中...'
              parsePollTimer = setTimeout(poll, 3000)
              return
            }
            if (task.status === 'SUCCESS' && task.result && task.result.length > 0) {
              applyParsedQuestions(task.result)
            } else if (task.status === 'SUCCESS') {
              parseResult.value = { success: false, message: 'AI解析结果为空' }
            } else {
              parseResult.value = {
                success: false,
                message: task.message || 'AI解析失败，可能原因：题目数量过多等，可以尝试减少题目数量或者使用"本地解析题目"功能'
              }
            }
          } else {
            // 任务不存在/已过期/无权限
            parseResult.value = { success: false, message: res.message || '查询解析任务失败' }
          }
        } catch {
          pollFailCount++
          if (pollFailCount >= 3) {
            parseResult.value = { success: false, message: '查询解析进度连续失败，请稍后在通知中心查看结果' }
          } else {
            parsePollTimer = setTimeout(poll, 3000)
            return
          }
        }
        resolve()
      }
      parsePollTimer = setTimeout(poll, 1000)
    })
  } catch (e: unknown) {
    const errorMsg = (e as Error).message || 'AI解析失败'
    parseResult.value = {
      success: false,
      message: `AI解析失败，可能原因：题目数量过多等，可以尝试减少题目数量或者使用"本地解析题目"功能\n\n错误详情：${errorMsg}`
    }
  } finally {
    stopPolling()
    parseProgress.value = ''
    parsing.value = false
  }
}

async function handleFileParseLocal() {
  if (!selectedFile.value || !userStore.userId) return
  parsingLocal.value = true
  parseResult.value = null
  try {
    const res = await apiAddRubricFileLocal(selectedFile.value)
    if (res.code === 200 && res.data) {
      // 后端直接返回数组对象，不需要再 JSON.parse
      const questionsArr = Array.isArray(res.data) ? res.data : []
      questions.value = questionsArr.map((q) => ({
        ...q,
        // 处理 calculationStepsText
        calculationStepsText: q.calculationSteps?.join('\n') || ''
      }))
      parseResult.value = { success: true, message: `成功解析 ${questionsArr.length} 道题目` }
    } else {
      parseResult.value = { success: false, message: res.message || '本地解析失败' }
    }
  } catch (e: unknown) {
    parseResult.value = { success: false, message: (e as Error).message || '本地解析失败' }
  } finally { parsingLocal.value = false }
}

function confirmParse(modelType: number) {
  pendingModelType.value = modelType
  // 先获取试卷列表，显示弹窗让用户确认
  examListLoading.value = true
  showExamListModal.value = true
  
  Promise.all([
    apiGetMyRubrics(),
    apiGetPublicRubrics()
  ]).then(([myRes, publicRes]) => {
    const myList = myRes.code === 200 && myRes.data ? myRes.data : []
    const publicList = publicRes.code === 200 && publicRes.data ? publicRes.data : []
    // 合并并去重（按id）
    const allList = [...myList]
    const existingIds = new Set(allList.map(r => r.id))
    publicList.forEach((r: RubricItem) => {
      if (!existingIds.has(r.id)) {
        allList.push(r)
      }
    })
    examList.value = allList
  }).catch(() => {
    examList.value = []
  }).finally(() => {
    examListLoading.value = false
  })
}

// 用户确认继续解析
function proceedToParse() {
  showExamListModal.value = false
  showParseConfirm.value = true
}

function confirmParseLocal() {
  // 直接进行本地解析，不需要确认
  handleFileParseLocal()
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content:space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.page-hint { font-size: 14px; color: var(--text-muted); }
.upload-zone { border: 2px dashed var(--ink); border-radius: var(--card-radius); padding: 48px 24px; text-align: center; cursor: pointer; transition: all 0.2s; background: var(--bg-secondary); }
.upload-zone:hover, .upload-zone.drag-over { border-color: var(--accent); background: var(--accent-light); }
.upload-zone-icon { font-size: 48px; margin-bottom: 12px; }
.upload-zone-text { font-size: 16px; color: var(--text-primary); margin-bottom: 4px; }
.upload-zone-hint { font-size: 13px; color: var(--text-muted); }
.upload-result { margin-top: 24px; padding: 16px 20px; border-radius: var(--card-radius); display: flex; align-items: center; gap: 12px; }
.upload-result.success { background: var(--success-light); border: 2px solid var(--success); }
.upload-result.error { background: var(--error-light); border: 2px solid var(--error); }
.result-icon { font-size: 24px; }
.result-text { flex: 1; font-size: 14px; }

.result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.result-actions { display: flex; gap: 12px; }

.question-card { border: 2px solid var(--ink); border-radius: var(--card-radius); padding: 20px; margin-bottom: 16px; box-shadow: 3px 3px 0 var(--shadow-ink); }
.question-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.question-number { font-weight: 600; color: var(--text-primary); }
.question-type { font-size: 12px; padding: 2px 10px; border-radius: 4px; font-weight: 600; border: 1px solid var(--ink); }
.type-single_choice { background: var(--tertiary-light); color: var(--tertiary); }
.type-multiple_choice { background: var(--accent); color: #141414; }
.type-true_false { background: var(--success-light); color: var(--success); }
.type-short_answer { background: rgba(249,115,22,0.1); color: var(--warning); }
.type-calculation { background: var(--secondary-light); color: var(--secondary); }
.calculation-steps { padding: 8px 12px; background: rgba(236,72,153,0.05); border-radius: 8px; margin-bottom: 8px; font-size: 14px; color: #db2777; }
.calculation-steps strong { display: block; margin-bottom: 8px; }
.step-item { padding: 4px 0; }
.question-text { font-size: 15px; line-height: 1.7; color: var(--text-primary); margin-bottom: 12px; }
.question-image { max-width: 100%; max-height: 300px; border-radius: 8px; margin-top: 8px; }
.question-options { margin-bottom: 12px; }
.option-item { display: flex; align-items: flex-start; gap: 8px; padding: 6px 0; font-size: 14px; color: var(--text-primary); }
.option-label { font-weight: 600; flex-shrink: 0; }
.option-image { max-width: 200px; max-height: 150px; border-radius: 6px; margin-left: 4px; }
.question-answer { padding: 8px 12px; background: var(--success-light); border-radius: 8px; margin-bottom: 8px; font-size: 14px; color: var(--success); }
.answer-image { max-width: 100%; max-height: 200px; border-radius: 6px; margin-top: 6px; display: block; }
.question-explanation { padding: 8px 12px; background: var(--tertiary-light); border-radius: 8px; font-size: 14px; color: var(--tertiary); line-height: 1.6; }
.explanation-image { max-width: 100%; max-height: 200px; border-radius: 6px; margin-top: 6px; display: block; }

/* 编辑模式样式 */
.type-select { padding: 4px 8px; border-radius: 6px; border: 2px solid var(--ink); background: var(--input-bg); color: var(--text-primary); font-size: 12px; cursor: pointer; }
.form-group { margin-bottom: 12px; }
.form-label { display: block; font-size: 13px; font-weight: 500; color: var(--text-muted); margin-bottom: 6px; }
.form-input { width: 100%; padding: 8px 12px; border: 2px solid var(--ink); border-radius: 6px; background: var(--input-bg); color: var(--text-primary); font-size: 14px; font-family: inherit; resize: vertical; }
.form-input:focus { outline: none; border-color: var(--accent); }
.option-edit-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.option-input { flex: 1; padding: 6px 10px; border: 2px solid var(--ink); border-radius: 6px; background: var(--input-bg); color: var(--text-primary); font-size: 14px; }
.option-input:focus { outline: none; border-color: var(--accent); }

/* 图片编辑样式 */
.image-edit-area { margin-top: 6px; }
.image-edit-item { display: inline-flex; flex-direction: column; align-items: flex-start; gap: 4px; margin-right: 8px; }
.image-edit-actions { display: flex; gap: 4px; }
.img-action-btn { padding: 2px 8px; border: 2px solid var(--ink); border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.2s; background: var(--bg-secondary); color: var(--text-primary); }
.img-action-btn.add { background: var(--accent-light); color: #141414; }
.img-action-btn.add:hover { background: var(--accent); }
.img-action-btn.replace { background: rgba(240,160,32,0.15); color: var(--warning); }
.img-action-btn.replace:hover { background: var(--warning); color: #141414; }
.img-action-btn.delete { background: var(--error-light); color: var(--error); }
.img-action-btn.delete:hover { background: var(--error); color: #fff; }

/* 模态框样式 */
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: var(--card-bg); border-radius: var(--card-radius); padding:  32px; width: 90%; max-width: 420px; border: 2px solid var(--ink); box-shadow: 8px 8px 0 var(--shadow-ink); }
.mode-switch { display: flex; gap: 8px; margin-bottom: 20px; background: var(--bg-tertiary); border-radius: 6px; padding: 4px; }
.mode-btn { flex: 1; padding: 8px 0; border: none; border-radius: 4px; background: transparent; color: var(--text-muted); font-size: 14px; cursor: pointer; transition: all 0.2s; font-weight: 600; }
.mode-btn.active { background: var(--accent); color: #141414; font-weight: 700; }
.no-rubric-tip { padding: 8px 0; font-size: 13px; color: var(--text-muted); }
.toggle { position: relative; display: inline-block; width: 48px; height: 26px; cursor: pointer; }
.toggle input { opacity: 0; width: 0; height: 0; }
.toggle-slider { position: absolute; inset: 0; background: var(--bg-tertiary); border: 1px solid var(--ink); border-radius: 26px; transition: 0.3s; }
.toggle-slider::before { content: ''; position: absolute; width: 20px; height: 20px; left: 3px; bottom: 3px; background: var(--bg-secondary); border: 1px solid var(--ink); border-radius: 50%; transition: 0.3s; }
.toggle input:checked + .toggle-slider { background: var(--success); border-color: var(--ink); }
.toggle input:checked + .toggle-slider::before { transform: translateX(22px); background: #fff; }

/* 试卷列表样式 */
.exam-item { display:flex;justify-content:space-between;align-items:center;padding:10px 12px;border-bottom:1px solid var(--border-glass); }
.exam-item:last-child { border-bottom:none; }
.exam-title { font-size:14px;color:var(--text-primary); }
.exam-badge { font-size:12px;padding:2px 8px;border-radius:4px; }
.exam-badge.public { background:var(--success-light);color:var(--success); }
.exam-badge.private { background:var(--tertiary-light);color:var(--tertiary); }
</style>