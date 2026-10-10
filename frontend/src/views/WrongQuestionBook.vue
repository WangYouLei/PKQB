<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">错题本</h2>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalCount }}</div>
        <div class="stat-label">总错题数</div>
      </div>
      <div class="stat-card" :class="{ highlight: stats.todayReviewCount > 0 }">
        <div class="stat-value">{{ stats.todayReviewCount }}</div>
        <div class="stat-label">今日待复习</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.learningCount }}</div>
        <div class="stat-label">学习中</div>
      </div>
      <div class="stat-card mastered">
        <div class="stat-value">{{ stats.masteredCount }}</div>
        <div class="stat-label">已掌握</div>
      </div>
    </div>

    <!-- 标签切换 -->
    <div class="tab-switch">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'all' }"
        @click="activeTab = 'all'"
      >
        全部错题
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'review' }"
        @click="activeTab = 'review'"
      >
        今日复习
      </button>
    </div>

    <!-- 全部错题 -->
    <div v-if="activeTab === 'all'">
      <div v-if="loading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="wrongQuestions.length === 0" class="empty-state">
        <div class="empty-state-icon">&#128218;</div>
        <div class="empty-state-text">暂无错题</div>
        <div class="empty-state-hint">做题时答错的题目会自动添加到错题本</div>
      </div>
      <div v-else>
        <!-- 批量操作栏 -->
        <div class="batch-bar">
          <button class="btn btn-sm" :class="batchMode ? 'btn-primary' : 'btn-secondary'" @click="toggleBatchMode">
            {{ batchMode ? '取消选择' : '批量管理' }}
          </button>
          <template v-if="batchMode && selectedIds.size > 0">
            <span class="batch-count">已选 {{ selectedIds.size }} 题</span>
            <button class="btn btn-sm btn-danger" @click="showDeleteConfirm = true">删除选中</button>
          </template>
        </div>
        <div class="rubric-groups">
          <div v-for="group in groupedQuestions" :key="group.rubricId" class="rubric-group">
            <div class="group-header" @click="batchMode ? selectAllInGroup(group) : toggleGroup(group.rubricId)">
              <template v-if="batchMode">
                <input type="checkbox" class="batch-checkbox" :checked="group.questions.every(q => selectedIds.has(q.id))" @click.stop="selectAllInGroup(group)" />
              </template>
              <span class="group-title">{{ group.rubricTitle }}</span>
              <span class="group-count">{{ group.questions.length }} 题</span>
              <span v-if="!batchMode" class="group-arrow" :class="{ expanded: expandedGroups.has(group.rubricId) }">›</span>
            </div>
            <div v-if="batchMode || expandedGroups.has(group.rubricId)" class="group-questions">
              <div
                v-for="q in group.questions"
                :key="q.id"
                class="wrong-question-card"
                :class="{ selected: selectedIds.has(q.id) }"
                @click="batchMode ? toggleSelect(q.id) : undefined"
              >
                <div class="wq-header" @click.stop="!batchMode && toggleExpand(q.id)">
                  <div class="wq-header-left">
                    <template v-if="batchMode">
                      <input type="checkbox" class="batch-checkbox" :checked="selectedIds.has(q.id)" @click.stop="toggleSelect(q.id)" />
                    </template>
                    <span class="question-type-badge" :class="q.questionType">{{ getTypeLabel(q.questionType) }}</span>
                    <span class="mastery-badge" :style="{ background: getMasteryColor(q.masteryLevel), color: '#fff' }">
                      {{ getMasteryLabel(q.masteryLevel) }}
                    </span>
                  </div>
                  <div class="wq-header-right">
                    <span class="wq-time">{{ formatRelativeTime(q.updateTime) }}</span>
                    <span v-if="!batchMode" class="wq-expand-icon" :class="{ expanded: expandedQuestions.has(q.id) }">›</span>
                  </div>
                </div>
                <div class="wq-text">{{ q.questionText }}</div>
                <template v-if="q.resources?.length">
                  <img v-for="img in getQuestionImages(q.resources)" :key="img.url" :src="img.url" class="wq-image" />
                </template>
                <div class="wq-answers">
                  <div class="wq-user-answer">
                    <span class="answer-label">你的答案：</span>
                    <span class="answer-value wrong">{{ q.userAnswer || '未作答' }}</span>
                  </div>
                  <div class="wq-correct-answer">
                    <span class="answer-label">正确答案：</span>
                    <span class="answer-value correct">{{ q.answer }}</span>
                  </div>
                </div>
                <!-- 展开详情 -->
                <div v-if="!batchMode && expandedQuestions.has(q.id)" class="wq-detail">
                  <div v-if="q.explanation" class="wq-explanation">
                    <div class="detail-label">解析</div>
                    <div class="detail-content">{{ q.explanation }}</div>
                  </div>
                  <div v-if="q.calculationStepsJson" class="wq-steps">
                    <div class="detail-label">计算步骤</div>
                    <div v-for="(step, si) in parseJson(q.calculationStepsJson)" :key="si" class="step-item">
                      {{ si + 1 }}. {{ step }}
                    </div>
                  </div>
                  <div class="wq-meta">
                    <span>错 {{ q.wrongCount }} 次</span>
                    <span>对 {{ q.correctCount }} 次</span>
                    <span>间隔 {{ q.intervalDays }} 天</span>
                  </div>
                </div>
                <div v-if="!batchMode" class="wq-actions">
                  <button class="btn btn-sm btn-secondary" @click.stop="viewOriginalQuestion(q)">查看原题</button>
                  <button class="btn btn-sm btn-danger" @click.stop="handleDelete(q.id)">删除</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 今日复习 -->
    <div v-if="activeTab === 'review'">
      <div v-if="reviewLoading" class="loading-center"><div class="spinner"></div></div>
      <div v-else-if="reviewQuestions.length === 0" class="empty-state">
        <div class="empty-state-icon">&#127881;</div>
        <div class="empty-state-text">今日没有待复习的错题</div>
        <div class="empty-state-hint">继续保持，加油！</div>
      </div>
      <div v-else class="review-mode">
        <!-- 进度指示 -->
        <div class="progress-bar">
          <div class="progress-text">已完成 {{ reviewCompleted }} / {{ reviewQuestions.length }} 题</div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: (reviewCompleted / reviewQuestions.length * 100) + '%' }"></div>
          </div>
        </div>

        <!-- 当前复习题目 -->
        <div v-if="currentReviewIndex < reviewQuestions.length" class="review-card">
          <div class="review-question-header">
            <span class="question-num">第 {{ currentReviewIndex + 1 }} 题</span>
            <span class="question-type-badge" :class="currentReviewQuestion?.questionType">
              {{ getTypeLabel(currentReviewQuestion?.questionType || '') }}
            </span>
            <span class="mastery-badge" :style="{ background: getMasteryColor(currentReviewQuestion?.masteryLevel || 0), color: '#fff' }">
              {{ getMasteryLabel(currentReviewQuestion?.masteryLevel || 0) }}
            </span>
          </div>
          <div class="review-question-text">{{ currentReviewQuestion?.questionText }}</div>
          <template v-if="currentReviewQuestion?.resources?.length">
            <img v-for="img in getQuestionImages(currentReviewQuestion?.resources)" :key="img.url" :src="img.url" class="review-image" />
          </template>

          <!-- 选择题/判断题选项 -->
          <div v-if="hasOptions(currentReviewQuestion) && !reviewRevealed" class="review-options">
            <div
              v-for="(opt, oi) in getOptions(currentReviewQuestion)"
              :key="oi"
              class="option-item"
              :class="{ selected: isReviewOptionSelected(String.fromCharCode(65 + oi)) }"
              @click="toggleReviewOption(String.fromCharCode(65 + oi))"
            >
              <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
              <span class="option-text">{{ opt }}</span>
            </div>
          </div>

          <!-- 简答题/计算题输入 -->
          <div v-if="isTextQuestion(currentReviewQuestion) && !reviewRevealed" class="text-answer-area">
            <textarea
              v-model="reviewTextAnswer"
              class="text-answer-input"
              placeholder="请输入你的答案..."
            ></textarea>
          </div>

          <!-- 未揭晓时：确认答案按钮 -->
          <div v-if="!reviewRevealed" class="review-submit">
            <button
              class="btn btn-primary"
              :disabled="!reviewUserAnswer && !reviewTextAnswer"
              @click="revealAnswer"
            >
              确认答案
            </button>
          </div>

          <!-- 揭晓答案后 -->
          <div v-if="reviewRevealed" class="review-reveal">
            <div class="reveal-section">
              <div class="your-answer" :class="{ correct: isReviewCorrect() }">
                你的答案：{{ reviewUserAnswer || reviewTextAnswer || '未作答' }}
              </div>
              <div class="correct-answer">正确答案：{{ currentReviewQuestion?.answer }}</div>
              <div v-if="currentReviewQuestion?.explanation" class="question-explanation">
                解析：{{ currentReviewQuestion.explanation }}
              </div>
            </div>
            <div class="review-judge">
              <button class="btn btn-success" @click="submitReview(true)">答对了</button>
              <button class="btn btn-danger" @click="submitReview(false)">答错了</button>
            </div>
          </div>
        </div>

        <!-- 全部完成 -->
        <div v-else class="review-complete">
          <div class="complete-icon">&#127881;</div>
          <div class="complete-text">今日复习已完成！</div>
          <div class="complete-sub">共复习 {{ reviewQuestions.length }} 题</div>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
      <div class="confirm-modal">
        <h3>确认删除</h3>
        <p v-if="pendingDeleteId !== null">确定删除该错题吗？删除后无法恢复。</p>
        <p v-else>确定删除选中的 {{ selectedIds.size }} 条错题吗？删除后无法恢复。</p>
        <div class="confirm-btns">
          <button class="btn btn-secondary" @click="showDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="pendingDeleteId !== null ? confirmDelete() : confirmBatchDelete()" :disabled="deleteLoading">
            <span v-if="deleteLoading" class="spinner"></span><span v-else>确定删除</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 查看原题弹窗 -->
    <div v-if="showOriginalQuestion" class="modal-overlay" @click.self="showOriginalQuestion = false">
      <div class="original-question-modal">
        <div class="modal-header">
          <h3>原题详情</h3>
          <button class="close-btn" @click="showOriginalQuestion = false">×</button>
        </div>
        <div class="modal-content" v-if="currentOriginalQuestion">
          <div class="original-question-item">
            <div class="oq-header">
              <span class="question-type-badge" :class="currentOriginalQuestion.questionType">
                {{ getTypeLabel(currentOriginalQuestion.questionType) }}
              </span>
            </div>
            <div class="oq-text">{{ currentOriginalQuestion.questionText }}</div>
            <template v-if="currentOriginalQuestion.resources?.length">
              <img v-for="img in getQuestionImages(currentOriginalQuestion?.resources)" :key="img.url" :src="img.url" class="oq-image" />
            </template>
            
            <!-- 选项 -->
            <div v-if="hasOptions(currentOriginalQuestion)" class="oq-options">
              <div
                v-for="(opt, oi) in getOptions(currentOriginalQuestion)"
                :key="oi"
                class="oq-option"
              >
                <span class="oq-option-label">{{ String.fromCharCode(65 + oi) }}.</span>
                <span class="oq-option-text">{{ opt }}</span>
              </div>
            </div>
            
            <!-- 答案 -->
            <div class="oq-answer-section">
              <div class="oq-section-label">正确答案</div>
              <div class="oq-answer-value">{{ currentOriginalQuestion.answer }}</div>
            </div>
            
            <!-- 解析 -->
            <div v-if="currentOriginalQuestion.explanation" class="oq-explanation-section">
              <div class="oq-section-label">解析</div>
              <div class="oq-explanation-content">{{ currentOriginalQuestion.explanation }}</div>
            </div>
            
            <!-- 计算步骤 -->
            <div v-if="currentOriginalQuestion.calculationStepsJson" class="oq-steps-section">
              <div class="oq-section-label">计算步骤</div>
              <div v-for="(step, si) in parseJson(currentOriginalQuestion.calculationStepsJson)" :key="si" class="oq-step-item">
                {{ si + 1 }}. {{ step }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  apiGetWrongQuestions,
  apiGetTodayReviewQuestions,
  apiGetWrongQuestionStats,
  apiDeleteWrongQuestion,
  apiBatchDeleteWrongQuestions,
  apiSubmitReviewResult
} from '@/api'
import type { WrongQuestion, WrongQuestionStats } from '@/types'
import { getQuestionImages } from '@/composables/useQuestionResources'

