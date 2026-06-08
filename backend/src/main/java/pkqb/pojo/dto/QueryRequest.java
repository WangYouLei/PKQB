package pkqb.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI查询请求 DTO
 * 用于AI对话和知识库问答的请求参数
 */
@Data
public class QueryRequest {
    @NotBlank(message = "查询内容不能为空")
    private String query;
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
}
