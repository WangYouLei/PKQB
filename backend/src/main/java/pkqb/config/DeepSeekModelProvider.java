package pkqb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pkqb.enums.ModelChannel;

/**
 * DeepSeek 模型提供者
 *
 * <p>DeepSeek 使用 OpenAI 兼容协议，因此复用 {@link OpenAIModelProvider} 的创建逻辑，
 * 仅覆盖渠道、接口地址、默认模型和平台级 API Key。
 */
@Component
@Slf4j
public class DeepSeekModelProvider extends OpenAIModelProvider {

    @Value("${pkqb.ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    @Value("${pkqb.ai.deepseek.default-model:deepseek-chat}")
    private String deepseekDefaultModel;

    @Value("${pkqb.ai.deepseek.default-vision-model:deepseek-chat}")
    private String deepseekDefaultVisionModel;

    @Value("${pkqb.ai.deepseek.local-max-tokens:8192}")
    private int deepseekLocalMaxTokens;

    @Value("${pkqb.ai.deepseek.personal-max-tokens:8192}")
    private int deepseekPersonalMaxTokens;

    @Value("${pkqb.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Override
    public String getChannel() {
        return ModelChannel.DEEPSEEK.getCode();
    }

    @Override
    protected String getBaseUrl() {
        return deepseekBaseUrl;
    }

    @Override
    protected String getDefaultApiKey() {
        return deepseekApiKey;
    }

    @Override
    public String getDefaultModel() {
        return deepseekDefaultModel;
    }

    @Override
    public String getDefaultVisionModel() {
        return deepseekDefaultVisionModel;
    }

    @Override
    public int getLocalMaxTokens() {
        return deepseekLocalMaxTokens;
    }

    @Override
    public int getPersonalMaxTokens() {
        return deepseekPersonalMaxTokens;
    }

    @Override
    public boolean supportsFeature(String feature) {
        // DeepSeek 通过 deepseek-reasoner 支持推理，但不支持 OpenAI 风格的 thinking 参数和视觉输入
        return false;
    }
}
