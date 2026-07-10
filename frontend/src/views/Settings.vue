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
              :placeholder="isMaskedKey ? '' : '请输入您的阿里云百炼 API Key'"
              class="api-key-input"
              @focus="handleApiKeyFocus"
              @blur="handleApiKeyBlur"
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
          <button class="btn btn-primary" @click="handleSave" :disabled="saving || !apiKeyInput.trim() || (isMaskedKey && apiKeyInput === MASKED_KEY)">
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

        <!-- 模型管理 -->
        <div class="model-section" v-if="status?.hasOwnApiKey">
          <div class="model-header">
            <label class="input-label">模型管理</label>
            <span class="model-count">已添加 {{ modelCount }}/{{ maxModelCount }} 个模型</span>
          </div>
          
          <!-- 多模型说明 -->
          <div class="model-info-card">
            <div class="model-info-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
            </div>
            <div class="model-info-content">
              <h4>多模型协作说明</h4>
              <ul>
                <li><strong>主模型</strong>：用于所有单模型操作（如AI对话、知识库问答等），并负责整合辅助模型的回答</li>
                <li><strong>辅助模型</strong>：在AI解答功能中，辅助模型会并行回答问题，然后由主模型综合判断并输出最优答案</li>
                <li><strong>视觉模型</strong>：用于上传题目时的图片识别和PDF解析，支持文字和图片同时理解</li>
                <li><strong>设计原因</strong>：不同模型有各自的优势领域，多模型协作可以获得更全面、更准确的答案</li>
              </ul>
            </div>
          </div>
          
          <!-- 模型列表 -->
          <div class="model-list" v-if="status?.allModels && status.allModels.length > 0">
            <!-- 主模型 -->
            <div class="model-item main-model" v-if="mainModel">
              <div class="model-badge main">主模型</div>
              <div class="model-name">{{ mainModel.modelName }}</div>
              <div class="model-actions">
                <span class="model-status">用于所有单模型操作</span>
                <button class="btn btn-sm btn-outline" @click="handleEditModel(mainModel)" title="修改模型名称">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                  修改
                </button>
              </div>
            </div>
            
            <!-- 辅助模型 -->
            <div class="model-item assistant-model" v-for="model in assistantModels" :key="model.id">
              <div class="model-badge assistant">辅助模型</div>
              <div class="model-name">{{ model.modelName }}</div>
              <div class="model-actions">
                <button class="btn btn-sm btn-outline" @click="handleEditModel(model)" title="修改模型名称">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                  修改
                </button>
                <button class="btn btn-sm btn-outline" @click="handleSetMainModel(model.id)" title="设为主模型">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                  </svg>
                  设为主模型
                </button>
                <button class="btn btn-sm btn-danger-outline" @click="handleDeleteModel(model.id)" :disabled="deletingModelId === model.id" title="删除模型">
                  <svg v-if="deletingModelId === model.id" class="spinner" viewBox="0 0 24 24">
                    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                  删除
                </button>
              </div>
            </div>

            <!-- 视觉模型 -->
            <div class="model-item vision-model" v-if="visionModel">
              <div class="model-badge vision">视觉</div>
              <div class="model-name">{{ visionModel.modelName }}</div>
              <div class="model-actions">
                <span class="model-status">用于图片识别和PDF解析</span>
                <button class="btn btn-sm btn-outline" @click="handleEditModel(visionModel)" title="修改模型名称">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                  修改
                </button>
                <button class="btn btn-sm btn-danger-outline" @click="handleDeleteModel(visionModel.id)" :disabled="deletingModelId === visionModel.id" title="删除模型">
                  <svg v-if="deletingModelId === visionModel.id" class="spinner" viewBox="0 0 24 24">
                    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                  删除
                </button>
              </div>
            </div>
          </div>
          
          <!-- 添加模型 -->
          <div class="add-model-section" v-if="canAddMainModel || canAddAssistantModel || canAddVisionModel">
            <div class="add-model-form">
              <input 
                v-model="newModelName" 
                type="text" 
                class="form-input add-model-input" 
                placeholder="请输入模型名称，如 qwen-plus、qwen-turbo"
              />
              <div class="add-model-options">
                <label class="checkbox-label" :class="{ disabled: !canAddMainModel }">
                  <input type="checkbox" v-model="newModelIsMain" :disabled="!canAddMainModel" @change="newModelIsVision = false" />
                  <span>设为主模型</span>
                </label>
                <label class="checkbox-label" :class="{ disabled: !canAddVisionModel }">
                  <input type="checkbox" v-model="newModelIsVision" :disabled="!canAddVisionModel" @change="newModelIsMain = false" />
                  <span>视觉模型</span>
                </label>
                <button class="btn btn-primary btn-sm" @click="handleAddModel" :disabled="savingModel || !newModelName.trim()">
                  <svg v-if="savingModel" class="spinner" viewBox="0 0 24 24">
                    <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/>
                  </svg>
                  {{ savingModel ? '添加中...' : '添加模型' }}
                </button>
              </div>
            </div>
            <p class="input-hint">
              不勾选则默认为辅助模型。您可以在 <a href="https://bailian.console.aliyun.com/" target="_blank" rel="noopener">阿里云百炼控制台</a> 获取模型名称
            </p>
          </div>
          
          <!-- 多模型状态提示 -->
          <div class="multi-model-status" v-if="status?.allModels && status.allModels.length > 0">
            <div class="status-badge" :class="{ active: supportsMultiModel }">
              {{ supportsMultiModel ? '✓ 多模型协作已启用' : '○ 需要至少1个辅助模型才能启用多模型协作' }}
            </div>
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
          <li><strong>平台 API Key</strong>：使用项目提供的 API Key，每日有使用次数限制（对话10次、知识库问答10次、上传知识库5次、上传题目5次）。不提供Multi-agent功能。</li>
          <li><strong>个人 API Key</strong>：使用您自己的 API Key，无使用次数限制，费用由您自己承担。此方式可以使用<strong>Multi-agent</strong>从而提高AI回答的准确度。<span class="recommend-badge">【推荐使用此方式】</span></li>
          <li>API Key 会加密存储，确保安全性</li>
          <li>删除 API Key 后将自动切换回平台 API Key</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiGetApiKeyStatus, apiSaveApiKey, apiDeleteApiKey, apiAddModel, apiDeleteModel, apiSetMainModel, apiGetAvatarUploadPath, apiUpdateAvatar, apiUpdateUsername, apiUpdatePassword } from '@/api'
