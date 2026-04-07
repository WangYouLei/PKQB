package pkqb.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.dto.PresignedUrlResponse;
import pkqb.service.FileService;

import java.util.List;

/**
 * 文件控制器
 */
@RestController
@RequestMapping("/api/files")
//添加构造函数(为使用的final添加上构造函数，让Bean可以注入)
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传 文件
     */
    @PostMapping("/upload")
    public Result<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") Long userId,
            @RequestParam("file_name") String fileName,
            @RequestParam("is_public") boolean isPublic
    ) {
        FileResponse response = fileService.uploadFile(file, userId,fileName,isPublic);
        return Result.success("上传成功", response);
    }

    /**
     * 获取文件预签名下载 URL
     */
    @GetMapping("/presigned/{fileId}")
    public Result<PresignedUrlResponse> getPresignedUrl(
            @PathVariable Long fileId,
            @RequestAttribute("userId") Long userId) {
        PresignedUrlResponse response = fileService.getPresignedUrl(fileId, userId);
        return Result.success(response);
    }

    /**
     * 获取当前用户的文件列表
     */
    @GetMapping("/my")
    public Result<List<FileResponse>> getMyFiles(
            @RequestAttribute("userId") Long userId) {
        List<FileResponse> files = fileService.getUserFiles(userId);
        return Result.success(files);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public Result<Void> deleteFile(
            @PathVariable Long fileId,
            @RequestAttribute("userId") Long userId) {
        fileService.deleteFile(fileId, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 获取班级公开文件列表
     */
    @GetMapping("/class/public")
    public Result<List<FileResponse>> getClassPublicFiles(
            @RequestAttribute("userId") Long userId) {
        List<FileResponse> files = fileService.getClassPublicFiles(userId);
        return Result.success(files);
    }
}
