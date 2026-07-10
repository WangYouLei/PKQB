<template>
  <div class="avatar-container" :style="{ width: size + 'px', height: size + 'px' }">
    <img
      v-if="userStore.avatarUrl"
      :src="userStore.avatarUrl"
      :alt="alt"
      class="avatar-image"
    />
    <div v-else class="avatar-placeholder">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
        <circle cx="12" cy="7" r="4"/>
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'

interface Props {
  size?: number
  alt?: string
}

withDefaults(defineProps<Props>(), {
  size: 40,
  alt: '用户头像'
})

const userStore = useUserStore()
</script>

<style scoped>
.avatar-container {
  border-radius: 12px;
  overflow: hidden;
  background: var(--accent-gradient);
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
  width: 60%;
  height: 60%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-placeholder svg {
  width: 100%;
  height: 100%;
  color: #fff;
}
</style>
