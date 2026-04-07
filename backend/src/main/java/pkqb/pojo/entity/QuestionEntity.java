package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目实体类
 */
@Data
@TableName("question")
public class QuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long rubricId;

    private Integer orderIndex;

    private String questionText;

    private String questionType;

    private String optionsJson;

    private String answer;

    private String explanation;

    private String calculationStepsJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
