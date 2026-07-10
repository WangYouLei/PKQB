package pkqb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import pkqb.pojo.entity.AuditLogEntity;

/**
 * 审计日志 Mapper 接口
 * 提供审计日志表的数据库操作
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
