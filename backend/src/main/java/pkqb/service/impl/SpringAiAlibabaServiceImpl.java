package pkqb.service.impl;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.common.RateLimitConstants;
import pkqb.config.DashScopeModelFactory;
import pkqb.config.RagAgentHook;
import pkqb.config.ReactAgentFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.dto.RubricParseTaskStatus;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.mapper.FileMapper;
import pkqb.pojo.entity.FileEntity;
import pkqb.service.MinioService;
import pkqb.service.NotificationService;
import pkqb.service.RagDataIsolationService;
import pkqb.service.RateLimitService;
import pkqb.service.SpringAiAlibabaService;
import pkqb.service.UserApiKeyService;
import pkqb.service.strategy.QuestionExtractContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 服务主入口（实现 {@link SpringAiAlibabaService}）：负责知识库上传、RAG/对话流式查询、
 * 会话历史管理、题目文件异步解析任务编排与本地解析。
 * 视觉模型解析链路拆分至 {@link RubricVisionParser}，AI 解题链路拆分至 {@link AiQuestionSolver}；
 * 共享线程池 AI_EXECUTOR 与静态工具方法统一放在 {@link AiCommonSupport}，全局仅一份。
 */
@Service
@Slf4j
public class SpringAiAlibabaServiceImpl implements SpringAiAlibabaService {
    private final VectorStore vectorStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final QuestionExtractContext questionExtractContext;
    private final ReactAgentFactory reactAgentFactory;
    private final DashScopeModelFactory dashScopeModelFactory;
    private final MinioService minioService;
    private final NotificationService notificationService;
    private final UserApiKeyService userApiKeyService;
    private final FileMapper fileMapper;
    private final RagDataIsolationService ragDataIsolationService;
    /** 视觉模型解析组件：PDF→图片→视觉模型的题目解析全链路（见 RubricVisionParser） */
    private final RubricVisionParser visionParser;
    /** AI 解题组件：答案/解析/步骤生成全链路（见 AiQuestionSolver） */
    private final AiQuestionSolver aiQuestionSolver;

