package pkqb.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class RubricRequest {
    //试卷ID (用于更新)
    private Long id;
    //试卷标题
    private String title;
    //班级名称
    private String className;
    //创建者ID
    private Long createId;
    //创建者学号
    private String createStudentNo;
    //是否公开
    private Boolean isPublic;
    //题目
    private List<AiRubric> rubrics;
}
