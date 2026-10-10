package pkqb.pojo.dto;

/**
 * 模型验证结果：包含错误信息及探测到的模型调用配置
 *
 * @param error 验证错误信息，null 表示验证通过
 * @param multiModel 是否需要走 multimodal-generation 端点（新架构模型如 kimi-k3）
 * @param enableThinking 是否需要显式开启思考模式（强制思考模型如 glm 系列）
 */
public record ModelValidationResult(String error, boolean multiModel, boolean enableThinking) {

    public static ModelValidationResult success(boolean multiModel, boolean enableThinking) {
        return new ModelValidationResult(null, multiModel, enableThinking);
    }

    public static ModelValidationResult fail(String error) {
        return new ModelValidationResult(error, false, false);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
