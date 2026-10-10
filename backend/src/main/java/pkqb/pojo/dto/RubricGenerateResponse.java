package pkqb.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Rubric HTML生成响应
 */
@Data
public class RubricGenerateResponse {

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 下载URL
     */
    private String downloadUrl;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}