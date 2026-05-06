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
| 用户认证 | 注册、登录、JWT Token 认证 |
| 文件管理 | Word/PDF 解析、MinIO 对象存储 |
| 题库管理 | 创建、修改、删除试卷 |
| 题库生成 | 5种题型（单选/多选/填空/判断/计算/简答）AI 生成 |
| AI 对话 | 通义千问模型 + RAG 知识库问答 |
| 班级管理 | 班级创建、文件共享 |
| API Key | 用户自定义 AI API Key 配置 |

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
# 编辑 application.yml填入配置
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
| `/api/user/update` | PUT | 更新用户信息 |
| `/api/user/avatar` | POST | 上传头像 |
| `/api/user/apikey` | GET | 获取 API Key |
| `/api/user/apikey` | POST | 设置 API Key |

### 题库

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/rubric/add-rubric` | POST | 创建试卷 |
| `/api/rubric/my` | GET | 我的试卷 |
| `/api/rubric/public` | GET | 公开试卷 |
| `/api/rubric/{id}/questions` | GET | 获取题目 |
| `/api/rubric/update` | PUT | 修改试卷 |
| `/api/rubric/{id}` | DELETE | 删除试卷 |
| `/api/rubric/question/update` | PUT | 修改题目 |
| `/api/rubric/{id}/questions/batch` | POST | 批量保存题目 |
| `/api/rubric/generate-html` | POST | 生成 HTML |

### 文件

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/file/upload` | POST | 文件上传 |
| `/api/file/list` | GET | 文件列表 |
| `/api/file/{id}` | DELETE | 删除文件 |

### AI 对话

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/ai/chat` | POST | AI 对话 |
| `/api/ai/rag-chat` | POST | RAG 知识库问答 |
| `/api/ai/kb/upload` | POST | 上传知识库 |

## 技术栈

### 后端
- Spring Boot 3.2.5
- Spring AI (Alibaba DashScope)
- MyBatis Plus
- Redis + Redis Vector Store
- MinIO 对象存储
- Apache POI (Word 解析)
- Apache PDFBox (PDF 解析)
- Swagger (OpenAPI 文档)

### 前端
- Vue 3
- Vite
- TypeScript
- Pinia
- Axios

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