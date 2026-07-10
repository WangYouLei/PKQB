<template>
  <div class="edit-field">
    <label>答案：</label>
    <select v-if="question.questionType === 'true_false'" :value="question.answer" @change="$emit('update:answer', ($event.target as HTMLSelectElement).value)" class="edit-input">
      <option value="">请选择答案</option>
      <option value="正确">正确</option>
      <option value="错误">错误</option>
    </select>
    <input v-else :value="question.answer" @input="$emit('update:answer', ($event.target as HTMLInputElement).value)" class="edit-input" />
  </div>
</template>

<script setup lang="ts">
import type { RubricQuestion } from '@/types'

defineProps<{
  question: RubricQuestion
}>()

defineEmits<{
  'update:answer': [value: string]
}>()
</script>
