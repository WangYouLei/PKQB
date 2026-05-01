<template>
  <div class="card file-card">
    <div class="card-header">
      <div class="card-header-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" y1="13" x2="8" y2="13"/>
          <line x1="16" y1="17" x2="8" y2="17"/>
        </svg>
      </div>
      <div class="card-header-text">
        <h3>{{ file.fileName }}</h3>
        <p>创建者: {{ file.creatorName }}</p>
      </div>
    </div>
    <div class="card-meta">
      <div class="meta-item">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="12 6 12 12 16 14"/>
        </svg>
        <span>{{ formatTime(file.createTime) }}</span>
      </div>
    </div>
    <div class="card-footer">
      <span class="tag" :class="file.isPrivate ? 'tag-muted' : 'tag-success'">
        <svg v-if="file.isPrivate" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
          <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="2" y1="12" x2="22" y2="12"/>
          <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
        </svg>
        {{ file.isPrivate ? '私有' : '公开' }}
      </span>
      <div class="card-actions">
        <button class="action-btn preview" @click.stop="$emit('preview', file)" title="预览">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
        </button>
        <button class="action-btn download" @click.stop="$emit('download', file)" title="下载">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
        </button>
        <button v-if="showEdit" class="action-btn edit" @click.stop="openEditModal" title="修改">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </button>
        <button v-if="showDelete" class="action-btn delete" @click.stop="$emit('remove', file)" title="删除">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            <line x1="10" y1="11" x2="10" y2="17"/>
            <line x1="14" y1="11" x2="14" y2="17"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- 修改弹窗 -->
    <Teleport to="body">
      <div v-if="showEditModal" class="modal-overlay" @click.self="showEditModal = false">
        <div class="edit-modal">
          <h3>修改文件信息</h3>
          <div class="form-group">
            <label class="form-label">文件名称</label>
            <input v-model="editForm.fileName" class="form-input" placeholder="请输入文件名称" />
          </div>
          <div class="form-group" style="display:flex;align-items:center;justify-content:space-between">
            <label class="form-label" style="margin-bottom:0">是否公开（班级可见）</label>
            <label class="toggle">
              <input :checked="!editForm.isPrivate" @change="editForm.isPrivate = !($event?.target as HTMLInputElement)?.checked" type="checkbox" />
              <span class="toggle-slider"></span>
            </label>
          </div>
          <div class="modal-actions">
            <button class="btn btn-secondary" @click="showEditModal = false">取消</button>
            <button class="btn btn-primary" @click="submitEdit" :disabled="editLoading">
              {{ editLoading ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { HtmlFileItem } from '@/types'
import { apiUpdateFile } from '@/api'

const props = defineProps<{ 
  file: HtmlFileItem,
  showEdit?: boolean,
  showDelete?: boolean
}>()
const emit = defineEmits<{ 
  open: [file: HtmlFileItem],
  preview: [file: HtmlFileItem],
  download: [file: HtmlFileItem],
  remove: [file: HtmlFileItem],
  updated: [file: HtmlFileItem]
}>()

const showEditModal = ref(false)
const editLoading = ref(false)
const editForm = reactive({
  fileName: '',
  isPrivate: false
})

function formatTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${m}-${day} ${h}:${min}`
}

function openEditModal() {
  editForm.fileName = props.file.fileName
  editForm.isPrivate = props.file.isPrivate || false
  showEditModal.value = true
}

async function submitEdit() {
  if (!editForm.fileName.trim()) {
    alert('请输入文件名称')
    return
  }
  
  editLoading.value = true
  try {
    const res = await apiUpdateFile(props.file.id, editForm.fileName, editForm.isPrivate)
    if (res.code === 200) {
      showEditModal.value = false
      emit('updated', res.data)
    }
  } catch (e) {
    console.error('修改文件失败', e)
    alert('修改文件失败')
  } finally {
    editLoading.value = false
  }
}
</script>

<style scoped>
.file-card { 
  cursor: pointer; 
  position: relative;
  overflow: hidden;
}

.file-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--accent-gradient);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.file-card:hover::before {
  opacity: 1;
}

.card-header-icon svg {
  width: 22px;
  height: 22px;
  color: #fff;
}

.card-header-text h3 {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.card-meta {
  padding: 0 24px 16px;
  display: flex;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.meta-item svg {
  width: 14px;
  height: 14px;
}

.card-footer {
  padding: 16px 24px 24px;
  border-top: 1px solid var(--border-glass);
  margin-top: auto;
}

.card-footer .tag {
  gap: 6px;
}

.card-footer .tag svg {
  width: 12px;
  height: 12px;
}

.card-actions { 
  display: flex; 
  gap: 8px; 
}

.action-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: var(--bg-glass);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.action-btn:hover {
  transform: scale(1.1);
}

.action-btn.preview:hover {
  background: var(--accent-light);
  color: var(--accent);
}

.action-btn.download:hover {
  background: var(--success-light);
  color: var(--success);
}

.action-btn.delete:hover {
  background: var(--error-light);
  color: var(--error);
}

.action-btn.edit:hover {
  background: rgba(245,158,11,0.1);
  color: #f59e0b;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.edit-modal {
  background: var(--card-bg);
  border: 1px solid var(--border-glass);
  border-radius: 16px;
  padding: 24px;
  min-width: 360px;
  max-width: 90vw;
}

.edit-modal h3 {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}

.form-group {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
  color: var(--text-secondary);
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-glass);
  border-radius: 10px;
  background: var(--bg-glass);
  color: var(--text-primary);
  font-size: 14px;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: var(--tertiary);
  box-shadow: 0 0 0 3px rgba(6,182,212,0.1);
}

.toggle { position: relative; display: inline-block; width: 48px; height: 26px; }
.toggle input { opacity: 0; width: 0; height: 0; }
.toggle-slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: var(--bg-glass); border: 1px solid var(--border-glass); transition: .3s; border-radius: 26px; }
.toggle-slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: var(--text-muted); transition: .3s; border-radius: 50%; }
.toggle input:checked + .toggle-slider { background: var(--tertiary-gradient); border-color: transparent; }
.toggle input:checked + .toggle-slider:before { transform: translateX(22px); background-color: #fff; }

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 24px;
}
</style>
