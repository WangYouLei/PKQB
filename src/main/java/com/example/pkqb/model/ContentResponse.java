package com.example.pkqb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentResponse<T> {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 模板名称（用于前端加载）
     */
    private String templateName;

    /**
     * 成功响应
     */
    public static <T> ContentResponse<T> success(String message, T data) {
        return ContentResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 失败响应
     */
    public static <T> ContentResponse<T> error(String message) {
        return ContentResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}