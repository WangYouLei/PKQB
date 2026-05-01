package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pkqb.config.GlobalExceptionHandler.ResourceNotFoundException;
import pkqb.config.GlobalExceptionHandler.PermissionDeniedException;
import pkqb.mapper.FileMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.entity.FileEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.FileService;
import pkqb.service.MinioService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件服务实现类
 * 实现文件上传、下载、删除、更新等核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final UserMapper userMapper;
    private final MinioService minioService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileResponse uploadFile(MultipartFile file, Long userId, String fileName, boolean isPrivate) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".text";
        String objectKey = "file/" + userId + "/" + UUID.randomUUID() + extension;

        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }

        FileEntity entity = new FileEntity();
        entity.setUserId(userId);
        entity.setFileName(fileName);
        entity.setMinioKey(objectKey);
        entity.setIsPrivate(isPrivate);

        fileMapper.insert(entity);

        return toResponse(entity);
    }

    @Override
    public List<FileResponse> getUserFiles(Long userId) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEntity::getUserId, userId)
               .orderByDesc(FileEntity::getCreateTime);

        List<FileEntity> entities = fileMapper.selectList(wrapper);
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
            throw new ResourceNotFoundException("文件不存在");
        }

        if (!entity.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权删除该文件");
        }

        minioService.remove(entity.getMinioKey());

        fileMapper.deleteById(fileId);
    }

    @Override
    public List<FileResponse> getClassPublicFiles(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getClassId() == null) {
            throw new ResourceNotFoundException("用户不存在或未加入班级");
        }

        LambdaQueryWrapper<UserEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(UserEntity::getClassId, user.getClassId());
        List<UserEntity> classmates = userMapper.selectList(userWrapper);
        List<Long> classUserIds = classmates.stream()
                .map(UserEntity::getId)
                .collect(Collectors.toList());

        if (classUserIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEntity::getIsPrivate, false)
               .in(FileEntity::getUserId, classUserIds)
               .ne(FileEntity::getUserId, userId)
               .orderByDesc(FileEntity::getCreateTime);

        List<FileEntity> entities = fileMapper.selectList(wrapper);
        Map<Long, String> userNameMap = batchGetUserNames(entities);
        return entities.stream()
                .map(entity -> toResponse(entity, userNameMap))
                .collect(Collectors.toList());
    }

    @Override
    public byte[] downloadFile(Long fileId, Long userId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new ResourceNotFoundException("文件不存在");
        }

        if (!entity.getUserId().equals(userId) && Boolean.TRUE.equals(entity.getIsPrivate())) {
            throw new PermissionDeniedException("无权访问该文件");
        }

        return minioService.getFile(entity.getMinioKey());
    }

    @Override
    public FileEntity getFileEntity(Long fileId, Long userId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new ResourceNotFoundException("文件不存在");
        }

        if (!entity.getUserId().equals(userId) && Boolean.TRUE.equals(entity.getIsPrivate())) {
            throw new PermissionDeniedException("无权访问该文件");
        }

        return entity;
    }

    @Override
    public FileResponse updateFile(Long fileId, Long userId, String fileName, Boolean isPrivate) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new ResourceNotFoundException("文件不存在");
        }

        if (!entity.getUserId().equals(userId)) {
            throw new PermissionDeniedException("无权修改该文件");
        }

        if (fileName != null && !fileName.trim().isEmpty()) {
            entity.setFileName(fileName);
        }
        if (isPrivate != null) {
            entity.setIsPrivate(isPrivate);
        }

        fileMapper.updateById(entity);
        return toResponse(entity);
    }

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
