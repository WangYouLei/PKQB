package pkqb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.common.RateLimitConstants;
import pkqb.config.ChatClientFactory;
import pkqb.enums.RubricEnum;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.pojo.dto.AiRubric;
import pkqb.service.RateLimitService;
import pkqb.service.ChatMemoryService;
import pkqb.service.SpringAiAlibabaService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
@Slf4j
public class SpringAiAlibabaServiceImpl implements SpringAiAlibabaService {
    private final VectorStore vectorStore;
    private final ChatClientFactory chatClientFactory;
    private final RedisTemplate<String,Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final ChatMemoryService chatMemoryService;

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

            1.必须以纯JSON数组格式返回，不要包含任何其他文字或说明。直接返回JSON数组，不要用对象包裹。
            2.从文档内容中提取信息，文档中有的答案和解析就提取。
            3.如果文档中没有答案、解析或计算步骤，请根据题目内容自行生成合理的答案、解析和计算步骤。
               - 单选题/多选题：根据题目内容推断正确答案，简要题目解析
               - 填空题：根据题目内容推断正确答案
               - 判断题：根据题目内容判断对错，给出解析
               - 简答题：给出合理的答案要点
               - 计算题：给出正确答案和详细的计算步骤
           
            JSON格式要求（直接返回数组，不要用对象包裹）：
            [
                {
                    "question": "题目内容",
                    "questionType": "题型代码",
                    "options": ["选项A", "选项B", "选项C", "选项D"],
                    "answer": "正确答案，多选题用逗号分隔如：A,B,C；简答题和计算题直接写答案内容；",
                    "explanation": "题目解析，字数控制在100字以内并且在不影响表达的情况越少越好",
                    "calculationSteps": ["步骤1", "步骤2", "步骤3"]
                }
            ]

