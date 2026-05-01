<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">班级共享</h2>
      <span class="page-hint">{{ userStore.className }} 的公开文件</span>
    </div>

    <!-- 切换按钮 -->
    <div class="tab-switch">
      <button 
        class="tab-btn" 
        :class="{ active: activeTab === 'files' }" 
        @click="switchTab('files')"
      >
        MinIO 文件
      </button>
      <button 
        class="tab-btn" 
        :class="{ active: activeTab === 'rubrics' }" 
        @click="switchTab('rubrics')"
      >
        班级试卷
      </button>
    </div>

    <!-- MinIO 文件列表 -->
    <div v-if="activeTab === 'files'">
      <div v-if="loading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="files.length === 0" class="empty-state">
        <div class="empty-state-icon">&#128101;</div>
        <div class="empty-state-text">暂无共享文件</div>
        <div class="empty-state-hint">班级中还没有同学分享公开文件</div>
      </div>
      <div v-else class="file-grid">
        <FileCard v-for="file in files" :key="file.id" :file="file" :showEdit="file.userId === userStore.userId" :showDelete="file.userId === userStore.userId" @preview="handlePreviewFile" @download="handleDownloadFile" @remove="handleDeleteFile" @updated="handleFileUpdated" />
      </div>
    </div>

    <!-- 试卷列表 -->
    <div v-if="activeTab === 'rubrics'">
      <div v-if="rubricLoading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="rubrics.length === 0" class="empty-state">
        <div class="empty-state-icon">&#128220;</div>
        <div class="empty-state-text">暂无公开试卷</div>
        <div class="empty-state-hint">班级中还没有同学分享试卷</div>
      </div>
      <div v-else class="rubric-list">
        <div 
          v-for="rubric in rubrics" 
          :key="rubric.id" 
          class="rubric-card"
          @click="handleOpenRubric(rubric)"
        >
          <div class="rubric-info">
            <div class="rubric-title">{{ rubric.title }}</div>
            <div class="rubric-meta">
              <span>创建者: {{ rubric.creatorName || rubric.createId }}</span>
              <span>班级: {{ rubric.className }}</span>
              <span>创建时间: {{ formatDate(rubric.createTime) }}</span>
            </div>
          </div>
          <div class="rubric-arrow">&#8250;</div>
        </div>
      </div>
    </div>

    <!-- 全屏试卷详情 -->
    <div v-if="showRubricDetail" class="exam-overlay">
      <div class="exam-container">
        <!-- 顶部导航 -->
        <div class="exam-header">
          <button class="back-btn" @click="showRubricDetail = false">&#8592; 返回</button>
          <h2 class="exam-title">{{ currentRubric?.title }}</h2>
          <div class="mode-switch">
            <button 
              class="mode-btn" 
              :class="{ active: examMode === 'practice' }" 
              @click="examMode = 'practice'"
            >
              做题模式
            </button>
            <button 
              class="mode-btn" 
              :class="{ active: examMode === 'review' }" 
              @click="examMode = 'review'"
            >
              背题模式
            </button>
            <button 
              v-if="isCreator"
              class="mode-btn edit-mode-btn" 
              :class="{ active: examMode === 'edit' }" 
              @click="examMode = 'edit'"
            >
              修改题目
            </button>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="questionsLoading" class="loading-center"><div class="spinner"></div></div>
        
        <!-- 做题模式 -->
        <div v-else-if="examMode === 'practice'" class="practice-mode">
          <!-- 进度条 -->
          <div class="progress-bar">
            <div class="progress-text">第 {{ currentQuestionIndex + 1 }} / {{ questions.length }} 题</div>
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: ((currentQuestionIndex + 1) / questions.length * 100) + '%' }"></div>
            </div>
          </div>

          <!-- 当前题目 -->
          <div class="question-card">
            <div class="question-header">
              <span class="question-num">第 {{ currentQuestionIndex + 1 }} 题</span>
              <span class="question-type">{{ getTypeLabel(questions[currentQuestionIndex]?.questionType) }}</span>
            </div>
            <div class="question-text">{{ questions[currentQuestionIndex]?.questionText }}</div>
            
            <!-- 选择题选项 -->
            <div v-if="hasOptions(questions[currentQuestionIndex])" class="question-options">
              <div 
                v-for="(opt, oi) in getOptions(questions[currentQuestionIndex])" 
                :key="oi" 
                class="option-item"
                :class="{ 
                  selected: userAnswers[currentQuestionIndex] === String.fromCharCode(65 + oi),
                  correct: showAnswer && (String.fromCharCode(65 + oi) === getCorrectAnswer(questions[currentQuestionIndex]) || opt === getCorrectAnswer(questions[currentQuestionIndex]))
                }"
                @click="selectAnswer(String.fromCharCode(65 + oi))"
              >
                <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
                <span class="option-text">{{ opt }}</span>
              </div>
            </div>

            <!-- 简答题/计算题 输入框 -->
            <div v-if="isTextQuestion(questions[currentQuestionIndex])" class="text-answer-area">
              <textarea 
                v-model="userTextAnswers[currentQuestionIndex]"
                class="text-answer-input"
                placeholder="请输入你的答案..."
                :disabled="showAnswer"
              ></textarea>
            </div>

            <!-- 简答题/计算题 提交答案按钮 -->
            <div v-if="!showAnswer && isTextQuestion(questions[currentQuestionIndex]) && userTextAnswers[currentQuestionIndex]" class="submit-answer">
              <button class="btn btn-primary" @click="submitAnswer">确认答案</button>
            </div>

            <!-- 显示答案和解析 -->
            <div v-if="showAnswer" class="answer-section">
              <div class="your-answer" :class="{ correct: isCurrentAnswerCorrect() }">
                你的答案: {{ getUserAnswer() || '未作答' }}
              </div>
              <div class="correct-answer">
                正确答案: {{ getCorrectAnswer(questions[currentQuestionIndex]) }}
              </div>
              <div v-if="questions[currentQuestionIndex]?.explanation" class="question-explanation">
                解析: {{ questions[currentQuestionIndex].explanation }}
              </div>
            </div>

            <!-- 上一题/下一题按钮 -->
            <div class="nav-buttons">
              <button 
                class="btn btn-secondary" 
                :disabled="currentQuestionIndex === 0" 
                @click="prevQuestion"
              >
                上一题
              </button>
              <button 
                v-if="!showAnswer && isTextQuestion(questions[currentQuestionIndex])" 
                class="btn btn-ghost" 
                @click="skipQuestion"
              >
                跳过
              </button>
              <button 
                v-if="showAnswer" 
                class="btn btn-primary" 
                @click="nextQuestion"
              >
                {{ currentQuestionIndex === questions.length - 1 ? '完成' : '下一题' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 背题模式 -->
        <div v-else-if="examMode === 'review'" class="review-mode">
          <div class="questions-list">
            <div v-for="(q, idx) in questions" :key="q.id" class="question-item">
              <div class="question-header">
                <span class="question-num">第 {{ idx + 1 }} 题</span>
                <span class="question-type">{{ getTypeLabel(q.questionType) }}</span>
              </div>
              <div class="question-text">{{ q.questionText }}</div>
              <div v-if="getOptions(q).length" class="question-options">
                <div v-for="(opt, oi) in getOptions(q)" :key="oi" class="option">
                  {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                </div>
              </div>
              <div class="question-answer">答案: {{ formatAnswer(q) }}</div>
              <div v-if="q.explanation" class="question-explanation">解析: {{ q.explanation }}</div>
              <div v-if="q.calculationStepsJson" class="question-steps">
                <div>计算步骤:</div>
                <div v-for="(step, si) in parseJson(q.calculationStepsJson)" :key="si">{{ si + 1 }}. {{ step }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 修改题目模式 -->
        <div v-else-if="examMode === 'edit'" class="edit-mode">
          <div class="questions-list">
            <div v-for="(q, idx) in questions" :key="q.id" class="question-item editable">
              <div class="question-header">
                <span class="question-num">第 {{ idx + 1 }} 题</span>
                <select v-model="q.questionType" class="form-select type-select">
                  <option value="single_choice">单选题</option>
                  <option value="multiple_choice">多选题</option>
                  <option value="true_false">判断题</option>
                  <option value="short_answer">简答题</option>
                  <option value="calculation">计算题</option>
                </select>
              </div>
              <div class="edit-field">
                <label>题目内容：</label>
                <textarea v-model="q.questionText" class="edit-textarea" rows="3"></textarea>
              </div>
              <div v-if="needsOptions(q.questionType)" class="edit-field">
                <label>选项：</label>
                <div v-for="(_, oi) in getOptionsForEdit(q)" :key="oi" class="option-edit-row">
                  <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
                  <input v-model="getOptionsForEdit(q)[oi]" class="edit-input" :disabled="q.questionType === 'true_false'" />
                  <button 
                    v-if="q.questionType !== 'true_false' && getOptionsForEdit(q).length > 2" 
                    class="option-action-btn delete" 
                    @click="removeOption(q, oi)"
                    title="删除选项"
                  >×</button>
                </div>
                <button 
                  v-if="q.questionType !== 'true_false'" 
                  class="add-option-btn" 
                  @click="addOption(q)"
                >+ 添加选项</button>
              </div>
              <div class="edit-field">
                <label>答案：</label>
                <select v-if="q.questionType === 'true_false'" v-model="q.answer" class="edit-input">
                  <option value="">请选择答案</option>
                  <option value="正确">正确</option>
                  <option value="错误">错误</option>
                </select>
                <input v-else v-model="q.answer" class="edit-input" />
              </div>
              <div v-if="aiResults[idx]?.answer" class="ai-result-box">
                <label>AI生成答案：</label>
                <textarea v-model="aiResults[idx].answer" class="edit-textarea ai-textarea" rows="2"></textarea>
                <div class="ai-btn-row">
                  <button class="btn-use" @click="useAiAnswer(idx)">使用此答案</button>
                  <button class="btn-delete" @click="clearAiAnswer(idx)">删除</button>
                </div>
              </div>
              <div class="edit-field">
                <label>解析：</label>
                <textarea v-model="q.explanation" class="edit-textarea" rows="2"></textarea>
              </div>
              <div v-if="aiResults[idx]?.explanation" class="ai-result-box">
                <label>AI生成解析：</label>
                <textarea v-model="aiResults[idx].explanation" class="edit-textarea ai-textarea" rows="3"></textarea>
                <div class="ai-btn-row">
                  <button class="btn-use" @click="useAiExplanation(idx)">使用此解析</button>
                  <button class="btn-delete" @click="clearAiExplanation(idx)">删除</button>
                </div>
              </div>
              <div v-if="q.questionType === 'calculation'" class="edit-field">
                <label>计算步骤：</label>
                <textarea v-model="q.calculationStepsJson" class="edit-textarea" rows="3" placeholder="每行一个步骤"></textarea>
              </div>
              <div v-if="aiResults[idx]?.steps" class="ai-result-box">
                <label>AI生成步骤：</label>
                <textarea v-model="aiResults[idx].steps" class="edit-textarea ai-textarea" rows="4"></textarea>
                <div class="ai-btn-row">
                  <button class="btn-use" @click="useAiSteps(idx)">使用此步骤</button>
                  <button class="btn-delete" @click="clearAiSteps(idx)">删除</button>
                </div>
              </div>
              
              <!-- AI解答按钮 -->
              <div class="ai-actions">
                <button class="ai-btn primary" @click="generateAiAll(idx)" :disabled="aiLoading[idx]">
                  {{ aiLoading[idx] ? 'AI解答中...' : 'AI解答' }}
                </button>
                <button class="ai-btn success" @click="addQuestionAfter(idx)">此后添加题目</button>
                <button class="ai-btn danger" @click="deleteQuestion(idx)">删除题目</button>
              </div>
            </div>
          </div>
          
          <!-- 底部统一保存按钮 -->
          <div class="save-all-actions">
            <button class="btn btn-primary btn-lg" @click="saveAllQuestions" :disabled="saveAllLoading">
              <span v-if="saveAllLoading" class="spinner"></span>
              <span v-else>保存所有修改</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 提示弹窗 -->
    <div v-if="showToast" class="modal-overlay" @click="showToast = false">
      <div class="confirm-modal">
        <h3>{{ toastTitle }}</h3>
        <p>{{ toastMessage }}</p>
        <div class="confirm-btns">
          <button class="btn btn-primary" @click="showToast = false">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, watch } from 'vue'
import { apiGetClassPublicFiles, apiGetPublicRubrics, apiGetQuestionsByRubricId, apiAiSolveQuestion, apiDeleteFile, apiBatchSaveQuestions } from '@/api'
import { useUserStore } from '@/stores/user'
import type { HtmlFileItem, RubricItem, RubricQuestion } from '@/types'
import FileCard from '@/components/FileCard.vue'

const userStore = useUserStore()

// 标签页切换
const activeTab = ref<'files' | 'rubrics'>('files')

// 文件相关
const files = ref<HtmlFileItem[]>([])
const loading = ref(true)

// 试卷相关
const rubrics = ref<RubricItem[]>([])
const rubricLoading = ref(true)
const showRubricDetail = ref(false)
const currentRubric = ref<RubricItem | null>(null)
const questions = ref<RubricQuestion[]>([])
const questionsLoading = ref(false)

watch(questions, (newQuestions) => {
  newQuestions.forEach(q => {
    initQuestionOptions(q)
  })
}, { deep: true })

// 考试模式
const examMode = ref<'practice' | 'review' | 'edit'>('practice')
const currentQuestionIndex = ref(0)
const userAnswers = ref<Record<number, string>>({})  // 选择题/判断题答案
const userTextAnswers = ref<Record<number, string>>({})  // 简答题/计算题答案
const showAnswer = ref(false)

// 是否为创建者
const isCreator = ref(false)

// AI解答加载状态
const aiLoading = reactive<Record<number, boolean>>({})
const aiResults = reactive<Record<number, { answer?: string; explanation?: string; steps?: string }>>({})
const saveAllLoading = ref(false)

// 提示弹窗
const showToast = ref(false)
const toastTitle = ref('')
const toastMessage = ref('')

function showToastMsg(title: string, message: string) {
  toastTitle.value = title
  toastMessage.value = message
  showToast.value = true
}

onMounted(async () => {
  await loadFiles()
})

async function switchTab(tab: 'files' | 'rubrics') {
  activeTab.value = tab
  if (tab === 'rubrics' && rubrics.value.length === 0) {
    await loadRubrics()
  }
}

async function loadFiles() {
  try {
    const res = await apiGetClassPublicFiles()
    if (res.code === 200) files.value = res.data || []
  } catch (e) { console.error('获取班级共享文件失败', e) }
  finally { loading.value = false }
}

async function loadRubrics() {
  try {
    const res = await apiGetPublicRubrics()
    if (res.code === 200) {
      rubrics.value = res.data || []
    }
  } catch (e) { console.error('获取公开试卷列表失败', e) }
  finally { rubricLoading.value = false }
}

async function handleDeleteFile(file: HtmlFileItem) {
  if (!confirm(`确定要删除文件"${file.fileName}"吗？`)) return
  
  try {
    const res = await apiDeleteFile(file.id)
    if (res.code === 200) {
      files.value = files.value.filter(f => f.id !== file.id)
    }
  } catch (e) {
    console.error('删除文件失败', e)
    alert('删除文件失败')
  }
}

function handleFileUpdated(updatedFile: HtmlFileItem) {
  const idx = files.value.findIndex(f => f.id === updatedFile.id)
  if (idx !== -1) {
    files.value[idx] = updatedFile
  }
}

async function handleOpenRubric(rubric: RubricItem) {
  currentRubric.value = rubric
  showRubricDetail.value = true
  questionsLoading.value = true
  examMode.value = 'practice'
  
  isCreator.value = rubric.createId === userStore.userId
  
  try {
    const res = await apiGetQuestionsByRubricId(rubric.id)
    if (res.code === 200) {
      questions.value = res.data || []
    }
  } catch (e) {
    console.error('获取题目列表失败', e)
  } finally {
    questionsLoading.value = false
  }
}

async function generateAiAll(idx: number) {
  const q = questions.value[idx]
  if (!q) return
  
  aiLoading[idx] = true
  try {
    const res = await apiAiSolveQuestion({
      questionText: q.questionText,
      questionType: q.questionType,
      optionsJson: q.optionsJson,
      generateType: 'all',
      userId: userStore.userId!
    })
    if (res.code === 200) {
      const data = JSON.parse(res.data)
      aiResults[idx] = {
        answer: data.answer || '',
        explanation: data.explanation || '',
        steps: data.steps || ''
      }
      showToastMsg('成功', 'AI解答完成，请查看下方生成结果')
    } else {
      const errorMsg = res.message || ''
      if (errorMsg.includes('API') || errorMsg.includes('key') || errorMsg.includes('Key') || errorMsg.includes('model') || errorMsg.includes('Model')) {
        showToastMsg('错误', '上传的API Key或模型名称有误，请核验')
      } else {
        showToastMsg('错误', res.message || 'AI解答失败')
      }
    }
  } catch (e: unknown) {
    console.error('AI解答失败', e)
    const errorMsg = (e as Error)?.message || ''
    if (errorMsg.includes('API') || errorMsg.includes('key') || errorMsg.includes('Key') || errorMsg.includes('model') || errorMsg.includes('Model') || errorMsg.includes('401') || errorMsg.includes('403') || errorMsg.includes('invalid')) {
      showToastMsg('错误', '上传的API Key或模型名称有误，请核验')
    } else {
      showToastMsg('错误', 'AI解答失败')
    }
  } finally {
    aiLoading[idx] = false
  }
}

function useAiAnswer(idx: number) {
  if (aiResults[idx]?.answer) {
    const q = questions.value[idx]
    if (q.questionType === 'true_false') {
      q.answer = normalizeAnswerText(aiResults[idx].answer!)
    } else {
      q.answer = aiResults[idx].answer
    }
  }
}

function useAiExplanation(idx: number) {
  if (aiResults[idx]?.explanation) {
    questions.value[idx].explanation = aiResults[idx].explanation
  }
}

function useAiSteps(idx: number) {
  if (aiResults[idx]?.steps) {
    const steps = aiResults[idx].steps.split('\n').filter((s: string) => s.trim())
    questions.value[idx].calculationStepsJson = JSON.stringify(steps)
  }
}

function clearAiAnswer(idx: number) {
  if (aiResults[idx]) {
    aiResults[idx].answer = ''
  }
}

function clearAiExplanation(idx: number) {
  if (aiResults[idx]) {
    aiResults[idx].explanation = ''
  }
}

function clearAiSteps(idx: number) {
  if (aiResults[idx]) {
    aiResults[idx].steps = ''
  }
}

function addQuestionAfter(idx: number) {
  const newQuestion: RubricQuestion = {
    id: -Date.now(),
    rubricId: currentRubric.value?.id || 0,
    questionType: 'single_choice',
    questionText: '',
    optionsJson: '[]',
    answer: '',
    explanation: '',
    calculationStepsJson: '',
    orderIndex: idx + 2
  }
  questions.value.splice(idx + 1, 0, newQuestion)
}

function deleteQuestion(idx: number) {
  if (confirm('确定要删除这道题目吗？')) {
    questions.value.splice(idx, 1)
  }
}

async function saveAllQuestions() {
  if (!currentRubric.value) return
  
  saveAllLoading.value = true
  try {
    const questionsData = questions.value.map((q, idx) => ({
      questionType: q.questionType,
      questionText: q.questionText,
      optionsJson: q.optionsJson,
      answer: q.answer,
      explanation: q.explanation,
      calculationStepsJson: q.calculationStepsJson,
      orderIndex: idx + 1
    }))
    
    const res = await apiBatchSaveQuestions(currentRubric.value.id, questionsData)
    if (res.code === 200) {
      showToastMsg('成功', '保存成功')
    } else {
      showToastMsg('错误', res.message || '保存失败')
    }
  } catch (e) {
    console.error('保存题目失败', e)
    showToastMsg('错误', '保存失败')
  } finally {
    saveAllLoading.value = false
  }
}

// 预览文件 - 在新窗口打开
async function handlePreviewFile(file: HtmlFileItem) {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/files/download/${file.id}`, {
      headers: { 'token': token || '' }
    })
    if (!response.ok) {
      throw new Error('预览失败')
    }
    const blob = await response.blob()
    
    // 将 blob 转换为 text/html 类型，确保浏览器能正确打开
    const htmlBlob = new Blob([blob], { type: 'text/html;charset=utf-8' })
    const blobUrl = URL.createObjectURL(htmlBlob)
    const newWindow = window.open(blobUrl, '_blank')
    if (!newWindow) {
      alert('无法打开新窗口，请允许弹窗')
    }
    // 延迟释放 URL，确保新窗口能够加载
    setTimeout(() => {
      URL.revokeObjectURL(blobUrl)
    }, 10000)
  } catch (e) {
    console.error('预览文件失败', e)
    alert('预览文件失败')
  }
}

// 下载文件 - 直接触发下载
async function handleDownloadFile(file: HtmlFileItem) {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/files/download/${file.id}`, {
      headers: { 'token': token || '' }
    })
    if (!response.ok) {
      throw new Error('下载失败')
    }
    const blob = await response.blob()
    
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    let fileName = file.fileName
    if (!fileName.toLowerCase().endsWith('.html')) {
      fileName += '.html'
    }
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    console.error('下载文件失败', e)
    alert('下载文件失败')
  }
}

// 判断题目是否有选项
function hasOptions(q: RubricQuestion): boolean {
  if (!q.optionsJson) return false
  try {
    const opts = JSON.parse(q.optionsJson)
    return Array.isArray(opts) && opts.length > 0
  } catch { return false }
}

function needsOptions(questionType: string): boolean {
  return questionType === 'single_choice' || questionType === 'multiple_choice' || questionType === 'true_false'
}

function getOptionsForEdit(q: RubricQuestion): string[] {
  if (q.questionType === 'true_false') {
    return ['正确', '错误']
  }
  if (!q.optionsJson) {
    return ['', '', '', '']
  }
  try {
    const opts = JSON.parse(q.optionsJson)
    return Array.isArray(opts) && opts.length > 0 ? opts : ['', '', '', '']
  } catch { 
    return ['', '', '', '']
  }
}

function addOption(q: RubricQuestion) {
  const options = getOptionsForEdit(q)
  options.push('')
  q.optionsJson = JSON.stringify(options)
}

function removeOption(q: RubricQuestion, index: number) {
  const options = getOptionsForEdit(q)
  if (options.length > 2) {
    options.splice(index, 1)
    q.optionsJson = JSON.stringify(options)
  }
}

function initQuestionOptions(q: RubricQuestion) {
  if (q.questionType === 'true_false') {
    q.optionsJson = JSON.stringify(['正确', '错误'])
    normalizeTrueFalseAnswer(q)
  } else if ((q.questionType === 'single_choice' || q.questionType === 'multiple_choice') && !q.optionsJson) {
    q.optionsJson = JSON.stringify(['', '', '', ''])
  }
}

function normalizeTrueFalseAnswer(q: RubricQuestion) {
  if (q.questionType !== 'true_false' || !q.answer) return
  const answer = q.answer.trim()
  const positiveKeywords = ['√', '正确', '对', '是', 'yes', 'YES', 'Yes', 'true', 'TRUE', 'True', '1', 'A', 'a']
  const negativeKeywords = ['×', '错误', '错', '否', 'no', 'NO', 'No', 'false', 'FALSE', 'False', '0', 'B', 'b']
  
  if (positiveKeywords.includes(answer)) {
    q.answer = '正确'
  } else if (negativeKeywords.includes(answer)) {
    q.answer = '错误'
  }
}

function normalizeAnswerText(answer: string): string {
  if (!answer) return ''
  const trimmed = answer.trim()
  const positiveKeywords = ['√', '正确', '对', '是', 'yes', 'YES', 'Yes', 'true', 'TRUE', 'True', '1', 'A', 'a']
  const negativeKeywords = ['×', '错误', '错', '否', 'no', 'NO', 'No', 'false', 'FALSE', 'False', '0', 'B', 'b']
  
  if (positiveKeywords.includes(trimmed)) {
    return '正确'
  } else if (negativeKeywords.includes(trimmed)) {
    return '错误'
  }
  return answer
}

function formatAnswer(q: RubricQuestion): string {
  if (q.questionType === 'true_false') {
    return normalizeAnswerText(q.answer || '')
  }
  return q.answer || ''
}

// 获取选项数组
function getOptions(q: RubricQuestion): string[] {
  if (!q.optionsJson) return []
  try {
    return JSON.parse(q.optionsJson)
  } catch { return [] }
}

// 解析JSON
function parseJson(str: string): string[] {
  if (!str) return []
  try {
    return JSON.parse(str)
  } catch { return [] }
}

// 获取正确答案
function getCorrectAnswer(q: RubricQuestion): string {
  return q.answer || ''
}

// 判断是否为简答题/计算题
function isTextQuestion(q: RubricQuestion): boolean {
  if (!q) return false
  return q.questionType === 'short_answer' || q.questionType === 'calculation'
}

// 选择答案
function selectAnswer(answer: string) {
  if (showAnswer.value) return
  userAnswers.value[currentQuestionIndex.value] = answer
  
  const q = questions.value[currentQuestionIndex.value]
  if (hasOptions(q)) {
    const options = getOptions(q)
    const selectedIndex = answer.charCodeAt(0) - 65
    const selectedContent = options[selectedIndex]
    const isCorrect = answer === q.answer || selectedContent === q.answer
    if (isCorrect) {
      setTimeout(() => {
        if (currentQuestionIndex.value < questions.value.length - 1) {
          currentQuestionIndex.value++
          showAnswer.value = false
        } else {
          showRubricDetail.value = false
        }
      }, 300)
    } else {
      showAnswer.value = true
    }
  }
}

// 提交答案
function submitAnswer() {
  const q = questions.value[currentQuestionIndex.value]
  if (isTextQuestion(q) && userTextAnswers.value[currentQuestionIndex.value]) {
    userAnswers.value[currentQuestionIndex.value] = userTextAnswers.value[currentQuestionIndex.value]
  }
  showAnswer.value = true
}

// 判断当前答案是否正确
function isCurrentAnswerCorrect(): boolean {
  const q = questions.value[currentQuestionIndex.value]
  if (!q) return false
  const userAnswer = userAnswers.value[currentQuestionIndex.value]
  if (!userAnswer) return false
  
  if (hasOptions(q)) {
    const options = getOptions(q)
    const selectedIndex = userAnswer.charCodeAt(0) - 65
    const selectedContent = options[selectedIndex]
    return userAnswer === q.answer || selectedContent === q.answer
  }
  return userAnswer === q.answer
}

// 上一题
function prevQuestion() {
  if (currentQuestionIndex.value > 0) {
    currentQuestionIndex.value--
    showAnswer.value = !!userAnswers.value[currentQuestionIndex.value] || !!userTextAnswers.value[currentQuestionIndex.value]
  }
}

// 下一题
function nextQuestion() {
  if (currentQuestionIndex.value < questions.value.length - 1) {
    currentQuestionIndex.value++
    showAnswer.value = false
  } else {
    showRubricDetail.value = false
  }
}

// 跳过当前题
function skipQuestion() {
  if (currentQuestionIndex.value < questions.value.length - 1) {
    currentQuestionIndex.value++
    showAnswer.value = false
  } else {
    showRubricDetail.value = false
  }
}

// 获取当前题目用户填写的答案
function getUserAnswer(): string {
  const q = questions.value[currentQuestionIndex.value]
  if (!q) return ''
  if (isTextQuestion(q)) {
    return userTextAnswers.value[currentQuestionIndex.value] || ''
  }
  return userAnswers.value[currentQuestionIndex.value] || ''
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  return dateStr.substring(0, 10)
}

function getTypeLabel(type: string): string {
  const map: Record<string, string> = {
    single_choice: '单选题',
    multiple_choice: '多选题',
    true_false: '判断题',
    short_answer: '简答题',
    calculation: '计算题'
  }
  return map[type] || type
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.page-hint { font-size: 14px; color: var(--text-muted); }
.loading-center { display: flex; justify-content: center; padding: 64px 0; }

/* 标签切换 */
.tab-switch { display: flex; gap: 8px; margin-bottom: 24px; }
.tab-btn { padding: 10px 24px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); color: var(--text-muted); cursor: pointer; transition: all 0.3s ease; font-size: 14px; font-weight: 500; }
.tab-btn:hover { border-color: var(--accent); color: var(--text-secondary); }
.tab-btn.active { background: var(--accent-gradient); color: #fff; border-color: transparent; box-shadow: 0 4px 16px rgba(99,102,241,0.3); }

/* 试卷列表 */
.rubric-list { display: flex; flex-direction: column; gap: 16px; }
.rubric-card { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border: 1px solid var(--border-glass); border-radius: 16px; cursor: pointer; transition: all 0.3s ease; background: var(--card-bg); position: relative; overflow: hidden; }
.rubric-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: var(--accent-gradient); opacity: 0; transition: opacity 0.3s ease; }
.rubric-card:hover { border-color: var(--accent-border); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.2); }
.rubric-card:hover::before { opacity: 1; }
.rubric-title { font-size: 17px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.rubric-meta { font-size: 13px; color: var(--text-muted); display: flex; gap: 20px; }
.rubric-arrow { font-size: 24px; color: var(--text-muted); transition: transform 0.3s ease; }
.rubric-card:hover .rubric-arrow { transform: translateX(4px); color: var(--accent); }

/* 全屏试卷详情 */
.exam-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--bg-primary); z-index: 1000; overflow-y: auto; }
.exam-container { max-width: 800px; margin: 0 auto; padding: 24px; }
.exam-header { display: flex; align-items: center; gap: 16px; margin-bottom: 28px; padding-bottom: 20px; border-bottom: 1px solid var(--border-glass); }
.back-btn { padding: 10px 20px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); cursor: pointer; color: var(--text-secondary); transition: all 0.3s ease; font-size: 14px; }
.back-btn:hover { background: var(--accent-light); border-color: var(--accent-border); color: var(--accent); }
.exam-title { flex: 1; font-size: 20px; font-weight: 600; text-align: center; }
.mode-switch { display: flex; gap: 8px; }
.mode-btn { padding: 8px 16px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); cursor: pointer; color: var(--text-secondary); transition: all 0.3s ease; }
.mode-btn:hover { color: var(--text-primary); border-color: var(--accent-border); }
.mode-btn.active { background: var(--accent-gradient); color: #fff; border-color: transparent; box-shadow: 0 4px 16px rgba(99,102,241,0.3); }

/* 做题模式 */
.practice-mode { display: flex; flex-direction: column; gap: 20px; }
.progress-bar { text-align: center; }
.progress-text { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; }
.progress-track { height: 8px; background: var(--bg-glass); border-radius: 4px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--accent-gradient); transition: width 0.3s ease; border-radius: 4px; }

