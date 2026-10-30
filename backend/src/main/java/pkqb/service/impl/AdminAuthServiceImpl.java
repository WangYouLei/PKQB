package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.config.GlobalExceptionHandler.PermissionDeniedException;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.admin.AdminLoginRequest;
import pkqb.pojo.dto.admin.AdminLoginResponse;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.AdminAuthService;
import pkqb.util.Argon2idUtil;
import pkqb.util.JwtUtil;

/**
 * 管理端认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminLoginResponse login(AdminLoginRequest request) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, request.getUsername());
        UserEntity user = userMapper.selectOne(wrapper);

        // 用户不存在或密码错误统一返回 400，避免暴露用户是否存在
        if (user == null || !Argon2idUtil.verify(user.getPasswordHash(), request.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        // 非管理员返回 403
        if (user.getRole() == null || user.getRole() != 1) {
            throw new PermissionDeniedException("该账号无管理员权限");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("[管理端登录] 管理员 {} 登录成功", user.getUsername());
        return new AdminLoginResponse(token, user.getId(), user.getUsername(), user.getRole());
    }
}
