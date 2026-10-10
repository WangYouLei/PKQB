package pkqb.service;

import pkqb.pojo.dto.LoginRequest;
import pkqb.pojo.dto.LoginResponse;
import pkqb.pojo.dto.RegisterRequest;

/**
 * 用户服务接口
 * 提供用户注册、登录、信息更新等核心业务功能
 */
public interface UserService {

    /**
     * 用户注册
     * 创建新用户账号，处理班级关联
     *
     * @param request 注册请求，包含用户名、密码、学号、班级信息
     * @throws IllegalArgumentException 当用户名或学号已存在时抛出
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     * 验证用户身份并生成JWT令牌
     *
     * @param request 登录请求，包含学号和密码
     * @return 登录响应，包含JWT令牌和用户信息
     * @throws pkqb.config.GlobalExceptionHandler.ResourceNotFoundException 用户不存在时抛出
     * @throws pkqb.config.GlobalExceptionHandler.PermissionDeniedException 密码错误时抛出
     */
    LoginResponse login(LoginRequest request);

    /**
     * 更新用户头像
     * 更新用户头像路径，并删除旧头像文件
     *
     * @param userId 用户ID
     * @param objectKey MinIO对象存储路径
     * @return 头像公开访问URL
     */
    String updateAvatar(Long userId, String objectKey);

    /**
     * 更新用户名
     * 修改用户的昵称/用户名
     *
     * @param userId 用户ID
     * @param username 新用户名
     * @throws IllegalArgumentException 当用户名为空或已存在时抛出
     */
    void updateUsername(Long userId, String username);

    /**
     * 更新密码
     * 验证原密码后更新为新密码
     *
     * @param userId 用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @throws IllegalArgumentException 当密码为空或长度不足时抛出
     * @throws pkqb.config.GlobalExceptionHandler.PermissionDeniedException 原密码错误时抛出
     */
    void updatePassword(Long userId, String oldPassword, String newPassword);
}
