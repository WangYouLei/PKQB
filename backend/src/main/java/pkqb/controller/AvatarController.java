package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.AvatarUpdateRequest;
import pkqb.service.OssService;
import pkqb.service.UserService;

@Slf4j
@RestController
@RequestMapping("/api/user/avatar")
@RequiredArgsConstructor
@Tag(name = "用户头像管理", description = "用户头像上传、获取接口")
public class AvatarController {

    private final UserService userService;
    private final OssService ossService;

    @GetMapping("/upload-path")
    @Operation(summary = "获取头像上传路径", description = "获取头像上传的objectKey和限时预签名上传URL")
    public Result<AvatarUploadInfo> getUploadPath(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "文件Content-Type，必传且须与上传请求头一致（OSS签名校验要求，浏览器对File会自动带该头）")
            @RequestParam String contentType) {
        String objectKey = "avatar/" + userId + "/" + System.currentTimeMillis();
        // 返回预签名PUT地址：仅对该objectKey生效且10分钟内有效，
        // 不把OSS桶的写权限直接暴露给前端
        String uploadUrl = ossService.generatePresignedPutUrl(objectKey, contentType, 600);
        log.info("[头像管理] 用户 {} 获取头像上传地址 objectKey={}", userId, objectKey);
        return Result.success(new AvatarUploadInfo(objectKey, uploadUrl));
    }

    @PutMapping
    @Operation(summary = "更新头像", description = "更新用户头像（上传后调用，传入OSS对象objectKey）")
    public Result<String> updateAvatar(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AvatarUpdateRequest request) {
        String avatarUrl = userService.updateAvatar(userId, request.getObjectKey());
        return Result.success("头像更新成功", avatarUrl);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AvatarUploadInfo {
        private String objectKey;
        private String uploadUrl;
    }
}
