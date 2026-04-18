package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新头像请求
 */
@Data
@Schema(description = "更新头像请求")
public class AvatarUpdateRequest {

    @NotBlank(message = "对象路径不能为空")
    @Schema(description = "MinIO对象路径", example = "avatar/1/1234567890")
    private String objectKey;
}
