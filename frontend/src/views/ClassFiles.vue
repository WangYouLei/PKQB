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
        <FileCard v-for="file in files" :key="file.id" :file="file" @open="handleOpenFile" />
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
              <span>创建者ID: {{ rubric.createId }}</span>
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
                  correct: showAnswer && String.fromCharCode(65 + oi) === getCorrectAnswer(questions[currentQuestionIndex])
                }"
                @click="selectAnswer(String.fromCharCode(65 + oi))"
              >
                <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
                <span class="option-text">{{ opt }}</span>
              </div>
            </div>

            <!-- 判断题选项 -->
            <div v-if="questions[currentQuestionIndex]?.questionType === 'true_false'" class="true-false-options">
              <button 
                class="tf-btn" 
                :class="{ selected: userAnswers[currentQuestionIndex] === '正确', correct: showAnswer && getCorrectAnswer(questions[currentQuestionIndex]) === '正确' }"
                @click="selectAnswer('正确')"
              >
                正确
              </button>
              <button 
                class="tf-btn" 
                :class="{ selected: userAnswers[currentQuestionIndex] === '错误', correct: showAnswer && getCorrectAnswer(questions[currentQuestionIndex]) === '错误' }"
                @click="selectAnswer('错误')"
              >
                错误
              </button>
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

            <!-- 提交答案按钮 -->
            <div v-if="!showAnswer && hasAnswer(currentQuestionIndex)" class="submit-answer">
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
                v-if="!showAnswer && hasAnswer(currentQuestionIndex)" 
                class="btn btn-secondary" 
                @click="submitAnswer"
              >
                显示答案
              </button>
              <button 
                v-if="!showAnswer" 
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { apiGetClassPublicFiles, apiGetPresignedUrl, apiGetPublicRubrics, apiGetQuestionsByRubricId } from '@/api'
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
const examMode = ref<'practice' | 'review'>('practice')
const currentQuestionIndex = ref(0)
const userAnswers = ref<Record<number, string>>({})  // 选择题/判断题答案
const userTextAnswers = ref<Record<number, string>>({})  // 简答题/计算题答案
const showAnswer = ref(false)

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
    if (res.code === 200) files.value = res.data?.data || []
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

async function handleOpenFile(file: HtmlFileItem) {
  currentRubric.value = rubric
  showRubricDetail.value = true
  examMode.value = 'practice'
  currentQuestionIndex.value = 0
  userAnswers.value = {}
  userTextAnswers.value = {}
  showAnswer.value = false
  questionsLoading.value = true
  try {
    const res = await apiGetQuestionsByRubricId(rubric.id)
    if (res.code === 200) {
      questions.value = res.data || []
    }
  } catch (e) { console.error('获取题目列表失败', e) }
  finally { questionsLoading.value = false }
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
  return userAnswers.value[currentQuestionIndex.value] === q.answer
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
.tab-btn { padding: 8px 20px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--card-bg); color: var(--text-muted); cursor: pointer; transition: all 0.2s; }
.tab-btn:hover { border-color: var(--accent); }
.tab-btn.active { background: var(--accent); color: #fff; border-color: var(--accent); }

/* 试卷列表 */
.rubric-list { display: flex; flex-direction: column; gap: 12px; }
.rubric-card { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border: 1px solid var(--border-color); border-radius: 12px; cursor: pointer; transition: all 0.2s; }
.rubric-card:hover { border-color: var(--accent); background: var(--accent-light); }
.rubric-title { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.rubric-meta { font-size: 13px; color: var(--text-muted); display: flex; gap: 16px; }
.rubric-arrow { font-size: 24px; color: var(--text-muted); }

/* 全屏试卷详情 */
.exam-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--bg-primary); z-index: 1000; overflow-y: auto; }
.exam-container { max-width: 800px; margin: 0 auto; padding: 24px; }
.exam-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--border-color); }
.back-btn { padding: 8px 16px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--card-bg); cursor: pointer; }
.exam-title { flex: 1; font-size: 20px; font-weight: 600; text-align: center; }
.mode-switch { display: flex; gap: 8px; }
.mode-btn { padding: 8px 16px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--card-bg); cursor: pointer; }
.mode-btn.active { background: var(--accent); color: #fff; border-color: var(--accent); }

/* 做题模式 */
.practice-mode { display: flex; flex-direction: column; gap: 20px; }
.progress-bar { text-align: center; }
.progress-text { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; }
.progress-track { height: 6px; background: var(--border-color); border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--accent); transition: width 0.3s; }

.question-card { padding: 24px; border: 1px solid var(--border-color); border-radius: 12px; background: var(--card-bg); }
.question-header { display: flex; justify-content: space-between; margin-bottom: 16px; }
.question-num { font-weight: 600; font-size: 16px; }
.question-type { font-size: 12px; padding: 4px 12px; background: var(--accent-light); color: var(--accent); border-radius: 12px; }
.question-text { font-size: 18px; line-height: 1.8; margin-bottom: 20px; }

.question-options { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.option-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border: 1px solid var(--border-color); border-radius: 8px; cursor: pointer; transition: all 0.2s; }
.option-item:hover { border-color: var(--accent); }
.option-item.selected { border-color: var(--accent); background: var(--accent-light); }
.option-item.correct { border-color: #22c55e; background: rgba(34,197,94,0.1); }
.option-label { font-weight: 600; width: 24px; }

.true-false-options { display: flex; gap: 16px; margin-bottom: 20px; }
.tf-btn { flex: 1; padding: 16px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--card-bg); font-size: 16px; cursor: pointer; transition: all 0.2s; }
.tf-btn:hover { border-color: var(--accent); }
.tf-btn.selected { border-color: var(--accent); background: var(--accent-light); }
.tf-btn.correct { border-color: #22c55e; background: rgba(34,197,94,0.1); }

.text-answer-area { margin-bottom: 20px; }
.text-answer-input { width: 100%; min-height: 150px; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--card-bg); color: var(--text-primary); font-size: 16px; font-family: inherit; resize: vertical; line-height: 1.6; }
.text-answer-input:focus { outline: none; border-color: var(--accent); }
.text-answer-input:disabled { opacity: 0.7; cursor: not-allowed; }

.submit-answer { text-align: center; margin-bottom: 20px; }

.answer-section { padding: 16px; border-radius: 8px; margin-bottom: 20px; }
.your-answer { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.your-answer.correct { color: #22c55e; }
.correct-answer { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }

.nav-buttons { display: flex; gap: 12px; justify-content: center; }

/* 背题模式 */
.review-mode .questions-list { display: flex; flex-direction: column; gap: 20px; }
.review-mode .question-item { padding: 20px; border: 1px solid var(--border-color); border-radius: 12px; }
.review-mode .question-header { margin-bottom: 12px; }
.review-mode .question-text { margin-bottom: 16px; }
.review-mode .question-options { margin-bottom: 12px; }
.review-mode .option { padding: 6px 0; }
.review-mode .question-answer { padding: 8px; background: rgba(34,197,94,0.1); border-radius: 6px; margin-bottom: 8px; color: #16a34a; }
.review-mode .question-explanation { padding: 8px; background: rgba(59,130,246,0.1); border-radius: 6px; color: #2563eb; margin-bottom: 8px; }
.review-mode .question-steps { padding: 8px; background: rgba(236,72,153,0.1); border-radius: 6px; color: #ec4899; }
</style>