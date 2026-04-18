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
              <div class="question-answer">答案: {{ q.answer }}</div>
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
                <span class="question-type">{{ getTypeLabel(q.questionType) }}</span>
              </div>
              <div class="question-text">{{ q.questionText }}</div>
              <div v-if="getOptions(q).length" class="question-options">
                <div v-for="(opt, oi) in getOptions(q)" :key="oi" class="option">
                  {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                </div>
              </div>
              <div class="question-answer">答案: {{ q.answer }}</div>
              <div v-if="q.explanation" class="question-explanation">解析: {{ q.explanation }}</div>
              <div v-if="q.calculationStepsJson" class="question-steps">
                <div>计算步骤:</div>
                <div v-for="(step, si) in parseJson(q.calculationStepsJson)" :key="si">{{ si + 1 }}. {{ step }}</div>
              </div>
              
              <!-- AI解答按钮 -->
              <div class="ai-actions">
                <button class="ai-btn" @click="generateAiAnswer(idx)" :disabled="aiLoading[idx]">
                  {{ aiLoading[idx] ? '生成中...' : 'AI生成答案' }}
                </button>
                <button class="ai-btn" @click="generateAiExplanation(idx)" :disabled="aiLoading[idx]">
                  {{ aiLoading[idx] ? '生成中...' : 'AI生成解析' }}
                </button>
                <button 
                  v-if="q.questionType === 'calculation'" 
                  class="ai-btn" 
                  @click="generateAiSteps(idx)" 
                  :disabled="aiLoading[idx]"
                >
                  {{ aiLoading[idx] ? '生成中...' : 'AI生成步骤' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { apiGetClassPublicFiles, apiGetPublicRubrics, apiGetQuestionsByRubricId, apiAiSolveQuestion, apiDeleteFile } from '@/api'
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
    console.log('获取公开试卷响应:', res)
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

async function generateAiAnswer(idx: number) {
  const q = questions.value[idx]
  if (!q) return
  
  aiLoading[idx] = true
  try {
    const res = await apiAiSolveQuestion({
      questionText: q.questionText,
      questionType: q.questionType,
      optionsJson: q.optionsJson,
      generateType: 'answer',
      userId: userStore.userId
    })
    if (res.code === 200) {
      questions.value[idx].answer = res.data
    }
  } catch (e) {
    console.error('AI生成答案失败', e)
    alert('AI生成答案失败')
  } finally {
    aiLoading[idx] = false
  }
}

async function generateAiExplanation(idx: number) {
  const q = questions.value[idx]
  if (!q) return
  
  aiLoading[idx] = true
  try {
    const res = await apiAiSolveQuestion({
      questionText: q.questionText,
      questionType: q.questionType,
      optionsJson: q.optionsJson,
      generateType: 'explanation',
      userId: userStore.userId
    })
    if (res.code === 200) {
      questions.value[idx].explanation = res.data
    }
  } catch (e) {
    console.error('AI生成解析失败', e)
    alert('AI生成解析失败')
  } finally {
    aiLoading[idx] = false
  }
}

async function generateAiSteps(idx: number) {
  const q = questions.value[idx]
  if (!q || q.questionType !== 'calculation') return
  
  aiLoading[idx] = true
  try {
    const res = await apiAiSolveQuestion({
      questionText: q.questionText,
      questionType: q.questionType,
      optionsJson: q.optionsJson,
      generateType: 'steps',
      userId: userStore.userId
    })
    if (res.code === 200) {
      const steps = res.data?.split('\n').filter((s: string) => s.trim()) || []
      questions.value[idx].calculationStepsJson = JSON.stringify(steps)
    }
  } catch (e) {
    console.error('AI生成步骤失败', e)
    alert('AI生成步骤失败')
  } finally {
    aiLoading[idx] = false
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

// 判断当前题目是否已有答案
function hasAnswer(index: number): boolean {
  const q = questions.value[index]
  if (!q) return false
  if (userAnswers.value[index]) return true
  if (userTextAnswers.value[index]?.trim()) return true
  return false
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
</style>