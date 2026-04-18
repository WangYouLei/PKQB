package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pkqb.common.Result;
import pkqb.pojo.dto.FileResponse;
import pkqb.pojo.entity.FileEntity;
import pkqb.service.FileService;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传、下载、删除等接口")
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件到MinIO存储")
    public Result<FileResponse> upload(
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "文件名") @RequestParam("file_name") String fileName,
            @Parameter(description = "是否私有") @RequestParam("is_private") boolean isPrivate
    ) {
        FileResponse response = fileService.uploadFile(file, userId,fileName,isPrivate);
        return Result.success("上传成功", response);
    }

    @GetMapping("/public-url/{fileId}")
    @Operation(summary = "获取文件公开URL", description = "获取文件的公开访问URL")
    public Result<String> getPublicUrl(
            @Parameter(description = "文件ID") @PathVariable Long fileId,
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        String publicUrl = fileService.getPublicUrl(fileId, userId);
        return Result.success(publicUrl);
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的文件列表", description = "获取当前用户上传的文件列表")
    public Result<List<FileResponse>> getMyFiles(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        List<FileResponse> files = fileService.getUserFiles(userId);
        return Result.success(files);
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件", description = "删除指定文件")
    public Result<Void> deleteFile(
            @Parameter(description = "文件ID") @PathVariable Long fileId,
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        fileService.deleteFile(fileId, userId);
        return Result.success("删除成功", null);
    }

    @GetMapping("/class/public")
    @Operation(summary = "获取班级公开文件", description = "获取班级公开的文件列表")
    public Result<List<FileResponse>> getClassPublicFiles(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        List<FileResponse> files = fileService.getClassPublicFiles(userId);
        return Result.success(files);
    }

    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文件", description = "通过后端代理下载文件")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "文件ID") @PathVariable Long fileId,
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        FileEntity entity = fileService.getFileEntity(fileId, userId);
        byte[] data = fileService.downloadFile(fileId, userId);
        
        ByteArrayResource resource = new ByteArrayResource(data);
        
        HttpHeaders headers = new HttpHeaders();
        String fileName = entity.getFileName();
        String encodedFileName;
        try {
            encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8");
        } catch (Exception e) {
            encodedFileName = fileName;
        }
        headers.add("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
        
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
