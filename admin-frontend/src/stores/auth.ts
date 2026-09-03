import { defineStore } from 'pinia'
import { apiAdminLogin } from '@/api'
import type { AdminLoginResponse } from '@/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('admin_token') || '',
    userId: Number(localStorage.getItem('admin_userId')) || 0,
    username: localStorage.getItem('admin_username') || '',
    role: Number(localStorage.getItem('admin_role')) || 0
  }),
  getters: {
    isLoggedIn: (s) => !!s.token && s.role === 1
  },
  actions: {
    async login(username: string, password: string) {
      const res = await apiAdminLogin({ username, password })
      const data: AdminLoginResponse = res.data!
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.role = data.role
      localStorage.setItem('admin_token', data.token)
      localStorage.setItem('admin_userId', String(data.userId))
      localStorage.setItem('admin_username', data.username)
      localStorage.setItem('admin_role', String(data.role))
    },
    logout() {
      this.token = ''
      this.userId = 0
      this.username = ''
      this.role = 0
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_userId')
      localStorage.removeItem('admin_username')
      localStorage.removeItem('admin_role')
    }
  }
})
