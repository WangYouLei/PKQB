import { post, get, del, put } from './request'
import type { Result, RegisterRequest, LoginRequest, LoginResponse, HtmlFileItem, QuestionItem, RubricItem, RubricQuestion, RubricGenerateResponse, ApiKeyStatus, HistorySessionData } from '@/types'

export const apiRegister = async (data: RegisterRequest) => {
  const res = await post<void>('/auth/register', data)
  return res
}

export const apiLogin = async (data: LoginRequest) => {
  const res = await post<LoginResponse>('/auth/login', data)
  return res
}

export const apiLogout = async () => {
  const res = await post<void>('/auth/logout')
  return res
}

export const apiUploadFile = async (file: File, fileName: string, isPrivate: boolean) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('file_name', fileName)
  formData.append('is_private', String(isPrivate))
  const { default: request } = await import('./request')
  const res = await request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data as Result<HtmlFileItem>
}

export const apiGetMyFiles = async () => {
  const res = await get<HtmlFileItem[]>('/files/my')
  return res
}

export const apiDeleteFile = async (fileId: number) => {
  const res = await del<void>(`/files/${fileId}`)
  return res
}

export const apiUpdateFile = async (fileId: number, fileName?: string, isPrivate?: boolean) => {
  const res = await put<HtmlFileItem>(`/files/${fileId}`, null, { 
    file_name: fileName, 
    is_private: isPrivate 
  })
  return res
}

export const apiGetClassPublicFiles = async () => {
  const res = await get<HtmlFileItem[]>('/files/class/public')
  return res
}

// ========== AI 相关 API ==========

/** 上传文档到知识库（文件） */
export const apiAddDocumentsFile = async (file: File, userId: number) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('userId', String(userId))
  const { default: request } = await import('./request')
  const res = await request.post('/ai/add-documentsFile', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data as Result<string>
}

/** 上传题目文件解析（AI） */
export const apiAddRubricFile = async (file: File, userId: number) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('userId', String(userId))
  const { default: request } = await import('./request')
  const res = await request.post('/ai/handle-rubricFile', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
  return res.data as Result<QuestionItem[]>
}

/** 上传题目文件解析（本地） */
export const apiAddRubricFileLocal = async (file: File, userId: number) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('userId', String(userId))
  const { default: request } = await import('./request')
  const res = await request.post('/ai/handle-rubricFile-local', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
  return res.data as Result<QuestionItem[]>
}

/** 获取历史会话列表 */
export const apiGetHistoryList = async (userId: string, type: 'rag' | 'chat') => {
  const res = await get<string[]>('/ai/get-historyList', { userId, type })
  return res
}

/** 根据 sessionId 获取具体对话历史 */
export const apiGetHistoryBySessionId = async (sessionId: string, userId: string, type: 'rag' | 'chat') => {
  const res = await get<HistorySessionData[]>('/ai/get-history-by-sessionId', { sessionId, userId, type })
  return res
}

/** 删除会话历史 */
export const apiDeleteHistory = async (sessionId: string, userId: string, type: 'rag' | 'chat') => {
  const res = await del<boolean>('/ai/delete-history', { sessionId, userId, type })
  return res
}

/** 删除会话中的指定消息 */
export const apiDeleteMessages = async (sessionId: string, userId: string, type: 'rag' | 'chat', messageIndices: number[]) => {
  const res = await del<boolean>('/ai/delete-messages', { sessionId, userId, type, messageIndices: messageIndices.join(',') })
  return res
}

/** 获取使用次数 */
export const apiGetUsage = async (userId: string, type: 'rag' | 'chat') => {
  const res = await get<{ hasOwnApiKey: boolean; used: number; limit: number; remaining: number }>('/ai/usage', { userId, type })
  return res
}

// ========== 试卷相关 API ==========

/** 获取我的试卷列表 */
export const apiGetMyRubrics = async () => {
  const res = await get<RubricItem[]>('/rubric/my')
  return res
}

