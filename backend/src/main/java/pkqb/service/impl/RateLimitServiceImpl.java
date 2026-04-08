package pkqb.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import pkqb.common.Result;
import pkqb.service.RateLimitService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制服务实现类
 * 使用 Redis 实现每日次数限制
 */
@Service
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Redis key 前缀
    private static final String KEY_PREFIX = "rate_limit:";

    public RateLimitServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result<?> checkLimit(Long userId, String feature, int limit) {
        Long currentUsage = getTodayUsage(userId, feature);
        if (currentUsage >= limit) {
            return Result.error("今日使用次数已达上限(" + limit + "次)，请明天再试");
        }
        return null;
    }

    @Override
    public void incrementUsage(Long userId, String feature) {
        String key = buildKey(userId, feature);
        Long increment = stringRedisTemplate.opsForValue().increment(key);
        // 设置过期时间为当前日期的23:59:59
        setExpiryToMidnight(key);
        if (increment != null && increment == 1) {
            // 首次设置过期时间
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

    /**
     * 构建 Redis key: rate_limit:{feature}:{userId}:{date}
     */
    private String buildKey(Long userId, String feature) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return KEY_PREFIX + feature + ":" + userId + ":" + today;
    }

    /**
     * 设置过期时间为当天午夜
     */
    private void setExpiryToMidnight(String key) {
        // 计算到午夜剩余的秒数
        long secondsUntilMidnight = getSecondsUntilMidnight();
        stringRedisTemplate.expire(key, secondsUntilMidnight, TimeUnit.SECONDS);
    }

    /**
     * 获取到午夜剩余的秒数
     */
    private long getSecondsUntilMidnight() {
        LocalDate now = LocalDate.now();
        LocalDateTime nowDateTime = LocalDateTime.now();
        LocalDateTime midnight = now.plusDays(1).atStartOfDay();
        long seconds = midnight.toEpochSecond(java.time.ZoneOffset.UTC) - nowDateTime.toEpochSecond(java.time.ZoneOffset.UTC);
        return seconds;
    }
}