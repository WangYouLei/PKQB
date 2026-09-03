// 统一响应
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

// 管理员登录响应
export interface AdminLoginResponse {
  token: string
  userId: number
  username: string
  role: number
}

// 账号展示
export interface AdminUserVO {
  id: number
  username: string
  studentNo: string
  classId: number | null
  className: string | null
  role: number
  createTime: string
}

// 班级
export interface ClassEntity {
  id: number
  className: string
  createTime: string
}

// 账号分页查询参数
export interface AdminUserQuery {
  page?: number
  size?: number
  keyword?: string
  classId?: number
  role?: number
}

// 新增账号参数
export interface AdminUserCreate {
  username: string
  studentNo: string
  classId: number
  password: string
  role?: number
}

// 修改账号参数
export interface AdminUserUpdate {
  username?: string
  studentNo?: string
  classId?: number
  role?: number
}

// MyBatis-Plus 分页响应
export interface IPage<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
