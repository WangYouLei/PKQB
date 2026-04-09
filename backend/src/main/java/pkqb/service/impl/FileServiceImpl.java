package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pkqb.mapper.FileMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.dto.PresignedUrlResponse;
import pkqb.pojo.entity.FileEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.FileService;
import pkqb.service.MinioService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTML文件服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final UserMapper userMapper;
    private final MinioService minioService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResponse uploadFile(MultipartFile file, Long userId,String fileName, boolean isPrivate) {
        // 生成唯一对象路径
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".text";
        String objectKey = "file/" + userId + "/" + UUID.randomUUID() + extension;

        // 上传到 MinIO
        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (Exception e) {
            log.info("上传文件失败,e={}", e);
        }

        // 保存数据库记录
        FileEntity entity = new FileEntity();
        entity.setUserId(userId);
        entity.setFileName(fileName);
        entity.setMinioKey(objectKey);
        entity.setIsPrivate(isPrivate);

        fileMapper.insert(entity);

        return toResponse(entity);
    }

    @Override
    public PresignedUrlResponse getPresignedUrl(Long fileId, Long userId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new RuntimeException("文件不存在");
        }

        // 权限校验：只能访问自己的文件或公开文件
        if (!entity.getUserId().equals(userId) && !Boolean.TRUE.equals(entity.getIsPrivate())) {
            throw new RuntimeException("无权访问该文件");
        }

        String presignedUrl = minioService.getPresignedGetUrl(entity.getMinioKey());
        return new PresignedUrlResponse(presignedUrl, fileId, entity.getFileName());
    }

    @Override
    public List<FileResponse> getUserFiles(Long userId) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEntity::getUserId, userId)
               .orderByDesc(FileEntity::getCreateTime);

        List<FileEntity> entities = fileMapper.selectList(wrapper);
        // 批量查询用户名称，避免 N+1
        Map<Long, String> userNameMap = batchGetUserNames(entities);
        return entities.stream()
                .map(entity -> toResponse(entity, userNameMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId, Long userId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new RuntimeException("文件不存在");
        }

        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该文件");
        }

        // 删除 MinIO 对象
        minioService.remove(entity.getMinioKey());

        // 删除数据库记录
        fileMapper.deleteById(fileId);
    }

    @Override
    public List<FileResponse> getClassPublicFiles(Long userId) {
        // 获取用户的班级信息
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getClassId() == null) {
            throw new RuntimeException("用户不存在或未加入班级");
        }

        // 先查询同班所有用户ID，避免SQL拼接
        LambdaQueryWrapper<UserEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(UserEntity::getClassId, user.getClassId());
        List<UserEntity> classmates = userMapper.selectList(userWrapper);
        List<Long> classUserIds = classmates.stream()
                .map(UserEntity::getId)
                .collect(Collectors.toList());

        if (classUserIds.isEmpty()) {
            return List.of();
        }

        // 获取该班级中所有用户的公开文件（排除当前用户）
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEntity::getIsPrivate, true)
               .in(FileEntity::getUserId, classUserIds)
               .ne(FileEntity::getUserId, userId)  // 排除当前用户
               .orderByDesc(FileEntity::getCreateTime);

        List<FileEntity> entities = fileMapper.selectList(wrapper);
        // 批量查询用户名称，避免 N+1
        Map<Long, String> userNameMap = batchGetUserNames(entities);
        return entities.stream()
                .map(entity -> toResponse(entity, userNameMap))
                .collect(Collectors.toList());
    }

    // 批量获取用户名称，避免 N+1 查询
    private Map<Long, String> batchGetUserNames(List<FileEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = entities.stream()
                .map(FileEntity::getUserId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<UserEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(UserEntity::getId, userIds);
        List<UserEntity> users = userMapper.selectList(userWrapper);
        return users.stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
    }

    private FileResponse toResponse(FileEntity entity) {
        return toResponse(entity, Map.of());
    }

    private FileResponse toResponse(FileEntity entity, Map<Long, String> userNameMap) {
        FileResponse response = new FileResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUserId());
        response.setFileName(entity.getFileName());
        response.setIsPrivate(entity.getIsPrivate());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        
        // 从缓存Map中获取创建者名称
        String creatorName = userNameMap.get(entity.getUserId());
        if (creatorName == null) {
            UserEntity user = userMapper.selectById(entity.getUserId());
            if (user != null) {
                creatorName = user.getUsername();
            }
        }
        response.setCreatorName(creatorName);
        
        return response;
    }
}
