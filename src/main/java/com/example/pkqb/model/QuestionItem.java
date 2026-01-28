package com.example.pkqb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 题目模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionItem {
    /**
     * 题目内容
     */
    private String question;

    /**
     * 题目类型
     */
    private String questionType;

    /**
     * 选项列表（判断题可为空）
     */
    private List<String> options;

    /**
     * 正确答案（多选题用逗号分隔如：A,B,C）
     */
    private String answer;

    /**
     * 答案解析
     */
    private String explanation;
}