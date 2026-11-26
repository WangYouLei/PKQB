package pkqb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import pkqb.pojo.entity.UserApiKeyEntity;

/**
 * 用户分渠道 API Key Mapper 接口
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKeyEntity> {
}
