# PKQB - 智能题库生成系统

AI 驱动的问题和笔记转 HTML 工具。

## 项目结构

```
PKQB/
├── backend/          # Spring Boot 后端 (Java 17)
├── frontend/         # Vue 3 + Vite 前端
├── docs/             # 文档
├── generated/        # 生成的 HTML 文件
└── uploads/          # 上传的文件
```

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+

### 后端启动

```bash
cd backend
./mvnw spring-boot:run
```

后端运行在 http://localhost:8080

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端运行在 http://localhost:5173

## API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/upload` | POST | 文件上传 (Word/PDF) |
| `/api/parse` | POST | 解析上传的文件 |
| `/api/analyze` | POST | AI 内容分析 |
| `/api/generate` | POST | 生成 HTML |
| `/api/download/{fileName}` | GET | 下载生成的文件 |

## 技术栈

### 后端
- Spring Boot 3.2.5
- Spring AI (Alibaba)
- Apache POI (Word 解析)
- Apache PDFBox (PDF 解析)
- Handlebars (模板引擎)

### 前端
- Vue 3
- Vite
- TypeScript
- Pinia
- Axios

## 部署

### 开发环境

前后端分离部署，开发时使用 Vite 代理：

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

## 配置

### 后端配置

编辑 `backend/src/main/resources/application.properties`:

```properties
# 服务端口
server.port=8080

# AI 配置
spring.ai.dashscope.api-key=your-api-key

# CORS 配置
cors.allowed-origins=http://localhost:5173
```

### 前端配置

编辑 `frontend/.env.production`:

```env
VITE_API_BASE_URL=https://your-api-server.com
```

## License

MIT