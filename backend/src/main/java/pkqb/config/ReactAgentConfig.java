package pkqb.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pkqb.service.DashScopeRerankService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ReactAgentConfig {

    private final VectorStore vectorStore;
    private final DashScopeRerankService rerankService;

    public ReactAgentConfig(VectorStore vectorStore, DashScopeRerankService rerankService) {
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
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
        // 使用带TTL淘汰的子类：官方MemorySaver的checkpoint永久驻留堆内，配合AgentSessionCleaner定期清理
        log.info("[MemorySaver] 创建 TtlMemorySaver（支持空闲会话淘汰）");
        return new TtlMemorySaver();
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
