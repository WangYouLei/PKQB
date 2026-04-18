package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度为2-64个字符")
    @Schema(description = "用户名", example = "张三")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度为6-64个字符")
    @Schema(description = "密码", example = "123456")
    private String password;

    @NotBlank(message = "学号不能为空")
    @Schema(description = "学号", example = "2021001")
    private String studentNo;

    @Schema(description = "班级ID")
    private Integer classId;
    
    @Schema(description = "班级名称")
    private String className;
}
