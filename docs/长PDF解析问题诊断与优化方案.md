# 长 PDF 解析问题诊断与优化方案

> 适用版本：`rebuild` 分支（对象存储已统一为阿里云 OSS 之后）
> 涉及入口：题目解析 `/api/ai/handle-rubricFile`、知识库上传 `/api/ai/add-documentsFile`
> 本文定位：先给可复现的根因，再给分阶段改造方案；每一条都标注了代码位置，便于直接落地。

---

## 一、问题现象

用户反馈集中在两类：

1. **解析不了**：长 PDF 提交后最终返回“解析失败 / 结果为空 / 任务不存在”，或返回的题目明显不全。
2. **解析时间过长**：几十页以上的 PDF 需要十几分钟甚至更久，前端进度长时间停在“AI 解析中”，最终可能超时。

这两类现象不是同一个原因造成的，而是**入口限制、链路选择、分片参数、任务生命周期**四层问题叠加。

---

## 二、现状链路

### 2.1 题目解析

入口：`SpringAiAlibabaController#handleRubricFile` → `SpringAiAlibabaServiceImpl#submitRubricFileParse` → 异步任务 → `doHandleRubricFile`。

链路选择只看扩展名（`SpringAiAlibabaServiceImpl.java` L736-744）：

```java
boolean isPdf = originalFilename.toLowerCase().endsWith(".pdf");
if (isPdf) {
    return visionParser.handleRubricFileWithVision(file, userId, progress);
}
return handleRubricFileWithText(file, userId, null, progress);
```

**只要后缀是 `.pdf`，即使是有文字层的数字版 PDF，也一律走视觉模型。**

视觉链路（`RubricVisionParser#handleRubricFileWithVision` L82-137）：

```
PDDocument 全量加载
  → 串行渲染全部页面为 JPEG（115 DPI）
  → 提取文档内嵌图片
  → 全部页面图 + 嵌入图并行上传 OSS
  → 等待全部上传完成
  → 按 2 页/片并发调用视觉模型（全局并发 3）
  → 按片顺序合并，跨页边界再补调一次
```

文本链路（`SpringAiAlibabaServiceImpl#handleRubricFileWithText` L761-819）：

```
PagePdfDocumentReader 抽取全文
  → 截断到 3000 字符
  → 单次结构化输出调用
  → 失败则直接报错
```

### 2.2 知识库上传

`SpringAiAlibabaServiceImpl#doAddDocuments`（L294-389）：

```
PagePdfDocumentReader 抽取全文
  → 拼接全文
  → isRubric() 调用 Agent 判断内容类型（最长阻塞 120s）
  → 上传 OSS + 写 file 表
  → 按 \n\n+ 分段 + TokenTextSplitter 切片
  → 每 10 个片段一批，串行写向量库
```

---

## 三、根因分析

### 3.1 为什么会“解析不了”

| # | 根因 | 代码位置 | 后果 |
|---|---|---|---|
| 1 | Spring multipart 上限只有 8MB，且没有专门的超限异常处理 | `application.yml` L19-22；`GlobalExceptionHandler` L68-83 | 超过 8MB 的试卷 PDF 直接失败，前端只看到“服务器内部错误” |
| 2 | 文本降级只保留前 3000 字符 | `SpringAiAlibabaServiceImpl.java` L135、L780-784 | 长 PDF 降级后实际只解析前 1~2 页，后续内容全部丢失且无提示 |
| 3 | 系统 Key 的文本模型输出上限只有 1024 tokens | `application.yml` L32；`DashScopeModelFactory#createLocalChatModel` L144-156 | 题目+选项+答案+解析的结构化输出远超 1024 tokens，JSON 必被截断，文本模式几乎必然失败 |
| 4 | 页面渲染的 try 包在整个循环外 | `RubricVisionParser.java` L143-156 | 任意一页渲染异常，后续所有页都不再渲染，静默返回“前半本” |
| 5 | 任务状态只存 Redis，TTL 30 分钟 | `SpringAiAlibabaServiceImpl.java` L97-98、L688-695 | 长 PDF 解析超过 30 分钟，轮询返回“任务不存在或已过期”，但任务其实还在跑 |
| 6 | 单片输出上限 4096 tokens | `DashScopeModelFactory.java` L105、L131 | 单页题目较多时 JSON 被截断，该片判为失败；重试后仍失败则整片题目丢失 |
| 7 | 视觉失败后降级文本，文本又因 #2/#3 失败 | `RubricVisionParser.java` L132-136 | 最终呈现为“解析失败”或“结果为空” |
| 8 | 视觉模型需要从公网拉取 OSS 图片 | `OssService#getPublicUrl` L165-168 | 如果 Bucket 是私有读、开启防盗链或 URL 不可达，所有分片都会失败 |

