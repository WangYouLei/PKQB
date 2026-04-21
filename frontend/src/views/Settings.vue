<template>
  <div class="settings-page">
    <div class="page-header">
      <h1>设置</h1>
      <p>管理您的 API Key 和其他设置</p>
    </div>

    <div class="settings-card">
      <div class="card-header">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="header-content">
          <h2>个人头像</h2>
          <p>上传您的个人头像</p>
        </div>
      </div>

      <div class="card-body">
        <div class="avatar-section">
          <div class="avatar-preview">
            <img 
              v-if="userStore.avatarUrl" 
              :src="userStore.avatarUrl" 
              alt="用户头像"
              class="avatar-image"
            />
            <div v-else class="avatar-placeholder">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
          </div>
          <div class="avatar-actions">
            <input
              type="file"
              ref="fileInput"
              accept="image/*"
              @change="handleFileSelect"
              style="display: none"
            />
            <button class="btn btn-primary" @click="triggerFileSelect" :disabled="uploading">
              <svg v-if="uploading" class="spinner" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
              </svg>
              {{ uploading ? '上传中...' : '上传头像' }}
            </button>
            <p class="avatar-hint">支持 JPG、PNG 格式，建议尺寸 200x200 像素</p>
          </div>
        </div>
      </div>
    </div>

    <div class="settings-card">
      <div class="card-header">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="header-content">
          <h2>个人信息</h2>
          <p>修改您的用户名和密码</p>
        </div>
      </div>

      <div class="card-body">
        <div class="form-section">
          <label class="input-label">用户名</label>
          <div class="input-group">
            <input
              type="text"
              v-model="usernameInput"
              placeholder="请输入新用户名"
              class="form-input"
              maxlength="20"
            />
            <button class="btn btn-primary btn-sm" @click="handleUpdateUsername" :disabled="savingUsername || !usernameInput.trim()">
              {{ savingUsername ? '保存中...' : '修改' }}
            </button>
          </div>
          <p class="input-hint">用户名长度2-20个字符</p>
        </div>

        <div class="form-section">
          <label class="input-label">修改密码</label>
          <div class="password-form">
            <input
              :type="showOldPassword ? 'text' : 'password'"
              v-model="oldPasswordInput"
              placeholder="请输入原密码"
              class="form-input"
            />
            <div class="input-group">
              <input
                :type="showNewPassword ? 'text' : 'password'"
                v-model="newPasswordInput"
                placeholder="请输入新密码"
                class="form-input"
                maxlength="50"
              />
              <button class="toggle-visibility-btn" @click="showNewPassword = !showNewPassword">
                <svg v-if="showNewPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
            <button class="btn btn-primary" @click="handleUpdatePassword" :disabled="savingPassword || !oldPasswordInput || !newPasswordInput">
              <svg v-if="savingPassword" class="spinner" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
              </svg>
              {{ savingPassword ? '修改中...' : '修改密码' }}
            </button>
          </div>
          <p class="input-hint">新密码长度至少6个字符</p>
        </div>
      </div>
    </div>

    <div class="settings-card">
      <div class="card-header">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/>
          </svg>
        </div>
        <div class="header-content">
          <h2>阿里云百炼 API Key</h2>
          <p>配置您自己的 API Key 以解除使用次数限制</p>
        </div>
      </div>

      <div class="card-body">
        <div class="status-info" v-if="status">
          <div class="status-item">
            <span class="status-label">当前状态</span>
            <span class="status-value" :class="{ 'has-key': status.hasOwnApiKey }">
              {{ status.hasOwnApiKey ? '已配置个人 API Key' : '使用平台 API Key' }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">使用模式</span>
            <span class="status-value">
              {{ status.currentMode === 'PERSONAL' ? '个人 Key（无限制）' : '平台 Key（有限制）' }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">次数限制</span>
            <span class="status-value" :class="{ 'limited': status.hasRateLimit }">
              {{ status.hasRateLimit ? '有限制' : '无限制' }}
            </span>
          </div>
        </div>

        <div class="api-key-section">
          <label class="input-label">API Key</label>
          <div class="input-group">
            <input
              :type="showApiKey ? 'text' : 'password'"
              v-model="apiKeyInput"
              placeholder="请输入您的阿里云百炼 API Key"
              class="api-key-input"
            />
            <button class="toggle-visibility-btn" @click="showApiKey = !showApiKey">
              <svg v-if="showApiKey" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                <line x1="1" y1="1" x2="23" y2="23"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </button>
          </div>
          <p class="input-hint">
            您可以在 
            <a href="https://bailian.console.aliyun.com/" target="_blank" rel="noopener">阿里云百炼控制台</a> 
            获取 API Key
          </p>
        </div>

        <div class="action-buttons">
          <button class="btn btn-primary" @click="handleSave" :disabled="saving || !apiKeyInput.trim()">
            <svg v-if="saving" class="spinner" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
            </svg>
            {{ saving ? '保存中...' : '保存 API Key' }}
          </button>
          <button 
            class="btn btn-danger" 
            @click="handleDelete" 
            :disabled="deleting || !status?.hasOwnApiKey"
            v-if="status?.hasOwnApiKey"
          >
            <svg v-if="deleting" class="spinner" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
            </svg>
            {{ deleting ? '删除中...' : '删除 API Key' }}
          </button>
        </div>

        <!-- 模型选择 -->
        <div class="model-section" v-if="status?.hasOwnApiKey">
          <label class="input-label">模型</label>
          <div class="input-group">
            <input 
              v-model="modelInput" 
              type="text" 
              class="api-key-input" 
              placeholder="请输入模型名称，如 qwen-plus"
            />
          </div>
          <p class="input-hint">
            您可以在 <a href="https://bailian.console.aliyun.com/" target="_blank" rel="noopener">阿里云百炼控制台</a> 获取模型名称
          </p>
          <div class="action-buttons">
            <button class="btn btn-primary" @click="handleSaveModel" :disabled="savingModel || !modelInput.trim()">
              <svg v-if="savingModel" class="spinner" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
              </svg>
              {{ savingModel ? '保存中...' : '保存模型' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="info-card">
      <div class="info-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="16" x2="12" y2="12"/>
          <line x1="12" y1="8" x2="12.01" y2="8"/>
        </svg>
      </div>
      <div class="info-content">
        <h3>使用说明</h3>
        <ul>
          <li><strong>平台 API Key</strong>：使用项目提供的 API Key，每日有使用次数限制（对话10次、知识库问答10次、上传知识库5次、上传题目5次）</li>
          <li><strong>个人 API Key</strong>：使用您自己的 API Key，无使用次数限制，费用由您自己承担</li>
          <li>API Key 会加密存储，确保安全性</li>
          <li>删除 API Key 后将自动切换回平台 API Key</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGetApiKeyStatus, apiSaveApiKey, apiDeleteApiKey, apiSaveModel, apiGetAvatarUploadPath, apiUpdateAvatar, apiUpdateUsername, apiUpdatePassword } from '@/api'
import type { ApiKeyStatus } from '@/types'

const userStore = useUserStore()
const status = ref<ApiKeyStatus | null>(null)
const apiKeyInput = ref('')
const showApiKey = ref(false)
const saving = ref(false)
const deleting = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const modelInput = ref('')
const savingModel = ref(false)

const usernameInput = ref('')
const savingUsername = ref(false)
const oldPasswordInput = ref('')
const newPasswordInput = ref('')
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const savingPassword = ref(false)

onMounted(async () => {
  await loadStatus()
})

async function loadStatus() {
  if (!userStore.userId) return
  try {
    const res = await apiGetApiKeyStatus(userStore.userId)
    if (res.code === 200) {
      status.value = res.data
      modelInput.value = res.data?.model || ''
    }
  } catch (error) {
    console.error('获取 API Key 状态失败:', error)
  }
}

async function handleSaveModel() {
  if (!modelInput.value.trim() || !userStore.userId) return
  
  savingModel.value = true
  try {
    const res = await apiSaveModel(userStore.userId, modelInput.value.trim())
    if (res.code === 200) {
      alert('模型保存成功')
    } else {
      alert(res.message || '模型保存失败')
    }
  } catch (error) {
    console.error('保存模型失败:', error)
    alert('模型保存失败')
  } finally {
    savingModel.value = false
  }
}

async function handleSave() {
  if (!userStore.userId || !apiKeyInput.value.trim()) return
  saving.value = true
  try {
    const res = await apiSaveApiKey(userStore.userId, apiKeyInput.value.trim())
    if (res.code === 200) {
      alert('API Key 保存成功！')
      apiKeyInput.value = ''
      await loadStatus()
    } else {
      alert(res.message || '保存失败')
    }
  } catch (error: any) {
    alert(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!userStore.userId) return
  if (!confirm('确定要删除您的 API Key 吗？删除后将使用平台 API Key（有限制）。')) return
  
  deleting.value = true
  try {
    const res = await apiDeleteApiKey(userStore.userId)
    if (res.code === 200) {
      alert('API Key 已删除')
      await loadStatus()
    } else {
      alert(res.message || '删除失败')
    }
  } catch (error: any) {
    alert(error.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

function triggerFileSelect() {
  fileInput.value?.click()
}

async function handleFileSelect(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    alert('图片大小不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const uploadPathRes = await apiGetAvatarUploadPath()
    if (uploadPathRes.code !== 200 || !uploadPathRes.data.uploadUrl) {
      alert('获取上传路径失败')
      return
    }

    const { objectKey, uploadUrl } = uploadPathRes.data

    const uploadResponse = await fetch(uploadUrl, {
      method: 'PUT',
      body: file,
      headers: {
        'Content-Type': file.type
      }
    })

    if (!uploadResponse.ok) {
      alert('上传失败')
      return
    }

    const updateRes = await apiUpdateAvatar(objectKey)
    console.log('更新头像响应:', updateRes)
    if (updateRes.code === 200 && updateRes.data) {
      console.log('新头像URL:', updateRes.data)
      userStore.updateAvatar(updateRes.data)
      alert('头像上传成功！')
    } else {
      alert(updateRes.message || '更新头像失败')
    }
  } catch (error: any) {
    console.error('上传头像失败:', error)
    alert(error.message || '上传失败')
  } finally {
    uploading.value = false
    if (fileInput.value) {
      fileInput.value.value = ''
    }
  }
}

async function handleUpdateUsername() {
  if (!usernameInput.value.trim()) return
  
  const username = usernameInput.value.trim()
  if (username.length < 2 || username.length > 20) {
    alert('用户名长度应在2-20个字符之间')
    return
  }
  
  savingUsername.value = true
  try {
    const res = await apiUpdateUsername(username)
    if (res.code === 200) {
      userStore.updateUsername(username)
      alert('用户名修改成功！')
      usernameInput.value = ''
    } else {
      alert(res.message || '修改失败')
    }
  } catch (error: any) {
    alert(error.message || '修改失败')
  } finally {
    savingUsername.value = false
  }
}

async function handleUpdatePassword() {
  if (!oldPasswordInput.value || !newPasswordInput.value) return
  
  if (newPasswordInput.value.length < 6) {
    alert('新密码长度不能少于6个字符')
    return
  }
  
  savingPassword.value = true
  try {
    const res = await apiUpdatePassword(oldPasswordInput.value, newPasswordInput.value)
    if (res.code === 200) {
      alert('密码修改成功！')
      oldPasswordInput.value = ''
      newPasswordInput.value = ''
    } else {
      alert(res.message || '修改失败')
    }
  } catch (error: any) {
    alert(error.message || '修改失败')
  } finally {
    savingPassword.value = false
  }
}
</script>

<style scoped>
.settings-page {
  padding: 32px;
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.page-header p {
  color: var(--text-secondary);
  font-size: 14px;
}

.settings-card {
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  border-radius: 20px;
  overflow: hidden;
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  border-bottom: 1px solid var(--border-glass);
  background: rgba(255, 255, 255, 0.02);
}

.header-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--accent-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-icon svg {
  width: 24px;
  height: 24px;
  color: #fff;
}

.header-content h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.header-content p {
  font-size: 13px;
  color: var(--text-muted);
}

.card-body {
  padding: 24px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid var(--border-glass);
  background: var(--bg-glass);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(99, 102, 241, 0.1);
}

.avatar-placeholder svg {
  width: 48px;
  height: 48px;
  color: var(--accent);
}

.avatar-actions {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.avatar-hint {
  font-size: 12px;
  color: var(--text-muted);
}

.form-section {
  margin-bottom: 24px;
}

.form-section:last-child {
  margin-bottom: 0;
}

.form-input {
  flex: 1;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-primary);
  font-size: 14px;
  transition: all 0.3s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.form-input::placeholder {
  color: var(--text-muted);
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.btn-sm {
  padding: 12px 20px;
  font-size: 13px;
}

.status-info {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 24px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
}

.status-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-label {
  font-size: 12px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.status-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

.status-value.has-key {
  color: var(--success);
}

.status-value.limited {
  color: var(--warning);
}

.api-key-section {
  margin-bottom: 24px;
}

.model-section { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--border-glass); }

.input-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.input-group {
  display: flex;
  gap: 8px;
}

.api-key-input {
  flex: 1;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-primary);
  font-size: 14px;
  transition: all 0.3s ease;
}

.api-key-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.api-key-input::placeholder {
  color: var(--text-muted);
}

.toggle-visibility-btn {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.toggle-visibility-btn:hover {
  background: var(--bg-glass-hover);
  color: var(--text-primary);
}

.toggle-visibility-btn svg {
  width: 20px;
  height: 20px;
}

.input-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.input-hint a {
  color: var(--accent);
  text-decoration: none;
}

.input-hint a:hover {
  text-decoration: underline;
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--accent-gradient);
  color: #fff;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
}

.btn-danger {
  background: var(--error-light);
  color: var(--error);
  border: 1px solid var(--error);
}

.btn-danger:hover:not(:disabled) {
  background: var(--error);
  color: #fff;
}

.spinner {
  width: 16px;
  height: 16px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.info-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 16px;
}

.info-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info-icon svg {
  width: 20px;
  height: 20px;
  color: var(--accent);
}

.info-content h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.info-content ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.info-content li {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  padding-left: 16px;
  position: relative;
}

.info-content li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: var(--accent);
}

.info-content li strong {
  color: var(--text-primary);
}

@media (max-width: 640px) {
  .settings-page {
    padding: 20px;
  }
  
  .avatar-section {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .status-info {
    flex-direction: column;
    gap: 16px;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .btn {
    width: 100%;
  }
}
</style>
