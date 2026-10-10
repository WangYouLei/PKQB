import { ref } from 'vue'

export interface ToastMessage {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
  duration?: number
}

const messages = ref<ToastMessage[]>([])
let idCounter = 0

function addToast(type: ToastMessage['type'], message: string, duration = 3000) {
  const id = ++idCounter
  messages.value.push({ id, type, message, duration })
  
  if (duration > 0) {
    setTimeout(() => {
      removeToast(id)
    }, duration)
  }
  
  return id
}

function removeToast(id: number) {
  const index = messages.value.findIndex(m => m.id === id)
  if (index > -1) {
    messages.value.splice(index, 1)
  }
}

function success(message: string, duration?: number) {
  return addToast('success', message, duration)
}

function error(message: string, duration?: number) {
  return addToast('error', message, duration)
}

function warning(message: string, duration?: number) {
  return addToast('warning', message, duration)
}

function info(message: string, duration?: number) {
  return addToast('info', message, duration)
}

export function useToast() {
  return {
    messages,
    success,
    error,
    warning,
    info,
    remove: removeToast
  }
}