.question-card { padding: 28px; border: 1px solid var(--border-glass); border-radius: 20px; background: var(--card-bg); position: relative; }
.question-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: var(--accent-gradient); border-radius: 20px 20px 0 0; }
.question-header { display: flex; justify-content: space-between; margin-bottom: 16px; }
.question-num { font-weight: 600; font-size: 16px; }
.question-type { font-size: 12px; padding: 6px 14px; background: var(--accent-light); color: var(--accent); border-radius: 20px; border: 1px solid var(--accent-border); }
.question-text { font-size: 18px; line-height: 1.8; margin-bottom: 20px; }

.question-options { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.option-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border: 1px solid var(--border-glass); border-radius: 14px; cursor: pointer; transition: all 0.3s ease; color: var(--text-primary); }
.option-item:hover { border-color: var(--accent); background: rgba(99,102,241,0.05); }
.option-item.selected { border-color: var(--accent); background: var(--accent-light); color: var(--accent); }
.option-item.correct { border-color: var(--success); background: var(--success-light); color: var(--success); }
.option-label { font-weight: 600; width: 24px; }

.true-false-options { display: flex; gap: 16px; margin-bottom: 20px; }
.tf-btn { flex: 1; padding: 16px; border: 1px solid var(--border-glass); border-radius: 14px; background: var(--card-bg); font-size: 16px; cursor: pointer; transition: all 0.3s ease; color: var(--text-primary); }
.tf-btn:hover { border-color: var(--accent); background: rgba(99,102,241,0.05); }
.tf-btn.selected { border-color: var(--accent); background: var(--accent-light); color: var(--accent); }
.tf-btn.correct { border-color: var(--success); background: var(--success-light); color: var(--success); }

