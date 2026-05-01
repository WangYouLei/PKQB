package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.config.ReactAgentFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.service.RateLimitService;
import pkqb.service.UserApiKeyService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/apikey")
@Tag(name = "API Key 管理", description = "用户 API Key 配置接口")
public class UserApiKeyController {

    private final UserApiKeyService userApiKeyService;
    private final RateLimitService rateLimitService;
    private final ReactAgentFactory reactAgentFactory;

    public UserApiKeyController(UserApiKeyService userApiKeyService, 
                                RateLimitService rateLimitService,
                                ReactAgentFactory reactAgentFactory) {
        this.userApiKeyService = userApiKeyService;
        this.rateLimitService = rateLimitService;
        this.reactAgentFactory = reactAgentFactory;
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
            reactAgentFactory.clearUserCache(userId);
            return Result.success("API Key 保存成功");
        } catch (Exception e) {
            log.error("[API Key管理] 保存 API Key 失败: {}", e.getMessage());
            return Result.error("保存 API Key 失败: " + e.getMessage());
        }
    }

    @DeleteMapping
    @Operation(summary = "删除 API Key", description = "删除用户的 API Key，切换回使用本地 API Key")
    public Result<String> deleteApiKey(@Parameter(description = "用户ID") @RequestParam Long userId) {
        try {
            userApiKeyService.deleteApiKey(userId);
            reactAgentFactory.clearUserCache(userId);
            return Result.success("API Key 已删除，将使用本地 API Key");
        } catch (Exception e) {
            log.error("[API Key管理] 删除 API Key 失败: {}", e.getMessage());
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
        ModelsEntity mainModel = userApiKeyService.getMainModel(userId);
        List<ModelsEntity> assistantModels = userApiKeyService.getAssistantModels(userId);
        List<ModelsEntity> allModels = userApiKeyService.getUserModels(userId);
        
        status.put("hasOwnApiKey", hasOwnKey);
        status.put("currentMode", mode.name());
        status.put("hasRateLimit", shouldLimit);
        status.put("mainModel", mainModel != null ? mainModel.getModelName() : null);
        status.put("assistantModels", assistantModels);
        status.put("allModels", allModels);
        status.put("modelCount", allModels.size());
        status.put("maxModelCount", UserApiKeyService.MAX_MODEL_COUNT);
        status.put("canAddModel", userApiKeyService.canAddModel(userId));
        status.put("supportsMultiModel", userApiKeyService.supportsMultiModel(userId));
        
        return Result.success(status);
    }

    @PostMapping("/model")
    @Operation(summary = "添加模型", description = "为用户添加新的AI模型（最多3个：1个主模型 + 2个辅助模型）")
    public Result<String> addModel(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "模型名称") @RequestParam String modelName,
            @Parameter(description = "是否设为主模型") @RequestParam(defaultValue = "false") boolean isMain) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return Result.error("模型名称不能为空");
        }
        
        if (!userApiKeyService.hasUserOwnApiKey(userId)) {
            return Result.error("请先保存 API Key 后再设置模型");
        }
        
        if (!userApiKeyService.canAddModel(userId)) {
            return Result.error("已达到最大模型数量限制（" + UserApiKeyService.MAX_MODEL_COUNT + "个）");
        }
        
        String apiKey = userApiKeyService.getPlainApiKey(userId);
        String validationError = userApiKeyService.validateModel(apiKey, modelName.trim());
        if (validationError != null) {
            log.warn("[模型管理] 模型验证失败: userId={}, modelName={}, error={}", userId, modelName, validationError);
            return Result.error(validationError);
        }
        
        try {
            ModelsEntity model = new ModelsEntity();
            model.setUserId(userId);
            model.setModelName(modelName.trim());
            model.setIsMain(isMain ? 1 : 0);
            
            List<ModelsEntity> existingModels = userApiKeyService.getUserModels(userId);
            if (existingModels == null || existingModels.isEmpty()) {
                model.setIsMain(1);
            }
            
            userApiKeyService.saveUserModel(model);
            reactAgentFactory.clearUserCache(userId);
            
            String modelType = model.getIsMain() == 1 ? "主模型" : "辅助模型";
            log.info("[模型管理] 用户 {} 添加{}成功: {}", userId, modelType, modelName.trim());
            return Result.success(modelType + "添加成功");
        } catch (Exception e) {
            log.error("[模型管理] 添加模型失败: {}", e.getMessage());
            return Result.error("添加模型失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/model")
    @Operation(summary = "删除模型", description = "删除用户的指定模型")
    public Result<String> deleteModel(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "模型ID") @RequestParam Long modelId) {
        try {
            userApiKeyService.deleteUserModel(modelId, userId);
            reactAgentFactory.clearUserCache(userId);
            return Result.success("模型删除成功");
        } catch (Exception e) {
            log.error("[模型管理] 删除模型失败: {}", e.getMessage());
            return Result.error("删除模型失败: " + e.getMessage());
        }
    }

    @PutMapping("/model/main")
    @Operation(summary = "设置主模型", description = "将指定模型设置为主模型（用于单模型操作）")
    public Result<String> setMainModel(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "模型ID") @RequestParam Long modelId) {
        try {
            userApiKeyService.setMainModel(modelId, userId);
            reactAgentFactory.clearUserCache(userId);
            return Result.success("主模型设置成功");
        } catch (Exception e) {
            log.error("[模型管理] 设置主模型失败: {}", e.getMessage());
            return Result.error("设置主模型失败: " + e.getMessage());
        }
    }

    @GetMapping("/models")
    @Operation(summary = "获取模型列表", description = "获取用户的所有模型列表")
    public Result<Map<String, Object>> getUserModels(@Parameter(description = "用户ID") @RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        ModelsEntity mainModel = userApiKeyService.getMainModel(userId);
        List<ModelsEntity> assistantModels = userApiKeyService.getAssistantModels(userId);
        List<ModelsEntity> allModels = userApiKeyService.getUserModels(userId);
        
        result.put("mainModel", mainModel);
        result.put("assistantModels", assistantModels);
        result.put("allModels", allModels);
        result.put("modelCount", allModels.size());
        result.put("maxModelCount", UserApiKeyService.MAX_MODEL_COUNT);
        result.put("canAddModel", userApiKeyService.canAddModel(userId));
        result.put("supportsMultiModel", userApiKeyService.supportsMultiModel(userId));
        
        return Result.success(result);
    }
}
