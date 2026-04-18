import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref<number | null>(null)
  const username = ref('')
  const studentNo = ref('')
  const classId = ref<number | null>(null)
  const className = ref<string | null>(null)
  const avatarUrl = ref<string | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  function setUser(user: { token: string; userId: number; username: string; studentNo: string; classId: number | null; className: string | null; avatarUrl?: string | null }) {
    token.value = user.token
    userId.value = user.userId
    username.value = user.username
    studentNo.value = user.studentNo
    classId.value = user.classId
    className.value = user.className
    avatarUrl.value = user.avatarUrl || null
    localStorage.setItem('token', user.token)
    localStorage.setItem('userId', String(user.userId))
    localStorage.setItem('username', user.username)
    localStorage.setItem('studentNo', user.studentNo)
    localStorage.setItem('classId', String(user.classId ?? ''))
    localStorage.setItem('className', user.className ?? '')
    localStorage.setItem('avatarUrl', user.avatarUrl ?? '')
  }

  function loadFromStorage() {
    const t = localStorage.getItem('token')
    if (t) {
      token.value = t
      userId.value = Number(localStorage.getItem('userId')) || null
      username.value = localStorage.getItem('username') || ''
      studentNo.value = localStorage.getItem('studentNo') || ''
      classId.value = Number(localStorage.getItem('classId')) || null
      className.value = localStorage.getItem('className') || null
      avatarUrl.value = localStorage.getItem('avatarUrl') || null
    }
  }

  function updateAvatar(newAvatarUrl: string | null) {
    avatarUrl.value = newAvatarUrl
    localStorage.setItem('avatarUrl', newAvatarUrl ?? '')
  }

  function updateUsername(newUsername: string) {
    username.value = newUsername
    localStorage.setItem('username', newUsername)
  }

  function logout() {
    token.value = ''
    userId.value = null
    username.value = ''
    studentNo.value = ''
    classId.value = null
    className.value = null
    avatarUrl.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('studentNo')
    localStorage.removeItem('classId')
    localStorage.removeItem('className')
    localStorage.removeItem('avatarUrl')
  }

  return { token, userId, username, studentNo, classId, className, avatarUrl, isLoggedIn, setUser, loadFromStorage, updateAvatar, updateUsername, logout }
})
