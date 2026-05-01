package pkqb.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import pkqb.common.Result;
import pkqb.service.RateLimitService;
import pkqb.service.UserApiKeyService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制服务实现类
 * 基于Redis实现用户AI功能调用次数限制
 */
@Service
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserApiKeyService userApiKeyService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String KEY_PREFIX = "rate_limit:";

    public RateLimitServiceImpl(StringRedisTemplate stringRedisTemplate, UserApiKeyService userApiKeyService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userApiKeyService = userApiKeyService;
    }

    @Override
    public Result<?> checkLimit(Long userId, String feature, int limit) {
        if (!shouldRateLimit(userId)) {
            return null;
        }
        Long currentUsage = getTodayUsage(userId, feature);
        if (currentUsage >= limit) {
            return Result.error("今日使用次数已达上限(" + limit + "次)，请明天再试");
        }
        return null;
    }

    @Override
    public void incrementUsage(Long userId, String feature) {
        if (!shouldRateLimit(userId)) {
            return;
        }
        String key = buildKey(userId, feature);
        Long increment = stringRedisTemplate.opsForValue().increment(key);
        if (increment != null && increment == 1) {
            setExpiryToMidnight(key);
        }
    }

    @Override
    public Long getTodayUsage(Long userId, String feature) {
        String key = buildKey(userId, feature);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public boolean shouldRateLimit(Long userId) {
        return !userApiKeyService.hasUserOwnApiKey(userId);
    }

    private String buildKey(Long userId, String feature) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return KEY_PREFIX + feature + ":" + userId + ":" + today;
    }

    private void setExpiryToMidnight(String key) {
        long secondsUntilMidnight = getSecondsUntilMidnight();
        stringRedisTemplate.expire(key, secondsUntilMidnight, TimeUnit.SECONDS);
    }

    private long getSecondsUntilMidnight() {
        LocalDate now = LocalDate.now();
        LocalDateTime nowDateTime = LocalDateTime.now();
        LocalDateTime midnight = now.plusDays(1).atStartOfDay();
        long seconds = midnight.toEpochSecond(ZoneOffset.ofHours(8)) - nowDateTime.toEpochSecond(ZoneOffset.ofHours(8));
        return seconds;
    }
}
