package pkqb.config;

import org.springframework.ai.chat.model.ChatModel;
import pkqb.pojo.entity.ModelsEntity;

/**
 * 模型提供者接口
 * 抽象不同AI模型渠道的创建逻辑，支持灵活切换模型厂商
 *
 * <p>实现类包括：
 * <ul>
 *   <li>{@link DashScopeModelProvider} - 阿里云百炼</li>
 *   <li>{@link OpenAIModelProvider} - OpenAI 兼容接口</li>
 *   <li>{@link DeepSeekModelProvider} - DeepSeek</li>
 * </ul>
 */
public interface ModelProvider {

    /**
     * 获取当前 Provider 的渠道类型
     */
    String getChannel();

    /**
     * 获取默认模型名称
     */
    String getDefaultModel();

    /**
     * 获取视觉模型名称
     */
    String getDefaultVisionModel();

    /**
     * 创建聊天模型（使用默认模型和默认配置）
     *
     * @param apiKey API Key
     * @return ChatModel 实例
     */
    ChatModel createChatModel(String apiKey);

    /**
     * 创建聊天模型（指定模型名称）
     *
     * @param apiKey  API Key
     * @param model   模型名称
     * @return ChatModel 实例
     */
    ChatModel createChatModel(String apiKey, String model);

    /**
     * 创建聊天模型（指定模型名称和最大 Token 数）
     *
     * @param apiKey   API Key
     * @param model    模型名称
     * @param maxTokens 最大 Token 数
     * @return ChatModel 实例
     */
    ChatModel createChatModel(String apiKey, String model, int maxTokens);

    /**
     * 创建聊天模型（完整参数）
     *
     * @param apiKey         API Key
     * @param model          模型名称
     * @param maxTokens      最大 Token 数
     * @param enableThinking 是否开启思考模式（部分模型支持）
     * @param multiModel     是否使用多模态端点
     * @return ChatModel 实例
     */
    ChatModel createChatModel(String apiKey, String model, int maxTokens, Boolean enableThinking, boolean multiModel);

    /**
     * 按用户模型配置创建 ChatModel
     *
     * @param apiKey API Key
     * @param model  用户模型实体（包含模型名称、类型等配置）
     * @return ChatModel 实例
     */
    ChatModel createChatModel(String apiKey, ModelsEntity model);

    /**
     * 创建视觉模型（用于图片理解）
     *
     * @return ChatModel 实例（单例）
     */
    ChatModel createVisionChatModel();

    /**
     * 创建用户自定义视觉模型
     *
     * @param apiKey    用户 API Key
     * @param modelName 视觉模型名称
     * @return ChatModel 实例
     */
    ChatModel createVisionChatModel(String apiKey, String modelName);

    /**
     * 创建本地解析用模型（使用平台默认 API Key）
     *
     * @return ChatModel 实例
     */
    ChatModel createLocalChatModel();

    /**
     * 获取本地解析的最大 Token 数
     */
    int getLocalMaxTokens();

    /**
     * 获取个人用户解析的最大 Token 数
     */
    int getPersonalMaxTokens();

    /**
     * 检查当前 Provider 是否支持指定功能
     *
     * @param feature 功能名称（如 "thinking", "vision", "multiModel"）
     * @return 是否支持
     */
    default boolean supportsFeature(String feature) {
        return false;
    }
}
