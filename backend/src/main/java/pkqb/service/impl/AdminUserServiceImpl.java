package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.config.GlobalExceptionHandler.ResourceNotFoundException;
import pkqb.mapper.ClassMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.admin.AdminUserCreateRequest;
import pkqb.pojo.dto.admin.AdminUserQueryRequest;
import pkqb.pojo.dto.admin.AdminUserUpdateRequest;
import pkqb.pojo.dto.admin.AdminUserVO;
import pkqb.pojo.entity.ClassEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.AdminUserService;
import pkqb.util.Argon2idUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理端账号服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final ClassMapper classMapper;

    @Override
    public IPage<AdminUserVO> page(AdminUserQueryRequest request) {
        Page<UserEntity> page = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = request.getKeyword();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(UserEntity::getUsername, kw)
                    .or().like(UserEntity::getStudentNo, kw));
        }
        if (request.getClassId() != null) {
            wrapper.eq(UserEntity::getClassId, request.getClassId());
        }
        if (request.getRole() != null) {
            wrapper.eq(UserEntity::getRole, request.getRole());
        }
        wrapper.orderByDesc(UserEntity::getCreateTime);

        IPage<UserEntity> result = userMapper.selectPage(page, wrapper);

        // 批量查询班级名称，避免 N+1
        List<Integer> classIds = result.getRecords().stream()
                .map(UserEntity::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, String> classNameMap = Collections.emptyMap();
        if (!classIds.isEmpty()) {
            List<ClassEntity> classes = classMapper.selectBatchIds(classIds);
            classNameMap = classes.stream()
                    .collect(Collectors.toMap(ClassEntity::getId, ClassEntity::getClassName, (a, b) -> a));
        }

        final Map<Integer, String> finalMap = classNameMap;
        return result.convert(user -> {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setStudentNo(user.getStudentNo());
            vo.setClassId(user.getClassId());
            vo.setClassName(user.getClassId() == null ? null : finalMap.get(user.getClassId()));
            vo.setRole(user.getRole());
            vo.setCreateTime(user.getCreateTime());
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(AdminUserCreateRequest request) {
        // 校验用户名
        LambdaQueryWrapper<UserEntity> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(UserEntity::getUsername, request.getUsername());
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        // 校验学号
        LambdaQueryWrapper<UserEntity> studentNoWrapper = new LambdaQueryWrapper<>();
        studentNoWrapper.eq(UserEntity::getStudentNo, request.getStudentNo());
        if (userMapper.selectCount(studentNoWrapper) > 0) {
            throw new IllegalArgumentException("学号已存在");
        }
        // 校验班级
        if (classMapper.selectById(request.getClassId()) == null) {
            throw new IllegalArgumentException("班级不存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setStudentNo(request.getStudentNo());
        user.setClassId(request.getClassId());
        user.setPasswordHash(Argon2idUtil.hash(request.getPassword()));
        user.setRole(request.getRole() == null ? 0 : request.getRole());
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名或学号已存在");
        }
        log.info("[账号管理] 新增账号: username={}, studentNo={}", user.getUsername(), user.getStudentNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminUserUpdateRequest request) {
        UserEntity existing = userMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("账号不存在");
        }
        // 校验用户名
        if (request.getUsername() != null) {
            LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserEntity::getUsername, request.getUsername())
                   .ne(UserEntity::getId, id);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new IllegalArgumentException("用户名已存在");
            }
        }
        // 校验学号
        if (request.getStudentNo() != null) {
            LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserEntity::getStudentNo, request.getStudentNo())
                   .ne(UserEntity::getId, id);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new IllegalArgumentException("学号已存在");
            }
        }
        // 校验班级
        if (request.getClassId() != null && classMapper.selectById(request.getClassId()) == null) {
            throw new IllegalArgumentException("班级不存在");
        }

        UserEntity user = new UserEntity();
        user.setId(id);
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getStudentNo() != null) user.setStudentNo(request.getStudentNo());
        if (request.getClassId() != null) user.setClassId(request.getClassId());
        if (request.getRole() != null) user.setRole(request.getRole());
        userMapper.updateById(user);
        log.info("[账号管理] 修改账号: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UserEntity existing = userMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("账号不存在");
        }
        userMapper.deleteById(id); // @TableLogic 逻辑删除
        log.info("[账号管理] 删除账号(逻辑): id={}, username={}", id, existing.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        UserEntity existing = userMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("账号不存在");
        }
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setPasswordHash(Argon2idUtil.hash(newPassword));
        userMapper.updateById(user);
        log.info("[账号管理] 重置密码: id={}", id);
    }
}
