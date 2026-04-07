package pkqb.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String QWEN_MODEL;

    // 超时配置：3分钟
    private static final Duration API_TIMEOUT = Duration.ofMinutes(3);

    /**
     * 配置自定义 HTTP Client（包含超时设置）
     * 这个配置会对所有使用 RestClient 的 ChatClient 生效
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(API_TIMEOUT)
                .build();
    }

    @Bean
    public RestClient.Builder restClientBuilder(HttpClient httpClient) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(API_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean("defaultChatClient")
    public ChatClient defaultChatClient(ChatModel chatModel){
        return ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(QWEN_MODEL)
                        .build())
                .build();
    }
    @Bean("chatClient")
    public ChatClient chatClient(ChatModel chatModel,RedisChatMemoryRepository redisChatMemoryRepository) {
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
                        .withModel(QWEN_MODEL)
                        //指定温度
                        .withTemperature(0.7)
                        .build())
                .defaultAdvisors(
                        // 添加日志顾问
                        new SimpleLoggerAdvisor(),
                        // 添加聊天记忆顾问
                        MessageChatMemoryAdvisor.builder(
                                // 创建一个基于窗口的聊天记忆
                                MessageWindowChatMemory.builder()
                                        //指定聊天记忆仓库
                                        .chatMemoryRepository(redisChatMemoryRepository)
                                        //指定最大消息数
                                        .maxMessages(Integer.MAX_VALUE)
                                        .build()).build()
                )
                .build();
    }
    @Bean("milvusChatClient")
    public ChatClient milvusChatClient(ChatModel chatModel,
                                       VectorStore vectorStore,
                                       RedisChatMemoryRepository redisChatMemoryRepository) {

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
                        .withModel(QWEN_MODEL)
                        //指定温度
                        .withTemperature(0.7)
                        .build())
                .defaultAdvisors(
                        // 添加日志顾问
                        new SimpleLoggerAdvisor(),
                        // 添加聊天记忆顾问
                        MessageChatMemoryAdvisor.builder(
                                // 指定聊天记忆
                                MessageWindowChatMemory.builder()
                                        //指定聊天记忆仓库
                                        .chatMemoryRepository(redisChatMemoryRepository)
                                        //指定最大消息数
                                        .maxMessages(Integer.MAX_VALUE)
                                        .build()).build(),
                        // 添加向量存储问答顾问
                        QuestionAnswerAdvisor
                                .builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        // 设置返回最相似的5个文档
                                        .topK(5)
                                        // 设置相似度阈值为0 .7    默认采用的是余弦大小判断相似度，所以阈值越小越相似
                                        .similarityThreshold(0.7)
                                        .build())
                                .build()
                )
                .build();
    }
}
