package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级实体类
 * 对应数据库class表，存储班级信息
 */
@Data
@TableName("class")
public class ClassEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String className;

    private LocalDateTime createTime;
}
