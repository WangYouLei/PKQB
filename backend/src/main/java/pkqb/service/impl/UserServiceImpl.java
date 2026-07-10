package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * 实现用户注册、登录、信息更新等核心业务逻辑
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
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // 密码强度校验
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6个字符");
        }
        if (request.getPassword().length() > 128) {
            throw new IllegalArgumentException("密码长度不能超过128个字符");
        }

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        LambdaQueryWrapper<UserEntity> studentNoWrapper = new LambdaQueryWrapper<>();
        studentNoWrapper.eq(UserEntity::getStudentNo, request.getStudentNo());
        if (userMapper.selectCount(studentNoWrapper) > 0) {
            throw new IllegalArgumentException("学号已被注册");
        }

        String className = request.getClassName().trim();
        LambdaQueryWrapper<ClassEntity> classWrapper = new LambdaQueryWrapper<>();
        classWrapper.eq(ClassEntity::getClassName, className);
        ClassEntity classEntity = classMapper.selectOne(classWrapper);
        if (classEntity == null) {
            throw new IllegalArgumentException("班级不存在，请重新输入");
        }
        Integer classId = classEntity.getId();

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(Argon2idUtil.hash(request.getPassword()));
        user.setStudentNo(request.getStudentNo());
        user.setClassId(classId);

        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名或学号已存在");
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getStudentNo, request.getStudentNo());
        UserEntity user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        if (!Argon2idUtil.verify(user.getPasswordHash(), request.getPassword())) {
            throw new PermissionDeniedException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        String className = null;
        if (user.getClassId() != null) {
            ClassEntity classEntity = classMapper.selectById(user.getClassId());
            if (classEntity != null) {
                className = classEntity.getClassName();
            }
        }

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

    private String getAvatarUrl(String objectKey, Long userId) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            return null;
        }
        return minioEndpoint + "/" + bucketName + "/" + objectKey;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateAvatar(Long userId, String objectKey) {
        log.info("开始更新用户 {} 的头像, objectKey: {}", userId, objectKey);

        UserEntity existingUser = userMapper.selectById(userId);
        String oldAvatarUrl = (existingUser != null && existingUser.getAvatarUrl() != null && !existingUser.getAvatarUrl().isEmpty())
                ? existingUser.getAvatarUrl() : null;

        // 先更新DB
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setAvatarUrl(objectKey);
        userMapper.updateById(user);
        log.info("用户 {} 更新头像成功: {}", userId, objectKey);

        // DB更新成功后再删旧头像
        if (oldAvatarUrl != null) {
            try {
                minioService.remove(oldAvatarUrl);
                log.info("删除用户 {} 的旧头像成功: {}", userId, oldAvatarUrl);
            } catch (Exception e) {
                log.warn("删除用户 {} 的旧头像失败: {}", userId, e.getMessage());
            }
        }

        String publicUrl = minioEndpoint + "/" + bucketName + "/" + objectKey;
        log.info("生成公开URL: {}", publicUrl);

        return publicUrl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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
