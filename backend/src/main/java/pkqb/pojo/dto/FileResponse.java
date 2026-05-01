package pkqb.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件响应 DTO
 * 用于返回文件信息的响应数据
 */
@Data
public class FileResponse {

    private Long id;
    private Long userId;
    private String fileName;
    private Boolean isPrivate;
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
