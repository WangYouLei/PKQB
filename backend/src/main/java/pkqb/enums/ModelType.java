package pkqb.enums;

import lombok.Getter;

/**
 * 模型类型枚举
 * 0=主模型，1=辅助模型，2=视觉模型
 */
@Getter
public enum ModelType {
    MAIN(0, "主模型", 1),
    ASSISTANT(1, "辅助模型", 2),
    VISION(2, "视觉模型", 1);

    private final int code;
    private final String description;
    private final int maxCount;

    ModelType(int code, String description, int maxCount) {
        this.code = code;
        this.description = description;
        this.maxCount = maxCount;
    }

    public static ModelType fromCode(int code) {
        for (ModelType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的模型类型: " + code);
    }
}
