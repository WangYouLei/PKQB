package pkqb.pojo.dto;

import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String studentNo;
    private Integer classId;
    private String className;

    public LoginResponse(String token, Long userId, String username, String studentNo, Integer classId, String className) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.studentNo = studentNo;
        this.classId = classId;
        this.className = className;
    }
}
