package pkqb.enums;

public enum RubricQuestionTypeEnum {
    //单选题
    SINGLE_CHOICE("单选题", "single_choice"),
    //多选题
    MULTIPLE_CHOICE("多选题", "multiple_choice"),
    //判断题
    TRUE_FALSE("判断题", "true_false"),
    //简答题
    SHORT_ANSWER("简答题", "short_answer"),
    //计算题
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
