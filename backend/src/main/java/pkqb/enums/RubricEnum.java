package pkqb.enums;

public enum RubricEnum {

    QUESTION("题目内容", "question"),
    QUESTION_TYPE("题目类型", "questionType"),
    OPTIONS("选项A,B,C,D", "options"),
    ANSWER("正确答案，多选题用逗号分隔如：A,B,C；简答题和计算题直接写答案内容；没有答案则为空字符串", "answer"),
    EXPLANATION("解析内容，没有解析则为空字符串", "explanation"),
    CALCULATION_STEPS("步骤1,步骤2,步骤3", "calculationSteps");

    private String value;
    private String code;

    RubricEnum(String value, String code) {
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
