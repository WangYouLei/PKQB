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
        
        <!-- 空题目 -->
        <div v-else-if="questions.length === 0" class="empty-state">
          <div class="empty-state-icon">&#128220;</div>
          <div class="empty-state-text">该试卷暂无题目</div>
          <div class="empty-state-hint">请先为试卷添加题目</div>
        </div>

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
            <template v-if="questions[currentQuestionIndex]?.resources?.length">
              <img v-for="img in getQuestionImages(questions[currentQuestionIndex].resources)" :key="img.url" :src="img.url" class="question-image" />
            </template>

            <!-- 选择题选项 -->
            <div v-if="hasOptions(questions[currentQuestionIndex])" class="question-options">
              <div 
                v-for="(opt, oi) in getOptions(questions[currentQuestionIndex])" 
                :key="oi" 
                class="option-item"
                :class="{ 
                  selected: isOptionSelected(currentQuestionIndex, String.fromCharCode(65 + oi)),
                  correct: showAnswer && isOptionCorrect(questions[currentQuestionIndex], String.fromCharCode(65 + oi), oi),
                  wrong: showAnswer && isOptionSelected(currentQuestionIndex, String.fromCharCode(65 + oi)) && !isOptionCorrect(questions[currentQuestionIndex], String.fromCharCode(65 + oi), oi)
                }"
                @click="selectAnswer(String.fromCharCode(65 + oi))"
              >
                <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
                <span class="option-text">{{ opt }}</span>
              </div>
            </div>

            <!-- 多选题确认按钮 -->
            <div v-if="!showAnswer && isMultipleChoice(questions[currentQuestionIndex]) && userAnswers[currentQuestionIndex]" class="submit-answer">
              <button class="btn btn-primary" @click="submitChoiceAnswer">确认答案</button>
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
              <template v-if="q.resources?.length">
                <img v-for="img in getQuestionImages(q.resources)" :key="img.url" :src="img.url" class="question-image" />
              </template>
              <div v-if="getOptions(q).length" class="question-options">
                <div v-for="(opt, oi) in getOptions(q)" :key="oi" class="option">
                  {{ String.fromCharCode(65 + oi) }}. {{ opt }}
                </div>
              </div>
              <div class="question-answer">答案: {{ formatAnswer(q) }}</div>
              <div v-if="q.explanation" class="question-explanation">解析: {{ q.explanation }}</div>
              <div v-if="q.calculationStepsJson" class="question-steps">
                <div>计算步骤:</div>
                <div v-for="(step, si) in getSteps(q)" :key="si">{{ si + 1 }}. {{ step }}</div>
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
              <template v-if="q.resources?.length">
                <img v-for="img in getQuestionImages(q.resources)" :key="img.url" :src="img.url" class="question-image" />
              </template>
              <div v-if="needsOptions(q.questionType)" class="edit-field">
                <label>选项：</label>
                <div v-for="(_, oi) in getOptionsForEdit(q)" :key="oi" class="option-edit-row">
                  <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
                  <input :value="getOptionsForEdit(q)[oi]" @input="updateOption(q, oi, ($event.target as HTMLInputElement).value)" class="edit-input" :disabled="q.questionType === 'true_false'" />
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
import { ref, onMounted } from 'vue'
import { apiGetClassPublicFiles, apiGetPublicRubrics, apiDeleteFile } from '@/api'
import { authHeaders } from '@/api/request'
import { useUserStore } from '@/stores/user'
import type { HtmlFileItem, RubricItem } from '@/types'
import FileCard from '@/components/FileCard.vue'
import { getQuestionImages } from '@/composables/useQuestionResources'
import { useToast } from '@/composables/useToast'
import { useExamPlayer } from '@/composables/useExamPlayer'

const userStore = useUserStore()
const toast = useToast()

// 标签页切换
const activeTab = ref<'files' | 'rubrics'>('files')

// 文件相关
const files = ref<HtmlFileItem[]>([])
const loading = ref(true)

// 试卷相关（试卷详情、题目与作答状态统一由 useExamPlayer 管理）
const rubrics = ref<RubricItem[]>([])
const rubricLoading = ref(true)

