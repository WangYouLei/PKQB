<template>
  <div v-if="needsOptions(question.questionType)" class="edit-field">
    <label>选项：</label>
    <div v-for="(_, oi) in getOptionsForEdit(question)" :key="oi" class="option-edit-row">
      <span class="option-label">{{ String.fromCharCode(65 + oi) }}.</span>
      <input 
        :value="getOptionsForEdit(question)[oi]" 
        @input="updateOption(oi, ($event.target as HTMLInputElement).value)"
        class="edit-input" 
        :disabled="question.questionType === 'true_false'" 
      />
      <button 
        v-if="question.questionType !== 'true_false' && getOptionsForEdit(question).length > 2" 
        class="option-action-btn delete" 
        @click="$emit('remove-option', oi)"
        title="删除选项"
      >×</button>
    </div>
    <button 
      v-if="question.questionType !== 'true_false'" 
      class="add-option-btn" 
      @click="$emit('add-option')"
    >+ 添加选项</button>
  </div>
</template>

<script setup lang="ts">
import type { RubricQuestion } from '@/types'

const props = defineProps<{
  question: RubricQuestion
}>()

defineEmits<{
  'add-option': []
  'remove-option': [index: number]
  'update-option': [index: number, value: string]
}>()

function needsOptions(questionType: string): boolean {
  return questionType === 'single_choice' || questionType === 'multiple_choice' || questionType === 'true_false'
}

function getOptionsForEdit(q: RubricQuestion): string[] {
  if (q.questionType === 'true_false') {
    return ['正确', '错误']
  }
  if (!q.optionsJson) {
    return ['', '', '', '']
  }
  try {
    const opts = JSON.parse(q.optionsJson)
    return Array.isArray(opts) && opts.length > 0 ? opts : ['', '', '', '']
  } catch {
    return ['', '', '', '']
  }
}

function updateOption(index: number, value: string) {
  const options = getOptionsForEdit(props.question)
  options[index] = value
  props.question.optionsJson = JSON.stringify(options)
}
</script>

<style scoped>
.option-edit-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.option-edit-row .option-label { font-weight: 600; color: var(--text-secondary); min-width: 24px; }
.option-edit-row input:disabled { background: var(--bg-glass); color: var(--text-muted); cursor: not-allowed; }
.option-action-btn { width: 28px; height: 28px; border: none; border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 16px; transition: all 0.2s; }
.option-action-btn.delete { background: rgba(239,68,68,0.1); color: #ef4444; }
.option-action-btn.delete:hover { background: rgba(239,68,68,0.2); }
.add-option-btn { margin-top: 8px; padding: 8px 16px; border: 1px dashed var(--border-glass); border-radius: 8px; background: transparent; color: var(--accent); font-size: 13px; cursor: pointer; transition: all 0.2s; }
.add-option-btn:hover { background: var(--accent-light); border-color: var(--accent); }
</style>
