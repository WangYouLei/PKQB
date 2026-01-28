package com.example.pkqb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteItem {
    /**
     * 知识点标题
     */
    private String title;

    /**
     * 知识点内容
     */
    private String content;
}