### 3.2 为什么会“时间过长”

| # | 根因 | 代码位置 | 说明 |
|---|---|---|---|
| 1 | 所有 PDF 都走视觉链路 | `SpringAiAlibabaServiceImpl.java` L736-744 | 数字版 PDF 本可秒级抽文本，却要渲染、上传、逐页调视觉模型，成本相差一个数量级 |
| 2 | 视觉 prompt 未明确禁止模型补全答案/解析，模型倾向输出完整答案 | `RubricVisionParser.java` L431-450 | 每道题输出被放大 2~3 倍，输出 token 是视觉调用耗时的主要来源（文本链路则显式要求生成答案） |
| 3 | 全量串行渲染 + 全量上传后才开始调模型 | `RubricVisionParser.java` L91-119 | 100 页时渲染+上传本身就需 1~3 分钟，期间模型完全空闲 |
| 4 | 每片 2 页、全局并发 3，且为跨用户共享的静态信号量 | `RubricVisionParser.java` L58-63 | 一个长 PDF 会占满全平台 1/3 以上的解析能力，其他用户排队 |
| 5 | 解析任务线程池只有 3 个线程 | `SpringAiAlibabaServiceImpl.java` L85-94 | 多用户并发提交长 PDF 时任务排队严重 |
| 6 | 无显式连接/读取超时，且存在双重自动重试 | `application.yml` L24-25；`RubricVisionParser.java` L289-309 | `spring.ai.retry.max-attempts=2` 加上手写重试，最坏情况同一分片调用 4 次 |
| 7 | 合并阶段串行补漏 | `RubricVisionParser.java` L339-406 | 片数越多，边界补调的串行等待越明显 |
| 8 | 知识库链路：`isRubric` 走 Agent、embedding 串行、无进度上报 | `SpringAiAlibabaServiceImpl.java` L315、L391、L410-415、L1077-1102 | 上传后状态长期停留在“等待处理”，用户无法判断是否卡死 |

### 3.3 关键量化估算

以一份 100 页、含 200 道题的扫描版试卷为例：

- 视觉分片数：100 / 2 = **50 片**
- 单片输出：约 4 题 × 200~400 tokens ≈ **800~1600 tokens**（含答案解析时更高）
- 按视觉模型 30~50 tokens/s 估算：**单片 20~60 秒**
- 全局并发 3：理论总耗时 **6~17 分钟**；叠加渲染、上传、重试与边界补调后，**15~30 分钟**是常见区间

若改为文本链路（数字版 PDF），100 页文本抽取通常 1~3 秒，分片调用 20~30 次，总耗时可以压到 **2~5 分钟**。

---

## 四、优化方案

### 4.1 P0：止血包（约 0.5~1 天，低风险）

目标：先解决“直接失败”和“静默少页/少题”，再做性能。

#### ① 上传上限 8MB → 50MB

