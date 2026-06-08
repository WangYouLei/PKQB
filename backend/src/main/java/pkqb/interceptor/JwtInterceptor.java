package pkqb.interceptor;

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
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = getTokenFromRequest(request);

        if (token == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            log.info("未登录或token已过期");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\"}");
            response.getWriter().flush();
            return false;
        }

        if (!jwtUtil.validateToken(token)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            log.info("未登录或token已过期");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\"}");
            response.getWriter().flush();
            return false;
        }

        // 检查 token 是否在黑名单中（已登出）
        if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + token))) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            log.info("token已失效，请重新登录");
            response.getWriter().write("{\"code\":401,\"message\":\"token已失效，请重新登录\"}");
            response.getWriter().flush();
            return false;
        }

        request.setAttribute("userId", jwtUtil.getUserId(token));
        request.setAttribute("username", jwtUtil.getUsername(token));

        return true;
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String token = request.getHeader("token");
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
