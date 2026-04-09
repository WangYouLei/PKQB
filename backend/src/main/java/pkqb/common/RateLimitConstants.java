package pkqb.common;

/**
 * AI功能次数限制常量
 */
public class RateLimitConstants {
    // AI对话: 每天30次
    public static final String FEATURE_CHAT = "chat";
    public static final int CHAT_LIMIT = 30;

    // 知识库问答: 每天30次
    public static final String FEATURE_RAG = "rag";
    public static final int RAG_LIMIT = 30;

    // 上传知识库: 每天10次
    public static final String FEATURE_KNOWLEDGE = "knowledge";
    public static final int KNOWLEDGE_LIMIT = 10;

    // 上传题目: 每天5次
    public static final String FEATURE_RUBRIC = "rubric";
    public static final int RUBRIC_LIMIT = 5;
}
