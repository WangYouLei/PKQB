package pkqb.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pkqb.mapper.AuditLogMapper;
import pkqb.pojo.entity.AuditLogEntity;
import pkqb.service.AuditLogService;

import java.time.LocalDateTime;

/**
 * 审计日志服务实现类
 * 异步记录用户操作日志到数据库
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Async
    public void log(Long userId, String username, String action, String resource, String resourceId, String detail, String ipAddress) {
        try {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setAction(action);
            entity.setResource(resource);
            entity.setResourceId(resourceId);
            entity.setDetail(detail);
            entity.setIpAddress(ipAddress);
            entity.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("保存审计日志失败: {}", e.getMessage());
        }
    }

    @Override
    @Async
    public void log(Long userId, String username, String action, String resource, String resourceId, String detail) {
        log(userId, username, action, resource, resourceId, detail, null);
    }
}