// 统计数据
const stats = ref<WrongQuestionStats>({ totalCount: 0, todayReviewCount: 0, masteredCount: 0, learningCount: 0 })

// 标签切换
const activeTab = ref<'all' | 'review'>('all')

// 全部错题
const wrongQuestions = ref<WrongQuestion[]>([])
const loading = ref(true)
const expandedGroups = ref(new Set<number>())
const expandedQuestions = ref(new Set<number>())

// 今日复习
const reviewQuestions = ref<WrongQuestion[]>([])
const reviewLoading = ref(true)
const currentReviewIndex = ref(0)
const reviewUserAnswer = ref('')
const reviewTextAnswer = ref('')
const reviewRevealed = ref(false)
const reviewCompletedSet = ref(new Set<number>())

// 删除
const showDeleteConfirm = ref(false)
const pendingDeleteId = ref<number | null>(null)
const deleteLoading = ref(false)

// 批量选择
const batchMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())

// 查看原题
const showOriginalQuestion = ref(false)
const currentOriginalQuestion = ref<WrongQuestion | null>(null)

onMounted(async () => {
  await Promise.all([loadStats(), loadWrongQuestions(), loadReviewQuestions()])
})

async function loadStats() {
  try {
    const res = await apiGetWrongQuestionStats()
    if (res.code === 200) {
      stats.value = res.data || { totalCount: 0, todayReviewCount: 0, masteredCount: 0, learningCount: 0 }
    }
  } catch (e) { console.error('获取错题统计失败', e) }
}

