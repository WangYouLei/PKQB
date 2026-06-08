package pkqb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import pkqb.pojo.entity.WrongQuestionEntity;

/**
 * 错题本 Mapper 接口
 */
@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestionEntity> {
}
