package pkqb.service;

import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.entity.FileEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {

    FileResponse uploadFile(MultipartFile file, Long userId,String fileName, boolean isPrivate);

    String getPublicUrl(Long fileId, Long userId);

    List<FileResponse> getUserFiles(Long userId);

    void deleteFile(Long fileId, Long userId);

    List<FileResponse> getClassPublicFiles(Long userId);

    byte[] downloadFile(Long fileId, Long userId);

    FileEntity getFileEntity(Long fileId, Long userId);
}
