package pkqb.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@HookPositions({HookPosition.BEFORE_AGENT})
@Slf4j
public class RagAgentHook extends AgentHook {

    private final VectorStore vectorStore;
    private static final int TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final String RAG_CONTEXT_KEY = "rag_context";

    public RagAgentHook(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String getName() {
        return "rag_agent_hook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        //从状态中提取用户问题
        Optional<Object> messagesOpt = state.value("messages");
        if (messagesOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) messagesOpt.get();

        // 提前最后一个用户消息作为查询
        String userQuery = messages.stream()
                .filter(msg -> msg instanceof UserMessage)
                .map(msg -> ((UserMessage) msg).getText())
                .reduce((first, second) -> second)
                .orElse("");

        if (userQuery.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        log.info("[RAG Hook] 开始检索，查询: {}", userQuery);


        //检索相关文档（只执行一次，整个Agent执行过程中）
        SearchRequest searchRequest = SearchRequest.builder()
                .query(userQuery)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            log.info("[RAG Hook] 未检索到相关文档");
            return CompletableFuture.completedFuture(Map.of());
        }

        log.info("[RAG Hook] 检索到 {} 个相关文档", relevantDocs.size());

        //构建上下文
        String context = relevantDocs.stream()
                .map(doc -> {
                    String content = doc.getText();
                    String source = doc.getMetadata() != null ?
                            (String) doc.getMetadata().getOrDefault("source", "未知来源") : "未知来源";
                    return "【来源: " + source + "】\n" + content;
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        config.metadata().ifPresent(meta -> {
            meta.put(RAG_CONTEXT_KEY, context);
        });



        // Step 3: 将检索到的上下文存储到状态中，供后续 ModelInterceptor 使用
        // 存储到 state 中，ModelInterceptor 可以通过 request.getContext() 访问
        return CompletableFuture.completedFuture(Map.of());
    }
}
