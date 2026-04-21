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

export interface QuestionItem {
  question: string
  questionType: 'single_choice' | 'multiple_choice' | 'true_false' | 'short_answer' | 'calculation'
  options: string[]
  answer: string
  explanation: string
  calculationSteps?: string[]  // 计算题步骤
  calculationStepsText?: string  // 编辑模式用的文本字段
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
  id: number
  rubricId: number
  questionText: string
  questionType: string
  optionsJson?: string
  answer: string
  explanation?: string
  calculationStepsJson?: string
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
  model?: string
}
