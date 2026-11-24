package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pkqb.enums.ModelChannel;
import pkqb.pojo.entity.ModelsEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型提供者工厂
 *
 * <p>根据配置或模型信息自动选择合适的 {@link ModelProvider} 实现类。
 *
 * <p>使用方式：
 * <pre>
 * // 方式1：根据渠道获取 Provider
 * ModelProvider provider = modelProviderFactory.getProvider(ModelChannel.OPENAI);
 *
 * // 方式2：根据 ModelsEntity 获取 Provider（自动识别渠道）
 * ModelProvider provider = modelProviderFactory.getProvider(modelsEntity);
 *
 * // 方式3：获取默认 Provider
 * ModelProvider provider = modelProviderFactory.getDefaultProvider();
 * </pre>
 */
@Component
@Slf4j
public class ModelProviderFactory {

    private final Map<String, ModelProvider> providerMap = new ConcurrentHashMap<>();
    private final DashScopeModelProvider dashScopeProvider;
    private final OpenAIModelProvider openAiProvider;

    /**
     * 默认渠道配置
     */
    private final String defaultChannel;

    public ModelProviderFactory(
            @Autowired(required = false) List<ModelProvider> providers,
            @Autowired(required = false) DashScopeModelProvider dashScopeProvider,
            @Autowired(required = false) OpenAIModelProvider openAiProvider) {

        this.dashScopeProvider = dashScopeProvider;
        this.openAiProvider = openAiProvider;

        // 注册所有 Provider
        if (providers != null) {
            for (ModelProvider provider : providers) {
                registerProvider(provider);
            }
        }

        // 默认渠道为阿里云百炼
        this.defaultChannel = ModelChannel.DASHSCOPE.getCode();
        log.info("[ModelProviderFactory] 初始化完成，已注册渠道: {}", providerMap.keySet());
    }

    /**
     * 注册 Provider
     */
    private void registerProvider(ModelProvider provider) {
        String channel = provider.getChannel();
        providerMap.put(channel, provider);
        log.info("[ModelProviderFactory] 注册模型渠道: {} -> {}", channel, provider.getClass().getSimpleName());
    }

    /**
     * 根据渠道代码获取 Provider
     *
     * @param channel 渠道代码（如 "dashscope", "openai"）
     * @return 对应的 Provider
     * @throws IllegalArgumentException 如果渠道不支持
     */
    public ModelProvider getProvider(String channel) {
        if (channel == null || channel.isEmpty()) {
            return getDefaultProvider();
        }

        ModelProvider provider = providerMap.get(channel.toLowerCase());
        if (provider == null) {
            log.warn("[ModelProviderFactory] 不支持的渠道: {}，使用默认渠道: {}", channel, defaultChannel);
            return getDefaultProvider();
        }
        return provider;
    }

    /**
     * 根据渠道枚举获取 Provider
     */
    public ModelProvider getProvider(ModelChannel channel) {
        return getProvider(channel.getCode());
    }

    /**
     * 根据 ModelsEntity 获取 Provider（自动识别渠道）
     */
    public ModelProvider getProvider(ModelsEntity model) {
        if (model == null) {
            return getDefaultProvider();
        }
        return getProvider(model.getModelChannel());
    }

    /**
     * 获取默认 Provider（阿里云百炼）
     */
    public ModelProvider getDefaultProvider() {
        return getProvider(defaultChannel);
    }

    /**
     * 获取阿里云百炼 Provider
     */
    public DashScopeModelProvider getDashScopeProvider() {
        return dashScopeProvider;
    }

    /**
     * 获取 OpenAI Provider
     */
    public OpenAIModelProvider getOpenAiProvider() {
        return openAiProvider;
    }

    /**
     * 创建聊天模型（根据渠道自动选择 Provider）
     *
     * @param apiKey API Key
     * @param model  模型实体
     * @return ChatModel 实例
     */
    public ChatModel createChatModel(String apiKey, ModelsEntity model) {
        ModelProvider provider = getProvider(model);
        return provider.createChatModel(apiKey, model);
    }

    /**
     * 创建聊天模型（指定渠道）
     */
    public ChatModel createChatModel(String apiKey, String channel, String modelName) {
        return getProvider(channel).createChatModel(apiKey, modelName);
    }

    /**
     * 检查是否支持指定渠道
     */
    public boolean isChannelSupported(String channel) {
        return providerMap.containsKey(channel.toLowerCase());
    }

    /**
     * 获取所有已注册的渠道列表
     */
    public List<String> getSupportedChannels() {
        return List.copyOf(providerMap.keySet());
    }
}
