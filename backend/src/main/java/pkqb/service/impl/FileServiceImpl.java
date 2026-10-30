package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.config.GlobalExceptionHandler.ResourceNotFoundException;
import pkqb.config.GlobalExceptionHandler.PermissionDeniedException;
import pkqb.mapper.FileMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.entity.FileEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.FileService;
import pkqb.service.MinioService;
import pkqb.service.RagDataIsolationService;

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
    private final RagDataIsolationService ragDataIsolationService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

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

        fileMapper.deleteById(fileId);

        minioService.remove(entity.getMinioKey());

        // 同步清理向量库中该文件的文档片段，避免已删文件仍可被 RAG 检索到
        ragDataIsolationService.deleteDocumentsByFileId(fileId);
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
    public FileDownload downloadFile(Long fileId, Long userId) {
        // 一次查询完成元数据获取与归属校验，原来元数据与内容分两次查库、两次鉴权
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new ResourceNotFoundException("文件不存在");
        }

        if (!entity.getUserId().equals(userId) && Boolean.TRUE.equals(entity.getIsPrivate())) {
            throw new PermissionDeniedException("无权访问该文件");
        }

        byte[] content = minioService.getFile(entity.getMinioKey());
        log.info("[文件管理] 用户 {} 下载文件 fileId={} size={}B", userId, fileId, content.length);
        return new FileDownload(entity, content);
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
        boolean privacyChanged = isPrivate != null && !isPrivate.equals(entity.getIsPrivate());
        if (privacyChanged) {
            entity.setIsPrivate(isPrivate);
        }

        fileMapper.updateById(entity);

        if (privacyChanged) {
            // 同步向量库中该文件片段的 isPrivate 元数据，保证 RAG 检索可见性与文件列表一致
            ragDataIsolationService.updateDocumentsPrivacy(fileId, isPrivate);
        }
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
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername, (a, b) -> a));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteFiles(List<Long> fileIds, Long userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return 0;
        }

        // 一次性批量查出本批文件再过滤本人文件，避免循环内逐条selectById（100个文件=100次查询）
        List<FileEntity> ownedFiles = fileMapper.selectBatchIds(fileIds.stream().distinct().toList()).stream()
                .filter(entity -> userId.equals(entity.getUserId()))
                .toList();

        if (!ownedFiles.isEmpty()) {
            // 批量删除DB记录
            fileMapper.deleteBatchIds(ownedFiles.stream().map(FileEntity::getId).toList());
            // 逐个清理MinIO对象（失败仅告警，接受孤立文件）
            for (FileEntity entity : ownedFiles) {
                try {
                    minioService.remove(entity.getMinioKey());
                } catch (Exception e) {
                    log.warn("[文件删除] Minio文件删除失败，fileId={}, minioKey={}: {}",
                            entity.getId(), entity.getMinioKey(), e.getMessage());
                }
            }
            // 同步清理向量库中这些文件的文档片段（失败仅告警，见 RagDataIsolationServiceImpl）
            ragDataIsolationService.deleteDocumentsByFileIds(
                    ownedFiles.stream().map(FileEntity::getId).toList());
        }

        log.info("[批量删除文件] userId={}, 总数={}, 成功={}", userId, fileIds.size(), ownedFiles.size());
        return ownedFiles.size();
    }
}
