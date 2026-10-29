package pkqb.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pkqb.mapper.UserMapper;
import pkqb.pojo.entity.UserEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端权限拦截器
 * 校验当前登录用户是否为管理员（role=1）
 * 需在 JwtInterceptor 之后执行，依赖其设置的 userId
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    // 角色短时缓存（60秒）：避免每个管理端请求都selectById查一次库；
    // 角色被修改/降权后最迟60秒生效，兼顾性能与权限回收时效
    private static final long ROLE_CACHE_TTL_MS = 60_000L;

    private record RoleCacheEntry(Integer role, long expireAt) {}

    private final Map<Long, RoleCacheEntry> roleCache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            response.getWriter().flush();
            return false;
        }

        Integer role = getCachedRole(userId);
        if (role == null || role != 1) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            log.info("非管理员访问被拒绝, userId={}", userId);
            response.getWriter().write("{\"code\":403,\"message\":\"无管理员权限\"}");
            response.getWriter().flush();
            return false;
        }

        return true;
    }

    /**
     * 查询用户角色（带60秒本地缓存）
     */
    private Integer getCachedRole(Long userId) {
        RoleCacheEntry entry = roleCache.get(userId);
        if (entry != null && entry.expireAt() > System.currentTimeMillis()) {
            return entry.role();
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null) {
            return null;
        }
        roleCache.put(userId, new RoleCacheEntry(user.getRole(), System.currentTimeMillis() + ROLE_CACHE_TTL_MS));
        return user.getRole();
    }
}
