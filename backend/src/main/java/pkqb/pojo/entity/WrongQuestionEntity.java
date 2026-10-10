package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题本实体
 */
@Data
@TableName("wrong_question")
public class WrongQuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 原题目ID */
    private Long questionId;

    /** 所属试卷ID */
    private Long rubricId;

    /** 题目内容（冗余存储） */
    private String questionText;

    /** 题型 */
    private String questionType;

    /** 选项JSON */
    private String optionsJson;

    /** 正确答案 */
    private String answer;

    /** 解析 */
    private String explanation;

    /** 计算步骤JSON */
    private String calculationStepsJson;

    /** 用户错误答案 */
    private String userAnswer;

    /** 错误次数 */
    private Integer wrongCount;

    /** 正确次数（复习时答对） */
    private Integer correctCount;

    /** SM-2算法的易度因子（>=1.3） */
    private Double easeFactor;

    /** 复习间隔天数 */
    private Integer intervalDays;

    /** 下次复习日期 */
    private LocalDate nextReviewDate;

    /** 上次复习时间 */
    private LocalDateTime lastReviewTime;

    /** 掌握程度：0=未掌握, 1=初步掌握, 2=基本掌握, 3=完全掌握 */
    private Integer masteryLevel;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 试卷标题（非数据库字段） */
    @TableField(exist = false)
    private String rubricTitle;

    @TableField(exist = false)
    private List<QuestionResourceEntity> resources;
}
