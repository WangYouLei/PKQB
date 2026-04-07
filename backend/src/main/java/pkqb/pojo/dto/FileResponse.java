package pkqb.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件响应 DTO
 */
@Data
public class FileResponse {

    private Long id;
    private Long userId;
    private String fileName;
    private Boolean isPublic;
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