- `application.yml`：

  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size: 50MB
        max-request-size: 50MB
  ```

- 部署若经过 Nginx，同步设置 `client_max_body_size 50m`。
- `GlobalExceptionHandler` 增加 `MaxUploadSizeExceededException` 处理，返回明确的业务提示，而不是 500“服务器内部错误”。
- 前端选择文件时先做大小校验，避免用户上传完才失败。

#### ② 提高系统模型的输出上限

- `application.yml` L32：`localhost-max-tokens: 1024` → `8192`
- 结构化输出场景温度降到 0.1~0.2，减少跑题和无效输出。

#### ③ 文本链路去掉 3000 字符截断，改为分片解析

`getDocuments()` 已按页返回 `Document`（`SpringAiAlibabaServiceImpl.java` L960-983），可直接按 4~6 页一组调用 `callForStructuredOutput`，再复用视觉链路的“按片合并 + 首尾跨页标记”思路。

示意：

```java
List<Document> pages = getDocuments(file);
List<List<Document>> chunks = partition(pages, 5);
List<AiRubric> all = new ArrayList<>();
for (List<Document> chunk : chunks) {
    String text = chunk.stream().map(Document::getText).collect(joining("\n"));
    all.addAll(callForStructuredOutput(getQuestionAnalysisPrompt(text), userId));
}
```

#### ④ 视觉 Prompt 去掉“自动生成答案/解析/步骤”

解析阶段只做提取：文档里有答案就提取，没有就留空。答案与解析另做“AI 补全”入口（保存前批量补全或单题补全）。

预期输出 token 下降 **50%~70%**，单片耗时接近腰斩。

#### ⑤ 渲染按页容错

把 `renderPdfPagesToBytes` 的 `try` 移进循环体：单页失败只跳过该页，并记录失败页号；空白页/几乎无内容的页直接跳过，不送给模型。

#### ⑥ 任务生命周期修正

- `RUBRIC_TASK_TTL`：30 分钟 → 2 小时。
- 进度信息带上：`已解析 x/y 片`、`已用时`、`失败页列表`。
- **部分成功就返回部分结果**，并附失败页提示；只有 0 片成功时才降级文本。
- 结果较大时单独存储（DB 或 OSS），Redis 只存状态，避免轮询反复传输大 JSON。

#### ⑦ 显式超时

- 模型调用设置连接超时 10s、读取超时 120~180s。
- 单个任务设置总 deadline（如 30 分钟）。
- 对确定性错误（401/403、模型不存在、JSON 解析失败）不重试；只对超时、429、5xx 做退避重试。

---

### 4.2 P1：结构性优化（约 2~4 天）

#### ① 先判断 PDF 类型，再选择链路（收益最大）

用 PDFBox 按页抽样统计可提取字符数：

- 前 5 页 + 随机 5 页平均字符数 > 阈值（如 50 字/页）→ **文本分片链路**
- 低于阈值 → **视觉链路（扫描件）**

示意：

```java
try (PDDocument doc = Loader.loadPDF(bytes)) {
    PDFTextStripper stripper = new PDFTextStripper();
    int sampled = 0, chars = 0;
    for (int p = 1; p <= Math.min(5, doc.getNumberOfPages()); p++) {
        stripper.setStartPage(p);
        stripper.setEndPage(p);
        chars += stripper.getText(doc).replaceAll("\\s", "").length();
        sampled++;
    }
    boolean textBased = sampled > 0 && chars / sampled > 50;
}
```

这一步能把“数字版长 PDF”从十几分钟降到几分钟。

#### ② 视觉链路改为流水线

当前是“全渲染 → 全上传 → 再解析”。改为滑动窗口：

```
渲染 2~4 页 → 上传这 2~4 页 → 立即提交模型
     ↑                                   ↓
     └──────── 释放图片内存，继续下一批 ────┘