/** 获取班级公开试卷列表 */
export const apiGetPublicRubrics = async () => {
  const res = await get<RubricItem[]>('/rubric/public')
  return res
}

/** 根据试卷ID获取题目列表 */
export const apiGetQuestionsByRubricId = async (rubricId: number) => {
  const res = await get<RubricQuestion[]>(`/rubric/${rubricId}/questions`)
  return res
}

/** 修改试卷 */
export const apiUpdateRubric = async (data: { id: number; title: string; isPrivate: boolean }) => {
  const res = await put<Result<void>>('/rubric/update', data)
  return res
}

/** 删除试卷 */
export const apiDeleteRubric = async (rubricId: number) => {
  const res = await del<Result<void>>(`/rubric/${rubricId}`)
  return res
}

/** 根据Rubric生成HTML文件 */
export const apiGenerateRubricHtml = async (rubricId: number, fileName?: string, isPrivate?: boolean) => {
  const res = await post<Result<RubricGenerateResponse>>('/rubric/generate-html', {
    rubricId,
    fileName,
    isPrivate
  })
  return res
}

// ========== API Key 管理 ==========

/** 保存用户 API Key */
export const apiSaveApiKey = async (userId: number, apiKey: string) => {
  const res = await post<string>('/apikey', null, { userId, apiKey })
  return res
}

/** 删除用户 API Key */
export const apiDeleteApiKey = async (userId: number) => {
  const res = await del<string>('/apikey', { userId })
  return res
}

/** 获取用户 API Key 状态 */
export const apiGetApiKeyStatus = async (userId: number) => {
  const res = await get<ApiKeyStatus>('/apikey/status', { userId })
  return res
}

/** 添加用户模型 */
export const apiAddModel = async (userId: number, modelName: string, isMain: boolean = false) => {
  const res = await post<string>('/apikey/model', null, { userId, modelName, isMain })
  return res
}

/** 删除用户模型 */
export const apiDeleteModel = async (userId: number, modelId: number) => {
  const res = await del<string>('/apikey/model', { userId, modelId })
  return res
}

/** 设置主模型 */
export const apiSetMainModel = async (userId: number, modelId: number) => {
  const res = await put<string>('/apikey/model/main', null, { userId, modelId })
  return res
}

// ========== 用户头像管理 ==========

/** 获取头像上传路径 */
export const apiGetAvatarUploadPath = async () => {
  const res = await get<{ objectKey: string; uploadUrl: string }>('/user/avatar/upload-path')
  return res
}

/** 更新用户头像 */
export const apiUpdateAvatar = async (objectKey: string) => {
  const res = await put<string>('/user/avatar', { objectKey })
  return res
}

// ========== 用户信息管理 ==========

/** 修改用户名 */
export const apiUpdateUsername = async (username: string) => {
  const res = await put<void>('/user/username', null, { username })
  return res
}

/** 修改密码 */
export const apiUpdatePassword = async (oldPassword: string, newPassword: string) => {
  const res = await put<void>('/user/password', null, { oldPassword, newPassword })
  return res
}

// ========== AI 解答相关 API ==========

/** AI解答题目 */
export const apiAiSolveQuestion = async (params: {
  questionText: string
  questionType: string
  optionsJson?: string
  generateType: 'answer' | 'explanation' | 'steps' | 'all'
  userId: number
}) => {
  const res = await post<string>('/ai/ai-solve', null, params)
  return res
}

/** 更新题目 */
export const apiUpdateQuestion = async (data: {
  id: number
  questionType?: string
  questionText?: string
  optionsJson?: string
  answer?: string
  explanation?: string
  calculationStepsJson?: string
}) => {
  const res = await put<void>('/rubric/question/update', data)
  return res
}

/** 批量保存题目 */
export const apiBatchSaveQuestions = async (rubricId: number, questions: RubricQuestion[]) => {
  const res = await post<void>(`/rubric/${rubricId}/questions/batch`, questions)
  return res
}
