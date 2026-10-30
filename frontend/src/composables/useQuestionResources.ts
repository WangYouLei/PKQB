import type { QuestionResource } from '@/types'

export function getQuestionImageUrl(resources?: QuestionResource[]): string | undefined {
  if (!resources || resources.length === 0) return undefined
  return resources.find(r => r.type === 'question_image')?.url
}

export function getQuestionImages(resources?: QuestionResource[]): QuestionResource[] {
  if (!resources || resources.length === 0) return []
  return resources.filter(r => r.type === 'question_image').sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
}

export function getOptionImage(resources: QuestionResource[] | undefined, label: string): string | undefined {
  if (!resources || resources.length === 0) return undefined
  return resources.find(r => r.type === 'option_image' && r.label === label)?.url
}

export function getExplanationImage(resources?: QuestionResource[]): string | undefined {
  if (!resources || resources.length === 0) return undefined
  return resources.find(r => r.type === 'explanation_image')?.url
}

export function getAnswerImage(resources?: QuestionResource[]): string | undefined {
  if (!resources || resources.length === 0) return undefined
  return resources.find(r => r.type === 'answer_image')?.url
}
