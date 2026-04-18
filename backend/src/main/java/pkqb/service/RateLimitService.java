package pkqb.service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pkqb.common.Result;

@Tag(name = "速率限制", description = "用户AI功能调用次数限制接口")
public interface RateLimitService {

    @Operation(summary = "检查速率限制", description = "检查用户是否超过每日调用次数限制")
    Result<?> checkLimit(Long userId, String feature, int limit);

    @Operation(summary = "增加使用次数", description = "记录用户AI功能调用次数")
    void incrementUsage(Long userId, String feature);

    @Operation(summary = "获取今日使用次数", description = "获取用户今日已调用的次数")
    Long getTodayUsage(Long userId, String feature);

    @Operation(summary = "检查是否需要限制", description = "检查用户是否需要被限制（有个人API Key的用户不受限制）")
    boolean shouldRateLimit(Long userId);
}