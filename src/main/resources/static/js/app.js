const { createApp } = Vue;

const app = createApp({
  data() {
    return {
      currentView: 'input',
      questions: [],
      notes: [],
      searchKeyword: '',
      filterType: 'all',
      loading: false,
      currentPage: 1,
      pageSize: 20,
      templateName: '',
      templateContent: '',
      fileId: null,
      generateStatus: 'idle',
      downloadPath: ''
    }
  },
  methods: {
    async analyzeContent() {
      const text = document.getElementById('textInput').value.trim();
      
      if (!text) {
        alert('请输入内容');
        return;
      }
      
      if (text.length < 10) {
        alert('内容太少，请提供更多信息');
        return;
      }

      this.loading = true;
      this.generateStatus = 'generating';

      try {
        const response = await axios.post('/api/analyze', {
          text: text
        });
        
        const result = response.data;
        
        if (result.success) {
          this.templateName = result.templateName;
          this.questions = result.items;
          this.notes = result.items;
          this.fileId = result.data?.fileId || null;
          
          if (result.type === 'question') {
            this.currentView = 'questions';
          } else {
            this.currentView = 'notes';
          }
          
          this.loadTemplate();
          
          this.startStatusPolling();
        } else {
          alert(result.message);
        }
      } catch (error) {
        console.error('分析失败', error);
        alert('分析失败：' + error.message);
      } finally {
        this.loading = false;
      }
    },
    
    async loadTemplate() {
      try {
        const response = await axios.get(`/api/templates/${this.templateName}`);
        this.templateContent = response.data;
        this.renderContent();
      } catch (error) {
        console.error('加载模板失败', error);
        alert('加载模板失败：' + error.message);
      }
    },
    
    renderContent() {
      const container = document.getElementById('contentContainer');
      if (!container) {
        return;
      }
      
      let html = this.templateContent;
      
      if (this.currentView === 'questions') {
        const questionsHtml = this.questions.map((q, index) => {
          return `
            <div class="question-item">
              <div class="question-header">
                <h3>${index + 1}. ${q.question}</h3>
                <span class="question-type">${this.getQuestionTypeText(q.questionType)}</span>
              </div>
              ${q.options ? `
                <div class="options">
                  ${q.options.map((opt, idx) => `
                    <div class="option">${String.fromCharCode(65 + idx)}. ${opt}</div>
                  `).join('')}
                </div>
              ` : ''}
              <div class="answer">
                <strong>答案：</strong>${q.answer}
              </div>
              ${q.explanation ? `
                <div class="explanation">
                  <strong>解析：</strong>${q.explanation}
                </div>
              ` : ''}
            </div>
          `;
        }).join('');
        
        html = html.replace('{{#each items}}', questionsHtml);
        html = html.replace('{{/each}}', '');
        html = html.replace('{{@index}}', '');
        html = html.replace('{{question}}', '');
        html = html.replace('{{questionType}}', '');
        html = html.replace('{{#if options}}', '');
        html = html.replace('{{/if}}', '');
        html = html.replace('{{#each options}}', '');
        html = html.replace('{{this}}', '');
        html = html.replace('{{/each}}', '');
        html = html.replace('{{#if explanation}}', '');
        html = html.replace('{{explanation}}', '');
        html = html.replace('{{answer}}', '');
        
      } else if (this.currentView === 'notes') {
        const notesHtml = this.notes.map((note, index) => {
          return `
            <div class="note-item">
              <div class="note-header">
                <h3>${note.title}</h3>
              </div>
              <div class="note-content">${note.content}</div>
            </div>
          `;
        }).join('');
        
        html = html.replace('{{#each items}}', notesHtml);
        html = html.replace('{{/each}}', '');
        html = html.replace('{{@index}}', '');
        html = html.replace('{{title}}', '');
        html = html.replace('{{content}}', '');
      }
      
      container.innerHTML = html;
    },
    
    getQuestionTypeText(type) {
      const typeMap = {
        'single_choice': '单选题',
        'multiple_choice': '多选题',
        'true_false': '判断题'
      };
      return typeMap[type] || type;
    },
    
    switchView(view) {
      this.currentView = view;
      if (view === 'input') {
        document.getElementById('inputSection').style.display = 'block';
        document.getElementById('contentSection').style.display = 'none';
      } else {
        document.getElementById('inputSection').style.display = 'none';
        document.getElementById('contentSection').style.display = 'block';
        this.renderContent();
      }
    },
    
    resetAll() {
      this.currentView = 'input';
      this.questions = [];
      this.notes = [];
      this.searchKeyword = '';
      this.filterType = 'all';
      this.templateName = '';
      this.templateContent = '';
      this.fileId = null;
      this.generateStatus = 'idle';
      this.downloadPath = '';
      
      document.getElementById('textInput').value = '';
      document.getElementById('inputSection').style.display = 'block';
      document.getElementById('contentSection').style.display = 'none';
      document.getElementById('statusSection').style.display = 'none';
      document.getElementById('downloadSection').style.display = 'none';
    },

    showDownloadModal() {
      const modal = document.getElementById('downloadModal');
      const downloadPathInput = document.getElementById('modalDownloadPath');
      const confirmBtn = document.getElementById('confirmDownloadBtn');
      const cancelBtn = document.getElementById('cancelDownloadBtn');
      const fileNameDisplay = document.getElementById('modalFileName');
      
      if (this.fileId) {
        modal.style.display = 'flex';
        fileNameDisplay.textContent = this.fileId + '.html';
        downloadPathInput.value = this.downloadPath;
        
        confirmBtn.onclick = () => {
          const path = downloadPathInput.value.trim() || '';
          this.downloadFile(path);
          this.hideDownloadModal();
        };
        
        confirmBtn.disabled = false;
        cancelBtn.onclick = () => {
          this.hideDownloadModal();
        };
      } else {
        alert('请先生成内容');
      }
    },

    hideDownloadModal() {
      const modal = document.getElementById('downloadModal');
      modal.style.display = 'none';

      this.statusPollingInterval = setInterval(() => {
        this.checkGenerateStatus();
      }, 2000);
    },

    async checkGenerateStatus() {
      if (!this.fileId) {
        return;
      }

      try {
        const response = await axios.get(`/api/generate/status/${this.fileId}`);
        const result = response.data;
        
        if (result.status === 'completed') {
          this.generateStatus = 'completed';
          this.stopStatusPolling();
          
          this.showDownloadModal();
        } else if (result.status === 'generating') {
          this.generateStatus = 'generating';
        }
      } catch (error) {
        console.error('查询状态失败', error);
      }
    },

    stopStatusPolling() {
      if (this.statusPollingInterval) {
        clearInterval(this.statusPollingInterval);
        this.statusPollingInterval = null;
      }
    },

    downloadFile() {
      if (!this.fileId) {
        alert('请先生成内容');
        return;
      }

      let downloadUrl = '/api/download/' + this.fileId + '.html';
      
      if (this.downloadPath && this.downloadPath.trim() !== '') {
        downloadUrl = this.downloadPath + '/' + this.fileId + '.html';
      }

      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = this.fileId + '.html';
      link.style.display = 'none';
      document.body.appendChild(link);
      
      link.click();
      
      setTimeout(() => {
        document.body.removeChild(link);
      }, 100);
    }
  },
  mounted() {
    document.getElementById('analyzeBtn').addEventListener('click', () => this.analyzeContent());
  }
});

app.mount('#app');