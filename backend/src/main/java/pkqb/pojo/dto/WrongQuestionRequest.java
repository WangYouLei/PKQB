package pkqb.pojo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加错题请求
 */
@Data
public class WrongQuestionRequest {

    /** 原题目ID */
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /** 所属试卷ID */
    @NotNull(message = "试卷ID不能为空")
    private Long rubricId;

    /** 用户错误答案 */
    @Size(max = 2000, message = "用户答案长度不能超过2000个字符")
    @Pattern(regexp = "^[^<>]*$", message = "用户答案不能包含<>字符")
    private String userAnswer;
}
