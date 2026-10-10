package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户模型实体类
 * 对应数据库models表，存储用户自定义AI模型配置
 */
@Data
@TableName("models")
public class ModelsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String modelName;

    /**
     * 模型类型：0=主模型，1=辅助模型，2=视觉模型
     */
    private Integer modelType;

    /**
     * 是否走 multimodal-generation 端点（新架构模型如 kimi-k3 需要）
     */
    private Boolean multiModel;

    /**
     * 是否需要显式开启思考模式（强制思考模型如 glm 系列）
     */
    private Boolean enableThinking;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
