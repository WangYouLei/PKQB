<template>
  <div class="question-item" :class="{ expanded: isExpanded }">
    <div class="question-header" @click="toggleExpand">
      <h3 class="question-title">{{ index + 1 }}. {{ question.question }}</h3>
      <span class="type-badge">{{ getQuestionTypeText(question.questionType) }}</span>
      <span class="expand-icon">{{ isExpanded ? '▼' : '▶' }}</span>
    </div>
    <div class="question-body" v-show="isExpanded">
      <div class="options" v-if="question.options">
        <div 
          v-for="(option, idx) in question.options" 
          :key="idx"
          class="option"
        >
          <span class="option-label">{{ String.fromCharCode(65 + idx) }}.</span> {{ option }}
        </div>
      </div>
      <div class="answer">
        <strong>答案：</strong>
        <span class="answer-text">{{ question.answer }}</span>
      </div>
      <div class="explanation" v-if="question.explanation">
        <strong>解析：</strong>
        <p class="explanation-text">{{ question.explanation }}</p>
      </div>
      <div class="actions">
        <button @click="markFavorite" class="action-btn favorite-btn">
          {{ isFavorite ? '★' : '☆' }} 收藏
        </button>
        <button @click="markWrong" class="action-btn wrong-btn">
          ✗ 标记错题
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'QuestionItem',
  props: {
    question: {
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
      isExpanded: false,
      isFavorite: false
    }
  },
  methods: {
    toggleExpand() {
      this.isExpanded = !this.isExpanded;
      this.$emit('expand', this.index);
    },
    getQuestionTypeText(type) {
      const typeMap = {
        'single_choice': '单选题',
        'multiple_choice': '多选题',
        'true_false': '判断题'
      };
      return typeMap[type] || type;
    },
    markFavorite() {
      this.isFavorite = !this.isFavorite;
      this.$emit('favorite', { index: this.index, isFavorite: this.isFavorite });
    },
    markWrong() {
      this.$emit('wrong', this.index);
    }
  }
}
</script>

<style scoped>
.question-item {
  background: #2a2a2a;
  border-radius: 8px;
  margin-bottom: 15px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  cursor: pointer;
  background: #1a1a1a;
  border-radius: 8px 8px 0 0;
}

.question-title {
  flex: 1;
  margin: 0;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.type-badge {
  padding: 4px 8px;
  background: #00ff88;
  color: #000;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.expand-icon {
  color: #00ff88;
  font-size: 18px;
  transition: transform 0.3s ease;
}

.question-item.expanded .expand-icon {
  transform: rotate(180deg);
}

.question-body {
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

.options {
  margin: 15px 0;
  padding: 10px;
  background: #333;
  border-radius: 4px;
}

.option {
  padding: 8px 12px;
  margin-bottom: 8px;
  color: #ddd;
  border-bottom: 1px solid #444;
}

.option:last-child {
  border-bottom: none;
}

.option-label {
  font-weight: 600;
  color: #00ff88;
  margin-right: 8px;
}

.answer {
  margin: 15px 0;
  padding: 12px;
  background: #1a1a1a;
  border-left: 4px solid #00ff88;
  border-radius: 4px;
}

.answer-text {
  color: #00ff88;
  font-weight: 600;
}

.explanation {
  margin: 15px 0;
  padding: 12px;
  background: #0a0a0a;
  border-left: 4px solid #00ff88;
  border-radius: 4px;
}

.explanation-text {
  margin: 5px 0 0 0;
  color: #aaa;
  line-height: 1.6;
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
  background: #00cc6a;
  transform: translateY(-2px);
}

.favorite-btn {
  background: #ffd700;
}

.favorite-btn:hover {
  background: #ffaa00;
}

.wrong-btn {
  background: #ff4444;
}

.wrong-btn:hover {
  background: #cc0000;
}
</style>
