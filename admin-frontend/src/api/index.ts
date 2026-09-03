import { get, post, put, del } from './request'
import type {
  AdminLoginResponse,
  AdminUserVO,
  AdminUserQuery,
  AdminUserCreate,
  AdminUserUpdate,
  ClassEntity,
  IPage
} from '@/types'

// ========== 管理端认证 ==========
export const apiAdminLogin = (data: { username: string; password: string }) =>
  post<AdminLoginResponse>('/admin/auth/login', data)

// ========== 账号管理 ==========
export const apiAdminUserPage = (params: AdminUserQuery) =>
  get<IPage<AdminUserVO>>('/admin/user/page', params as unknown as Record<string, unknown>)

export const apiAdminUserCreate = (data: AdminUserCreate) =>
  post<void>('/admin/user', data)

export const apiAdminUserUpdate = (id: number, data: AdminUserUpdate) =>
  put<void>(`/admin/user/${id}`, data)

export const apiAdminUserDelete = (id: number) =>
  del<void>(`/admin/user/${id}`)

export const apiAdminResetPassword = (id: number, newPassword: string) =>
  put<void>(`/admin/user/${id}/password`, { newPassword })

// ========== 班级管理 ==========
export const apiAdminClassList = () => get<ClassEntity[]>('/admin/class')

export const apiAdminClassCreate = (className: string) =>
  post<ClassEntity>('/admin/class', { className })

export const apiAdminClassUpdate = (id: number, className: string) =>
  put<ClassEntity>(`/admin/class/${id}`, { className })

export const apiAdminClassDelete = (id: number) =>
  del<void>(`/admin/class/${id}`)
