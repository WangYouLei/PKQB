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
        <button class="action-btn delete" @click.stop="$emit('remove', file)" title="删除">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            <line x1="10" y1="11" x2="10" y2="17"/>
            <line x1="14" y1="11" x2="14" y2="17"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { HtmlFileItem } from '@/types'
defineProps<{ file: HtmlFileItem }>()
defineEmits<{ 
  open: [file: HtmlFileItem],
  preview: [file: HtmlFileItem],
  download: [file: HtmlFileItem],
  remove: [file: HtmlFileItem]
 }>()
function formatTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${m}-${day} ${h}:${min}`
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
</style>
