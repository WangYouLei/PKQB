import { post, get, del, put } from './request'
import type { Result, RegisterRequest, LoginRequest, LoginResponse, HtmlFileItem, PresignedUrlResponse, QuestionItem, RubricItem, RubricQuestion, RubricGenerateResponse } from '@/types'

export const apiRegister = async (data: RegisterRequest) => {
  const res = await post<Result<void>>('/auth/register', data)
  return res
}

export const apiLogin = async (data: LoginRequest) => {
  const res = await post<Result<LoginResponse>>('/auth/login', data)
  return res
}

export const apiUploadFile = async (file: File, fileName: string, isPublic: boolean) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('file_name', fileName)
  formData.append('is_public', String(isPublic))
  const { default: request } = await import('./request')
  const res = await request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data as Result<HtmlFileItem>
}

export const apiGetMyFiles = async () => {
  const res = await get<Result<HtmlFileItem[]>>('/files/my')
  return res
}

export const apiGetPresignedUrl = async (fileId: number) => {
  const res = await get<Result<PresignedUrlResponse>>(`/files/presigned/${fileId}`)
  return res
}

export const apiDeleteFile = async (fileId: number) => {
  const res = await del<Result<void>>(`/files/${fileId}`)
  return res
}

export const apiGetClassPublicFiles = async () => {
  const res = await get<Result<HtmlFileItem[]>>('/files/class/public')
  return res
}

// ========== AI 相关 API ==========

/** 上传文档到知识库（文件） */
export const apiAddDocumentsFile = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const { default: request } = await import('./request')
  const res = await request.post('/ai/add-documentsFile', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data as Result<string>
}

/** 上传文档到知识库（文本） */
export const apiAddDocuments = async (content: string) => {
  const formData = new FormData()
  formData.append('content', content)
  const { default: request } = await import('./request')
  const res = await request.post('/ai/add-documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data as Result<string>
}

/** 上传题目文件解析 */
export const apiAddRubricFile = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const { default: request } = await import('./request')
  const res = await request.post('/ai/handle-rubricFile', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5分钟超时，文档解析需要较长时间
  })
  return res.data as Result<QuestionItem[]>
}

/** 上传题目文本解析 */
export const apiAddRubric = async (content: string) => {
  const formData = new FormData()
  formData.append('content', content)
  const { default: request } = await import('./request')
  const res = await request.post('/ai/add-Rubric', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data as Result<string>
}

/** 获取历史会话列表 */
export const apiGetHistoryList = async (userId: string, type: 'rag' | 'chat') => {
  const res = await get<Result<Object[]>>('/ai/get-historyList', { userId, type })
  return res
}

/** 根据 sessionId 获取具体对话历史 */
export const apiGetHistoryBySessionId = async (sessionId: string, userId: string, type: 'rag' | 'chat') => {
  const res = await get<Result<Object[]>>('/ai/get-history-by-sessionId', { sessionId, userId, type })
  return res
}

// ========== 试卷相关 API ==========

/** 获取我的试卷列表 */
export const apiGetMyRubrics = async () => {
  const res = await get<Result<RubricItem[]>>('/rubric/my')
  return res
}

/** 获取班级公开试卷列表 */
export const apiGetPublicRubrics = async () => {
  const res = await get<Result<RubricItem[]>>('/rubric/public')
  return res
}

/** 根据试卷ID获取题目列表 */
export const apiGetQuestionsByRubricId = async (rubricId: number) => {
  const res = await get<Result<any[]>>(`/rubric/${rubricId}/questions`)
  // 转换 JSON 字段为数组
  if (res.code === 200 && res.data) {
    res.data = res.data.map((q: any) => ({
      ...q,
      question: q.questionText,
      questionType: q.questionType,
      answer: q.answer,
      explanation: q.explanation || '',
      options: q.optionsJson ? JSON.parse(q.optionsJson) : [],
      calculationSteps: q.calculationStepsJson ? JSON.parse(q.calculationStepsJson) : []
    }))
  }
  return res
}

/** 修改试卷 */
export const apiUpdateRubric = async (data: { id: number; title: string; isPublic: boolean }) => {
  const res = await put<Result<void>>('/rubric/update', data)
  return res
}

/** 根据Rubric生成HTML文件 */
export const apiGenerateRubricHtml = async (rubricId: number, fileName?: string, isPublic?: boolean) => {
  const res = await post<Result<RubricGenerateResponse>>('/rubric/generate-html', {
    rubricId,
    fileName,
    isPublic
  })
  return res
}
