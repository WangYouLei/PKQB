package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端-账号列表展示 VO
 */
@Data
@Schema(description = "账号展示对象")
public class AdminUserVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "班级ID")
    private Integer classId;

    @Schema(description = "班级名称")
    private String className;

    @Schema(description = "角色：0普通用户 1管理员")
    private Integer role;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
