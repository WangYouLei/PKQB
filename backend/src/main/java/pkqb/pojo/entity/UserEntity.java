package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import pkqb.enums.ModelChannel;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库user表，存储用户基本信息
 */
@Data
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String studentNo;

    private Integer classId;

    private String apiKey;

    /**
     * 用户 API Key 对应的模型渠道
     * dashscope=阿里云百炼，openai=OpenAI兼容接口，zhipu=智谱GLM 等
     * 默认为 dashscope
     */
    private String modelChannel;

    private String avatarUrl;

    /** 角色：0普通用户 1管理员 */
    private Integer role;

    /** 逻辑删除：0未删除 1已删除 */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 获取模型渠道枚举
     */
    public ModelChannel getModelChannelEnum() {
        return ModelChannel.fromCode(this.modelChannel);
    }

    /**
     * 设置模型渠道（自动转换为代码）
     */
    public void setModelChannelEnum(ModelChannel channel) {
        this.modelChannel = channel.getCode();
    }
}
