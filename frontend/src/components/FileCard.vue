<template>
  <div class="card file-card" @click="$emit('open', file)">
    <div class="card-header">
      <div class="card-header-icon">&#128196;</div>
      <div class="card-header-text">
        <h3>{{ file.fileName }}</h3>
        <p>{{ file.creatorName }}</p>
      </div>
    </div>
    <div class="card-footer">
      <span class="tag" :class="file.isPublic ? 'tag-success' : 'tag-muted'">
        {{ file.isPublic ? '公开' : '私有' }}
      </span>
      <span class="tag tag-muted">{{ formatTime(file.createTime) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { HtmlFileItem } from '@/types'
defineProps<{ file: HtmlFileItem }>()
defineEmits<{ open: [file: HtmlFileItem] }>()
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
.file-card { cursor: pointer; }
</style>
