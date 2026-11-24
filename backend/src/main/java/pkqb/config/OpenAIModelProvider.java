package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pkqb.enums.ModelChannel;
import pkqb.pojo.entity.ModelsEntity;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI 兼容模型提供者
 * 实现 {@link ModelProvider} 接口，支持 OpenAI 兼容 API
 *
 * <p>可用于以下场景：
 * <ul>
 *   <li>OpenAI 官方 API（GPT-4、GPT-4o 等）</li>
 *   <li>DeepSeek 独立部署或 API</li>
 *   <li>Claude（通过兼容代理）</li>
 *   <li>其他兼容 OpenAI API 格式的模型服务</li>
 * </ul>
 */
@Component
@Slf4j
public class OpenAIModelProvider implements ModelProvider {

    @Value("${pkqb.ai.openai.default-model:gpt-4o-mini}")
    private String defaultModel;

    @Value("${pkqb.ai.openai.default-vision-model:gpt-4o-mini}")
    private String defaultVisionModel;

    @Value("${pkqb.ai.openai.local-max-tokens:8192}")
    private int localMaxTokens;

    @Value("${pkqb.ai.openai.personal-max-tokens:8192}")
    private int personalMaxTokens;

    @Value("${pkqb.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    /** 模型 HTTP 调用超时（秒） */
    @Value("${pkqb.ai.request-timeout.connect-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${pkqb.ai.request-timeout.read-seconds:180}")
    private int readTimeoutSeconds;

    /** 默认 API Key（平台级别） */
    @Value("${pkqb.ai.openai.api-key:}")
    private String defaultApiKey;

    private volatile ChatModel defaultVisionChatModel;

    /** 缓存用户创建的 ChatModel，避免重复创建 */
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    @Override
    public String getChannel() {
        return ModelChannel.OPENAI.getCode();
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public String getDefaultVisionModel() {
        return defaultVisionModel;
    }

    @Override
    public int getLocalMaxTokens() {
        return localMaxTokens;
    }

    @Override
    public int getPersonalMaxTokens() {
        return personalMaxTokens;
    }

    /**
     * 构建带超时的 OpenAiApi
     */
    private OpenAiApi buildOpenAiApi(String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
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
        OpenAiApi openAiApi = buildOpenAiApi(apiKey);

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(actualModel)
                .maxTokens(maxTokens);

        // OpenAI 兼容 API 不直接支持 thinking 模式，部分服务通过参数开启
        // 这里暂时不支持，后续可扩展
        if (enableThinking != null && enableThinking) {
            log.warn("[OpenAI Provider] 当前版本不支持 enableThinking 参数，将忽略");
        }

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    @Override
    public ChatModel createChatModel(String apiKey, ModelsEntity model) {
        if (model == null) {
            return createChatModel(apiKey, defaultModel, personalMaxTokens, null, false);
        }
        return createChatModel(apiKey, model.getModelName(), personalMaxTokens,
                model.getEnableThinking(),
                Boolean.TRUE.equals(model.getMultiModel()));
    }

    /**
     * 创建系统默认视觉模型
     */
    @Override
    public ChatModel createVisionChatModel() {
        if (defaultVisionChatModel == null) {
            synchronized (this) {
                if (defaultVisionChatModel == null) {
                    if (defaultApiKey == null || defaultApiKey.isEmpty()) {
                        throw new IllegalStateException("未配置默认 API Key，无法创建视觉模型");
                    }
                    OpenAiApi openAiApi = buildOpenAiApi(defaultApiKey);
                    defaultVisionChatModel = OpenAiChatModel.builder()
                            .openAiApi(openAiApi)
                            .defaultOptions(OpenAiChatOptions.builder()
                                    .model(defaultVisionModel)
                                    .maxTokens(8192)
                                    .build())
                            .build();
                }
            }
        }
        return defaultVisionChatModel;
    }

    /**
     * 创建用户自定义视觉模型
     */
    @Override
    public ChatModel createVisionChatModel(String apiKey, String modelName) {
        String actualModel = (modelName != null && !modelName.isEmpty()) ? modelName : defaultVisionModel;
        OpenAiApi openAiApi = buildOpenAiApi(apiKey);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(actualModel)
                        .maxTokens(8192)
                        .build())
                .build();
    }

    @Override
    public ChatModel createLocalChatModel() {
        if (defaultApiKey == null || defaultApiKey.isEmpty()) {
            throw new IllegalStateException("未配置默认 API Key，无法创建本地解析模型");
        }
        OpenAiApi openAiApi = buildOpenAiApi(defaultApiKey);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(defaultModel)
                        .maxTokens(localMaxTokens)
                        .temperature(0.2)
                        .build())
                .build();
    }

    @Override
    public boolean supportsFeature(String feature) {
        return switch (feature) {
            // OpenAI API 支持 vision（多模态）
            case "vision" -> true;
            // OpenAI 官方支持 thinking 模式，但需要特定模型
            case "thinking" -> true;
            default -> false;
        };
    }
}
