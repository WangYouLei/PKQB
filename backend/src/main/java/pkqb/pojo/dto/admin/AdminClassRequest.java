package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端-班级新增/修改请求 DTO
 */
@Data
@Schema(description = "班级新增/修改请求")
public class AdminClassRequest {

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 50, message = "班级名称长度不能超过50个字符")
    @Schema(description = "班级名称")
    private String className;
}
