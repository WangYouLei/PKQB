// 全局状态
let currentTab = 'file';
let uploadedFilePath = null;
let uploadedFileType = null;
let analysisResult = null;
let generatedFileUrl = null;

// DOM 元素
let uploadArea, fileInput, fileItem, fileName, textInput, resultSection, successSection, loadingOverlay, loadingText;

// 初始化
document.addEventListener('DOMContentLoaded', function() {
    // 获取DOM元素
    uploadArea = document.getElementById('uploadArea');
    fileInput = document.getElementById('fileInput');
    fileItem = document.getElementById('fileItem');
    fileName = document.getElementById('fileName');
    textInput = document.getElementById('textInput');
    resultSection = document.getElementById('resultSection');
    successSection = document.getElementById('successSection');
    loadingOverlay = document.getElementById('loadingOverlay');
    loadingText = document.getElementById('loadingText');
    
    initEventListeners();
});

// 初始化事件监听器
function initEventListeners() {
    // 选项卡切换
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', function() {
            const tabName = this.dataset.tab;
            switchTab(tabName);
        });
    });

    // 上传区域点击
    uploadArea.addEventListener('click', function() {
        fileInput.click();
    });

    // 拖拽上传
    uploadArea.addEventListener('dragover', function(e) {
        e.preventDefault();
        this.style.borderColor = '#00ff88';
    });

    uploadArea.addEventListener('dragleave', function(e) {
        e.preventDefault();
        this.style.borderColor = '#333';
    });

    uploadArea.addEventListener('drop', function(e) {
        e.preventDefault();
        this.style.borderColor = '#333';
        handleFileDrop(e.dataTransfer.files[0]);
    });

    // 文件选择
    fileInput.addEventListener('change', function(e) {
        if (this.files[0]) {
            handleFileSelect(this.files[0]);
        }
    });

    // 移除文件
    document.getElementById('removeFile').addEventListener('click', function() {
        removeUploadedFile();
    });

    // 解析按钮
    document.getElementById('parseBtn').addEventListener('click', parseFile);

    // 分析按钮
    document.getElementById('analyzeBtn').addEventListener('click', analyzeContent);

    // 生成按钮
    document.getElementById('generateBtn').addEventListener('click', generateHtml);

    // 下载按钮
    document.getElementById('downloadBtn').addEventListener('click', downloadHtml);

    // 重新开始
    document.getElementById('resetBtn').addEventListener('click', resetAll);
}

// 切换选项卡
function switchTab(tabName) {
    currentTab = tabName;

    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.tab === tabName);
    });

    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.toggle('active', content.id === tabName + '-tab');
    });
}

// 处理文件选择
function handleFileSelect(file) {
    const fileName = file.name.toLowerCase();
    const fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);

    // 根据文件后缀判断文件类型
    let fileType = '';
    switch (fileExtension) {
        case 'doc':
        case 'docx':
            fileType = 'word';
            break;
        case 'pdf':
            fileType = 'pdf';
            break;
        default:
            alert('不支持的文件类型，请上传 Word（.doc/.docx）或 PDF 文件');
            return;
    }

    const validMimeTypes = ['application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                         'application/msword',
                         'application/pdf'];

    if (!validMimeTypes.includes(file.type)) {
        alert('不支持的文件类型，请上传 Word 或 PDF 文件');
        return;
    }

    uploadFile(file, fileType);
}

// 处理文件拖放
function handleFileDrop(file) {
    if (!file) {
        return;
    }
    handleFileSelect(file);
}

// 上传文件
async function uploadFile(file, fileType) {
    showLoading('上传中...');

    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', fileType);

    try {
        const response = await fetch('/api/upload', {
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (result.success) {
            uploadedFilePath = result.data.filePath;
            uploadedFileType = fileType;
            fileName.textContent = file.name;
            document.getElementById('fileList').style.display = 'block';
        } else {
            alert(result.message);
        }

    } catch (error) {
        alert('上传失败：' + error.message);
    }

    hideLoading();
}

// 移除上传的文件
function removeUploadedFile() {
    uploadedFilePath = null;
    uploadedFileType = null;
    fileName.textContent = '';
    document.getElementById('fileList').style.display = 'none';
}

// 解析文件
async function parseFile() {
    if (!uploadedFilePath) {
        alert('请先上传文件');
        return;
    }

    showLoading('解析中...');

    try {
        const response = await fetch('/api/parse', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                fileType: uploadedFileType,
                filePath: uploadedFilePath
            })
        });

        const result = await response.json();

        if (result.success) {
            textInput.value = result.data.text;
            showTab('text');
        } else {
            alert(result.message);
        }

    } catch (error) {
        alert('解析失败：' + error.message);
    }

    hideLoading();
}