import type { ApiKeyStatus, ModelsEntity } from '@/types'

const userStore = useUserStore()
const status = ref<ApiKeyStatus | null>(null)
const apiKeyInput = ref('')
const showApiKey = ref(false)
const saving = ref(false)
const deleting = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const newModelName = ref('')
const newModelIsMain = ref(false)
const newModelIsVision = ref(false)
const savingModel = ref(false)
const deletingModelId = ref<number | null>(null)

const MASKED_KEY = '***************'
const isMaskedKey = ref(false)

const usernameInput = ref('')
const savingUsername = ref(false)
const oldPasswordInput = ref('')
const newPasswordInput = ref('')
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const savingPassword = ref(false)

const mainModel = computed(() => status.value?.allModels?.find(m => m.modelType === 0))
const assistantModels = computed(() => status.value?.allModels?.filter(m => m.modelType === 1) || [])
const visionModel = computed(() => status.value?.allModels?.find(m => m.modelType === 2) || null)
const modelCount = computed(() => status.value?.modelCount ?? 0)
const maxModelCount = computed(() => status.value?.maxModelCount ?? 4)
const supportsMultiModel = computed(() => status.value?.supportsMultiModel ?? false)

// 按类型判断是否还能添加
const canAddMainModel = computed(() => !mainModel.value)
const canAddAssistantModel = computed(() => assistantModels.value.length < 2)
const canAddVisionModel = computed(() => !visionModel.value)

onMounted(async () => {
  await loadStatus()
})

