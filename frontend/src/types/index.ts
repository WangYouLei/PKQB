export interface User {
  userId: number
  username: string
  studentNo: string
  classId: number | null
  token: string
}

export interface HtmlFileItem {
  id: number
  userId: number
  fileName: string
  isPrivate: boolean  // 是否私有：true=私有（仅自己可见），false=公开（班级可见）
  creatorName?: string
  createTime: string
  updateTime: string
}

export interface RegisterRequest {
  username: string
  password: string
  studentNo: string
  classId: number | null
  className?: string
}

export interface LoginRequest {
  studentNo: string
  password: string
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  studentNo: string
  classId: number | null
  className: string | null
  avatarUrl: string | null
}

export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

// ========== AI 相关类型 ==========

export interface QuestionResource {
  id?: number
  questionId?: number
  type: 'question_image' | 'option_image' | 'answer_image' | 'explanation_image'
  label?: string
  url: string
  mimeType?: string
  sortOrder?: number
}

export interface QuestionItem {
  question: string
  questionType: 'single_choice' | 'multiple_choice' | 'true_false' | 'short_answer' | 'calculation'
  options: string[]
  answer: string
  explanation: string
  calculationSteps?: string[]
  calculationStepsText?: string
  resources?: QuestionResource[]
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}

export interface HistorySession {
  sessionId: string
  messages: ChatMessage[]
  createTime: string
}

// ========== 试卷相关类型 ==========

export interface RubricItem {
  id: number
  title: string
  className: string
  createId: number
  createStudentNo: string
  creatorName?: string
  isPrivate: boolean
  createTime: string
  updateTime: string
  questionCount?: number
}

export interface RubricQuestion {
  id?: number
  rubricId?: number
  questionText: string
  questionType: string
  optionsJson?: string
  answer: string
  explanation?: string
  calculationStepsJson?: string
  resources?: QuestionResource[]
  orderIndex?: number
}

/** Rubric生成HTML响应 */
export interface RubricGenerateResponse {
  fileId: number
  fileName: string
  downloadUrl: string
  createTime: string
}

export interface ApiKeyStatus {
  hasOwnApiKey: boolean
  currentMode: 'LOCAL' | 'PERSONAL'
  hasRateLimit: boolean
  mainModel?: string
  assistantModels: ModelsEntity[]
  visionModel: ModelsEntity | null
  allModels: ModelsEntity[]
  modelCount: number
  maxModelCount: number
  canAddModel: boolean
  supportsMultiModel: boolean
}

export interface ModelsEntity {
  id: number
  userId: number
  modelName: string
  modelType: number  // 0=主模型，1=辅助模型，2=视觉模型
  createTime: string
  updateTime: string
}

export interface ApiError {
  code: number
  message: string
}

export interface HistoryMessage {
  messageType: 'USER' | 'ASSISTANT'
  text: string
  timestamp: number
}

export type QuestionType = 'single_choice' | 'multiple_choice' | 'true_false' | 'short_answer' | 'calculation'

export interface AiSolveResult {
  answer?: string
  explanation?: string
  steps?: string
}

export interface HistorySessionData {
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}

export interface ParsedQuestion {
  question: string
  questionType: QuestionType
  options?: string[]
  answer: string
  explanation?: string
  calculationSteps?: string[]
}

// ========== 错题本相关类型 ==========

export interface WrongQuestion {
  id: number
  userId: number
  questionId: number
  rubricId: number
  rubricTitle?: string
  questionText: string
  questionType: string
  optionsJson?: string
  answer: string
  explanation?: string
  calculationStepsJson?: string
  resources?: QuestionResource[]
  userAnswer?: string
  wrongCount: number
  correctCount: number
  easeFactor: number
  intervalDays: number
  nextReviewDate: string
  lastReviewTime?: string
  masteryLevel: number  // 0=未掌握, 1=初步掌握, 2=基本掌握, 3=完全掌握
  createTime: string
  updateTime: string
}

export interface WrongQuestionStats {
  totalCount: number
  todayReviewCount: number
  masteredCount: number
  learningCount: number
}

// ========== 通知相关类型 ==========

export interface NotificationItem {
  id: number
  userId: number
  type: string
  title: string
  message: string
  isRead: boolean
  createTime: string
}
