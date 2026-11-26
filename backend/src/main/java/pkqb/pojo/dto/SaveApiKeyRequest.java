package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "保存API Key请求")
public class SaveApiKeyRequest {
    @NotBlank(message = "API Key不能为空")
    @Size(min = 1, max = 512, message = "API Key长度不合法")
    @Schema(description = "API Key")
    private String apiKey;

    @Schema(description = "模型渠道：dashscope=阿里云百炼，openai=OpenAI兼容接口，deepseek=DeepSeek 等，默认 dashscope")
    private String channel;
}
