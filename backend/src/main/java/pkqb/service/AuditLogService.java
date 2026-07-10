package pkqb.service;

/**
 * 审计日志服务接口
 * 提供操作日志记录功能，用于记录用户操作行为
 */
public interface AuditLogService {
    
    /**
     * 记录审计日志
     * 异步记录用户操作日志到数据库
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param action 操作类型（如CREATE、UPDATE、DELETE等）
     * @param resource 资源类型（如FILE、RUBRIC、USER等）
     * @param resourceId 资源ID
     * @param detail 操作详情
     * @param ipAddress 客户端IP地址
     */
    void log(Long userId, String username, String action, String resource, String resourceId, String detail, String ipAddress);
    
    /**
     * 记录审计日志（不包含IP地址）
     * 异步记录用户操作日志到数据库
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param action 操作类型
     * @param resource 资源类型
     * @param resourceId 资源ID
     * @param detail 操作详情
     */
    void log(Long userId, String username, String action, String resource, String resourceId, String detail);
}