    /** 题目文件异步解析任务线程池：与分片线程池 AI_EXECUTOR（见 AiCommonSupport）分离，避免任务线程 join 分片任务造成线程饥饿 */
    private static final ExecutorService PARSE_TASK_EXECUTOR = new ThreadPoolExecutor(
            3, 3, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "rubric-parse-task-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );

    /** 题目解析任务状态 Redis key 前缀与过期时间 */
    private static final String RUBRIC_TASK_KEY_PREFIX = "rubric:parse:task:";
    private static final Duration RUBRIC_TASK_TTL = Duration.ofMinutes(30);

    private static final StringBuilder TYPE_DESC = new StringBuilder();
    static {
        for (RubricQuestionTypeEnum type : RubricQuestionTypeEnum.values()) {
            TYPE_DESC.append("- ").append(type.getCode()).append(": ").append(type.getValue());
            TYPE_DESC.append("\n");
        }
    }
    
    private static final String QUESTION_ANALYSIS_PROMPT_TEMPLATE = """
            请提取每个题目的问题、题型、选项和答案。

            题型说明：
            %s

            要求：
            1. 从文档内容中提取信息，文档中有的答案和解析就提取。
            2. 如果文档中没有答案、解析或计算步骤，请根据题目内容自行生成合理的答案、解析和计算步骤。
               - 单选题/多选题：根据题目内容推断正确答案，简要题目解析
               - 填空题：根据题目内容推断正确答案
               - 判断题：根据题目内容判断对错，给出解析
               - 简答题：给出合理的答案要点
               - 计算题：给出正确答案和详细的计算步骤
            3. explanation字段字数控制在100字以内并且在不影响表达的情况越少越好
            4. answer字段：多选题用逗号分隔如A,B,C；简答题和计算题直接写答案内容
            5. 数学内容禁止使用LaTeX语法（如\\angle、^\\circ、\\frac、$...$），必须直接使用Unicode字符：
               角度用∠、度数用°、乘号用×、除号用÷、不等于用≠、小于等于用≤、大于等于用≥、约等于用≈、根号用√、圆周率用π、三角形用△；分数写成a/b，幂写成x²

            文本内容：
            %s
            """;
    
    private String getQuestionAnalysisPrompt(String content) {
        return String.format(QUESTION_ANALYSIS_PROMPT_TEMPLATE, TYPE_DESC, content);
    }

    private static final int RUBRIC_TRUNCATE_LENGTH = 3000;
    private static final int RUBRIC_DETECT_LENGTH = 1500;
    private static final int NOTE_DETECT_LENGTH = 1000;

    private static final String[] QUESTION_TYPE_KEYWORDS = {"单选题", "多选题", "选择题", "填空题", "判断题", "简答题",
            "计算题", "应用题", "解答题", "证明题", "阅读理解", "完形填空",
            "听力", "作文题", "论述题", "分析题"};

    private static final String[] ANSWER_MARKERS = {"【答案】", "[答案]", "【解析】", "[解析]", "参考答案",
            "得分", "分值", "（  ）", "(  )", "____", "___", "……"};

    private static final String[] QUESTION_WORDS = {"什么", "哪个", "哪些", "为什么", "如何", "怎样", "多少", "是否", "能否", "何"};

    private static final String[] NOTE_KEYWORDS = {"概念", "定义", "原理", "性质", "特点", "特征", "作用", "意义",
            "方法", "步骤", "流程", "机制", "结构", "组成", "分类", "类型",
            "首先", "其次", "最后", "因此", "所以", "因为", "然而", "但是",
            "总之", "综上所述", "例如", "比如", "换句话说", "也就是说",
            "核心", "关键", "本质", "基础", "前提", "条件", "因素",
            "包括", "分为", "由...组成", "主要有", "常见的", "一般来说"};

    private static final String[] CONNECTIVES = {"因此", "所以", "然而", "但是", "而且", "此外", "同时",
            "另外", "另一方面", "综上所述", "由此可见", "换言之"};

    private static final Pattern OPTION_PATTERN_CACHE = Pattern.compile("(?i)[（(\\[]?[A-D][）)\\]]?[、.．，,]");
    private static final Pattern QUESTION_MARK_PATTERN = Pattern.compile("\\?|\\？");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?m)^\\s*\\d+[、.．)）]");
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*[#第][一二三四五六七八九十百0-9]+[章章节部分篇条]");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`]+`");

    // 关键词预编译为字面量Pattern：内容判断高频调用，避免每次循环内反复Pattern.compile（约50个词）
    private static final Pattern[] QUESTION_WORD_PATTERNS = compileLiterals(QUESTION_WORDS);
    private static final Pattern[] CONNECTIVE_PATTERNS = compileLiterals(CONNECTIVES);

    private static Pattern[] compileLiterals(String[] keywords) {
        return Arrays.stream(keywords)
                .map(kw -> Pattern.compile(Pattern.quote(kw)))
                .toArray(Pattern[]::new);
    }

    private static final int MAX_SESSIONS = 20;

    private static final String RECORD_SESSION_SCRIPT = 
        "local listKey = KEYS[1]\n" +
        "local sessionId = ARGV[1]\n" +
        "local maxSessions = tonumber(ARGV[2])\n" +
        "local ttl = tonumber(ARGV[3])\n" +
        "local list = redis.call('LRANGE', listKey, 0, -1)\n" +
        "for i, v in ipairs(list) do\n" +
        "  if v == sessionId then\n" +
        "    redis.call('LREM', listKey, 1, v)\n" +
        "    redis.call('LPUSH', listKey, v)\n" +
        "    redis.call('EXPIRE', listKey, ttl)\n" +
        "    return 1\n" +
        "  end\n" +
        "end\n" +
        "if #list >= maxSessions then\n" +
        "  redis.call('RPOP', listKey)\n" +
        "end\n" +
        "redis.call('LPUSH', listKey, sessionId)\n" +
        "redis.call('EXPIRE', listKey, ttl)\n" +
        "return 1";

    public SpringAiAlibabaServiceImpl(VectorStore vectorStore,
                                      StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper,
                                      RateLimitService rateLimitService,
                                      QuestionExtractContext questionExtractContext,
                                      ReactAgentFactory reactAgentFactory,
                                      DashScopeModelFactory dashScopeModelFactory,
                                      MinioService minioService,
                                      NotificationService notificationService,
                                      UserApiKeyService userApiKeyService,
                                      FileMapper fileMapper,
                                      RagDataIsolationService ragDataIsolationService,
                                      RubricVisionParser visionParser,
                                      AiQuestionSolver aiQuestionSolver) {
        this.vectorStore = vectorStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.questionExtractContext = questionExtractContext;
        this.reactAgentFactory = reactAgentFactory;
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.minioService = minioService;
        this.notificationService = notificationService;
        this.userApiKeyService = userApiKeyService;
        this.fileMapper = fileMapper;
        this.ragDataIsolationService = ragDataIsolationService;
        this.visionParser = visionParser;
        this.aiQuestionSolver = aiQuestionSolver;
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        // 共享AI分片线程池统一关闭（定义在 AiCommonSupport，全局仅一份）
        AiCommonSupport.shutdownAiExecutor();
        PARSE_TASK_EXECUTOR.shutdown();
        try {
            if (!PARSE_TASK_EXECUTOR.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                PARSE_TASK_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            PARSE_TASK_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Result<String> addDocuments(MultipartFile file, Long userId) {
        log.info("[知识库-文件上传] 提交异步上传任务: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);

        // 限流检查同步执行，快速失败
        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_KNOWLEDGE, RateLimitConstants.KNOWLEDGE_LIMIT);
        if (limitCheck != null) {
            log.warn("[知识库-文件上传] 用户 {} 超过每日限制", userId);
            return Result.error(limitCheck.getMessage());
        }

        // 同步阶段读出文件字节：上传的临时文件在请求结束后可能被清理
        final String filename = file.getOriginalFilename();
        final byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            log.error("[知识库-文件上传] 读取上传文件失败", e);
            return Result.error("读取上传文件失败，请重新上传");
        }

        // 此前同步请求内做AI内容判断+全文embedding，单请求可挂2分钟以上；改为taskId异步任务，前端轮询结果
        String taskId = UUID.randomUUID().toString();
        saveTaskStatus(new RubricParseTaskStatus(taskId, userId,
                RubricParseTaskStatus.STATUS_RUNNING, "received", "任务已提交，等待处理"));

        PARSE_TASK_EXECUTOR.submit(() -> {
            RubricParseTaskStatus finalStatus;
            try {
                Result<String> result = doAddDocuments(new ByteArrayMultipartFile(filename, fileBytes), userId);
                if (result.getCode() == 200) {
                    finalStatus = new RubricParseTaskStatus(taskId, userId,
                            RubricParseTaskStatus.STATUS_SUCCESS, "done", result.getMessage());
                } else {
                    finalStatus = new RubricParseTaskStatus(taskId, userId,
                            RubricParseTaskStatus.STATUS_FAILED, "done", result.getMessage());
                }
            } catch (Exception e) {
                log.error("[知识库-文件上传] 异步任务执行异常, taskId={}", taskId, e);
                finalStatus = new RubricParseTaskStatus(taskId, userId,
                        RubricParseTaskStatus.STATUS_FAILED, "done", "处理文件失败: " + e.getMessage());
            }
            saveTaskStatus(finalStatus);
        });
        return Result.success(taskId);
    }

    /**
     * 知识库上传的实际处理逻辑（由异步任务调用，限流已在提交入口完成）
     */
    private Result<String> doAddDocuments(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String objectKey = "knowledge/" + userId + "/" + UUID.randomUUID() + extension;
        Long fileRecordId = null;

        try {
            // 1. 文档解析
            List<Document> documents = getDocuments(file);
            if (documents == null || documents.isEmpty()) {
                log.error("[知识库-文件上传] 文件解析失败，未获取到文档内容");
                return Result.error("处理文档发生异常，请从新上传");
            }
            log.info("[知识库-文件上传] 文件解析成功，共 {} 个文档", documents.size());

            // 3. 提取文本内容 + 内容审核
            String content = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));
            if (isRubric(content, userId)) {
                log.info("[知识库-文件上传] 检测到题目内容，已拒绝上传");
                return Result.error("检测到题目内容，已拒绝上传");
            }

            // 4. 上传文件到 Minio
            try {
                minioService.upload(objectKey, file.getInputStream(), file.getContentType(), file.getSize());
                log.info("[知识库-文件上传] 文件已上传到Minio, objectKey={}", objectKey);
            } catch (Exception e) {
                log.error("[知识库-文件上传] 上传文件到Minio失败: ", e);
                return Result.error("上传文件失败，请从新上传");
            }

            // 5. 写入 file 表
            try {
                FileEntity fileEntity = new FileEntity();
                fileEntity.setUserId(userId);
                fileEntity.setFileName(originalFilename);
                fileEntity.setMinioKey(objectKey);
                fileEntity.setIsPrivate(false);
                fileMapper.insert(fileEntity);
                fileRecordId = fileEntity.getId();
                log.info("[知识库-文件上传] 文件记录已保存, fileId={}", fileRecordId);
            } catch (Exception e) {
                // DB写入失败，回滚Minio文件
                log.error("[知识库-文件上传] 保存文件记录失败，回滚Minio文件: ", e);
                try {
                    minioService.remove(objectKey);
                } catch (Exception removeEx) {
                    log.error("[知识库-文件上传] 回滚Minio文件失败, objectKey={}: ", objectKey, removeEx);
                }
                return Result.error("上传文件失败，请从新上传");
            }

            // 6. 写入向量库
            Result<String> result = processDocumentsContent(content, "文件上传", userId, fileRecordId);
            if (result.getCode() != 200) {
                // 向量库写入失败，回滚Minio文件和DB记录
                log.error("[知识库-文件上传] 写入向量库失败，回滚Minio文件和DB记录");
                try {
                    minioService.remove(objectKey);
                } catch (Exception removeEx) {
                    log.error("[知识库-文件上传] 回滚Minio文件失败, objectKey={}: ", objectKey, removeEx);
                }
                try {
                    fileMapper.deleteById(fileRecordId);
                } catch (Exception dbEx) {
                    log.error("[知识库-文件上传] 回滚DB记录失败, fileId={}: ", fileRecordId, dbEx);
                }
                return result;
            }

            // 7. 全部成功
            notificationService.notifyKnowledgeUploadComplete(userId, originalFilename);
            return result;
        } catch (Exception e) {
            // 未知异常，尝试回滚Minio和DB
            log.error("[知识库-文件上传] 上传到向量数据库时发生错误: ", e);
            if (fileRecordId != null) {
                try {
                    fileMapper.deleteById(fileRecordId);
                } catch (Exception dbEx) {
                    log.error("[知识库-文件上传] 回滚DB记录失败, fileId={}: ", fileRecordId, dbEx);
                }
            }
            try {
                minioService.remove(objectKey);
            } catch (Exception removeEx) {
                log.error("[知识库-文件上传] 回滚Minio文件失败, objectKey={}: ", objectKey, removeEx);
            }
            notificationService.notifyKnowledgeUploadFailed(userId, originalFilename, e.getMessage());
            return Result.error("上传文件失败，请从新上传");
        }
    }

    private static final int EMBEDDING_BATCH_SIZE = 10;

    private Result<String> processDocumentsContent(String content, String operationType, Long userId, Long fileId) {
        log.info("[知识库-{}] 开始分割文档", operationType);

        // 权限元数据：检索时按"本人文档或同班共享"过滤，同一文件的所有片段共享同一份元数据
        Map<String, Object> isolationMetadata = ragDataIsolationService.buildUploadMetadata(userId, fileId);

        List<Document> documents = Arrays.stream(content.split("\n\n+"))
                .filter(s -> !s.trim().isEmpty())
                .map(s -> new Document(s, isolationMetadata))
                .collect(Collectors.toList());
        log.info("[知识库-{}] 文档分段完成，共 {} 段", operationType, documents.size());
        
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<Document> splitDocuments = tokenTextSplitter.split(documents);
        log.info("[知识库-{}] 文档分割完成，共 {} 个片段，开始写入向量库", operationType, splitDocuments.size());
        
        int totalSize = splitDocuments.size();
        for (int i = 0; i < totalSize; i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, totalSize);
            List<Document> batch = splitDocuments.subList(i, end);
            vectorStore.add(batch);
            log.info("[知识库-{}] 已写入 {}/{} 个文档片段", operationType, end, totalSize);
        }
        log.info("[知识库-{}] 成功写入向量库，共处理 {} 个文档片段", operationType, totalSize);
        return Result.success("成功上传文件并添加到向量数据库，共处理了 " +
                totalSize + " 个文档片段");
    }

    @Override
    public Flux<String> ragQuery(String query, String sessionId, Long userId) {
        log.info("[RAG查询] userId={}, sessionId={}", userId, sessionId);

        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RAG, RateLimitConstants.RAG_LIMIT);
        if (limitCheck != null) {
            return Flux.error(new RuntimeException(limitCheck.getMessage()));
        }

        recordSessionId(userId.toString(), sessionId, "rag");

        String historyKey = "spring_ai_alibaba_chat_memory:history:rag:" + userId + ":" + sessionId;
        String threadId = "rag:" + userId + ":" + sessionId;
        return streamChat("RAG查询", reactAgentFactory.getRagReactAgent(userId), query, sessionId, historyKey, threadId, userId);
    }

    /**
     * 聊天/RAG共用的流式查询逻辑
     * （此前ragQuery与query各维护一份约100行的相同流式处理代码，收敛于此，差异仅剩日志前缀/Agent/threadId）
     *
     * @param logTag     日志前缀（RAG查询/AI对话）
     * @param agent      已按用户Key模式构建好的ReactAgent
     * @param historyKey Redis历史记录key
     * @param threadId   Agent会话线程ID
     * @param userId     当前用户ID，经 RunnableConfig metadata 传递给 RagAgentHook 做检索数据隔离
     */
    private Flux<String> streamChat(String logTag, ReactAgent agent, String query, String sessionId,
                                    String historyKey, String threadId, Long userId) {
        try {
            String userMsgJson = objectMapper.writeValueAsString(Map.of(
                    "messageType", "USER",
                    "text", query,
                    "timestamp", System.currentTimeMillis()
            ));
            stringRedisTemplate.opsForList().rightPush(historyKey, userMsgJson);
            stringRedisTemplate.expire(historyKey, 7, java.util.concurrent.TimeUnit.DAYS);
            log.info("[{}] 保存用户消息到历史记录, key={}", logTag, historyKey);
        } catch (Exception e) {
            log.warn("[{}] 保存用户消息失败", logTag, e);
        }

        StringBuilder aiResponse = new StringBuilder();

        try {
            log.info("[{}] 开始ReactAgent流式查询，sessionId={}, threadId={}", logTag, sessionId, threadId);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .addMetadata(RagAgentHook.USER_ID_METADATA_KEY, userId)
                    .build();

            return agent.stream(query, config)
                    .filter(output -> output instanceof StreamingOutput<?>)
                    .map(output -> (StreamingOutput<?>) output)
                    .flatMap(streamingOutput -> {
                        OutputType type = streamingOutput.getOutputType();
                        Message message = streamingOutput.message();
                        if(type == OutputType.AGENT_MODEL_STREAMING){
                            if(message instanceof AssistantMessage){
                                // 推理模型的思考过程：单独标记推送，由前端折叠展示，不混入正式回复与历史记录
                                Object reasoningContent = message.getMetadata().get("reasoningContent");
                                if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                    try {
                                        return Mono.just(objectMapper.writeValueAsString(Map.of(
                                                "thinking", true,
                                                "text", reasoningContent.toString())));
                                    } catch (JsonProcessingException e) {
                                        log.warn("[{}] 序列化推理内容失败，跳过该段", logTag, e);
                                        return Mono.empty();
                                    }
                                } else {
                                    if(message.getText() != null && !message.getText().isEmpty()){
                                        aiResponse.append(message.getText());
                                        try {
                                            return Mono.just(objectMapper.writeValueAsString(message.getText()));
                                        } catch (JsonProcessingException e) {
                                            return Mono.just(message.getText().replace("\n", "\\n").replace("\r", "\\r"));
                                        }
                                    }
                                }
                            }
                        }
                        return Mono.empty();
                    })
                    .doOnComplete(() -> {
                        log.info("[{}] 流式查询完成，sessionId={}", logTag, sessionId);

                        if (aiResponse.length() > 0) {
                            try {
                                String aiMsgJson = objectMapper.writeValueAsString(Map.of(
                                        "messageType", "ASSISTANT",
                                        "text", aiResponse.toString(),
                                        "timestamp", System.currentTimeMillis()
                                ));
                                stringRedisTemplate.opsForList().rightPush(historyKey, aiMsgJson);
                                stringRedisTemplate.expire(historyKey, 7, java.util.concurrent.TimeUnit.DAYS);
                                log.info("[{}] 保存AI回复到历史记录, key={}, length={}", logTag, historyKey, aiResponse.length());
                            } catch (Exception e) {
                                log.warn("[{}] 保存AI回复失败", logTag, e);
                            }
                        }
                    })
                    .doOnError(e -> {
                        log.error("[{}] 流式查询异常，sessionId={}", logTag, sessionId, e);
                        if (AiCommonSupport.isApiKeyOrModelError(e)) {
                            throw new RuntimeException("API Key或模型名称有误，请核验");
                        }
                    });
        } catch (Exception e) {
            log.error("[{}] 查询时发生错误", logTag, e);
            return Flux.error(e);
        }
    }

    @Override
    public Result<List<String>> getHistory(String userId, String type) {
        log.info("[历史列表] 获取历史会话列表，userId={}, type={}", userId, type);
        if (type == null || type.isEmpty() || userId == null || userId.isEmpty()) {
            log.warn("[历史列表] 参数为空，userId={}, type={}", userId, type);
            return Result.error("参数不能为空");
        }
        String listKey = "history:" + type + ":" + userId;
        List<String> range = stringRedisTemplate.opsForList().range(listKey, 0, -1);
        int size = range != null ? range.size() : 0;
        log.info("[历史列表] 查询完成，key={}, 共 {} 个会话", listKey, size);
        return Result.success(range);
    }

    @Override
    public Flux<String> query(String query, String sessionId, Long userId) {
        log.info("[AI对话] userId={}, sessionId={}", userId, sessionId);

        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_CHAT, RateLimitConstants.CHAT_LIMIT);
        if (limitCheck != null) {
            return Flux.error(new RuntimeException(limitCheck.getMessage()));
        }

        recordSessionId(userId.toString(), sessionId, "chat");

        String historyKey = "spring_ai_alibaba_chat_memory:history:chat:" + userId + ":" + sessionId;
        String threadId = "chat:" + userId + ":" + sessionId;
        return streamChat("AI对话", reactAgentFactory.getChatReactAgent(userId), query, sessionId, historyKey, threadId, userId);
    }

    private void recordSessionId(String userId, String sessionId, String type) {
        try {
            String sessionListKey = "history:" + type + ":" + userId;
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RECORD_SESSION_SCRIPT, Long.class);
            stringRedisTemplate.execute(script, 
                    List.of(sessionListKey), 
                    sessionId, 
                    String.valueOf(MAX_SESSIONS), 
                    String.valueOf(java.util.concurrent.TimeUnit.DAYS.toSeconds(7)));
            log.debug("[会话管理] 记录会话 {} for 用户 {}", sessionId, userId);
        } catch (Exception e) {
            log.error("[会话管理] 记录会话失败", e);
        }
    }

    @Override
    public Result<Object> getHistoryBySessionId(String sessionId, String userId, String type) {
        log.info("[历史记录] 获取会话历史，sessionId={}, userId={}, type={}", sessionId, userId, type);
        if (sessionId == null || userId == null || type == null) {
            return Result.error("参数不能为空");
        }

        String redisKey = "spring_ai_alibaba_chat_memory:history:" + type + ":" + userId + ":" + sessionId;
        
        try {
            log.info("[历史记录] 查询Redis，key={}", redisKey);
            List<String> messageList = stringRedisTemplate.opsForList().range(redisKey, 0, -1);
            if (messageList == null || messageList.isEmpty()) {
                log.info("[历史记录] 无历史消息，sessionId={}", sessionId);
                return Result.success(Collections.emptyList());
            }
            log.info("[历史记录] 读取到{}条消息", messageList.size());
            List<Map<String, Object>> messages = new ArrayList<>();
            for (String json : messageList) {
                Map<String, Object> msg = new HashMap<>();
                try {
                    JsonNode node = objectMapper.readTree(json);
                    String messageType = node.has("messageType") ? node.get("messageType").asText() : "";
                    String content = node.has("text") ? node.get("text").asText() : "";
                    long timestamp = node.has("timestamp") ? node.get("timestamp").asLong() : System.currentTimeMillis();
                    msg.put("role", "USER".equals(messageType) ? "user" : "assistant");
                    msg.put("content", content);
                    msg.put("timestamp", timestamp);
                } catch (Exception e) {
                    log.warn("[历史记录] 解析消息 JSON 失败: {}", json);
                    msg.put("role", "unknown");
                    msg.put("content", json);
                    msg.put("timestamp", System.currentTimeMillis());
                }
                messages.add(msg);
            }
            return Result.success(messages);
        } catch (Exception e) {
            log.error("[历史记录] 获取会话历史消息时发生错误", e);
            return Result.error("获取历史记录失败");
        }
    }

    @Override
    public Result<String> submitRubricFileParse(String filename, byte[] fileBytes, Long userId, Integer modelType) {
        log.info("[题目文件解析-AI] 提交异步解析任务: {}, 大小: {} bytes, userId={}, modelType={}",
                filename, fileBytes.length, userId, modelType);

        // 限流检查同步执行，快速失败
        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RUBRIC, RateLimitConstants.RUBRIC_LIMIT);
        if (limitCheck != null) {
            return Result.error(limitCheck.getMessage());
        }

        String taskId = UUID.randomUUID().toString();
        saveTaskStatus(new RubricParseTaskStatus(taskId, userId,
                RubricParseTaskStatus.STATUS_RUNNING, "received", "任务已提交，等待解析"));

        PARSE_TASK_EXECUTOR.submit(() -> {
            RubricParseTaskStatus finalStatus;
            try {
                MultipartFile inMemoryFile = new ByteArrayMultipartFile(filename, fileBytes);
                java.util.function.Consumer<String> progress = msg ->
                        saveTaskStatus(new RubricParseTaskStatus(taskId, userId,
                                RubricParseTaskStatus.STATUS_RUNNING, "analyzing", msg));
                Result<List<AiRubric>> result = doHandleRubricFile(inMemoryFile, userId, modelType, progress);
                if (result.getCode() == 200) {
                    finalStatus = new RubricParseTaskStatus(taskId, userId,
                            RubricParseTaskStatus.STATUS_SUCCESS, "done", "解析完成");
                    finalStatus.setResult(result.getData());
                } else {
                    finalStatus = new RubricParseTaskStatus(taskId, userId,
                            RubricParseTaskStatus.STATUS_FAILED, "done", result.getMessage());
                }
            } catch (Exception e) {
                log.error("[题目文件解析-AI] 异步任务执行异常, taskId={}", taskId, e);
                finalStatus = new RubricParseTaskStatus(taskId, userId,
                        RubricParseTaskStatus.STATUS_FAILED, "done", "处理文件失败: " + e.getMessage());
            }
            saveTaskStatus(finalStatus);
        });
        return Result.success(taskId);
    }

    @Override
    public Result<RubricParseTaskStatus> getKbUploadTask(String taskId, Long userId) {
        // 知识库上传任务与题目解析任务共用同一套任务状态存储
        return getRubricParseTask(taskId, userId);
    }

    @Override
    public Result<RubricParseTaskStatus> getRubricParseTask(String taskId, Long userId) {
        String json = stringRedisTemplate.opsForValue().get(RUBRIC_TASK_KEY_PREFIX + taskId);
        if (json == null) {
            return Result.error("任务不存在或已过期");
        }
        try {
            RubricParseTaskStatus status = objectMapper.readValue(json, RubricParseTaskStatus.class);
            if (!status.getUserId().equals(userId)) {
                return Result.error("无权查询该任务");
            }
            return Result.success(status);
        } catch (Exception e) {
            log.error("[题目文件解析-AI] 读取任务状态失败, taskId={}", taskId, e);
            return Result.error("读取任务状态失败");
        }
    }

    private void saveTaskStatus(RubricParseTaskStatus status) {
        try {
            stringRedisTemplate.opsForValue().set(RUBRIC_TASK_KEY_PREFIX + status.getTaskId(),
                    objectMapper.writeValueAsString(status), RUBRIC_TASK_TTL);
        } catch (Exception e) {
            log.error("[题目文件解析-AI] 保存任务状态失败, taskId={}", status.getTaskId(), e);
        }
    }

    /**
     * 基于内存字节的 MultipartFile 包装，供异步任务使用
     * （原始上传的临时文件在HTTP请求结束后可能被清理，必须在同步阶段读出字节）
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String filename;
        private final byte[] bytes;

        ByteArrayMultipartFile(String filename, byte[] bytes) {
            this.filename = filename;
            this.bytes = bytes;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { return bytes == null || bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }

    /**
     * 解析任务主体（原同步解析逻辑），由异步任务线程调用
     */
    private Result<List<AiRubric>> doHandleRubricFile(MultipartFile file, Long userId, Integer modelType,
                                                      java.util.function.Consumer<String> progress) {
        log.info("[题目文件解析-AI] 开始处理文件: {}, 大小: {} bytes, userId={}, modelType={}",
                file.getOriginalFilename(), file.getSize(), userId, modelType);
        try {
            if (modelType != null && modelType == 2) {
                // 用户指定视觉模型
                return visionParser.handleRubricFileWithVision(file, userId, progress);
            } else if (modelType != null && modelType == 1) {
                // 用户指定纯文本模型
                return handleRubricFileWithText(file, userId, null, progress);
            } else {
                // 未指定：自动选择（PDF→视觉模型，其他→纯文本）
                String originalFilename = file.getOriginalFilename();
                boolean isPdf = originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");
                if (isPdf) {
                    return visionParser.handleRubricFileWithVision(file, userId, progress);
                } else {
                    return handleRubricFileWithText(file, userId, null, progress);
                }
            }

        } catch (Exception e) {
            log.error("[题目文件解析-AI] 处理文件时发生错误: ", e);
            return Result.error("处理文件失败: " + e.getMessage());
        }
    }

    /**
     * 使用纯文本提取流程（原有逻辑）
     */
    /**
     * 使用纯文本提取流程（原有逻辑）
     *
     * @param visionFailReason 视觉模型解析失败的原因；非空表示本次是视觉解析失败后的自动降级
     */
    Result<List<AiRubric>> handleRubricFileWithText(MultipartFile file, Long userId, String visionFailReason,
                                                            java.util.function.Consumer<String> progress) {
        try {
            if (visionFailReason != null && progress != null) {
                progress.accept("视觉模型解析失败，已自动降级为文本解析");
            }
            if (progress != null) progress.accept("正在提取文档内容");
            List<Document> documents = getDocuments(file);
            if (documents == null || documents.isEmpty()) {
                log.error("[题目文件解析-AI] 文件解析失败，未获取到文档内容");
                return Result.error("文件解析失败");
            }

            String content = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

            log.info("[题目文件解析-AI] 文件解析成功，内容长度: {}", content.length());

            // 截断内容，防止超出模型 token 限制
            if (content.length() > RUBRIC_TRUNCATE_LENGTH) {
                log.info("[题目文件解析-文本] 内容过长({}字符)，截断到{}字符", content.length(), RUBRIC_TRUNCATE_LENGTH);
                content = content.substring(0, RUBRIC_TRUNCATE_LENGTH);
            }

            String prompt = getQuestionAnalysisPrompt(content);

            try {
                log.info("[题目文件解析-AI] 开始调用AI解析题目（结构化输出）");
                if (progress != null) progress.accept("AI解析中");
                List<AiRubric> aiRubrics = callForStructuredOutput(prompt, userId);

                log.info("[题目文件解析-AI] AI解析完成");

                if (aiRubrics == null || aiRubrics.isEmpty()) {
                    log.warn("[题目文件解析-AI] AI返回为空");
                    return Result.error("AI解析结果为空");
                }

                log.info("[题目文件解析-AI] 解析完成，共提取 {} 道题目", aiRubrics.size());
                if (visionFailReason != null) {
                    notificationService.notifyRubricParseCompleteWithFallback(userId, "视觉模型", visionFailReason, "文本模型");
                } else {
                    notificationService.notifyRubricParseComplete(userId, "文本模型");
                }
                return Result.success(aiRubrics);
            } catch (Exception e) {
                log.error("[题目文件解析-AI] AI解析失败: {}", e.getMessage(), e);
                String failReason = visionFailReason != null
                        ? "视觉模型解析失败：" + visionFailReason + "，自动降级后文本模型也解析失败：" + e.getMessage()
                        : e.getMessage();
                notificationService.notifyRubricParseFailed(userId, failReason);
                return Result.error("AI解析失败，可能原因：题目数量过多等，可以尝试减少题目数量或者使用\"本地解析题目\"功能");
            }
        } catch (Exception e) {
            log.error("[题目文件解析-AI] 纯文本处理失败: ", e);
            return Result.error("处理文件失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<AiRubric>> handleRubricFileLocal(MultipartFile file, Long userId) {
        log.info("[题目文件解析-本地] 开始处理文件: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);
        try {
            Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RUBRIC, RateLimitConstants.RUBRIC_LIMIT);
            if (limitCheck != null) {
                return Result.error(limitCheck.getMessage());
            }
            
            List<Document> documents = getDocuments(file);
            if (documents == null || documents.isEmpty()) {
                log.error("[题目文件解析-本地] 文件解析失败，未获取到文档内容");
                return Result.error("文件解析失败");
            }
            
            String content = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));
            
            log.info("[题目文件解析-本地] 文件解析成功，内容长度: {}", content.length());
            
            try {
                log.info("[题目文件解析-本地] 开始调用本地解析题目");
                List<Map<String, Object>> maps = questionExtractContext.extractQuestions(content);
                if (maps.isEmpty()) {
                    log.warn("[题目文件解析-本地] 本地解析失败");
                    return Result.error("解析题目失败，请检查文件内容格式");
                }
                
                List<AiRubric> aiRubrics = objectMapper.convertValue(maps,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, AiRubric.class));
                log.info("[题目文件解析-本地] 本地解析完成，共 {} 道题目", aiRubrics.size());
                return Result.success(aiRubrics);
            } catch (Exception e) {
                log.error("[题目文件解析-本地] 本地解析失败: {}", e.getMessage(), e);
                return Result.error("本地解析失败: " + e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("[题目文件解析-本地] 处理文件时发生错误: ", e);
            return Result.error("处理文件失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> deleteHistory(String sessionId, Long userId, String type) {
        log.info("[删除历史] 删除{}会话, userId={}, sessionId={}", type, userId, sessionId);
        
        if (type == null || (!type.equals("chat") && !type.equals("rag"))) {
            return Result.error("无效的会话类型");
        }
        
        if (sessionId == null || sessionId.isEmpty() || userId == null) {
            return Result.error("参数不能为空");
        }
        
        try {
            // 1. 从历史列表中移除会话ID
            String listKey = "history:" + type + ":" + userId;
            Long removedCount = stringRedisTemplate.opsForList().remove(listKey, 1, sessionId);
            boolean removed = removedCount != null && removedCount > 0;
            
            // 2. 删除会话详细数据
            String sessionKey = "spring_ai_alibaba_chat_memory:history:" + type + ":" + userId + ":" + sessionId;
            Boolean deleted = stringRedisTemplate.delete(sessionKey);
            

            
            if (removed || deleted) {
                log.info("[删除历史] 成功删除{}会话, userId={}, sessionId={}", type, userId, sessionId);
                return Result.success(true);
            } else {
                log.warn("[删除历史] 会话不存在或已被删除, userId={}, sessionId={}", userId, sessionId);
                return Result.success(false); // 仍然返回成功，因为会话已经不存在了
            }
        } catch (Exception e) {
            log.error("[删除历史] 删除会话时发生错误: ", e);
            return Result.error("删除会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> deleteMessages(String sessionId, Long userId, String type, List<Integer> messageIndices) {
        log.info("[删除消息] 删除消息, userId={}, sessionId={}, type={}, indices={}", userId, sessionId, type, messageIndices);
        
        if (type == null || (!type.equals("chat") && !type.equals("rag"))) {
            return Result.error("无效的会话类型");
        }
        
        if (sessionId == null || sessionId.isEmpty() || userId == null || messageIndices == null || messageIndices.isEmpty()) {
            return Result.error("参数不能为空");
        }
        
        try {
            String sessionKey = "spring_ai_alibaba_chat_memory:history:" + type + ":" + userId + ":" + sessionId;
            
            List<String> messages = stringRedisTemplate.opsForList().range(sessionKey, 0, -1);
            if (messages == null || messages.isEmpty()) {
                return Result.error("会话不存在或没有消息");
            }
            
            // 按下标重建列表后整体覆写，避免LREM按值删除时误删同会话中内容相同的其他消息
            Set<Integer> toDelete = new HashSet<>();
            for (Integer index : messageIndices) {
                if (index < 0 || index >= messages.size()) {
                    log.warn("[删除消息] 索引超出范围: {}", index);
                    continue;
                }
                toDelete.add(index);
            }
            List<String> remain = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                if (!toDelete.contains(i)) {
                    remain.add(messages.get(i));
                }
            }
            // 删旧列表再写入剩余消息，完成按索引精确删除
            stringRedisTemplate.delete(sessionKey);
            if (!remain.isEmpty()) {
                stringRedisTemplate.opsForList().rightPushAll(sessionKey, remain);
                // delete+重写会清掉key原有的TTL，按会话历史统一的7天过期补回，避免历史key在Redis中永久残留
                stringRedisTemplate.expire(sessionKey, 7, java.util.concurrent.TimeUnit.DAYS);
            }

            log.info("[删除消息] 成功删除 {} 条消息, 剩余 {} 条", toDelete.size(), remain.size());
            return Result.success(true);
        } catch (Exception e) {
            log.error("[删除消息] 删除消息时发生错误: ", e);
            return Result.error("删除消息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<String> aiSolveQuestion(String questionText, String questionType, String optionsJson, String generateType, Long userId) {
        // AI 解题全链路已拆分至 AiQuestionSolver，此处仅作接口委托
        return aiQuestionSolver.aiSolveQuestion(questionText, questionType, optionsJson, generateType, userId);
    }

    private List<Document> getDocuments(MultipartFile file) {
        List<Document> documents = new ArrayList<>();
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return documents;
            }
            String substring = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            if ("pdf".equalsIgnoreCase(substring)) {
                byte[] pdfContent = file.getBytes();
                ByteArrayInputStream pdfInputStream = new ByteArrayInputStream(pdfContent);
                InputStreamResource pdfResource = new InputStreamResource(pdfInputStream);
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
                documents = pdfReader.get();
            }else{
                TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
                documents = reader.get();
            }
        } catch (Exception e) {
            log.error("处理文件时发生错误: ", e);
            return documents;
        }
        return documents;
    }

    private boolean isRubric(String content, Long userId) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String sample = content.length() > RUBRIC_DETECT_LENGTH ? content.substring(0, RUBRIC_DETECT_LENGTH) : content;
        String prompt = "请判断下面内容是题目，还是知识点：" + "\n" + "\n"
                + sample +
                "。" + "\n" +
                "回答模版：如果是【题目】就回答：题目，如果是【知识点】就回答：知识点。要求：严格按照回答模版回答。";
        try {
            String call = callReactAgentForText(prompt, userId);
            return call.contains("题目");
        } catch (Exception e) {
            log.info("[题目判断] AI调用失败，fallback到本地规则算法，e={}", e.getMessage());
            return isRubric2(content);
        }
    }

    private boolean isRubric2(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String sample = content.length() > NOTE_DETECT_LENGTH ? content.substring(0, NOTE_DETECT_LENGTH) : content;

        int questionScore = 0;
        int noteScore = 0;

        int optionCount = countMatches(sample, OPTION_PATTERN_CACHE);
        if (optionCount >= 3) {questionScore += 30;}
        else if (optionCount >= 2) {questionScore += 15;}

        int questionMarkCount = countMatches(sample, QUESTION_MARK_PATTERN);
        double questionMarkRatio = (double) questionMarkCount / sample.length();
        if (questionMarkRatio > 0.02) {questionScore += 20;}
        else if (questionMarkRatio > 0.005) {questionScore += 10;}

        for (String kw : QUESTION_TYPE_KEYWORDS) {
            if (sample.contains(kw)) {
                questionScore += 20;
                break;
            }
        }

        for (String marker : ANSWER_MARKERS) {
            if (sample.contains(marker)) {
                questionScore += 15;
                break;
            }
        }

        int numberPattern = countMatches(sample, NUMBER_PATTERN);
        if (numberPattern >= 3) {questionScore += 20;}
        else if (numberPattern >= 2) {questionScore += 10;}

        for (Pattern wordPattern : QUESTION_WORD_PATTERNS) {
            questionScore += countMatches(sample, wordPattern) * 3;
        }

        for (String kw : NOTE_KEYWORDS) {
            if (sample.contains(kw)) {noteScore += 8;}
        }

        String[] paragraphs = sample.split("\n\n+");
        double avgParagraphLength = 0;
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {avgParagraphLength += trimmed.length();}
        }
        int nonEmptyParagraphs = (int) Arrays.stream(paragraphs).filter(p -> !p.trim().isEmpty()).count();
        if (nonEmptyParagraphs > 0) {
            avgParagraphLength /= nonEmptyParagraphs;
            if (avgParagraphLength > 200) {noteScore += 15;}
            else if (avgParagraphLength > 100) {noteScore += 8;}
        }

        for (Pattern connPattern : CONNECTIVE_PATTERNS) {
            noteScore += countMatches(sample, connPattern) * 5;
        }

        int headingPattern = countMatches(sample, HEADING_PATTERN);
        if (headingPattern >= 2) {noteScore += 15;}

        return questionScore > noteScore;
    }

    private int countMatches(String text, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private String callReactAgentForText(String prompt, Long userId) {
        try {
            ReactAgent simpleReactAgent = reactAgentFactory.getSimpleReactAgent(userId != null ? userId : 0L);
            String threadId = "simple_call:" + (userId != null ? userId : 0L) + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            StringBuilder result = new StringBuilder();
            simpleReactAgent.stream(prompt, config)
                    .filter(output -> output instanceof StreamingOutput<?>)
                    .map(output -> (StreamingOutput<?>) output)
                    .doOnNext(streamingOutput -> {
                        Message message = streamingOutput.message();
                        if (message != null && message.getText() != null) {
                            result.append(message.getText());
                        }
                    })
                    .blockLast(Duration.ofSeconds(120));
            
            return result.toString();
        } catch (Exception e) {
            log.error("[ReactAgent文本调用] 调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }

    private List<AiRubric> callForStructuredOutput(String prompt, Long userId) {
        ChatModel chatModel;
        if (userId != null && userApiKeyService.getApiKeyMode(userId) == ApiKeyMode.PERSONAL) {
            String apiKey = userApiKeyService.getPlainApiKey(userId);
            ModelsEntity mainModel = userApiKeyService.getMainModel(userId);
            chatModel = dashScopeModelFactory.createChatModel(apiKey, mainModel);
        } else {
            chatModel = dashScopeModelFactory.createLocalChatModel();
        }
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        BeanOutputConverter<List<AiRubric>> converter = new BeanOutputConverter<>(
                new ParameterizedTypeReference<>() {});

        String content = chatClient.prompt()
                .user(prompt + "\n\n" + converter.getFormat())
                .call()
                .content();

        String jsonStr = content.trim();
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }

        List<AiRubric> rubrics = converter.convert(jsonStr);
        AiCommonSupport.sanitizeRubricMath(rubrics);
        return rubrics;
    }

}
