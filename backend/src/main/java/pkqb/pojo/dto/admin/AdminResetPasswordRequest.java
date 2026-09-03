package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端-重置密码请求 DTO
 */
@Data
@Schema(description = "重置密码请求")
public class AdminResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度应在6-128个字符之间")
    @Schema(description = "新密码")
    private String newPassword;
}
