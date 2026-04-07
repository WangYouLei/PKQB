package pkqb.pojo.dto;

import lombok.Data;

/**
 * 预签名URL响应 DTO
 */
@Data
public class PresignedUrlResponse {

    private String url;
    private Long fileId;
    private String fileName;

    public PresignedUrlResponse(String url, Long fileId, String fileName) {
        this.url = url;
        this.fileId = fileId;
        this.fileName = fileName;
    }
}
