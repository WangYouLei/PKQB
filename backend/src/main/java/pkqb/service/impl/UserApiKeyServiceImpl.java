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
import pkqb.service.UserApiKeyService;
import pkqb.util.ApiKeyEncryptor;

import java.util.Collections;
import java.util.List;
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
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return cached;
        }

        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getId, userId)
                .select(UserEntity::getApiKey);

        UserEntity user = userMapper.selectOne(queryWrapper);
        String result = null;
        if (user != null && user.getApiKey() != null && !user.getApiKey().isEmpty()) {
            result = apiKeyEncryptor.decrypt(user.getApiKey());
        }

        if (result != null) {
            stringRedisTemplate.opsForValue().set(cacheKey, result,
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        return result;
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
    public String validateModel(String apiKey, String modelName, int modelType) {
        log.info("[模型验证] 开始验证模型: {}, modelType: {}", modelName, modelType);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[模型验证] API Key 为空");
            return "API Key 不能为空";
        }
        
        if (modelName == null || modelName.trim().isEmpty()) {
            log.warn("[模型验证] 模型名称为空");
            return "模型名称不能为空";
        }
        
        try {
            if (modelType == ModelType.VISION.getCode()) {
                // 视觉模型跳过调用验证，直接保存
                // 原因：不同系列视觉模型（qwen-vl、qwen-omni等）走不同API端点，
                // 统一验证容易误判，使用时如果报错用户可自行修改模型名称
                log.info("[模型验证] 视觉模型 {} 跳过调用验证，直接保存", modelName);
                return null;
            } else {
                // 主模型/辅助模型：纯文本验证
                ChatModel chatModel = dashScopeModelFactory.createChatModel(apiKey.trim(), modelName.trim(), 100);
                Prompt prompt = new Prompt("你好");
                ChatResponse response = chatModel.call(prompt);
                
                if (response != null && response.getResult() != null) {
                    log.info("[模型验证] 模型 {} 验证成功", modelName);
                    return null;
                } else {
                    log.warn("[模型验证] 模型 {} 验证失败：响应为空", modelName);
                    return "请检查模型名称或API Key是否有误";
                }
            }
        } catch (Exception e) {
            log.error("[模型验证] 模型 {} 验证失败: {}", modelName, e.getMessage());
            
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("InvalidParameter") || errorMessage.contains("url error")) {
                    return "请检查模型名称或API Key是否有误";
                }
                if (errorMessage.contains("InvalidApiKey") || errorMessage.contains("api key")) {
                    return "API Key 无效，请检查";
                }
                if (errorMessage.contains("ModelNotFound") || errorMessage.contains("model not found")) {
                    return "模型不存在，请检查模型名称";
                }
            }
            
            return "请检查模型名称或API Key是否有误";
        }
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
