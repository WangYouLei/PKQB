package pkqb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import pkqb.common.Result;
import pkqb.config.ReactAgentFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.enums.ModelType;
import pkqb.pojo.dto.SaveApiKeyRequest;
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
            HttpServletRequest httpRequest,
            @Valid @RequestBody SaveApiKeyRequest request) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        String apiKey = request.getApiKey().trim();
        try {
            userApiKeyService.saveApiKey(userId, apiKey);
            reactAgentFactory.clearUserCache(userId);
            return Result.success("API Key 保存成功");
        } catch (Exception e) {
            log.error("[API Key管理] 保存 API Key 失败: {}", e.getMessage());
            return Result.error("保存 API Key 失败: " + e.getMessage());
        }
    }

    @DeleteMapping
    @Operation(summary = "删除 API Key", description = "删除用户的 API Key，切换回使用本地 API Key")
    public Result<String> deleteApiKey(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
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
    public Result<Map<String, Object>> getApiKeyStatus(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        Map<String, Object> status = new HashMap<>();
        
        boolean hasOwnKey = userApiKeyService.hasUserOwnApiKey(userId);
        ApiKeyMode mode = userApiKeyService.getApiKeyMode(userId);
        boolean shouldLimit = rateLimitService.shouldRateLimit(userId);
        ModelsEntity mainModel = userApiKeyService.getMainModel(userId);
        List<ModelsEntity> assistantModels = userApiKeyService.getAssistantModels(userId);
        ModelsEntity visionModel = userApiKeyService.getVisionModel(userId);
        List<ModelsEntity> allModels = userApiKeyService.getUserModels(userId);
        
        status.put("hasOwnApiKey", hasOwnKey);
        status.put("currentMode", mode.name());
        status.put("hasRateLimit", shouldLimit);
        status.put("mainModel", mainModel != null ? mainModel.getModelName() : null);
        status.put("assistantModels", assistantModels);
        status.put("visionModel", visionModel);
        status.put("allModels", allModels);
        status.put("modelCount", allModels.size());
        status.put("maxModelCount", UserApiKeyService.MAX_MODEL_COUNT);
        status.put("canAddModel", userApiKeyService.canAddModel(userId));
        status.put("supportsMultiModel", userApiKeyService.supportsMultiModel(userId));
        
        return Result.success(status);
    }

    @PostMapping("/model")
    @Operation(summary = "添加模型", description = "为用户添加新的AI模型（最多4个：1个主模型 + 2个辅助模型 + 1个视觉模型）")
    public Result<String> addModel(
            HttpServletRequest httpRequest,
            @Parameter(description = "模型名称") @RequestParam String modelName,
            @Parameter(description = "模型类型：0=主模型，1=辅助模型，2=视觉模型") @RequestParam(defaultValue = "1") int modelType) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            return Result.error(400, "模型名称不能为空");
        }

        // Bug 6: 校验模型名称长度
        if (modelName.trim().length() > 100) {
            return Result.error(400, "模型名称长度不能超过100个字符");
        }

        // Bug 4: XSS防护 - 转义HTML特殊字符
        String safeModelName = HtmlUtils.htmlEscape(modelName.trim());

        if (!userApiKeyService.hasUserOwnApiKey(userId)) {
            return Result.error(400, "请先保存 API Key 后再设置模型");
        }

        // 校验 modelType 是否合法
        ModelType type;
        try {
            type = ModelType.fromCode(modelType);
        } catch (IllegalArgumentException e) {
            return Result.error(400, "无效的模型类型: " + modelType);
        }

        // 按类型检查数量限制
        if (!userApiKeyService.canAddModel(userId, type)) {
            return Result.error(400, type.getDescription() + "已达到最大数量限制（" + type.getMaxCount() + "个）");
        }
        
        String apiKey = userApiKeyService.getPlainApiKey(userId);
        String validationError = userApiKeyService.validateModel(apiKey, safeModelName, type.getCode());
        if (validationError != null) {
            log.warn("[模型管理] 模型验证失败: userId={}, modelName={}, error={}", userId, safeModelName, validationError);
            return Result.error(400, validationError);
        }
        
        try {
            ModelsEntity model = new ModelsEntity();
            model.setUserId(userId);
            model.setModelName(safeModelName);
            model.setModelType(type.getCode());
            
            // 如果用户没有任何模型，强制设为主模型
            List<ModelsEntity> existingModels = userApiKeyService.getUserModels(userId);
            if (existingModels == null || existingModels.isEmpty()) {
                model.setModelType(ModelType.MAIN.getCode());
            }
            
            userApiKeyService.saveUserModel(model);
            reactAgentFactory.clearUserCache(userId);
            
            log.info("[模型管理] 用户 {} 添加{}成功: {}", userId, type.getDescription(), safeModelName);
            return Result.success(type.getDescription() + "添加成功");
        } catch (Exception e) {
            log.error("[模型管理] 添加模型失败: {}", e.getMessage());
            return Result.error("添加模型失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/model")
    @Operation(summary = "删除模型", description = "删除用户的指定模型")
    public Result<String> deleteModel(
            HttpServletRequest httpRequest,
            @Parameter(description = "模型ID") @RequestParam Long modelId) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        try {
            userApiKeyService.deleteUserModel(modelId, userId);
            reactAgentFactory.clearUserCache(userId);
            return Result.success("模型删除成功");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[模型管理] 删除模型失败: {}", e.getMessage());
            return Result.error("删除模型失败: " + e.getMessage());
        }
    }

    @PutMapping("/model/main")
    @Operation(summary = "设置主模型", description = "将指定模型设置为主模型（用于单模型操作）")
    public Result<String> setMainModel(
            HttpServletRequest httpRequest,
            @Parameter(description = "模型ID") @RequestParam Long modelId) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        try {
            userApiKeyService.setMainModel(modelId, userId);
            reactAgentFactory.clearUserCache(userId);
            return Result.success("主模型设置成功");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[模型管理] 设置主模型失败: {}", e.getMessage());
            return Result.error("设置主模型失败: " + e.getMessage());
        }
    }

    @GetMapping("/models")
    @Operation(summary = "获取模型列表", description = "获取用户的所有模型列表")
    public Result<Map<String, Object>> getUserModels(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        Map<String, Object> result = new HashMap<>();
        
        ModelsEntity mainModel = userApiKeyService.getMainModel(userId);
        List<ModelsEntity> assistantModels = userApiKeyService.getAssistantModels(userId);
        ModelsEntity visionModel = userApiKeyService.getVisionModel(userId);
        List<ModelsEntity> allModels = userApiKeyService.getUserModels(userId);

        result.put("mainModel", mainModel);
        result.put("assistantModels", assistantModels);
        result.put("visionModel", visionModel);
        result.put("allModels", allModels);
        result.put("modelCount", allModels.size());
        result.put("maxModelCount", UserApiKeyService.MAX_MODEL_COUNT);
        result.put("canAddModel", userApiKeyService.canAddModel(userId));
        result.put("supportsMultiModel", userApiKeyService.supportsMultiModel(userId));
        
        return Result.success(result);
    }
}
