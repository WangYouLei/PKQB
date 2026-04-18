package pkqb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pkqb.enums.ApiKeyMode;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.UserEntity;
import pkqb.service.UserApiKeyService;
import pkqb.util.ApiKeyEncryptor;

@Service
@Slf4j
public class UserApiKeyServiceImpl implements UserApiKeyService {

    private final UserMapper userMapper;
    private final ApiKeyEncryptor apiKeyEncryptor;

    public UserApiKeyServiceImpl(UserMapper userMapper, ApiKeyEncryptor apiKeyEncryptor) {
        this.userMapper = userMapper;
        this.apiKeyEncryptor = apiKeyEncryptor;
    }

    @Override
    public void saveApiKey(Long userId, String apiKey) {
        String encryptedKey = apiKeyEncryptor.encrypt(apiKey);
        
        LambdaUpdateWrapper<UserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserEntity::getId, userId)
                .set(UserEntity::getApiKey, encryptedKey);
        
        int updated = userMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("用户 {} 保存 API Key 成功: {}****{}", 
                userId, 
                apiKey.substring(0, Math.min(4, apiKey.length())), 
                apiKey.substring(Math.max(0, apiKey.length() - 4)));
        } else {
            log.warn("用户 {} 保存 API Key 失败，用户不存在", userId);
        }
    }

    @Override
    public void deleteApiKey(Long userId) {
        LambdaUpdateWrapper<UserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserEntity::getId, userId)
                .set(UserEntity::getApiKey, null);
        
        userMapper.update(null, updateWrapper);
        log.info("用户 {} 删除 API Key 成功", userId);
    }

    @Override
    public String getPlainApiKey(Long userId) {
        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getId, userId)
                .select(UserEntity::getApiKey);
        
        UserEntity user = userMapper.selectOne(queryWrapper);
        if (user == null || user.getApiKey() == null || user.getApiKey().isEmpty()) {
            return null;
        }
        
        return apiKeyEncryptor.decrypt(user.getApiKey());
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
        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getId, userId)
                .select(UserEntity::getApiKey)
                .isNotNull(UserEntity::getApiKey);
        
        UserEntity user = userMapper.selectOne(queryWrapper);
        return user != null && user.getApiKey() != null && !user.getApiKey().isEmpty();
    }
}
