package pkqb.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashScope 重排序服务
 * <p>
 * 调用阿里百炼 qwen3-rerank 模型，对向量检索召回的文档进行语义精排，
 * 降低检索噪音，提升 RAG 回答准确性。
 * 复用 spring.ai.dashscope.api-key 配置的 API Key。
 * <p>
 * 典型流程：向量库粗排召回 TopK 篇 → 本服务精排取 TopN 篇。
 */
@Service
@Slf4j
public class DashScopeRerankService {

    /** 重排序模型，gte-rerank 已于 2026-05-30 停服，使用 qwen3-rerank */
    private static final String RERANK_MODEL = "qwen3-rerank";

    /** DashScope 国内节点重排序接口 */
    private static final String BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String RERANK_ENDPOINT = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestClient rerankClient;

    public DashScopeRerankService(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        this.rerankClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("[重排序服务] 初始化完成，模型: {}", RERANK_MODEL);
    }

    /**
     * 对召回文档进行重排序。
     *
     * @param query     用户查询
     * @param documents 向量检索召回的文档列表
     * @param topN      重排后保留的文档数量
     * @return 按相关性降序排列的文档列表；调用失败时返回原始顺序（截断到 topN）
     */
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        // 仅一篇文档无需重排
        if (documents.size() == 1) {
            return documents;
        }

        int finalTopN = Math.min(topN, documents.size());
        try {
            // 提取文档纯文本作为重排输入
            List<String> docTexts = documents.stream()
                    .map(Document::getText)
                    .toList();

            Map<String, Object> requestBody = Map.of(
                    "model", RERANK_MODEL,
                    "input", Map.of(
                            "query", query,
                            "documents", docTexts
                    ),
                    "parameters", Map.of(
                            "return_documents", false,
                            "top_n", finalTopN
                    )
            );

            RerankResponse response = rerankClient.post()
                    .uri(RERANK_ENDPOINT)
                    .body(requestBody)
                    .retrieve()
                    .body(RerankResponse.class);

            if (response == null || response.output() == null || response.output().results() == null) {
                log.warn("[重排序] 响应为空，返回原始顺序前 {} 篇", finalTopN);
                return truncate(documents, finalTopN);
            }

            // 按返回顺序（已按相关性降序）映射回原始 Document
            List<Document> reranked = new ArrayList<>();
            for (RerankResult r : response.output().results()) {
                if (r.index() >= 0 && r.index() < documents.size()) {
                    reranked.add(documents.get(r.index()));
                }
            }

            double topScore = reranked.isEmpty() ? 0.0 : response.output().results().get(0).relevanceScore();
            log.info("[重排序] 成功，输入 {} 篇，输出 {} 篇，最高相关性分数: {}",
                    documents.size(), reranked.size(), topScore);
            return reranked;
        } catch (Exception e) {
            log.warn("[重排序] 调用失败，返回原始顺序前 {} 篇: {}", finalTopN, e.getMessage());
            return truncate(documents, finalTopN);
        }
    }

    /** 截断文档列表到指定长度 */
    private List<Document> truncate(List<Document> documents, int topN) {
        return documents.size() > topN ? new ArrayList<>(documents.subList(0, topN)) : documents;
    }

    // ---- DashScope 重排序响应结构 ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResponse(Output output, Map<String, Object> usage, String requestId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(List<RerankResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResult(int index,
                               @JsonProperty("relevance_score") double relevanceScore) {
    }
}
