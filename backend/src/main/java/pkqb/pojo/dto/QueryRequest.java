package pkqb.pojo.dto;

import lombok.Data;

@Data
public class QueryRequest {
    private String query;
    private String sessionId;
}