// 是否为创建者
const isCreator = ref(false)

// 提示弹窗
const showToast = ref(false)
const toastTitle = ref('')
const toastMessage = ref('')

function showToastMsg(title: string, message: string) {
  toastTitle.value = title
  toastMessage.value = message
  showToast.value = true
}

// 试卷作答/背题/改题的公共逻辑（与 MyFiles.vue 共用）
const {
  showRubricDetail,
  currentRubric,
  questions,
  questionsLoading,
  examMode,
  currentQuestionIndex,
  userAnswers,
  userTextAnswers,
  showAnswer,
  aiLoading,
  aiResults,
  saveAllLoading,
  openRubric,
  resetProgress,
  hasOptions,
  needsOptions,
  getOptions,
  getOptionsForEdit,
  updateOption,
  getSteps,
  addOption,
  removeOption,
  formatAnswer,
  getCorrectAnswer,
  isTextQuestion,
  isMultipleChoice,
  isOptionSelected,
  isOptionCorrect,
  selectAnswer,
  submitChoiceAnswer,
  submitAnswer,
  isCurrentAnswerCorrect,
  prevQuestion,
  nextQuestion,
  skipQuestion,
  getUserAnswer,
  getTypeLabel,
  generateAiAll,
  useAiAnswer,
  useAiExplanation,
  useAiSteps,
  clearAiAnswer,
  clearAiExplanation,
  clearAiSteps,
  addQuestionAfter,
  deleteQuestion,
  saveAllQuestions
} = useExamPlayer({ notify: showToastMsg })

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
    toast.error('删除文件失败')
  }
}

function handleFileUpdated(updatedFile: HtmlFileItem) {
  const idx = files.value.findIndex(f => f.id === updatedFile.id)
  if (idx !== -1) {
    files.value[idx] = updatedFile
  }
}

function handleOpenRubric(rubric: RubricItem) {
  isCreator.value = rubric.createId === userStore.userId
  // 与MyFiles保持一致：切换试卷时重置题号与作答，避免上一份试卷的答案残留到新试卷
  resetProgress()
  openRubric(rubric)
}

