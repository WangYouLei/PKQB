package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 试卷请求 DTO
 * 用于创建或更新试卷的请求参数
 */
@Data
@Schema(description = "试卷请求")
public class RubricRequest {
    
    @Schema(description = "试卷ID（用于更新）")
    private Long id;
    
    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 200, message = "试卷标题长度不能超过200个字符")
    @Pattern(regexp = "^[^<>]*$", message = "试卷标题不能包含<>字符")
    @Schema(description = "试卷标题", example = "2024年期末考试卷")
    private String title;

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 100, message = "班级名称长度不能超过100个字符")
    @Pattern(regexp = "^[^<>]*$", message = "班级名称不能包含<>字符")
    @Schema(description = "班级名称", example = "计算机1班")
    private String className;
    
    @Schema(description = "创建者ID")
    private Long createId;
    
    @Schema(description = "创建者学号")
    private String createStudentNo;
    
    @Schema(description = "是否私有：true=私有（仅自己可见），false=公开（班级可见）")
    private Boolean isPrivate = false;
    
    @Schema(description = "题目列表")
    @Valid
    private List<AiRubric> rubrics;
}
