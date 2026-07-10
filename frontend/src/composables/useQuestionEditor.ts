import { ref, watch } from 'vue'
import type { RubricQuestion } from '@/types'
import { apiAiSolveQuestion } from '@/api'

const POSITIVE_KEYWORDS = ['√', '正确', '对', '是', 'yes', 'YES', 'Yes', 'true', 'TRUE', 'True', '1', 'A', 'a']
const NEGATIVE_KEYWORDS = ['×', '错误', '错', '否', 'no', 'NO', 'No', 'false', 'FALSE', 'False', '0', 'B', 'b']

export function useQuestionEditor() {
  const aiResults = ref<Record<number, { answer?: string; explanation?: string; steps?: string }>>({})
  const aiLoading = ref<Record<number, boolean>>({})

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
    if (POSITIVE_KEYWORDS.includes(answer)) {
      q.answer = '正确'
    } else if (NEGATIVE_KEYWORDS.includes(answer)) {
      q.answer = '错误'
    }
  }

  function normalizeAnswerText(answer: string): string {
    if (!answer) return ''
    const trimmed = answer.trim()
    if (POSITIVE_KEYWORDS.includes(trimmed)) {
      return '正确'
    } else if (NEGATIVE_KEYWORDS.includes(trimmed)) {
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

  function getOptions(q: RubricQuestion): string[] {
    if (!q.optionsJson) return []
    try {
      const opts = JSON.parse(q.optionsJson)
      return Array.isArray(opts) ? opts : []
    } catch {
      return []
    }
  }

  async function aiSolve(q: RubricQuestion, idx: number, generateType: 'answer' | 'explanation' | 'steps' | 'all' = 'all') {
    aiLoading.value[idx] = true
    try {
      const res = await apiAiSolveQuestion({
        questionText: q.questionText,
        questionType: q.questionType,
        optionsJson: q.optionsJson,
        generateType,
      })
      if (res.code === 200 && res.data) {
        if (generateType === 'all') {
          try {
            const parsed = JSON.parse(res.data)
            aiResults.value[idx] = {
              answer: parsed.answer || '',
              explanation: parsed.explanation || '',
              steps: parsed.steps || ''
            }
          } catch {
            aiResults.value[idx] = { answer: res.data }
          }
        } else {
          aiResults.value[idx] = { ...aiResults.value[idx], [generateType]: res.data }
        }
      }
    } catch (e: unknown) {
      console.error('AI解答失败:', e)
      alert((e as Error).message || 'AI解答失败')
    } finally {
      aiLoading.value[idx] = false
    }
  }

  function useAiAnswer(idx: number, questions: RubricQuestion[]) {
    if (aiResults.value[idx]?.answer) {
      const q = questions[idx]
      if (q.questionType === 'true_false') {
        q.answer = normalizeAnswerText(aiResults.value[idx].answer!)
      } else {
        q.answer = aiResults.value[idx].answer
      }
    }
  }

  function useAiExplanation(idx: number, questions: RubricQuestion[]) {
    if (aiResults.value[idx]?.explanation) {
      questions[idx].explanation = aiResults.value[idx].explanation
    }
  }

  function useAiSteps(idx: number, questions: RubricQuestion[]) {
    if (aiResults.value[idx]?.steps) {
      questions[idx].calculationStepsJson = aiResults.value[idx].steps
    }
  }

  function watchQuestions(questions: Ref<RubricQuestion[]>) {
    watch(questions, (newQuestions) => {
      newQuestions.forEach(q => {
        initQuestionOptions(q)
      })
    }, { deep: true })
  }

  return {
    aiResults,
    aiLoading,
    needsOptions,
    getOptionsForEdit,
    addOption,
    removeOption,
    initQuestionOptions,
    normalizeTrueFalseAnswer,
    normalizeAnswerText,
    formatAnswer,
    getOptions,
    aiSolve,
    useAiAnswer,
    useAiExplanation,
    useAiSteps,
    watchQuestions
  }
}

import type { Ref } from 'vue'
