package pkqb.service;

import pkqb.pojo.dto.LoginRequest;
import pkqb.pojo.dto.LoginResponse;
import pkqb.pojo.dto.RegisterRequest;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
}
