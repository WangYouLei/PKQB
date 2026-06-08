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
}
