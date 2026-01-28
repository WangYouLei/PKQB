<template>
  <div class="note-list">
    <div class="search-bar">
      <input 
        v-model="searchKeyword" 
        @input="handleSearch"
        placeholder="搜索笔记..."
        class="search-input"
      />
    </div>
    <div class="notes-container">
      <NoteItem 
        v-for="(note, index) in filteredNotes" 
        :key="index"
        :note="note"
        :index="index"
        @expand="handleExpand"
        @edit="handleEdit"
        @delete="handleDelete"
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
  name: 'NoteList',
  components: {
    NoteItem: () => import('./NoteItem.vue')
  },
  props: {
    notes: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      searchKeyword: '',
      currentPage: 1,
      pageSize: 20
    }
  },
  computed: {
    filteredNotes() {
      if (!this.searchKeyword) {
        return this.notes;
      }
      
      const keyword = this.searchKeyword.toLowerCase();
      return this.notes.filter(note => 
        note.title.toLowerCase().includes(keyword) ||
        note.content.toLowerCase().includes(keyword)
      );
    },
    totalPages() {
      return Math.ceil(this.filteredNotes.length / this.pageSize);
    },
    paginatedNotes() {
      const start = (this.currentPage - 1) * this.pageSize;
      const end = start + this.pageSize;
      return this.filteredNotes.slice(start, end);
    }
  },
  methods: {
    handleSearch() {
      this.currentPage = 1;
    },
    handleExpand(index) {
      this.$emit('expand', index);
    },
    handleEdit(index) {
      this.$emit('edit', index);
    },
    handleDelete(index) {
      this.$emit('delete', index);
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
.note-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.search-bar {
  margin-bottom: 20px;
  padding: 15px;
  background: #1a1a1a;
  border-radius: 8px;
}

.search-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #333;
  border-radius: 4px;
  background: #2a2a2a;
  color: #fff;
  font-size: 14px;
  box-sizing: border-box;
}

.notes-container {
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
