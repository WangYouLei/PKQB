package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件实体类
 * 对应数据库file表，存储文件元数据信息
 */
@Data
@TableName("file")
public class FileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long rubricId;

    private String fileName;

    private String minioKey;

    private Boolean isPrivate;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
