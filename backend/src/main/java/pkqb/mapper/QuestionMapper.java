package pkqb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import pkqb.pojo.entity.QuestionEntity;

/**
 * 题目 Mapper 接口
 */
@Mapper
public interface QuestionMapper extends BaseMapper<QuestionEntity> {
}
