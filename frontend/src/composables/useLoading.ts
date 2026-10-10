import { ref } from 'vue'

const loading = ref(false)
const loadingText = ref('')

function showLoading(text = '加载中...') {
  loading.value = true
  loadingText.value = text
}

function hideLoading() {
  loading.value = false
  loadingText.value = ''
}

async function withLoading<T>(fn: () => Promise<T>, text = '加载中...'): Promise<T> {
  showLoading(text)
  try {
    return await fn()
  } finally {
    hideLoading()
  }
}

export function useLoading() {
  return {
    loading,
    loadingText,
    showLoading,
    hideLoading,
    withLoading
  }
}