```

收益：

- 内存占用从“整本 PDF 的页面图”降为“窗口内几页”
- 首题返回时间从“全部预处理完成”提前到 30~60 秒
- 渲染/上传与模型调用重叠，总耗时下降

同时可把渲染参数从 115 DPI 降到 100 DPI、JPEG 质量 0.7 → 0.65，渲染与上传各降约 30%。

#### ③ 分片与并发参数外置

- 每片 2 页 → 3~4 页
- 单片 `max_tokens` 4096 → 8192
- 增加配置项：单任务并发 4~6、全局并发 8~12
- 遇到 429/限流做指数退避，而不是立即失败
- `PARSE_TASK_EXECUTOR` 按用户做公平排队，避免单个用户的超长任务占满线程池

#### ④ 结果带页码，支持“只重试失败页”

每道题增加 `pageIndex` 元数据；失败页单独重跑并合并，不必整篇重来。

#### ⑤ 知识库上传链路优化

- embedding 批次改为 4~8 并发（单批仍 10 条），写入速度约提升 4 倍
- `isRubric` 先走现有 `isRubric2` 规则判断，只有不确定时才调用模型，并把超时压到 15~20s
- 每批 embedding 完成后更新任务进度
- 扫描件抽不出文本时返回明确提示：“该 PDF 为扫描件，请使用题目解析的视觉模式或 OCR”

---

### 4.3 P2：支撑超长文档（可选）

- 解析任务落 DB + 独立 worker 或消息队列，进程重启可恢复、可断点续跑（与 `docs/后期优化清单.md` 4.4 对应）
- 扫描件改走 OCR / 阿里云文档智能，比逐页视觉模型快一个数量级，成本也更低
- 前端支持按页段拆分为多个任务，解析完成后合并

---

## 五、改动清单

| 优先级 | 文件 | 位置 | 改动 | 预期收益 |
|---|---|---|---|---|
| P0 | `backend/src/main/resources/application.yml` | L19-22 | multipart 上限 → 50MB | 解除大文件直接失败 |
| P0 | `backend/src/main/java/pkqb/config/GlobalExceptionHandler.java` | L68-83 | 增加上传超限异常处理 | 错误提示可理解 |
| P0 | `application.yml` / `DashScopeModelFactory.java` | L32 / L144-156 | 系统模型输出上限 → 8192 | 文本模式不再截断 |
| P0 | `SpringAiAlibabaServiceImpl.java` | L135、L780-784 | 去掉 3000 字符截断，改按页分片 | 长文本全量解析 |
| P0 | `RubricVisionParser.java` | L431-450 | Prompt 去除自动生成答案/解析/步骤 | 输出 token 降 50%~70% |
| P0 | `RubricVisionParser.java` | L143-156 | 渲染 try 移入循环 | 坏页不再毁掉整本 |
| P0 | `SpringAiAlibabaServiceImpl.java` | L97-98、L322-327 | TTL → 2 小时；部分成功返回部分结果 | 长任务不再“任务不存在” |
| P0 | `RubricVisionParser.java` | L289-309 | 区分可重试/不可重试错误 + 超时 | 减少无效重试 |
| P1 | `SpringAiAlibabaServiceImpl.java` | L736-744 | 增加 PDF 文本层判别 | 数字版 PDF 走文本链路 |
| P1 | `RubricVisionParser.java` | L91-119 | 渲染/上传/解析流水线化 | 首题提前、内存下降 |
| P1 | `RubricVisionParser.java` | L58-63 | 分片与并发参数外置 | 多用户互不阻塞 |
| P1 | `SpringAiAlibabaServiceImpl.java` | L391、L410-415 | embedding 并发 + 进度上报 | 知识库入库提速 |
| P1 | `SpringAiAlibabaServiceImpl.java` | L985-1001 | `isRubric` 规则优先 | 去掉最长 120s 的额外等待 |
| P2 | 新增任务表 / worker | — | 任务持久化与断点续跑 | 重启不丢任务 |

---

## 六、预期效果（估算）

| 场景 | 现状 | P0 后 | P1 后 |
|---|---|---|---|
| 8~50MB 的 PDF | 直接 500 | 正常提交 | 正常提交 |
| 100 页数字版 PDF | 走视觉，15~30 分钟，易失败 | 文本分片，3~8 分钟 | 2~5 分钟，稳定 |
| 100 页扫描版 PDF | 15~30 分钟，可能超时/丢片 | 8~15 分钟，可交付部分结果 | 4~8 分钟，首题 1 分钟内 |
| 长文本降级 | 只解析前 3000 字符 | 全量解析 | 全量解析 |
| 单片输出截断 | 整片题目丢失 | max_tokens 8192 + 不生成答案，基本消除 | 同左 |

> 以上为按当前参数推算的量级，实际以线上测量为准。

---

## 七、验收与观测指标

上线前建议先补一组分阶段耗时日志：

- `load`：PDFBox 加载耗时
- `render`：逐页渲染耗时与页数
- `upload`：OSS 上传张数与耗时
- `chunkCall`：每片模型调用的输入页数、输出字符数、耗时、是否重试
- `merge`：合并与边界补调耗时
- `total`：任务总耗时、成功片数、失败片数

用一份真实长 PDF 跑一遍，确认瓶颈权重后再决定参数，避免凭感觉调并发。

验收标准建议：

1. 50MB 以内的 PDF 均可提交，且超限时有明确提示；
2. 100 页数字版 PDF 在 5 分钟内完成，题目数量与原文一致；
3. 100 页扫描版 PDF 在 8 分钟内完成，部分失败时能返回成功页的题目和失败页清单；
4. 任务运行超过 30 分钟仍可轮询到状态；
5. 任意单页渲染异常不影响其余页面解析。

---

## 八、需要确认的参数

1. **最大文件大小**：是否可以放开到 50MB（涉及 OSS、Nginx、前端三处）。
2. **解析阶段是否必须立即生成答案**：如果允许先提取、后补全，视觉链路预计再快一半。
3. **视觉模型额度**：当前 Key 的 RPM/TPM 上限，决定全局并发能开到几。
4. **扫描件占比**：如果多数试卷是扫描件，P2 的 OCR/文档智能方案应提前。

---

## 九、建议实施顺序

1. 加分阶段耗时日志，用真实长 PDF 跑基线（约 1 小时）
2. P0 ①~③：配置 + 文本分片 + 错误处理（约半天）
3. P0 ④~⑦：Prompt 精简 + 渲染容错 + TTL/部分结果 + 超时（约半天）
4. P1 ①：PDF 文本层判别（收益最大）
5. P1 ②③：流水线 + 并发参数
6. P1 ⑤：知识库链路并发与进度

---

## 十、P0 落地记录（2026-11-24）

已落地：

- 上传上限 8MB → 50MB，新增 `MaxUploadSizeExceededException` 处理与前端 50MB 预校验；上传接口超时提高到 180s
- 系统模型输出上限 1024 → 8192；系统模型与视觉模型的结构化解析温度 0.7 → 0.2（个人 Key 的主模型仍使用模型默认温度）
- 文本链路移除 3000 字符截断，改为按页/字符分段并发解析（每段约 6000 字符、并发 3、重叠 150 字符、题干去重）
- 视觉 prompt 明确“只提取已有内容，不编造答案/解析”
- 逐页渲染容错，空白页跳过，失败页汇总到最终结果提示
- 任务 TTL 30 分钟 → 2 小时；进度携带片数/段数与已用时长；部分分片失败返回部分结果
- 模型调用增加连接 10s / 读取 180s 超时；鉴权失败、模型不存在、400 等确定性错误不再重试
- 新增单次任务规模上限：`pkqb.ai.parse.max-pdf-pages=300`、`pkqb.ai.parse.max-text-chunks=200`

暂未落地（留待 P1）：

- 解析结果单独存储（目前仍存 Redis，2 小时 TTL）
- 单任务总 deadline（目前只有单次调用超时）
- 文本链路仍保留“无答案时自动生成答案/解析”的提示词；视觉链路已改为只提取
- PDF 文本层判别、渲染/上传/解析流水线、并发参数外置

---

## 附：相关代码索引

- 解析入口与编排：`backend/src/main/java/pkqb/service/impl/SpringAiAlibabaServiceImpl.java`
- 视觉解析全链路：`backend/src/main/java/pkqb/service/impl/RubricVisionParser.java`
- 模型构建与参数：`backend/src/main/java/pkqb/config/DashScopeModelFactory.java`
- 上传配置：`backend/src/main/resources/application.yml`
- 全局异常：`backend/src/main/java/pkqb/config/GlobalExceptionHandler.java`
- OSS 公网 URL：`backend/src/main/java/pkqb/service/OssService.java`
- 前端轮询：`frontend/src/views/RubricUpload.vue`、`frontend/src/views/KBUpload.vue`
- 既有技术债台账：`docs/后期优化清单.md`（3.1 文件上传、3.2 切分策略、4.4 异步任务持久化）
