package com.example.pkqb.model;

import lombok.Data;

/**
 * 内容分析请求模型
 */
@Data
public class ContentRequest {
    /**
     * 文本内容
     */
    private String text;

    /**
     * 文件ID（如果有上传文件）
     */
    private String fileId;

    /**
     * 文件类型：word/pdf/text
     */
    private String fileType;

    /**
     * 文件路径
     */
    private String filePath;
}