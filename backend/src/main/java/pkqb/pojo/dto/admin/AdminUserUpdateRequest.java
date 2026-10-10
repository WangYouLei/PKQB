package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端-修改账号请求 DTO
 */
@Data
@Schema(description = "修改账号请求")
public class AdminUserUpdateRequest {

    @Size(min = 2, max = 20, message = "用户名长度应在2-20个字符之间")
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "班级ID")
    private Integer classId;

    @Schema(description = "角色：0普通用户 1管理员")
    private Integer role;
}
