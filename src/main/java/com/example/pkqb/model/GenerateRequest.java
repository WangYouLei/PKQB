package com.example.pkqb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HTML生成请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateRequest {
    /**
     * 类型：question/note
     */
    private String type;

    /**
     * 标题
     */
    private String title;

    /**
     * 题目或笔记列表
     */
    private List<?> items;
}