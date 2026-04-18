package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.AvatarUpdateRequest;
import pkqb.service.UserService;

/**
 * 用户头像控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user/avatar")
@RequiredArgsConstructor
@Tag(name = "用户头像管理", description = "用户头像上传、获取接口")
public class AvatarController {

    private final UserService userService;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 获取头像上传路径
     */
    @GetMapping("/upload-path")
    @Operation(summary = "获取头像上传路径", description = "获取头像上传的objectKey和公开URL")
    public Result<AvatarUploadInfo> getUploadPath(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId) {
        String objectKey = "avatar/" + userId + "/" + System.currentTimeMillis();
        String publicUrl = minioEndpoint + "/" + bucketName + "/" + objectKey;
        return Result.success(new AvatarUploadInfo(objectKey, publicUrl));
    }

    /**
     * 更新用户头像
     */
    @PutMapping
    @Operation(summary = "更新头像", description = "更新用户头像（上传后调用，传入MinIO对象路径）")
    public Result<String> updateAvatar(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @RequestBody AvatarUpdateRequest request) {
        String avatarUrl = userService.updateAvatar(userId, request.getObjectKey());
        return Result.success("头像更新成功", avatarUrl);
    }

    /**
     * 头像上传信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AvatarUploadInfo {
        private String objectKey;
        private String uploadUrl;
    }
}