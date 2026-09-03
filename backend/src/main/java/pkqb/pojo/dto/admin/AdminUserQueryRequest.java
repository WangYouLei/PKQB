package pkqb.pojo.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理端-账号分页查询请求 DTO
 */
@Data
@Schema(description = "账号分页查询请求")
public class AdminUserQueryRequest {

    @Schema(description = "页码，默认1", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数，默认10", example = "10")
    private Integer size = 10;

    @Schema(description = "关键字（用户名/学号模糊匹配）")
    private String keyword;

    @Schema(description = "班级ID筛选")
    private Integer classId;

    @Schema(description = "角色筛选：0普通用户 1管理员")
    private Integer role;
}
