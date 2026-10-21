package pkqb.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 题目文件异步解析任务状态
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RubricParseTaskStatus {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String taskId;
    /** 任务归属用户，查询时校验 */
    private Long userId;
    /** RUNNING / SUCCESS / FAILED */
    private String status;
    /** 阶段：rendering / uploading / analyzing / merging */
    private String stage;
    /** 进度描述或失败原因 */
    private String message;
    /** 解析成功时的题目结果 */
    private List<AiRubric> result;

    public RubricParseTaskStatus() {
    }

    public RubricParseTaskStatus(String taskId, Long userId, String status, String stage, String message) {
        this.taskId = taskId;
        this.userId = userId;
        this.status = status;
        this.stage = stage;
        this.message = message;
    }
}
