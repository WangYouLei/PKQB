package pkqb.pojo.dto;

import lombok.Data;

/**
 * 生成Rubric HTML文件的请求
 */
@Data
public class RubricGenerateRequest {

    /**
     * Rubric ID
     */
    private Long rubricId;

    /**
     * 文件名（可选，默认使用Rubric标题）
     */
    private String fileName;

    /**
     * 是否公开（可选，默认false）
     */
    private Boolean isPublic;
}