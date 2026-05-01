package pkqb.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

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
    private String[] options;

    @Schema(description = "正确答案", example = "A")
    private String answer;

    @Schema(description = "题目解析", example = "CPU是Central Processing Unit的缩写，即中央处理器，是计算机的核心部件。")
    private String explanation;

    @Schema(description = "计算步骤（计算题）", example = "[\"第一步：理解题意\", \"第二步：列出公式\", \"第三步：代入计算\"]")
    private List<String> calculationSteps;
}
