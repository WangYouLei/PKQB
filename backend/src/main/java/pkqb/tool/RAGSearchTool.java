package pkqb.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class RAGSearchTool implements BiFunction<String, ToolContext, String> {

    private final VectorStore vectorStore;
    private final Long userId;
    private final Integer classId;
    private final String scope;

    @Override
    public String apply(String query, ToolContext toolContext) {
        log.info("[RAG工具] 执行搜索，query={}, userId={}, scope={}", query, userId, scope);
        
        String filterExpression = buildFilterExpression();
        log.info("[RAG工具] 过滤表达式: {}", filterExpression);
        
        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.7);
        
        if (filterExpression != null && !filterExpression.isEmpty()) {
            searchRequestBuilder.filterExpression(filterExpression);
        }
        
        List<Document> documents = vectorStore.similaritySearch(searchRequestBuilder.build());
        
        if (documents == null || documents.isEmpty()) {
            log.info("[RAG工具] 未找到相关文档");
            return "未在知识库中找到相关内容";
        }
        
        String result = documents.stream()
                .map(doc -> {
                    String content = doc.getText();
                    String source = doc.getMetadata() != null ? 
                            (String) doc.getMetadata().getOrDefault("source", "未知来源") : "未知来源";
                    return "【来源: " + source + "】\n" + content;
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        
        log.info("[RAG工具] 找到 {} 个相关文档片段", documents.size());
        return "知识库检索结果：\n\n" + result;
    }
    
    private String buildFilterExpression() {
        if ("private".equals(scope)) {
            return String.format("scope == 'private' && userId == '%s'", userId);
        } else {
            if (classId != null) {
                return String.format("(scope == 'private' && userId == '%s') || (scope == 'public' && classId == '%s')", userId, classId);
            } else {
                return String.format("scope == 'private' && userId == '%s'", userId);
            }
        }
    }
    
    public static FunctionToolCallback createTool(VectorStore vectorStore, Long userId, Integer classId, String scope) {
        RAGSearchTool tool = new RAGSearchTool(vectorStore, userId, classId, scope);
        return FunctionToolCallback.builder("rag_search", tool)
                .description("搜索知识库获取相关信息。当用户问题可能涉及已上传的文档、知识点或学习资料时使用此工具。")
                .inputType(String.class)
                .build();
    }
}
