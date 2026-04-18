package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.config.ChatClientFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.service.RateLimitService;
import pkqb.service.UserApiKeyService;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/apikey")
@Tag(name = "API Key 管理", description = "用户 API Key 配置接口")
public class UserApiKeyController {

    private final UserApiKeyService userApiKeyService;
    private final RateLimitService rateLimitService;
    private final ChatClientFactory chatClientFactory;

    public UserApiKeyController(UserApiKeyService userApiKeyService, 
                                RateLimitService rateLimitService,
                                ChatClientFactory chatClientFactory) {
        this.userApiKeyService = userApiKeyService;
        this.rateLimitService = rateLimitService;
        this.chatClientFactory = chatClientFactory;
    }

    @PostMapping
    @Operation(summary = "保存 API Key", description = "保存或更新用户的阿里云百炼 API Key")
    public Result<String> saveApiKey(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "API Key") @RequestParam String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Result.error("API Key 不能为空");
        }
        try {
            userApiKeyService.saveApiKey(userId, apiKey.trim());
            chatClientFactory.clearUserCache(userId);
            return Result.success("API Key 保存成功");
        } catch (Exception e) {
            log.error("保存 API Key 失败: {}", e.getMessage());
            return Result.error("保存 API Key 失败: " + e.getMessage());
        }
    }

    @DeleteMapping
    @Operation(summary = "删除 API Key", description = "删除用户的 API Key，切换回使用本地 API Key")
    public Result<String> deleteApiKey(@Parameter(description = "用户ID") @RequestParam Long userId) {
        try {
            userApiKeyService.deleteApiKey(userId);
            chatClientFactory.clearUserCache(userId);
            return Result.success("API Key 已删除，将使用本地 API Key");
        } catch (Exception e) {
            log.error("删除 API Key 失败: {}", e.getMessage());
            return Result.error("删除 API Key 失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    @Operation(summary = "获取 API Key 状态", description = "获取用户当前的 API Key 配置状态")
    public Result<Map<String, Object>> getApiKeyStatus(@Parameter(description = "用户ID") @RequestParam Long userId) {
        Map<String, Object> status = new HashMap<>();
        
        boolean hasOwnKey = userApiKeyService.hasUserOwnApiKey(userId);
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        boolean shouldLimit = rateLimitService.shouldRateLimit(userId);
        
        status.put("hasOwnApiKey", hasOwnKey);
        status.put("currentMode", mode.name());
        status.put("hasRateLimit", shouldLimit);
        
        return Result.success(status);
    }
}
