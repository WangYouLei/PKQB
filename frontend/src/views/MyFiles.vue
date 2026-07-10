<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">我的文件</h2>
    </div>
    
    <!-- 切换按钮 -->
    <div class="tab-switch">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'files' }"
        @click="activeTab = 'files'"
      >
        MinIO 文件
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'rubrics' }"
        @click="activeTab = 'rubrics'"
      >
        我的试卷
      </button>
      <!-- 批量管理切换按钮 -->
      <button
        class="tab-btn batch-toggle-btn"
        :class="{ active: batchMode }"
        @click="toggleBatchMode"
      >
        {{ batchMode ? '退出管理' : '批量管理' }}
      </button>
    </div>

    <!-- MinIO 文件列表 -->
    <div v-if="activeTab === 'files'">
      <div v-if="loading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="files.length === 0" class="empty-state">
        <div class="empty-state-icon">&#128194;</div>
        <div class="empty-state-text">暂无文件</div>
        <div class="empty-state-hint">点击上方按钮生成你的第一个文件</div>
      </div>
      <div v-else class="file-grid">
        <div v-for="file in files" :key="file.id" class="batch-card-wrapper" @click="batchMode && toggleFileSelection(file.id)">
          <!-- 批量选择复选框 -->
          <div v-if="batchMode" class="batch-checkbox" :class="{ checked: selectedFileIds.has(file.id) }" @click.stop="toggleFileSelection(file.id)">
            <svg v-if="selectedFileIds.has(file.id)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
          </div>
          <FileCard :file="file" :showEdit="!batchMode" :showDelete="!batchMode" @preview="handlePreviewFile" @download="handleDownloadFile" @remove="handleDeleteFile" @updated="handleFileUpdated" />
        </div>
      </div>
    </div>

    <!-- 试卷列表 -->
    <div v-if="activeTab === 'rubrics'">
      <div v-if="rubricLoading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="rubrics.length === 0" class="empty-state">
        <div class="empty-state-icon">&#128220;</div>
        <div class="empty-state-text">暂无试卷</div>
        <div class="empty-state-hint">在上传题目页面生成你的第一个试卷</div>
      </div>
      <div v-else class="rubric-list">
        <div
          v-for="rubric in rubrics"
          :key="rubric.id"
          class="rubric-card"
          :class="{ 'batch-selected': batchMode && selectedRubricIds.has(rubric.id) }"
          @click="batchMode ? toggleRubricSelection(rubric.id) : handleOpenRubric(rubric)"
        >
          <!-- 批量选择复选框 -->
          <div v-if="batchMode" class="batch-checkbox rubric-checkbox" :class="{ checked: selectedRubricIds.has(rubric.id) }" @click.stop="toggleRubricSelection(rubric.id)">
            <svg v-if="selectedRubricIds.has(rubric.id)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
          </div>
          <div class="rubric-info">
            <div class="rubric-title">{{ rubric.title }}</div>
            <div class="rubric-meta">
              <span>创建者: {{ rubric.creatorName || '我' }}</span>
              <span>班级: {{ rubric.className }}</span>
              <span>创建时间: {{ formatDate(rubric.createTime) }}</span>
            </div>
          </div>
          <div class="rubric-actions" v-if="!batchMode">
            <button class="btn btn-sm btn-secondary" @click.stop="showEditRubric(rubric)">修改</button>
            <button class="btn btn-sm btn-primary" @click.stop="handleGenerateHtml(rubric)">生成HTML</button>
            <span class="rubric-arrow">›</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="modal-overlay confirm-overlay" @click.self="showDeleteConfirm = false">
      <div class="confirm-modal">
        <h3>确认删除</h3>
        <p>确定删除该试卷吗？删除后无法恢复。</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmDeleteRubric" :disabled="deleteLoading">
            <span v-if="deleteLoading" class="spinner"></span><span v-else>确定删除</span>
          </button>
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

    <!-- 删除文件确认弹窗 -->
    <div v-if="showFileDeleteConfirm" class="modal-overlay" @click.self="showFileDeleteConfirm = false">
      <div class="confirm-modal">
        <h3>确认删除</h3>
        <p>确定要删除文件 "{{ pendingDeleteFile?.fileName }}" 吗？</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showFileDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmDeleteFile">确定删除</button>
        </div>
      </div>
    </div>

    <!-- 批量删除确认弹窗 -->
    <div v-if="showBatchDeleteConfirm" class="modal-overlay confirm-overlay" @click.self="showBatchDeleteConfirm = false">
      <div class="confirm-modal">
        <h3>确认批量删除</h3>
        <p>确定删除选中的 {{ batchSelectedCount }} 项吗？删除后无法恢复。</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showBatchDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmBatchDelete" :disabled="batchDeleteLoading">
            <span v-if="batchDeleteLoading" class="spinner"></span><span v-else>确定删除</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 批量操作底栏 -->
    <div v-if="batchMode" class="batch-action-bar">
      <div class="batch-action-left">
        <span class="batch-selected-count">已选择 {{ batchSelectedCount }} 项</span>
        <button class="btn btn-ghost btn-sm" @click="clearSelection" :disabled="batchSelectedCount === 0">取消选择</button>
      </div>
      <button class="btn btn-danger" @click="handleBatchDelete" :disabled="batchSelectedCount === 0">
        批量删除
      </button>
    </div>

    <!-- 修改试卷弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-content">
        <h3 style="font-size:18px;margin-bottom:20px">修改试卷</h3>
        <div class="form-group">
          <label class="form-label">试卷名称</label>
          <input v-model="editRubricForm.title" class="form-input" placeholder="请输入试卷名称" />
        </div>
        <div class="form-group" style="display:flex;align-items:center;justify-content:space-between">
          <label class="form-label" style="margin-bottom:0">是否公开（班级可见）</label>
          <label class="toggle">
            <input :checked="!editRubricForm.isPrivate" @change="editRubricForm.isPrivate = !($event?.target as HTMLInputElement)?.checked" type="checkbox" />
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div style="display:flex;gap:12px;margin-top:24px">
          <button class="btn btn-secondary" @click="showEditModal = false">取消</button>
          <button class="btn btn-danger" @click="deleteRubric" :disabled="deleteLoading">
            <span v-if="deleteLoading" class="spinner"></span><span v-else>删除</span>
          </button>
          <button class="btn btn-primary" @click="submitEditRubric" :disabled="editLoading">
            <span v-if="editLoading" class="spinner"></span><span v-else>保存</span>
          </button>
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
                <img v-if="getOptionImage(questions[currentQuestionIndex]?.resources, String.fromCharCode(65 + oi))" :src="getOptionImage(questions[currentQuestionIndex]?.resources, String.fromCharCode(65 + oi))!" class="option-image" />
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
              <button 
                v-if="!showAnswer && !isTextQuestion(questions[currentQuestionIndex])" 
                class="btn btn-ghost" 
                @click="skipQuestion"
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
                  <img v-if="getOptionImage(q.resources, String.fromCharCode(65 + oi))" :src="getOptionImage(q.resources, String.fromCharCode(65 + oi))!" class="option-image" />
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
              <template v-if="q.resources?.length">
                <img v-for="img in getQuestionImages(q.resources)" :key="img.url" :src="img.url" class="question-image" />
              </template>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, watch, computed } from 'vue'
