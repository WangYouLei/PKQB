package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷实体类
 */
@Data
@TableName("rubric")
public class RubricEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String className;

    private Long createId;

    private String createStudentNo;

    /**
     * 是否私有：true=私有（仅自己可见），false=公开（班级可见）
     */
    private Boolean isPrivate;

    private Integer questionCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    @TableField(exist = false)
    private String creatorName;
}
