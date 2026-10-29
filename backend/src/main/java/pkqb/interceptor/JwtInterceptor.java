package pkqb.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pkqb.util.JwtUtil;

/**
 * JWT认证拦截器
 * 拦截请求并验证JWT令牌的有效性
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = getTokenFromRequest(request);

        // 一次解析并复用Claims，避免validate/getUserId/getUsername各自重复解析同一token（此前同token解析3次）
        Claims claims = null;
        if (token != null) {
            try {
                claims = jwtUtil.parseToken(token);
            } catch (Exception e) {
                log.info("token解析失败或已过期: {}", e.getMessage());
            }
        }
        if (claims == null) {
            return unauthorized(response, "未登录或token已过期");
        }

        // 检查 token 是否在黑名单中（已登出）
        if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + token))) {
            return unauthorized(response, "token已失效，请重新登录");
        }

        request.setAttribute("userId", claims.get("userId", Long.class));
        request.setAttribute("username", claims.getSubject());

        return true;
    }

    /**
     * 统一返回401未认证响应
     */
    private boolean unauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        log.info(message);
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
        response.getWriter().flush();
        return false;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (token != null) {
            return token;
        }

        // SSE 的 EventSource 无法自定义请求头，从 URL 参数获取（现已由Cookie鉴权覆盖，保留兼容旧客户端）
        token = request.getParameter("token");
        if (token != null) {
            return token;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