import { apiGetMyFiles, apiGetMyRubrics, apiGetQuestionsByRubricId, apiUpdateRubric, apiDeleteRubric, apiGenerateRubricHtml, apiDeleteFile, apiAiSolveQuestion, apiBatchSaveQuestions, apiAddWrongQuestion, apiBatchDeleteRubrics, apiBatchDeleteFiles } from '@/api'
import type { HtmlFileItem, RubricItem, RubricQuestion } from '@/types'
import FileCard from '@/components/FileCard.vue'
import { getQuestionImages, getOptionImage } from '@/composables/useQuestionResources'

// 标签页切换
const activeTab = ref<'files' | 'rubrics'>('files')

// 批量管理模式
const batchMode = ref(false)
const selectedRubricIds = ref<Set<number>>(new Set())
const selectedFileIds = ref<Set<number>>(new Set())
const showBatchDeleteConfirm = ref(false)
const batchDeleteLoading = ref(false)

// 当前批量选中的数量
const batchSelectedCount = computed(() => {
  return activeTab.value === 'files' ? selectedFileIds.value.size : selectedRubricIds.value.size
})

// 切换标签页时清空选择
watch(activeTab, () => {
  clearSelection()
})

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

// 修改试卷弹窗
const showEditModal = ref(false)
const editRubricForm = ref({
  id: 0,
  title: '',
  isPrivate: false
})
const editLoading = ref(false)
const deleteLoading = ref(false)
const showDeleteConfirm = ref(false)

