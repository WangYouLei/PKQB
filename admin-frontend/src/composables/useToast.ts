import { ref } from 'vue'

export interface ToastItem {
  id: number
  type: 'success' | 'error'
  message: string
}

const toasts = ref<ToastItem[]>([])
let id = 0

export function useToast() {
  function push(type: 'success' | 'error', message: string, duration = 2500) {
    const tid = ++id
    toasts.value.push({ id: tid, type, message })
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== tid)
    }, duration)
  }
  return {
    toasts,
    success: (m: string) => push('success', m),
    error: (m: string) => push('error', m)
  }
}
