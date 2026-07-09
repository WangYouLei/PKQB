# PKQB · 智能题库生成系统

> 基于 **Spring AI Alibaba ReactAgent** 的 AI 驱动题库管理与智能学习平台，融合 RAG 知识库问答、AI 自动出题、SM-2 间隔复习等能力，面向学生群体提供"出题 — 练习 — 错题巩固 — 智能答疑"的闭环学习体验。

---

## 目录

- [项目概述](#项目概述)
- [核心功能](#核心功能)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [技术亮点与难点](#技术亮点与难点)
- [数据库设计](#数据库设计)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [API 接口概览](#api-接口概览)
- [部署说明](#部署说明)

---

## 项目概述

PKQB（智能题库生成系统）是一个前后端分离的 AI 教育应用，核心解决三个问题：

1. **AI 自动出题**：上传 Word/PDF 试卷或输入知识点，AI 自动结构化提取 6 种题型（单选/多选/填空/判断/计算/简答），并生成答案、解析与计算步骤。
2. **RAG 知识库问答**：学生上传学习资料构建私有/班级知识库，基于两阶段检索（向量粗排 + qwen3-rerank 精排）的 RAG 进行精准答疑。
3. **错题间隔复习**：基于 SM-2 记忆曲线算法，对做错的题目自动收集并安排科学的复习计划。

系统采用 **Spring Boot 3.2 + Vue 3** 技术栈，集成 **Spring AI Alibaba 1.1.2** 的 ReactAgent 多 Agent 框架，通过 SSE 流式输出 + WebSocket 实时通知双通道实现良好的实时交互体验。

---

## 核心功能

| 模块 | 功能说明 |
|------|----------|
| **用户认证** | 注册 / 登录 / 登出，JWT Token 认证，Argon2id 密码加密 |
| **模型管理** | 用户自定义 API Key（加密存储），支持配置主模型与辅助模型 |
| **文件管理** | Word/PDF 解析、图片识别、MinIO 对象存储、班级文件共享 |
| **题库管理** | 试卷 CRUD、批量管理、6 种题型、AI 自动生成、导出 HTML 试卷 |
| **AI 对话** | ReactAgent 多 Agent 对话 + RAG 知识库问答，SSE 流式输出 |
| **AI 解题** | 拍照/上传题目，AI 识别并给出答案与解析 |
| **错题本** | 错题自动收集、SM-2 间隔重复复习、4 级掌握度统计 |
| **通知系统** | WebSocket 实时推送，7 种通知类型，消息中心管理 |
| **班级管理** | 班级创建、文件共享、公开题库 |
| **历史管理** | 聊天历史保存、删除、重新生成回复、用量统计 |
| **审计日志** | AOP 切面记录关键操作，接口限流保护 |

---

## 系统架构

### 整体架构

![系统框架图](docs/系统框架图.png)

系统采用分层架构：

- **客户端层**：浏览器 / 移动端
- **前端展示层**：Vue 3 + TypeScript + Vite + Pinia + Axios
- **网关代理层**：Nginx 反向代理 + 静态托管
- **后端服务层**：Spring Boot 3.2（端口 5555），含 Auth / File / AI / Rubric / Wrong / Notification / User 7 大业务模块
- **AI 能力层**：Spring AI Alibaba / DashScope / ReactAgent / RAG
- **数据存储层**：MySQL 8.0（业务数据）+ Redis（缓存/向量库/会话记忆）+ MinIO/OSS（对象存储）

### 功能模块结构

![系统功能模块结构图](docs/系统功能模块结构图.png)

---

## 技术栈

### 后端

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **核心框架** | Spring Boot | 3.2.5 | Web 框架、自动配置 |
| **AI 框架** | Spring AI Alibaba | 1.1.2.0 | ReactAgent 多 Agent、DashScope 大模型接入 |
| **AI 工具** | Spring AI | 1.1.2 | Vector Store、Tika/PDF Document Reader、Advisors |
| **ORM** | MyBatis-Plus | 3.5.5 | 数据持久化、分页、代码生成 |
| **数据库** | MySQL | 8.0+ | 业务数据存储 |
| **缓存/向量库** | Redis | 8.0+ | 缓存、Redis Vector Store、ReactAgent 会话记忆 |
| **Redis 客户端** | Jedis / Redisson | 5.2.0 / 3.27.2 | 连接池、分布式锁（RedisSaver） |
| **对象存储** | MinIO | 8.5.7 | 文件、试卷 HTML、头像存储 |
| **文档解析** | Apache POI / PDFBox | 5.4.0 / 3.0.1 | Word / PDF 文档解析 |
| **认证** | JJWT | 0.12.5 | JWT Token 生成与校验 |
| **密码加密** | Argon2 (argon2-jvm) | 2.11 | PHC 冠军密码哈希算法 |
| **实时通信** | Spring WebSocket | - | 通知实时推送 |
| **API 文档** | springdoc-openapi | 2.5.0 | OpenAPI 3.0 文档 |
| **AOP** | spring-boot-starter-aop | - | 审计日志切面 |

### 前端

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **框架** | Vue | 3.4.21 | 响应式 UI 框架 |
| **构建工具** | Vite | 5.2.6 | 开发服务器、打包 |
| **语言** | TypeScript | 5.4.3 | 类型安全 |
| **状态管理** | Pinia | 2.1.7 | 全局状态 |
| **路由** | Vue Router | 4.3.0 | SPA 路由 |
| **HTTP** | Axios | 1.6.8 | API 请求 |
| **Markdown** | marked | 18.0.2 | AI 回复 Markdown 渲染 |
| **PWA** | Service Worker | - | 离线缓存、可安装 |

---

## 技术亮点与难点

> 以下为本项目中较具技术含量的设计与实现，适合在面试中深入展开。

### 1. Spring AI Alibaba ReactAgent 多 Agent 架构

系统基于 ReactAgent 构建了三个职责分明的智能体，通过 Spring Bean 容器管理，按场景路由调用：

| Agent | 职责 | 系统提示词 | Hook 配置 |
|-------|------|-----------|-----------|
| `chatReactAgent` | 通用学习助手对话 | 启发式导师角色 | SummarizationHook + ModelCallLimitHook |
| `ragReactAgent` | RAG 知识库问答 | 知识库问答助手，区分"引用/未引用"回答 | RagAgentHook + SummarizationHook + ModelCallLimitHook |
| `simpleReactAgent` | 轻量任务（如解题） | 默认 | 无 |

**关键设计**：
- **SummarizationHook**：长对话自动摘要压缩，当上下文超过 4000 tokens 触发，保留最近 20 条消息，避免 Token 溢出同时保留短期记忆。
- **ModelCallLimitHook**：限制 Agent 单轮工具调用次数上限为 3，防止 Agent 陷入无限循环。
- **MemorySaver**：会话状态持久化，支持基于 sessionId 的多轮对话恢复。

详见 [ReactAgentConfig.java](backend/src/main/java/pkqb/config/ReactAgentConfig.java)。

### 2. RAG 两阶段检索（粗排 + 精排）

区别于传统的单阶段向量检索，系统实现了"召回率优先 + 精确率优先"的两阶段检索策略，显著降低检索噪音：

```
用户查询
  │
  ▼
Step 1: 粗排（向量检索，Redis Vector Store）
        ├─ topK = 20（扩大召回）
        └─ similarityThreshold = 0.5
  │
  ▼
Step 2: 精排（qwen3-rerank 语义重排序）
        └─ topN = 5（按相关性降序）
  │
  ▼
注入 Agent 上下文 → 生成回答
```

**实现要点**：
- 粗排使用 Spring AI 的 `VectorStore.similaritySearch()`，基于 Redis Vector Store 的向量索引。
- 精排调用阿里百炼 `qwen3-rerank` 模型（自研 [DashScopeRerankService](backend/src/main/java/pkqb/service/DashScopeRerankService.java)），对粗排结果做语义相关性重排序。
- 失败降级：rerank 调用异常时返回粗排原始顺序前 N 篇，保证可用性。
- **数据隔离**：通过 `RAGSearchTool` 的过滤表达式实现"私有知识库 + 班级公开知识库"的权限隔离。

### 3. Agent Hook 自定义扩展

`RagAgentHook` 继承 `AgentHook`，通过 `@HookPositions({HookPosition.BEFORE_AGENT})` 在 Agent 执行前注入 RAG 检索上下文，实现"检索增强"与"Agent 推理"的解耦：

- Agent 本身不感知检索逻辑，保持单一职责。
- 检索上下文通过 `OverAllState` 与 `RunnableConfig.metadata` 在 Agent 生命周期内传递。
- 同时支持工具调用模式（`RAGSearchTool` 作为 FunctionToolCallback 注册），让 Agent 在推理过程中按需主动检索。

详见 [RagAgentHook.java](backend/src/main/java/pkqb/config/RagAgentHook.java)。

### 4. 策略模式处理多题型解析

针对 6 种题型的结构化提取，采用策略模式实现，符合开闭原则：

```
QuestionExtractStrategy (接口)
  ├─ SingleChoiceQuestionStrategy      单选题
  ├─ MultipleChoiceQuestionStrategy    多选题
  ├─ FillBlankQuestionStrategy         填空题
  ├─ TrueFalseQuestionStrategy         判断题
  ├─ CalculationQuestionStrategy       计算题
  └─ ShortAnswerQuestionStrategy       简答题
```

`QuestionExtractContext` 作为上下文持有所有策略实现，根据题目文本特征选择对应策略，新增题型只需新增 Strategy 实现类，无需修改现有逻辑。

### 5. SM-2 间隔重复复习算法

错题本实现了经典的 **SM-2（SuperMemo 2）记忆曲线算法**，科学安排复习计划：

**算法参数**：
- `ease_factor`（易度因子 EF）：初始 2.5，最低 1.3，反映题目对用户的"难度"
- `interval_days`（复习间隔）：下次复习距今天的天数
- `mastery_level`（掌握程度）：0-3 四级

**更新规则**：
- 答对：`EF = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))`，间隔按 EF 递增
- 答错：`EF = max(1.3, EF - 0.2)`，间隔重置为 1 天，掌握度归零

**掌握度等级**：

| 等级 | 标签 | 条件 |
|------|------|------|
| 0 | 未掌握 | 初始 / 答错重置 |
| 1 | 初步掌握 | 答对 1-2 次 |
| 2 | 基本掌握 | 答对 3-4 次 |
| 3 | 完全掌握 | 答对 5+ 次且 EF ≥ 2.0 |

详见 [WrongQuestionServiceImpl.java](backend/src/main/java/pkqb/service/impl/WrongQuestionServiceImpl.java)。

### 6. SSE + WebSocket 双通道实时通信

针对不同实时场景选择最优技术方案：

| 通道 | 技术 | 场景 | 理由 |
|------|------|------|------|
| **SSE** | Fetch + ReadableStream | AI 流式对话 | 单向流式输出，SSE 是最佳场景 |
| **WebSocket** | Spring WebSocket | 服务端推送通知 | 需要服务端主动推送，双向通信 |

**WebSocket 实现**：
- 端点 `/ws/notification`，JWT 握手认证（优先级：Header > Cookie > URL 参数）
- `CopyOnWriteArrayList` 支持同一用户多端连接
- 7 种通知类型：题目解析完成/失败、HTML 生成完成、知识库上传完成/失败、AI 解题完成、系统通知
- 前端指数退避自动重连（1s → 2s → 4s → 最大 30s），ws/wss 协议自适应

详见 [WebSocketConfig.java](backend/src/main/java/pkqb/config/WebSocketConfig.java) 与 [useWebSocket.ts](frontend/src/composables/useWebSocket.ts)。

### 7. 安全设计

| 维度 | 方案 |
|------|------|
| **密码加密** | Argon2id（2025 年 PHC 冠军算法），抗 GPU/ASIC 破解 |
| **API Key 保护** | 用户 API Key 加密存储（[ApiKeyEncryptor](backend/src/main/java/pkqb/util/ApiKeyEncryptor.java)），不明文落库 |
| **认证** | JWT Token，拦截器统一校验，WebSocket 独立握手认证 |
| **审计日志** | `@AuditLog` 注解 + AOP 切面（[AuditLogAspect](backend/src/main/java/pkqb/aspect/AuditLogAspect.java)）记录关键操作 |
| **接口限流** | [RateLimitService](backend/src/main/java/pkqb/service/RateLimitService.java) 基于 Redis 的滑动窗口限流 |
| **异常处理** | `@RestControllerAdvice` 全局异常处理 + `Result<T>` 统一响应封装 |
| **软删除** | 试卷、题目采用 `deleted` 字段软删除，避免误删数据丢失 |

---

## 数据库设计

### ER 图

![数据库ER图](docs/数据库ER图.png)

### 核心表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `class` | 班级表 | id, class_name |
| `user` | 用户表 | id, username, password_hash, student_no, class_id |
| `rubric` | 试卷表（软删除） | id, title, create_id, is_private, question_count |
| `question` | 题目表（软删除） | id, rubric_id, question_type, options_json, answer, explanation |
| `question_resource` | 题目资源表 | 题目配图、选项图片、答案图片等 |
| `file` | 文件表 | id, user_id, minio_key, is_private |
| `models` | 用户模型配置表 | 用户自定义的 AI 模型列表 |
| `wrong_question` | 错题本表 | SM-2 参数（ease_factor, interval_days, next_review_date, mastery_level） |
| `notification` | 通知表 | id, user_id, type, is_read |
| `audit_log` | 审计日志表 | 操作记录 |

建表 SQL 位于 [docs/](docs/) 目录：
- [database-schema.sql](docs/database-schema.sql) — 基础表结构
- [rubric_tables.sql](docs/rubric_tables.sql) — 试卷与题目表
- [wrong_question_tables.sql](docs/wrong_question_tables.sql) — 错题本表
- [notification_tables.sql](docs/notification_tables.sql) — 通知表

---

## 项目结构

```
PKQB/
├── backend/                          # 后端服务
│   ├── src/main/java/pkqb/
│   │   ├── PkqbApplication.java      # 启动类
│   │   ├── controller/               # 控制器层（7 个 Controller）
│   │   ├── service/                  # 业务服务层
│   │   │   ├── impl/                 # 服务实现
│   │   │   └── strategy/             # 策略模式（题型解析）
│   │   ├── config/                   # 配置类（AI/WebSocket/Redis/MinIO/Swagger）
│   │   ├── tool/                     # AI 工具（RAGSearchTool/ImageViewTool）
│   │   ├── interceptor/              # 拦截器（JWT/Log）
│   │   ├── aspect/                   # AOP 切面（审计日志）
│   │   ├── annotation/               # 自定义注解（@AuditLog）
│   │   ├── mapper/                   # MyBatis-Plus Mapper
│   │   ├── pojo/                     # 实体与 DTO
│   │   │   ├── entity/               # 数据库实体
│   │   │   └── dto/                  # 数据传输对象
│   │   ├── enums/                    # 枚举（题型/模型类型等）
│   │   ├── common/                   # 公共类（Result/常量）
│   │   └── util/                     # 工具类（JWT/Argon2id/ApiKeyEncryptor）
│   └── src/main/resources/
│       ├── application.example.yml   # 配置模板
│       └── templates/                # 试卷 HTML 模板
│
├── frontend/                         # 前端应用
│   ├── src/
│   │   ├── views/                    # 页面（12 个视图）
│   │   │   ├── Home.vue              # 主页
│   │   │   ├── Login.vue / Register.vue
│   │   │   ├── MyFiles.vue           # 我的文件/做题
│   │   │   ├── ClassFiles.vue        # 班级文件
│   │   │   ├── RubricUpload.vue      # 试卷上传
│   │   │   ├── AIChat.vue            # AI 对话
│   │   │   ├── RAGChat.vue           # RAG 知识库问答
│   │   │   ├── KBUpload.vue          # 知识库上传
│   │   │   ├── WrongQuestionBook.vue # 错题本
│   │   │   ├── History.vue           # 聊天历史
│   │   │   ├── Settings.vue          # 设置
│   │   │   └── Upload.vue            # 文件上传
│   │   ├── components/               # 通用组件
│   │   ├── composables/              # 组合式函数（useWebSocket/useToast 等）
│   │   ├── stores/                   # Pinia 状态
│   │   ├── api/                      # Axios 封装与接口定义
│   │   ├── router/                   # 路由配置
│   │   └── types/                    # TypeScript 类型
│   └── public/                       # 静态资源（PWA manifest/sw.js）
│
└── docs/                             # 项目文档
    ├── 系统框架图.puml / .png
    ├── 系统功能模块结构图.puml / .png
    ├── 数据库ER图.puml / .png
    ├── 系统顶层用例图.png
    ├── 功能更新日志_2026-05-27.md
    └── *.sql                         # 建表脚本
```

---

## 快速开始

### 环境要求

| 依赖 | 版本 |
|------|------|
| Java | 17+ |
| Node.js | 18+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Redis | 8.0+ |
| MinIO | 最新版（可选，也可用阿里云 OSS） |

### 1. 准备基础设施

```bash
# 启动 MySQL 和 Redis（以 Docker 为例）
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=your_password mysql:8.0
docker run -d --name redis -p 6379:6379 redis:8.0

# 启动 MinIO
docker run -d --name minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

### 2. 初始化数据库

```bash
mysql -u root -p < docs/database-schema.sql
mysql -u root -p pkqb < docs/rubric_tables.sql
mysql -u root -p pkqb < docs/wrong_question_tables.sql
mysql -u root -p pkqb < docs/notification_tables.sql
```

### 3. 启动后端

```bash
cd backend
cp src/main/resources/application.example.yml src/main/resources/application.yml
# 编辑 application.yml，填入 MySQL/Redis/MinIO/DashScope API Key 等配置
./mvnw spring-boot:run
```

后端运行在 http://localhost:5555 ，API 文档位于 http://localhost:5555/swagger-ui.html

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 http://localhost:5173

---

## API 接口概览

系统提供 RESTful API，共 7 大模块 40+ 接口，完整文档可通过 Swagger UI 查看。

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
| `/api/user/avatar` | POST/PUT | 上传/更新头像 |

### API Key 与模型管理

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/apikey` | POST/DELETE | 设置/删除 API Key |
| `/api/apikey/status` | GET | 获取 API Key 状态 |
| `/api/apikey/model` | POST/DELETE | 添加/删除模型 |
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
| `/api/files/{id}` | DELETE/PUT | 删除/更新文件 |
| `/api/files/batch` | DELETE | 批量删除文件 |

### AI 对话

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/ai/query` | POST | AI 多 Agent 对话（SSE 流式） |
| `/api/ai/rag-query` | POST | RAG 知识库问答（SSE 流式） |
| `/api/ai/add-documentsFile` | POST | 上传文档到知识库 |
| `/api/ai/handle-rubricFile` | POST | AI 解析试卷文件（含图片识别） |
| `/api/ai/ai-solve` | POST | AI 解题 |
| `/api/ai/get-historyList` | GET | 获取聊天历史列表 |
| `/api/ai/get-history-by-sessionId` | GET | 获取指定会话聊天记录 |
| `/api/ai/delete-history` | DELETE | 删除聊天会话 |
| `/api/ai/usage` | GET | AI 用量统计 |

### 错题本

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/wrong-question` | POST | 添加错题（重复错题自动累加） |
| `/api/wrong-question/list` | GET | 获取错题列表（按试卷分组） |
| `/api/wrong-question/today-review` | GET | 获取今日待复习错题 |
| `/api/wrong-question/review` | POST | 提交复习结果（更新 SM-2 参数） |
| `/api/wrong-question/stats` | GET | 获取错题统计 |
| `/api/wrong-question/{id}` | DELETE | 删除错题 |

### 通知

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/notification/list` | GET | 获取通知列表 |
| `/api/notification/unread-count` | GET | 获取未读通知数 |
| `/api/notification/read/{id}` | PUT | 标记通知已读 |
| `/api/notification/read-all` | PUT | 全部标记已读 |
| `/api/notification/{id}` | DELETE | 删除通知 |

---

## 部署说明

### 开发环境

```bash
# 终端 1: 启动后端
cd backend && ./mvnw spring-boot:run

# 终端 2: 启动前端
cd frontend && npm run dev
```

### 生产环境

1. **构建前端**：

```bash
cd frontend
npm run build
```

2. **部署前端**：将 `frontend/dist/` 部署到 Nginx，配置反向代理：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://backend:5555;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket 代理（通知实时推送）
    location /ws/ {
        proxy_pass http://backend:5555;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
```

3. **部署后端**：打包 JAR 部署到服务器

```bash
cd backend
./mvnw clean package -DskipTests
java -jar target/pkqb-0.0.1-SNAPSHOT.jar
```

### 部署注意事项

1. **数据库迁移**：首次部署需依次执行 `docs/` 下的建表 SQL。
2. **WebSocket 代理**：Nginx 需配置 WebSocket 升级支持（见上方配置）。
3. **HTTPS 环境**：WebSocket 已自动适配 `wss://` 协议，无需额外配置。
4. **DashScope API Key**：需在阿里云百炼平台申请，配置到 `application.yml`。
