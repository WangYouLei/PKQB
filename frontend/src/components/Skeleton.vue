<template>
  <div class="skeleton" :style="{ width, height }"></div>
</template>

<script setup lang="ts">
defineProps<{
  width?: string
  height?: string
}>()
</script>

<style scoped>
.skeleton {
  background: linear-gradient(90deg, var(--bg-glass) 25%, var(--border-glass) 50%, var(--bg-glass) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 8px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