// 提示弹窗
const showToast = ref(false)
const toastTitle = ref('')
const toastMessage = ref('')

function showToastMsg(title: string, message: string) {
  toastTitle.value = title
  toastMessage.value = message
  showToast.value = true
}

// ========== 批量管理相关 ==========

/** 切换批量管理模式 */
function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    // 退出批量模式时清空选择
    clearSelection()
  }
}

/** 切换试卷选中状态 */
function toggleRubricSelection(id: number) {
  const newSet = new Set(selectedRubricIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedRubricIds.value = newSet
}

/** 切换文件选中状态 */
function toggleFileSelection(id: number) {
  const newSet = new Set(selectedFileIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedFileIds.value = newSet
}

/** 清空选择 */
function clearSelection() {
  selectedRubricIds.value = new Set()
  selectedFileIds.value = new Set()
}

/** 点击批量删除按钮 */
function handleBatchDelete() {
  showBatchDeleteConfirm.value = true
}

/** 确认批量删除 */
async function confirmBatchDelete() {
  batchDeleteLoading.value = true
  try {
    if (activeTab.value === 'files') {
      // 批量删除文件
      const ids = Array.from(selectedFileIds.value)
      const res = await apiBatchDeleteFiles(ids)
      if (res.code === 200) {
        showToastMsg('成功', `成功删除 ${ids.length} 个文件`)
        await loadFiles()
      } else {
        showToastMsg('错误', res.message || '批量删除失败')
      }
    } else {
      // 批量删除试卷
      const ids = Array.from(selectedRubricIds.value)
      const res = await apiBatchDeleteRubrics(ids)
      if (res.code === 200) {
        showToastMsg('成功', `成功删除 ${ids.length} 个试卷`)
        await loadRubrics()
      } else {
        showToastMsg('错误', res.message || '批量删除失败')
      }
    }
    // 删除成功后退出批量模式
    showBatchDeleteConfirm.value = false
    batchMode.value = false
    clearSelection()
  } catch (e) {
    console.error('批量删除失败', e)
    showToastMsg('错误', '批量删除失败')
  } finally {
    batchDeleteLoading.value = false
  }
}

// 考试模式
const examMode = ref<'practice' | 'review' | 'edit'>('practice')
const currentQuestionIndex = ref(0)
const userAnswers = ref<Record<number, string>>({})  // 选择题/判断题答案
const userTextAnswers = ref<Record<number, string>>({})  // 简答题/计算题答案
const showAnswer = ref(false)

// AI解答加载状态
const aiLoading = reactive<Record<number, boolean>>({})
const aiResults = reactive<Record<number, { answer?: string; explanation?: string; steps?: string }>>({})
const saveAllLoading = ref(false)

onMounted(async () => {
  await Promise.all([loadFiles(), loadRubrics()])
})

async function loadFiles() {
  try {
    const res = await apiGetMyFiles()
    if (res.code === 200) {
      // 直接使用 isPrivate 字段
      files.value = res.data || []
    }
  } catch (e) { console.error('获取文件列表失败', e) }
  finally { loading.value = false }
}

async function loadRubrics() {
  try {
    const res = await apiGetMyRubrics()
    if (res.code === 200) {
      rubrics.value = res.data || []
    }
  } catch (e) { console.error('获取试卷列表失败', e) }
  finally { rubricLoading.value = false }
}

// 显示修改试卷弹窗
function showEditRubric(rubric: RubricItem) {
  editRubricForm.value = {
    id: rubric.id,
    title: rubric.title,
    isPrivate: rubric.isPrivate
  }
  showEditModal.value = true
}

// 提交修改
async function submitEditRubric() {
  if (!editRubricForm.value.title.trim()) {
    showToastMsg('提示', '请输入试卷名称')
    return
  }
  editLoading.value = true
  try {
    const res = await apiUpdateRubric({
      id: editRubricForm.value.id,
      title: editRubricForm.value.title,
      isPrivate: editRubricForm.value.isPrivate
    })
    if (res.code === 200) {
      const idx = rubrics.value.findIndex(r => r.id === editRubricForm.value.id)
      if (idx !== -1) {
        rubrics.value[idx].title = editRubricForm.value.title
        rubrics.value[idx].isPrivate = editRubricForm.value.isPrivate
      }
      showEditModal.value = false
      showToastMsg('成功', '修改成功')
    } else {
      showToastMsg('错误', res.message || '修改失败')
    }
  } catch (e) { console.error('修改试卷失败', e) }
  finally { editLoading.value = false }
}

// 删除试卷
function deleteRubric() {
  showDeleteConfirm.value = true
}

async function confirmDeleteRubric() {
  deleteLoading.value = true
  try {
    const res = await apiDeleteRubric(editRubricForm.value.id)
    if (res.code === 200) {
      rubrics.value = rubrics.value.filter(r => r.id !== editRubricForm.value.id)
      showEditModal.value = false
      showDeleteConfirm.value = false
      showToastMsg('成功', '删除成功')
    } else {
      showToastMsg('错误', res.message || '删除失败')
    }
  } catch (e) { 
    console.error('删除试卷失败', e)
    showToastMsg('错误', '删除失败')
  }
  finally { deleteLoading.value = false }
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
    const htmlBlob = new Blob([blob], { type: 'text/html;charset=utf-8' })
    const blobUrl = URL.createObjectURL(htmlBlob)
    const newWindow = window.open(blobUrl, '_blank')
    if (!newWindow) {
      showToastMsg('提示', '无法打开新窗口，请允许弹窗')
    }
    setTimeout(() => {
      URL.revokeObjectURL(blobUrl)
    }, 10000)
  } catch (e) {
    console.error('预览文件失败', e)
    showToastMsg('错误', '预览文件失败')
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
    showToastMsg('错误', '下载文件失败')
  }
}

// 删除文件
const showFileDeleteConfirm = ref(false)
const pendingDeleteFile = ref<HtmlFileItem | null>(null)

function handleDeleteFile(file: HtmlFileItem) {
  pendingDeleteFile.value = file
  showFileDeleteConfirm.value = true
}

function handleFileUpdated(updatedFile: HtmlFileItem) {
  const idx = files.value.findIndex(f => f.id === updatedFile.id)
  if (idx !== -1) {
    files.value[idx] = updatedFile
  }
  showToastMsg('成功', '修改成功')
}

async function confirmDeleteFile() {
  if (!pendingDeleteFile.value) return
  const file = pendingDeleteFile.value
  showFileDeleteConfirm.value = false
  
  try {
    const res = await apiDeleteFile(file.id)
    if (res.code === 200) {
      showToastMsg('成功', '删除成功')
      await loadFiles()
    } else {
      showToastMsg('错误', res.message || '删除失败')
    }
  } catch (e) {
    console.error('删除文件失败', e)
    showToastMsg('错误', '删除失败')
  } finally {
    pendingDeleteFile.value = null
  }
}

async function handleOpenRubric(rubric: RubricItem) {
  currentRubric.value = rubric
  showRubricDetail.value = true
  examMode.value = 'practice'
  currentQuestionIndex.value = 0
  userAnswers.value = {}
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

async function generateAiAll(idx: number) {
  const q = questions.value[idx]
  if (!q) return
  
  aiLoading[idx] = true
  try {
    const res = await apiAiSolveQuestion({
      questionText: q.questionText,
      questionType: q.questionType,
      optionsJson: q.optionsJson,
      generateType: 'all'
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
      resources: q.resources || [],
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

// 生成HTML文件
async function handleGenerateHtml(rubric: RubricItem) {
  try {
    const res = await apiGenerateRubricHtml(rubric.id, rubric.title + '.html', false)
    if (res.code === 200) {
      showToastMsg('成功', 'HTML生成成功！文件已保存到您的文件列表中。')
      await loadFiles()
    } else {
      showToastMsg('错误', res.message || '生成失败')
    }
  } catch (e) {
    console.error('生成HTML失败', e)
    showToastMsg('错误', '生成失败')
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

// 判断是否为多选题
function isMultipleChoice(q: RubricQuestion): boolean {
  return q?.questionType === 'multiple_choice'
}

// 判断选项是否被用户选中（支持多选答案如 "A,C"）
function isOptionSelected(index: number, optionLetter: string): boolean {
  const answer = userAnswers.value[index]
  if (!answer) return false
  // 多选题答案格式如 "A,C"，单选/判断题格式如 "A"
  return answer.split(',').map(s => s.trim()).includes(optionLetter)
}

// 判断选项是否为正确答案
function isOptionCorrect(q: RubricQuestion, optionLetter: string, optionIndex: number): boolean {
  const options = getOptions(q)
  const correctAnswer = getCorrectAnswer(q)
  // 匹配字母或匹配选项内容
  return optionLetter === correctAnswer || options[optionIndex] === correctAnswer ||
    correctAnswer.split(',').map(s => s.trim()).includes(optionLetter)
}

// 选择答案（单选直接判断，多选 toggle）
function selectAnswer(answer: string) {
  if (showAnswer.value) return
  const q = questions.value[currentQuestionIndex.value]

  if (isMultipleChoice(q)) {
    // 多选题：toggle 选项
    const current = userAnswers.value[currentQuestionIndex.value] || ''
    const selected = current ? current.split(',').map(s => s.trim()) : []
    const idx = selected.indexOf(answer)
    if (idx >= 0) {
      selected.splice(idx, 1)
    } else {
      selected.push(answer)
    }
    // 按字母顺序排序
    selected.sort()
    userAnswers.value[currentQuestionIndex.value] = selected.join(',')
    // 多选题不自动判断，需要点击确认按钮
  } else {
    // 单选题/判断题：直接判断
    userAnswers.value[currentQuestionIndex.value] = answer
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
        // 答错时自动添加到错题本
        apiAddWrongQuestion({
          questionId: q.id!,
          rubricId: currentRubric.value?.id || 0,
          userAnswer: answer
        }).catch(e => console.error('添加错题失败', e))
      }
    }
  }
}

// 多选题确认答案
function submitChoiceAnswer() {
  const q = questions.value[currentQuestionIndex.value]
  if (!q || !userAnswers.value[currentQuestionIndex.value]) return
  showAnswer.value = true
  // 答错时自动添加到错题本
  if (!isCurrentAnswerCorrect()) {
    apiAddWrongQuestion({
      questionId: q.id!,
      rubricId: currentRubric.value?.id || 0,
      userAnswer: userAnswers.value[currentQuestionIndex.value]
    }).catch(e => console.error('添加错题失败', e))
  }
}

// 提交答案
function submitAnswer() {
  // 如果是简答题/计算题，将文本答案保存到userAnswers
  const q = questions.value[currentQuestionIndex.value]
  if (isTextQuestion(q) && userTextAnswers.value[currentQuestionIndex.value]) {
    userAnswers.value[currentQuestionIndex.value] = userTextAnswers.value[currentQuestionIndex.value]
  }
  showAnswer.value = true
  // 简答题/计算题答错时自动添加到错题本
  if (q && !isCurrentAnswerCorrect()) {
    apiAddWrongQuestion({
      questionId: q.id!,
      rubricId: currentRubric.value?.id || 0,
      userAnswer: userTextAnswers.value[currentQuestionIndex.value] || ''
    }).catch(e => console.error('添加错题失败', e))
  }
}

// 判断当前答案是否正确
function isCurrentAnswerCorrect(): boolean {
  const q = questions.value[currentQuestionIndex.value]
  if (!q) return false
  const userAnswer = userAnswers.value[currentQuestionIndex.value]
  if (!userAnswer) return false
  
  if (hasOptions(q)) {
    if (isMultipleChoice(q)) {
      // 多选题：将用户答案和正确答案都拆分为集合比较
      const userSet = new Set(userAnswer.split(',').map(s => s.trim().toUpperCase()))
      const correctAnswer = q.answer || ''
      const correctSet = new Set(correctAnswer.split(',').map(s => s.trim().toUpperCase()))
      if (userSet.size !== correctSet.size) return false
      for (const item of userSet) {
        if (!correctSet.has(item)) return false
      }
      return true
    }
    // 单选题/判断题
    const options = getOptions(q)
    const selectedIndex = userAnswer.charCodeAt(0) - 65
    const selectedContent = options[selectedIndex]
    return userAnswer === q.answer || selectedContent === q.answer
  }
  // 简答题/计算题：忽略首尾空格差异
  return userAnswer.trim() === q.answer?.trim()
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
    // 最后一题跳过，关闭弹窗
    showRubricDetail.value = false
  }
}

// 获取当前题目用户填写的答案（用于显示）
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
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28px; }
.page-title { font-size: 28px; font-weight: 700; color: var(--text-primary); letter-spacing: -0.02em; }
.loading-center { display: flex; justify-content: center; padding: 80px 0; }

.tab-switch { display: flex; gap: 8px; margin-bottom: 28px; }
.tab-btn { padding: 10px 24px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); color: var(--text-muted); cursor: pointer; transition: all 0.3s ease; font-size: 14px; font-weight: 500; }
.tab-btn:hover { border-color: var(--accent); color: var(--text-secondary); }
.tab-btn.active { background: var(--accent-gradient); color: #fff; border-color: transparent; box-shadow: 0 4px 16px rgba(99,102,241,0.3); }

.rubric-list { display: flex; flex-direction: column; gap: 16px; }
.rubric-card { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border: 1px solid var(--border-glass); border-radius: 16px; cursor: pointer; transition: all 0.3s ease; background: var(--card-bg); position: relative; overflow: hidden; }
.rubric-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: var(--accent-gradient); opacity: 0; transition: opacity 0.3s ease; }
.rubric-card:hover { border-color: var(--accent-border); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.2); }
.rubric-card:hover::before { opacity: 1; }
.rubric-title { font-size: 17px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.rubric-meta { font-size: 13px; color: var(--text-muted); display: flex; gap: 20px; }
.rubric-arrow { font-size: 24px; color: var(--text-muted); transition: transform 0.3s ease; }
.rubric-card:hover .rubric-arrow { transform: translateX(4px); color: var(--accent); }
.rubric-actions { display: flex; align-items: center; gap: 10px; }

.exam-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--bg-primary); z-index: 1000; overflow-y: auto; }
.exam-container { max-width: 800px; margin: 0 auto; padding: 32px; }
.exam-header { display: flex; align-items: center; gap: 16px; margin-bottom: 28px; padding-bottom: 20px; border-bottom: 1px solid var(--border-glass); }
.back-btn { padding: 10px 20px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); cursor: pointer; color: var(--text-secondary); transition: all 0.3s ease; font-size: 14px; }
.back-btn:hover { background: var(--accent-light); border-color: var(--accent-border); color: var(--accent); }
.exam-title { flex: 1; font-size: 22px; font-weight: 600; text-align: center; }
.mode-switch { display: flex; gap: 8px; }
.mode-btn { padding: 10px 20px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); cursor: pointer; font-size: 14px; transition: all 0.3s ease; color: var(--text-secondary); }
.mode-btn:hover { color: var(--text-primary); border-color: var(--accent-border); }
.mode-btn.active { background: var(--accent-gradient); color: #fff; border-color: transparent; box-shadow: 0 4px 16px rgba(99,102,241,0.3); }

.practice-mode { display: flex; flex-direction: column; gap: 24px; }
.progress-bar { text-align: center; }
.progress-text { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }
.progress-track { height: 8px; background: var(--bg-glass); border-radius: 4px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--accent-gradient); transition: width 0.3s ease; border-radius: 4px; }

