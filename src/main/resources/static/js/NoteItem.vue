<template>
  <div class="note-item" :class="{ expanded: isExpanded }">
    <div class="note-header" @click="toggleExpand">
      <h3 class="note-title">{{ note.title }}</h3>
      <span class="expand-icon">{{ isExpanded ? '▼' : '▶' }}</span>
    </div>
    <div class="note-body" v-show="isExpanded">
      <div class="note-content">{{ note.content }}</div>
      <div class="actions">
        <button @click="editNote" class="action-btn edit-btn">
          ✎ 编辑
        </button>
        <button @click="deleteNote" class="action-btn delete-btn">
          ✗ 删除
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NoteItem',
  props: {
    note: {
      type: Object,
      required: true
    },
    index: {
      type: Number,
      required: true
    }
  },
  data() {
    return {
      isExpanded: false
    }
  },
  methods: {
    toggleExpand() {
      this.isExpanded = !this.isExpanded;
      this.$emit('expand', this.index);
    },
    editNote() {
      this.$emit('edit', { index: this.index, note: this.note });
    },
    deleteNote() {
      this.$emit('delete', this.index);
    }
  }
}
</script>

<style scoped>
.note-item {
  background: #2a2a2a;
  border-radius: 8px;
  margin-bottom: 15px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.note-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  cursor: pointer;
  background: #1a1a1a;
  border-radius: 8px 8px 0 0;
}

.note-title {
  flex: 1;
  margin: 0;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.expand-icon {
  color: #00ff88;
  font-size: 18px;
  transition: transform 0.3s ease;
}

.note-item.expanded .expand-icon {
  transform: rotate(180deg);
}

.note-body {
  padding: 0 20px 20px;
  animation: slideDown 0.3s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    max-height: 0;
  }
  to {
    opacity: 1;
    max-height: 1000px;
  }
}

.note-content {
  color: #ddd;
  line-height: 1.6;
  margin-bottom: 15px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 15px;
  padding: 10px;
  background: #1a1a1a;
  border-radius: 4px;
}

.action-btn {
  flex: 1;
  padding: 8px 16px;
  background: #00ff88;
  color: #000;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
}

.edit-btn {
  background: #00cc6a;
}

.edit-btn:hover {
  background: #00994a;
}

.delete-btn {
  background: #ff4444;
}

.delete-btn:hover {
  background: #cc0000;
}
</style>
