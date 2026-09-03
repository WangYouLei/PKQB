package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端-新增账号请求 DTO
 */
@Data
@Schema(description = "新增账号请求")
public class AdminUserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度应在2-20个字符之间")
    @Schema(description = "用户名")
    private String username;

    @NotBlank(message = "学号不能为空")
    @Schema(description = "学号")
    private String studentNo;

    @NotNull(message = "班级不能为空")
    @Schema(description = "班级ID")
    private Integer classId;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度应在6-128个字符之间")
    @Schema(description = "初始密码")
    private String password;

    @Schema(description = "角色：0普通用户 1管理员，默认0")
    private Integer role = 0;
}