async function loadWrongQuestions() {
  loading.value = true
  try {
    const res = await apiGetWrongQuestions()
    if (res.code === 200) {
      wrongQuestions.value = res.data || []
      // 默认展开第一个分组
      if (wrongQuestions.value.length > 0) {
        expandedGroups.value.add(wrongQuestions.value[0].rubricId)
      }
    }
  } catch (e) { console.error('获取错题列表失败', e) }
  finally { loading.value = false }
}

async function loadReviewQuestions() {
  reviewLoading.value = true
  try {
    const res = await apiGetTodayReviewQuestions()
    if (res.code === 200) {
      reviewQuestions.value = res.data || []
    }
  } catch (e) { console.error('获取今日复习错题失败', e) }
  finally { reviewLoading.value = false }
}

// 按试卷分组
const groupedQuestions = computed(() => {
  const map = new Map<number, { rubricId: number; rubricTitle: string; questions: WrongQuestion[] }>()
  for (const q of wrongQuestions.value) {
    if (!map.has(q.rubricId)) {
      map.set(q.rubricId, { rubricId: q.rubricId, rubricTitle: q.rubricTitle || '未知试卷', questions: [] })
    }
    map.get(q.rubricId)!.questions.push(q)
  }
  return Array.from(map.values())
})

function toggleGroup(rubricId: number) {
  if (expandedGroups.value.has(rubricId)) {
    expandedGroups.value.delete(rubricId)
  } else {
    expandedGroups.value.add(rubricId)
  }
}