async function loadStatus() {
  if (!userStore.userId) return
  try {
    const res = await apiGetApiKeyStatus()
    if (res.code === 200) {
      status.value = res.data
      if (res.data?.hasOwnApiKey) {
        apiKeyInput.value = MASKED_KEY
        isMaskedKey.value = true
      } else {
        apiKeyInput.value = ''
        isMaskedKey.value = false
      }
    }
  } catch (error) {
    console.error('获取 API Key 状态失败:', error)
  }
}

function handleApiKeyFocus() {
  if (isMaskedKey.value && apiKeyInput.value === MASKED_KEY) {
    apiKeyInput.value = ''
    isMaskedKey.value = false
  }
}

function handleApiKeyBlur() {
  if (!apiKeyInput.value.trim() && status.value?.hasOwnApiKey) {
    apiKeyInput.value = MASKED_KEY
    isMaskedKey.value = true
  }
}

async function handleAddModel() {
  if (!newModelName.value.trim() || !userStore.userId) return
  
  // 从 checkbox 计算 modelType：主模型=0，视觉=2，辅助模型=1
  const modelType = newModelIsMain.value ? 0 : newModelIsVision.value ? 2 : 1

  // 前端按类型校验
  if (modelType === 0 && !canAddMainModel.value) {
    alert('主模型已存在，最多只能设置1个主模型')
    return
  }
  if (modelType === 1 && !canAddAssistantModel.value) {
    alert('辅助模型已达到最大数量限制（2个）')
    return
  }
  if (modelType === 2 && !canAddVisionModel.value) {
    alert('视觉模型已存在，最多只能设置1个视觉模型')
    return
  }

  savingModel.value = true
  try {
    const res = await apiAddModel(newModelName.value.trim(), modelType)
    if (res.code === 200) {
      alert('模型添加成功')
      newModelName.value = ''
      newModelIsMain.value = false
      newModelIsVision.value = false
      await loadStatus()
    } else {
      alert(res.message || '模型添加失败')
    }
  } catch (error) {
    console.error('添加模型失败:', error)
    alert('模型添加失败')
  } finally {
    savingModel.value = false
  }
}

async function handleDeleteModel(modelId: number) {
  if (!userStore.userId) return
  if (!confirm('确定要删除这个模型吗？')) return
  
  deletingModelId.value = modelId
  try {
    const res = await apiDeleteModel(modelId)
    if (res.code === 200) {
      alert('模型删除成功')
      await loadStatus()
    } else {
      alert(res.message || '模型删除失败')
    }
  } catch (error) {
    console.error('删除模型失败:', error)
    alert('模型删除失败')
  } finally {
    deletingModelId.value = null
  }
}

async function handleSetMainModel(modelId: number) {
  if (!userStore.userId) return
  if (!confirm('确定要将此模型设为主模型吗？')) return
  
  try {
    const res = await apiSetMainModel(modelId)
    if (res.code === 200) {
      alert('主模型设置成功')
      await loadStatus()
    } else {
      alert(res.message || '设置主模型失败')
    }
  } catch (error) {
    console.error('设置主模型失败:', error)
    alert('设置主模型失败')
  }
}

function handleEditModel(model: ModelsEntity) {
  const newName = prompt('请输入新的模型名称：', model.modelName)
  if (!newName || !newName.trim() || newName.trim() === model.modelName) return
  // 先删除旧模型，再添加新模型（保持类型不变）
  editingModelId.value = model.id
  editingModelType.value = model.modelType
  editingModelNewName.value = newName.trim()
  doEditModel()
}

const editingModelId = ref<number | null>(null)
const editingModelType = ref<number>(0)
const editingModelNewName = ref('')

async function doEditModel() {
  if (!userStore.userId || editingModelId.value === null) return
  try {
    // 删除旧模型
    await apiDeleteModel(editingModelId.value)
    // 添加新模型（保持原类型）
    const res = await apiAddModel(editingModelNewName.value, editingModelType.value)
    if (res.code === 200) {
      alert('模型修改成功')
      await loadStatus()
    } else {
      alert(res.message || '模型修改失败')
      await loadStatus()
    }
  } catch (error) {
    console.error('模型修改失败:', error)
    alert('模型修改失败')
    await loadStatus()
  } finally {
    editingModelId.value = null
  }
}

