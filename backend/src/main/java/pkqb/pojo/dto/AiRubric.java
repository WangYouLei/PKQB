package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI解析题目 DTO
 * 用于存储AI解析后的题目信息
 */
@Data
@Schema(description = "AI解析的题目")
public class AiRubric {

    @Schema(description = "题目内容", example = "下列关于计算机的描述中，正确的是？")
    private String question;

    @Schema(description = "题型：single_choice(单选题), multiple_choice(多选题), true_false(判断题), short_answer(简答题), calculation(计算题)", example = "single_choice")
    private String questionType;

    @Schema(description = "选项（选择题）", example = "[\"A. CPU是中央处理器\", \"B. 内存用于存储数据\", \"C. 硬盘是外部存储器\", \"D. 显示器是输出设备\"]")
    @Setter(AccessLevel.NONE)
    private String[] options;

    /**
     * 兼容不同模型返回的 options 格式：
     * 本地模型通常返回字符串数组 ["A. xxx", "B. yyy"]
     * 用户上传模型可能返回对象数组 [{"label":"A","text":"xxx"}, ...]
     */
    public void setOptions(Object[] options) {
        if (options == null) {
            this.options = null;
        } else {
            this.options = Arrays.stream(options)
                    .map(obj -> {
                        if (obj instanceof String s) return s;
                        if (obj instanceof Map<?, ?> m) {
                            Object label = m.get("label");
                            Object text = m.get("text");
                            if (label != null && text != null) {
                                return label + ". " + text;
                            }
                            if (m.size() == 1) {
                                return String.valueOf(m.values().iterator().next());
                            }
                            return m.values().stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(" "));
                        }
                        return String.valueOf(obj);
                    })
                    .toArray(String[]::new);
        }
    }

    @Schema(description = "正确答案", example = "A")
    private String answer;

    @Schema(description = "题目解析", example = "CPU是Central Processing Unit的缩写，即中央处理器，是计算机的核心部件。")
    private String explanation;

    @Schema(description = "计算步骤（计算题）", example = "[\"第一步：理解题意\", \"第二步：列出公式\", \"第三步：代入计算\"]")
    private List<String> calculationSteps;

    @Schema(description = "题目关联的资源列表（图片等）")
    private List<AiResource> resources;

    @Data
    @Schema(description = "题目关联资源")
    public static class AiResource {
        @Schema(description = "资源类型：question_image(题目图片), option_image(选项图片), answer_image(答案图片), explanation_image(解析图片)", example = "question_image")
        private String type;

        @Schema(description = "标签，选项图片时填A/B/C/D", example = "A")
        private String label;

        @Schema(description = "资源URL", example = "http://192.168.236.200:9000/pkqb-files/question-image/xxx.png")
        private String url;
    }
}
