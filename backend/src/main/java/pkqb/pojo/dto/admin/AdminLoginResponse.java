package pkqb.pojo.dto.admin;

import lombok.Data;

/**
 * 管理员登录响应 DTO
 */
@Data
public class AdminLoginResponse {

    private String token;
    private Long userId;
    private String username;
    private Integer role;

    public AdminLoginResponse(String token, Long userId, String username, Integer role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }
}
