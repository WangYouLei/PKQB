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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.common.RateLimitConstants;
import pkqb.enums.RubricEnum;
import pkqb.enums.RubricQuestionTypeEnum;
import pkqb.pojo.dto.AiRubric;
import pkqb.service.RateLimitService;
import pkqb.service.SpringAiAlibabaService;
import reactor.core.publisher.Flux;
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

    private final ChatClient defaultChatClient;

    private final ChatClient chatClient;

    private final ChatClient milvusChatClient;

    private final RedisTemplate<String,Object> redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final RateLimitService rateLimitService;

    // 题目类型枚举映射，用于动态生成prompt和转换
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
                    "explanation": "题目解析",
                    "calculationSteps": ["步骤1", "步骤2", "步骤3"]
                }
            ]

            文本内容：
            %s
            """;
    
    /**
     * 获取题目分析Prompt
     */
    private String getQuestionAnalysisPrompt(String content) {
        // 动态生成题型说明
        return String.format(QUESTION_ANALYSIS_PROMPT_TEMPLATE, TYPE_DESC, content);
    }

    // 正则表达式静态常量（避免重复编译）
    private static final Pattern QUESTION_NUMBER_PATTERN = Pattern.compile("^(\\d+|[（(]\\d+[）)]|[（(][A-Z][）)]|\\([A-Z]\\))([.、.．])\\s*(.+)");
    private static final Pattern ANSWER_IN_BRACKETS = Pattern.compile(".*\\(([A-D]+)\\)\\s*$");
    private static final Pattern MULTI_ANSWER_NO_BRACKETS = Pattern.compile(".*[？?]?\\s*([A-D]{2,})\\s*$");
    private static final Pattern SINGLE_ANSWER = Pattern.compile(".*[？?]?\\s*([A-D])\\s*$");
    private static final Pattern OPTION_PATTERN = Pattern.compile("^([A-D])[.、、]\\s*(.+)");
    private static final Pattern MULTI_OPTION_LINE = Pattern.compile("([A-D])[.、]\\s*([^A-D]+?)(?=\\s+[A-D][.、]|$)");
    private static final Pattern ANSWER_LINE = Pattern.compile("(答案|Answer|参考答案)[:：]\\s*(.+)");
    private static final Pattern EXPLANATION_LINE = Pattern.compile("(解析|Explanation|解析如下)[:：]\\s*(.+)");
    private static final Pattern CALCULATION_STEP = Pattern.compile("(步骤\\d*|第\\d*步)[:：.]?\\s*(.+)");

    // isRubric2 方法中使用的静态数组常量
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

    // countMatches 中使用的预编译正则 Pattern
    private static final Pattern OPTION_PATTERN_CACHE = Pattern.compile("(?i)[（(\\[]?[A-D][）)\\]]?[、.．，,]");
    private static final Pattern QUESTION_MARK_PATTERN = Pattern.compile("\\?|\\？");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?m)^\\s*\\d+[、.．)）]");



    // 内容截取长度常量

    // 题目解析截取长度
    private static final int RUBRIC_TRUNCATE_LENGTH = 3000;
    // 题目判断截取长度
    private static final int RUBRIC_DETECT_LENGTH = 1500;
    // 笔记判断截取长度
    private static final int NOTE_DETECT_LENGTH = 1000;

    public SpringAiAlibabaServiceImpl(VectorStore vectorStore,
                                      ChatClient defaultChatClient,
                                      @Qualifier("chatClient") ChatClient chatClient,
                                      @Qualifier("milvusChatClient") ChatClient milvusChatClient,
                                      RedisTemplate<String,Object> redisTemplate,
                                      StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper,
                                      RateLimitService rateLimitService) {
        this.vectorStore = vectorStore;
        this.defaultChatClient = defaultChatClient;
        this.chatClient = chatClient;
        this.milvusChatClient = milvusChatClient;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public Result<String> addDocuments(MultipartFile file, Long userId) {
        log.info("[知识库-文件上传] 开始处理文件: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);
        try {
            // 检查速率限制 - 上传知识库每天10次
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
            Result<String> result = processDocumentsContent(content, "文件上传");
            // 上传成功后增加计数
            if (result.getCode() == 200) {
                rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_KNOWLEDGE);
            }
            return result;
        } catch (Exception e) {
            log.error("[知识库-文件上传] 上传到向量数据库时发生错误: ", e);
            return Result.error("上传文件失败，请从新上传");
        }
    }


    /**
     * 处理文档内容的公共方法
     */
    private Result<String> processDocumentsContent(String content, String operationType) {
        boolean isRubric = isRubric(content);
        if (isRubric) {
            log.info("[知识库-{}] 检测到题目内容，已拒绝上传", operationType);
            return Result.success("检测到题目内容，已拒绝上传");
        }
        log.info("[知识库-{}] 检测为知识点内容，开始分割文档", operationType);
        
        // 将内容分段并转换为 Document
        List<Document> documents = Arrays.stream(content.split("\n\n+"))
                .filter(s -> !s.trim().isEmpty())
                .map(Document::new)
                .collect(Collectors.toList());
        log.info("[知识库-{}] 文档分段完成，共 {} 段", operationType, documents.size());
        
        // 使用成员变量的 TokenTextSplitter 进行分割
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<Document> splitDocuments = tokenTextSplitter.split(documents);
        log.info("[知识库-{}] 文档分割完成，共 {} 个片段，开始写入向量库", operationType, splitDocuments.size());
        
        // 添加到向量数据库
        vectorStore.add(splitDocuments);
        log.info("[知识库-{}] 成功写入向量库，共处理 {} 个文档片段", operationType, splitDocuments.size());
        
        return Result.success("成功上传文件并添加到向量数据库，共处理了 " +
                splitDocuments.size() + " 个文档片段");
    }

    @Override
    public Flux<String> ragQuery(String query, String sessionId, Long userId) {
        log.info("[RAG查询] userId={}, sessionId={}, query={}", userId, sessionId, query);
        
        // 检查速率限制 - 知识库问答每天30次
        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RAG, RateLimitConstants.RAG_LIMIT);
        if (limitCheck != null) {
            return Flux.error(new RuntimeException(limitCheck.getMessage()));
        }
        
        // 记录会话ID
        recordSessionId(userId.toString(), sessionId, "rag");
        try {
            log.info("[RAG查询] 开始流式查询，conversationId={}", "spring_ai_alibaba_chat_memory" + "history:rag:" + userId + ":" + sessionId);
            // 使用QuestionAnswerAdvisor实现RAG功能
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
                    .doOnComplete(() -> {
                        log.info("[RAG查询] 流式查询完成，sessionId={}", sessionId);
                        // 成功后增加计数
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
        // 会话ID存储在单独的list中
        String listKey = "history:" + type + ":" + userId;
        List<Object> range = redisTemplate.opsForList().range(listKey, 0, -1);
        int size = range != null ? range.size() : 0;
        log.info("[历史列表] 查询完成，key={}, 共 {} 个会话", listKey, size);
        return Result.success(range);
    }

    @Override
    public Flux<String> query(String query, String sessionId, Long userId) {
        log.info("[AI对话] userId={}, sessionId={}, query={}", userId, sessionId, query);
        
        // 检查速率限制 - AI对话每天30次
        Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_CHAT, RateLimitConstants.CHAT_LIMIT);
        if (limitCheck != null) {
            return Flux.error(new RuntimeException(limitCheck.getMessage()));
        }
        
        // 记录会话ID
        recordSessionId(userId.toString(), sessionId, "chat");
        try {
            log.info("[AI对话] 开始流式查询，conversationId={}", "spring_ai_alibaba_chat_memory:history:chat:" + userId + ":" + sessionId);
            return chatClient
                    .prompt()
                    .advisors(
                            a -> a.param(CONVERSATION_ID, "history:chat:" + userId + ":" + sessionId)
                    )
                    .user(query)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        log.info("[AI对话] 流式查询完成，sessionId={}", sessionId);
                        // 成功后增加计数
                        rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_CHAT);
                    })
                    .doOnError(e -> log.error("[AI对话] 流式查询异常，sessionId={}", sessionId, e));
        } catch (Exception e) {
            log.error("[AI对话] 查询时发生错误", e);
            return Flux.error(e);
        }

    }

    /**
     * 记录会话ID到Redis
     */
    private void recordSessionId(String userId, String sessionId, String type) {
        String sessionListKey = "history:" + type + ":" + userId;
        BoundListOperations<String, Object> boundListOps = redisTemplate.boundListOps(sessionListKey);
        // 只在 sessionId 不在列表中时才添加，避免重复
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
        
        return parseRubricContent(content);
    }

    /**
     * 解析题目的公共方法
     */
    private Result<String> parseRubricContent(String content) {
        log.info("[题目解析] 内容长度={}", content.length());
        
        String jsonStr;
        // 截取指定长度字符，避免内容过长
        String truncatedContent = content.length() > RUBRIC_TRUNCATE_LENGTH ? content.substring(0, RUBRIC_TRUNCATE_LENGTH) : content;
        String prompt = getQuestionAnalysisPrompt(truncatedContent);
        
        // AI 解析，普通 ChatClient，60秒超时
        try {
            log.info("[题目解析] 开始调用AI解析题目");
            String result = defaultChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            // 解析json并赋值给questions
            jsonStr = result.trim();
            // 去除AI可能返回的markdown代码块标记
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }
            // 验证JSON是否有效
            objectMapper.readTree(jsonStr);
            log.info("[题目解析] AI解析完成");
            return Result.success(jsonStr);
        } catch (Exception e) {
            log.warn("[题目解析] AI解析超时或失败: {}，开始执行兜底策略", e.getMessage());
        }
        
        // 兜底策略
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

        //这个spring_ai_alibaba_chat_memory是springAiAlibaba框架默认添加上去的，目的是防止污染Redis
        // key格式：spring_ai_alibaba_chat_memory:history:{type}:{userId}:{sessionId}
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
        log.info("[题目文件解析] 开始处理文件: {}, 大小: {} bytes, userId={}",
                file.getOriginalFilename(), file.getSize(), userId);
        try {
            // 检查速率限制 - 上传题目每天5次
            Result<?> limitCheck = rateLimitService.checkLimit(userId, RateLimitConstants.FEATURE_RUBRIC, RateLimitConstants.RUBRIC_LIMIT);
            if (limitCheck != null) {
                return Result.error(limitCheck.getMessage());
            }
            
            // 1. 读取文件内容
            List<Document> documents = getDocuments(file);
            if (documents == null || documents.isEmpty()) {
                log.error("[题目文件解析] 文件解析失败，未获取到文档内容");
                return Result.error("文件解析失败");
            }
            
            // 2. 合并文档内容
            String content = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));
            
            log.info("[题目文件解析] 文件解析成功，内容长度: {}", content.length());
            
            // 3. 使用AI解析题目内容
            String prompt = getQuestionAnalysisPrompt(content);
            
            try {
                log.info("[题目文件解析] 开始调用AI解析题目");
                List<AiRubric> aiRubrics = defaultChatClient.prompt()
                        .user(prompt)
                        .call()
                        .entity(new ParameterizedTypeReference<>() {});
                log.info("[题目文件解析] AI解析完成");
                
                if (aiRubrics == null || aiRubrics.isEmpty()) {
                    log.warn("[题目文件解析] AI返回为空");
                    return Result.error("AI解析结果为空");
                }
                
                log.info("[题目文件解析] 解析完成，共提取 {} 道题目", aiRubrics.size());
                // 上传成功后增加计数
                rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_RUBRIC);
                return Result.success(aiRubrics);
            } catch (Exception e) {
                log.warn("[题目文件解析] AI解析超时或失败: {}，开始执行兜底策略", e.getMessage());
                // 兜底策略：使用本地规则解析
                List<Map<String, Object>> maps = extractQuestions(content);
                if (maps.isEmpty()) {
                    log.warn("[题目文件解析] 兜底解析也失败");
                    return Result.error("解析题目失败，请检查文件内容格式");
                }
                
                // 兜底策略返回Map列表，前端需要处理
                @SuppressWarnings("unchecked")
                List<AiRubric> aiRubrics = (List<AiRubric>) (List<?>) maps;
                log.info("[题目文件解析] 兜底解析完成，共 {} 道题目", aiRubrics.size());
                rateLimitService.incrementUsage(userId, RateLimitConstants.FEATURE_RUBRIC);
                return Result.success(aiRubrics);
            }
            
        } catch (Exception e) {
            log.error("[题目文件解析] 处理文件时发生错误: ", e);
            return Result.error("处理文件失败: " + e.getMessage());
        }
    }




    /* =============================  私有方法   ===============================*/

    //处理文件
    private List<Document> getDocuments(MultipartFile file) {
        List<Document> documents = new ArrayList<>();
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return documents;
            }
            String substring = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            //对PDF文件使用专门的PDF文档读取器
            if ("pdf".equalsIgnoreCase(substring)) {
                byte[] pdfContent = file.getBytes();
                ByteArrayInputStream pdfInputStream = new
                        ByteArrayInputStream(pdfContent);
                InputStreamResource pdfResource = new
                        InputStreamResource(pdfInputStream);
                PagePdfDocumentReader pdfReader = new
                        PagePdfDocumentReader(pdfResource);
                documents = pdfReader.get();
            }else{
                // 对其他格式使用TikaDocumentReader
                TikaDocumentReader reader = new
                        TikaDocumentReader(file.getResource());
                documents = reader.get();
            }
        } catch (Exception e) {
            log.error("处理文件时发生错误: ", e);
            return documents;
        }
        return documents;
    }

    //判断上传的文件、内容是题目还是知识点 题目：true  知识点：false
    private boolean isRubric(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String sample = content.length() > RUBRIC_DETECT_LENGTH ? content.substring(0, RUBRIC_DETECT_LENGTH) : content;
        String prompt = "请判断下面内容是题目，还是知识点：" + "\n" + "\n"
                + sample +
                "。" + "\n" +
                "回答模版：如果是【题目】就回答：题目，如果是【知识点】就回答：知识点。要求：严格按照回答模版回答。";
        try {
            String call = defaultChatClient.prompt().user(prompt).call().content();
            return call.contains("题目");
        } catch (Exception e) {
            log.info("[题目判断] AI调用失败，fallback到本地规则算法，e={}", e.getMessage());
            return isRubric2(content);
        }
    }

    //备用方法    判断上传的文件、内容是题目还是知识点（基于文本特征评分算法）
    //true:题目   false：笔记
    private boolean isRubric2(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String sample = content.length() > NOTE_DETECT_LENGTH ? content.substring(0, NOTE_DETECT_LENGTH) : content;

        int questionScore = 0;
        int noteScore = 0;

        // ========== 题目特征 ==========

        // 1. 选项特征: A. B. C. D. 或 A、B、C、D 或 【A】【B】
        int optionCount = countMatches(sample, OPTION_PATTERN_CACHE);
        if (optionCount >= 3) {questionScore += 30;}
        else if (optionCount >= 2) {questionScore += 15;}

        // 2. 问号密度（题目通常有较多问号）
        int questionMarkCount = countMatches(sample, QUESTION_MARK_PATTERN);
        double questionMarkRatio = (double) questionMarkCount / sample.length();
        if (questionMarkRatio > 0.02) {questionScore += 20;}
        else if (questionMarkRatio > 0.005) {questionScore += 10;}

        // 3. 题型关键词
        for (String kw : QUESTION_TYPE_KEYWORDS) {
            if (sample.contains(kw)) {
                questionScore += 20;
                break;
            }
        }

        // 4. 答案/得分标记
        for (String marker : ANSWER_MARKERS) {
            if (sample.contains(marker)) {
                questionScore += 15;
                break;
            }
        }

        // 5. 题号特征: 数字开头+点/顿号 + 后续还有题号
        int numberPattern = countMatches(sample, NUMBER_PATTERN);
        if (numberPattern >= 3) {questionScore += 20;}
        else if (numberPattern >= 2) {questionScore += 10;}

        // 6. 疑问词密度
        for (String word : QUESTION_WORDS) {
            questionScore += countMatches(sample, word) * 3;
        }

        // ========== 笔记特征 ==========

        // 1. 概念/知识类关键词
        for (String kw : NOTE_KEYWORDS) {
            if (sample.contains(kw)) {noteScore += 8;}
        }

        // 2. 段落连贯性（笔记通常有较长段落）
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

        // 3. 连接词/过渡词密度（笔记论述性强）
        for (String conn : CONNECTIVES) {
            noteScore += countMatches(sample, conn) * 5;
        }

        // 4. 标题/章节结构（笔记常有层级标题）
        int headingPattern = countMatches(sample, "(?m)^\\s*[#第][一二三四五六七八九十百0-9]+[章章节部分篇条]");
        if (headingPattern >= 2) {noteScore += 15;}

        // ========== 综合判定 ==========
        // 题目特征更明显则返回 true
        return questionScore > noteScore;
    }

    // 正则匹配计数
    private int countMatches(String text, String regex) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {count++;}
        return count;
    }

    // 使用预编译Pattern的正则匹配计数
    private int countMatches(String text, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /**
     * 提取题目（备用实现 - 兜底方案）
     * 支持格式：
     * 1. 判断题（末尾有 √/× 或 对/错）
     * 2. 单选题（末尾有答案字母，或有A.B.C.D.选项）
     * 3. 多选题（末尾有(AD)或ABC格式）
     * 4. 填空题（包含 __ 或 （  ） 或 []）
     * 5. 简答题（题目没有选项，有"答："或"答案："标记）
     * 6. 计算题（题目包含"计算"关键词或需要计算步骤）
     */
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
                // 空行可能是分隔符，保存当前题目
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

            // 检测题目编号开头（如 "1. " 或 "1、" 或 "（1）" 或 "(A)"）
            Matcher qm = QUESTION_NUMBER_PATTERN.matcher(line);
            if (qm.find()) {
                // 保存上一题
                if (currentQuestion != null && currentQuestion.containsKey(RubricEnum.QUESTION.getCode())) {
                    addQuestionToList(questions, currentQuestion, currentOptions, currentAnswer, currentExplanation, currentQuestionType, calculationSteps);
                }

                String questionText = qm.group(3);
                currentOptions = new ArrayList<>();
                currentAnswer = null;
                currentExplanation = null;
                calculationSteps = new ArrayList<>();

                // 1. 判断题检测（末尾有 √ 或 × 或 对 或 错）
                if (questionText.endsWith("√") || questionText.endsWith("×")
                        || questionText.endsWith("对") || questionText.endsWith("错")
                        || questionText.endsWith("正确") || questionText.endsWith("错误")
                ) {
                    // 判断答案
                    if (questionText.endsWith("√") || questionText.endsWith("对") || questionText.endsWith("正确")) {
                        currentAnswer = "正确";
                    } else {
                        currentAnswer = "错误";
                    }
                    // 去除答案部分
                    currentQuestion = new HashMap<>();
                currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText.replaceAll("[√×对错正确错误\\s]+$", "").trim());
                    currentQuestionType = RubricQuestionTypeEnum.TRUE_FALSE.getCode();
                    continue;
                }

                // 2. 填空题检测（题目中包含 __ 或 （  ） 或 ____ 或 []）
                if (questionText.contains("___") || questionText.contains("____") 
                        || questionText.contains("（  ）") || questionText.contains("（）")
                        || questionText.contains("[]") || questionText.contains("（ ）")) {
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText);
                    currentQuestionType = RubricQuestionTypeEnum.TRUE_FALSE.getCode();
                    continue;
                }

                // 3. 检测是否是多选题（题目中有标识或末尾有多个答案字母）
                boolean isMultipleChoice = questionText.contains("(多选题)") 
                        || questionText.contains("【多选题]") 
                        || questionText.contains("[多选题]");

                // 检查题目末尾是否有答案（括号包裹如 (AD) 或 (ABC)）
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

                // 检查题目末尾是否有多个答案字母（无括号，如 "ABC" 或 "ABD"）- 多选题
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

                // 检查题目末尾是否有单个答案字母（排除选项开头的情况）
                Matcher singleAnswer = SINGLE_ANSWER.matcher(questionText);
                if (singleAnswer.matches() && !questionText.matches("^[A-D][.、].*")) {
                    currentAnswer = singleAnswer.group(1);
                    String cleanedQuestion = questionText.replaceAll("\\s*[A-D]\\s*$", "").trim();
                    currentQuestion = new HashMap<>();
                    currentQuestion.put(RubricEnum.QUESTION.getCode(), cleanedQuestion);
                    currentQuestionType = isMultipleChoice ? RubricQuestionTypeEnum.MULTIPLE_CHOICE.getCode() : RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                    continue;
                }

                // 4. 简答题/计算题检测（没有选项，但题目有特定关键词）
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

                // 默认作为单选题（如果有选项）或简答题（如果没有选项）
                currentQuestion = new HashMap<>();
                currentQuestion.put(RubricEnum.QUESTION.getCode(), questionText);
                currentQuestionType = RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                continue;
            }

            // 检测选项（支持 A. B. C. D. 格式，也支持 A、B、C、D 格式）
            Matcher om = OPTION_PATTERN.matcher(line);
            if (om.find()) {
                String optionLetter = om.group(1);
                String optionText = om.group(2).trim();
                currentOptions.add(optionLetter + ". " + optionText);
                // 如果检测到选项，默认为选择题
                if (currentQuestionType == null) {
                    currentQuestionType = RubricQuestionTypeEnum.SINGLE_CHOICE.getCode();
                }
                continue;
            }

            // 检测一行多个选项（如 "A. xxx    B. xxx    C. xxx"）
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

            // 检测答案行（答案：xxx 或 Answer: xxx）
            Matcher am = ANSWER_LINE.matcher(line);
            if (am.find()) {
                currentAnswer = am.group(2).trim();
                continue;
            }

            // 检测解析行（解析：xxx 或 Explanation: xxx）
            Matcher em = EXPLANATION_LINE.matcher(line);
            if (em.find()) {
                currentExplanation = em.group(2).trim();
                continue;
            }

            // 检测计算步骤（步骤1: xxx 或 步骤 1. xxx）
            Matcher cs = CALCULATION_STEP.matcher(line);
            if (cs.find()) {
                calculationSteps.add(cs.group(2).trim());
                if (currentQuestionType == null) {
                    currentQuestionType = RubricQuestionTypeEnum.CALCULATION.getCode();
                }
            }
        }

        // 保存最后一题
        if (currentQuestion != null && currentQuestion.containsKey(RubricEnum.QUESTION.getCode())) {
            addQuestionToList(questions, currentQuestion, currentOptions, currentAnswer, currentExplanation, currentQuestionType, calculationSteps);
        }

        return questions;
    }

    /**
     * 将题目信息添加到结果列表
     */
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
        
        // options 直接存数组（和AI返回格式一致）
        if (currentOptions != null && !currentOptions.isEmpty()) {
            questionMap.put(RubricEnum.OPTIONS.getCode(), currentOptions);
        } else {
            questionMap.put(RubricEnum.OPTIONS.getCode(), Collections.emptyList());
        }
        
        // 计算题添加计算步骤（和AI返回格式一致）
        if (RubricQuestionTypeEnum.CALCULATION.getCode().equals(currentQuestionType) && !calculationSteps.isEmpty()) {
            questionMap.put(RubricEnum.CALCULATION_STEPS.getCode(), calculationSteps);
        } else {
            questionMap.put(RubricEnum.CALCULATION_STEPS.getCode(), Collections.emptyList());
        }
        
        questions.add(questionMap);
    }



}