function toggleExpand(id: number) {
  if (expandedQuestions.value.has(id)) {
    expandedQuestions.value.delete(id)
  } else {
    expandedQuestions.value.add(id)
  }
}

// 当前复习题目
const currentReviewQuestion = computed(() => reviewQuestions.value[currentReviewIndex.value] || null)

// 复习已完成数
const reviewCompleted = computed(() => reviewCompletedSet.value.size)

// 揭晓答案
function revealAnswer() {
  reviewRevealed.value = true
}

// 判断复习答案是否正确
function isReviewCorrect(): boolean {
  const q = currentReviewQuestion.value
  if (!q) return false
  const userAns = reviewUserAnswer.value || reviewTextAnswer.value
  if (!userAns) return false
  if (hasOptions(q)) {
    if (isMultipleChoice(q)) {
      // 多选题：集合比较
      const userSet = new Set(userAns.split(',').map(s => s.trim().toUpperCase()))
      const correctAnswer = q.answer || ''
      const correctSet = new Set(correctAnswer.split(',').map(s => s.trim().toUpperCase()))
      if (userSet.size !== correctSet.size) return false
      for (const item of userSet) {
        if (!correctSet.has(item)) return false
      }
      return true
    }
    const options = getOptions(q)
    const selectedIndex = userAns.charCodeAt(0) - 65
    const selectedContent = options[selectedIndex]
    return userAns === q.answer || selectedContent === q.answer
  }
  return userAns.trim() === q.answer?.trim()
}

