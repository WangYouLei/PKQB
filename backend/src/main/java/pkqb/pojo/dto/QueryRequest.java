package pkqb.pojo.dto;

import lombok.Data;

/**
 * AI查询请求 DTO
 * 用于AI对话和知识库问答的请求参数
 */
@Data
public class QueryRequest {
    private String query;
    private String sessionId;
}
