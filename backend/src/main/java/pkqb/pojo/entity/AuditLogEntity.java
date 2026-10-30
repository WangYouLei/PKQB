package pkqb.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体类
 * 对应数据库audit_log表，记录用户操作日志
 */
@Data
@TableName("audit_log")
public class AuditLogEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String action;
    
    private String resource;
    
    private String resourceId;
    
    private String detail;
    
    private String ipAddress;
    
    private LocalDateTime createTime;
}
