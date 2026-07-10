package pkqb.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DashScopeModelFactory {

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String defaultModel;

    @Value("${spring.ai.dashscope.chat.options.localhost-max-tokens}")
    private int localMaxTokens;

    @Value("${spring.ai.dashscope.chat.options.personal-max-tokens}")
    private int personalMaxTokens;

    @Value("${spring.ai.dashscope.vision.model}")
    private String visionModel;

    @Value("${spring.ai.dashscope.api-key}")
    private String defaultApiKey;

    private volatile ChatModel visionChatModel;

    public ChatModel createChatModel(String apiKey) {
        return createChatModel(apiKey, defaultModel);
    }

    public ChatModel createChatModel(String apiKey, String model) {
        return createChatModel(apiKey, model, personalMaxTokens);
    }

    public ChatModel createChatModel(String apiKey, String model, int maxTokens) {
        String actualModel = (model != null && !model.isEmpty()) ? model : defaultModel;
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(actualModel)
                        .withMaxToken(maxTokens)
                        .withTemperature(0.7)
                        .build())
                .build();
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String getDefaultApiKey() {
        return defaultApiKey;
    }

    public String getVisionModel() {
        return visionModel;
    }

    /**
     * 创建系统默认视觉模型（使用平台Key和配置文件中的视觉模型名）
     */
    public ChatModel createVisionChatModel() {
        if (visionChatModel == null) {
            synchronized (this) {
                if (visionChatModel == null) {
                    DashScopeApi dashScopeApi = DashScopeApi.builder()
                            .apiKey(defaultApiKey)
                            .build();
                    visionChatModel = DashScopeChatModel.builder()
                            .dashScopeApi(dashScopeApi)
                            .defaultOptions(DashScopeChatOptions.builder()
                                    .withModel(visionModel)
                                    .withMultiModel(true)
                                    .withMaxToken(4096)
                                    .withTemperature(0.7)
                                    .build())
                            .build();
                }
            }
        }
        return visionChatModel;
    }

    /**
     * 创建用户自定义视觉模型（使用用户Key和自定义模型名）
     * @param apiKey 用户API Key
     * @param modelName 视觉模型名称
     */
    public ChatModel createVisionChatModel(String apiKey, String modelName) {
        String actualModel = (modelName != null && !modelName.isEmpty()) ? modelName : visionModel;
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(actualModel)
                        .withMultiModel(true)
                        .withMaxToken(4096)
                        .withTemperature(0.7)
                        .build())
                .build();
    }

    public int getLocalMaxTokens() {
        return localMaxTokens;
    }

    public int getPersonalMaxTokens() {
        return personalMaxTokens;
    }

    public ChatModel createLocalChatModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(defaultApiKey)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(defaultModel)
                        .withMaxToken(localMaxTokens)
                        .withTemperature(0.7)
                        .build())
                .build();
    }
}
