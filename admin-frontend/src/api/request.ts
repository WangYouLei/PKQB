import axios, { type AxiosResponse } from 'axios'
import type { Result } from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：注入 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers['token'] = token
  }
  return config
})

// 响应拦截：401 跳登录
request.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error) => {
    // 登录接口的 401 表示账号密码错误，不应触发“登录已过期”跳转
    // config.url 保存调用时的相对路径（如 /admin/auth/login），用前缀精确匹配避免误伤其他接口
    const url = String(error.config?.url || '')
    const isLoginRequest = url === '/admin/auth/login' || url.startsWith('/admin/auth/login?')
    if (error.response?.status === 401 && !isLoginRequest) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_userId')
      localStorage.removeItem('admin_username')
      localStorage.removeItem('admin_role')
      window.location.href = '/login'
    }
    return Promise.reject(error.response?.data || error)
  }
)

async function get<T = unknown>(url: string, params?: Record<string, unknown>) {
  const res = await request.get<any, AxiosResponse<Result<T>>>(url, { params })
  return res.data
}

async function post<T = unknown>(url: string, data?: unknown, params?: Record<string, unknown>) {
  const res = await request.post<any, AxiosResponse<Result<T>>>(url, data, { params })
  return res.data
}

async function put<T = unknown>(url: string, data?: unknown, params?: Record<string, unknown>) {
  const res = await request.put<any, AxiosResponse<Result<T>>>(url, data, { params })
  return res.data
}

async function del<T = unknown>(url: string, data?: unknown, params?: Record<string, unknown>) {
  const res = await request.delete<any, AxiosResponse<Result<T>>>(url, { data, params })
  return res.data
}

export { get, post, put, del }
export default request