// 预览文件 - 在新窗口打开
async function handlePreviewFile(file: HtmlFileItem) {
  try {
    const response = await fetch(`/api/files/download/${file.id}`, {
      headers: authHeaders()
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
      toast.warning('无法打开新窗口，请允许弹窗')
    }
    // 延迟释放 URL，确保新窗口能够加载
    setTimeout(() => {
      URL.revokeObjectURL(blobUrl)
    }, 10000)
  } catch (e) {
    console.error('预览文件失败', e)
    toast.error('预览文件失败')
  }
}

// 下载文件 - 直接触发下载
async function handleDownloadFile(file: HtmlFileItem) {
  try {
    const response = await fetch(`/api/files/download/${file.id}`, {
      headers: authHeaders()
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
    toast.error('下载文件失败')
  }
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  return dateStr.substring(0, 10)
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.page-hint { font-size: 14px; color: var(--text-muted); }
.loading-center { display: flex; justify-content: center; padding: 64px 0; }

/* 标签切换 */
.tab-switch { display: flex; gap: 8px; margin-bottom: 24px; }
.tab-btn { padding: 10px 24px; border: 2px solid var(--ink); border-radius: 6px; background: var(--bg-secondary); color: var(--text-secondary); cursor: pointer; transition: background 0.12s ease,color 0.12s ease; font-size: 14px; font-weight: 700; }
.tab-btn:hover { background: var(--bg-tertiary); color: var(--text-primary); }
.tab-btn.active { background: var(--accent); color: #141414; border-color: var(--ink); box-shadow: 3px 3px 0 var(--shadow-ink); }

/* 试卷列表 */
.rubric-list { display: flex; flex-direction: column; gap: 16px; }
.rubric-card { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border: 2px solid var(--ink); border-radius: var(--card-radius); cursor: pointer; transition: transform 0.15s ease,box-shadow 0.15s ease; background: var(--card-bg); position: relative; box-shadow: 4px 4px 0 var(--shadow-ink); }
.rubric-card:hover { transform: translate(-2px,-2px); box-shadow: 6px 6px 0 var(--shadow-ink); }
.rubric-title { font-size: 17px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; }
.rubric-meta { font-size: 13px; color: var(--text-muted); display: flex; gap: 20px; }
.rubric-arrow { font-size: 24px; color: var(--text-muted); transition: transform 0.12s ease; }
.rubric-card:hover .rubric-arrow { transform: translateX(4px); color: var(--text-primary); }

/* 全屏试卷详情 */
.exam-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--bg-primary); z-index: 1000; overflow-y: auto; }
.exam-container { max-width: 800px; margin: 0 auto; padding: 24px; }
.exam-header { display: flex; align-items: center; gap: 16px; margin-bottom: 28px; padding-bottom: 20px; border-bottom: 2px solid var(--ink); }
.back-btn { padding: 10px 20px; border: 2px solid var(--ink); border-radius: 6px; background: var(--bg-secondary); cursor: pointer; color: var(--text-secondary); transition: background 0.12s ease; font-size: 14px; font-weight: 700; }
.back-btn:hover { background: var(--accent); color: #141414; }
.exam-title { flex: 1; font-size: 20px; font-weight: 700; text-align: center; }
.mode-switch { display: flex; gap: 8px; }
.mode-btn { padding: 8px 16px; border: 2px solid var(--ink); border-radius: 6px; background: var(--bg-secondary); cursor: pointer; color: var(--text-secondary); transition: background 0.12s ease; font-weight: 700; }
.mode-btn:hover { color: var(--text-primary); background: var(--bg-tertiary); }
.mode-btn.active { background: var(--accent); color: #141414; box-shadow: 2px 2px 0 var(--shadow-ink); }

/* 做题模式 */
.practice-mode { display: flex; flex-direction: column; gap: 20px; }
.progress-bar { text-align: center; }
.progress-text { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; }
.progress-track { height: 10px; background: var(--bg-tertiary); border: 2px solid var(--ink); border-radius: 4px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--accent); transition: width 0.3s ease; }

.question-card { padding: 28px; border: 2px solid var(--ink); border-radius: var(--card-radius); background: var(--card-bg); position: relative; box-shadow: 4px 4px 0 var(--shadow-ink); }
.question-header { display: flex; justify-content: space-between; margin-bottom: 16px; }
.question-num { font-weight: 800; font-size: 16px; }
.question-type { font-size: 12px; padding: 4px 10px; background: var(--accent); color: #141414; border-radius: 4px; border: 2px solid var(--ink); font-weight: 700; }
.question-text { font-size: 18px; line-height: 1.8; margin-bottom: 20px; }
.question-image { max-width: 100%; max-height: 300px; border-radius: 6px; margin-top: 8px; border: 2px solid var(--ink); }

.question-options { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.option-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border: 2px solid var(--ink); border-radius: 6px; cursor: pointer; transition: background 0.12s ease; color: var(--text-primary); background: var(--bg-secondary); }
.option-item:hover { background: var(--bg-tertiary); }
.option-item.selected { background: var(--accent); color: #141414; box-shadow: 3px 3px 0 var(--shadow-ink); }
.option-item.correct { border-color: var(--success); background: var(--success-light); color: var(--text-primary); }
.option-item.wrong { border-color: var(--error); background: var(--error-light); color: var(--text-primary); }
.option-label { font-weight: 800; width: 24px; }

.true-false-options { display: flex; gap: 16px; margin-bottom: 20px; }
.tf-btn { flex: 1; padding: 16px; border: 2px solid var(--ink); border-radius: 6px; background: var(--bg-secondary); font-size: 16px; font-weight: 700; cursor: pointer; transition: background 0.12s ease; color: var(--text-primary); }
.tf-btn:hover { background: var(--bg-tertiary); }
.tf-btn.selected { background: var(--accent); color: #141414; box-shadow: 3px 3px 0 var(--shadow-ink); }
.tf-btn.correct { border-color: var(--success); background: var(--success-light); }

.text-answer-area { margin-bottom: 24px; }
.text-answer-input { width: 100%; min-height: 160px; padding: 16px; border: 2px solid var(--input-border); border-radius: 6px; background: var(--input-bg); color: var(--text-primary); font-size: 15px; font-family: inherit; resize: vertical; line-height: 1.7; }
.text-answer-input:focus { outline: none; border-color: var(--input-focus); box-shadow: 3px 3px 0 var(--accent); }
.text-answer-input:disabled { opacity: 0.7; cursor: not-allowed; }

.submit-answer { text-align: center; margin-bottom: 20px; }

.answer-section { padding: 20px; border-radius: var(--card-radius); margin-bottom: 24px; background: var(--bg-tertiary); border: 2px solid var(--ink); }
.your-answer { font-size: 16px; font-weight: 700; margin-bottom: 8px; }
.your-answer.correct { color: var(--success); }
.correct-answer { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }

.nav-buttons { display: flex; gap: 12px; justify-content: center; }

/* 背题模式 */
.review-mode .questions-list { display: flex; flex-direction: column; gap: 24px; }
.review-mode .question-item { padding: 24px; border: 2px solid var(--ink); border-radius: var(--card-radius); background: var(--card-bg); }
.review-mode .question-header { margin-bottom: 16px; }
.review-mode .question-text { margin-bottom: 20px; }
.review-mode .question-options { margin-bottom: 16px; }
.review-mode .option { padding: 8px 0; }
.review-mode .question-answer { padding: 12px 16px; background: var(--success-light); border-radius: 6px; margin-bottom: 10px; color: var(--text-primary); border: 2px solid var(--success); }
.review-mode .question-explanation { padding: 12px 16px; background: var(--tertiary-light); border-radius: 6px; color: var(--text-primary); margin-bottom: 10px; border: 2px solid var(--tertiary); }
.review-mode .question-steps { padding: 12px 16px; background: var(--secondary-light); border-radius: 6px; color: var(--text-primary); border: 2px solid var(--secondary); }

/* 修改题目模式 */
.edit-mode-btn { background: var(--warning) !important; color: #141414 !important; border-color: var(--ink) !important; }
.edit-mode-btn.active { box-shadow: 3px 3px 0 var(--shadow-ink) !important; }

.edit-mode .questions-list { display: flex; flex-direction: column; gap: 24px; }
.edit-mode .question-item.editable { padding: 24px; border: 2px solid var(--ink); border-radius: var(--card-radius); background: var(--card-bg); }
.edit-mode .question-header { margin-bottom: 16px; }
.edit-mode .question-text { margin-bottom: 20px; }
.edit-mode .question-options { margin-bottom: 16px; }
.edit-mode .option { padding: 8px 0; }
.edit-mode .question-answer { padding: 12px 16px; background: var(--success-light); border-radius: 6px; margin-bottom: 10px; color: var(--text-primary); border: 2px solid var(--success); }
.edit-mode .question-explanation { padding: 12px 16px; background: var(--tertiary-light); border-radius: 6px; color: var(--text-primary); margin-bottom: 10px; border: 2px solid var(--tertiary); }
.edit-mode .question-steps { padding: 12px 16px; background: var(--secondary-light); border-radius: 6px; color: var(--text-primary); border: 2px solid var(--secondary); margin-bottom: 16px; }

.ai-actions { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 16px; padding-top: 16px; border-top: 2px dashed var(--border-glass); }
.ai-btn { padding: 10px 20px; border: 2px solid var(--ink); border-radius: 6px; background: var(--accent-light); color: var(--text-primary); cursor: pointer; transition: background 0.12s ease,transform 0.12s ease,box-shadow 0.12s ease; font-size: 13px; font-weight: 700; box-shadow: 2px 2px 0 var(--shadow-ink); }
.ai-btn:hover:not(:disabled) { background: var(--accent); color: #141414; transform: translate(-1px,-1px); box-shadow: 3px 3px 0 var(--shadow-ink); }
.ai-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.ai-btn.primary { background: var(--accent); color: #141414; }
.ai-btn.success { background: var(--success); color: #fff; }
.ai-btn.danger { background: var(--error); color: #fff; }

.save-all-actions { display: flex; justify-content: center; padding: 24px 0; margin-top: 16px; border-top: 2px solid var(--ink); }
.btn-lg { padding: 14px 40px; font-size: 16px; font-weight: 700; }

.type-select { padding: 6px 12px; border: 2px solid var(--ink); border-radius: 6px; background: var(--card-bg); color: var(--text-primary); font-size: 12px; cursor: pointer; }
.type-select option { background: var(--card-bg); color: var(--text-primary); }
.edit-field { margin-bottom: 16px; }
.edit-field label { display: block; font-size: 13px; color: var(--text-muted); margin-bottom: 6px; font-weight: 700; }
.edit-textarea { width: 100%; padding: 12px; border: 2px solid var(--input-border); border-radius: 6px; background: var(--input-bg); color: var(--text-primary); font-size: 14px; font-family: inherit; resize: vertical; line-height: 1.6; }
.edit-textarea:focus { outline: none; border-color: var(--input-focus); box-shadow: 3px 3px 0 var(--accent); }
.edit-input { width: 100%; padding: 10px 12px; border: 2px solid var(--input-border); border-radius: 6px; background: var(--input-bg); color: var(--text-primary); font-size: 14px; }
.edit-input:focus { outline: none; border-color: var(--input-focus); box-shadow: 3px 3px 0 var(--accent); }
.option-edit-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.option-edit-row .option-label { font-weight: 700; color: var(--text-secondary); min-width: 24px; }
.option-edit-row input:disabled { background: var(--bg-tertiary); color: var(--text-muted); cursor: not-allowed; }
.option-action-btn { width: 28px; height: 28px; border: 2px solid var(--ink); border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 16px; transition: background 0.12s ease; }
.option-action-btn.delete { background: var(--error-light); color: var(--error); }
.option-action-btn.delete:hover { background: var(--error); color: #fff; }
.add-option-btn { margin-top: 8px; padding: 8px 16px; border: 2px dashed var(--ink); border-radius: 6px; background: transparent; color: var(--text-primary); font-size: 13px; font-weight: 700; cursor: pointer; transition: background 0.12s ease; }
.add-option-btn:hover { background: var(--accent-light); border-style: solid; }

.ai-result-box { margin-bottom: 16px; padding: 12px; border: 2px dashed var(--ink); border-radius: 6px; background: var(--accent-light); }
.ai-result-box label { display: block; font-size: 13px; color: var(--text-primary); margin-bottom: 6px; font-weight: 700; }
.ai-textarea { border-color: var(--ink) !important; background: var(--input-bg) !important; }
.ai-btn-row { display: flex; gap: 8px; margin-top: 8px; }
.btn-use { padding: 6px 16px; border: 2px solid var(--ink); border-radius: 6px; background: var(--accent); color: #141414; font-size: 12px; font-weight: 700; cursor: pointer; box-shadow: 2px 2px 0 var(--shadow-ink); transition: transform 0.12s ease,box-shadow 0.12s ease; }
.btn-use:hover { transform: translate(-1px,-1px); box-shadow: 3px 3px 0 var(--shadow-ink); }
.btn-delete { padding: 6px 16px; border: 2px solid var(--error); border-radius: 6px; background: transparent; color: var(--error); font-size: 12px; font-weight: 700; cursor: pointer; transition: background 0.12s ease; }
.btn-delete:hover { background: var(--error); color: #fff; }

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(20,20,20,0.55); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.confirm-modal { background: var(--card-bg); border: 2px solid var(--ink); border-radius: var(--card-radius); padding: 24px; min-width: 320px; text-align: center; box-shadow: 8px 8px 0 var(--shadow-ink); }
.confirm-modal h3 { margin: 0 0 12px; font-size: 18px; color: var(--text-primary); }
.confirm-modal p { margin: 0 0 20px; color: var(--text-secondary); font-size: 14px; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }
</style>
