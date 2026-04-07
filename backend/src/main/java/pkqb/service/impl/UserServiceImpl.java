package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pkqb.pojo.dto.LoginRequest;
import pkqb.pojo.dto.LoginResponse;
import pkqb.pojo.dto.RegisterRequest;
import pkqb.mapper.ClassMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.ClassEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.UserService;
import pkqb.util.Argon2idUtil;
import pkqb.util.JwtUtil;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final JwtUtil jwtUtil;

    @Override
    public void register(RegisterRequest request) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
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
            throw new RuntimeException("用户不存在");
        }

        // 验证密码
        if (!Argon2idUtil.verify(user.getPasswordHash(), request.getPassword())) {
            throw new RuntimeException("密码错误");
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

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getStudentNo(),
                user.getClassId(),
                className
        );
    }
}
