package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pkqb.config.GlobalExceptionHandler.ResourceNotFoundException;
import pkqb.config.GlobalExceptionHandler.PermissionDeniedException;
import pkqb.pojo.dto.LoginRequest;
import pkqb.pojo.dto.LoginResponse;
import pkqb.pojo.dto.RegisterRequest;
import pkqb.mapper.ClassMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.ClassEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.MinioService;
import pkqb.service.UserService;
import pkqb.util.Argon2idUtil;
import pkqb.util.JwtUtil;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final JwtUtil jwtUtil;
    private final MinioService minioService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    public void register(RegisterRequest request) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 检查学号是否已存在
        LambdaQueryWrapper<UserEntity> studentNoWrapper = new LambdaQueryWrapper<>();
        studentNoWrapper.eq(UserEntity::getStudentNo, request.getStudentNo());
        if (userMapper.selectCount(studentNoWrapper) > 0) {
            throw new IllegalArgumentException("学号已被注册");
        }

        // 处理班级
        Integer classId = request.getClassId();
        if (classId == null && request.getClassName() != null && !request.getClassName().trim().isEmpty()) {
            // 根据班级名称查找或创建班级
            LambdaQueryWrapper<ClassEntity> classWrapper = new LambdaQueryWrapper<>();
            classWrapper.eq(ClassEntity::getClassName, request.getClassName().trim());
            ClassEntity classEntity = classMapper.selectOne(classWrapper);
            if (classEntity == null) {
                // 创建新班级
                classEntity = new ClassEntity();
                classEntity.setClassName(request.getClassName().trim());
                classMapper.insert(classEntity);
            }
            classId = classEntity.getId();
        }

        // 创建用户
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(Argon2idUtil.hash(request.getPassword()));
        user.setStudentNo(request.getStudentNo());
        user.setClassId(classId);

        userMapper.insert(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 根据学号查询用户
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getStudentNo, request.getStudentNo());
        UserEntity user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 验证密码
        if (!Argon2idUtil.verify(user.getPasswordHash(), request.getPassword())) {
            throw new PermissionDeniedException("密码错误");
        }

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 获取班级名称
        String className = null;
        if (user.getClassId() != null) {
            ClassEntity classEntity = classMapper.selectById(user.getClassId());
            if (classEntity != null) {
                className = classEntity.getClassName();
            }
        }

        // 获取头像 URL
        String avatarUrl = getAvatarUrl(user.getAvatarUrl(), user.getId());

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getStudentNo(),
                user.getClassId(),
                className,
                avatarUrl
        );
    }

    /**
     * 获取用户头像公开URL
     */
    private String getAvatarUrl(String objectKey, Long userId) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            return null;
        }
        
        // 直接返回公开URL
        return minioEndpoint + "/" + bucketName + "/" + objectKey;
    }

    @Override
    public String updateAvatar(Long userId, String objectKey) {
        log.info("开始更新用户 {} 的头像, objectKey: {}", userId, objectKey);
        
        // 先删除旧头像（如果存在）
        UserEntity existingUser = userMapper.selectById(userId);
        if (existingUser != null && existingUser.getAvatarUrl() != null && !existingUser.getAvatarUrl().isEmpty()) {
            try {
                minioService.remove(existingUser.getAvatarUrl());
                log.info("删除用户 {} 的旧头像成功: {}", userId, existingUser.getAvatarUrl());
            } catch (Exception e) {
                log.warn("删除用户 {} 的旧头像失败: {}", userId, e.getMessage());
            }
        }

        // 更新用户头像路径
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setAvatarUrl(objectKey);
        userMapper.updateById(user);
        log.info("用户 {} 更新头像成功: {}", userId, objectKey);

        // 返回公开URL
        String publicUrl = minioEndpoint + "/" + bucketName + "/" + objectKey;
        log.info("生成公开URL: {}", publicUrl);
        
        return publicUrl;
    }

    @Override
    public void updateUsername(Long userId, String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        
        username = username.trim();
        
        if (username.length() < 2 || username.length() > 20) {
            throw new IllegalArgumentException("用户名长度应在2-20个字符之间");
        }
        
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username)
               .ne(UserEntity::getId, userId);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername(username);
        userMapper.updateById(user);
        log.info("用户 {} 更新用户名成功: {}", userId, username);
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new IllegalArgumentException("原密码不能为空");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6个字符");
        }
        
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        
        if (!Argon2idUtil.verify(user.getPasswordHash(), oldPassword)) {
            throw new PermissionDeniedException("原密码错误");
        }
        
        UserEntity updateUser = new UserEntity();
        updateUser.setId(userId);
        updateUser.setPasswordHash(Argon2idUtil.hash(newPassword));
        userMapper.updateById(updateUser);
        log.info("用户 {} 更新密码成功", userId);
    }
}
