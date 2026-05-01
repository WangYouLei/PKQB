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

    public int getLocalMaxTokens() {
        return localMaxTokens;
    }

    public int getPersonalMaxTokens() {
        return personalMaxTokens;
    }
}