            文本内容：
            %s
            """;
    
    private String getQuestionAnalysisPrompt(String content) {
        return String.format(QUESTION_ANALYSIS_PROMPT_TEMPLATE, TYPE_DESC, content);
    }

    private static final Pattern QUESTION_NUMBER_PATTERN = Pattern.compile("^(\\d+|[（(]\\d+[）)]|[（(][A-Z][）)]|\\([A-Z]\\))([.、.．])\\s*(.+)");
    private static final Pattern ANSWER_IN_BRACKETS = Pattern.compile(".*\\(([A-D]+)\\)\\s*$");
    private static final Pattern MULTI_ANSWER_NO_BRACKETS = Pattern.compile(".*[？?]?\\s*([A-D]{2,})\\s*$");
    private static final Pattern SINGLE_ANSWER = Pattern.compile(".*[？?]?\\s*([A-D])\\s*$");
    private static final Pattern OPTION_PATTERN = Pattern.compile("^([A-D])[.、、]\\s*(.+)");
    private static final Pattern MULTI_OPTION_LINE = Pattern.compile("([A-D])[.、]\\s*([^A-D]+?)(?=\\s+[A-D][.、]|$)");
    private static final Pattern ANSWER_LINE = Pattern.compile("(答案|Answer|参考答案)[:：]\\s*(.+)");
    private static final Pattern EXPLANATION_LINE = Pattern.compile("(解析|Explanation|解析如下)[:：]\\s*(.+)");
    private static final Pattern CALCULATION_STEP = Pattern.compile("(步骤\\d*|第\\d*步)[:：.]?\\s*(.+)");

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

    private static final int RUBRIC_TRUNCATE_LENGTH = 3000;
    private static final int RUBRIC_DETECT_LENGTH = 1500;
    private static final int NOTE_DETECT_LENGTH = 1000;

    public SpringAiAlibabaServiceImpl(VectorStore vectorStore,
                                      ChatClientFactory chatClientFactory,
                                      RedisTemplate<String,Object> redisTemplate,
                                      StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper,
                                      RateLimitService rateLimitService,
                                      ChatMemoryService chatMemoryService) {
        this.vectorStore = vectorStore;
        this.chatClientFactory = chatClientFactory;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.chatMemoryService = chatMemoryService;
    }

    @Override
    public Result<String> addDocuments(MultipartFile file, Long userId) {
        log.info("[知识库-文件上传] 开始处理文件: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);
        try {
            Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_KNOWLEDGE, RateLimitConstants.KNOWLEDGE_LIMIT);
            if (limitCheck != null) {
                log.warn("[知识库-文件上传] 用户 {} 超过每日限制", userId);
                return Result.error(limitCheck.getMessage());
            }

            List<Document> documents = getDocuments(file);
            if(documents == null || documents.isEmpty()){
                log.error("[知识库-文件上传] 文件解析失败，未获取到文档内容");
                return Result.error("处理文档发生异常，请从新上传");
            }
            log.info("[知识库-文件上传] 文件解析成功，共 {} 个文档", documents.size());

            String content = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));
            Result<String> result = processDocumentsContent(content, "文件上传", userId);
            if (result.getCode() == 200) {
                rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_KNOWLEDGE);
            }
            return result;
        } catch (Exception e) {
            log.error("[知识库-文件上传] 上传到向量数据库时发生错误: ", e);
            return Result.error("上传文件失败，请从新上传");
        }
    }

    private Result<String> processDocumentsContent(String content, String operationType, Long userId) {
        boolean isRubric = isRubric(content, userId);
        if (isRubric) {
            log.info("[知识库-{}] 检测到题目内容，已拒绝上传", operationType);
            return Result.success("检测到题目内容，已拒绝上传");
        }
        log.info("[知识库-{}] 检测为知识点内容，开始分割文档", operationType);
        
        List<Document> documents = Arrays.stream(content.split("\n\n+"))
                .filter(s -> !s.trim().isEmpty())
                .map(Document::new)
                .collect(Collectors.toList());
        log.info("[知识库-{}] 文档分段完成，共 {} 段", operationType, documents.size());
        
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<Document> splitDocuments = tokenTextSplitter.split(documents);
        log.info("[知识库-{}] 文档分割完成，共 {} 个片段，开始写入向量库", operationType, splitDocuments.size());
        
        vectorStore.add(splitDocuments);
        log.info("[知识库-{}] 成功写入向量库，共处理 {} 个文档片段", operationType, splitDocuments.size());
        
        return Result.success("成功上传文件并添加到向量数据库，共处理了 " +
                splitDocuments.size() + " 个文档片段");
    }

    @Override
    public Flux<String> ragQuery(String query, String sessionId, Long userId) {
        log.info("[RAG查询] userId={}, sessionId={}, query={}", userId, sessionId, query);
        
        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RAG, RateLimitConstants.RAG_LIMIT);
        if (limitCheck != null) {
            return Flux.error(new RuntimeException(limitCheck.getMessage()));
        }
        
        chatMemoryService.compressIfNeeded(userId.toString(), sessionId, "rag");
        
        recordSessionId(userId.toString(), sessionId, "rag");
        try {
            log.info("[RAG查询] 开始流式查询，conversationId={}", "spring_ai_alibaba_chat_memory" + "history:rag:" + userId + ":" + sessionId);
            ChatClient milvusChatClient = chatClientFactory.getMilvusChatClient(userId);
            return milvusChatClient
                    .prompt()
                    .advisors(
                            a -> a.param(CONVERSATION_ID, "history:rag:" + userId + ":" + sessionId)
                    )
                    .user(query)
                    .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                            .searchRequest(SearchRequest.builder().query(query).build())
                            .build()
                    )
                    .stream()
                    .content()
                    .map(content -> {
                        try {
                            return objectMapper.writeValueAsString(content);
                        } catch (Exception e) {
                            return content.replace("\n", "\\n").replace("\r", "\\r");
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("[RAG查询] 流式查询完成，sessionId={}", sessionId);
                        rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_RAG);
                    })
                    .doOnError(e -> log.error("[RAG查询] 流式查询异常，sessionId={}", sessionId, e));
        } catch (Exception e) {
            log.error("[RAG查询] RAG查询时发生错误: ", e);
            return Flux.error(e);
        }
    }

    @Override
    public Result<List<Object>> getHistory(String userId, String type) {
        log.info("[历史列表] 获取历史会话列表，userId={}, type={}", userId, type);
        if (type == null || type.isEmpty() || userId == null || userId.isEmpty()) {
            log.warn("[历史列表] 参数为空，userId={}, type={}", userId, type);
            return Result.error("参数不能为空");
        }
        String listKey = "history:" + type + ":" + userId;
        List<Object> range = redisTemplate.opsForList().range(listKey, 0, -1);
        int size = range != null ? range.size() : 0;
        log.info("[历史列表] 查询完成，key={}, 共 {} 个会话", listKey, size);
        return Result.success(range);
    }

    @Override
    public Flux<String> query(String query, String sessionId, Long userId) {
        log.info("[AI对话] userId={}, sessionId={}, query={}", userId, sessionId, query);
        
        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_CHAT, RateLimitConstants.CHAT_LIMIT);
        if (limitCheck != null) {
            return Flux.error(new RuntimeException(limitCheck.getMessage()));
        }
        
        chatMemoryService.compressIfNeeded(userId.toString(), sessionId, "chat");
        
        recordSessionId(userId.toString(), sessionId, "chat");
        try {
            log.info("[AI对话] 开始流式查询，conversationId={}", "spring_ai_alibaba_chat_memory:history:chat:" + userId + ":" + sessionId);
            ChatClient chatClient = chatClientFactory.getChatClient(userId);
            return chatClient
                    .prompt()
                    .advisors(
                            a -> a.param(CONVERSATION_ID, "history:chat:" + userId + ":" + sessionId)
                    )
                    .user(query)
                    .stream()
                    .content()
                    .map(content -> {
                        try {
                            return objectMapper.writeValueAsString(content);
                        } catch (Exception e) {
                            return content.replace("\n", "\\n").replace("\r", "\\r");
                        }
                    })
                    .doOnComplete(() -> {
                        log.info("[AI对话] 流式查询完成，sessionId={}", sessionId);
                        rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_CHAT);
                    })
                    .doOnError(e -> log.error("[AI对话] 流式查询异常，sessionId={}", sessionId, e));
        } catch (Exception e) {
            log.error("[AI对话] 查询时发生错误", e);
            return Flux.error(e);
        }
    }

    private void recordSessionId(String userId, String sessionId, String type) {
        String sessionListKey = "history:" + type + ":" + userId;
        BoundListOperations<String, Object> boundListOps = redisTemplate.boundListOps(sessionListKey);
        List<Object> existing = redisTemplate.opsForList().range(sessionListKey, 0, -1);
        if (existing == null || !existing.contains(sessionId)) {
            boundListOps.leftPush(sessionId);
            log.info("[会话记录] 新会话已记录，type={}, sessionId={}", type, sessionId);
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
        
        String jsonStr;
        String truncatedContent = content.length() > RUBRIC_TRUNCATE_LENGTH ? content.substring(0, RUBRIC_TRUNCATE_LENGTH) : content;
        String prompt = getQuestionAnalysisPrompt(truncatedContent);
        
        try {
            log.info("[题目解析] 开始调用AI解析题目");
            ChatClient defaultChatClient = userId != null ? chatClientFactory.getDefaultChatClient(userId) : chatClientFactory.getDefaultChatClient(0L);
            String result = defaultChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            jsonStr = result.trim();
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }
            objectMapper.readTree(jsonStr);
            log.info("[题目解析] AI解析完成");
            return Result.success(jsonStr);
        } catch (Exception e) {
            log.warn("[题目解析] AI解析超时或失败: {}，开始执行兜底策略", e.getMessage());
        }
        
        List<Map<String, Object>> maps = extractQuestions(content);
        if (maps.isEmpty()) {
            log.warn("[题目解析] 兜底解析也失败，内容可能不符合预期格式");
            return Result.error("解析题目失败，请检查内容格式");
        }
        try {
            jsonStr = objectMapper.writeValueAsString(maps);
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
            List<Map<String, String>> messages = new ArrayList<>();
            for (String json : messageList) {
                Map<String, String> msg = new HashMap<>();
                try {
                    JsonNode node = objectMapper.readTree(json);
                    String messageType = node.has("messageType") ? node.get("messageType").asText() : "";
                    String content = node.has("text") ? node.get("text").asText() : "";
                    msg.put("role", "USER".equals(messageType) ? "user" : "assistant");
                    msg.put("content", content);
                } catch (Exception e) {
                    log.warn("[历史记录] 解析消息 JSON 失败: {}", json);
                    msg.put("role", "unknown");
                    msg.put("content", json);
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
    public Result<List<AiRubric>> handleRubricFile(MultipartFile file, Long userId) {
        log.info("[题目文件解析-AI] 开始处理文件: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);
        try {
            Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RUBRIC, RateLimitConstants.RUBRIC_LIMIT);
            if (limitCheck != null) {
                return Result.error(limitCheck.getMessage());
            }
            
            List<Document> documents = getDocuments(file);
            if (documents == null || documents.isEmpty()) {
                log.error("[题目文件解析-AI] 文件解析失败，未获取到文档内容");
                return Result.error("文件解析失败");
            }
            
            String content = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));
            
            log.info("[题目文件解析-AI] 文件解析成功，内容长度: {}", content.length());
            
            String prompt = getQuestionAnalysisPrompt(content);
            
            try {
                log.info("[题目文件解析-AI] 开始调用AI解析题目");
                ChatClient defaultChatClient = chatClientFactory.getDefaultChatClient(userId);
                List<AiRubric> aiRubrics = defaultChatClient.prompt()
                        .user(prompt)
                        .call()
                        .entity(new ParameterizedTypeReference<>() {});
                log.info("[题目文件解析-AI] AI解析完成");
                
                if (aiRubrics == null || aiRubrics.isEmpty()) {
                    log.warn("[题目文件解析-AI] AI返回为空");
                    return Result.error("AI解析结果为空");
                }
                
                log.info("[题目文件解析-AI] 解析完成，共提取 {} 道题目", aiRubrics.size());
                rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_RUBRIC);
                return Result.success(aiRubrics);
            } catch (Exception e) {
                log.error("[题目文件解析-AI] AI解析失败: {}", e.getMessage(), e);
                return Result.error("AI解析失败，可能原因：题目数量过多等，可以尝试减少题目数量或者使用\"本地解析题目\"功能");
            }
            
        } catch (Exception e) {
            log.error("[题目文件解析-AI] 处理文件时发生错误: ", e);
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
                List<Map<String, Object>> maps = extractQuestions(content);
                if (maps.isEmpty()) {
                    log.warn("[题目文件解析-本地] 本地解析失败");
                    return Result.error("解析题目失败，请检查文件内容格式");
                }
                
                @SuppressWarnings("unchecked")
                List<AiRubric> aiRubrics = (List<AiRubric>) (List<?>) maps;
                log.info("[题目文件解析-本地] 本地解析完成，共 {} 道题目", aiRubrics.size());
                rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_RUBRIC);
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
            Boolean removed = redisTemplate.opsForList().remove(listKey, 1, sessionId) > 0;
            
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
            ChatClient defaultChatClient = chatClientFactory.getDefaultChatClient(userId);
            String call = defaultChatClient.prompt().user(prompt).call().content();
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

        int headingPattern = countMatches(sample, "(?m)^\\s*[#第][一二三四五六七八九十百0-9]+[章章节部分篇条]");
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

    private List<Map<String, Object>> extractQuestions(String text) {
        List<Map<String, Object>> questions = new ArrayList<>();
        String[] lines = text.split("\n");

        Map<String, Object> currentQuestion = null;
        List<String> currentOptions = new ArrayList<>();
        String currentAnswer = null;
        String currentExplanation = null;
        String currentQuestionType = null;
        List<String> calculationSteps = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                if (currentQuestion != null && currentQuestion.containsKey(RubricEnum.QUESTION.getCode())) {
                    addQuestionToList(questions, currentQuestion, currentOptions, currentAnswer, currentExplanation, currentQuestionType, calculationSteps);
                    currentQuestion = null;
                    currentOptions = new ArrayList<>();
                    currentAnswer = null;
                    currentExplanation = null;
                    currentQuestionType = null;
                    calculationSteps = new ArrayList<>();
                }
                continue;
            }

            Matcher qm = QUESTION_NUMBER_PATTERN.matcher(line);
            if (qm.find()) {
                if (currentQuestion != null && currentQuestion.containsKey(RubricEnum.QUESTION.getCode())) {
                    addQuestionToList(questions, currentQuestion, currentOptions, currentAnswer, currentExplanation, currentQuestionType, calculationSteps);
                }

                String questionText = qm.group(3);
                currentOptions = new ArrayList<>();
                currentAnswer = null;
                currentExplanation = null;
                calculationSteps = new ArrayList<>();

                if (questionText.endsWith("√") || questionText.endsWith("×")
                        || questionText.endsWith("对") || questionText.endsWith("错")
                        || questionText.endsWith("正确") || questionText.endsWith("错误")
                ) {
                    if (questionText.endsWith("√") || questionText.endsWith("对") || questionText.endsWith("正确")) {
                        currentAnswer = "正确";
                    } else {
                        currentAnswer = "错误";
                    }
                    currentQuestion = new HashMap<>();
                currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText.replaceAll("[√×对错正确错误\\s]+$", "").trim());
                    currentQuestionType = RubricQuestionTypeEnum.TRUE_FALSE.getCode();
                    continue;
                }

                if (questionText.contains("___") || questionText.contains("____") 
                        || questionText.contains("（  ）") || questionText.contains("（）")
                        || questionText.contains("[]") || questionText.contains("（ ）")) {
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText);
                    currentQuestionType = RubricQuestionTypeEnum.TRUE_FALSE.getCode();
                    continue;
                }

                boolean isMultipleChoice = questionText.contains("(多选题)") 
                        || questionText.contains("【多选题]") 
                        || questionText.contains("[多选题]");

                Matcher answerInBrackets = ANSWER_IN_BRACKETS.matcher(questionText);
                if (answerInBrackets.matches()) {
                    String answerLetters = answerInBrackets.group(1);
                    currentAnswer = String.join(",", answerLetters.split(""));
                    String cleanedQuestion = questionText.replaceAll("\\([A-D]+\\)\\s*$", "").trim();
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), cleanedQuestion);
                    currentQuestionType = isMultipleChoice || answerLetters.length() > 1 ? RubricQuestionTypeEnum.MULTIPLE_CHOICE.getCode() : RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                    continue;
                }

                Matcher multiAnswerNoBrackets = MULTI_ANSWER_NO_BRACKETS.matcher(questionText);
                if (multiAnswerNoBrackets.matches()) {
                    String answerLetters = multiAnswerNoBrackets.group(1);
                    currentAnswer = String.join(",", answerLetters.split(""));
                    String cleanedQuestion = questionText.replaceAll("\\s*[A-D]{2,}\\s*$", "").trim();
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), cleanedQuestion);
                    currentQuestionType = RubricQuestionTypeEnum.MULTIPLE_CHOICE.getCode();
                    continue;
                }

                Matcher singleAnswer = SINGLE_ANSWER.matcher(questionText);
                if (singleAnswer.matches() && !questionText.matches("^[A-D][.、].*")) {
                    currentAnswer = singleAnswer.group(1);
                    String cleanedQuestion = questionText.replaceAll("\\s*[A-D]\\s*$", "").trim();
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), cleanedQuestion);
                    currentQuestionType = isMultipleChoice ? RubricQuestionTypeEnum.MULTIPLE_CHOICE.getCode() : RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                    continue;
                }

                if (questionText.contains("简答题") || questionText.contains("论述题") 
                        || questionText.contains("问答") || questionText.contains("计算题")
                        || questionText.contains("应用题")) {
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText);
                    if (questionText.contains("计算") || questionText.contains("应用")) {
                        currentQuestionType = RubricQuestionTypeEnum.CALCULATION.getCode();
                    } else {
                        currentQuestionType = RubricQuestionTypeEnum.SHORT_ANSWER.getCode();
                    }
                    continue;
                }

                currentQuestion = new HashMap<>();
                currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText);
                currentQuestionType = RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                continue;
            }

            Matcher om = OPTION_PATTERN.matcher(line);
            if (om.find()) {
                String optionLetter = om.group(1);
                String optionText = om.group(2).trim();
                currentOptions.add(optionLetter + ". " + optionText);
                if (currentQuestionType == null) {
                    currentQuestionType = RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                }
                continue;
            }

            Matcher multiOptionLine = MULTI_OPTION_LINE.matcher(line);
            if (multiOptionLine.find()) {
                java.util.regex.Matcher m = MULTI_OPTION_LINE.matcher(line);
                while (m.find()) {
                    String optionLetter = m.group(1);
                    String optionText = m.group(2).trim();
                    if (!optionText.isEmpty()) {
                        currentOptions.add(optionLetter + ". " + optionText);
                    }
                }
                if (currentQuestionType == null) {
                    currentQuestionType = RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                }
                continue;
            }

            Matcher am = ANSWER_LINE.matcher(line);
            if (am.find()) {
                currentAnswer = am.group(2).trim();
                continue;
            }

            Matcher em = EXPLANATION_LINE.matcher(line);
            if (em.find()) {
                currentExplanation = em.group(2).trim();
                continue;
            }

            Matcher cs = CALCULATION_STEP.matcher(line);
            if (cs.find()) {
                calculationSteps.add(cs.group(2).trim());
                if (currentQuestionType == null) {
                    currentQuestionType = RubricQuestionTypeEnum.CALCULATION.getCode();
                }
            }
        }

        if (currentQuestion != null && currentQuestion.containsKey(RubricEnum.QUESTION.getCode())) {
            addQuestionToList(questions, currentQuestion, currentOptions, currentAnswer, currentExplanation, currentQuestionType, calculationSteps);
        }

        return questions;
    }

    private void addQuestionToList(List<Map<String, Object>> questions, Map<String, Object> currentQuestion,
                                    List<String> currentOptions, String currentAnswer, String currentExplanation,
                                    String currentQuestionType, List<String> calculationSteps) {
        Map<String, Object> questionMap = new HashMap<>(currentQuestion);
        questionMap.put(RubricEnum.QUESTION_TYPE.getCode(), currentQuestionType != null ? currentQuestionType : RubricQuestionTypeEnum.SINGLE_CHOICE.getCode());
        
        if (currentAnswer != null &&!currentAnswer.isEmpty()) {
            questionMap.put(RubricEnum.ANSWER.getCode(), currentAnswer);
        } else {
            questionMap.put(RubricEnum.ANSWER.getCode(), "");
        }
        
        if ( currentExplanation != null &&!currentExplanation.isEmpty()) {
            questionMap.put(RubricEnum.EXPLANATION.getCode(), currentExplanation);
        } else {
            questionMap.put(RubricEnum.EXPLANATION.getCode(), "");
        }
        
        if (currentOptions != null && !currentOptions.isEmpty()) {
            questionMap.put(RubricEnum.OPTIONS.getCode(), currentOptions);
        } else {
            questionMap.put(RubricEnum.OPTIONS.getCode(), Collections.emptyList());
        }
        
        if (RubricQuestionTypeEnum.CALCULATION.getCode().equals(currentQuestionType) && !calculationSteps.isEmpty()) {
            questionMap.put(RubricEnum.CALCULATION_STEPS.getCode(), calculationSteps);
        } else {
            questionMap.put(RubricEnum.CALCULATION_STEPS.getCode(), Collections.emptyList());
        }
        
        questions.add(questionMap);
    }
}
