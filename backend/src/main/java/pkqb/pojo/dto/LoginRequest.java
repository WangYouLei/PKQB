package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO
 * 用于用户登录的请求参数
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "学号不能为空")
    @Schema(description = "学号", example = "2021001")
    private String studentNo;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123456")
    private String password;
}
