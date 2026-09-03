package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员登录请求 DTO
 */
@Data
@Schema(description = "管理员登录请求")
public class AdminLoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128个字符")
    @Schema(description = "密码", example = "123456")
    private String password;
}
