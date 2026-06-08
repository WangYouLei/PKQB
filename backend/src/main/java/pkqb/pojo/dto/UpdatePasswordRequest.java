package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改密码请求")
public class UpdatePasswordRequest {
    @NotBlank(message = "原密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128个字符")
    @Schema(description = "原密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "新密码长度必须在6-128个字符之间")
    @Schema(description = "新密码")
    private String newPassword;
}
