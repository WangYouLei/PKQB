package pkqb.service;

import pkqb.pojo.dto.admin.AdminLoginRequest;
import pkqb.pojo.dto.admin.AdminLoginResponse;

/**
 * 管理端认证服务接口
 */
public interface AdminAuthService {

    /** 管理员登录 */
    AdminLoginResponse login(AdminLoginRequest request);
}
