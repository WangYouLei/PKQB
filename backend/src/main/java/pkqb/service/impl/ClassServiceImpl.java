package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.config.GlobalExceptionHandler.ResourceNotFoundException;
import pkqb.mapper.ClassMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.admin.AdminClassRequest;
import pkqb.pojo.entity.ClassEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.ClassService;

import java.util.List;

/**
 * 班级服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassMapper classMapper;
    private final UserMapper userMapper;

    @Override
    public List<ClassEntity> list() {
        LambdaQueryWrapper<ClassEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ClassEntity::getClassName);
        return classMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassEntity create(AdminClassRequest request) {
        String className = request.getClassName().trim();
        LambdaQueryWrapper<ClassEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassEntity::getClassName, className);
        if (classMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("班级名称已存在");
        }
        ClassEntity entity = new ClassEntity();
        entity.setClassName(className);
        classMapper.insert(entity);
        log.info("[班级管理] 新增班级: {}", className);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassEntity update(Integer id, AdminClassRequest request) {
        ClassEntity entity = classMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("班级不存在");
        }
        String className = request.getClassName().trim();
        LambdaQueryWrapper<ClassEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassEntity::getClassName, className)
               .ne(ClassEntity::getId, id);
        if (classMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("班级名称已存在");
        }
        entity.setClassName(className);
        classMapper.updateById(entity);
        log.info("[班级管理] 修改班级: id={}, name={}", id, className);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        ClassEntity entity = classMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("班级不存在");
        }
        // 检查是否有未删除账号引用该班级（@TableLogic 自动过滤已删账号）
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getClassId, id);
        long count = userMapper.selectCount(wrapper);
        if (count > 0) {
            throw new IllegalArgumentException("该班级下仍有 " + count + " 个账号，无法删除");
        }
        classMapper.deleteById(id);
        log.info("[班级管理] 删除班级: id={}", id);
    }
}
