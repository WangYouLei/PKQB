package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import pkqb.pojo.entity.ModelsEntity;

/**
 * 阿里云百炼模型工厂（兼容类）
 *
 * <p>此类已迁移到 {@link DashScopeModelProvider}，保留此类是为了向后兼容旧代码。
 * 新代码建议直接使用 {@link DashScopeModelProvider} 或通过 {@link ModelProviderFactory} 获取。
 *
 * @deprecated 请使用 {@link DashScopeModelProvider} 或 {@link ModelProviderFactory}
 */
@Component
@Slf4j
@Deprecated
public class DashScopeModelFactory {

    private final DashScopeModelProvider modelProvider;

    public DashScopeModelFactory(DashScopeModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    public ChatModel createChatModel(String apiKey) {
        return modelProvider.createChatModel(apiKey);
    }

    public ChatModel createChatModel(String apiKey, String model) {
        return modelProvider.createChatModel(apiKey, model);
    }

    public ChatModel createChatModel(String apiKey, String model, int maxTokens) {
        return modelProvider.createChatModel(apiKey, model, maxTokens);
    }

    public ChatModel createChatModel(String apiKey, String model, int maxTokens, Boolean enableThinking) {
        return modelProvider.createChatModel(apiKey, model, maxTokens, enableThinking, false);
    }

    public ChatModel createChatModel(String apiKey, String model, int maxTokens, Boolean enableThinking, boolean multiModel) {
        return modelProvider.createChatModel(apiKey, model, maxTokens, enableThinking, multiModel);
    }

    public ChatModel createChatModel(String apiKey, ModelsEntity model) {
        return modelProvider.createChatModel(apiKey, model);
    }

    public String getDefaultModel() {
        return modelProvider.getDefaultModel();
    }

    public String getDefaultApiKey() {
        return modelProvider.getDefaultApiKey();
    }

    public String getVisionModel() {
        return modelProvider.getDefaultVisionModel();
    }

    public ChatModel createVisionChatModel() {
        return modelProvider.createVisionChatModel();
    }

    public ChatModel createVisionChatModel(String apiKey, String modelName) {
        return modelProvider.createVisionChatModel(apiKey, modelName);
    }

    public int getLocalMaxTokens() {
        return modelProvider.getLocalMaxTokens();
    }

    public int getPersonalMaxTokens() {
        return modelProvider.getPersonalMaxTokens();
    }

    public ChatModel createLocalChatModel() {
        return modelProvider.createLocalChatModel();
    }
}
