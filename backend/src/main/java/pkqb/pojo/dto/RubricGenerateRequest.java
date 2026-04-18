package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 生成Rubric HTML文件的请求
 */
@Data
@Schema(description = "生成HTML请求")
public class RubricGenerateRequest {

    @NotNull(message = "试卷ID不能为空")
    @Schema(description = "试卷ID", example = "1")
    private Long rubricId;

    @Schema(description = "文件名（可选，默认使用Rubric标题）")
    private String fileName;

    @Schema(description = "是否私有：true=私有（仅自己可见），false=公开（班级可见）")
    private Boolean isPrivate;
}