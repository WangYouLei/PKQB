package com.example.pkqb.model;

/**
 * 题目类型枚举
 */
public enum QuestionType {
    SINGLE_CHOICE("single_choice", "单选题"),
    MULTIPLE_CHOICE("multiple_choice", "多选题"),
    TRUE_FALSE("true_false", "判断题");

    private final String value;
    private final String description;

    QuestionType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static QuestionType fromValue(String value) {
        for (QuestionType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown question type: " + value);
    }
}