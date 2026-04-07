package pkqb.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiRubric {

    //题目
     private String question;
     //题目类型
     private String questionType;
     //选项  （选择题）
     private String[] options;
     //答案
     private String answer;
     //解析
     private String explanation;
     //计算步骤（计算题）
     private List<String> calculationSteps;
}