.question-card { padding: 28px; border: 1px solid var(--border-glass); border-radius: 20px; background: var(--card-bg); position: relative; }
.question-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: var(--accent-gradient); border-radius: 20px 20px 0 0; }
.question-header { display: flex; justify-content: space-between; margin-bottom: 20px; }
.question-num { font-weight: 600; font-size: 17px; }
.question-type { font-size: 12px; padding: 6px 14px; background: var(--accent-light); color: var(--accent); border-radius: 20px; border: 1px solid var(--accent-border); }
.question-text { font-size: 18px; line-height: 1.8; margin-bottom: 24px; }
.question-image { max-width: 100%; max-height: 300px; border-radius: 8px; margin-top: 8px; }

.question-options { display: flex; flex-direction: column; gap: 12px; margin-bottom: 24px; }
.option-item { display: flex; align-items: center; gap: 14px; padding: 14px 18px; border: 1px solid var(--border-glass); border-radius: 14px; cursor: pointer; transition: all 0.3s ease; color: var(--text-primary); }
.option-item:hover { border-color: var(--accent); background: rgba(99,102,241,0.05); }
.option-item.selected { border-color: var(--accent); background: var(--accent-light); color: var(--accent); }
.option-item.correct { border-color: var(--success); background: var(--success-light); color: var(--success); }
.option-item.wrong { border-color: #ef4444; background: rgba(239,68,68,0.1); color: #ef4444; }
.option-label { font-weight: 600; width: 28px; height: 28px; border-radius: 8px; background: var(--bg-glass); display: flex; align-items: center; justify-content: center; font-size: 14px; color: var(--text-primary); }
.option-image { max-width: 200px; max-height: 150px; border-radius: 6px; margin-left: 4px; }
.option-item.selected .option-label { background: var(--accent); color: #fff; }
.option-item.correct .option-label { background: var(--success); color: #fff; }
.option-item.wrong .option-label { background: #ef4444; color: #fff; }

.true-false-options { display: flex; gap: 16px; margin-bottom: 24px; }
.tf-btn { flex: 1; padding: 18px; border: 1px solid var(--border-glass); border-radius: 14px; background: var(--card-bg); font-size: 16px; font-weight: 500; cursor: pointer; transition: all 0.3s ease; color: var(--text-primary); }
.tf-btn:hover { border-color: var(--accent); background: rgba(99,102,241,0.05); }
.tf-btn.selected { border-color: var(--accent); background: var(--accent-light); color: var(--accent); }
.tf-btn.correct { border-color: var(--success); background: var(--success-light); color: var(--success); }

.text-answer-area { margin-bottom: 24px; }
.text-answer-input { width: 100%; min-height: 160px; padding: 16px; border: 1px solid var(--border-glass); border-radius: 14px; background: var(--bg-glass); color: var(--text-primary); font-size: 15px; font-family: inherit; resize: vertical; line-height: 1.7; }
.text-answer-input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.text-answer-input:disabled { opacity: 0.7; cursor: not-allowed; }

.submit-answer { text-align: center; margin-bottom: 24px; }

.answer-section { padding: 20px; border-radius: 14px; margin-bottom: 24px; background: var(--bg-glass); border: 1px solid var(--border-glass); }
.your-answer { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.your-answer.correct { color: var(--success); }
.correct-answer { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }

.nav-buttons { display: flex; gap: 12px; justify-content: center; }

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
.confirm-overlay { z-index: 1100; }
.confirm-modal { background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 16px; padding: 24px; min-width: 320px; text-align: center; }
.confirm-modal h3 { margin: 0 0 12px; font-size: 18px; color: var(--text-primary); }
.confirm-modal p { margin: 0 0 20px; color: var(--text-secondary); font-size: 14px; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }

/* 批量管理按钮 */
.batch-toggle-btn { margin-left: auto; }
.batch-toggle-btn.active { background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%); border-color: transparent; box-shadow: 0 4px 16px rgba(245,158,11,0.3); }

/* 批量选择复选框 */
.batch-checkbox { width: 22px; height: 22px; border: 2px solid var(--border-glass); border-radius: 6px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s ease; flex-shrink: 0; background: var(--bg-glass); }
.batch-checkbox:hover { border-color: var(--accent); }
.batch-checkbox.checked { background: var(--accent-gradient); border-color: transparent; }
.batch-checkbox svg { width: 14px; height: 14px; color: #fff; }

/* 文件卡片批量选择包装器 */
.batch-card-wrapper { position: relative; display: flex; align-items: flex-start; gap: 10px; }
.batch-card-wrapper .batch-checkbox { margin-top: 16px; z-index: 1; }
.batch-card-wrapper .file-card { flex: 1; }

/* 试卷卡片批量选中样式 */
.rubric-card.batch-selected { border-color: var(--accent); background: var(--accent-light); }
.rubric-checkbox { margin-right: 12px; }

/* 批量操作底栏 */
.batch-action-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; align-items: center; justify-content: space-between; padding: 16px 32px; background: var(--card-bg); border-top: 1px solid var(--border-glass); box-shadow: 0 -4px 24px rgba(0,0,0,0.2); z-index: 100; backdrop-filter: blur(12px); }
.batch-action-left { display: flex; align-items: center; gap: 16px; }
.batch-selected-count { font-size: 14px; font-weight: 500; color: var(--text-secondary); }
.btn-ghost { background: transparent; border: 1px solid var(--border-glass); color: var(--text-secondary); }
.btn-ghost:hover { border-color: var(--accent-border); color: var(--accent); }
.btn-ghost:disabled { opacity: 0.4; cursor: not-allowed; }
</style>