package pkqb.enums;

import lombok.Getter;

/**
 * 模型渠道枚举
 * 定义支持的AI模型提供渠道
 */
@Getter
public enum ModelChannel {

    /**
     * 阿里云百炼（DashScope）
     * 支持 qwen、deepseek 等模型
     */
    DASHSCOPE("dashscope", "阿里云百炼"),

    /**
     * OpenAI 兼容 API
     * 支持 OpenAI、DeepSeek（独立部署）、Claude（通过兼容代理）等
     * 需要配置 base-url
     */
    OPENAI("openai", "OpenAI 兼容接口"),

    /**
     * DeepSeek
     * 支持 deepseek-chat、deepseek-reasoner 等模型
     * 使用 OpenAI 兼容协议，base-url 需指向 DeepSeek 开放平台
     */
    DEEPSEEK("deepseek", "DeepSeek"),

    /**
     * 智谱 GLM
     * 支持 glm-4 等智谱模型
     */
    ZHIPU("zhipu", "智谱 GLM"),

    /**
     * Kimi（月之暗面）
     * 支持 moonshot 系列模型
     */
    KIMI("kimi", "Kimi 月之暗面"),

    /**
     * 百度文心一言
     * 支持 ernie 系列模型
     */
    WENXIN("wenxin", "百度文心一言"),

    /**
     * 腾讯混元
     * 支持 hunyuan 系列模型
     */
    HUNYUAN("hunyuan", "腾讯混元");

    private final String code;
    private final String description;

    ModelChannel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 查找枚举
     */
    public static ModelChannel fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return DASHSCOPE; // 默认为阿里云百炼
        }
        for (ModelChannel channel : values()) {
            if (channel.code.equalsIgnoreCase(code)) {
                return channel;
            }
        }
        return DASHSCOPE;
    }
}
