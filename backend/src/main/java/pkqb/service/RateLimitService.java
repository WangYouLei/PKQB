package pkqb.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pkqb.common.Result;

/**
 * 速率限制服务接口
 * 提供用户AI功能调用次数限制功能
 */
@Tag(name = "速率限制", description = "用户AI功能调用次数限制接口")
public interface RateLimitService {

    /**
     * 检查速率限制
     * 检查用户是否超过每日调用次数限制
     *
     * @param userId 用户ID
     * @param feature 功能类型（如chat、rag、rubric等）
     * @param limit 每日限制次数
     * @return 如果超过限制返回错误结果，否则返回null
     */
    @Operation(summary = "检查速率限制", description = "检查用户是否超过每日调用次数限制")
    Result<?> checkLimit(Long userId, String feature, int limit);

    /**
     * 增加使用次数
     * 记录用户AI功能调用次数
     *
     * @param userId 用户ID
     * @param feature 功能类型
     */
    @Operation(summary = "增加使用次数", description = "记录用户AI功能调用次数")
    void incrementUsage(Long userId, String feature);

    /**
     * 获取今日使用次数
     * 获取用户今日已调用的次数
     *
     * @param userId 用户ID
     * @param feature 功能类型
     * @return 今日使用次数
     */
    @Operation(summary = "获取今日使用次数", description = "获取用户今日已调用的次数")
    Long getTodayUsage(Long userId, String feature);

    /**
     * 检查是否需要限制
     * 有个人API Key的用户不受限制
     *
     * @param userId 用户ID
     * @return true表示需要限制，false表示不需要限制
     */
    @Operation(summary = "检查是否需要限制", description = "检查用户是否需要被限制（有个人API Key的用户不受限制）")
    boolean shouldRateLimit(Long userId);
}
