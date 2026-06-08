# PKQB - 智能题库生成系统

AI 驱动的题库管理和生成系统。

## 项目结构

```
PKQB/
├── backend/          # Spring Boot 后端 (Java 17)
└── frontend/        # Vue 3 + Vite 前端
```

## 核心功能

| 模块 | 功能 |
|------|------|
| 用户认证 | 注册、登录、修改密码、JWT Token 认证 |
| 模型管理 | 用户自定义 API Key，支持配置主模型和辅助模型 |
| 文件管理 | Word/PDF 解析、图片识别、MinIO 对象存储 |
| 题库管理 | 创建、修改、删除试卷，批量管理题目 |
| 题库生成 | 5+ 种题型（单选/多选/填空/判断/计算/简答）AI 自动生成 |
| AI 对话 | ReactAgent 多 Agent 对话 + RAG 知识库问答，流式输出 |
| 错题本 | 错题自动收集、间隔重复复习、掌握度统计 |
| 通知系统 | WebSocket 实时推送通知、消息中心管理 |
| 班级管理 | 班级创建、文件共享 |
| 历史管理 | 聊天历史保存、删除、重新生成回复 |

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+
- MySQL 8.0+
- Redis 8.0+

### 后端启动

```bash
cd backend
cp src/main/resources/application.example.yml src/main/resources/application.yml
# 编辑 application.yml 填入配置
./mvnw spring-boot:run
```

后端运行在 http://localhost:5555

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端运行在 http://localhost:5173

## API 端点

### 认证

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/logout` | POST | 用户登出 |

### 用户

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/user/info` | GET | 获取用户信息 |
| `/api/user/username` | PUT | 修改用户名 |
| `/api/user/password` | PUT | 修改密码 |
| `/api/user/avatar` | POST | 上传头像 |
| `/api/user/avatar` | PUT | 更新头像 |
| `/api/user/avatar/upload-path` | GET | 获取头像上传路径 |

### API Key 与模型管理

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/apikey` | POST | 设置 API Key |
| `/api/apikey` | DELETE | 删除 API Key |
| `/api/apikey/status` | GET | 获取 API Key 状态 |
| `/api/apikey/model` | POST | 添加模型 |
| `/api/apikey/model` | DELETE | 删除模型 |
| `/api/apikey/model/main` | PUT | 设置主模型 |
| `/api/apikey/models` | GET | 获取模型列表 |

### 题库

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/rubric/add-rubric` | POST | 创建试卷 |
| `/api/rubric/my` | GET | 我的试卷 |
| `/api/rubric/public` | GET | 公开试卷 |
| `/api/rubric/{id}/questions` | GET | 获取题目 |
| `/api/rubric/update` | PUT | 修改试卷 |
| `/api/rubric/{id}` | DELETE | 删除试卷 |
| `/api/rubric/batch` | DELETE | 批量删除试卷 |
| `/api/rubric/question/update` | PUT | 修改题目 |
| `/api/rubric/{id}/questions/batch` | POST | 批量保存题目 |
| `/api/rubric/generate-html` | POST | 生成试卷 HTML |
| `/api/rubric/upload-image` | POST | 上传题目图片 |

### 文件

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/files/upload` | POST | 文件上传 |
| `/api/files/my` | GET | 我的文件列表 |
| `/api/files/class/public` | GET | 班级公开文件 |
| `/api/files/download/{id}` | GET | 下载文件 |
| `/api/files/{id}` | DELETE | 删除文件 |
| `/api/files/{id}` | PUT | 更新文件 |
| `/api/files/batch` | DELETE | 批量删除文件 |

### AI 对话

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/ai/query` | POST | AI 多 Agent 对话（SSE 流式） |
| `/api/ai/rag-query` | POST | RAG 知识库问答（SSE 流式） |
| `/api/ai/add-documentsFile` | POST | 上传文档到知识库 |
| `/api/ai/handle-rubricFile` | POST | AI 解析试卷文件（含图片识别） |
| `/api/ai/handle-rubricFile-local` | POST | AI 解析本地试卷文件 |
| `/api/ai/ai-solve` | POST | AI 解题 |
| `/api/ai/get-historyList` | GET | 获取聊天历史列表 |
| `/api/ai/get-history-by-sessionId` | GET | 获取指定会话聊天记录 |
| `/api/ai/delete-history` | DELETE | 删除聊天会话 |
| `/api/ai/delete-messages` | DELETE | 删除指定聊天消息 |
| `/api/ai/usage` | GET | AI 用量统计 |

### 错题本

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/wrong-question` | POST | 添加错题 |
| `/api/wrong-question/list` | GET | 获取错题列表 |
| `/api/wrong-question/today-review` | GET | 获取今日待复习错题 |
| `/api/wrong-question/review` | POST | 提交复习结果 |
| `/api/wrong-question/stats` | GET | 获取错题统计 |
| `/api/wrong-question/{id}` | DELETE | 删除错题 |
| `/api/wrong-question/batch` | DELETE | 批量删除错题 |

### 通知

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/notification/list` | GET | 获取通知列表 |
| `/api/notification/unread-count` | GET | 获取未读通知数 |
| `/api/notification/read/{id}` | PUT | 标记通知已读 |
| `/api/notification/read-all` | PUT | 全部标记已读 |
| `/api/notification/{id}` | DELETE | 删除通知 |
| `/api/notification/clear` | POST | 清空所有通知 |

## 技术栈

### 后端
- Spring Boot 3.2.5
- Spring AI Alibaba (DashScope + ReactAgent)
- MyBatis Plus
- Redis + Redis Vector Store
- MinIO 对象存储
- WebSocket (通知实时推送)
- Apache POI (Word 解析)
- Apache PDFBox (PDF 解析)
- Swagger (OpenAPI 文档)

### 前端
- Vue 3
- Vite
- TypeScript
- Pinia
- Axios
- WebSocket

## 部署

### 开发环境

```bash
# 终端 1: 启动后端
cd backend && ./mvnw spring-boot:run

# 终端 2: 启动前端
cd frontend && npm run dev
```

### 生产环境

1. 构建前端：
```bash
cd frontend
npm run build
```

2. 将 `frontend/dist/` 部署到 Nginx 或 CDN

3. 后端 JAR 部署到服务器
