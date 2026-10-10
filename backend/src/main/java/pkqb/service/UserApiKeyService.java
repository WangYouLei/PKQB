package pkqb.service;

import pkqb.enums.ApiKeyMode;
import pkqb.enums.ModelType;
import pkqb.pojo.entity.ModelsEntity;
import pkqb.pojo.dto.ModelValidationResult;

import java.util.List;

public interface UserApiKeyService {

    /**
     * 各类型模型最大数量限制
     */
    int MAX_MAIN_MODEL_COUNT = 1;
    int MAX_ASSISTANT_MODEL_COUNT = 2;
    int MAX_VISION_MODEL_COUNT = 1;
    int MAX_MODEL_COUNT = MAX_MAIN_MODEL_COUNT + MAX_ASSISTANT_MODEL_COUNT + MAX_VISION_MODEL_COUNT;

    void saveApiKey(Long userId, String apiKey);

    void deleteApiKey(Long userId);

    String getPlainApiKey(Long userId);

    ApiKeyMode getApiKeyMode(Long userId);

    boolean hasUserOwnApiKey(Long userId);

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
     * 修改模型名称（模型不存在时不进行修改）
     * @param modelId 模型ID
     * @param userId 用户ID
     * @param newModelName 新模型名称
     */
    void updateUserModelName(Long modelId, Long userId, String newModelName);

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
}
