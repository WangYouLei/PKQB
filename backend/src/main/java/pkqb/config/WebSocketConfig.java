package pkqb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import pkqb.util.JwtUtil;

import java.util.Arrays;
import java.util.Map;

/**
 * WebSocket 配置类
 * 注册 WebSocket 端点，配置 JWT 认证拦截器
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5555}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                .addInterceptors(new JwtHandshakeInterceptor(jwtUtil, redisTemplate))
                .setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .toArray(String[]::new));
    }

    /**
     * WebSocket 握手拦截器
     * 在握手阶段从请求参数中提取 JWT Token 并验证，将 userId 存入 WebSocket Session 属性
     */
    @RequiredArgsConstructor
    static class JwtHandshakeInterceptor implements HandshakeInterceptor {

        private final JwtUtil jwtUtil;
        private final RedisTemplate<String, String> redisTemplate;

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                // 优先从 URL 参数获取（前端 WebSocket 连接时主动传入的最新 token）
                String token = servletRequest.getServletRequest().getParameter("token");

                // 其次从请求头获取
                if (token == null) {
                    token = servletRequest.getServletRequest().getHeader("token");
                }

                // 最后从 Cookie 获取（可能携带过期的旧 token，优先级最低）
                if (token == null) {
                    var cookies = servletRequest.getServletRequest().getCookies();
                    if (cookies != null) {
                        for (var cookie : cookies) {
                            if ("token".equals(cookie.getName())) {
                                token = cookie.getValue();
                                break;
                            }
                        }
                    }
                }

                if (token != null) {
                    try {
                        if (jwtUtil.validateToken(token)) {
                            // 检查 token 是否在黑名单中（已登出）
                            if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + token))) {
                                log.warn("[WebSocket握手] token已失效，拒绝连接");
                                return false;
                            }
                            Long userId = jwtUtil.getUserId(token);
                            attributes.put("userId", userId);
                            log.info("[WebSocket握手] 认证成功, userId={}", userId);
                            return true;
                        } else {
                            // 尝试解析看具体失败原因
                            try {
                                jwtUtil.parseToken(token);
                            } catch (Exception parseEx) {
                                log.warn("[WebSocket握手] token解析失败: {}", parseEx.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[WebSocket握手] Token验证异常: {}", e.getMessage());
                    }
                } else {
                    log.warn("[WebSocket握手] 未找到token（Header/Cookie/URL参数均无）");
                }
            } else {
                log.warn("[WebSocket握手] 非ServletServerHttpRequest类型: {}", request.getClass().getSimpleName());
            }

            log.warn("[WebSocket握手] 认证失败，拒绝连接");
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 握手后无需处理
        }
    }
}
