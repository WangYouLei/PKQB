package pkqb.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 用户级 ChatClient 配置工厂
 * 负责创建用户使用自己 API Key 的 ChatClient 实例
 */
@Component
@Slf4j
public class ChatClientUserConfig {

    private final RedisChatMemoryRepository redisChatMemoryRepository;

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String qwenModel;

    @Value("${spring.ai.dashscope.chat.options.personal-max-tokens}")
    private int personalMaxTokens;

    public ChatClientUserConfig(RedisChatMemoryRepository redisChatMemoryRepository) {
        this.redisChatMemoryRepository = redisChatMemoryRepository;
    }

    /**
     * 创建用户级默认 ChatClient
     */
    public ChatClient createUserDefaultChatClient(String apiKey) {
        log.info("为用户创建个人 DefaultChatClient");
        ChatModel chatModel = createChatModel(apiKey);
        return ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(qwenModel)
                        .withMaxToken(personalMaxTokens)
                        .build())
                .build();
    }

    /**
     * 创建用户级 ChatClient（AI学习助手）
     */
    public ChatClient createUserChatClient(String apiKey) {
        log.info("为用户创建个人 ChatClient");
        ChatModel chatModel = createChatModel(apiKey);
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一个专业、耐心且富有启发性的 AI 学习助手，名字叫小磊。
                        你的目标是帮助用户深入理解知识点，而不仅仅是提供标准答案。
                        请遵循以下原则进行回复：
                        1. 角色设定：你是用户的导师，用鼓励性的语气交流。
                        2. 循序渐进：如果用户问一个复杂问题，先解释基础概念，再逐步深入。
                        3. 举例说明：尽量使用生活中的例子或代码示例来解释抽象概念。
                        4. 启发思考：在给出答案后，提出一个相关的问题，引导用户进一步思考。
                        5. 结构清晰：使用 Markdown 格式（如标题、列表、加粗）使内容易于阅读。
                        6. 准确严谨：确保提供的信息是最新且准确的，如果遇到不确定的领域，请诚实告知。
                        """)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(qwenModel)
                        .withTemperature(0.7)
                        .withMaxToken(personalMaxTokens)
                        .build())
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .chatMemoryRepository(redisChatMemoryRepository)
                                        .maxMessages(20)
                                        .build()).build()
                )
                .build();
    }

    /**
     * 创建用户级 Milvus ChatClient（知识库问答）
     */
    public ChatClient createUserMilvusChatClient(String apiKey) {
        log.info("为用户创建个人 MilvusChatClient（暂不支持向量存储）");
        ChatModel chatModel = createChatModel(apiKey);
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一个专业的知识库问答助手，叫小磊。
                        请遵循以下原则进行回复：
                        1. 如果你引用了Milvus向量数据库中检索到的内容进行回答，必须严格遵循以下格式：
                           第一行（单独一行）：【根据知识库回答】
                           第二行起：你的详细回答
                        2. 如果你没有引用任何向量数据库中的内容（完全由你自己回答），必须严格遵循以下格式：
                           第一行（单独一行）：【知识库中未搜索到，此为AI回答】
                           第二行起：你的详细回答
                        3. 回答要简洁明了，重点突出，结构清晰
                        4. 如果检索到的信息与用户问题相关，请结合这些信息给出准确的回答
                        5. 请保持专业、客观的语气，不要添加个人意见
                        """)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(qwenModel)
                        .withTemperature(0.7)
                        .withMaxToken(personalMaxTokens)
                        .build())
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .chatMemoryRepository(redisChatMemoryRepository)
                                        .maxMessages(20)
                                        .build()).build()
                )
                .build();
    }

    /**
     * 创建 ChatModel
     */
    private ChatModel createChatModel(String apiKey) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(qwenModel)
                        .withMaxToken(personalMaxTokens)
                        .withTemperature(0.7)
                        .build())
                .build();
    }
}
