package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pkqb.config.DashScopeModelFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.enums.ModelType;
import pkqb.mapper.ModelsMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.pojo.dto.ModelValidationResult;
import pkqb.service.UserApiKeyService;
import pkqb.util.ApiKeyEncryptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserApiKeyServiceImpl implements UserApiKeyService {

    private final UserMapper userMapper;
    private final ModelsMapper modelsMapper;
    private final ApiKeyEncryptor apiKeyEncryptor;
    private final DashScopeModelFactory dashScopeModelFactory;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "user_api_config:";
    private static final String HAS_API_KEY_SUFFIX = ":has_api_key";
    private static final String PLAIN_API_KEY_SUFFIX = ":plain_api_key";
    private static final String MAIN_MODEL_SUFFIX = ":main_model";
    private static final String ASSISTANT_MODELS_SUFFIX = ":assistant_models";
    private static final String VISION_MODEL_SUFFIX = ":vision_model";
    private static final String ALL_MODELS_SUFFIX = ":all_models";
    
    private static final long CACHE_EXPIRE_HOURS = 24;

    public UserApiKeyServiceImpl(UserMapper userMapper, 
                                  ModelsMapper modelsMapper, 
                                  ApiKeyEncryptor apiKeyEncryptor, 
                                  DashScopeModelFactory dashScopeModelFactory,
                                  StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.modelsMapper = modelsMapper;
        this.apiKeyEncryptor = apiKeyEncryptor;
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveApiKey(Long userId, String apiKey) {
        String encryptedKey = apiKeyEncryptor.encrypt(apiKey);
        
        LambdaUpdateWrapper<UserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserEntity::getId, userId)
                .set(UserEntity::getApiKey, encryptedKey);
        
        int updated = userMapper.update(null, updateWrapper);
        if (updated > 0) {
            clearUserCache(userId);
            log.info("[API Key管理] 用户 {} 保存 API Key 成功", userId);
        } else {
            log.warn("[API Key管理] 用户 {} 保存 API Key 失败，用户不存在", userId);
        }
    }

    @Override
    public Map<String, Object> getApiKeyStatusView(Long userId) {
        Map<String, Object> status = new HashMap<>();

        boolean hasOwnKey = hasUserOwnApiKey(userId);
        // shouldRateLimit的实现即为 !hasUserOwnApiKey，此处直接派生，省一次服务调用
        boolean shouldLimit = !hasOwnKey;

        // 一次getUserModels（带缓存）后按类型派生主/辅助/视觉模型，
        // 替代此前各自独立查库，串行服务调用由8+次降为常数次缓存查询
        List<ModelsEntity> allModels = getUserModels(userId);
        ModelsEntity mainModel = allModels.stream()
                .filter(m -> m.getModelType().equals(ModelType.MAIN.getCode()))
                .findFirst().orElse(null);
        // 与getAssistantModels保持一致：按创建时间取最新的前2条
        List<ModelsEntity> assistantModels = allModels.stream()
                .filter(m -> m.getModelType().equals(ModelType.ASSISTANT.getCode()))
                .limit(MAX_ASSISTANT_MODEL_COUNT)
                .collect(Collectors.toList());
        ModelsEntity visionModel = allModels.stream()
                .filter(m -> m.getModelType().equals(ModelType.VISION.getCode()))
                .findFirst().orElse(null);

        status.put("hasOwnApiKey", hasOwnKey);
        status.put("currentMode", getApiKeyMode(userId).name());
        status.put("hasRateLimit", shouldLimit);
        status.put("mainModel", mainModel != null ? mainModel.getModelName() : null);
        status.put("assistantModels", assistantModels);
        status.put("visionModel", visionModel);
        status.put("allModels", allModels);
        status.put("modelCount", allModels.size());
        status.put("maxModelCount", MAX_MODEL_COUNT);
        status.put("canAddModel", allModels.size() < MAX_MODEL_COUNT);
        // 与supportsMultiModel等价：有自己的Key + 有主模型 + 至少1个辅助模型，直接派生省一次调用
        status.put("supportsMultiModel", hasOwnKey && mainModel != null && !assistantModels.isEmpty());

        log.info("[API Key管理] 用户 {} 状态视图聚合完成, modelCount={}", userId, allModels.size());
        return status;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long userId) {
        LambdaUpdateWrapper<UserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserEntity::getId, userId)
                .set(UserEntity::getApiKey, null);

        userMapper.update(null, updateWrapper);

        // 级联删除该用户的所有模型
        LambdaQueryWrapper<ModelsEntity> modelsDeleteWrapper = new LambdaQueryWrapper<>();
        modelsDeleteWrapper.eq(ModelsEntity::getUserId, userId);
        modelsMapper.delete(modelsDeleteWrapper);

        clearUserCache(userId);
        log.info("[API Key管理] 用户 {} 删除 API Key 及关联模型成功", userId);
    }

    @Override
    public String getPlainApiKey(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + PLAIN_API_KEY_SUFFIX;
        // Redis 只缓存加密后的密文，避免明文 API Key 泄露到 Redis 内存/RDB/AOF；使用时再解密
        String cachedCipher = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedCipher != null) {
            try {
                return apiKeyEncryptor.decrypt(cachedCipher);
            } catch (Exception e) {
                // 兼容历史明文缓存：解密失败说明缓存不可信，清除后回源数据库重新加载
                log.warn("[API Key管理] 用户 {} 的缓存解密失败，清除缓存并回源数据库", userId);
                stringRedisTemplate.delete(cacheKey);
            }
        }

        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getId, userId)
                .select(UserEntity::getApiKey);

        UserEntity user = userMapper.selectOne(queryWrapper);
        if (user != null && user.getApiKey() != null && !user.getApiKey().isEmpty()) {
            // 缓存DB中已有的密文，需要明文时解密返回
            stringRedisTemplate.opsForValue().set(cacheKey, user.getApiKey(),
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("[API Key管理] 用户 {} 缓存API Key密文至Redis", userId);
            return apiKeyEncryptor.decrypt(user.getApiKey());
        }

        return null;
    }

    @Override
    public ApiKeyMode getApiKeyMode(Long userId) {
        if (!hasUserOwnApiKey(userId)) {
            return ApiKeyMode.LOCAL;
        }
        return ApiKeyMode.PERSONAL;
    }

    @Override
    public boolean hasUserOwnApiKey(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + HAS_API_KEY_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return "1".equals(cached);
        }
        
        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getId, userId)
                .select(UserEntity::getApiKey)
                .isNotNull(UserEntity::getApiKey);
        
        UserEntity user = userMapper.selectOne(queryWrapper);
        boolean result = user != null && user.getApiKey() != null && !user.getApiKey().isEmpty();
        
        stringRedisTemplate.opsForValue().set(cacheKey, result ? "1" : "0", 
                CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        
        return result;
    }

    @Override
    public List<ModelsEntity> getUserModels(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + ALL_MODELS_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<ModelsEntity>>() {});
            } catch (JsonProcessingException e) {
                log.warn("[模型管理] 解析缓存失败，从数据库查询: {}", e.getMessage());
            }
        }
        
        log.info("[模型管理] 获取用户 {} 的所有模型", userId);
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getUserId, userId)
                .orderByAsc(ModelsEntity::getModelType)
                .orderByDesc(ModelsEntity::getCreateTime);
        List<ModelsEntity> result = modelsMapper.selectList(queryWrapper);
        
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), 
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("[模型管理] 缓存序列化失败: {}", e.getMessage());
        }
        
        return result;
    }

    @Override
    public ModelsEntity getMainModel(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + MAIN_MODEL_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ModelsEntity.class);
            } catch (JsonProcessingException e) {
                log.warn("[模型管理] 解析主模型缓存失败: {}", e.getMessage());
            }
        }

        log.info("[模型管理] 获取用户 {} 的主模型", userId);
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getUserId, userId)
                .eq(ModelsEntity::getModelType, ModelType.MAIN.getCode());
        ModelsEntity result = modelsMapper.selectOne(queryWrapper);

        if (result != null) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(result),
                        CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            } catch (JsonProcessingException e) {
                log.warn("[模型管理] 缓存序列化失败: {}", e.getMessage());
            }
        }

        return result;
    }

    @Override
    public List<ModelsEntity> getAssistantModels(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + ASSISTANT_MODELS_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<ModelsEntity>>() {});
            } catch (JsonProcessingException e) {
                log.warn("[模型管理] 解析辅助模型缓存失败: {}", e.getMessage());
            }
        }
        
        log.info("[模型管理] 获取用户 {} 的辅助模型", userId);
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getUserId, userId)
                .eq(ModelsEntity::getModelType, ModelType.ASSISTANT.getCode())
                .orderByDesc(ModelsEntity::getCreateTime);
        List<ModelsEntity> allAssistantModels = modelsMapper.selectList(queryWrapper);
        
        List<ModelsEntity> result = allAssistantModels.stream()
                .limit(MAX_ASSISTANT_MODEL_COUNT)
                .collect(Collectors.toList());
        
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), 
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("[模型管理] 缓存序列化失败: {}", e.getMessage());
        }
        
        return result;
    }

    @Override
    public ModelsEntity getVisionModel(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + VISION_MODEL_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ModelsEntity.class);
            } catch (JsonProcessingException e) {
                log.warn("[模型管理] 解析视觉模型缓存失败: {}", e.getMessage());
            }
        }

        log.info("[模型管理] 获取用户 {} 的视觉模型", userId);
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getUserId, userId)
                .eq(ModelsEntity::getModelType, ModelType.VISION.getCode());
        ModelsEntity result = modelsMapper.selectOne(queryWrapper);

        if (result != null) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey,
                        objectMapper.writeValueAsString(result),
                        CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            } catch (JsonProcessingException e) {
                log.warn("[模型管理] 缓存序列化失败: {}", e.getMessage());
            }
        }

        return result;
    }

    @Override
    public void saveUserModel(ModelsEntity model) {
        log.info("[模型管理] 保存用户模型, userId={}, modelName={}, modelType={}", 
                model.getUserId(), model.getModelName(), model.getModelType());
        modelsMapper.insert(model);
        clearUserCache(model.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserModel(Long modelId, Long userId) {
        log.info("[模型管理] 删除用户模型, modelId={}, userId={}", modelId, userId);
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId);
        int deleted = modelsMapper.delete(queryWrapper);
        if (deleted == 0) {
            throw new IllegalArgumentException("模型不存在");
        }
        clearUserCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserModelName(Long modelId, Long userId, String newModelName) {
        log.info("[模型管理] 修改模型名称, modelId={}, userId={}, newModelName={}", modelId, userId, newModelName);

        // 先校验模型存在且属于当前用户，不存在则不进行修改
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId);
        ModelsEntity model = modelsMapper.selectOne(queryWrapper);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }

        // 校验新模型名称在 AI 服务商处有效（与添加模型一致），并同步更新调用配置
        String apiKey = getPlainApiKey(userId);
        ModelValidationResult result = validateModel(apiKey, newModelName, model.getModelType());
        if (!result.isSuccess()) {
            log.warn("[模型管理] 修改模型名称验证失败: userId={}, modelId={}, newModelName={}, error={}",
                    userId, modelId, newModelName, result.error());
            throw new IllegalArgumentException(result.error());
        }

        // 验证通过后才执行 update（含探测到的调用配置）
        LambdaUpdateWrapper<ModelsEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId)
                .set(ModelsEntity::getModelName, newModelName)
                .set(ModelsEntity::getMultiModel, result.multiModel())
                .set(ModelsEntity::getEnableThinking, result.enableThinking());
        modelsMapper.update(null, updateWrapper);

        clearUserCache(userId);
        log.info("[模型管理] 模型名称修改完成, modelId={}", modelId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setMainModel(Long modelId, Long userId) {
        log.info("[模型管理] 设置主模型, modelId={}, userId={}", modelId, userId);

        // 先校验目标模型存在且属于当前用户
        LambdaQueryWrapper<ModelsEntity> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId);
        ModelsEntity target = modelsMapper.selectOne(checkWrapper);
        if (target == null) {
            throw new IllegalArgumentException("模型不存在");
        }

        // 先将该用户所有模型设为辅助模型
        LambdaUpdateWrapper<ModelsEntity> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(ModelsEntity::getUserId, userId)
                .set(ModelsEntity::getModelType, ModelType.ASSISTANT.getCode());
        modelsMapper.update(null, clearWrapper);

        // 再将指定模型设为主模型
        LambdaUpdateWrapper<ModelsEntity> setWrapper = new LambdaUpdateWrapper<>();
        setWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId)
                .set(ModelsEntity::getModelType, ModelType.MAIN.getCode());
        modelsMapper.update(null, setWrapper);

        clearUserCache(userId);
        log.info("[模型管理] 主模型设置完成, modelId={}", modelId);
    }

    @Override
    public boolean canAddModel(Long userId) {
        List<ModelsEntity> models = getUserModels(userId);
        return models.size() < MAX_MODEL_COUNT;
    }

    @Override
    public boolean canAddModel(Long userId, ModelType modelType) {
        List<ModelsEntity> models = getUserModels(userId);
        long currentCount = models.stream()
                .filter(m -> m.getModelType().equals(modelType.getCode()))
                .count();
        return currentCount < modelType.getMaxCount();
    }

    @Override
    public boolean supportsMultiModel(Long userId) {
        if (!hasUserOwnApiKey(userId)) {
            return false;
        }
        
        ModelsEntity mainModel = getMainModel(userId);
        if (mainModel == null) {
            return false;
        }
        
        List<ModelsEntity> assistantModels = getAssistantModels(userId);
        return !assistantModels.isEmpty();
    }

    @Override
    public ModelValidationResult validateModel(String apiKey, String modelName, int modelType) {
        log.info("[模型验证] 开始验证模型: {}, modelType: {}", modelName, modelType);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[模型验证] API Key 为空");
            return ModelValidationResult.fail("API Key 不能为空");
        }

        if (modelName == null || modelName.trim().isEmpty()) {
            log.warn("[模型验证] 模型名称为空");
            return ModelValidationResult.fail("模型名称不能为空");
        }

        if (modelType == ModelType.VISION.getCode()) {
            // 视觉模型跳过调用验证，直接保存
            // 原因：视觉模型统一走 multimodal 端点（createVisionChatModel 已处理），
            // 不同系列参数差异大，统一验证容易误判
            log.info("[模型验证] 视觉模型 {} 跳过调用验证，直接保存", modelName);
            return ModelValidationResult.success(false, false);
        }

        // 主模型/辅助模型：流式探测调用
        // 百炼模型的调用方式差异（通过错误信息驱动自动切换重试）：
        // - 端点：text-generation（默认）/ multimodal-generation（报 url error 的新架构模型如 kimi-k3）
        // - 思考模式：强制思考模型（报 enable_thinking restricted 的如 glm 系列）必须显式开启
        Boolean enableThinking = null;
        boolean multiModel = false;
        String lastErrorMessage = null;

        while (true) {
            try {
                // maxTokens 需给足：思考型模型的思考过程会消耗大量 token
                ChatResponse response = doModelValidationCall(apiKey, modelName, enableThinking, multiModel);
                if (response != null && response.getResult() != null) {
                    log.info("[模型验证] 模型 {} 验证成功, multiModel={}, enableThinking={}",
                            modelName, multiModel, enableThinking);
                    return ModelValidationResult.success(multiModel, Boolean.TRUE.equals(enableThinking));
                }
                log.warn("[模型验证] 模型 {} 验证失败：响应为空", modelName);
                return ModelValidationResult.fail("请检查模型名称或API Key是否有误");
            } catch (Exception e) {
                lastErrorMessage = e.getMessage();
                log.warn("[模型验证] 模型 {} 验证尝试失败 (multiModel={}, enableThinking={}): {}",
                        modelName, multiModel, enableThinking, lastErrorMessage);

                boolean needThinking = lastErrorMessage != null && lastErrorMessage.contains("enable_thinking");
                boolean needMulti = lastErrorMessage != null && lastErrorMessage.contains("url error");
                if (!needThinking && !needMulti) {
                    break; // 其他错误（Key无效/模型不存在等）切换配置也无法解决
                }

                boolean changed = false;
                if (needThinking && enableThinking == null) {
                    enableThinking = Boolean.TRUE;
                    changed = true;
                }
                if (needMulti && !multiModel) {
                    multiModel = true;
                    changed = true;
                }
                if (!changed) {
                    break; // 配置已到位仍失败，不再重试
                }
            }
        }

        log.error("[模型验证] 模型 {} 验证失败: {}", modelName, lastErrorMessage);

        if (lastErrorMessage != null) {
            if (lastErrorMessage.contains("InvalidApiKey") || lastErrorMessage.contains("api key")) {
                return ModelValidationResult.fail("API Key 无效，请检查");
            }
            if (lastErrorMessage.contains("ModelNotFound") || lastErrorMessage.contains("model not found")) {
                return ModelValidationResult.fail("模型不存在，请检查模型名称");
            }
        }

        return ModelValidationResult.fail("请检查模型名称或API Key是否有误");
    }
    
    /**
     * 发起一次模型验证调用（流式）
     *
     * @param enableThinking 为 null 时不设置该参数；强制思考型模型需传 Boolean.TRUE
     * @param multiModel true 走 multimodal-generation 端点（新架构模型如 kimi-k3）
     */
    private ChatResponse doModelValidationCall(String apiKey, String modelName, Boolean enableThinking, boolean multiModel) {
        ChatModel chatModel = dashScopeModelFactory.createChatModel(
                apiKey.trim(), modelName.trim(), 2048, enableThinking, multiModel);
        Prompt prompt = new Prompt("你好");
        return chatModel.stream(prompt).blockLast(Duration.ofSeconds(60));
    }

    private void clearUserCache(Long userId) {
        String prefix = CACHE_PREFIX + userId;
        stringRedisTemplate.delete(List.of(
                prefix + HAS_API_KEY_SUFFIX,
                prefix + PLAIN_API_KEY_SUFFIX,
                prefix + MAIN_MODEL_SUFFIX,
                prefix + ASSISTANT_MODELS_SUFFIX,
                prefix + VISION_MODEL_SUFFIX,
                prefix + ALL_MODELS_SUFFIX
        ));
        log.info("[缓存管理] 清除用户 {} 的Redis缓存", userId);
    }
}
