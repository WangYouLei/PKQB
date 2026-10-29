import { reactive, ref, watch, onScopeDispose } from 'vue'
import { apiAddWrongQuestion, apiAiSolveQuestion, apiBatchSaveQuestions, apiGetQuestionsByRubricId } from '@/api'
import type { RubricItem, RubricQuestion } from '@/types'

export interface ExamPlayerOptions {
  /** 页面自行决定提示方式（弹窗或全局 toast） */
  notify: (title: string, message: string) => void
}

/**
 * 试卷作答 / 背题 / 改题模式的公共逻辑，供 MyFiles.vue 与 ClassFiles.vue 复用
 */
export function useExamPlayer(options: ExamPlayerOptions) {
  const notify = options.notify

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

  // AI解答加载状态
  const aiLoading = reactive<Record<number, boolean>>({})
  const aiResults = reactive<Record<number, { answer?: string; explanation?: string; steps?: string }>>({})
  const saveAllLoading = ref(false)

  // 选项/计算步骤解析缓存：题目加载时一次性解析，渲染时只查表，避免模板里反复 JSON.parse
  // 缓存记录来源字符串，源字段被编辑后自动失效，不会出现陈旧数据
  interface ParsedCache {
    source: string
    value: string[]
  }

  const optionsCache = new WeakMap<RubricQuestion, ParsedCache>()
  const stepsCache = new WeakMap<RubricQuestion, ParsedCache>()

  function parseStringArray(json?: string): string[] {
    if (!json) return []
    try {
      const value = JSON.parse(json)
      return Array.isArray(value) ? value : []
    } catch {
      return []
    }
  }

  function readCache(cache: WeakMap<RubricQuestion, ParsedCache>, q: RubricQuestion, source?: string): string[] {
    const key = source || ''
    const cached = cache.get(q)
    if (cached && cached.source === key) return cached.value
    const value = parseStringArray(key)
    cache.set(q, { source: key, value })
    return value
  }

  function initQuestionOptions(q: RubricQuestion) {
    if (q.questionType === 'true_false') {
      q.optionsJson = JSON.stringify(['正确', '错误'])
      normalizeTrueFalseAnswer(q)
    } else if ((q.questionType === 'single_choice' || q.questionType === 'multiple_choice') && !q.optionsJson) {
      q.optionsJson = JSON.stringify(['', '', '', ''])
    }
  }

  function prepareQuestion(q: RubricQuestion) {
    initQuestionOptions(q)
    getOptions(q)
    getSteps(q)
  }

  // 只在题目列表整体替换或题目类型变化时重新初始化，避免 deep 侦听遍历整个题目数组
  watch(
    () => questions.value.map(q => q.questionType),
    () => {
      questions.value.forEach(q => prepareQuestion(q))
    }
  )

  // 答对后300ms自动跳题的定时器：弹窗关闭/切题/重开试卷时清理，避免关闭后仍推进题号
  let autoNextTimer: ReturnType<typeof setTimeout> | null = null
  function clearAutoNext() {
    if (autoNextTimer !== null) {
      clearTimeout(autoNextTimer)
      autoNextTimer = null
    }
  }
  watch(showRubricDetail, (open) => {
    if (!open) clearAutoNext()
  })
  onScopeDispose(clearAutoNext)

  let openSequence = 0

  /**
   * 打开试卷并加载题目。
   * 快速连续点击不同试卷时，慢的响应可能晚于后点的请求返回，这里用递增序号丢弃过期响应。
   */
  async function openRubric(rubric: RubricItem) {
    const seq = ++openSequence
    currentRubric.value = rubric
    showRubricDetail.value = true
    examMode.value = 'practice'
    questionsLoading.value = true
    try {
      const res = await apiGetQuestionsByRubricId(rubric.id)
      if (seq !== openSequence) return
      if (res.code === 200) {
        questions.value = res.data || []
      }
    } catch (e) {
      if (seq !== openSequence) return
      console.error('获取题目列表失败', e)
    } finally {
      if (seq === openSequence) {
        questionsLoading.value = false
      }
    }
  }

  // 重置作答进度（打开新试卷时由页面决定是否调用）
  function resetProgress() {
    currentQuestionIndex.value = 0
    userAnswers.value = {}
    showAnswer.value = false
  }

  // 判断题目是否有选项
  function getOptions(q: RubricQuestion): string[] {
    return readCache(optionsCache, q, q.optionsJson)
  }

  function hasOptions(q: RubricQuestion): boolean {
    return getOptions(q).length > 0
  }

  function needsOptions(questionType: string): boolean {
    return questionType === 'single_choice' || questionType === 'multiple_choice' || questionType === 'true_false'
  }

  // 改题模式下展示用的选项；返回副本，供模板只读渲染
  function getOptionsForEdit(q: RubricQuestion): string[] {
    if (q.questionType === 'true_false') {
      return ['正确', '错误']
    }
    const opts = getOptions(q)
    return opts.length > 0 ? opts.slice() : ['', '', '', '']
  }

  /**
   * 改题模式下修改某个选项的内容
   * 原来模板用 v-model 绑定 getOptionsForEdit(q)[oi]，写进的是临时数组，
   * 内容永远回不到 optionsJson；这里统一以 optionsJson 为唯一数据源写回
   */
  function updateOption(q: RubricQuestion, index: number, value: string) {
    const options = getOptionsForEdit(q)
    while (options.length <= index) {
      options.push('')
    }
    options[index] = value
    q.optionsJson = JSON.stringify(options)
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

  // 计算步骤（解析结果缓存，渲染时查表）
  function getSteps(q: RubricQuestion): string[] {
    return readCache(stepsCache, q, q.calculationStepsJson)
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
          clearAutoNext()
          autoNextTimer = setTimeout(() => {
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
        notify('成功', 'AI解答完成，请查看下方生成结果')
      } else {
        const errorMsg = res.message || ''
        if (errorMsg.includes('API') || errorMsg.includes('key') || errorMsg.includes('Key') || errorMsg.includes('model') || errorMsg.includes('Model')) {
          notify('错误', '上传的API Key或模型名称有误，请核验')
        } else {
          notify('错误', res.message || 'AI解答失败')
        }
      }
    } catch (e: unknown) {
      console.error('AI解答失败', e)
      const errorMsg = (e as Error)?.message || ''
      if (errorMsg.includes('API') || errorMsg.includes('key') || errorMsg.includes('Key') || errorMsg.includes('model') || errorMsg.includes('Model') || errorMsg.includes('401') || errorMsg.includes('403') || errorMsg.includes('invalid')) {
        notify('错误', '上传的API Key或模型名称有误，请核验')
      } else {
        notify('错误', 'AI解答失败')
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
        notify('成功', '保存成功')
      } else {
        notify('错误', res.message || '保存失败')
      }
    } catch (e) {
      console.error('保存题目失败', e)
      notify('错误', '保存失败')
    } finally {
      saveAllLoading.value = false
    }
  }

  return {
    // 状态
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
    // 试卷打开
    openRubric,
    resetProgress,
    // 题目解析/选项
    hasOptions,
    needsOptions,
    getOptions,
    getOptionsForEdit,
    updateOption,
    getSteps,
    addOption,
    removeOption,
    // 作答
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
    // AI 解答与改题
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
  }
}
