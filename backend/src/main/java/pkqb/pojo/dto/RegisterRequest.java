package pkqb.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度为2-64个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度为6-64个字符")
    private String password;

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    private Integer classId;
    
    private String className;
}
