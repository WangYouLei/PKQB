package pkqb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import pkqb.pojo.entity.FileEntity;

/**
 * 文件 Mapper 接口
 */
@Mapper
public interface FileMapper extends BaseMapper<FileEntity> {
}
