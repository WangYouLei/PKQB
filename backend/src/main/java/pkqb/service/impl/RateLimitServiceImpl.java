package pkqb.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import pkqb.common.Result;
import pkqb.service.RateLimitService;
import pkqb.service.UserApiKeyService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
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

    /**
     * Lua脚本：原子地检查并增加使用次数
     * KEYS[1] = 限流key
     * ARGV[1] = 限制次数
     * ARGV[2] = 过期时间（秒）
     * 返回值：-1 表示超限，否则返回当前使用次数
     */
    private static final String CHECK_AND_INCREMENT_SCRIPT =
            "local current = redis.call('GET', KEYS[1]) " +
            "if current and tonumber(current) >= tonumber(ARGV[1]) then " +
            "  return -1 " +
            "end " +
            "local result = redis.call('INCR', KEYS[1]) " +
            "if result == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "end " +
            "return result";

    private static final String INCREMENT_AND_EXPIRE_SCRIPT =
            "local result = redis.call('INCR', KEYS[1]) " +
            "if result == 1 then " +
            "  local seconds = tonumber(ARGV[1]) " +
            "  redis.call('EXPIRE', KEYS[1], seconds) " +
            "end " +
            "return result";

    private final DefaultRedisScript<Long> checkAndIncrementScript;
    private final DefaultRedisScript<Long> incrementAndExpireScript;

    public RateLimitServiceImpl(StringRedisTemplate stringRedisTemplate, UserApiKeyService userApiKeyService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userApiKeyService = userApiKeyService;

        this.checkAndIncrementScript = new DefaultRedisScript<>();
        this.checkAndIncrementScript.setScriptText(CHECK_AND_INCREMENT_SCRIPT);
        this.checkAndIncrementScript.setResultType(Long.class);

        this.incrementAndExpireScript = new DefaultRedisScript<>();
        this.incrementAndExpireScript.setScriptText(INCREMENT_AND_EXPIRE_SCRIPT);
        this.incrementAndExpireScript.setResultType(Long.class);
    }

    @Override
    public Result<?> checkLimit(Long userId, String feature, int limit) {
        if (!shouldRateLimit(userId)) {
            return null;
        }
        String key = buildKey(userId, feature);
        long secondsUntilMidnight = getSecondsUntilMidnight();
        Long result = stringRedisTemplate.execute(
                checkAndIncrementScript,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(secondsUntilMidnight)
        );
        if (result != null && result == -1) {
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
        long secondsUntilMidnight = getSecondsUntilMidnight();
        stringRedisTemplate.execute(
                incrementAndExpireScript,
                Collections.singletonList(key),
                String.valueOf(secondsUntilMidnight)
        );
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
        ZoneId zone = ZoneId.systemDefault();
        long seconds = midnight.atZone(zone).toEpochSecond() - nowDateTime.atZone(zone).toEpochSecond();
        return seconds;
    }
}
