<template>
  <div class="question-list">
    <div class="search-bar">
      <input 
        v-model="searchKeyword" 
        @input="handleSearch"
        placeholder="搜索题目..."
        class="search-input"
      />
      <select v-model="filterType" class="filter-select">
        <option value="all">全部</option>
        <option value="single_choice">单选题</option>
        <option value="multiple_choice">多选题</option>
        <option value="true_false">判断题</option>
      </select>
    </div>
    <div class="questions-container">
      <QuestionItem 
        v-for="(question, index) in filteredQuestions" 
        :key="index"
        :question="question"
        :index="index"
        @expand="handleExpand"
      />
    </div>
    <div class="pagination" v-if="totalPages > 1">
      <button 
        @click="prevPage" 
        :disabled="currentPage === 1"
        class="pagination-btn"
      >
        上一页
      </button>
      <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
      <button 
        @click="nextPage" 
        :disabled="currentPage === totalPages"
        class="pagination-btn"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'QuestionList',
  components: {
    QuestionItem: () => import('./QuestionItem.vue')
  },
  props: {
    questions: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      searchKeyword: '',
      filterType: 'all',
      currentPage: 1,
      pageSize: 20
    }
  },
  computed: {
    filteredQuestions() {
      let filtered = this.questions;
      
      if (this.filterType !== 'all') {
        filtered = filtered.filter(q => q.questionType === this.filterType);
      }
      
      if (this.searchKeyword) {
        const keyword = this.searchKeyword.toLowerCase();
        filtered = filtered.filter(q => 
          q.question.toLowerCase().includes(keyword) ||
          (q.options && q.options.some(o => o.toLowerCase().includes(keyword))) ||
          q.answer.toLowerCase().includes(keyword)
        );
      }
      
      return filtered;
    },
    totalPages() {
      return Math.ceil(this.filteredQuestions.length / this.pageSize);
    },
    paginatedQuestions() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      return this.filteredQuestions.slice(start, end);
    }
  },
  methods: {
    handleSearch() {
      this.currentPage = 1;
    },
    handleExpand(index) {
      this.$emit('expand', index);
    },
    prevPage() {
      if (this.currentPage > 1) {
        this.currentPage--;
      }
    },
    nextPage() {
      if (this.currentPage < this.totalPages) {
        this.currentPage++;
      }
    }
  }
}
</script>

<style scoped>
.question-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  padding: 15px;
  background: #1a1a1a;
  border-radius: 8px;
}

.search-input {
  flex: 1;
  padding: 10px 15px;
  border: 1px solid #333;
  border-radius: 4px;
  background: #2a2a2a;
  color: #fff;
  font-size: 14px;
}

.filter-select {
  padding: 10px 15px;
  border: 1px solid #333;
  border-radius: 4px;
  background: #2a2a2a;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.questions-container {
  min-height: 400px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 20px;
  padding: 15px;
  background: #1a1a1a;
  border-radius: 8px;
}

.pagination-btn {
  padding: 8px 16px;
  background: #00ff88;
  color: #000;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.pagination-btn:disabled {
  background: #666;
  cursor: not-allowed;
}

.page-info {
  color: #fff;
  font-size: 14px;
}
</style>
