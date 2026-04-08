package pkqb.service;

import pkqb.common.Result;

/**
 * 速率限制服务接口
 */
public interface RateLimitService {

    /**
     * 检查用户是否超过每日限制
     * @param userId 用户ID
     * @param feature 功能类型 (chat/rag/knowledge/rubric)
     * @param limit 每日限制次数
     * @return 如果超过限制返回Result.error，否则返回null
     */
    Result<?> checkLimit(Long userId, String feature, int limit);

    /**
     * 增加用户使用次数
     * @param userId 用户ID
     * @param feature 功能类型
     */
    void incrementUsage(Long userId, String feature);

    /**
     * 获取用户今日使用次数
     * @param userId 用户ID
     * @param feature 功能类型
     * @return 今日使用次数
     */
    Long getTodayUsage(Long userId, String feature);
}