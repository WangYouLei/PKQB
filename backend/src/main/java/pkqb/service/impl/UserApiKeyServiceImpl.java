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
import pkqb.config.ModelProvider;
import pkqb.config.ModelProviderFactory;
import pkqb.enums.ApiKeyMode;
import pkqb.enums.ModelChannel;
import pkqb.enums.ModelType;
import pkqb.mapper.ModelsMapper;
import pkqb.mapper.UserApiKeyMapper;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.pojo.entity.UserApiKeyEntity;
import pkqb.pojo.entity.UserEntity;
import pkqb.pojo.dto.ModelValidationResult;
import pkqb.service.UserApiKeyService;
import pkqb.util.ApiKeyEncryptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final UserApiKeyMapper userApiKeyMapper;
    private final ApiKeyEncryptor apiKeyEncryptor;
    private final DashScopeModelFactory dashScopeModelFactory;
    private final ModelProviderFactory modelProviderFactory;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "user_api_config:";
    /**
     * 分渠道 API Key 密文映射缓存：channel -> 加密后的 Key
     */
    private static final String API_KEYS_SUFFIX = ":api_keys";
    private static final String HAS_API_KEY_SUFFIX = ":has_api_key";
    private static final String PLAIN_API_KEY_SUFFIX = ":plain_api_key";
    private static final String MAIN_MODEL_SUFFIX = ":main_model";
    private static final String ASSISTANT_MODELS_SUFFIX = ":assistant_models";
    private static final String VISION_MODEL_SUFFIX = ":vision_model";
    private static final String ALL_MODELS_SUFFIX = ":all_models";
    private static final String MODEL_CHANNEL_SUFFIX = ":model_channel";
    
    private static final long CACHE_EXPIRE_HOURS = 24;

    public UserApiKeyServiceImpl(UserMapper userMapper,
                                  ModelsMapper modelsMapper,
                                  UserApiKeyMapper userApiKeyMapper,
                                  ApiKeyEncryptor apiKeyEncryptor,
                                  DashScopeModelFactory dashScopeModelFactory,
                                  ModelProviderFactory modelProviderFactory,
                                  StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.modelsMapper = modelsMapper;
        this.userApiKeyMapper = userApiKeyMapper;
        this.apiKeyEncryptor = apiKeyEncryptor;
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.modelProviderFactory = modelProviderFactory;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveApiKey(Long userId, ModelChannel channel, String apiKey) {
        ModelChannel actualChannel = channel != null ? channel : ModelChannel.DASHSCOPE;
        String encryptedKey = apiKeyEncryptor.encrypt(apiKey);

        LambdaQueryWrapper<UserApiKeyEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserApiKeyEntity::getUserId, userId)
                .eq(UserApiKeyEntity::getChannel, actualChannel.getCode());
        UserApiKeyEntity existing = userApiKeyMapper.selectOne(queryWrapper);

        if (existing != null) {
            LambdaUpdateWrapper<UserApiKeyEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UserApiKeyEntity::getId, existing.getId())
                    .set(UserApiKeyEntity::getApiKey, encryptedKey);
            userApiKeyMapper.update(null, updateWrapper);
        } else {
            UserApiKeyEntity entity = new UserApiKeyEntity();
            entity.setUserId(userId);
            entity.setChannel(actualChannel.getCode());
            entity.setApiKey(encryptedKey);
            userApiKeyMapper.insert(entity);
        }

        clearUserCache(userId);
        log.info("[API Key管理] 用户 {} 保存 {} 渠道 API Key 成功", userId, actualChannel.getCode());
    }

    @Override
    public Map<String, Object> getApiKeyStatusView(Long userId) {
        Map<String, Object> status = new HashMap<>();

        Map<String, Boolean> apiKeyStatus = getApiKeyStatus(userId);
        boolean hasOwnKey = apiKeyStatus.values().stream().anyMatch(Boolean::booleanValue);
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

        // 所有已配置模型所属渠道都必须有 Key，多模型协作才能正常调用
        boolean allModelsConfigured = allModels.stream()
                .allMatch(m -> hasUserOwnApiKey(userId, m.getModelChannelEnum()));

        status.put("apiKeyStatus", apiKeyStatus);
        status.put("configuredChannels", apiKeyStatus.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()));
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
        // 与supportsMultiModel等价：有主模型和至少1个辅助模型，且相关渠道都有 Key
        status.put("supportsMultiModel",
                hasOwnKey && mainModel != null && !assistantModels.isEmpty() && allModelsConfigured);

        log.info("[API Key管理] 用户 {} 状态视图聚合完成, modelCount={}", userId, allModels.size());
        return status;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long userId, ModelChannel channel) {
        ModelChannel actualChannel = channel != null ? channel : ModelChannel.DASHSCOPE;

        LambdaQueryWrapper<UserApiKeyEntity> keyDeleteWrapper = new LambdaQueryWrapper<>();
        keyDeleteWrapper.eq(UserApiKeyEntity::getUserId, userId)
                .eq(UserApiKeyEntity::getChannel, actualChannel.getCode());
        userApiKeyMapper.delete(keyDeleteWrapper);

        // 该渠道的模型依赖对应 Key，级联删除，避免留下无法调用的模型
        LambdaQueryWrapper<ModelsEntity> modelsDeleteWrapper = new LambdaQueryWrapper<>();
        modelsDeleteWrapper.eq(ModelsEntity::getUserId, userId)
                .eq(ModelsEntity::getModelChannel, actualChannel.getCode());
        modelsMapper.delete(modelsDeleteWrapper);

        clearUserCache(userId);
        log.info("[API Key管理] 用户 {} 删除 {} 渠道 API Key 及关联模型成功", userId, actualChannel.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long userId) {
        LambdaUpdateWrapper<UserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserEntity::getId, userId)
                .set(UserEntity::getApiKey, null);

        userMapper.update(null, updateWrapper);

        // 删除分渠道 Key 与所有模型
        userApiKeyMapper.delete(new LambdaQueryWrapper<UserApiKeyEntity>()
                .eq(UserApiKeyEntity::getUserId, userId));
        modelsMapper.delete(new LambdaQueryWrapper<ModelsEntity>()
                .eq(ModelsEntity::getUserId, userId));

        clearUserCache(userId);
        log.info("[API Key管理] 用户 {} 删除所有 API Key 及关联模型成功", userId);
    }

    @Override
    public String getPlainApiKey(Long userId, ModelChannel channel) {
        ModelChannel actualChannel = channel != null ? channel : ModelChannel.DASHSCOPE;
        String cachedCipher = getEncryptedApiKeyMap(userId).get(actualChannel.getCode());
        if (cachedCipher == null || cachedCipher.isEmpty()) {
            return null;
        }
        try {
            return apiKeyEncryptor.decrypt(cachedCipher);
        } catch (Exception e) {
            log.warn("[API Key管理] 用户 {} 的 {} 渠道缓存解密失败，清除缓存并回源数据库",
                    userId, actualChannel.getCode());
            clearUserCache(userId);
            return null;
        }
    }

    @Override
    public ApiKeyMode getApiKeyMode(Long userId) {
        if (!hasUserOwnApiKey(userId)) {
            return ApiKeyMode.LOCAL;
        }
        return ApiKeyMode.PERSONAL;
    }

    @Override
    public boolean hasUserOwnApiKey(Long userId, ModelChannel channel) {
        ModelChannel actualChannel = channel != null ? channel : ModelChannel.DASHSCOPE;
        String cipher = getEncryptedApiKeyMap(userId).get(actualChannel.getCode());
        return cipher != null && !cipher.isEmpty();
    }

    @Override
    public Map<String, Boolean> getApiKeyStatus(Long userId) {
        Map<String, String> encryptedKeys = getEncryptedApiKeyMap(userId);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (ModelChannel channel : ModelChannel.values()) {
            String cipher = encryptedKeys.get(channel.getCode());
            result.put(channel.getCode(), cipher != null && !cipher.isEmpty());
        }
        return result;
    }

    /**
     * 获取用户分渠道 API Key 的密文映射。
     * Redis 只缓存密文，避免明文 Key 泄露到 Redis 内存/RDB/AOF。
     */
    private Map<String, String> getEncryptedApiKeyMap(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + API_KEYS_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<Map<String, String>>() {});
            } catch (JsonProcessingException e) {
                log.warn("[API Key管理] 解析分渠道 Key 缓存失败: {}", e.getMessage());
            }
        }

        List<UserApiKeyEntity> rows = userApiKeyMapper.selectList(
                new LambdaQueryWrapper<UserApiKeyEntity>()
                        .eq(UserApiKeyEntity::getUserId, userId));
        Map<String, String> result = new HashMap<>();
        for (UserApiKeyEntity row : rows) {
            if (row.getChannel() != null && row.getApiKey() != null && !row.getApiKey().isEmpty()) {
                result.put(row.getChannel(), row.getApiKey());
            }
        }

        try {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(result),
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("[API Key管理] 分渠道 Key 缓存序列化失败: {}", e.getMessage());
        }

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
    public void updateUserModel(Long modelId, Long userId, String newModelName, ModelChannel modelChannel) {
        log.info("[模型管理] 修改模型, modelId={}, userId={}, newModelName={}, modelChannel={}",
                modelId, userId, newModelName, modelChannel);

        // 先校验模型存在且属于当前用户，不存在则不进行修改
        LambdaQueryWrapper<ModelsEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId);
        ModelsEntity model = modelsMapper.selectOne(queryWrapper);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }

        // 未指定渠道时沿用原渠道，避免修改名称时意外切换到默认渠道
        ModelChannel targetChannel = modelChannel != null ? modelChannel : model.getModelChannelEnum();

        // 校验新模型名称在目标渠道处有效（与添加模型一致），并同步更新调用配置
        String apiKey = getPlainApiKey(userId, targetChannel);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("请先配置 " + targetChannel.getDescription() + " 渠道的 API Key");
        }
        ModelValidationResult result = validateModel(apiKey, newModelName, model.getModelType(), targetChannel);
        if (!result.isSuccess()) {
            log.warn("[模型管理] 修改模型验证失败: userId={}, modelId={}, newModelName={}, channel={}, error={}",
                    userId, modelId, newModelName, targetChannel, result.error());
            throw new IllegalArgumentException(result.error());
        }

        // 验证通过后才执行 update（含渠道与探测到的调用配置）
        LambdaUpdateWrapper<ModelsEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ModelsEntity::getId, modelId)
                .eq(ModelsEntity::getUserId, userId)
                .set(ModelsEntity::getModelName, newModelName)
                .set(ModelsEntity::getModelChannel, targetChannel.getCode())
                .set(ModelsEntity::getMultiModel, result.multiModel())
                .set(ModelsEntity::getEnableThinking, result.enableThinking());
        modelsMapper.update(null, updateWrapper);

        clearUserCache(userId);
        log.info("[模型管理] 模型修改完成, modelId={}, channel={}", modelId, targetChannel);
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
        if (assistantModels.isEmpty()) {
            return false;
        }
        if (!hasUserOwnApiKey(userId, mainModel.getModelChannelEnum())) {
            return false;
        }
        return assistantModels.stream()
                .allMatch(m -> hasUserOwnApiKey(userId, m.getModelChannelEnum()));
    }

    @Override
    public ModelChannel getUserModelChannel(Long userId) {
        String cacheKey = CACHE_PREFIX + userId + MODEL_CHANNEL_SUFFIX;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return ModelChannel.fromCode(cached);
        }

        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getId, userId)
                .select(UserEntity::getModelChannel);
        UserEntity user = userMapper.selectOne(queryWrapper);

        ModelChannel channel = user != null ? ModelChannel.fromCode(user.getModelChannel()) : ModelChannel.DASHSCOPE;

        stringRedisTemplate.opsForValue().set(cacheKey, channel.getCode(),
                CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        return channel;
    }

    @Override
    public void setUserModelChannel(Long userId, ModelChannel channel) {
        LambdaUpdateWrapper<UserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserEntity::getId, userId)
                .set(UserEntity::getModelChannel, channel.getCode());
        userMapper.update(null, updateWrapper);

        clearUserCache(userId);
        log.info("[API Key管理] 用户 {} 设置模型渠道为 {}", userId, channel.getCode());
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
    
    private void clearUserCache(Long userId) {
        String prefix = CACHE_PREFIX + userId;
        stringRedisTemplate.delete(List.of(
                prefix + API_KEYS_SUFFIX,
                prefix + HAS_API_KEY_SUFFIX,
                prefix + PLAIN_API_KEY_SUFFIX,
                prefix + MAIN_MODEL_SUFFIX,
                prefix + ASSISTANT_MODELS_SUFFIX,
                prefix + VISION_MODEL_SUFFIX,
                prefix + ALL_MODELS_SUFFIX,
                prefix + MODEL_CHANNEL_SUFFIX
        ));
        log.info("[缓存管理] 清除用户 {} 的Redis缓存", userId);
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

    /**
     * 发起模型验证调用（指定渠道）
     */
    private ChatResponse doModelValidationCall(String apiKey, String modelName, Boolean enableThinking, boolean multiModel, ModelChannel channel) {
        ModelProvider provider = modelProviderFactory.getProvider(channel);
        ChatModel chatModel = provider.createChatModel(
                apiKey.trim(), modelName.trim(), 2048, enableThinking, multiModel);
        Prompt prompt = new Prompt("你好");
        return chatModel.stream(prompt).blockLast(Duration.ofSeconds(60));
    }

    /**
     * 验证模型（指定渠道）
     */
    @Override
    public ModelValidationResult validateModel(String apiKey, String modelName, int modelType, ModelChannel channel) {
        log.info("[模型验证] 开始验证模型: {}, modelType: {}, channel: {}", modelName, modelType, channel);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[模型验证] API Key 为空");
            return ModelValidationResult.fail("API Key 不能为空");
        }

        if (modelName == null || modelName.trim().isEmpty()) {
            log.warn("[模型验证] 模型名称为空");
            return ModelValidationResult.fail("模型名称不能为空");
        }

        if (modelType == ModelType.VISION.getCode()) {
            log.info("[模型验证] 视觉模型 {} 跳过调用验证，直接保存", modelName);
            return ModelValidationResult.success(false, false);
        }

        // OpenAI 等渠道暂时不支持自动探测，简单验证即可
        if (channel != ModelChannel.DASHSCOPE) {
            try {
                ChatResponse response = doModelValidationCall(apiKey, modelName, null, false, channel);
                if (response != null && response.getResult() != null) {
                    log.info("[模型验证] 模型 {} 验证成功 (channel: {})", modelName, channel);
                    return ModelValidationResult.success(false, false);
                }
                return ModelValidationResult.fail("请检查模型名称或API Key是否有误");
            } catch (Exception e) {
                log.error("[模型验证] 模型 {} 验证失败: {}", modelName, e.getMessage());
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("InvalidApiKey") || e.getMessage().contains("api key")) {
                        return ModelValidationResult.fail("API Key 无效，请检查");
                    }
                    if (e.getMessage().contains("ModelNotFound") || e.getMessage().contains("model not found")) {
                        return ModelValidationResult.fail("模型不存在，请检查模型名称");
                    }
                }
                return ModelValidationResult.fail("请检查模型名称或API Key是否有误: " + e.getMessage());
            }
        }

        // DashScope 渠道使用原有探测逻辑
        Boolean enableThinking = null;
        boolean multiModel = false;
        String lastErrorMessage = null;

        while (true) {
            try {
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
                    break;
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
                    break;
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
}
