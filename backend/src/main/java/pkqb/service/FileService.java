package pkqb.service;

import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.entity.FileEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务接口
 * 提供文件上传、下载、删除、更新等核心业务功能
 */
public interface FileService {

    /**
     * 上传文件
     * 将文件上传到MinIO对象存储，并记录文件信息到数据库
     *
     * @param file 上传的文件
     * @param userId 用户ID
     * @param fileName 文件显示名称
     * @param isPrivate 是否私有
     * @return 文件响应信息
     * @throws IllegalArgumentException 当文件或文件名为空时抛出
     */
    FileResponse uploadFile(MultipartFile file, Long userId,String fileName, boolean isPrivate);

    /**
     * 获取用户文件列表
     * 获取指定用户上传的所有文件
     *
     * @param userId 用户ID
     * @return 文件响应列表
     */
    List<FileResponse> getUserFiles(Long userId);

    /**
     * 删除文件
     * 从MinIO和数据库中删除指定文件
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @throws pkqb.config.GlobalExceptionHandler.ResourceNotFoundException 文件不存在时抛出
     * @throws pkqb.config.GlobalExceptionHandler.PermissionDeniedException 无权删除时抛出
     */
    void deleteFile(Long fileId, Long userId);

    /**
     * 获取班级公开文件列表
     * 获取同班级其他用户公开的文件
     *
     * @param userId 用户ID
     * @return 公开文件列表
     * @throws pkqb.config.GlobalExceptionHandler.ResourceNotFoundException 用户不存在或未加入班级时抛出
     */
    List<FileResponse> getClassPublicFiles(Long userId);

    /**
     * 下载文件
     * 获取文件内容字节数组
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件内容字节数组
     * @throws pkqb.config.GlobalExceptionHandler.ResourceNotFoundException 文件不存在时抛出
     * @throws pkqb.config.GlobalExceptionHandler.PermissionDeniedException 无权访问时抛出
     */
    byte[] downloadFile(Long fileId, Long userId);

    /**
     * 获取文件实体
     * 获取文件的基本信息
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件实体
     * @throws pkqb.config.GlobalExceptionHandler.ResourceNotFoundException 文件不存在时抛出
     * @throws pkqb.config.GlobalExceptionHandler.PermissionDeniedException 无权访问时抛出
     */
    FileEntity getFileEntity(Long fileId, Long userId);

    /**
     * 更新文件信息
     * 更新文件名称和可见性
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @param fileName 新文件名（可选）
     * @param isPrivate 是否私有（可选）
     * @return 更新后的文件响应
     * @throws pkqb.config.GlobalExceptionHandler.ResourceNotFoundException 文件不存在时抛出
     * @throws pkqb.config.GlobalExceptionHandler.PermissionDeniedException 无权修改时抛出
     */
    FileResponse updateFile(Long fileId, Long userId, String fileName, Boolean isPrivate);
}
