package pkqb.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 复习结果提交请求
 */
@Data
public class ReviewResultRequest {

    /** 错题记录ID */
    @NotNull(message = "错题ID不能为空")
    private Long wrongQuestionId;

    /** 复习结果：true=答对，false=答错 */
    @NotNull(message = "复习结果不能为空")
    private Boolean correct;
}
