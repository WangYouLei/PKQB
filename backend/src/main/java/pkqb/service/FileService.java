package pkqb.service;

import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.dto.PresignedUrlResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * HTML文件服务接口
 */
public interface FileService {

    /**
     * 上传文件
     */
    FileResponse uploadFile(MultipartFile file, Long userId,String fileName, boolean isPublic);

    /**
     * 获取文件预签名下载 URL
     */
    PresignedUrlResponse getPresignedUrl(Long fileId, Long userId);

    /**
     * 获取用户的文件列表
     */
    List<FileResponse> getUserFiles(Long userId);

    /**
     * 删除文件（同时删除 MinIO 对象和数据库记录）
     */
    void deleteFile(Long fileId, Long userId);

    /**
     * 获取班级公开文件列表
     */
    List<FileResponse> getClassPublicFiles(Long userId);
}