// 分析内容
async function analyzeContent() {
    const text = textInput.value.trim();

    if (!text) {
        alert('请输入内容');
        return;
    }

    if (text.length < 10) {
        alert('内容太少，请提供更多信息');
        return;
    }

    showLoading('AI 分析中，请稍候...');

    try {
        const response = await fetch('/api/analyze', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                text: text
            })
        });

        const result = await response.json();

        if (result.success) {
            analysisResult = result.data;
            displayAnalysisResult(result.data);
        } else {
            alert(result.message);
        }

    } catch (error) {
        alert('分析失败：' + error.message);
    }

    hideLoading();
}

// 显示分析结果
function displayAnalysisResult(result) {
    document.getElementById('resultType').textContent =
        result.type === 'question' ? '题目' : '笔记';
    document.getElementById('resultTitle').textContent = result.title || '未命名';

    const count = result.items ? result.items.length : 0;
    document.getElementById('resultCount').textContent = count;

    // 显示内容预览
    const previewList = document.getElementById('previewList');
    previewList.innerHTML = '';

    if (result.items) {
        result.items.forEach((item, index) => {
            const div = document.createElement('div');
            div.className = 'preview-item';

            if (result.type === 'question') {
                div.innerHTML = `
                    <h4>${index + 1}. ${item.question}</h4>
                    <p><strong>类型：</strong>${getQuestionTypeText(item.questionType)}</p>
                    ${item.options ? `<p><strong>选项：</strong>${item.options.join('、')}</p>` : ''}
                    <p><strong>答案：</strong>${item.answer}</p>
                `;
            } else {
                div.innerHTML = `
                    <h4>${item.title}</h4>
                    <p>${item.content}</p>
                `;
            }

            previewList.appendChild(div);
        });
    }

    resultSection.style.display = 'block';
}

// 获取题目类型文本
function getQuestionTypeText(type) {
    const typeMap = {
        'single_choice': '单选题',
        'multiple_choice': '多选题',
        'true_false': '判断题'
    };
    return typeMap[type] || type;
}

// 生成 HTML
async function generateHtml() {
    if (!analysisResult) {
        alert('请先分析内容');
        return;
    }

    showLoading('生成 HTML 中...');

    try {
        const response = await fetch('/api/generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                type: analysisResult.type,
                title: analysisResult.title,
                items: analysisResult.items
            })
        });

        const result = await response.json();

        if (result.success) {
            generatedFileUrl = '/api/download/' + result.data.fileName;
            showSuccessSection();
        } else {
            alert(result.message);
        }

    } catch (error) {
        alert('生成失败：' + error.message);
    }

    hideLoading();
}

// 显示成功区域
function showSuccessSection() {
    resultSection.style.display = 'none';
    successSection.style.display = 'block';
}

// 下载 HTML
function downloadHtml() {
    if (generatedFileUrl) {
        window.open(generatedFileUrl, '_blank');
    }
}

// 重置所有
function resetAll() {
    currentTab = 'file';
    uploadedFilePath = null;
    uploadedFileType = null;
    analysisResult = null;
    generatedFileUrl = null;

    fileInput.value = '';
    textInput.value = '';
    document.getElementById('fileList').style.display = 'none';
    resultSection.style.display = 'none';
    successSection.style.display = 'none';

    switchTab('file');
}

// 显示加载遮罩
function showLoading(text) {
    loadingText.textContent = text;
    loadingOverlay.style.display = 'flex';
}

// 隐藏加载遮罩
function hideLoading() {
    loadingOverlay.style.display = 'none';
}

// 显示指定选项卡
function showTab(tabName) {
    switchTab(tabName);
}
