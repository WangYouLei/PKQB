package pkqb.enums;

/**
 * 题目类型枚举
 * 定义系统支持的题目类型
 */
public enum RubricQuestionTypeEnum {
    SINGLE_CHOICE("单选题", "single_choice"),
    MULTIPLE_CHOICE("多选题", "multiple_choice"),
    TRUE_FALSE("判断题", "true_false"),
    SHORT_ANSWER("简答题", "short_answer"),
    FILL_IN_THE_BLANKS("填空题", "fill_in_the_blanks"),
    CALCULATION("计算题", "calculation");
    
    private String value;
    private String code;
    
    RubricQuestionTypeEnum(String value, String code) {
        this.value = value;
        this.code = code;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getCode() {
        return code;
    }
}