// 提交复习结果
async function submitReview(correct: boolean) {
  const q = currentReviewQuestion.value
  if (!q) return
  try {
    await apiSubmitReviewResult({ wrongQuestionId: q.id, correct })
    reviewCompletedSet.value.add(currentReviewIndex.value)
    // 进入下一题
    currentReviewIndex.value++
    reviewUserAnswer.value = ''
    reviewTextAnswer.value = ''
    reviewRevealed.value = false
    // 刷新统计
    loadStats()
  } catch (e) {
    console.error('提交复习结果失败', e)
  }
}

// 查看原题
function viewOriginalQuestion(q: WrongQuestion) {
  currentOriginalQuestion.value = q
  showOriginalQuestion.value = true
}

// 删除错题
function handleDelete(id: number) {
  pendingDeleteId.value = id
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (pendingDeleteId.value === null) return
  deleteLoading.value = true
  try {
    const res = await apiDeleteWrongQuestion(pendingDeleteId.value)
    if (res.code === 200) {
      wrongQuestions.value = wrongQuestions.value.filter(q => q.id !== pendingDeleteId.value)
      showDeleteConfirm.value = false
      pendingDeleteId.value = null
      await loadStats()
    }
  } catch (e) { console.error('删除错题失败', e) }
  finally { deleteLoading.value = false }
}

// 批量选择
function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) {
    selectedIds.value.clear()
  }
}

function toggleSelect(id: number) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

function selectAllInGroup(group: { questions: WrongQuestion[] }) {
  const allSelected = group.questions.every(q => selectedIds.value.has(q.id))
  if (allSelected) {
    group.questions.forEach(q => selectedIds.value.delete(q.id))
  } else {
    group.questions.forEach(q => selectedIds.value.add(q.id))
  }
}

async function confirmBatchDelete() {
  if (selectedIds.value.size === 0) return
  deleteLoading.value = true
  try {
    const res = await apiBatchDeleteWrongQuestions(Array.from(selectedIds.value))
    if (res.code === 200) {
      wrongQuestions.value = wrongQuestions.value.filter(q => !selectedIds.value.has(q.id))
      selectedIds.value.clear()
      batchMode.value = false
      showDeleteConfirm.value = false
      await loadStats()
    }
  } catch (e) { console.error('批量删除错题失败', e) }
  finally { deleteLoading.value = false }
}

// ========== 辅助函数 ==========

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

function getOptions(q: WrongQuestion | null): string[] {
  if (!q?.optionsJson) return []
  try {
    return JSON.parse(q.optionsJson)
  } catch { return [] }
}

function hasOptions(q: WrongQuestion | null): boolean {
  if (!q?.optionsJson) return false
  try {
    const opts = JSON.parse(q.optionsJson)
    return Array.isArray(opts) && opts.length > 0
  } catch { return false }
}

function isTextQuestion(q: WrongQuestion | null): boolean {
  if (!q) return false
  return q.questionType === 'short_answer' || q.questionType === 'calculation'
}

// 判断是否为多选题
function isMultipleChoice(q: WrongQuestion | null): boolean {
  return q?.questionType === 'multiple_choice'
}

// 多选题选项 toggle
function toggleReviewOption(optionLetter: string) {
  if (isMultipleChoice(currentReviewQuestion.value)) {
    const selected = reviewUserAnswer.value ? reviewUserAnswer.value.split(',').map(s => s.trim()) : []
    const idx = selected.indexOf(optionLetter)
    if (idx >= 0) {
      selected.splice(idx, 1)
    } else {
      selected.push(optionLetter)
    }
    selected.sort()
    reviewUserAnswer.value = selected.join(',')
  } else {
    reviewUserAnswer.value = optionLetter
  }
}

// 判断复习选项是否被选中
function isReviewOptionSelected(optionLetter: string): boolean {
  if (!reviewUserAnswer.value) return false
  return reviewUserAnswer.value.split(',').map(s => s.trim()).includes(optionLetter)
}

function parseJson(str: string): string[] {
  if (!str) return []
  try { return JSON.parse(str) } catch { return [] }
}

function formatRelativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return dateStr.substring(0, 10)
}

function getMasteryLabel(level: number): string {
  const map: Record<number, string> = { 0: '未掌握', 1: '初步掌握', 2: '基本掌握', 3: '完全掌握' }
  return map[level] || '未掌握'
}

function getMasteryColor(level: number): string {
  const map: Record<number, string> = { 0: '#ef4444', 1: '#f97316', 2: '#3b82f6', 3: '#10b981' }
  return map[level] || '#ef4444'
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28px; }
.page-title { font-size: 28px; font-weight: 700; color: var(--text-primary); letter-spacing: -0.02em; }
.loading-center { display: flex; justify-content: center; padding: 80px 0; }

/* 统计卡片 */
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 28px; }
.stat-card { padding: 20px; border: 1px solid var(--border-glass); border-radius: 16px; background: var(--card-bg); text-align: center; transition: all 0.3s ease; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.15); }
.stat-value { font-size: 32px; font-weight: 700; color: var(--text-primary); margin-bottom: 4px; }
.stat-label { font-size: 13px; color: var(--text-muted); }
.stat-card.highlight { border-color: #f97316; background: rgba(249,115,22,0.08); }
.stat-card.highlight .stat-value { color: #f97316; }
.stat-card.mastered { border-color: #10b981; background: rgba(16,185,129,0.08); }
.stat-card.mastered .stat-value { color: #10b981; }

@media (max-width: 768px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}

/* 标签切换 */
.tab-switch { display: flex; gap: 8px; margin-bottom: 28px; }
.tab-btn { padding: 10px 24px; border: 1px solid var(--border-glass); border-radius: 12px; background: var(--bg-glass); color: var(--text-muted); cursor: pointer; transition: all 0.3s ease; font-size: 14px; font-weight: 500; }
.tab-btn:hover { border-color: var(--accent); color: var(--text-secondary); }
.tab-btn.active { background: var(--accent-gradient); color: #fff; border-color: transparent; box-shadow: 0 4px 16px rgba(99,102,241,0.3); }

/* 空状态 */
.empty-state { text-align: center; padding: 80px 20px; }
.empty-state-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state-text { font-size: 18px; color: var(--text-secondary); margin-bottom: 8px; }
.empty-state-hint { font-size: 14px; color: var(--text-muted); }

/* 按试卷分组 */
.rubric-groups { display: flex; flex-direction: column; gap: 16px; }
.rubric-group { border: 1px solid var(--border-glass); border-radius: 16px; background: var(--card-bg); overflow: hidden; }
.group-header { display: flex; align-items: center; padding: 16px 20px; cursor: pointer; transition: background 0.2s ease; gap: 10px; }
.group-header:hover { background: rgba(255,255,255,0.03); }
:root.light .group-header:hover { background: rgba(0,0,0,0.03); }
.group-title { flex: 1; font-size: 16px; font-weight: 600; color: var(--text-primary); }
.group-count { font-size: 13px; color: var(--text-muted); margin-right: 12px; }
.group-arrow { font-size: 20px; color: var(--text-muted); transition: transform 0.3s ease; }
.group-arrow.expanded { transform: rotate(90deg); }
.group-questions { border-top: 1px solid var(--border-glass); }

/* 批量操作栏 */
.batch-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.batch-count { font-size: 14px; color: var(--text-muted); }
.batch-checkbox { width: 18px; height: 18px; accent-color: var(--accent); cursor: pointer; flex-shrink: 0; }
.wrong-question-card.selected { background: rgba(99,102,241,0.08); }

/* 错题卡片 */
.wrong-question-card { padding: 16px 20px; border-bottom: 1px solid var(--border-glass); transition: background 0.2s ease; }
.wrong-question-card:last-child { border-bottom: none; }
.wrong-question-card:hover { background: rgba(255,255,255,0.02); }
:root.light .wrong-question-card:hover { background: rgba(0,0,0,0.02); }

.wq-header { display: flex; align-items: center; justify-content: space-between; cursor: pointer; margin-bottom: 10px; }
.wq-header-left { display: flex; align-items: center; gap: 8px; }
.wq-header-right { display: flex; align-items: center; gap: 8px; }
.wq-time { font-size: 12px; color: var(--text-muted); }
.wq-expand-icon { font-size: 18px; color: var(--text-muted); transition: transform 0.3s ease; }
.wq-expand-icon.expanded { transform: rotate(90deg); }

.question-type-badge { font-size: 11px; padding: 3px 10px; border-radius: 20px; font-weight: 500; }
.question-type-badge.single_choice { background: rgba(99,102,241,0.12); color: #6366f1; }
.question-type-badge.multiple_choice { background: rgba(168,85,247,0.12); color: #a855f7; }
.question-type-badge.true_false { background: rgba(236,72,153,0.12); color: #ec4899; }
.question-type-badge.short_answer { background: rgba(59,130,246,0.12); color: #3b82f6; }
.question-type-badge.calculation { background: rgba(245,158,11,0.12); color: #f59e0b; }

.mastery-badge { font-size: 11px; padding: 3px 10px; border-radius: 20px; font-weight: 500; }

.wq-text { font-size: 15px; line-height: 1.7; color: var(--text-primary); margin-bottom: 12px; }
.wq-image { max-width: 100%; border-radius: 10px; margin-bottom: 12px; }

.wq-answers { display: flex; gap: 24px; margin-bottom: 8px; }
.wq-user-answer, .wq-correct-answer { font-size: 13px; }
.answer-label { color: var(--text-muted); }
.answer-value.wrong { color: #ef4444; font-weight: 500; }
.answer-value.correct { color: #10b981; font-weight: 500; }

.wq-detail { padding: 12px 0; border-top: 1px dashed var(--border-glass); margin-top: 8px; }
.wq-explanation { margin-bottom: 10px; }
.detail-label { font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.detail-content { font-size: 14px; color: var(--text-secondary); line-height: 1.7; padding: 10px 14px; background: rgba(59,130,246,0.08); border-radius: 10px; border: 1px solid rgba(59,130,246,0.15); }
.wq-steps { margin-bottom: 10px; }
.step-item { font-size: 14px; color: var(--text-secondary); padding: 4px 0 4px 14px; }
.wq-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-muted); }

.wq-actions { display: flex; justify-content: flex-end; margin-top: 8px; }

/* 复习模式 */
.review-mode { display: flex; flex-direction: column; gap: 24px; }
.progress-bar { text-align: center; }
.progress-text { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }
.progress-track { height: 8px; background: var(--bg-glass); border-radius: 4px; overflow: hidden; }
.progress-fill { height: 100%; background: var(--accent-gradient); transition: width 0.3s ease; border-radius: 4px; }

.review-card { padding: 28px; border: 1px solid var(--border-glass); border-radius: 20px; background: var(--card-bg); position: relative; }
.review-card::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: var(--accent-gradient); border-radius: 20px 20px 0 0; }

.review-question-header { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
.review-question-header .question-num { font-weight: 600; font-size: 17px; color: var(--text-primary); }
.review-question-text { font-size: 18px; line-height: 1.8; color: var(--text-primary); margin-bottom: 24px; }
.review-image { max-width: 100%; border-radius: 10px; margin-bottom: 16px; }

.review-options { display: flex; flex-direction: column; gap: 12px; margin-bottom: 24px; }
.option-item { display: flex; align-items: center; gap: 14px; padding: 14px 18px; border: 1px solid var(--border-glass); border-radius: 14px; cursor: pointer; transition: all 0.3s ease; color: var(--text-primary); }
.option-item:hover { border-color: var(--accent); background: rgba(99,102,241,0.05); }
.option-item.selected { border-color: var(--accent); background: var(--accent-light); color: var(--accent); }
.option-label { font-weight: 600; width: 28px; height: 28px; border-radius: 8px; background: var(--bg-glass); display: flex; align-items: center; justify-content: center; font-size: 14px; color: var(--text-primary); }
.option-item.selected .option-label { background: var(--accent); color: #fff; }

.text-answer-area { margin-bottom: 24px; }
.text-answer-input { width: 100%; min-height: 120px; padding: 16px; border: 1px solid var(--border-glass); border-radius: 14px; background: var(--bg-glass); color: var(--text-primary); font-size: 15px; font-family: inherit; resize: vertical; line-height: 1.7; }
.text-answer-input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }

.review-submit { text-align: center; margin-bottom: 8px; }

.review-reveal { margin-top: 16px; }
.reveal-section { padding: 20px; border-radius: 14px; margin-bottom: 20px; background: var(--bg-glass); border: 1px solid var(--border-glass); }
.your-answer { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
.your-answer.correct { color: var(--success); }
.your-answer:not(.correct) { color: #ef4444; }
.correct-answer { font-size: 14px; color: var(--text-muted); margin-bottom: 12px; }
.question-explanation { padding: 12px 16px; background: rgba(59,130,246,0.08); border-radius: 10px; color: #3b82f6; border: 1px solid rgba(59,130,246,0.15); font-size: 14px; line-height: 1.7; }

.review-judge { display: flex; gap: 16px; justify-content: center; }
.btn-success { padding: 12px 32px; border: none; border-radius: 12px; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: #fff; cursor: pointer; font-size: 15px; font-weight: 600; transition: all 0.3s ease; }
.btn-success:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(16,185,129,0.4); }
.btn-danger { padding: 12px 32px; border: none; border-radius: 12px; background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%); color: #fff; cursor: pointer; font-size: 15px; font-weight: 600; transition: all 0.3s ease; }
.btn-danger:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(239,68,68,0.4); }

/* 复习完成 */
.review-complete { text-align: center; padding: 80px 20px; }
.complete-icon { font-size: 64px; margin-bottom: 16px; }
.complete-text { font-size: 22px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.complete-sub { font-size: 14px; color: var(--text-muted); }

/* 弹窗 */
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; backdrop-filter: blur(4px); }
.confirm-modal { background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 16px; padding: 24px; min-width: 320px; text-align: center; }
.confirm-modal h3 { margin: 0 0 12px; font-size: 18px; color: var(--text-primary); }
.confirm-modal p { margin: 0 0 20px; color: var(--text-secondary); font-size: 14px; }
.confirm-btns { display: flex; gap: 12px; justify-content: center; }

/* 查看原题弹窗 */
.original-question-modal { background: var(--card-bg); border: 1px solid var(--border-glass); border-radius: 20px; max-width: 700px; width: 90%; max-height: 85vh; overflow: hidden; display: flex; flex-direction: column; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 24px; border-bottom: 1px solid var(--border-glass); }
.modal-header h3 { margin: 0; font-size: 20px; font-weight: 700; color: var(--text-primary); }
.close-btn { width: 36px; height: 36px; border: none; border-radius: 50%; background: var(--bg-glass); color: var(--text-secondary); font-size: 20px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s ease; }
.close-btn:hover { background: rgba(239,68,68,0.1); color: #ef4444; }
.modal-content { padding: 24px; overflow-y: auto; }
.original-question-item { display: flex; flex-direction: column; gap: 16px; }
.oq-header { display: flex; gap: 8px; margin-bottom: 4px; }
.oq-text { font-size: 17px; line-height: 1.8; color: var(--text-primary); }
.oq-image { max-width: 100%; border-radius: 12px; }
.oq-options { display: flex; flex-direction: column; gap: 10px; }
.oq-option { display: flex; gap: 12px; padding: 12px 16px; background: var(--bg-glass); border-radius: 12px; border: 1px solid var(--border-glass); }
.oq-option-label { font-weight: 600; color: var(--text-primary); width: 20px; }
.oq-option-text { color: var(--text-secondary); }
.oq-answer-section, .oq-explanation-section, .oq-steps-section { padding: 16px; background: rgba(16,185,129,0.06); border: 1px solid rgba(16,185,129,0.18); border-radius: 12px; }
.oq-explanation-section { background: rgba(59,130,246,0.08); border-color: rgba(59,130,246,0.18); }
.oq-steps-section { background: rgba(245,158,11,0.06); border-color: rgba(245,158,11,0.18); }
.oq-section-label { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px; }
.oq-answer-section .oq-section-label { color: #10b981; }
.oq-explanation-section .oq-section-label { color: #3b82f6; }
.oq-steps-section .oq-section-label { color: #f59e0b; }
.oq-answer-value { font-size: 16px; font-weight: 600; color: #10b981; }
.oq-explanation-content { font-size: 15px; line-height: 1.7; color: var(--text-secondary); }
.oq-step-item { font-size: 15px; line-height: 1.7; color: var(--text-secondary); padding: 4px 0; }
</style>
