package com.example.pkqb.model;

/**
 * 内容类型枚举
 */
public enum ContentType {
    QUESTION("question", "题目"),
    NOTE("note", "笔记");

    private final String value;
    private final String description;

    ContentType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static ContentType fromValue(String value) {
        for (ContentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown content type: " + value);
    }
}