package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户分渠道 API Key 实体类
 * 对应数据库 user_api_key 表，同一用户每个模型渠道可保存一份 API Key
 */
@Data
@TableName("user_api_key")
public class UserApiKeyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 模型渠道编码：dashscope/openai/deepseek/zhipu/kimi/wenxin/hunyuan
     */
    private String channel;

    /**
     * 加密存储的 API Key
     */
    private String apiKey;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
