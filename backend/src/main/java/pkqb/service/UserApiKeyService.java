package pkqb.service;

import pkqb.enums.ApiKeyMode;
import pkqb.enums.ModelChannel;
import pkqb.enums.ModelType;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.pojo.dto.ModelValidationResult;

import java.util.List;
import java.util.Map;

public interface UserApiKeyService {

    /**
     * 各类型模型最大数量限制
     */
    int MAX_MAIN_MODEL_COUNT = 1;
    int MAX_ASSISTANT_MODEL_COUNT = 2;
    int MAX_VISION_MODEL_COUNT = 1;
    int MAX_MODEL_COUNT = MAX_MAIN_MODEL_COUNT + MAX_ASSISTANT_MODEL_COUNT + MAX_VISION_MODEL_COUNT;

    /**
     * 保存指定渠道的 API Key（同一用户每个渠道各存一份）
     *
     * @param userId  用户ID
     * @param channel 模型渠道
     * @param apiKey  API Key
     */
    void saveApiKey(Long userId, ModelChannel channel, String apiKey);

    /**
     * 保存阿里云百炼渠道的 API Key（兼容旧调用）
     */
    default void saveApiKey(Long userId, String apiKey) {
        saveApiKey(userId, ModelChannel.DASHSCOPE, apiKey);
    }

    /**
     * 删除指定渠道的 API Key，同时删除该渠道下已配置的模型
     *
     * @param userId  用户ID
     * @param channel 模型渠道
     */
    void deleteApiKey(Long userId, ModelChannel channel);

    /**
     * 聚合获取API Key状态视图（主/辅助/视觉模型由一次模型查询派生，供状态接口使用）
     *
     * @param userId 用户ID
     * @return 状态字段集合
     */
    Map<String, Object> getApiKeyStatusView(Long userId);

    /**
     * 删除用户所有渠道的 API Key 及所有模型
     */
    void deleteApiKey(Long userId);

    /**
     * 获取指定渠道的明文 API Key
     *
     * @param userId  用户ID
     * @param channel 模型渠道
     * @return 明文 API Key，未配置时返回 null
     */
    String getPlainApiKey(Long userId, ModelChannel channel);

    /**
     * 获取阿里云百炼渠道的明文 API Key（兼容旧调用）
     */
    default String getPlainApiKey(Long userId) {
        return getPlainApiKey(userId, ModelChannel.DASHSCOPE);
    }

    ApiKeyMode getApiKeyMode(Long userId);

    /**
     * 检查用户是否配置了指定渠道的 API Key
     */
    boolean hasUserOwnApiKey(Long userId, ModelChannel channel);

    /**
     * 检查用户是否配置了任意渠道的 API Key
     */
    default boolean hasUserOwnApiKey(Long userId) {
        return getApiKeyStatus(userId).values().stream().anyMatch(Boolean::booleanValue);
    }

    /**
     * 获取用户各渠道 API Key 的配置状态
     *
     * @param userId 用户ID
     * @return channel -> 是否已配置
     */
    Map<String, Boolean> getApiKeyStatus(Long userId);

    /**
     * 获取用户的模型渠道
     * @param userId 用户ID
     * @return 模型渠道，默认返回 DASHSCOPE
     */
    ModelChannel getUserModelChannel(Long userId);

    /**
     * 设置用户的模型渠道
     * @param userId 用户ID
     * @param channel 模型渠道
     */
    void setUserModelChannel(Long userId, ModelChannel channel);

    /**
     * 获取用户的所有模型列表
     * @param userId 用户ID
     * @return 模型列表
     */
    List<ModelsEntity> getUserModels(Long userId);

    /**
     * 获取用户的主模型
     * @param userId 用户ID
     * @return 主模型，如果没有则返回null
     */
    ModelsEntity getMainModel(Long userId);

    /**
     * 获取用户的辅助模型列表
     * @param userId 用户ID
     * @return 辅助模型列表，最多2个
     */
    List<ModelsEntity> getAssistantModels(Long userId);

    /**
     * 获取用户的视觉模型
     * @param userId 用户ID
     * @return 视觉模型，如果没有则返回null
     */
    ModelsEntity getVisionModel(Long userId);

    /**
     * 保存用户模型
     * @param model 模型实体
     */
    void saveUserModel(ModelsEntity model);

    /**
     * 删除用户模型
     * @param modelId 模型ID
     * @param userId 用户ID
     */
    void deleteUserModel(Long modelId, Long userId);

    /**
     * 修改模型名称和渠道（模型不存在时不进行修改）
     * @param modelId 模型ID
     * @param userId 用户ID
     * @param newModelName 新模型名称
     * @param modelChannel 新模型渠道，为 null 时保持原渠道
     */
    void updateUserModel(Long modelId, Long userId, String newModelName, ModelChannel modelChannel);

    /**
     * 设置主模型
     * @param modelId 模型ID
     * @param userId 用户ID
     */
    void setMainModel(Long modelId, Long userId);

    /**
     * 检查用户是否可以添加更多模型
     * @param userId 用户ID
     * @return 是否可以添加
     */
    boolean canAddModel(Long userId);

    /**
     * 检查用户是否可以添加指定类型的模型
     * @param userId 用户ID
     * @param modelType 模型类型
     * @return 是否可以添加
     */
    boolean canAddModel(Long userId, ModelType modelType);

    /**
     * 检查用户是否支持多模型查询（有主模型和至少一个辅助模型）
     * @param userId 用户ID
     * @return 是否支持多模型查询
     */
    boolean supportsMultiModel(Long userId);

    /**
     * 验证模型和API Key是否有效，并探测模型的调用配置（端点、思考模式）
     * @param apiKey API Key
     * @param modelName 模型名称
     * @param modelType 模型类型（0=主模型，1=辅助模型，2=视觉模型）
     * @return 验证结果，isSuccess()为true表示验证通过（含探测到的调用配置），否则含错误信息
     */
    ModelValidationResult validateModel(String apiKey, String modelName, int modelType);

    /**
     * 验证模型和API Key是否有效（指定渠道）
     * @param apiKey API Key
     * @param modelName 模型名称
     * @param modelType 模型类型
     * @param channel 模型渠道
     * @return 验证结果
     */
    ModelValidationResult validateModel(String apiKey, String modelName, int modelType, ModelChannel channel);
}
