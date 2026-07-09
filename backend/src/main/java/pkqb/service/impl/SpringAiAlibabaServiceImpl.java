package pkqb.service.impl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.Agent;
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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.common.RateLimitConstants;
import pkqb.config.DashScopeModelFactory;
import pkqb.config.ReactAgentFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.pojo.dto.AiRubric;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.mapper.FileMapper;
import pkqb.pojo.entity.FileEntity;
import pkqb.service.MinioService;
import pkqb.service.NotificationService;
import pkqb.service.OssService;
import pkqb.service.RateLimitService;
import pkqb.service.SpringAiAlibabaService;
import pkqb.service.UserApiKeyService;
import pkqb.service.DashScopeRerankService;
import pkqb.service.strategy.QuestionExtractContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpringAiAlibabaServiceImpl implements SpringAiAlibabaService {
    private final VectorStore vectorStore;
    private final RedisTemplate<String,Object> redisTemplate;
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
    private final OssService ossService;
    private final DashScopeRerankService rerankService;
    
    private static final ExecutorService AI_EXECUTOR = new ThreadPoolExecutor(
            6, 6, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "ai-executor-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );

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
                                      RedisTemplate<String,Object> redisTemplate,
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
                                      OssService ossService,
                                      DashScopeRerankService rerankService) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
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
        this.ossService = ossService;
        this.rerankService = rerankService;
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        AI_EXECUTOR.shutdown();
        try {
            if (!AI_EXECUTOR.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                AI_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            AI_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Result<String> addDocuments(MultipartFile file, Long userId) {
        log.info("[知识库-文件上传] 开始处理文件: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String objectKey = "knowledge/" + userId + "/" + UUID.randomUUID() + extension;
        Long fileRecordId = null;

        try {
            // 1. 频率限制检查
            Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_KNOWLEDGE, RateLimitConstants.KNOWLEDGE_LIMIT);
            if (limitCheck != null) {
                log.warn("[知识库-文件上传] 用户 {} 超过每日限制", userId);
                return Result.error(limitCheck.getMessage());
            }

            // 2. 文档解析
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
            Result<String> result = processDocumentsContent(content, "文件上传", userId);
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

    private Result<String> processDocumentsContent(String content, String operationType, Long userId) {
        log.info("[知识库-{}] 开始分割文档", operationType);
        
        List<Document> documents = Arrays.stream(content.split("\n\n+"))
                .filter(s -> !s.trim().isEmpty())
                .map(Document::new)
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
        try {
            String userMsgJson = objectMapper.writeValueAsString(Map.of(
                    "messageType", "USER",
                    "text", query,
                    "timestamp", System.currentTimeMillis()
            ));
            stringRedisTemplate.opsForList().rightPush(historyKey, userMsgJson);
            stringRedisTemplate.expire(historyKey, 7, java.util.concurrent.TimeUnit.DAYS);
            log.info("[RAG查询] 保存用户消息到历史记录, key={}", historyKey);
        } catch (Exception e) {
            log.warn("[RAG查询] 保存用户消息失败", e);
        }
        
        StringBuilder aiResponse = new StringBuilder();
        
        try {
            log.info("[RAG查询] 开始ReactAgent流式查询，sessionId={}", sessionId);
            
            String threadId = "rag:" + userId + ":" + sessionId;
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            ReactAgent ragReactAgent = reactAgentFactory.getRagReactAgent(userId);

            return ragReactAgent.stream(query, config)
                    .filter(output -> output instanceof StreamingOutput<?>)
                    .map(output -> (StreamingOutput<?>) output)
                    .flatMap(streamingOutput -> {
                        OutputType type = streamingOutput.getOutputType();
                        Message message = streamingOutput.message();
                        if(type == OutputType.AGENT_MODEL_STREAMING){
                            if(message instanceof AssistantMessage){
                                // 检查是否为 Thinking 消息
                                Object reasoningContent = message.getMetadata().get("reasoningContent");
                                if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                    aiResponse.append(reasoningContent);
                                    try {
                                        return Mono.just(objectMapper.writeValueAsString(reasoningContent));
                                    } catch (JsonProcessingException e) {
                                        log.warn("[RAG查询] 序列化推理内容失败，使用原始内容", e);
                                        return Mono.just(reasoningContent.toString().replace("\n", "\\n").replace("\r", "\\r"));

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
                        log.info("[RAG查询] 流式查询完成，sessionId={}", sessionId);

                        if (aiResponse.length() > 0) {
                            try {
                                String aiMsgJson = objectMapper.writeValueAsString(Map.of(
                                        "messageType", "ASSISTANT",
                                        "text", aiResponse.toString(),
                                        "timestamp", System.currentTimeMillis()
                                ));
                                stringRedisTemplate.opsForList().rightPush(historyKey, aiMsgJson);
                                stringRedisTemplate.expire(historyKey, 7, java.util.concurrent.TimeUnit.DAYS);
                                log.info("[RAG查询] 保存AI回复到历史记录, key={}, length={}", historyKey, aiResponse.length());
                            } catch (Exception e) {
                                log.warn("[RAG查询] 保存AI回复失败", e);
                            }
                        }
                    })
                    .doOnError(e -> {
                        log.error("[RAG查询] 流式查询异常，sessionId={}", sessionId, e);
                        if (isApiKeyOrModelError(e)) {
                            throw new RuntimeException("API Key或模型名称有误，请核验");
                        }
                    });
        } catch (Exception e) {
            log.error("[RAG查询] RAG查询时发生错误: ", e);
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
        try {
            String userMsgJson = objectMapper.writeValueAsString(Map.of(
                    "messageType", "USER",
                    "text", query,
                    "timestamp", System.currentTimeMillis()
            ));
            stringRedisTemplate.opsForList().rightPush(historyKey, userMsgJson);
            stringRedisTemplate.expire(historyKey, 7, java.util.concurrent.TimeUnit.DAYS);
            log.info("[AI对话] 保存用户消息到历史记录, key={}", historyKey);
        } catch (Exception e) {
            log.warn("[AI对话] 保存用户消息失败", e);
        }
        
        StringBuilder aiResponse = new StringBuilder();
        
        try {
            String threadId = "chat:" + userId + ":" + sessionId;
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            log.info("[AI对话] 开始ReactAgent流式查询，sessionId={}, threadId={}", sessionId, threadId);
            
            ReactAgent chatReactAgent = reactAgentFactory.getChatReactAgent(userId);
            
            return chatReactAgent.stream(query, config)
                    .filter(output -> output instanceof StreamingOutput<?>)
                    .map(output -> (StreamingOutput<?>) output)
                    .flatMap(streamingOutput -> {
                        OutputType type = streamingOutput.getOutputType();
                        Message message = streamingOutput.message();
                        if(type == OutputType.AGENT_MODEL_STREAMING){
                            if(message instanceof AssistantMessage){
                                // 检查是否为 Thinking 消息
                                Object reasoningContent = message.getMetadata().get("reasoningContent");
                                if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                   aiResponse.append(reasoningContent);
                                    try {
                                        return Mono.just(objectMapper.writeValueAsString(reasoningContent));
                                    } catch (JsonProcessingException e) {
                                        log.warn("[AI对话] 序列化推理内容失败，使用原始内容", e);
                                        return Mono.just(reasoningContent.toString().replace("\n", "\\n").replace("\r", "\\r"));

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
                        log.info("[AI对话] 流式查询完成，sessionId={}", sessionId);
                        
                        if (aiResponse.length() > 0) {
                            try {
                                String aiMsgJson = objectMapper.writeValueAsString(Map.of(
                                        "messageType", "ASSISTANT",
                                        "text", aiResponse.toString(),
                                        "timestamp", System.currentTimeMillis()
                                ));
                                stringRedisTemplate.opsForList().rightPush(historyKey, aiMsgJson);
                                stringRedisTemplate.expire(historyKey, 7, java.util.concurrent.TimeUnit.DAYS);
                                log.info("[AI对话] 保存AI回复到历史记录, key={}, length={}", historyKey, aiResponse.length());
                            } catch (Exception e) {
                                log.warn("[AI对话] 保存AI回复失败", e);
                            }
                        }
                    })
                    .doOnError(e -> {
                        log.error("[AI对话] 流式查询异常，sessionId={}", sessionId, e);
                        if (isApiKeyOrModelError(e)) {
                            throw new RuntimeException("API Key或模型名称有误，请核验");
                        }
                    });
        } catch (Exception e) {
            log.error("[AI对话] 查询时发生错误", e);
            return Flux.error(e);
        }
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
    public Result<String> addRubric(String content) {
        log.info("[题目解析] 收到字符串内容解析，长度={}", content != null ? content.length() : 0);
        if (content == null || content.trim().isEmpty()) {
            return Result.error("内容不能为空");
        }
        
        return parseRubricContent(content, null);
    }

    private Result<String> parseRubricContent(String content, Long userId) {
        log.info("[题目解析] 内容长度={}", content.length());
        
        String truncatedContent = content.length() > RUBRIC_TRUNCATE_LENGTH ? content.substring(0, RUBRIC_TRUNCATE_LENGTH) : content;
        String prompt = getQuestionAnalysisPrompt(truncatedContent);
        
        try {
            log.info("[题目解析] 开始调用AI解析题目（结构化输出）");
            List<AiRubric> aiRubrics = callForStructuredOutput(prompt, userId);
            String jsonStr = objectMapper.writeValueAsString(aiRubrics);
            log.info("[题目解析] AI解析完成");
            return Result.success(jsonStr);
        } catch (Exception e) {
            log.warn("[题目解析] AI解析超时或失败: {}，开始执行兜底策略", e.getMessage());
        }
        
        List<Map<String, Object>> maps = questionExtractContext.extractQuestions(content);
        if (maps.isEmpty()) {
            log.warn("[题目解析] 兜底解析也失败，内容可能不符合预期格式");
            return Result.error("解析题目失败，请检查内容格式");
        }
        try {
            String jsonStr = objectMapper.writeValueAsString(maps);
            log.info("[题目解析] 兜底解析完成");
            return Result.success(jsonStr);
        } catch (Exception ex) {
            log.error("[题目解析] 兜底解析JSON序列化失败: {}", ex.getMessage());
            return Result.error("解析题目失败");
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
    public Result<List<AiRubric>> handleRubricFile(MultipartFile file, Long userId, Integer modelType) {
        log.info("[题目文件解析-AI] 开始处理文件: {}, 大小: {} bytes, userId={}, modelType={}",
                file.getOriginalFilename(), file.getSize(), userId, modelType);
        try {
            Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RUBRIC, RateLimitConstants.RUBRIC_LIMIT);
            if (limitCheck != null) {
                return Result.error(limitCheck.getMessage());
            }

            if (modelType != null && modelType == 2) {
                // 用户指定视觉模型
                return handleRubricFileWithVision(file, userId);
            } else if (modelType != null && modelType == 1) {
                // 用户指定纯文本模型
                return handleRubricFileWithText(file, userId);
            } else {
                // 未指定：自动选择（PDF→视觉模型，其他→纯文本）
                String originalFilename = file.getOriginalFilename();
                boolean isPdf = originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");
                if (isPdf) {
                    return handleRubricFileWithVision(file, userId);
                } else {
                    return handleRubricFileWithText(file, userId);
                }
            }

        } catch (Exception e) {
            log.error("[题目文件解析-AI] 处理文件时发生错误: ", e);
            return Result.error("处理文件失败: " + e.getMessage());
        }
    }

    /**
     * 使用视觉模型解析PDF文件：PDF页面渲染为图片→上传OSS→URL传给视觉模型→结构化输出+嵌入图片提取映射
     */
    private Result<List<AiRubric>> handleRubricFileWithVision(MultipartFile file, Long userId) {
        try {
            log.info("[题目文件解析-视觉] 使用视觉模型解析PDF文件");
            byte[] fileBytes = file.getBytes();

            // Step 1+2: 一次打开PDF，同时渲染页面图片和提取嵌入图片，上传OSS
                List<String> pageImageUrls;
                List<ExtractedImage> extractedImages;
                try (PDDocument document = Loader.loadPDF(fileBytes)) {
                pageImageUrls = renderPdfPagesToOss(document, userId);
                extractedImages = extractImagesFromPdf(document, userId);
            }

            if (pageImageUrls.isEmpty()) {
                log.warn("[题目文件解析-视觉] PDF页面渲染失败，降级到纯文本模式");
                return handleRubricFileWithText(file, userId);
            }
            log.info("[题目文件解析-视觉] PDF渲染为 {} 张页面图片，提取到 {} 张嵌入图片",
                    pageImageUrls.size(), extractedImages.size());

            // Step 3: 将页面图片URL发给视觉模型，获取结构化题目
            List<AiRubric> aiRubrics = callVisionModelForQuestions(pageImageUrls, extractedImages, userId);
            if (aiRubrics == null || aiRubrics.isEmpty()) {
                log.warn("[题目文件解析-视觉] 视觉模型返回为空，降级到纯文本模式");
                return handleRubricFileWithText(file, userId);
            }

            // Step 4: 将图片编号映射为实际URL
            mapImageReferencesToUrls(aiRubrics, extractedImages);

            log.info("[题目文件解析-视觉] 解析完成，共提取 {} 道题目", aiRubrics.size());
            notificationService.notifyRubricParseComplete(userId, "AI视觉解析");
            return Result.success(aiRubrics);
        } catch (Exception e) {
            log.error("[题目文件解析-视觉] 视觉识别失败: {}，降级到纯文本模式", e.getMessage());
            // OssService 暂无 delete 方法，已上传的 OSS 页面/嵌入图片无法清理，记录日志
            log.warn("[题目文件解析-视觉] 降级到纯文本模式，已上传的OSS资源可能孤立，待后续清理");
            return handleRubricFileWithText(file, userId);
        }
    }

    /**
     * 将PDF每页渲染为图片并上传到OSS
     */
    private List<String> renderPdfPagesToOss(PDDocument document, Long userId) {
        List<String> urls = new ArrayList<>();
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(pageImage, "png", baos);
                byte[] imageBytes = baos.toByteArray();

                String objectKey = "question-image/" + userId + "/page-" + i + "-" + UUID.randomUUID() + ".png";
                String url = ossService.upload(objectKey, imageBytes, "image/png");
                urls.add(url);
            }
        } catch (Exception e) {
            log.error("[PDF页面渲染] 渲染PDF页面失败: {}", e.getMessage());
        }
        return urls;
    }

    /**
     * 调用视觉模型，传入页面图片URL列表，获取结构化题目
     */
    private List<AiRubric> callVisionModelForQuestions(List<String> pageImageUrls, List<ExtractedImage> extractedImages, Long userId) {
        try {
            // 构建图片编号提示
            StringBuilder imageRef = new StringBuilder();
            if (!extractedImages.isEmpty()) {
                imageRef.append("\n\n文档中包含以下嵌入图片：\n");
                for (ExtractedImage img : extractedImages) {
                    imageRef.append("- 图片").append(img.index).append("\n");
                }
            }

            String promptText = """
                你是一个专业的题目解析专家。以下是一份试卷的每一页图片，请从中提取所有题目，输出为JSON数组。

                要求：
                1. 每道题包含：question（题目内容）、questionType（题型）、options（选项，没有则为空数组）、answer（答案）、explanation（解析）、calculationSteps（计算步骤，没有则为空数组）、resources（图片资源）
                2. resources格式为[{"url":"图片N","type":"资源类型","label":"标签"}]，N为图片编号
                   type必须为以下之一：
                   - question_image：题目本身的配图
                   - option_image：选项配图（必须同时填写label为对应选项字母，如"A"、"B"、"C"、"D"）
                   - answer_image：答案中的配图
                   - explanation_image：解析中的配图
                3. 如果题目中包含图片，在question字段中用[图片N]标记图片位置，同时在resources中添加对应引用
                4. 严格输出JSON数组，不要输出其他内容
                """ + imageRef;

            // 获取视觉模型
            String apiKey = userApiKeyService.hasUserOwnApiKey(userId) ? userApiKeyService.getPlainApiKey(userId) : null;
            ModelsEntity visionModel = userApiKeyService.getVisionModel(userId);
            String modelName = visionModel != null ? visionModel.getModelName() : null;
            ChatModel chatModel = dashScopeModelFactory.createVisionChatModel(
                    apiKey != null ? apiKey : dashScopeModelFactory.getDefaultApiKey(),
                    modelName);

            // 构建包含多张图片的请求
            var userSpec = ChatClient.builder(chatModel).build().prompt()
                    .user(u -> {
                        u.text(promptText);
                        for (String url : pageImageUrls) {
                            try {
                                u.media(org.springframework.util.MimeTypeUtils.IMAGE_PNG,
                                        new org.springframework.core.io.UrlResource(url));
                            } catch (java.net.MalformedURLException e) {
                                log.warn("[题目文件解析-视觉] 图片URL格式错误: {}", url);
                            }
                        }
                    });

            String response = userSpec.call().content();
            log.info("[题目文件解析-视觉] 视觉模型返回，响应长度: {}", response != null ? response.length() : 0);

            // 解析JSON响应
            if (response == null || response.isEmpty()) return null;
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }
            int start = jsonStr.indexOf("[");
            int end = jsonStr.lastIndexOf("]");
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
            return objectMapper.readValue(jsonStr,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AiRubric.class));
        } catch (Exception e) {
            log.error("[题目文件解析-视觉] 视觉模型调用失败: {}", e.getMessage());
            return null;
        }
    }

    private static class ExtractedImage {
        int index;
        String url;
        int pageIndex;

        ExtractedImage(int index, String url, int pageIndex) {
            this.index = index;
            this.url = url;
            this.pageIndex = pageIndex;
        }
    }

    private List<ExtractedImage> extractImagesFromPdf(PDDocument document, Long userId) {
        List<ExtractedImage> images = new ArrayList<>();
        Set<String> uploadedHashes = new HashSet<>();
        int pageIndex = 0;
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) { pageIndex++; continue; }

            for (COSName name : resources.getXObjectNames()) {
                try {
                    PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof PDImageXObject pdImage) {
                        BufferedImage bufferedImage = pdImage.getImage();
                        if (bufferedImage.getWidth() < 50 || bufferedImage.getHeight() < 50) continue;

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bufferedImage, "png", baos);
                        byte[] imageBytes = baos.toByteArray();

                        String hash = DigestUtils.md5Hex(imageBytes);
                        if (uploadedHashes.contains(hash)) continue;
                        uploadedHashes.add(hash);

                        // 上传到OSS（公网可达，供DashScope模型和前端访问）
                        String objectKey = "question-image/" + userId + "/" + UUID.randomUUID() + ".png";
                        String url = ossService.upload(objectKey, imageBytes, "image/png");

                        // 临时用-1作为index，排序后重新编号
                        images.add(new ExtractedImage(-1, url, pageIndex));
                    }
                } catch (Exception e) {
                    log.debug("[PDF图片提取] 跳过无法处理的图片: {}", e.getMessage());
                }
            }
            pageIndex++;
        }

        // 按页码排序，同页内保持PDFBox遍历顺序
        images.sort(Comparator.comparingInt(a -> a.pageIndex));

        // 重新编号
        for (int i = 0; i < images.size(); i++) {
            images.get(i).index = i;
        }

        return images;
    }

    private void mapImageReferencesToUrls(List<AiRubric> aiRubrics, List<ExtractedImage> extractedImages) {
        Map<Integer, String> indexToUrl = new HashMap<>();
        for (ExtractedImage img : extractedImages) {
            indexToUrl.put(img.index, img.url);
        }

        for (AiRubric rubric : aiRubrics) {
            if (rubric.getResources() == null) continue;
            for (AiRubric.AiResource resource : rubric.getResources()) {
                // 规范化 type 字段：AI 可能返回非标准的 type，统一为前端期望的值
                if (resource.getType() != null) {
                    String type = resource.getType().toLowerCase();
                    if ("image".equals(type) || "img".equals(type)) {
                        // 未分类的图片，根据 label 推断类型
                        if (resource.getLabel() != null && resource.getLabel().matches("^[A-Da-d]$")) {
                            resource.setType("option_image");
                            resource.setLabel(resource.getLabel().toUpperCase());
                        } else {
                            resource.setType("question_image");
                        }
                    } else {
                        // 确保已知类型的小写变体也能匹配，统一为标准命名
                        switch (type) {
                            case "question_image": resource.setType("question_image"); break;
                            case "option_image": resource.setType("option_image"); break;
                            case "answer_image": resource.setType("answer_image"); break;
                            case "explanation_image": resource.setType("explanation_image"); break;
                            default: resource.setType("question_image"); break;
                        }
                    }
                }

                if (resource.getUrl() != null && resource.getUrl().startsWith("图片")) {
                    try {
                        String numStr = resource.getUrl().replace("图片", "").trim();
                        int idx = Integer.parseInt(numStr);
                        String actualUrl = indexToUrl.get(idx);
                        if (actualUrl != null) {
                            resource.setUrl(actualUrl);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    /**
     * 使用纯文本提取流程（原有逻辑）
     */
    private Result<List<AiRubric>> handleRubricFileWithText(MultipartFile file, Long userId) {
        try {
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
                List<AiRubric> aiRubrics = callForStructuredOutput(prompt, userId);

                log.info("[题目文件解析-AI] AI解析完成");

                if (aiRubrics == null || aiRubrics.isEmpty()) {
                    log.warn("[题目文件解析-AI] AI返回为空");
                    return Result.error("AI解析结果为空");
                }

                log.info("[题目文件解析-AI] 解析完成，共提取 {} 道题目", aiRubrics.size());
                notificationService.notifyRubricParseComplete(userId, "AI文本解析");
                return Result.success(aiRubrics);
            } catch (Exception e) {
                log.error("[题目文件解析-AI] AI解析失败: {}", e.getMessage(), e);
                notificationService.notifyRubricParseFailed(userId, e.getMessage());
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
            
            List<Integer> sortedIndices = messageIndices.stream()
                    .sorted(java.util.Collections.reverseOrder())
                    .collect(Collectors.toList());
            
            for (Integer index : sortedIndices) {
                if (index < 0 || index >= messages.size()) {
                    log.warn("[删除消息] 索引超出范围: {}", index);
                    continue;
                }
                String messageToDelete = messages.get(index);
                stringRedisTemplate.opsForList().remove(sessionKey, 1, messageToDelete);
            }
            
            log.info("[删除消息] 成功删除 {} 条消息", messageIndices.size());
            return Result.success(true);
        } catch (Exception e) {
            log.error("[删除消息] 删除消息时发生错误: ", e);
            return Result.error("删除消息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<String> aiSolveQuestion(String questionText, String questionType, String optionsJson, String generateType, Long userId) {
        log.info("[AI解答] 开始生成，userId={}, questionType={}, generateType={}", userId, questionType, generateType);
        
        if (questionText == null || questionText.trim().isEmpty()) {
            return Result.error("题目内容不能为空");
        }
        
        if ("all".equals(generateType)) {
            return aiSolveAll(questionText, questionType, optionsJson, userId);
        }
        
        String prompt = buildAiSolvePromptWithRag(questionText, questionType, optionsJson, generateType, userId);
        if (prompt == null) {
            return Result.error("无效的生成类型");
        }
        
        try {
            Agent agent = reactAgentFactory.getMultiModelReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            StringBuilder result = new StringBuilder();
            Optional<OverAllState> invoke = agent.invoke(prompt, config);

            if (invoke.isPresent()) {
                OverAllState state = invoke.get();
                // 访问第最后一个Agent（也就是总结模型）的输出
                state.value("final_answer").ifPresent(finalAnswer -> {
                    if (finalAnswer instanceof AssistantMessage) {
                        String text = ((AssistantMessage) finalAnswer).getText();
                        result.append(text);
                    }
                });
            }
           /* agent.stream(prompt, config)
                    .filter(output -> output instanceof StreamingOutput<?>)
                    .map(output -> (StreamingOutput<?>) output)
                    .doOnNext(streamingOutput -> {
                        Message message = streamingOutput.message();
                        if (message != null && message.getText() != null) {
                            // 关键修改：判断是否来自 summary_agent
                            // 这里的 "summary_agent" 需要和 ReactAgentUserConfig 中定义的 name 一致
                            if ("summary_agent".equals(streamingOutput.agent())) {
                                result.setLength(0); // 清空之前可能收集到的草稿
                                result.append(message.getText());
                            }
                        }
                    })
                    .blockLast(Duration.ofSeconds(120));*/
            
            log.info("[AI解答] 生成完成，generateType={}", generateType);
            return Result.success(result.toString());
        } catch (Exception e) {
            log.error("[AI解答] 生成失败: ", e);
            if (isApiKeyOrModelError(e)) {
                return Result.error("API Key或模型名称有误，请核验");
            }
            return Result.error("AI解答失败: " + e.getMessage());
        }
    }
    
    private Result<String> aiSolveAll(String questionText, String questionType, String optionsJson, Long userId) {
        try {
            String ragContext = searchVectorStore(questionText, userId);
            
            if (isSimpleQuestion(questionText, questionType)) {
                log.info("[AI解答] 简单问题，使用单次调用模式");
                return aiSolveAllInOne(questionText, questionType, optionsJson, ragContext, userId);
            } else {
                log.info("[AI解答] 复杂问题，使用并行调用模式");
                return aiSolveAllParallel(questionText, questionType, optionsJson, ragContext, userId);
            }
        } catch (Exception e) {
            log.error("[AI解答] 全部生成失败: ", e);
            if (isApiKeyOrModelError(e)) {
                return Result.error("API Key或模型名称有误，请核验");
            }
            return Result.error("AI解答失败: " + e.getMessage());
        }
    }
    
    private boolean isSimpleQuestion(String questionText, String questionType) {
        if (questionText == null) return false;
        
        if ("multiple_choice".equals(questionType)) {
            return false;
        }
        
        if ("single_choice".equals(questionType) || "true_false".equals(questionType)) {
            return true;
        }
        
        if (questionText.length() < 50 && !"calculation".equals(questionType)) {
            return true;
        }
        
        return false;
    }
    
    private Result<String> aiSolveAllInOne(String questionText, String questionType, String optionsJson, String ragContext, Long userId) {
        try {
            String prompt = buildAllInOnePrompt(questionText, questionType, optionsJson, ragContext);
            
            Agent agent = reactAgentFactory.getMultiModelReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            StringBuilder result = new StringBuilder();
            Optional<OverAllState> invoke = agent.invoke(prompt, config);
            if (invoke.isPresent()) {
                OverAllState state = invoke.get();
                state.value("final_answer").ifPresent(finalAnswer -> {
                    if (finalAnswer instanceof AssistantMessage) {
                        String text = ((AssistantMessage) finalAnswer).getText();
                        result.append(text);
                    }
                });
            }
            
            String aiResponse = result.toString().trim();
            Map<String, String> results = parseAllInOneResponse(aiResponse, questionType);
            
            String jsonResult = objectMapper.writeValueAsString(results);
            log.info("[AI解答] 单次调用完成");
            return Result.success(jsonResult);
        } catch (Exception e) {
            log.error("[AI解答] 单次调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }
    
    private String buildAllInOnePrompt(String questionText, String questionType, String optionsJson, String ragContext) {
        StringBuilder prompt = new StringBuilder();
        
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("以下是从知识库中检索到的相关内容，请参考这些内容来回答问题：\n\n");
            prompt.append(ragContext);
            prompt.append("\n\n---\n\n");
        }
        
        String typeDesc = getTypeDescription(questionType);
        prompt.append("请根据以下").append(typeDesc).append("，一次性生成答案和解析");
        if ("calculation".equals(questionType)) {
            prompt.append("以及计算步骤");
        }
        prompt.append("。\n\n");
        
        if (optionsJson != null && !optionsJson.isEmpty() && !optionsJson.equals("[]")) {
            prompt.append("选项：\n").append(optionsJson).append("\n\n");
        }
        
        prompt.append("题目：\n").append(questionText).append("\n\n");
        
        prompt.append("请严格按照以下JSON格式返回，不要包含任何其他内容：\n");
        prompt.append("{\n");
        
        if ("true_false".equals(questionType)) {
            prompt.append("  \"answer\": \"正确或错误\",\n");
        } else if ("single_choice".equals(questionType) || "multiple_choice".equals(questionType)) {
            prompt.append("  \"answer\": \"选项字母，如A或A,B,C\",\n");
        } else if ("short_answer".equals(questionType)) {
            prompt.append("  \"answer\": \"答案要点\",\n");
        } else if ("calculation".equals(questionType)) {
            prompt.append("  \"answer\": \"最终计算结果\",\n");
        }
        
        prompt.append("  \"explanation\": \"题目解析，150字以内\"\n");
        
        if ("calculation".equals(questionType)) {
            prompt.append(",\n  \"steps\": \"计算步骤，每步用换行分隔\"");
        }
        
        prompt.append("\n}\n\n");
        prompt.append("注意：只返回JSON，不要有任何其他文字。");
        
        return prompt.toString();
    }
    
    private Map<String, String> parseAllInOneResponse(String aiResponse, String questionType) {
        Map<String, String> results = new HashMap<>();
        results.put("answer", "");
        results.put("explanation", "");
        results.put("steps", "");
        
        if (aiResponse == null || aiResponse.isEmpty()) {
            log.warn("[AI解答] AI返回为空");
            return results;
        }
        
        String jsonStr = aiResponse;
        if (jsonStr.contains("```")) {
            jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        
        int start = jsonStr.indexOf("{");
        int end = jsonStr.lastIndexOf("}");
        if (start >= 0 && end > start) {
            jsonStr = jsonStr.substring(start, end + 1);
        }
        
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            
            if (node.has("answer")) {
                results.put("answer", node.get("answer").asText().trim());
            }
            if (node.has("explanation")) {
                results.put("explanation", node.get("explanation").asText().trim());
            }
            if (node.has("steps")) {
                results.put("steps", node.get("steps").asText().trim());
            }
            
            if (results.get("answer").isEmpty() && results.get("explanation").isEmpty()) {
                log.warn("[AI解答] JSON解析后内容为空，原始响应: {}", aiResponse.substring(0, Math.min(100, aiResponse.length())));
            }
        } catch (Exception e) {
            log.warn("[AI解答] JSON解析失败，尝试提取内容: {}, 原始响应前100字符: {}", 
                    e.getMessage(), aiResponse.substring(0, Math.min(100, aiResponse.length())));
            
            if (aiResponse.contains("答案") || aiResponse.contains("正确") || aiResponse.contains("错误")) {
                results.put("answer", aiResponse);
            } else {
                results.put("answer", aiResponse);
                results.put("explanation", "AI返回格式异常，请重新生成");
            }
        }
        
        return results;
    }
    
    private Result<String> aiSolveAllParallel(String questionText, String questionType, String optionsJson, String ragContext, Long userId) {
        try {
            CompletableFuture<String> answerFuture = CompletableFuture.supplyAsync(() -> {
                String prompt = buildAiSolvePromptWithRagContext(questionText, questionType, optionsJson, "answer", ragContext);
                if (prompt == null) return "";
                String result = callMultiModelReactAgent(prompt, userId);
                return result != null ? result.trim() : "";
            }, AI_EXECUTOR);
            
            CompletableFuture<String> explanationFuture = CompletableFuture.supplyAsync(() -> {
                String prompt = buildAiSolvePromptWithRagContext(questionText, questionType, optionsJson, "explanation", ragContext);
                if (prompt == null) return "";
                String result = callMultiModelReactAgent(prompt, userId);
                return result != null ? result.trim() : "";
            }, AI_EXECUTOR);
            
            CompletableFuture<String> stepsFuture = CompletableFuture.completedFuture("");
            if ("calculation".equals(questionType)) {
                stepsFuture = CompletableFuture.supplyAsync(() -> {
                    String prompt = buildAiSolvePromptWithRagContext(questionText, questionType, optionsJson, "steps", ragContext);
                    if (prompt == null) return "";
                    String result = callMultiModelReactAgent(prompt, userId);
                    return result != null ? result.trim() : "";
                }, AI_EXECUTOR);
            }
            
            try {
                CompletableFuture.allOf(answerFuture, explanationFuture, stepsFuture).get(120, java.util.concurrent.TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("[AI解题] 并行解题超时", e);
                throw new RuntimeException("AI解题超时，请稍后重试");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("AI解题被中断");
            } catch (ExecutionException e) {
                log.error("[AI解题] 并行解题执行异常", e);
                throw new RuntimeException("AI解题执行失败: " + e.getCause().getMessage());
            }
            
            Map<String, String> results = new HashMap<>();
            results.put("answer", answerFuture.get());
            results.put("explanation", explanationFuture.get());
            results.put("steps", stepsFuture.get());
            
            String jsonResult = objectMapper.writeValueAsString(results);
            log.info("[AI解答] 并行调用完成");
            return Result.success(jsonResult);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("[AI解答] 并行调用执行异常: ", cause);
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("AI调用失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[AI解答] 并行调用被中断: ", e);
            throw new RuntimeException("AI调用被中断: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[AI解答] 并行调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }
    
    private String buildAiSolvePromptWithRagContext(String questionText, String questionType, String optionsJson, String generateType, String ragContext) {
        StringBuilder prompt = new StringBuilder();
        
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("以下是从知识库中检索到的相关内容，请参考这些内容来回答问题：\n\n");
            prompt.append(ragContext);
            prompt.append("\n\n---\n\n");
        }
        
        prompt.append("请根据以下题目内容");
        
        String typeDesc = getTypeDescription(questionType);
        prompt.append("（").append(typeDesc).append("）");
        
        if (optionsJson != null && !optionsJson.isEmpty() && !optionsJson.equals("[]")) {
            prompt.append("\n\n选项：\n").append(optionsJson);
        }
        
        prompt.append("\n\n题目：\n").append(questionText);
        
        switch (generateType) {
            case "answer":
                prompt.append("\n\n请直接给出正确答案，不要包含任何解释。");
                if ("true_false".equals(questionType)) {
                    prompt.append(" 判断题请回答:正确或错误");
                } else if ("short_answer".equals(questionType)) {
                    prompt.append(" 简答题请给出简洁的答案要点。");
                } else if ("calculation".equals(questionType)) {
                    prompt.append(" 计算题请给出最终计算结果。");
                } else {
                    prompt.append(" 选择题请直接给出选项字母（如A、B、C、D，多选用逗号分隔）。");
                }
                break;
            case "explanation":
                prompt.append("\n\n请给出这道题的详细解析，字数控制在150字以内，要求简洁明了，突出重点。");
                break;
            case "steps":
                if (!"calculation".equals(questionType)) {
                    return null;
                }
                prompt.append("\n\n请给出这道计算题的详细计算步骤，每步一行，格式如下：\n");
                prompt.append("步骤1: xxx\n步骤2: xxx\n...");
                break;
            default:
                return null;
        }
        
        return prompt.toString();
    }
    
    private String callReactAgent(String prompt, Long userId) {
        try {
            ReactAgent ragReactAgent = reactAgentFactory.getRagReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            StringBuilder result = new StringBuilder();
            ragReactAgent.stream(prompt, config)
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
            log.error("[AI解答] ReactAgent调用失败: ", e);
            return "";
        }
    }
    
    /**
     * 调用多模型Agent（使用Multi-agent模式）
     * 如果用户配置了主模型和辅助模型，则使用多模型模式
     * 否则使用单模型模式
     */
    private String callMultiModelReactAgent(String prompt, Long userId) {
        try {
            Agent agent = reactAgentFactory.getMultiModelReactAgent(userId);
            String threadId = "ai_solve:" + userId + ":" + System.currentTimeMillis();
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(threadId)
                    .build();
            
            StringBuilder result = new StringBuilder();
            Optional<OverAllState> invoke = agent.invoke(prompt, config);
            if (invoke.isPresent()) {
                OverAllState state = invoke.get();
                // 访问第最后一个Agent（也就是总结模型）的输出
                state.value("final_answer").ifPresent(finalAnswer -> {
                    if (finalAnswer instanceof AssistantMessage) {
                        String text = ((AssistantMessage) finalAnswer).getText();
                        result.append(text);
                    }
                });
            }
           /* agent.stream(prompt, config)
                    .filter(output -> output instanceof StreamingOutput<?>)
                    .map(output -> (StreamingOutput<?>) output)
                    .doOnNext(streamingOutput -> {
                        Message message = streamingOutput.message();
                        if (message != null && message.getText() != null) {
                            result.append(message.getText());
                        }
                    })
                    .blockLast(Duration.ofSeconds(120));*/
            
            return result.toString();
        } catch (Exception e) {
            log.error("[ReactAgent文本调用] 调用失败: ", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
    }
    
    private String buildAiSolvePromptWithRag(String questionText, String questionType, String optionsJson, String generateType, Long userId) {
        StringBuilder prompt = new StringBuilder();
        
        String ragContext = searchVectorStore(questionText, userId);
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("以下是从知识库中检索到的相关内容，请参考这些内容来回答问题：\n\n");
            prompt.append(ragContext);
            prompt.append("\n\n---\n\n");
        }
        
        prompt.append("请根据以下题目内容");
        
        String typeDesc = getTypeDescription(questionType);
        prompt.append("（").append(typeDesc).append("）");
        
        if (optionsJson != null && !optionsJson.isEmpty() && !optionsJson.equals("[]")) {
            prompt.append("\n\n选项：\n").append(optionsJson);
        }
        
        prompt.append("\n\n题目：\n").append(questionText);
        
        switch (generateType) {
            case "answer":
                prompt.append("\n\n请直接给出正确答案，不要包含任何解释。");
                if ("true_false".equals(questionType)) {
                    prompt.append(" 判断题请回答:正确或错误");
                } else if ("short_answer".equals(questionType)) {
                    prompt.append(" 简答题请给出简洁的答案要点。");
                } else if ("calculation".equals(questionType)) {
                    prompt.append(" 计算题请给出最终计算结果。");
                } else {
                    prompt.append(" 选择题请直接给出选项字母（如A、B、C、D，多选用逗号分隔）。");
                }
                break;
            case "explanation":
                prompt.append("\n\n请给出这道题的详细解析，字数控制在150字以内，要求简洁明了，突出重点。");
                break;
            case "steps":
                if (!"calculation".equals(questionType)) {
                    return null;
                }
                prompt.append("\n\n请给出这道计算题的详细计算步骤，每步一行，格式如下：\n");
                prompt.append("步骤1: xxx\n步骤2: xxx\n...");
                break;
            default:
                return null;
        }
        
        return prompt.toString();
    }
    
    private String searchVectorStore(String query, Long userId) {
        try {
            // 粗排：扩大召回，为精排提供更多候选
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(20)
                    .similarityThreshold(0.5)
                    .build();
            
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            if (documents == null || documents.isEmpty()) {
                log.info("[AI解答-向量检索] 未找到相关文档");
                return null;
            }
            
            log.info("[AI解答-向量检索] 粗排召回 {} 个候选文档，开始精排", documents.size());

            // 精排：使用 qwen3-rerank 重排序，取 Top5
            List<Document> rerankedDocs = rerankService.rerank(query, documents, 5);

            String result = rerankedDocs.stream()
                    .map(doc -> {
                        String content = doc.getText();
                        String source = doc.getMetadata() != null ? 
                                (String) doc.getMetadata().getOrDefault("source", "未知来源") : "未知来源";
                        return "【来源: " + source + "】\n" + content;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
            
            log.info("[AI解答-向量检索] 精排完成，保留 {} 个文档片段", rerankedDocs.size());
            return result;
        } catch (Exception e) {
            log.warn("[AI解答-向量检索] 检索失败: {}", e.getMessage());
            return null;
        }
    }
    
    private String getTypeDescription(String questionType) {
        if (questionType == null) return "题目";
        switch (questionType) {
            case "single_choice": return "单选题";
            case "multiple_choice": return "多选题";
            case "true_false": return "判断题";
            case "short_answer": return "简答题";
            case "calculation": return "计算题";
            default: return questionType;
        }
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

        for (String word : QUESTION_WORDS) {
            questionScore += countMatches(sample, word) * 3;
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

        for (String conn : CONNECTIVES) {
            noteScore += countMatches(sample, conn) * 5;
        }

        int headingPattern = countMatches(sample, HEADING_PATTERN);
        if (headingPattern >= 2) {noteScore += 15;}

        return questionScore > noteScore;
    }

    private int countMatches(String text, String regex) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {count++;}
        return count;
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
            String modelName = mainModel != null ? mainModel.getModelName() : null;
            chatModel = dashScopeModelFactory.createChatModel(apiKey, modelName);
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

        return converter.convert(jsonStr);
    }

    private boolean isApiKeyOrModelError(Throwable e) {
        if (e == null) return false;
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("invalid api") 
                || lowerMessage.contains("invalid api-key")
                || lowerMessage.contains("invalid apikey")
                || lowerMessage.contains("invalid key")
                || lowerMessage.contains("api key") && lowerMessage.contains("invalid")
                || lowerMessage.contains("authentication")
                || lowerMessage.contains("unauthorized")
                || lowerMessage.contains("401")
                || lowerMessage.contains("403")
                || lowerMessage.contains("model") && lowerMessage.contains("not found")
                || lowerMessage.contains("model") && lowerMessage.contains("does not exist")
                || lowerMessage.contains("quota")
                || lowerMessage.contains("rate limit");
    }
}
