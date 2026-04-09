package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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

    private Boolean isPrivate;

    private Integer questionCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
