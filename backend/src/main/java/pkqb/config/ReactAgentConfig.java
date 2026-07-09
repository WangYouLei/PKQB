package pkqb.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pkqb.service.DashScopeRerankService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ReactAgentConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private final VectorStore vectorStore;
    private final DashScopeRerankService rerankService;

    public ReactAgentConfig(VectorStore vectorStore, DashScopeRerankService rerankService) {
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setTimeout(3000);
        return Redisson.create(config);
    }

    @Bean
    public SummarizationHook summarizationHook(ChatModel chatModel) {
        return SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(4000)
                .messagesToKeep(20)
                .build();
    }

    @Bean
    public ModelCallLimitHook modelCallLimitHook() {
        return ModelCallLimitHook.builder().runLimit(3).build();
    }

    @Bean
    public MemorySaver memorySaver() {
        log.info("[MemorySaver] 创建 MemorySaver");
        return new MemorySaver();
    }

    @Bean("chatReactAgent")
    public ReactAgent chatReactAgent(ChatModel chatModel, ModelCallLimitHook modelCallLimitHook, SummarizationHook summarizationHook, MemorySaver memorySaver) {
        return ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .systemPrompt(AiConstants.CHAT_SYSTEM_PROMPT)
                .hooks(summarizationHook, modelCallLimitHook)
                .saver(memorySaver)
                .build();
    }

    @Bean("ragReactAgent")
    public ReactAgent ragReactAgent(ChatModel chatModel, ModelCallLimitHook modelCallLimitHook, SummarizationHook summarizationHook, MemorySaver memorySaver) {
        return ReactAgent.builder()
                .name("rag_agent")
                .model(chatModel)
                .systemPrompt(AiConstants.RAG_SYSTEM_PROMPT)
                .hooks(new RagAgentHook(vectorStore, rerankService),summarizationHook, modelCallLimitHook)
                .saver(memorySaver)
                .interceptors(new RAGContextInterceptor())
                .build();
    }

    @Bean("simpleReactAgent")
    public ReactAgent simpleReactAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("simple_agent")
                .model(chatModel)
                .build();
    }
}
