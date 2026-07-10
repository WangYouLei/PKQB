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
import pkqb.service.DashScopeRerankService;

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
    private final DashScopeRerankService rerankService;

    /** 粗排召回数量，扩大召回为精排提供更多候选 */
    private static final int RECALL_TOP_K = 20;
    /** 粗排相似度阈值，降低阈值以扩大召回范围 */
    private static final double RECALL_SIMILARITY_THRESHOLD = 0.5;
    /** 精排后保留的文档数量 */
    private static final int RERANK_TOP_N = 5;
    private static final String RAG_CONTEXT_KEY = "rag_context";

    public RagAgentHook(VectorStore vectorStore, DashScopeRerankService rerankService) {
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
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


        // Step 1: 粗排——向量检索召回较多候选文档（只执行一次，整个Agent执行过程中）
        SearchRequest searchRequest = SearchRequest.builder()
                .query(userQuery)
                .topK(RECALL_TOP_K)
                .similarityThreshold(RECALL_SIMILARITY_THRESHOLD)
                .build();
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            log.info("[RAG Hook] 未检索到相关文档");
            return CompletableFuture.completedFuture(Map.of());
        }

        log.info("[RAG Hook] 粗排召回 {} 个候选文档，开始精排", relevantDocs.size());

        // Step 2: 精排——使用 DashScope qwen3-rerank 对候选文档重排序
        List<Document> rerankedDocs = rerankService.rerank(userQuery, relevantDocs, RERANK_TOP_N);
        log.info("[RAG Hook] 精排完成，保留 {} 个文档", rerankedDocs.size());

        //构建上下文
        String context = rerankedDocs.stream()
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