.text-answer-area { margin-bottom: 24px; }
.text-answer-input { width: 100%; min-height: 160px; padding: 16px; border: 1px solid var(--border-glass); border-radius: 14px; background: var(--bg-glass); color: var(--text-primary); font-size: 15px; font-family: inherit; resize: vertical; line-height: 1.7; }
.text-answer-input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.text-answer-input:disabled { opacity: 0.7; cursor: not-allowed; }

.submit-answer { text-align: center; margin-bottom: 20px; }

.answer-section { padding: 20px; border-radius: 14px; margin-bottom: 24px; background: var(--bg-glass); border: 1px solid var(--border-glass); }
.your-answer { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.your-answer.correct { color: var(--success); }
.correct-answer { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }

.nav-buttons { display: flex; gap: 12px; justify-content: center; }

/* 背题模式 */
.review-mode .questions-list { display: flex; flex-direction: column; gap: 24px; }
.review-mode .question-item { padding: 24px; border: 1px solid var(--border-glass); border-radius: 16px; background: var(--card-bg); }
.review-mode .question-header { margin-bottom: 16px; }
.review-mode .question-text { margin-bottom: 20px; }
.review-mode .question-options { margin-bottom: 16px; }
.review-mode .option { padding: 8px 0; }
.review-mode .question-answer { padding: 12px 16px; background: var(--success-light); border-radius: 10px; margin-bottom: 10px; color: var(--success); border: 1px solid rgba(16,185,129,0.2); }
.review-mode .question-explanation { padding: 12px 16px; background: rgba(59,130,246,0.08); border-radius: 10px; color: #3b82f6; margin-bottom: 10px; border: 1px solid rgba(59,130,246,0.15); }
.review-mode .question-steps { padding: 12px 16px; background: rgba(236,72,153,0.08); border-radius: 10px; color: var(--secondary); border: 1px solid rgba(236,72,153,0.15); }

/* 修改题目模式 */
.edit-mode-btn { background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%) !important; border-color: transparent !important; }
.edit-mode-btn.active { box-shadow: 0 4px 16px rgba(245,158,11,0.4) !important; }

.edit-mode .questions-list { display: flex; flex-direction: column; gap: 24px; }
.edit-mode .question-item.editable { padding: 24px; border: 1px solid var(--border-glass); border-radius: 16px; background: var(--card-bg); }
.edit-mode .question-header { margin-bottom: 16px; }
.edit-mode .question-text { margin-bottom: 20px; }
.edit-mode .question-options { margin-bottom: 16px; }
.edit-mode .option { padding: 8px 0; }
.edit-mode .question-answer { padding: 12px 16px; background: var(--success-light); border-radius: 10px; margin-bottom: 10px; color: var(--success); border: 1px solid rgba(16,185,129,0.2); }
.edit-mode .question-explanation { padding: 12px 16px; background: rgba(59,130,246,0.08); border-radius: 10px; color: #3b82f6; margin-bottom: 10px; border: 1px solid rgba(59,130,246,0.15); }
.edit-mode .question-steps { padding: 12px 16px; background: rgba(236,72,153,0.08); border-radius: 10px; color: var(--secondary); border: 1px solid rgba(236,72,153,0.15); margin-bottom: 16px; }

.ai-actions { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 16px; padding-top: 16px; border-top: 1px dashed var(--border-glass); }
.ai-btn { padding: 10px 20px; border: 1px solid var(--accent-border); border-radius: 10px; background: var(--accent-light); color: var(--accent); cursor: pointer; transition: all 0.3s ease; font-size: 13px; font-weight: 500; }
.ai-btn:hover:not(:disabled) { background: var(--accent-gradient); color: #fff; border-color: transparent; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(99,102,241,0.3); }
.ai-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.ai-btn.primary { background: var(--accent-gradient); color: #fff; border-color: transparent; }
.ai-btn.success { background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: #fff; border-color: transparent; }
.ai-btn.danger { background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%); color: #fff; border-color: transparent; }

.save-all-actions { display: flex; justify-content: center; padding: 24px 0; margin-top: 16px; border-top: 1px solid var(--border-glass); }
.btn-lg { padding: 14px 40px; font-size: 16px; font-weight: 600; }

.type-select { padding: 6px 12px; border: 1px solid var(--border-glass); border-radius: 8px; background: var(--card-bg); color: var(--text-primary); font-size: 12px; cursor: pointer; }
.type-select option { background: var(--card-bg); color: var(--text-primary); }
.edit-field { margin-bottom: 16px; }
.edit-field label { display: block; font-size: 13px; color: var(--text-muted); margin-bottom: 6px; font-weight: 500; }
.edit-textarea { width: 100%; padding: 12px; border: 1px solid var(--border-glass); border-radius: 10px; background: var(--bg-glass); color: var(--text-primary); font-size: 14px; font-family: inherit; resize: vertical; line-height: 1.6; }
.edit-textarea:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.edit-input { width: 100%; padding: 10px 12px; border: 1px solid var(--border-glass); border-radius: 10px; background: var(--bg-glass); color: var(--text-primary); font-size: 14px; }
.edit-input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.option-edit-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.option-edit-row .option-label { font-weight: 600; color: var(--text-secondary); min-width: 24px; }
.option-edit-row input:disabled { background: var(--bg-glass); color: var(--text-muted); cursor: not-allowed; }
.option-action-btn { width: 28px; height: 28px; border: none; border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 16px; transition: all 0.2s; }
.option-action-btn.delete { background: rgba(239,68,68,0.1); color: #ef4444; }
.option-action-btn.delete:hover { background: rgba(239,68,68,0.2); }
.add-option-btn { margin-top: 8px; padding: 8px 16px; border: 1px dashed var(--border-glass); border-radius: 8px; background: transparent; color: var(--accent); font-size: 13px; cursor: pointer; transition: all 0.2s; }
.add-option-btn:hover { background: var(--accent-light); border-color: var(--accent); }

.ai-result-box { margin-bottom: 16px; padding: 12px; border: 1px dashed var(--accent-border); border-radius: 10px; background: rgba(99,102,241,0.05); }
.ai-result-box label { display: block; font-size: 13px; color: var(--accent); margin-bottom: 6px; font-weight: 600; }
.ai-textarea { border-color: var(--accent-border) !important; background: rgba(255,255,255,0.5) !important; }
.ai-btn-row { display: flex; gap: 8px; margin-top: 8px; }
.btn-use { padding: 6px 16px; border: none; border-radius: 8px; background: var(--accent-gradient); color: #fff; font-size: 12px; cursor: pointer; transition: all 0.3s ease; }
.btn-use:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(99,102,241,0.3); }
.btn-delete { padding: 6px 16px; border: 1px solid #ef4444; border-radius: 8px; background: transparent; color: #ef4444; font-size: 12px; cursor: pointer; transition: all 0.3s ease; }
.btn-delete:hover { background: #ef4444; color: #fff; }

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; backdrop-filter: blur(4px); }
.confirm-modal { background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 16px; padding: 24px; min-width: 320px; text-align: center; }
.confirm-modal h3 { margin: 0 0 12px; font-size: 18px; color: var(--text-primary); }
.confirm-modal p { margin: 0 0 20px; color: var(--text-secondary); font-size: 14px; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }
</style>