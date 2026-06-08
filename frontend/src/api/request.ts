import axios, { type AxiosResponse } from 'axios'
import type { Result } from '@/types'
import { useToast } from '@/composables/useToast'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  (error) => {
    const toast = useToast()
    
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('studentNo')
      localStorage.removeItem('classId')
      localStorage.removeItem('className')
      localStorage.removeItem('avatarUrl')
      toast.error('登录已过期，请重新登录')
      window.location.href = '/login'
    } else if (error.response?.status === 403) {
      toast.error('权限不足')
    } else if (error.response?.status === 404) {
      toast.error('资源不存在')
    } else if (error.response?.status === 500) {
      toast.error('服务器错误，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      toast.error('请求超时，请稍后重试')
    } else if (!error.response) {
      toast.error('网络错误，请检查网络连接')
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