async function handleSave() {
  if (!userStore.userId || !apiKeyInput.value.trim()) return
  if (isMaskedKey.value && apiKeyInput.value === MASKED_KEY) {
    alert('请输入新的 API Key')
    return
  }
  saving.value = true
  try {
    const res = await apiSaveApiKey(apiKeyInput.value.trim())
    if (res.code === 200) {
      alert('API Key 保存成功！')
      apiKeyInput.value = MASKED_KEY
      isMaskedKey.value = true
      await loadStatus()
    } else {
      alert(res.message || '保存失败')
    }
  } catch (error: unknown) {
    alert((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!userStore.userId) return
  if (!confirm('确定要删除您的 API Key 吗？删除后将使用平台 API Key（有限制）。')) return
  
  deleting.value = true
  try {
    const res = await apiDeleteApiKey()
    if (res.code === 200) {
      alert('API Key 已删除')
      apiKeyInput.value = ''
      isMaskedKey.value = false
      await loadStatus()
    } else {
      alert(res.message || '删除失败')
    }
  } catch (error: unknown) {
    alert((error as Error).message || '删除失败')
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
    if (updateRes.code === 200 && updateRes.data) {
      userStore.updateAvatar(updateRes.data)
      alert('头像上传成功！')
    } else {
      alert(updateRes.message || '更新头像失败')
    }
  } catch (error: unknown) {
    console.error('上传头像失败:', error)
    alert((error as Error).message || '上传失败')
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
  } catch (error: unknown) {
    alert((error as Error).message || '修改失败')
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
  } catch (error: unknown) {
    alert((error as Error).message || '修改失败')
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

.model-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.model-count {
  font-size: 13px;
  color: var(--text-muted);
  background: rgba(99, 102, 241, 0.1);
  padding: 4px 12px;
  border-radius: 20px;
}

.model-info-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 12px;
  margin-bottom: 20px;
}

.model-info-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.model-info-icon svg {
  width: 18px;
  height: 18px;
  color: var(--accent);
}

.model-info-content h4 {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.model-info-content ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.model-info-content li {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
  padding-left: 12px;
  position: relative;
}

.model-info-content li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: var(--accent);
}

.model-info-content li strong {
  color: var(--text-primary);
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.model-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.model-item.main-model {
  background: rgba(99, 102, 241, 0.08);
  border-color: rgba(99, 102, 241, 0.3);
}

.model-item.assistant-model:hover {
  border-color: var(--accent);
}

.model-item.vision-model {
  background: rgba(245, 158, 11, 0.05);
  border-color: rgba(245, 158, 11, 0.2);
}

.model-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.model-badge.main {
  background: var(--accent-gradient);
  color: #fff;
}

.model-badge.assistant {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.model-badge.vision {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.model-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.model-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-status {
  font-size: 12px;
  color: var(--text-muted);
}

.btn-outline {
  background: transparent;
  color: var(--accent);
  border: 1px solid var(--accent);
}

.btn-outline:hover:not(:disabled) {
  background: var(--accent);
  color: #fff;
}

.btn-danger-outline {
  background: transparent;
  color: var(--error);
  border: 1px solid var(--error);
}

.btn-danger-outline:hover:not(:disabled) {
  background: var(--error);
  color: #fff;
}

.add-model-section {
  padding: 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed var(--border-glass);
  border-radius: 12px;
}

.add-model-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.add-model-input {
  width: 100%;
}

.add-model-options {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

.checkbox-label.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.multi-model-status {
  margin-top: 16px;
}

.status-badge {
  font-size: 13px;
  padding: 10px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
  color: var(--text-muted);
  border: 1px solid var(--border-glass);
}

.status-badge.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.3);
}

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

.recommend-badge {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.8;
  }
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
