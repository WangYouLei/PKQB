package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_resource")
public class QuestionResourceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private String type;

    private String label;

    private String url;

    private String mimeType;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
