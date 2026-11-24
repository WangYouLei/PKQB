package pkqb.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pkqb.enums.ModelChannel;
import pkqb.pojo.entity.ModelsEntity;

import java.time.Duration;

/**
 * 阿里云百炼模型提供者
 * 实现 {@link ModelProvider} 接口，提供阿里云百炼（DashScope）模型的创建能力
 */
@Component
@Slf4j
public class DashScopeModelProvider implements ModelProvider {

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

    /** 模型 HTTP 调用超时（秒）：避免 DashScope 请求无限期挂起导致解析任务卡死 */
    @Value("${pkqb.ai.request-timeout.connect-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${pkqb.ai.request-timeout.read-seconds:180}")
    private int readTimeoutSeconds;

    private volatile ChatModel visionChatModel;

    @Override
    public String getChannel() {
        return ModelChannel.DASHSCOPE.getCode();
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public String getDefaultVisionModel() {
        return visionModel;
    }

    @Override
    public int getLocalMaxTokens() {
        return localMaxTokens;
    }

    @Override
    public int getPersonalMaxTokens() {
        return personalMaxTokens;
    }

    public String getDefaultApiKey() {
        return defaultApiKey;
    }

    /**
     * 构建带超时的 DashScopeApi
     */
    private DashScopeApi buildDashScopeApi(String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }

    @Override
    public ChatModel createChatModel(String apiKey) {
        return createChatModel(apiKey, defaultModel);
    }

    @Override
    public ChatModel createChatModel(String apiKey, String model) {
        return createChatModel(apiKey, model, personalMaxTokens);
    }

    @Override
    public ChatModel createChatModel(String apiKey, String model, int maxTokens) {
        return createChatModel(apiKey, model, maxTokens, null, false);
    }

    @Override
    public ChatModel createChatModel(String apiKey, String model, int maxTokens, Boolean enableThinking, boolean multiModel) {
        String actualModel = (model != null && !model.isEmpty()) ? model : defaultModel;
        DashScopeApi dashScopeApi = buildDashScopeApi(apiKey);
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(actualModel)
                        .withMaxToken(maxTokens)
                        .withEnableThinking(enableThinking)
                        .withMultiModel(multiModel)
                        .build())
                .build();
    }

    @Override
    public ChatModel createChatModel(String apiKey, ModelsEntity model) {
        if (model == null) {
            return createChatModel(apiKey, defaultModel, personalMaxTokens, null, false);
        }
        return createChatModel(apiKey, model.getModelName(), personalMaxTokens,
                Boolean.TRUE.equals(model.getEnableThinking()) ? Boolean.TRUE : null,
                Boolean.TRUE.equals(model.getMultiModel()));
    }

    /**
     * 创建系统默认视觉模型（使用平台 Key 和配置文件中的视觉模型名）
     */
    @Override
    public ChatModel createVisionChatModel() {
        if (visionChatModel == null) {
            synchronized (this) {
                if (visionChatModel == null) {
                    DashScopeApi dashScopeApi = buildDashScopeApi(defaultApiKey);
                    visionChatModel = DashScopeChatModel.builder()
                            .dashScopeApi(dashScopeApi)
                            .defaultOptions(DashScopeChatOptions.builder()
                                    .withModel(visionModel)
                                    .withMultiModel(true)
                                    .withMaxToken(8192)
                                    .withTemperature(0.2)
                                    .build())
                            .build();
                }
            }
        }
        return visionChatModel;
    }

    /**
     * 创建用户自定义视觉模型（使用用户 Key 和自定义模型名）
     */
    @Override
    public ChatModel createVisionChatModel(String apiKey, String modelName) {
        String actualModel = (modelName != null && !modelName.isEmpty()) ? modelName : visionModel;
        DashScopeApi dashScopeApi = buildDashScopeApi(apiKey);
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(actualModel)
                        .withMultiModel(true)
                        .withMaxToken(8192)
                        .withTemperature(0.2)
                        .build())
                .build();
    }

    @Override
    public ChatModel createLocalChatModel() {
        DashScopeApi dashScopeApi = buildDashScopeApi(defaultApiKey);
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(defaultModel)
                        .withMaxToken(localMaxTokens)
                        .withTemperature(0.2)
                        .build())
                .build();
    }

    @Override
    public boolean supportsFeature(String feature) {
        return switch (feature) {
            case "thinking", "vision", "multiModel", "embedding" -> true;
            default -> false;
        };
    }
}
