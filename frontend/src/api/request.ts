import axios, { type AxiosResponse } from 'axios'
import type { Result } from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.token = token
  }
  return config
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error.response?.data || error)
  }
)

// Wrapper functions that return Result<T>
async function get<T = unknown>(url: string, params?: Record<string, unknown>) {
  const res = await request.get<any, AxiosResponse<Result<T>>>(url, { params })
  return res.data
}

async function post<T = unknown>(url: string, data?: unknown) {
  const res = await request.post<any, AxiosResponse<Result<T>>>(url, data)
  return res.data
}

async function put<T = unknown>(url: string, data?: unknown) {
  const res = await request.put<any, AxiosResponse<Result<T>>>(url, data)
  return res.data
}

async function del<T = unknown>(url: string) {
  const res = await request.delete<any, AxiosResponse<Result<T>>>(url)
  return res.data
}

export { get, post, put, del }
export default request
